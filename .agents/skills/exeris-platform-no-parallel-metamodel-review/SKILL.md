---
name: exeris-platform-no-parallel-metamodel-review
description: Deep, evidence-gathering review that detects parallel-metamodel regression in `exeris-platform-lsp` — finds the diff itself, classifies each new type as a derived projection, the LSP's own operational state, or a second domain shape, and issues an APPROVE / CONDITIONAL / REJECT verdict. Use on any change that adds a record/class to the LSP, caches or persists domain data, or is motivated by "avoid re-reading the model" / "a client needs a different shape". (For a quick inline audit with a diff in hand, the `/no-parallel-metamodel-check` command is the lighter path.)
---

# Exeris Platform No-Parallel-Metamodel Review

## Purpose
Enforce: domain shape lives in `DomainMetadata` (from `exeris-sdk-source-model`), and the LSP projects it onto the wire. The LSP server does NOT define, cache as authoritative, or persist a metamodel of its own.

`EntityDefinition` / `PropertyDefinition` / `RelationDefinition` / `Project` are absent from this repository on purpose. This skill is the gate that keeps them, and any renamed equivalent, out.

## When to Use
- Any PR adding a new record/class to `exeris-platform-lsp/src/main/java/`.
- Any PR that caches, indexes or persists domain data in the LSP.
- Any PR adding a wire projection type in `ProtocolProjections` or beside it.
- Any PR whose stated motivation is "avoid re-reading the model" or "a client needs a different shape".

## Required Inputs
- PR diff and stated motivation.
- New records / classes introduced.
- Whether each shape is derived from `DomainMetadata` per use, held as the LSP's own operational state, or held independently of the SDK model.

## Evidence Gathering (do this first)
When no diff is handed in (autodispatch), find the change yourself — never review from the PR text alone:
- `git diff origin/main...HEAD -- exeris-platform-lsp` (or `git diff --staged` only in a pre-commit-hook context)
- New types: `git diff origin/main...HEAD -- '*.java' | grep -E '^\+.*(record|class|interface|enum) '`
- Domain-shape smell on added lines: grep for `name|field|property|relation|action|validation|event|saga`
- Wrapper vs re-declaration: does the new type carry a `DomainMetadata` component, or re-declare its fields?
Ground every finding in a real `file:line`.

## Review Procedure
1. **Scan for domain-shaped types** — flag any new record/class carrying `name`, `field`, `property`, `relation`, `action`, `validation`, `event`, `saga`, or any structure that mirrors `DomainMetadata`.
2. **Distinguish projection, own state and second model** — a projection computed from `DomainMetadata` is OK; a wrapper that carries `DomainMetadata` plus the LSP's own bookkeeping (source path, digest, document version) is OK; a shape stored, queried or mutated independently of the SDK model is a regression.
3. **Check the motivation** — if the need is "a client needs entity X in another shape", the right answer is a projection of `DomainMetadata` over `exeris/*`; if the SDK lacks the facet, the facet is added upstream.
4. **ADR check** — introducing a domain-shaped type beside `DomainMetadata` requires a NEW ADR.
5. **Decision and report** — produce one of: `APPROVE`, `CONDITIONAL`, `REJECT`.

## Decision Logic
- **APPROVE**: New types are the LSP's own operational state or derived projections; no field of `DomainMetadata` is re-declared as an independent source.
- **CONDITIONAL**: A derived projection whose lifetime makes it look authoritative (e.g. cached past the source it came from) — recommend tying it to the source digest or computing it per use.
- **REJECT**: A domain shape held, persisted or served independently of `DomainMetadata`; assume regression intent if no ADR cites the change.

## Completion Criteria
- Every new record / class scanned.
- Projection / own state / second model classified.
- ADR requirement checked.
- Verdict and remediation provided.

## Review Output Template
1. **Scope analysed** (records / classes added)
2. **Domain-shape findings** (what mirrors `DomainMetadata`)
3. **Classification** (projection / own state / second model, per finding)
4. **Motivation audit** (projection or upstream SDK facet possible?)
5. **ADR requirement** (none / new ADR required)
6. **Verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
7. **Required actions** (precise and minimal)

## Non-Negotiable Rules
- Never approve a domain shape held independently of `DomainMetadata` without a new ADR.
- Never approve a local model filling a facet the SDK lacks — the facet goes upstream.
- Always redirect "a client needs entity X" to the LSP read methods (`exeris/domains`, `exeris/domainDescribe`).
