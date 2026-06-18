---
name: exeris-platform-no-parallel-metamodel-review
description: Deep, evidence-gathering review that detects parallel-metamodel regression in `exeris-studio-backend` or `exeris-platform-lsp` — finds the diff itself, classifies each new type as projection vs persistence, and issues an APPROVE / CONDITIONAL / REJECT verdict. Use on any change that adds a record/class to those modules, adds a backend REST/HTTP endpoint, or is motivated by "store workspace entities" / "track project structure". (For a quick inline audit with a diff in hand, the `/no-parallel-metamodel-check` command is the lighter path.)
---

# Exeris Platform No-Parallel-Metamodel Review

## Purpose
Enforce: domain shape lives in `DomainMetadata` (from `exeris-sdk-source-model`), accessed via LSP. Studio backend and LSP server do NOT define or persist their own metamodel.

The Corelio-era `EntityDefinition` / `PropertyDefinition` / `RelationDefinition` / `Project` records were deliberately deleted during the repo split. This skill is the gate that keeps them gone.

## When to Use
- Any PR adding a new record/class to `exeris-studio-backend/src/main/java/`.
- Any PR adding a new REST/HTTP endpoint to the backend.
- Any PR adding a new persistence-shaped type to `exeris-platform-lsp`.
- Any PR whose stated motivation is "store workspace entities" or "track project structure".

## Required Inputs
- PR diff and stated motivation.
- New records / classes / endpoints introduced.
- Whether the shape is persisted or in-memory-projected.

## Evidence Gathering (do this first)
When no diff is handed in (autodispatch), find the change yourself — never review from the PR text alone:
- `git diff --staged` and `git diff main...HEAD -- exeris-studio-backend exeris-platform-lsp`
- New types: `git diff main...HEAD -- '*.java' | grep -E '^\+.*(record|class|interface|enum) '`
- Domain-shape smell on added lines: grep for `name|field|property|relation|action|validation|event|saga`
- New endpoints: grep added lines for `@GetMapping|@PostMapping|@PutMapping|@RequestMapping|@Path`
Ground every finding in a real `file:line`.

## Review Procedure
1. **Scan for domain-shaped types** — flag any new record/class carrying `name`, `field`, `property`, `relation`, `action`, `validation`, `event`, `saga`, or any structure that mirrors `DomainMetadata`.
2. **Distinguish projection from persistence** — in-memory view-model for UI is OK; stored / queried / mutated shape is a regression.
3. **Scan REST/HTTP endpoints** — backend endpoints serving domain shape are a regression. Workspace state (paths, opened files, recent edits) is OK.
4. **Check the motivation** — if the need is "the UI needs entity X", the right answer is "ask LSP `exeris/entityModel`", not "store entities in backend".
5. **ADR check** — a genuine reintroduction requires a NEW ADR overriding the 0.1.0 deletion decision.
6. **Decision and report** — produce one of: `APPROVE`, `CONDITIONAL`, `REJECT`.

## Decision Logic
- **APPROVE**: New types are workspace-state-shaped (not domain-shaped); endpoints serve workspace state; or change is genuinely a frontend in-memory projection.
- **CONDITIONAL**: Domain-shaped projection that should be ephemeral but is structured as if persisted — recommend lifetime change.
- **REJECT**: Persisted domain shape in backend / LSP; backend HTTP serving domain shape; assume regression intent if no ADR cites the change.

## Completion Criteria
- Every new record / class / endpoint scanned.
- Projection vs persistence classified.
- ADR requirement checked.
- Verdict and remediation provided.

## Review Output Template
1. **Scope analysed** (records / classes / endpoints added)
2. **Domain-shape findings** (what mirrors `DomainMetadata`)
3. **Projection vs persistence** (per finding)
4. **Motivation audit** (LSP redirect possible?)
5. **ADR requirement** (none / new ADR required)
6. **Verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
7. **Required actions** (precise and minimal)

## Non-Negotiable Rules
- Never approve a persisted parallel metamodel without a new ADR.
- Never approve a backend HTTP endpoint that serves domain shape.
- Always redirect "the UI needs entity X" to LSP `exeris/entityModel`.
