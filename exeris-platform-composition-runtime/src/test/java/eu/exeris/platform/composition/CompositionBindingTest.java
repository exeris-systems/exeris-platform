package eu.exeris.platform.composition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins the content-binding algorithm to the shared ADR-024 spec via a golden vector. */
class CompositionBindingTest {

    /**
     * SHA-256 of the canonical form for {com.app.Audit[AuditLog@1.0.0], com.app.Billing[Invoice@2.0.0,
     * PaymentApi@1.2.0]}, computed independently (shell {@code sha256sum}) — a true cross-impl check
     * that this port matches the tooling's {@code CompositionStamp#computeBinding} byte-for-byte.
     */
    private static final String GOLDEN =
            "sha256:83aae84863de8480b0c1ec943f7d350900a1ff2aab78b4c311684ca2ecc79e96";

    @Test
    void matchesGoldenVectorRegardlessOfInputOrder() {
        // Modules AND provides deliberately unsorted — compute() must canonicalise to the golden.
        List<CapManifest.Module> modules = List.of(
                new CapManifest.Module("com.app.Billing", new CapManifest.ModuleBody(List.of(
                        new CapManifest.Provided("com.api.PaymentApi", "1.2.0"),
                        new CapManifest.Provided("com.api.Invoice", "2.0.0")))),
                new CapManifest.Module("com.app.Audit", new CapManifest.ModuleBody(List.of(
                        new CapManifest.Provided("com.api.AuditLog", "1.0.0")))));

        assertThat(CompositionBinding.compute(modules)).isEqualTo(GOLDEN);
    }

    @Test
    void differentVersionProducesDifferentBinding() {
        List<CapManifest.Module> a = List.of(new CapManifest.Module("com.app.Audit",
                new CapManifest.ModuleBody(List.of(new CapManifest.Provided("com.api.AuditLog", "1.0.0")))));
        List<CapManifest.Module> b = List.of(new CapManifest.Module("com.app.Audit",
                new CapManifest.ModuleBody(List.of(new CapManifest.Provided("com.api.AuditLog", "1.0.1")))));

        assertThat(CompositionBinding.compute(a)).isNotEqualTo(CompositionBinding.compute(b));
    }

    @Test
    void moduleWithNoProvidesContributesOnlyItsName() {
        List<CapManifest.Module> modules = List.of(
                new CapManifest.Module("com.app.Empty", new CapManifest.ModuleBody(List.of())));

        assertThat(CompositionBinding.compute(modules)).matches("sha256:[0-9a-f]{64}");
    }
}
