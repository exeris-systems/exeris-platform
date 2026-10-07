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
 * <p>{@code exeris/previewMutation} is its read-only sibling: the same request, computed against the
 * source as it is, answered with the verdict and a unified diff, and written nowhere. It is the
 * route by which a consumer that must not write — {@code exeris-ai-bridge} — reaches the canonical
 * writer (ADR-025).
 *
 * <p><b>API note:</b> carrying {@link JsonElement} puts Gson in this public signature — deliberate
 * (it is LSP4J's own transport type), but it means an external implementor depends on Gson. Revisit
 * when the {@code exeris/*} wire contract freezes at 1.0 (e.g. a versioned interface) if that
 * coupling becomes a problem.
 */
public interface ExerisProtocolExtensions {

    /**
     * Lists the domains in the workspace.
     *
     * @return one summary per indexed {@code @ExerisDomain} source
     */
    @JsonRequest("exeris/domains")
    CompletableFuture<List<DomainSummary>> domains();

    /**
     * Describes one domain. Unknown {@code qualifiedName} yields a JSON {@code null} result.
     *
     * <p><b>Absent and empty are different answers, and the wire keeps them apart.</b> A facet the
     * pipeline does not carry is a {@code null} component, which LSP4J's Gson omits from the
     * response (the launcher's Gson does not {@code serializeNulls}); a facet that is carried and
     * has no entries is serialized as {@code []}. That is the same reading the SDK's
     * {@code DomainMetadata} gives its own JSON ({@code @JsonInclude(NON_NULL)}), so a projection
     * must <em>propagate</em> a null facet rather than flatten it into an empty list —
     * {@code relationships: []} where the model said nothing would tell a consumer "this domain
     * declares none", which is a confident falsehood where silence was available.
     *
     * <p>Only facets the source-model pipeline actually populates belong here. {@code relationships}
     * is extracted by the SDK reader; facets the processor reserves but never extracts (for example
     * {@code projections}, {@code eventHandlers}) arrive empty by construction and must not be
     * projected as though they were data.
     *
     * <p>This method's response shape is pinned for {@code exeris-ai-bridge} by ADR-025, and every
     * change to it is an amendment to that ADR. A component renamed or removed breaks the bridge's
     * shape validator, so that amendment and the validator change land first. A component added as
     * optional is invisible to the bridge, which re-emits only the contract fields; the amendment
     * is what makes it part of the bridge contract ({@code docs/adr/ADR-025.link.md}).
     *
     * @param params the domain to describe
     * @return the domain's description, or {@code null} for an unknown domain
     */
    @JsonRequest("exeris/domainDescribe")
    CompletableFuture<DomainDescription> domainDescribe(DomainDescribeParams params);

    /**
     * Lists every action across the workspace.
     *
     * @return one summary per action, each naming its owning domain
     */
    @JsonRequest("exeris/actions")
    CompletableFuture<List<ActionSummary>> actions();

    /**
     * Applies one SDK {@code MutationOp} to a domain's source and writes it back on success.
     *
     * @param params the target domain, the op, and what it was computed against
     * @return the SDK {@code MutationResult} verdict as JSON
     */
    @JsonRequest("exeris/applyMutation")
    CompletableFuture<JsonElement> applyMutation(ApplyMutationParams params);

    /**
     * Computes what {@code exeris/applyMutation} would write for the same request against the
     * source as it is now, and writes nothing. The verdict is the one the apply would return; the
     * diff is the change it would make, empty when it would change no bytes.
     *
     * @param params the target domain, the op, and what it was computed against — the same payload
     *               {@code exeris/applyMutation} takes
     * @return the verdict, the source's location, and the diff
     */
    @JsonRequest("exeris/previewMutation")
    CompletableFuture<MutationPreview> previewMutation(ApplyMutationParams params);

    /**
     * One-line identity of a domain, returned by {@code exeris/domains}.
     *
     * @param qualifiedName the domain's fully qualified class name — its identity on the wire
     * @param simpleName    the domain's entity name
     * @param packageName   the domain's package
     * @param sourcePath    the {@code file:} URI of the domain's source
     */
    record DomainSummary(String qualifiedName, String simpleName, String packageName, String sourcePath) {
    }

    /**
     * Request payload for {@code exeris/domainDescribe}.
     *
     * @param qualifiedName the domain to describe
     */
    record DomainDescribeParams(String qualifiedName) {
    }

    /**
     * Request payload for {@code exeris/applyMutation} and {@code exeris/previewMutation}.
     *
     * @param qualifiedName    the target domain (which on-disk source to mutate)
     * @param op               the SDK {@code MutationOp} as JSON (Jackson shape, {@code op}
     *                         discriminator) — kept as a {@link JsonElement} so the SDK's Jackson
     *                         mapper, not LSP4J's Gson, decodes the polymorphic payload
     * @param baselineJson     the last-codegen baseline JSON for three-way conflict detection, or
     *                         {@code null}: then the source matching {@code concurrencyToken} is the
     *                         baseline, and without a token the verdict is {@code NO_BASELINE}
     * @param concurrencyToken the {@code SourceDigest} the op was computed against, or {@code null}
     *                         to skip the optimistic-concurrency check
     */
    record ApplyMutationParams(String qualifiedName, JsonElement op, String baselineJson,
                               String concurrencyToken) {
    }

    /**
     * The answer to {@code exeris/previewMutation}.
     *
     * @param result     the SDK {@code MutationResult} the apply would return, as JSON (Jackson
     *                   shape, {@code outcome} discriminator)
     * @param sourcePath the {@code file:} URI of the source the preview was computed against, or
     *                   {@code null} (omitted) when the request was rejected before one was resolved
     * @param diff       the unified diff the apply would make to that source, its paths relative to
     *                   the workspace root; empty when no bytes would change, including every
     *                   non-{@code SUCCESS} verdict
     */
    record MutationPreview(JsonElement result, String sourcePath, String diff) {
    }

    /**
     * Full read-only view of one domain, returned by {@code exeris/domainDescribe}.
     * {@code artefacts} lists the generated surfaces the domain produces (e.g. {@code rest},
     * {@code graphql}), derived from its API/behaviour flags.
     *
     * @param qualifiedName the domain's fully qualified class name
     * @param simpleName    the domain's entity name
     * @param packageName   the domain's package
     * @param sourcePath    the {@code file:} URI of the domain's source
     * @param fields        the domain's fields
     * @param actions       the domain's actions
     * @param artefacts     the generated surfaces the domain produces
     * @param relationships the domain's associations, or {@code null} when the source model does
     *                      not carry the facet (omitted from the wire); an empty list means the
     *                      domain declares none
     * @param sourceDigest  the SDK {@code SourceDigest} of the source as indexed — the
     *                      {@code concurrencyToken} a client passes to {@code exeris/applyMutation} —
     *                      or {@code null} when not available (omitted from the wire)
     */
    record DomainDescription(
            String qualifiedName,
            String simpleName,
            String packageName,
            String sourcePath,
            List<FieldDescription> fields,
            List<ActionDescription> actions,
            List<String> artefacts,
            List<RelationshipDescription> relationships,
            String sourceDigest) {
    }

    /**
     * One association, projected from the SDK's
     * {@link eu.exeris.sdk.sourcemodel.ast.RelationshipMetadata} under the SDK's own component
     * names, so the wire adds no vocabulary of its own.
     *
     * @param name         the relationship's identity (the declaring field)
     * @param targetEntity the target type exactly as the SDK reader extracts it — the declared type
     *                     name (collection element type unwrapped), which is a simple name unless
     *                     the source spelled it qualified. It is not resolved against the workspace;
     *                     a consumer that needs a domain identity matches it itself
     * @param type         the cardinality in the SDK's serialized form (e.g. {@code ONE_TO_MANY}),
     *                     or {@code null} (omitted) when the model carries none
     */
    record RelationshipDescription(String name, String targetEntity, String type) {
    }

    /**
     * One field of a domain.
     *
     * @param name     the field's name
     * @param type     the field's declared type
     * @param required whether the field is declared required
     */
    record FieldDescription(String name, String type, boolean required) {
    }

    /**
     * One action of a domain.
     *
     * @param name       the action's name
     * @param httpMethod the HTTP method it is exposed under
     * @param resultType the type it returns
     * @param params     its parameters
     */
    record ActionDescription(String name, String httpMethod, String resultType, List<ParamSummary> params) {
    }

    /**
     * An action with its owning domain, returned by {@code exeris/actions}.
     *
     * @param owningDomain the qualified name of the domain declaring the action
     * @param name         the action's name
     * @param httpMethod   the HTTP method it is exposed under
     * @param resultType   the type it returns
     * @param params       its parameters
     */
    record ActionSummary(String owningDomain, String name, String httpMethod, String resultType,
                         List<ParamSummary> params) {
    }

    /**
     * One parameter of an action.
     *
     * @param name     the parameter's name
     * @param type     the parameter's declared type
     * @param required whether the parameter is required
     */
    record ParamSummary(String name, String type, boolean required) {
    }
}
