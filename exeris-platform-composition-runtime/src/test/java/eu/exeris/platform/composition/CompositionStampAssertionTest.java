package eu.exeris.platform.composition;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Boot-time assertion behaviour: the four checks + schema-v2 JSON parsing (ADR-024 obligation 8). */
class CompositionStampAssertionTest {

    private static List<CapManifest.Module> fixtureModules() {
        return List.of(
                new CapManifest.Module("com.app.Audit", new CapManifest.ModuleBody(List.of(
                        new CapManifest.Provided("com.api.AuditLog", "1.0.0")))),
                new CapManifest.Module("com.app.Billing", new CapManifest.ModuleBody(List.of(
                        new CapManifest.Provided("com.api.Invoice", "2.0.0"),
                        new CapManifest.Provided("com.api.PaymentApi", "1.2.0")))));
    }

    /** A self-consistent manifest: stamp binding == the binding recomputed over its modules. */
    private static CapManifest valid() {
        List<CapManifest.Module> modules = fixtureModules();
        return new CapManifest(2,
                new CapManifest.Stamp(true, "0.0.0", CompositionBinding.compute(modules)),
                modules);
    }

    @Test
    void validManifestPasses() {
        assertThatCode(() -> CompositionStampAssertion.assertConsistent(valid()))
                .doesNotThrowAnyException();
    }

    @Test
    void unversionedCompositionIsTolerated() {
        // valid() uses compositionVersion "0.0.0" — the default until the 0.3.0 plugin wires it.
        assertThatCode(() -> CompositionStampAssertion.assertConsistent(valid()))
                .doesNotThrowAnyException();
    }

    @Test
    void bindingMismatchThrows() {
        CapManifest m = valid();
        // Keep the original (valid) stamp but tamper the modules → recomputed binding diverges.
        CapManifest tampered = new CapManifest(2, m.stamp(), List.of(
                new CapManifest.Module("com.app.Audit", new CapManifest.ModuleBody(List.of(
                        new CapManifest.Provided("com.api.AuditLog", "9.9.9"))))));
        assertThatThrownBy(() -> CompositionStampAssertion.assertConsistent(tampered))
                .isInstanceOf(CompositionStampException.class)
                .hasMessageContaining("binding mismatch");
    }

    @Test
    void missingStampThrows() {
        assertThatThrownBy(() -> CompositionStampAssertion.assertConsistent(
                new CapManifest(2, null, fixtureModules())))
                .isInstanceOf(CompositionStampException.class)
                .hasMessageContaining("no validation stamp");
    }

    @Test
    void notValidatedThrows() {
        List<CapManifest.Module> modules = fixtureModules();
        CapManifest m = new CapManifest(2,
                new CapManifest.Stamp(false, "0.0.0", CompositionBinding.compute(modules)), modules);
        assertThatThrownBy(() -> CompositionStampAssertion.assertConsistent(m))
                .isInstanceOf(CompositionStampException.class)
                .hasMessageContaining("not 'validated'");
    }

    @Test
    void malformedBindingThrows() {
        CapManifest m = new CapManifest(2,
                new CapManifest.Stamp(true, "0.0.0", "deadbeef"), fixtureModules());
        assertThatThrownBy(() -> CompositionStampAssertion.assertConsistent(m))
                .isInstanceOf(CompositionStampException.class)
                .hasMessageContaining("malformed contentBinding");
    }

    @Test
    void schemaNewerThanKnownThrows() {
        List<CapManifest.Module> modules = fixtureModules();
        CapManifest m = new CapManifest(3,
                new CapManifest.Stamp(true, "0.0.0", CompositionBinding.compute(modules)), modules);
        assertThatThrownBy(() -> CompositionStampAssertion.assertConsistent(m))
                .isInstanceOf(CompositionStampException.class)
                .hasMessageContaining("schemaVersion");
    }

    @Test
    void versionDriftAgainstClasspathThrows() {
        // The classpath carries PaymentApi at a different version than the manifest pins.
        assertThatThrownBy(() -> CompositionStampAssertion.assertConsistent(
                valid(), Map.of("com.api.PaymentApi", "9.9.9")))
                .isInstanceOf(CompositionStampException.class)
                .hasMessageContaining("version drift");
    }

    @Test
    void matchingClasspathVersionsPass() {
        CapManifest m = valid();
        assertThatCode(() -> CompositionStampAssertion.assertConsistent(
                m, CompositionStampAssertion.serviceVersions(m)))
                .doesNotThrowAnyException();
    }

