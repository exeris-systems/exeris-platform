package eu.exeris.platform.lsp;

import eu.exeris.platform.lsp.ExerisProtocolExtensions.ActionDescription;
import eu.exeris.platform.lsp.ExerisProtocolExtensions.ActionSummary;
import eu.exeris.platform.lsp.ExerisProtocolExtensions.DomainDescription;
import eu.exeris.platform.lsp.ExerisProtocolExtensions.DomainSummary;
import eu.exeris.platform.lsp.ExerisProtocolExtensions.FieldDescription;
import eu.exeris.platform.lsp.ExerisProtocolExtensions.ParamSummary;
import eu.exeris.platform.lsp.WorkspaceIndex.IndexedDomain;
import eu.exeris.sdk.sourcemodel.ast.ActionMetadata;
import eu.exeris.sdk.sourcemodel.ast.DomainMetadata;
import java.util.ArrayList;
import java.util.List;

/** Projects the canonical {@link DomainMetadata} onto the read-only {@code exeris/*} wire shapes. */
final class ProtocolProjections {

    private ProtocolProjections() {
    }

    static DomainSummary toSummary(IndexedDomain indexed) {
        DomainMetadata d = indexed.metadata();
        return new DomainSummary(d.fullyQualifiedName(), d.entityName(), d.packageName(),
                indexed.sourcePath().toString());
    }

    static DomainDescription toDescription(IndexedDomain indexed) {
        DomainMetadata d = indexed.metadata();
        List<FieldDescription> fields = d.fields().stream()
                .map(f -> new FieldDescription(f.name(), f.type(), f.required()))
                .toList();
        List<ActionDescription> actions = d.actions().stream()
                .map(a -> new ActionDescription(a.name(), a.httpMethod(), a.resultType(), params(a)))
                .toList();
        return new DomainDescription(d.fullyQualifiedName(), d.entityName(), d.packageName(),
                indexed.sourcePath().toString(), fields, actions, artefacts(d));
    }

    static List<ActionSummary> toActionSummaries(IndexedDomain indexed) {
        DomainMetadata d = indexed.metadata();
        String owner = d.fullyQualifiedName();
        return d.actions().stream()
                .map(a -> new ActionSummary(owner, a.name(), a.httpMethod(), a.resultType(), params(a)))
                .toList();
    }

    private static List<ParamSummary> params(ActionMetadata action) {
        return action.params().stream()
                .map(p -> new ParamSummary(p.name(), p.type(), p.required()))
                .toList();
    }

    /** The generated surfaces a domain produces — derived from its API/behaviour flags. */
    private static List<String> artefacts(DomainMetadata d) {
        List<String> artefacts = new ArrayList<>();
        if (d.restApi()) {
            artefacts.add("rest");
        }
        if (d.graphqlApi()) {
            artefacts.add("graphql");
        }
        if (d.realTimeApi()) {
            artefacts.add("realtime");
        }
        if (d.isEventSourced()) {
            artefacts.add("eventSourced");
        }
        if (d.isSaga()) {
            artefacts.add("saga");
        }
        if (d.hasEvents()) {
            artefacts.add("events");
        }
        if (d.internalClient()) {
            artefacts.add("internalClient");
        }
        return artefacts;
    }
}
