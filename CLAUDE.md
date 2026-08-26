# CLAUDE.md — exeris-platform

Guardrails for AI assistants working inside `~/exeris-systems/exeris-platform/`. Human-facing description lives in [`README.md`](README.md); this file captures the constraints, conventions, and "what to do when" rules a Claude Code session must respect.

## What this repo is — load-bearing facts

`exeris-platform` is the **user-facing platform** of Exeris: Studio (Angular shell + embedded React editor), the LSP server that powers bidirectional sync between Studio / IDE plugins / on-disk `@ExerisDomain` sources, and the thin studio-backend that exposes workspace state.

The single most load-bearing fact about this repo:

> **One canonical model, three editing surfaces, idempotent write-back.**

Studio operates **exclusively** on the canonical `DomainMetadata` model defined in [`exeris-sdk-source-model`](../exeris-sdk). The backend deliberately holds **no parallel metamodel**. Corelio-era `EntityDefinition` / `PropertyDefinition` / `RelationDefinition` / `Project` were deleted during the repo split — having two metamodels would have rotted in opposite directions.

**Status:** skeleton. Most modules are placeholders that scaffold the target architecture. Real implementation lands once `exeris-sdk-source-model` ships its JavaParser-based parser/writer (see `ROADMAP.md` 0.2.0+).

## Hard constraints (always enforce)

These are not negotiable.

1. **No parallel metamodel.** The studio-backend holds no domain shape. Anything that looks like a per-repo `EntityDefinition`, `Project`, or `Field` record is a regression — the canonical shape is `DomainMetadata` (in `exeris-sdk-source-model`), accessed via LSP. The deletion of Corelio-era types is documented in the backend `package-info` and is irreversible without an ADR.
2. **LSP is the wire boundary.** Studio frontend and IDE plugins do NOT call backend Java directly for model questions. They go through `exeris-platform-lsp` over JSON-RPC (stdio for IDE plugins, WebSocket for Studio frontend). The backend's REST/HTTP surface is for workspace state and project management ONLY — never for domain model questions.
3. **Idempotent write-back.** The LSP server is the only writer to on-disk sources. Mutations go through `exeris/applyMutation` → `exeris-sdk-source-model` writer. Applying the same mutation twice must converge to the same on-disk state — no duplicated imports, no shifted line numbers, no whitespace drift between rounds. This is a contract, not a quality of life feature.
4. **Open-core boundary.** This repo is Apache-2.0. Premium features ship in a separate, closed-source `exeris-platform-enterprise` repository (multi-environment promotion, design-time RBAC, approval workflows, audit dashboards, multi-tenant org management, enterprise-only Studio plugins). Do NOT inline premium-shaped features here — the boundary mirrors the kernel `community / enterprise` split.
5. **Custom Exeris LSP methods are namespaced under `exeris/`.** Standard LSP methods (`initialize`, `shutdown`, `textDocument/*`, `workspace/*`) follow the spec. Exeris-specific extensions (`exeris/entityModel`, `exeris/applyMutation`, `exeris/listCapabilities`, `exeris/diffPreview`) use the `exeris/` prefix — never invent unprefixed custom methods.

## Strong defaults (justified exceptions allowed)

1. **Studio frontend is Angular 21 with an embedded React editor.** Adding a third UI framework requires a justified ADR — the embedded-React choice was deliberate (editor surface) and not an open invitation. Node 24+ is required (per `package.json` engines field).
2. **Backend is thin.** `exeris-studio-backend` exposes workspace state and a small REST/HTTP surface for the Studio frontend. It is NOT a domain server, NOT a tx coordinator, NOT a persistence layer. Classes carrying domain shape are a smell; route the work through the LSP server.
3. **Tailwind via the `exeris-sdk-ui-kit` preset** (0.4.0 direction). Component library inherits the kit; don't ship a competing design system inside Studio.
4. **LSP transports**: stdio for IDE plugins, WebSocket for Studio frontend. Both speak the same JSON-RPC. Don't fork the method surface per transport.
5. **Sibling-repo orchestration** is currently in-job (clone + `mvn install` each upstream) per the 0.2.0 CI plan. A SNAPSHOT registry is a future option, not a near-term commitment.

## Scoped bans

