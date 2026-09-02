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
     * <p>The distinction between "there are none" and "this pipeline does not carry them" is
     * therefore load-bearing — and it does <b>not</b> need inventing here, which is the easy
     * mistake. {@code DomainMetadata} is annotated {@code @JsonInclude(NON_NULL)}: a null facet is
     * omitted from the wire, an empty list is serialized as {@code []}. Null already means "not
     * carried" and {@code []} already means "none". Gson, which LSP4J uses on this method's
     * response, omits nulls by default too, so the same reading survives our transport.
     *
     * <p>Two things follow, and only one of them is ours.
     *
     * <ul>
     *   <li><b>Ours:</b> a widened {@link DomainDescription} must <em>propagate</em> that
     *       distinction rather than flatten it. Projecting a null facet into an empty list
     *       manufactures {@code projections: []} where the model said nothing — and the bridge
     *       validates and re-emits contract fields, so an agent would read that as "this domain
     *       declares no projections". A confident falsehood, frozen into a wire format, where
     *       silence was available.</li>
     *   <li><b>Upstream's:</b> the convention exists but is applied inconsistently —
     *       {@code GraphMetadata} passes {@code properties} as null and {@code queries} as an empty
     *       list for the same "not carried" state. That is a tooling ask, not something to paper
     *       over with a platform-local convention on top of a model this repo does not own.</li>
     * </ul>
     *
     * <p>Widening this method is an <b>amendment to ADR-025</b>, not a new decision — see
     * {@code docs/adr/ADR-025.link.md}, which prescribes exactly that for any change to this
     * surface. This paragraph is that amendment's input rather than something to be rediscovered
     * at review.
     *
     * <p>Provenance, so the amendment's author re-checks rather than trusts: facet liveness and the
     * {@code UNREAD_NOTES} quotations come from {@code ExerisDomainProcessor} in
     * {@code exeris-tooling} v0.8.0; {@code NON_NULL} and the component count from
     * {@code DomainMetadata} in {@code exeris-sdk} v0.11.0. Both are sibling repositories that move.
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
