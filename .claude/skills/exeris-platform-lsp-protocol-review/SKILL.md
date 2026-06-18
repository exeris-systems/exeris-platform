---
name: exeris-platform-lsp-protocol-review
description: Deep, evidence-gathering review of the LSP wire surface — finds the diff itself, audits namespacing, SDK-owned shapes, and transport parity, and issues a verdict. Use whenever a custom `exeris/*` method is added/removed/renamed, the wire shape of `MutationOp`/`MutationResult` changes, stdio vs WebSocket transport could diverge, or a method is consumed by `exeris-ai-bridge`. (For a quick inline audit with a diff in hand, the `/lsp-protocol-purity` command is the lighter path.)
---

# Exeris Platform LSP Protocol Review

## Purpose
Enforce the LSP wire-surface contract:
- Custom methods are namespaced under `exeris/`.
- `MutationOp` / `MutationResult` wire shape is owned by `exeris-sdk-source-model` (SDK 0.5.0).
- stdio (IDE plugins) and WebSocket (Studio frontend) speak the same method surface.
- Standard LSP methods follow the spec.

## When to Use
- Any PR adding/removing/renaming a custom `exeris/*` method.
- Any PR widening or narrowing wire-shape of an existing method.
- Any PR touching only one transport (stdio or WebSocket) — verify the other stays aligned.
- Any PR that proposes a wire-shape that should be SDK-owned (mutation, capability, entity model).

## Required Inputs
- PR diff scoped to `exeris-platform-lsp/`.
- Method surface before/after.
- Transport coverage (stdio / WebSocket / both).
- SDK alignment evidence (which SDK types this PR uses).

## Evidence Gathering (do this first)
When no diff is handed in (autodispatch), find the change yourself:
- `git diff main...HEAD -- exeris-platform-lsp`
- Custom methods touched: grep added/removed lines for method-string literals matching `"[a-zA-Z]+/[a-zA-Z]+"`; flag any not starting with `exeris/` or a standard LSP prefix (`textDocument/`, `workspace/`).
- SDK alignment: grep for local definitions of `MutationOp|MutationResult|CapabilityDescriptor|DomainMetadata` (a local `record`/`class` of these is a regression).
- Transport parity: confirm the stdio and WebSocket handler registrations changed together.

## Review Procedure
1. **Namespace audit** — every custom method MUST start with `exeris/`. Unprefixed custom methods are a hard reject.
2. **Standard-LSP compliance** — `initialize`, `shutdown`, `textDocument/*`, `workspace/*` follow the spec. No Exeris-specific divergence.
3. **SDK alignment** — `MutationOp` / `MutationResult` / `CapabilityDescriptor` / `DomainMetadata` MUST come from the SDK. Redefining locally is a regression.
4. **Transport parity** — stdio and WebSocket speak the same method surface. A change to one MUST be mirrored to the other (or the divergence MUST be explicit, justified, and ADR-backed).
5. **Cross-tool visibility** — if the change affects methods consumed by `exeris-ai-bridge` (`lsp:*` family per ADR-025), mark cross-tool-visibility and require ADR review.
6. **Backward compatibility** — at 0.x, breaks are acceptable IF documented; at 1.0+ they require an ADR and a migration story.
7. **Decision and report** — produce one of: `APPROVE`, `CONDITIONAL`, `REJECT`.

## Decision Logic
- **APPROVE**: All methods namespaced; SDK shapes used; transports aligned; cross-tool visibility flagged if applicable.
- **CONDITIONAL**: Documentation gap (missing entry in ROADMAP / cross-repo ADR) but wire shape is sound.
- **REJECT**: Unprefixed custom method, redefined SDK shape, transport divergence without rationale, unannounced breaking change at 1.0+.

## Completion Criteria
- Every method in change scanned for namespacing.
- SDK alignment confirmed.
- Transport parity confirmed.
- Cross-tool visibility classified.
- Verdict and remediation provided.

## Review Output Template
1. **Scope analysed** (methods touched, wire shapes changed)
2. **Namespace findings** (every custom method under `exeris/*`?)
3. **SDK alignment** (which types are SDK-owned, which are local)
4. **Transport parity** (stdio / WebSocket alignment)
5. **Cross-tool visibility** (consumed by `exeris-ai-bridge`?)
6. **Verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
7. **Required actions** (precise and minimal)

## Non-Negotiable Rules
- Never approve an unprefixed custom LSP method.
- Never approve a local redefinition of a shape that the SDK owns.
- Never approve a transport-specific method surface.
