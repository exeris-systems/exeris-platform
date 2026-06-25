package eu.exeris.platform.composition;

import java.util.List;

/**
 * The subset of {@code cap-manifest.json} (ADR-024 schema v2) the boot-time stamp assertion needs:
 * the {@link Stamp} and, per module, the {@code qualifiedName} and provided {@code service@version}
 * pairs the content binding is computed over. Everything else the tooling emits
 * ({@code name}/{@code packageName}, {@code requires}, {@code resolutions}, {@code initOrder},
 * {@code warnings}) is intentionally ignored — the mapper is configured to tolerate unknown fields,
 * so this model stays minimal and forward-tolerant within the schema-version handshake.
 *
 * <p>This is NOT a parallel composition model: it is a read-only projection of the tooling's
 * emitted contract, used only to recompute and compare the stamp. No resolution logic lives here.
 */
public record CapManifest(int schemaVersion, Stamp stamp, List<Module> modules) {

    /** The ADR-024 validation stamp (obligation 7): the build-time verdict + its content binding. */
    public record Stamp(boolean validated, String compositionVersion, String contentBinding) {
    }

    /** One capability module entry; only {@code qualifiedName} + {@code module} feed the binding. */
    public record Module(String qualifiedName, ModuleBody module) {
    }

    /** A module's contract body; only {@code provides} feeds the binding. */
    public record ModuleBody(List<Provided> provides) {
    }

    /** A provided service and its version — the unit the binding hashes as {@code service@version}. */
    public record Provided(String service, String version) {
    }
}
