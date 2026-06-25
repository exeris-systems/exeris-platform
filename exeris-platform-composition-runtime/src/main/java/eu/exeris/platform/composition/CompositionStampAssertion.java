package eu.exeris.platform.composition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Boot-time assertion of the ADR-024 composition validation stamp (obligation 8). A generic,
 * once-tested library every SKU bootstrap invokes <b>at startup, before any cap enters
 * {@code initialize}</b>. It <b>asserts</b> the stamp the tooling emitted into
 * {@code cap-manifest.json}; it never re-validates (no {@code @Requires}→{@code @Provides} DAG
 * re-resolution — that would duplicate the tooling resolver and defeat "composition is a build-time
 * concern"). On any failure it throws {@link CompositionStampException}, aborting startup with a
 * diagnostic naming the divergence.
 *
 * <p>The checks (O(n) over caps, no resolution):
 * <ol>
 *   <li><b>Handshake.</b> {@code schemaVersion} in {@code [1, }{@value #KNOWN_SCHEMA_VERSION}{@code ]}
 *       — refuse a missing/pre-ADR-024 ({@code 0}) manifest and one newer than this runtime
 *       understands, each with a clear message.</li>
 *   <li><b>Presence + well-formedness.</b> stamp present, {@code validated == true},
 *       {@code contentBinding} matches {@code ^sha256:[0-9a-f]{64}$}. ({@code compositionVersion}
 *       may be {@code "0.0.0"} until the codegen plugin wires it — tolerated, never a hard fail.)</li>
 *   <li><b>Binding-match.</b> recompute the binding over the manifest's modules and compare to the
 *       stamp — a mismatch means the deployed composition is not the one that was validated
 *       (stale / hand-edited / partial manifest).</li>
 *   <li><b>Version-match.</b> {@link #assertConsistent(CapManifest, Map)} compares the manifest's
 *       provided {@code service@version} to the versions actually on the classpath. The single
 *       bundled-manifest case is self-consistent (the manifest <em>is</em> the deployed set), so the
 *       no-map entry points skip it; supply the observed versions for multi-manifest / mesh deploys.</li>
 * </ol>
 *
 * <p>This is a correctness / operability assertion (catches honest config drift early), <b>not</b> a
 * security or licensing gate — {@code exeris-platform} is itself source-available and forkable
 * (ADR-024 amendment). The open kernel stays cap-blind (obligation 9): nothing here lives in or is
 * called from a kernel package.
 */
public final class CompositionStampAssertion {

    /** Highest {@code cap-manifest.json} schemaVersion this runtime understands (ADR-024 handshake). */
    static final int KNOWN_SCHEMA_VERSION = 2;

    private static final Pattern CONTENT_BINDING = Pattern.compile("^sha256:[0-9a-f]{64}$");

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();

    private CompositionStampAssertion() {
    }

    /** Read {@code cap-manifest.json} from {@code manifestPath} and assert it (single-bundled). */
    public static void assertConsistent(Path manifestPath) {
        Objects.requireNonNull(manifestPath, "manifestPath");
        String json;
        try {
            json = Files.readString(manifestPath);
        } catch (IOException unreadable) {
            throw new CompositionStampException(
                    "cannot read cap-manifest.json at " + manifestPath, unreadable);
        }
        assertConsistent(parse(json, manifestPath.toString()));
    }

    /** Parse {@code manifestJson} and assert it (single-bundled). */
    public static void assertConsistent(String manifestJson) {
        assertConsistent(parse(manifestJson, "<string>"));
    }

    /** Assert a parsed manifest (single-bundled; no classpath version-match). */
    public static void assertConsistent(CapManifest manifest) {
        assertConsistent(manifest, Map.of());
    }

    /**
     * Assert a parsed manifest, additionally checking each provided service's version against
     * {@code classpathServiceVersions} (service → version actually loaded). An empty map skips the
     * version-match (self-consistent single-bundled case).
     */
    public static void assertConsistent(CapManifest manifest, Map<String, String> classpathServiceVersions) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(classpathServiceVersions, "classpathServiceVersions");

        // 1. Handshake — refuse a manifest shape we don't understand rather than mis-assert it.
        // Lower bound matters: FAIL_ON_NULL_FOR_PRIMITIVES=false makes a missing/null schemaVersion
        // deserialize to 0, which must be refused (not silently accepted as "<= 2").
        int schema = manifest.schemaVersion();
        if (schema < 1 || schema > KNOWN_SCHEMA_VERSION) {
            throw new CompositionStampException("cap-manifest schemaVersion " + schema
                    + " is outside the supported range [1, " + KNOWN_SCHEMA_VERSION + "]"
                    + (schema > KNOWN_SCHEMA_VERSION
                            ? " — newer than this runtime understands; upgrade exeris-platform"
                                    + " composition runtime in lock-step with the tooling"
                            : " — a missing or pre-ADR-024 manifest"));
        }

        // 2. Presence + well-formedness.
        CapManifest.Stamp stamp = manifest.stamp();
        if (stamp == null) {
            throw new CompositionStampException(
                    "cap-manifest has no validation stamp — it was not emitted by a passing tooling build");
        }
        if (!stamp.validated()) {
            throw new CompositionStampException(
                    "composition validation stamp is not 'validated' — refusing to start");
        }
        String binding = stamp.contentBinding();
        if (binding == null || !CONTENT_BINDING.matcher(binding).matches()) {
            throw new CompositionStampException(
                    "malformed contentBinding: " + binding + " (expected sha256:<64 lowercase hex>)");
        }
        // compositionVersion is a build input that may be the "0.0.0" default — deliberately not asserted.

        if (manifest.modules() == null) {
            throw new CompositionStampException("cap-manifest has no modules — cannot verify the binding");
        }
        // Reject null fields up front so the binding computation can't NPE (null qualifiedName) or
        // silently diverge ("service@null" hashes to a different binding, masking the real cause as a
        // generic "binding mismatch"). Both surface as a named CompositionStampException instead.
        assertModulesWellFormed(manifest.modules());

        // 3. Binding-match — the deployed cap set must be the one the stamp attests.
        String computed = CompositionBinding.compute(manifest.modules());
        if (!computed.equals(binding)) {
            throw new CompositionStampException(
                    "composition binding mismatch — the deployed composition is not the one that was validated"
                            + "\n  expected (stamp):    " + binding
                            + "\n  computed (manifest): " + computed
                            + "\n  cause: a stale, hand-edited, or partially-deployed cap-manifest.json");
        }

        // 4. Version-match vs the classpath (multi-manifest / mesh). Empty map ⇒ single-bundled, self-consistent.
        if (!classpathServiceVersions.isEmpty()) {
            assertVersionsMatch(manifest, classpathServiceVersions);
        }
    }

    private static void assertModulesWellFormed(List<CapManifest.Module> modules) {
        for (int i = 0; i < modules.size(); i++) {
            CapManifest.Module module = modules.get(i);
            if (module == null) {
                throw new CompositionStampException("cap-manifest module[" + i + "] is null");
            }
            if (module.qualifiedName() == null) {
                throw new CompositionStampException("cap-manifest module[" + i + "] has a null qualifiedName");
            }
            CapManifest.ModuleBody body = module.module();
            if (body == null || body.provides() == null) {
                continue;
            }
            for (CapManifest.Provided provided : body.provides()) {
                if (provided == null) {
                    throw new CompositionStampException(
                            "module '" + module.qualifiedName() + "' has a null provided entry");
                }
                if (provided.service() == null || provided.version() == null) {
                    throw new CompositionStampException("module '" + module.qualifiedName()
                            + "' has a provided entry with a null service or version: " + provided);
                }
            }
        }
    }

    private static void assertVersionsMatch(CapManifest manifest, Map<String, String> classpath) {
        for (CapManifest.Module module : manifest.modules()) {
            CapManifest.ModuleBody body = module.module();
            if (body == null || body.provides() == null) {
                continue;
            }
            for (CapManifest.Provided provided : body.provides()) {
                String onClasspath = classpath.get(provided.service());
                if (onClasspath != null && !onClasspath.equals(provided.version())) {
                    throw new CompositionStampException("composition version drift for service '"
                            + provided.service() + "' (module " + module.qualifiedName() + ")"
                            + "\n  manifest pins: " + provided.version()
                            + "\n  on classpath:  " + onClasspath
                            + "\n  cause: a cap was deployed at a different version than was validated");
                }
            }
        }
    }

    private static CapManifest parse(String json, String source) {
        try {
            CapManifest manifest = MAPPER.readValue(json, CapManifest.class);
            if (manifest == null) {
                throw new CompositionStampException("cap-manifest at " + source + " is empty");
            }
            return manifest;
        } catch (RuntimeException malformed) {
            // Jackson 3 throws unchecked; any parse failure is a refusal to start.
            throw new CompositionStampException(
                    "cap-manifest at " + source + " is not parseable: " + malformed.getMessage(), malformed);
        }
    }

    /**
     * Convenience: collect a {@code service → version} map from one or more already-parsed manifests
     * (e.g. the cap-manifest fragments discovered on the classpath) for use as the
     * {@code classpathServiceVersions} argument in a multi-manifest deploy.
     *
     * <p>Precondition: {@code manifest} must be well-formed (no null module elements) — typically a
     * manifest that already passed {@link #assertConsistent}. Passing a manifest with a null module
     * entry NPEs, by design; this is a post-assertion helper, not an entry validator.
     */
    public static Map<String, String> serviceVersions(CapManifest manifest) {
        Map<String, String> versions = new HashMap<>();
        if (manifest.modules() != null) {
            for (CapManifest.Module module : manifest.modules()) {
                CapManifest.ModuleBody body = module.module();
                if (body != null && body.provides() != null) {
                    for (CapManifest.Provided provided : body.provides()) {
                        // Last-write-wins on a duplicate service. A well-formed manifest has none
                        // (the tooling rejects duplicate providers); a duplicate here only arises
                        // from an already-invalid manifest, where masking a drift warning is moot.
                        versions.put(provided.service(), provided.version());
                    }
                }
            }
        }
        return versions;
    }
}
