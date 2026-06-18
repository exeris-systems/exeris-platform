---
name: exeris-platform-contract-sweep
description: One-pass sweep of all four platform contracts for a broad or multi-module change — no-parallel-metamodel, LSP wire surface, idempotent write-back, and open-core boundary — returning a single consolidated verdict. Use on a large PR, a pre-merge gate, or any change whose blast radius crosses studio-backend, exeris-platform-lsp, and exeris-studio-frontend at once, when running the four reviews individually would be slow or easy to forget one.
---

# Exeris Platform Contract Sweep

## Purpose
A meta-review that runs all four contract gates in one pass and consolidates them, so a broad change can't slip through by having one gate quietly skipped. This is breadth, not depth: it triages every contract against the change, then hands the genuinely-at-risk ones to their dedicated review skill for the full procedure. It does NOT replace the per-contract skills — it dispatches to them.

## When to Use
- A large or multi-module PR touching more than one of `exeris-studio-backend`, `exeris-platform-lsp`, `exeris-studio-frontend`.
- A pre-merge or release gate where every contract must be confirmed, not just the obvious one.
- Any change where it would be easy to forget one of the four contracts.
- Explicit "review everything" / "full contract check" requests.

## The Four Contracts
1. **No parallel metamodel** — no domain-shaped record/class or domain-serving HTTP endpoint in backend/LSP.
2. **LSP wire surface** — custom methods under `exeris/`, SDK-owned `MutationOp`/`MutationResult`, stdio↔WebSocket parity.
3. **Idempotent write-back** — same `MutationOp` twice → identical on-disk state, via the SDK writer.
4. **Open-core boundary** — premium-shape features (multi-env, RBAC, approval, audit, multi-tenant) stay in `exeris-platform-enterprise`.

## Evidence Gathering (do this first)
- `git diff --name-only main...HEAD` — map touched paths to the modules each contract guards.
- Per-contract relevance triage (cheap greps over added lines):
  - metamodel → `(record|class|interface|enum) ` in backend/LSP Java; `@GetMapping|@PostMapping|@RequestMapping`
  - LSP → method-string literals `"[a-zA-Z]+/[a-zA-Z]+"`; local `MutationOp|MutationResult|CapabilityDescriptor`
  - write-back → `Files.write|FileWriter|OutputStream` in the LSP write path; new mutation kinds
  - open-core → `role|rbac|permission|approval|audit|tenant|environment|promote|staging`
- A contract with zero relevant hits is `NOT_TRIGGERED` — record it as such (silence ≠ skipped).

## Sweep Procedure
1. **Triage all four** — mark each `TRIGGERED` or `NOT_TRIGGERED` with the one-line evidence that decided it.
2. **Dispatch the triggered ones** — for each `TRIGGERED` contract, run its dedicated review skill for the full procedure and verdict:
   - `exeris-platform-no-parallel-metamodel-review`
   - `exeris-platform-lsp-protocol-review`
   - `exeris-platform-idempotent-writeback-review`
   - `exeris-platform-open-core-boundary-review`
   - plus `exeris-platform-cross-build-validation` if the change spans Java + frontend.
3. **Consolidate** — collect the per-contract verdicts.
4. **Roll up** — the sweep verdict is the worst per-contract verdict (`REJECT` > `CONDITIONAL` > `APPROVE`).

## Decision Logic
- **APPROVE**: every triggered contract APPROVE; untriggered ones explicitly recorded.
- **CONDITIONAL**: at least one CONDITIONAL, none REJECT — list each condition with its owning contract.
- **REJECT**: any contract REJECT — name which, and why, first.

## Review Output Template
1. **Sweep scope** (modules / paths touched)
2. **Contract triage table** (each of the four + cross-build: `TRIGGERED` / `NOT_TRIGGERED` + evidence)
3. **Per-contract verdicts** (only the triggered ones, each with its skill's findings)
4. **Roll-up verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
5. **Required actions** (grouped by contract, precise and minimal)

## Non-Negotiable Rules
- Never report a contract as passing — only as `APPROVE` (reviewed) or `NOT_TRIGGERED` (no relevant change). Don't conflate the two.
- Never let the sweep substitute for a triggered contract's full review — always dispatch to the dedicated skill.
- The roll-up is the worst child verdict; a single REJECT rejects the sweep.
