---
name: exeris-platform-idempotent-writeback-review
description: Deep, evidence-gathering review that the same `MutationOp` applied twice converges to identical on-disk state. Finds the write-path diff itself, checks writer routing, import/line/whitespace stability, and the round-trip test, then issues a verdict. Use whenever the `exeris/applyMutation` path, the `exeris-sdk-source-model` writer integration, a new mutation kind, or import/whitespace/line-number-sensitive emission changes. (For a quick inline audit with a diff in hand, the `/idempotent-writeback-check` command is the lighter path.)
---

# Exeris Platform Idempotent Write-Back Review

## Purpose
Enforce the contract: applying the same `MutationOp` twice converges to identical on-disk state. No duplicated imports, no shifted line numbers, no whitespace drift between rounds.

This is what makes Studio + IDE plugins + on-disk sources safe to round-trip through each other.

## When to Use
- Any PR touching `exeris/applyMutation` handler or its downstream writer integration.
- Any PR touching import handling, line-number-sensitive insertion, or whitespace normalisation.
- Any PR that adds a new mutation kind.
- Any PR that bypasses the `exeris-sdk-source-model` writer with a direct file write.

## Required Inputs
- PR diff scoped to the LSP write path.
- Whether a round-trip test exists.
- The mutation kind under change.

## Evidence Gathering (do this first)
When no diff is handed in (autodispatch), find the change yourself:
- `git diff main...HEAD -- exeris-platform-lsp`
- Direct-write smell: grep added lines for `Files.write|Files.writeString|FileWriter|OutputStream|BufferedWriter` — any of these in the mutation path is a writer bypass to investigate.
- Writer routing: confirm the path delegates into the `exeris-sdk-source-model` writer rather than emitting text itself.
- Round-trip test: grep test sources for a twice-applied / byte-equality assertion covering the touched mutation kind; its absence is the key finding.

## Review Procedure
1. **Writer-routing audit** — confirm the change goes through `exeris-sdk-source-model` writer, not direct file writes. Direct writes are a hard reject unless the writer genuinely cannot express the change (and that's a writer-side gap, not a workaround opportunity).
2. **Import handling** — repeated mutation must not duplicate imports.
3. **Line-number stability** — repeated mutation against a reference snapshot must keep line numbers stable.
4. **Whitespace drift** — no second-round whitespace change. No "normalised on read" / "denormalised on write" cycles.
5. **Round-trip test** — require a test that applies the mutation twice and asserts byte-equality between the second-round output and the first.
6. **New mutation kinds** — require the round-trip test as part of the PR.
7. **Decision and report** — produce one of: `APPROVE`, `CONDITIONAL`, `REJECT`.

## Decision Logic
- **APPROVE**: Writer-routed, no import duplication, line-number stable, no whitespace drift, round-trip test present.
- **CONDITIONAL**: Missing round-trip test for an otherwise sound change — propose the test as the minimum addition.
- **REJECT**: Direct file write bypassing the writer, observable second-round drift, or new mutation kind without a round-trip test.

## Completion Criteria
- Writer routing confirmed.
- Import / line / whitespace audits done.
- Round-trip test present or proposed.
- Verdict and remediation recorded.

## Review Output Template
1. **Scope analysed** (writer path / mutation kinds touched)
2. **Writer routing** (through `exeris-sdk-source-model` or bypass)
3. **Import handling** (duplication risk)
4. **Line-number / whitespace audit** (drift risk)
5. **Round-trip test** (present / proposed)
6. **Verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
7. **Required actions** (precise and minimal)

## Non-Negotiable Rules
- Never approve a direct file write that bypasses the SDK writer.
- Never approve a new mutation kind without a round-trip test.
- Never accept "idempotency is a quality of life feature" — it is a contract.
