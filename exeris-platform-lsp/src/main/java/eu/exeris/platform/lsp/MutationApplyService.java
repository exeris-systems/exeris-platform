package eu.exeris.platform.lsp;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import eu.exeris.platform.lsp.ExerisProtocolExtensions.ApplyMutationParams;
import eu.exeris.sdk.sourcemodel.io.ApplyResult;
import eu.exeris.sdk.sourcemodel.io.SourceModelMutationApplier;
import eu.exeris.sdk.sourcemodel.mutation.MutationOp;
import eu.exeris.sdk.sourcemodel.mutation.MutationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Backs {@code exeris/applyMutation}: applies one {@link MutationOp} to an on-disk
 * {@code @ExerisDomain} source through the {@code source-model-io} conflict-aware,
 * idempotent {@link SourceModelMutationApplier} (ADR-042).
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
 */
final class MutationApplyService {

    /** SDK consumer contract: Jackson 3 with null→primitive coercion tolerated (AST package-info). */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();

    // Shared across requests. Safe because LSP4J dispatches messages on a single reader thread
    // (sequential), and MAPPER is a thread-safe Jackson mapper; the applier holds no per-call
    // mutable state. If the server ever moves to async / thread-pool dispatch, revisit this.
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
        if (params.op() == null) {
            return verdict(new MutationResult.ValidationError(null, "mutation op is required"));
        }
        MutationOp op;
        try {
            op = MAPPER.readValue(params.op().toString(), MutationOp.class);
        } catch (RuntimeException unparseable) {
            // Jackson 3's JacksonException is UNCHECKED (extends RuntimeException, unlike Jackson 2's
            // IOException-rooted one), so a malformed op surfaces here, not as a checked throw.
            return verdict(new MutationResult.ValidationError(null,
                    "unparseable mutation op: " + unparseable.getMessage()));
        }

        Optional<WorkspaceIndex.IndexedDomain> target = index.findByQualifiedName(params.qualifiedName());
        if (target.isEmpty()) {
            return verdict(new MutationResult.ValidationError(op.path(),
                    "unknown domain: " + params.qualifiedName()));
        }
        Path file = target.get().sourcePath();

        String current;
        try {
            current = Files.readString(file);
        } catch (IOException unreadable) {
            return verdict(new MutationResult.ValidationError(op.path(),
                    "cannot read source for " + params.qualifiedName() + ": " + unreadable.getMessage()));
        }

        ApplyResult result = applier.apply(op, params.baselineJson(), current, params.concurrencyToken());

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

    /** Serialize an SDK verdict to the JsonElement the LSP4J boundary expects (SDK Jackson shape). */
    private static JsonElement verdict(MutationResult result) {
        return JsonParser.parseString(MAPPER.writeValueAsString(result));
    }
}
