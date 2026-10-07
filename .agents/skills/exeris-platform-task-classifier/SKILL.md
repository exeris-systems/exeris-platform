---
name: exeris-platform-task-classifier
description: Triage step for any incoming exeris-platform task. Use FIRST — before routing or implementation — to classify an LSP server / protocol / build / docs request into task type (architecture / implementation / LSP protocol / docs / multi-domain), scope, severity, and primary contract risk, and to name the specialist agent that should own it. Invoke whenever the owner or the main risk is not yet obvious, or when scope may cross concerns.
---

# Exeris Platform Task Classifier

## Purpose
Classify incoming work before execution starts. Triage only — no implementation.

## Output Contract
Return exactly:
1. `task_class` (`ARCHITECTURE` | `IMPLEMENTATION` | `LSP_PROTOCOL` | `DOCS_ADR` | `MULTI_DOMAIN`)
2. `scope` (single-module | cross-module | cross-repo)
3. `severity` (low | medium | high | critical)
4. `primary_risk`
5. `recommended_primary_agent`

## Classification Heuristics
- `ARCHITECTURE`: placement, no-parallel-metamodel, open-core boundary — including any request for Studio, CMS or account functionality, which belongs to the closed product.
- `IMPLEMENTATION`: `exeris-platform-lsp` server code that keeps every wire shape; `exeris-platform-bom` / `exeris-platform-parent` and build plumbing.
- `LSP_PROTOCOL`: wire surface, `exeris/*` methods, transport parity, idempotent write-back.
- `DOCS_ADR`: README target architecture, ROADMAP milestones, cross-repo ADR registry.
- `MULTI_DOMAIN`: at least two classes above are first-order concerns.

## Guardrails
- Preserve no-parallel-metamodel discipline.
- Preserve LSP as the wire boundary for domain shape.
- Preserve open-core boundary.
- Preserve `exeris/*` namespace for custom LSP methods.
- If uncertain between two classes, emit `MULTI_DOMAIN` and state both dominant concerns.

## Completion Criteria
All five output fields present and each justified in 1-2 concise bullets.
