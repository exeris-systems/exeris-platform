/**
 * Exeris Platform composition runtime — boot-time validation-stamp assertion (ADR-024 obligation 8).
 *
 * <p><b>What this is.</b> A generic, once-tested library every SKU bootstrap invokes at startup,
 * before any cap enters {@code initialize}. It asserts the validation stamp the {@code exeris-tooling}
 * pipeline emits into {@code cap-manifest.json} (ADR-024 obligation 7) — presence, well-formedness,
 * content-binding-match, and (multi-manifest) version-match — and {@linkplain
 * eu.exeris.platform.composition.CompositionStampException fails fast} on drift. Entry point:
 * {@link eu.exeris.platform.composition.CompositionStampAssertion}.
 *
 * <p><b>What this is NOT.</b> Not a re-validation: no {@code @Requires}→{@code @Provides} DAG
 * re-resolution (that stays a build-time concern in the tooling). Not a security or licensing gate —
 * {@code exeris-platform} is source-available and forkable, so this is a correctness / operability
 * assertion that catches honest config drift early (ADR-024 amendment, 2026-06-17). A hardened,
 * signature-backed boot gate would be a sealed-enterprise-substrate concern with its own ADR.
 *
 * <p><b>Boundaries.</b> The open kernel stays cap-blind (ADR-024 obligation 9): this module depends
 * on no {@code exeris-kernel}, {@code exeris-tooling}, or {@code exeris-sdk} type, and no kernel
 * package calls into it. The content-binding algorithm is a verbatim port of the tooling's
 * {@code CompositionStamp#computeBinding}, pinned by a golden test vector.
 *
 * <p><b>Follow-up (not this slice).</b> Binding the four-phase cap lifecycle
 * ({@code initialize → ready → drain → terminate}) to the kernel bootstrap state machine is the
 * larger composition-runtime piece; it needs the kernel bootstrap contract and lands separately.
 */
package eu.exeris.platform.composition;
