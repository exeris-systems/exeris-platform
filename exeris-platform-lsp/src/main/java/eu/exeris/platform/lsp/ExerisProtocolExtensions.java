package eu.exeris.platform.lsp;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

/**
 * Exeris-specific LSP extensions, namespaced under {@code exeris/*} per the platform LSP
 * method-surface contract.
 *
 * <p>The read trio backs the {@code lsp:*} tool family in exeris-ai-bridge (ADR-025): list the
 * domains in the workspace, describe one in detail, and list every action across the workspace.
 *
 * <p>{@code exeris/applyMutation} is the single write method (ADR-042): it applies one SDK-owned
 * {@link eu.exeris.sdk.sourcemodel.mutation.MutationOp} to an {@code @ExerisDomain} source via the
 * {@code source-model-io} conflict-aware, idempotent writer. Its {@code op} payload and
 * {@code MutationResult} verdict cross the wire as JSON conforming to the SDK's Jackson contract
 * (polymorphic on the {@code op} / {@code outcome} discriminators); they are carried as
 * {@link JsonElement} because LSP4J's Gson layer does not honour those discriminators — the server
 * (de)serializes them with the SDK's Jackson 3 mapper. See {@link MutationApplyService}.
 *
 * <p><b>API note:</b> carrying {@link JsonElement} puts Gson in this public signature — deliberate
 * (it is LSP4J's own transport type), but it means an external implementor depends on Gson. Revisit
 * when the {@code exeris/*} wire contract freezes at 1.0 (e.g. a versioned interface) if that
 * coupling becomes a problem.
 */
public interface ExerisProtocolExtensions {

    @JsonRequest("exeris/domains")
    CompletableFuture<List<DomainSummary>> domains();

    /**
     * Read this before widening {@link DomainDescription}.
     *
     * <p>The temptation is understandable and the mechanics are trivial:
     * {@code ProtocolProjections.toDescription} already holds the whole
     * {@link eu.exeris.sdk.sourcemodel.ast.DomainMetadata} and casts three of its ~35 components
     * onto the wire. Adding {@code relationships} is a projection widening, not data plumbing.
     * The cost of that change is entirely process — ADR-025's 2026-06-24 amendment pins this
     * method's shape for {@code exeris-ai-bridge} — and not implementation.
     *
     * <p><b>But the facets are not equally real, and the wire cannot afford to pretend they
     * are.</b> Measured against the {@code exeris-tooling} processor rather than against the AST:
     *
     * <ul>
     *   <li>{@code relationships}, {@code events} (from {@code @DomainEvent}) and
     *       {@code sagaMetadata} are LIVE — extracted, and consumed by the emitters.</li>
     *   <li>{@code projections} and {@code eventHandlers} are permanently empty.
     *       {@code @Projection} and {@code @EventHandler} sit in the processor's own
     *       {@code UNREAD_NOTES} registry as <em>reserved</em>, design-gated on the behavioural
     *       corpus. Nothing extracts them, so the lists arrive empty by construction.</li>
     *   <li>{@code graphMetadata} is partial in a way that is worse than either: edges are read,
     *       {@code properties} is passed as {@code null} and {@code queries} as an empty list —
     *       the same "not carried" state spelled two different ways, upstream.</li>
     * </ul>
     *
     * <p>So the question this method's next change has to answer is <em>not</em> "which fields to
     * add". It is <b>how the shape distinguishes "there are none" from "this pipeline does not
     * carry them"</b>. The bridge validates and re-emits contract fields, so pinning
     * {@code projections} would hand every agent {@code projections: []} on every domain, which
     * reads as "this domain declares no projections" — a confident falsehood, frozen into a wire
     * format, where an absent field would have said nothing at all.
     *
     * <p>The ecosystem has already decided this once, one level up: the processor's
     * {@code UNREAD_NOTES} deliberately separates <em>reserved</em> from merely <em>unbuilt</em>
     * because "an author deserves to know which one they have hit". Same distinction, and the same
     * argument for spending words on it, at the wire instead of at the diagnostic.
     *
     * <p>Widening therefore triggers an ADR, and this paragraph is its input rather than something
     * to be rediscovered at review.
     */
    @JsonRequest("exeris/domainDescribe")
    CompletableFuture<DomainDescription> domainDescribe(DomainDescribeParams params);

    @JsonRequest("exeris/actions")
    CompletableFuture<List<ActionSummary>> actions();

    @JsonRequest("exeris/applyMutation")
    CompletableFuture<JsonElement> applyMutation(ApplyMutationParams params);

    /** One-line identity of a domain, returned by {@code exeris/domains}. */
    record DomainSummary(String qualifiedName, String simpleName, String packageName, String sourcePath) {
    }

    /** Request payload for {@code exeris/domainDescribe}. */
    record DomainDescribeParams(String qualifiedName) {
    }

    /**
     * Request payload for {@code exeris/applyMutation}.
     *
     * @param qualifiedName    the target domain (which on-disk source to mutate)
     * @param op               the SDK {@code MutationOp} as JSON (Jackson shape, {@code op}
     *                         discriminator) — kept as a {@link JsonElement} so the SDK's Jackson
     *                         mapper, not LSP4J's Gson, decodes the polymorphic payload
     * @param baselineJson     the last-codegen baseline JSON for three-way conflict detection, or
     *                         {@code null} (→ {@code NO_BASELINE})
     * @param concurrencyToken the {@code SourceDigest} the op was computed against, or {@code null}
     *                         to skip the optimistic-concurrency check
     */
    record ApplyMutationParams(String qualifiedName, JsonElement op, String baselineJson,
                               String concurrencyToken) {
    }

    /**
     * Full read-only view of one domain, returned by {@code exeris/domainDescribe}.
     * {@code artefacts} lists the generated surfaces the domain produces (e.g. {@code rest},
     * {@code graphql}), derived from its API/behaviour flags.
     */
    record DomainDescription(
            String qualifiedName,
            String simpleName,
            String packageName,
            String sourcePath,
            List<FieldDescription> fields,
            List<ActionDescription> actions,
            List<String> artefacts) {
    }

    record FieldDescription(String name, String type, boolean required) {
    }

    record ActionDescription(String name, String httpMethod, String resultType, List<ParamSummary> params) {
    }

    /** An action with its owning domain, returned by {@code exeris/actions}. */
    record ActionSummary(String owningDomain, String name, String httpMethod, String resultType,
                         List<ParamSummary> params) {
    }

    record ParamSummary(String name, String type, boolean required) {
    }
}
