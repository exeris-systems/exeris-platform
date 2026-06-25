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
