---
name: exeris-platform-contract-sweep
description: One-pass sweep of all four platform contracts for a broad change — no-parallel-metamodel, LSP wire surface, idempotent write-back, and open-core boundary — returning a single consolidated verdict. Use on a large PR, a pre-merge gate, or any change whose blast radius crosses the LSP's read path, write path and transports at once, when running the reviews individually would be slow or easy to forget one.
---

# Exeris Platform Contract Sweep

## Purpose
A meta-review that runs all four contract gates in one pass and consolidates them, so a broad change can't slip through by having one gate quietly skipped. This is breadth, not depth: it triages every contract against the change, then hands the genuinely-at-risk ones to their dedicated review skill for the full procedure. It does NOT replace the per-contract skills — it dispatches to them.

## When to Use
- A large PR touching more than one of: the `exeris/*` read path, the write path, the transports, the BOM / parent.
- A pre-merge or release gate where every contract must be confirmed, not just the obvious one.
- Any change where it would be easy to forget one of the four contracts.
- Explicit "review everything" / "full contract check" requests.

## The Four Contracts
1. **No parallel metamodel** — no domain-shaped record/class in `exeris-platform-lsp` beside `DomainMetadata`; wire projections are derived, never authoritative.
2. **LSP wire surface** — custom methods under `exeris/`, SDK-owned `MutationOp`/`MutationResult`, stdio↔WebSocket parity.
3. **Idempotent write-back** — same `MutationOp` twice → identical on-disk state, via the SDK writer.
4. **Open-core boundary** — only the LSP server and protocol live here; Studio, its workspace state, the CMS, accounts and premium features (multi-env, RBAC, approval, audit, multi-tenant) are closed and live elsewhere.

## Evidence Gathering (do this first)
- `git diff --name-only origin/main...HEAD` — map touched paths to the contract each guards.
- Per-contract relevance triage (cheap greps over added lines):
  - metamodel → `(record|class|interface|enum) ` in LSP Java carrying `name|field|property|relation|action`
  - LSP → method-string literals `"[a-zA-Z]+/[a-zA-Z]+"`; local `MutationOp|MutationResult|CapabilityDescriptor`
  - write-back → `Files.write|FileWriter|OutputStream` in the LSP write path; new mutation kinds
  - open-core → `role|rbac|permission|approval|audit|tenant|environment|promote|staging|account|login|cms`; any new top-level module or directory
- A contract with zero relevant hits is `NOT_TRIGGERED` — record it as such (silence ≠ skipped).

## Sweep Procedure
1. **Triage all four** — mark each `TRIGGERED` or `NOT_TRIGGERED` with the one-line evidence that decided it.
2. **Dispatch the triggered ones** — for each `TRIGGERED` contract, run its dedicated review skill for the full procedure and verdict:
   - `exeris-platform-no-parallel-metamodel-review`
   - `exeris-platform-lsp-protocol-review`
   - `exeris-platform-idempotent-writeback-review`
   - `exeris-platform-open-core-boundary-review`
3. **Consolidate** — collect the per-contract verdicts.
4. **Roll up** — the sweep verdict is the worst per-contract verdict (`REJECT` > `CONDITIONAL` > `APPROVE`).

## Decision Logic
- **APPROVE**: every triggered contract APPROVE; untriggered ones explicitly recorded.
- **CONDITIONAL**: at least one CONDITIONAL, none REJECT — list each condition with its owning contract.
- **REJECT**: any contract REJECT — name which, and why, first.

## Review Output Template
1. **Sweep scope** (modules / paths touched)
2. **Contract triage table** (each of the four: `TRIGGERED` / `NOT_TRIGGERED` + evidence)
3. **Per-contract verdicts** (only the triggered ones, each with its skill's findings)
4. **Roll-up verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
5. **Required actions** (grouped by contract, precise and minimal)

## Non-Negotiable Rules
- Never report a contract as passing — only as `APPROVE` (reviewed) or `NOT_TRIGGERED` (no relevant change). Don't conflate the two.
- Never let the sweep substitute for a triggered contract's full review — always dispatch to the dedicated skill.
- The roll-up is the worst child verdict; a single REJECT rejects the sweep.
