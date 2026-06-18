---
name: exeris-platform-decision-doc-shape
description: Pick the right decision-document shape BEFORE drafting — Research vs RFC vs ADR — per the exeris-docs templates and the single ecosystem-wide ADR namespace. Use when asked to "write an ADR", "document this decision", "draft an RFC", or "write a research note" for the platform, especially when upstream measurement or option comparison is still missing. Enforces reserve-number-first discipline for true ADRs and steers premature ADRs back to Research/RFC.
---

# Exeris Platform Decision-Doc Shape

## Purpose
Three decision shapes exist in `exeris-docs/templates/` and they are NOT interchangeable. People reach for "ADR" by reflex even when the decision isn't made yet. This skill picks the right shape first, so the artefact is correct and lands in the right place — and so ADR numbers aren't burned on open questions. Owns the "is this a new ADR or just a README edit?" triage together with the `exeris-platform-docs-adr` agent.

## When to Use
- Any request to "write an ADR" / "document this decision" / "draft an RFC" / "write a research note" touching the platform (Studio / LSP / studio-backend / open-core boundary).
- Before reserving an ADR number — confirm the question is actually a decision-already-made.
- When a change to the LSP method surface, the open-core boundary, or the idempotent-write-back contract is proposed (these trigger ADRs per the repo CLAUDE.md).

## Shape Selection (decide first)
- **Research** (`RESEARCH-TEMPLATE.md`) — there is a falsifiable hypothesis and the answer needs measurement (JMH/JFR, a benchmark, a spike). Branch-scoped (`research/<slug>`), **no central registry**.
- **RFC** (`RFC-TEMPLATE.md`) — a multi-option strategic question with no decision yet. Filename `RFC-YYYY-MM-DD <Short Title>.md`, **no central registry**.
- **ADR** (`ADR-TEMPLATE.md`) — the decision is already made and needs recording. Filename `ADR-NNN <Short Title>.md`, **enters the registry**.

If upstream measurement or option comparison is missing, recommend Research or RFC and stop — do not draft an ADR for an open question.

## Procedure
1. **Classify the question** — decided / multi-option-open / needs-measurement. Map to ADR / RFC / Research.
2. **Scope check** — is this a platform-local concern or cross-repo? Cross-repo decisions and anything touching the shared `DomainMetadata` contract or LSP wire surface are ecosystem-level.
3. **Registry routing (ADR only)** — there is ONE ADR namespace, indexed in `~/exeris-systems/exeris-docs/adr-index.md` (that path is the standard sibling-repo workspace layout — adjust to wherever your `exeris-docs` clone lives; in a container/CI context the registry may be absent, which is a hard miss, not a silent skip). **Reserve the number there first**, then write content. Platform-local ADRs live in `docs/adr/`; cross-repo ADRs leave a `docs/adr/ADR-NNN.link.md` stub here.
4. **Business-vs-tech check** — legal / IP / financial / procurement decisions do NOT enter the tech registry; they belong to the private business registry. Refer to such policy descriptively, not by an internal id.
5. **Refactor-only check** — a refactor-only decision goes in the PR description / commit history, never the ADR registry.
6. **Recommend and (if ADR) reserve** — output shape, filename, location, and the next action.

## Output Template
1. **Question kind** (decided / open-multi-option / needs-measurement)
2. **Recommended shape** (Research / RFC / ADR) + one-line why
3. **Scope** (platform-local / cross-repo / business — out of tech registry)
4. **Filename + location** (per the template's naming rule)
5. **Registry action** (none / reserve `ADR-NNN` in `exeris-docs/adr-index.md` first + `.link.md` stubs)
6. **Next action** (single concrete step)

## Non-Negotiable Rules
- Never draft an ADR for a decision that isn't made — route to RFC or Research.
- Never write ADR content before the number is reserved in `exeris-docs/adr-index.md`.
- Never put a business (legal/IP/financial) decision into the tech ADR registry.
- A change to the LSP method surface, the open-core boundary, or the idempotent-write-back contract triggers an ADR — flag it rather than editing code silently.
