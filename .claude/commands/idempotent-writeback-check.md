---
description: Verify that an `exeris/applyMutation` change preserves idempotent write-back — applying the same mutation twice converges to identical on-disk state.
argument-hint: LSP writer / `exeris/applyMutation` diff or files in the write path
---

Audit this change for idempotent write-back.

Contract:
- The LSP server is the only writer to on-disk sources.
- Mutations flow Studio/IDE → LSP `exeris/applyMutation` → `exeris-sdk-source-model` writer → disk.
- Applying the same `MutationOp` twice MUST converge to identical on-disk state:
  - No duplicated imports.
  - No shifted line numbers (relative to a reference snapshot).
  - No whitespace drift between rounds.
- Idempotency is a contract, not a quality of life feature.

Change:
$ARGUMENTS

Please review:
1. Does the change introduce any path where the same `MutationOp` applied twice would produce different on-disk text?
2. Does it touch import handling, line-number-sensitive insertions, or whitespace?
3. Is there a round-trip test that applies the mutation twice and asserts byte-equality of the second-round output vs the first?
4. Does the writer go through `exeris-sdk-source-model`, or does it bypass with a direct write?
5. Minimal correction if idempotency is at risk.

If the change is non-trivial and lacks a round-trip test, the minimum addition is exactly that test — propose it as part of the PR.
