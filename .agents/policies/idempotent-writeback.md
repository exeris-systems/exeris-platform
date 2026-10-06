# Policy: Idempotent Write-Back

**One canonical model, three editing surfaces, idempotent write-back.** The LSP server is the only
writer to on-disk sources, and writing is a contract, not a quality-of-life feature.

## The rule

- **One write path.** Studio / IDE → `exeris/applyMutation` → the `exeris-sdk-source-model-io`
  writer → disk. The frontend never edits `.java` files directly, and no module writes source
  around the SDK writer.
- **The vocabulary is SDK-owned and frozen by ADR-042.** `MutationOp`, `MutationResult`, the
  conflict semantics and baseline-trust gating are defined in the SDK. Never redefine, wrap into a
  platform-side variant, or reshape them here.
- **Applying the same mutation twice converges to the same on-disk state.** No duplicated imports,
  no shifted line numbers, no whitespace drift between rounds.
- **A mutation-path change carries a round-trip test** that applies the operation twice and asserts
  the second round's output is byte-identical to the first.

## Why

Three surfaces edit the same source. If a write is not idempotent, the surfaces fight: each sync
produces a diff nobody made, and the on-disk source stops being something a human can review.

Changing this contract triggers an ADR ([`adr-triggers.md`](adr-triggers.md)). Review procedure:
`exeris-platform-idempotent-writeback-review`.
