package eu.exeris.platform.composition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;

/**
 * Recomputes the ADR-024 content binding — {@code "sha256:" + hex(SHA-256)} over a canonical,
 * sorted serialization of the resolved cap set — so the boot-time assertion can compare it against
 * the stamp the tooling emitted.
 *
 * <p><b>Verbatim port (load-bearing).</b> This MUST stay byte-for-byte identical to
 * {@code eu.exeris.tooling.codegen.core.capability.CompositionStamp#computeBinding} (exeris-tooling,
 * ADR-024 obligation 7): modules sorted by {@code qualifiedName} ascending, each emitting
 * {@code qualifiedName + "\n"} then, for every provided service formatted {@code service@version}
 * sorted ascending, {@code "  provides " + s + "\n"} (two-space prefix, trailing newline). Any
 * divergence — sort order, separators, the {@code "  provides "} prefix, the trailing {@code \n} —
 * silently breaks the hash and turns every deploy into a false binding-mismatch. The binding hashes
 * the cap set ONLY; {@code compositionVersion} is deliberately excluded (matched separately).
 *
 * <p>We re-implement rather than depend on {@code exeris-codegen-core} on purpose: the runtime must
 * not drag the build-time pipeline onto the SKU classpath. The shared spec is pinned by a golden
 * test vector ({@code CompositionBindingTest}).
 *
 * <p>Package-private: SKUs call {@link CompositionStampAssertion}, the only public entry; the binding
 * is an internal detail kept off the published API surface before the 1.0 freeze.
 */
final class CompositionBinding {

    private CompositionBinding() {
    }

    /**
     * Compute the content binding for {@code modules}. Robust to the manifest's array order — the
     * canonical form sorts by {@code qualifiedName} regardless — so a reordered (but otherwise
     * intact) manifest still binds identically. Callers must pass well-formed modules (non-null
     * {@code qualifiedName} and {@code service}/{@code version}); {@link CompositionStampAssertion}
     * validates that before calling.
     */
    static String compute(List<CapManifest.Module> modules) {
        List<CapManifest.Module> sorted = modules.stream()
                .sorted(Comparator.comparing(CapManifest.Module::qualifiedName))
                .toList();
        StringBuilder canonical = new StringBuilder();
        for (CapManifest.Module m : sorted) {
            canonical.append(m.qualifiedName()).append('\n');
            CapManifest.ModuleBody body = m.module();
            if (body != null && body.provides() != null) {
                body.provides().stream()
                        .map(p -> p.service() + '@' + p.version())
                        .sorted()
                        .forEach(s -> canonical.append("  provides ").append(s).append('\n'));
            }
        }
        return "sha256:" + sha256Hex(canonical.toString());
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e); // never on a conformant JRE
        }
    }
}
