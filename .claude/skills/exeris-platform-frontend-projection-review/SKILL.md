---
name: exeris-platform-frontend-projection-review
description: Ensure the Studio frontend PROJECTS the canonical DomainMetadata into an ephemeral view-model rather than PERSISTING a parallel shape, and never writes `.java` sources directly. Use on any exeris-studio-frontend (Angular + embedded React) change that adds a model/store/state type, a service that holds entity/field/relation shape, or any file-write path. Complements no-parallel-metamodel-review, which guards backend/LSP rather than the frontend.
---

# Exeris Platform Frontend Projection Review

## Purpose
The no-parallel-metamodel ban applies to the frontend too, but differently. The repo `CLAUDE.md` ("Scoped bans" → no second metamodel reintroduction) is the live policy and takes precedence; in short, it forbids reintroducing a parallel metamodel even "just for the UI" or "just for the workspace tree", and directs the frontend to project the canonical `DomainMetadata` into a view-model rather than persist a parallel shape. This skill is the frontend-side gate that `exeris-platform-no-parallel-metamodel-review` (backend/LSP-focused) does not cover. It also enforces that the frontend never writes `.java` files directly — mutations flow Studio → LSP → SDK writer → disk.

## When to Use
- Any `exeris-studio-frontend` change adding a model / interface / class / store / signal / state slice carrying entity, field, property, relation, action, or validation shape.
- Any new Angular service or React state container that holds domain shape rather than deriving it.
- Any frontend code path that looks like it writes source files or persists model data (localStorage, IndexedDB, a backend POST of domain shape).
- Any "cache the model on the client" or "keep the workspace tree in a store" change.

## The Distinction (the whole game)
- **Projection (OK)** — a view-model derived from `DomainMetadata` fetched over LSP, held only as long as the view needs it, rebuilt from the source of truth, never the authority.
- **Persistence (REGRESSION)** — a client-side shape that is stored, mutated in place, treated as the source of truth, or written back to disk/backend as domain data.

## Evidence Gathering (do this first)
- `git diff origin/main...HEAD -- exeris-studio-frontend`
- Domain-shape smell on added TS lines: grep for `interface |class |type ` near `field|property|relation|entity|action|validation|metadata`.
- Persistence smell: grep for `localStorage|sessionStorage|indexedDB|IDBDatabase|writeFile|fs\.|new File(`.
- Direct write-back smell: grep for any `.java` string construction, or a backend POST/PUT carrying domain shape rather than workspace state.
- Source-of-truth check: does the new type get **populated from** an `exeris/entityModel` (or similar) LSP response, or **authored locally**? Locally-authored domain shape is the regression.

## Review Procedure
1. **Classify each new shape** — projection (derived, ephemeral) vs persistence (stored, authoritative).
2. **Trace the origin** — confirm domain shape enters the frontend from an `exeris/*` LSP response, not from client-side authoring.
3. **Trace mutations** — any model edit must go out as an `exeris/applyMutation` over LSP, never a direct file write and never a backend domain POST.
4. **Lifetime check** — a projection that outlives its view (cached, persisted across sessions) is drifting toward a parallel metamodel; recommend rebuild-from-source.
5. **ADR check** — a genuine client-side authoritative store is a metamodel reintroduction and needs a new ADR.
6. **Decision and report** — `APPROVE` / `CONDITIONAL` / `REJECT`.

## Decision Logic
- **APPROVE**: derived, ephemeral view-models populated from LSP; mutations routed through `exeris/applyMutation`.
- **CONDITIONAL**: a projection structured as if authoritative or cached too long — recommend deriving/rebuilding it instead.
- **REJECT**: a persisted/authoritative client-side domain shape, a direct `.java` write from the frontend, or a backend POST of domain shape.

## Review Output Template
1. **Scope analysed** (frontend types / stores / services / write paths touched)
2. **Projection vs persistence** (per new shape)
3. **Origin trace** (LSP-sourced vs locally authored)
4. **Mutation routing** (`exeris/applyMutation` vs direct write / backend POST)
5. **Lifetime audit** (ephemeral vs cached/persisted)
6. **Verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
7. **Required actions** (precise and minimal)

## Non-Negotiable Rules
- Never approve a client-side authoritative domain store — "just for the UI" / "just for the workspace tree" is explicitly insufficient.
- Never approve a frontend code path that writes `.java` sources or POSTs domain shape to the backend.
- Domain shape enters the frontend only as a projection of `DomainMetadata` fetched over LSP.