    @Test
    void parsesSchemaV2JsonWithUnknownFieldsAndPasses() {
        assertThatCode(() -> CompositionStampAssertion.assertConsistent(VALID_JSON))
                .doesNotThrowAnyException();
    }

    @Test
    void tamperedJsonBindingThrows() {
        assertThatThrownBy(() -> CompositionStampAssertion.assertConsistent(TAMPERED_JSON))
                .isInstanceOf(CompositionStampException.class)
                .hasMessageContaining("binding mismatch");
    }

    @Test
    void malformedJsonThrows() {
        assertThatThrownBy(() -> CompositionStampAssertion.assertConsistent("{ not json"))
                .isInstanceOf(CompositionStampException.class)
                .hasMessageContaining("not parseable");
    }

    @Test
    void schemaBelowOneThrows() {
        // A missing/null schemaVersion deserializes to 0 — must be refused, not accepted as "<= 2".
        List<CapManifest.Module> modules = fixtureModules();
        CapManifest m = new CapManifest(0,
                new CapManifest.Stamp(true, "0.0.0", CompositionBinding.compute(modules)), modules);
        assertThatThrownBy(() -> CompositionStampAssertion.assertConsistent(m))
                .isInstanceOf(CompositionStampException.class)
                .hasMessageContaining("range");
    }

    @Test
    void nullQualifiedNameIsNamedNotAnNpe() {
        CapManifest.Stamp wellFormedStamp = new CapManifest.Stamp(true, "0.0.0", "sha256:" + "0".repeat(64));
        CapManifest m = new CapManifest(2, wellFormedStamp, List.of(
                new CapManifest.Module(null, new CapManifest.ModuleBody(List.of()))));
        assertThatThrownBy(() -> CompositionStampAssertion.assertConsistent(m))
                .isInstanceOf(CompositionStampException.class)
                .hasMessageContaining("null qualifiedName");
    }

    @Test
    void nullProvidedVersionIsNamedNotAnNpeOrSilentDrift() {
        CapManifest.Stamp wellFormedStamp = new CapManifest.Stamp(true, "0.0.0", "sha256:" + "0".repeat(64));
        CapManifest m = new CapManifest(2, wellFormedStamp, List.of(
                new CapManifest.Module("com.app.X", new CapManifest.ModuleBody(
                        Collections.singletonList(new CapManifest.Provided("com.api.Y", null))))));
        assertThatThrownBy(() -> CompositionStampAssertion.assertConsistent(m))
                .isInstanceOf(CompositionStampException.class)
                .hasMessageContaining("null service or version");
    }

    @Test
    void validManifestFromPathPasses(@TempDir Path dir) throws Exception {
        Path manifest = Files.writeString(dir.resolve("cap-manifest.json"), VALID_JSON);
        assertThatCode(() -> CompositionStampAssertion.assertConsistent(manifest))
                .doesNotThrowAnyException();
    }

    @Test
    void unreadablePathThrows(@TempDir Path dir) {
        assertThatThrownBy(() -> CompositionStampAssertion.assertConsistent(dir.resolve("absent.json")))
                .isInstanceOf(CompositionStampException.class)
                .hasMessageContaining("cannot read");
    }

    // schemaVersion 2 manifest with the golden binding, modules in unsorted array order, and the
    // full set of tooling-emitted fields the assertion ignores (name/packageName/requires/
    // resolutions/initOrder/warnings) — exercises the parse + ignore-unknown + binding-match path.
    private static final String VALID_JSON = """
            {
              "schemaVersion": 2,
              "stamp": {
                "validated": true,
                "compositionVersion": "0.0.0",
                "contentBinding": "sha256:83aae84863de8480b0c1ec943f7d350900a1ff2aab78b4c311684ca2ecc79e96"
              },
              "modules": [
                { "name": "Billing", "packageName": "com.app", "qualifiedName": "com.app.Billing",
                  "module": { "provides": [ {"service":"com.api.PaymentApi","version":"1.2.0"},
                                            {"service":"com.api.Invoice","version":"2.0.0"} ],
                              "requires": [] } },
                { "name": "Audit", "packageName": "com.app", "qualifiedName": "com.app.Audit",
                  "module": { "provides": [ {"service":"com.api.AuditLog","version":"1.0.0"} ],
                              "requires": [] } }
              ],
              "resolutions": [],
              "initOrder": ["com.app.Audit","com.app.Billing"],
              "warnings": []
            }
            """;

    // Same as VALID_JSON but PaymentApi is deployed at 9.9.9 while the stamp still pins the golden:
    // the recomputed binding diverges → mismatch.
    private static final String TAMPERED_JSON = VALID_JSON.replace("\"1.2.0\"", "\"9.9.9\"");
}
