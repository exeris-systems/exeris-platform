package eu.exeris.platform.lsp;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import eu.exeris.platform.lsp.ExerisProtocolExtensions.ApplyMutationParams;
import eu.exeris.platform.lsp.ExerisProtocolExtensions.MutationPreview;
import eu.exeris.sdk.sourcemodel.io.ApplyResult;
import eu.exeris.sdk.sourcemodel.io.SourceModelMutationApplier;
import eu.exeris.sdk.sourcemodel.io.SourceModelReader;
import eu.exeris.sdk.sourcemodel.mutation.BaselineTrust;
import eu.exeris.sdk.sourcemodel.mutation.MutationOp;
import eu.exeris.sdk.sourcemodel.mutation.MutationResult;
import eu.exeris.sdk.sourcemodel.mutation.SourceDigest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Backs {@code exeris/applyMutation} and {@code exeris/previewMutation}: applies one
 * {@link MutationOp} to an on-disk {@code @ExerisDomain} source through the {@code source-model-io}
 * conflict-aware, idempotent {@link SourceModelMutationApplier} (ADR-042) — and writes the result
 * back, or, for a preview, returns it as a diff and writes nothing.
 *
 * <p>Both paths compute through the same {@link #compute} step, so a preview answers exactly what
 * an apply of the same request against the same bytes would write.
 *
 * <p>The op and the {@link MutationResult} verdict are SDK-owned Jackson-polymorphic shapes
 * (discriminators {@code op} / {@code outcome}). LSP4J serializes with Gson, which ignores
 * {@code @JsonTypeInfo}, so this service (de)serializes those payloads with the SDK's Jackson 3
 * mapper — configured per the SDK consumer contract ({@code FAIL_ON_NULL_FOR_PRIMITIVES=false},
 * see the SDK {@code ast} package-info) — and only the opaque {@link JsonElement} crosses the
 * LSP4J boundary.
 *
 * <p><b>Idempotent write-back (contract).</b> The source is written back only on a {@code SUCCESS}
 * verdict that actually changes bytes; a convergent op is a {@code SUCCESS} no-op and is not
 * rewritten. Since the underlying writer is idempotent, applying the same op twice converges to
 * identical on-disk state. The index is invalidated only when bytes changed.
 *
 * <p><b>One writer per file at a time.</b> Each transport session owns its own server and so its
 * own instance of this service, and sessions run concurrently. The read → apply → write of one
 * source is therefore serialised per file across the whole process: a second op on the same file
 * reads the bytes the first one wrote and is judged against them (merged, or reported as a
 * {@code CONFLICT}), instead of overwriting them with a result computed from stale input.
 *
 * <p><b>Token-anchored baseline.</b> A client that sends no {@code baselineJson} but does send a
 * {@code concurrencyToken} computed its op against the source whose digest is that token. When the
 * live source still carries that digest, that source <em>is</em> the baseline the op was computed
 * against, so the server derives the baseline from it, stamped with the current schema version.
 * When the digest no longer matches, no baseline is derived and the SDK applier rejects the op as
 * {@code STALE_DIGEST}. Without either a baseline or a token the verdict stays {@code NO_BASELINE}:
 * the server never guesses a baseline from build output, which can belong to another domain or
 * predate the server's own writes.
 */
final class MutationApplyService {

    /** SDK consumer contract: Jackson 3 with null→primitive coercion tolerated (AST package-info). */
    private static final ObjectMapper MAPPER = SdkJson.MAPPER;

    /**
     * Process-wide lock stripes, chosen by the hash of the file's real path, so every path that
     * reaches one file (a symlink included) takes the same lock. A fixed stripe count keeps the
     * memory bounded however many files a long-lived launcher writes; two files that share a stripe
     * only take turns, which costs latency and never correctness.
     */
    private static final ReentrantLock[] FILE_LOCKS = new ReentrantLock[64];

    static {
        for (int i = 0; i < FILE_LOCKS.length; i++) {
            FILE_LOCKS[i] = new ReentrantLock();
        }
    }

    // MAPPER is a thread-safe Jackson mapper and the applier holds no per-call mutable state, so
    // both are safe to share between concurrent calls; the file itself is guarded by FILE_LOCKS.
    private final SourceModelMutationApplier applier = new SourceModelMutationApplier();

    /**
     * Applies {@code params.op()} to the source for {@code params.qualifiedName()}. Every
     * apply-path failure (unparseable op, unknown domain, unreadable/unwritable source) maps to a
     * {@link MutationResult.ValidationError} rather than throwing, mirroring the SDK applier's
     * "{@code ApplyResult} never throws" contract. The only theoretical unchecked escape is a
     * serialization failure inside {@link #verdict} — which the SDK-sealed {@link MutationResult}
     * types do not trigger in practice.
     *
     * @param index            the live workspace index (resolves qualifiedName → source path)
     * @param params           the request payload
     * @param onSourcesChanged invoked exactly once if (and only if) bytes were written back
     * @return the SDK {@link MutationResult} serialized as a {@link JsonElement}
     */
    JsonElement apply(WorkspaceIndex index, ApplyMutationParams params, Runnable onSourcesChanged) {
        Target target = resolve(index, params);
        if (target.rejection() != null) {
            return verdict(target.rejection());
        }
        Path file = target.file();
        ReentrantLock lock = FILE_LOCKS[Math.floorMod(lockKey(file).hashCode(), FILE_LOCKS.length)];
        lock.lock();
        try {
            return applyToFile(target.op(), params, file, onSourcesChanged);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Computes what {@link #apply} would write for {@code params}, against the source as it is now,
     * and returns it as a unified diff with the verdict. Nothing is written and the index is left
     * as it is. Failures map to a {@link MutationResult.ValidationError} verdict, as for apply.
     *
     * @param index  the live workspace index (resolves qualifiedName → source path)
     * @param params the request payload, the same as {@code exeris/applyMutation} takes
     * @return the verdict, the source's {@code file:} URI, and the diff (empty when nothing changes)
     */
    MutationPreview preview(WorkspaceIndex index, ApplyMutationParams params) {
        Target target = resolve(index, params);
        if (target.rejection() != null) {
            return new MutationPreview(verdict(target.rejection()), null, "");
        }
        Path file = target.file();
        String current;
        // The read takes the file's write lock, so a preview never sees a half-written source.
        ReentrantLock lock = FILE_LOCKS[Math.floorMod(lockKey(file).hashCode(), FILE_LOCKS.length)];
        lock.lock();
        try {
            current = Files.readString(file);
        } catch (IOException unreadable) {
            return new MutationPreview(verdict(new MutationResult.ValidationError(target.op().path(),
                    "cannot read source for " + params.qualifiedName() + ": " + unreadable.getMessage())),
                    file.toUri().toString(), "");
        } finally {
            lock.unlock();
        }
        ApplyResult result = compute(target.op(), params, current);
        String diff = result.applied() ? UnifiedDiff.of(diffPath(index, file), current, result.source()) : "";
        return new MutationPreview(verdict(result.outcome()), file.toUri().toString(), diff);
    }

    /** The op and its source, or the verdict that rejected the request before either was known. */
    private record Target(MutationOp op, Path file, MutationResult rejection) {
    }

    private static Target resolve(WorkspaceIndex index, ApplyMutationParams params) {
        if (params.op() == null) {
            return new Target(null, null, new MutationResult.ValidationError(null, "mutation op is required"));
        }
        MutationOp op;
        try {
            op = MAPPER.readValue(params.op().toString(), MutationOp.class);
        } catch (RuntimeException unparseable) {
            // Jackson 3's JacksonException is UNCHECKED (extends RuntimeException, unlike Jackson 2's
            // IOException-rooted one), so a malformed op surfaces here, not as a checked throw.
            return new Target(null, null, new MutationResult.ValidationError(null,
                    "unparseable mutation op: " + unparseable.getMessage()));
        }
        Optional<WorkspaceIndex.IndexedDomain> domain = index.findByQualifiedName(params.qualifiedName());
        if (domain.isEmpty()) {
            return new Target(op, null, new MutationResult.ValidationError(op.path(),
                    "unknown domain: " + params.qualifiedName()));
        }
        return new Target(op, domain.get().sourcePath(), null);
    }

    /** The diff header path: relative to the workspace root, so the patch applies from there. */
    private static String diffPath(WorkspaceIndex index, Path file) {
        Path root = index.root();
        Path relative = root != null && file.startsWith(root) ? root.relativize(file) : file;
        return relative.toString().replace('\\', '/');
    }

    /** The file's real path; a path that cannot be resolved (the read reports why) keys by itself. */
    private static Path lockKey(Path file) {
        try {
            return file.toRealPath();
        } catch (IOException _) {
            return file.toAbsolutePath().normalize();
        }
    }

    /** The read → apply → write of one source; the caller holds that file's lock. */
    private JsonElement applyToFile(MutationOp op, ApplyMutationParams params, Path file,
                                    Runnable onSourcesChanged) {
        String current;
        try {
            current = Files.readString(file);
        } catch (IOException unreadable) {
            return verdict(new MutationResult.ValidationError(op.path(),
                    "cannot read source for " + params.qualifiedName() + ": " + unreadable.getMessage()));
        }

        ApplyResult result = compute(op, params, current);

        if (result.applied() && !result.source().equals(current)) {
            try {
                Files.writeString(file, result.source());
            } catch (IOException unwritable) {
                // Not strictly a *validation* failure — the op computed cleanly (result.applied());
                // this is an IO error at write-back. We reuse VALIDATION_ERROR because it is the
                // only non-success MutationResult variant the SDK exposes for "couldn't complete";
                // the message names write-back so an ai-bridge consumer doesn't read it as
                // "the op was structurally wrong".
                return verdict(new MutationResult.ValidationError(op.path(),
                        "mutation computed but write-back failed for " + params.qualifiedName()
                                + ": " + unwritable.getMessage()));
            }
            onSourcesChanged.run();
        }
        return verdict(result.outcome());
    }

    /** The SDK verdict and resulting source for {@code op} against {@code current}; writes nothing. */
    private ApplyResult compute(MutationOp op, ApplyMutationParams params, String current) {
        String baselineJson = params.baselineJson();
        if (baselineJson == null || baselineJson.isBlank()) {
            baselineJson = tokenAnchoredBaseline(current, params.concurrencyToken());
        }
        return applier.apply(op, baselineJson, current, params.concurrencyToken());
    }

    /**
     * The baseline a token-carrying op was computed against: the live source's model, stamped as a
     * trusted baseline, when the live source still has the token's digest; otherwise {@code null},
     * leaving the verdict to the applier ({@code STALE_DIGEST} for a moved source, {@code NO_BASELINE}
     * for a missing token). An unparseable source also yields {@code null}; the applier reports it.
     */
    private static String tokenAnchoredBaseline(String current, String concurrencyToken) {
        if (concurrencyToken == null || !concurrencyToken.equals(SourceDigest.of(current))) {
            return null;
        }
        try {
            return new SourceModelReader().read(current)
                    .map(model -> {
                        ObjectNode node = (ObjectNode) MAPPER.valueToTree(model);
                        node.setAll((ObjectNode) MAPPER.valueToTree(BaselineTrust.current(concurrencyToken)));
                        return MAPPER.writeValueAsString(node);
                    })
                    .orElse(null);
        } catch (RuntimeException unparseable) {
            return null;
        }
    }

    /** Serialize an SDK verdict to the JsonElement the LSP4J boundary expects (SDK Jackson shape). */
    private static JsonElement verdict(MutationResult result) {
        return JsonParser.parseString(MAPPER.writeValueAsString(result));
    }
}
