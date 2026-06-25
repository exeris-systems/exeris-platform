package eu.exeris.platform.lsp;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;

/**
 * Read-only Exeris-specific LSP extensions, namespaced under {@code exeris/*} per the
 * platform LSP method-surface contract.
 *
 * <p>This trio backs the {@code lsp:*} tool family in exeris-ai-bridge (ADR-025): list the
 * domains in the workspace, describe one in detail, and list every action across the
 * workspace. There are no mutation methods here — write-back ({@code exeris/applyMutation})
 * is a separate, later slice.
 */
public interface ExerisProtocolExtensions {

    @JsonRequest("exeris/domains")
    CompletableFuture<List<DomainSummary>> domains();

    @JsonRequest("exeris/domainDescribe")
    CompletableFuture<DomainDescription> domainDescribe(DomainDescribeParams params);

    @JsonRequest("exeris/actions")
    CompletableFuture<List<ActionSummary>> actions();

    /** One-line identity of a domain, returned by {@code exeris/domains}. */
    record DomainSummary(String qualifiedName, String simpleName, String packageName, String sourcePath) {
    }

    /** Request payload for {@code exeris/domainDescribe}. */
    record DomainDescribeParams(String qualifiedName) {
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
