package eu.exeris.platform.composition;

/**
 * Thrown when the boot-time composition stamp assertion fails (ADR-024 obligation 8). The SKU
 * bootstrap call-site lets this propagate so startup aborts <em>before any cap enters
 * {@code initialize}</em>, with a message naming the specific divergence (missing/malformed stamp,
 * schema handshake, version drift, or binding mismatch) — an early, legible refusal instead of a
 * confusing mid-boot crash.
 */
public final class CompositionStampException extends RuntimeException {

    public CompositionStampException(String message) {
        super(message);
    }

    public CompositionStampException(String message, Throwable cause) {
        super(message, cause);
    }
}