- **No `EntityDefinition` / `PropertyDefinition` / `RelationDefinition` / `Project` records in `exeris-studio-backend`** — Corelio-era types are deliberately deleted; their reintroduction is a regression.
- **No domain-model REST endpoints in `exeris-studio-backend`** — domain shape comes over LSP, not over backend HTTP.
- **No direct file write-back from Studio frontend** — mutations flow Studio → LSP → `exeris-sdk-source-model` writer → disk. The frontend never edits `.java` files directly.
- **No premium / enterprise feature inlined into open-core modules** — multi-environment, RBAC, approval workflows, audit dashboards, multi-tenant — these belong in `exeris-platform-enterprise`.
- **No unprefixed custom LSP methods** — all Exeris extensions live under the `exeris/` namespace.
- **No second metamodel reintroduction** — even "just for the UI" or "just for the workspace tree" is forbidden. Project the canonical `DomainMetadata` to a view-model in the frontend if needed; don't persist a parallel shape.
- **No security/licence semantics in `exeris-platform-composition-runtime`** — the boot-time stamp assertion (ADR-024 obligation 8) is correctness/operability only; this repo is source-available and forkable, so it is NOT a tamper-proof gate. Don't add signature/attestation/licence-key checks (that's a sealed-enterprise concern with its own ADR), don't push any stamp/manifest/capability awareness into the kernel (obligation 9 — the kernel stays cap-blind), and don't re-validate the DAG (assert only). Keep `CompositionBinding` a byte-verbatim port of the tooling's `CompositionStamp#computeBinding` — it's pinned by a golden test vector; drift silently false-fails every deploy.

## Cross-repo dependencies

This repo sits at the top of the design-time stack:

- **Reads from:** `exeris-sdk` (`exeris-sdk-source-model` for parser/writer/AST; annotations for canonical shape) and `exeris-tooling` (DomainMetadata JSON producer). Local `mvn install` of both is required.
- **Reads from (runtime):** the running Exeris kernel via diagnostic surfaces is NOT this repo's job — that's `exeris-ai-bridge`'s `kernel:*` tool family.
- **Read by:** Studio users (Angular + React), IDE plugins (IntelliJ, VS Code) over LSP, and `exeris-platform-enterprise` extension points.
- **Premium counterpart:** `exeris-platform-enterprise` (closed-source, separate repo) consumes the SPI/extension points this repo exposes.

## ADR registry

Platform-specific ADRs (when they appear) live in `docs/adr/`. Per the top-level `~/exeris-systems/CLAUDE.md`, all `exeris-*` ADRs share the single `ADR-NNN` namespace owned by `~/exeris-systems/exeris-docs/adr-index.md`. Reserve numbers there before drafting.

ADRs to consult cross-repo:
- **ADR-006** (Spring-Free Kernel Boundary) — does not apply directly to this repo (no kernel runtime here), but the spirit (don't smuggle implementation details past a contract surface) is the same rule that protects `DomainMetadata` as the canonical shape.
- **ADR-020** (Visibility taxonomy: `public` / `enterprise-private`) — applies when documenting any feature with an enterprise counterpart.
- **ADR-025** (AI Agent Bridge) — `exeris-ai-bridge` consumes the LSP surface of this repo for its `lsp:*` tool family. LSP shape changes are visible to that ADR.

A change to LSP method surface (add / remove / rename `exeris/*` method, change wire shape of `MutationOp`), to the open-core boundary, or to the idempotent-write-back contract → **trigger an ADR**, don't just edit code.

## Build & test

```bash
mvn clean install                                             # backend + LSP (Java reactor)
mvn -pl exeris-platform-lsp -am test                          # LSP module + deps
cd exeris-studio-frontend && npm install && npm run build     # Angular frontend (separate npm build)
cd exeris-studio-frontend && npm run test                     # Angular unit tests
```

Local `mvn install` of `eu.exeris:exeris-sdk-*` and `eu.exeris.tooling:*` is required — until SNAPSHOTs are published, a fresh clone of this repo alone cannot resolve those deps.

## Documentation precedence

When sources disagree, the source-of-truth order is:

1. `docs/adr/*` — long-lived architectural intent (when populated; cross-repo ADRs in `~/exeris-systems/exeris-docs/adr/` take precedence on shared concerns).
2. `README.md` target architecture diagram + module table + "no metamodel" rationale.
3. `ROADMAP.md` milestone scope (0.1.0 scaffold → 1.0.0 GA = "Studio replaces the IDE for design-time work").
4. This file.
5. Top-level `~/exeris-systems/CLAUDE.md` for cross-repo routing.

Higher source wins; lower source is a doc-drift task.

## Language

English everywhere — source, comments, commit messages, PR titles, ADRs, this file. Conversation with the founder happens in Polish; persisted artefacts are English.
