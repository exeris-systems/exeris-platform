---
name: exeris-platform-routing-planner
description: Turn a classified exeris-platform task into a risk-ordered execution plan — primary agent, ordered secondary handoffs, step sequence, must-pass validation gates, and the minimal next action. Use right after exeris-platform-task-classifier, or whenever a task spans modules (Studio / LSP / backend) and needs a plan before any code is written.
---

# Exeris Platform Routing Planner

## Purpose
Given a classified task (see `exeris-platform-task-classifier`), produce a minimal, risk-aware execution order across `exeris-platform-{router,architect,implementer,lsp-protocol,docs-adr}`.

## Output Contract
1. `primary_agent`
2. `secondary_handoffs` (ordered list with reason)
3. `execution_plan` (3–5 steps)
4. `validation_gates` (must-pass list)
5. `minimal_next_action`

## Routing Patterns
- `ARCHITECTURE` → `exeris-platform-architect` primary; `docs-adr` secondary when ADR impact.
- `BACKEND_IMPLEMENTATION` → `exeris-platform-implementer` primary; `architect` secondary if anything looks domain-shaped.
- `LSP_PROTOCOL` → `exeris-platform-lsp-protocol` primary; `implementer` secondary for actual code; `docs-adr` mandatory when method-shape changes.
- `FRONTEND_IMPLEMENTATION` → `exeris-platform-implementer` primary; `architect` secondary if frontend persists shape (instead of projecting).
- `DOCS_ADR` → `exeris-platform-docs-adr` primary; `architect` secondary if new ADR proposed.
- `CROSS_BUILD` → `exeris-platform-architect` primary; `implementer` secondary; coordination + smoke required.
- `MULTI_DOMAIN` → start with `architect`, list all dominant handoffs.

## Default Validation Gates
- No-parallel-metamodel scan (always; cheap to run, catches the worst regression).
- LSP wire-shape snapshot when `exeris/*` method changes.
- Idempotent write-back round-trip when writer surface changes.
- Open-core boundary scan when premium-shaped feature proposed.
- Frontend `npm run build` green when frontend changes.
- Backend `mvn install` green when backend / LSP changes.

## Completion Criteria
All five contract fields present and gates tied to the specific risk surface.
