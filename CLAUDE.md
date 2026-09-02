# CLAUDE.md — exeris-platform

Guardrails for AI assistants working inside `~/exeris-systems/exeris-platform/`. Human-facing description lives in [`README.md`](README.md); this file captures the constraints, conventions, and "what to do when" rules a Claude Code session must respect.

## What this repo is — load-bearing facts

`exeris-platform` is the **user-facing platform** of Exeris: Studio (Angular shell + embedded React editor), the LSP server that powers bidirectional sync between Studio / IDE plugins / on-disk `@ExerisDomain` sources, and the thin studio-backend that exposes workspace state.

The single most load-bearing fact about this repo:

> **One canonical model, three editing surfaces, idempotent write-back.**

Studio operates **exclusively** on the canonical `DomainMetadata` model defined in [`exeris-sdk-source-model`](../exeris-sdk). The backend deliberately holds **no parallel metamodel**. Corelio-era `EntityDefinition` / `PropertyDefinition` / `RelationDefinition` / `Project` were deleted during the repo split — having two metamodels would have rotted in opposite directions.

**Status:** uneven, and the docs lag the code. `exeris-platform-lsp` is past scaffold — it already depends on `exeris-sdk-source-model-io` (ADR-037), ships the read-only `exeris/*` trio plus `exeris/applyMutation` (ADR-042, recorded as *realized in this repo*), and ships a standalone launcher (`-standalone` shaded jar) that runs with no source tree. The studio-backend and frontend are still largely placeholders. Status claims in `README.md` and `ROADMAP.md` were reconciled with the code in this same change, so they should agree today — but the code is what settles it, so verify against `exeris-platform-lsp` sources rather than assuming any doc kept up. For the split of coordinates: `exeris-sdk-source-model-io` holds the JavaParser parser/writer, `exeris-sdk-source-model` the canonical AST records (kept dependency-light).

## Hard constraints (always enforce)

These are not negotiable.

1. **No parallel metamodel.** The studio-backend holds no domain shape. Anything that looks like a per-repo `EntityDefinition`, `Project`, or `Field` record is a regression — the canonical shape is `DomainMetadata` (in `exeris-sdk-source-model`), accessed via LSP. The deletion of Corelio-era types is documented in the backend `package-info` and is irreversible without an ADR.
2. **LSP is the wire boundary.** Studio frontend and IDE plugins do NOT call backend Java directly for model questions. They go through `exeris-platform-lsp` over JSON-RPC (stdio for IDE plugins, WebSocket for Studio frontend). The backend's REST/HTTP surface is for workspace state and project management ONLY — never for domain model questions.
3. **Idempotent write-back.** The LSP server is the only writer to on-disk sources. Mutations go through `exeris/applyMutation` → the `exeris-sdk-source-model-io` writer; the `MutationOp` / `MutationResult` vocabulary and the conflict semantics are SDK-owned and frozen by ADR-042 — never redefine them platform-side. Applying the same mutation twice must converge to the same on-disk state — no duplicated imports, no shifted line numbers, no whitespace drift between rounds. This is a contract, not a quality of life feature.
4. **Open-core boundary.** This repo is Apache-2.0. Premium features ship in a separate, closed-source `exeris-platform-enterprise` repository (multi-environment promotion, design-time RBAC, approval workflows, audit dashboards, multi-tenant org management, enterprise-only Studio plugins). Do NOT inline premium-shaped features here — the boundary mirrors the kernel `community / enterprise` split.
5. **Custom Exeris LSP methods are namespaced under `exeris/`.** Standard LSP methods (`initialize`, `shutdown`, `textDocument/*`, `workspace/*`) follow the spec; Exeris-specific extensions use the `exeris/` prefix — never invent unprefixed custom methods. The shipped surface is the read-only `exeris/domains`, `exeris/domainDescribe`, `exeris/actions` and the single writer `exeris/applyMutation` (ADR-042). **Read the method names off the `@JsonRequest` annotations in `exeris-platform-lsp/src/main/java/eu/exeris/platform/lsp/ExerisProtocolExtensions.java`, not off a doc** — an earlier draft of this file took them from `ROADMAP.md`'s then-unchecked 0.3.0 boxes and named three methods (`exeris/entityModel`, `exeris/listCapabilities`, `exeris/diffPreview`) that never shipped under those names. `ROADMAP.md` 0.3.0 now records that history in a note. The two halves of the surface have **different** consumers, and the split is deliberate: the read trio backs `exeris-ai-bridge`'s `lsp:*` tool family with wire shapes pinned by ADR-025's 2026-06-24 amendment, so renaming or reshaping any of the three means amending ADR-025 and the bridge tool definitions before merging. `exeris/applyMutation` is explicitly **not** in that slice — ADR-025 reserves it in the method surface but bars the bridge from consuming it, keeping the agent surface off the write path. Do not "finish" the bridge by wiring it to the writer; that is a boundary change needing its own ADR.

## Strong defaults (justified exceptions allowed)

1. **Studio frontend is Angular 21 with an embedded React editor.** Adding a third UI framework requires a justified ADR — the embedded-React choice was deliberate (editor surface) and not an open invitation. Node 24+ is required (per `package.json` engines field).
2. **Backend is thin.** `exeris-studio-backend` exposes workspace state and a small REST/HTTP surface for the Studio frontend. It is NOT a domain server, NOT a tx coordinator, NOT a persistence layer. Classes carrying domain shape are a smell; route the work through the LSP server.
3. **Tailwind via the `exeris-sdk-ui-kit` preset** (0.4.0 direction). Component library inherits the kit; don't ship a competing design system inside Studio.
4. **LSP transports**: stdio for IDE plugins, WebSocket for Studio frontend. Both speak the same JSON-RPC. Don't fork the method surface per transport.
5. **Sibling-repo orchestration** is currently in-job (clone + `mvn install` each upstream) per the 0.2.0 CI plan. A SNAPSHOT registry is a future option, not a near-term commitment.
6. **The standalone launcher is a consumer contract, and `LauncherIT` is what makes it one.** `exeris-platform-lsp` attaches a shaded `-standalone` jar; `LauncherIT` starts *that jar* as a separate `java -jar` process and drives a real LSP session through it. Do not delete it, do not move it to surefire (it needs `package` to have run), and do not "fix" a red build by skipping it. It exists because the neighbouring `exeris-kernel-diagnostics-cli` published a 0.11.0 shaded jar that initialised and then died on its first call — a build that never runs the artifact it ships cannot see that class of bug. Shading is also where the SDK's Jackson-3 polymorphic `MutationOp` / `MutationResult` vocabulary is most likely to break, and the IT covers exactly that path.
7. **JDK floor is 25** (`maven.compiler.release` in the root POM), matching `exeris-kernel`, `exeris-sdk` v0.10.0 and `exeris-tooling` v0.7.0. This is a downstream constraint, not a style preference: `exeris-ai-bridge` runs our launcher beside `exeris-kernel-diagnostics-cli`, and emitting class-file 70 here would give a consumer two different JDK floors. Raise it only when the kernel does.
8. **`mvn deploy` goes to GitHub Packages, not Maven Central.** Central is a 1.0.0 milestone and needs signing, sources/javadoc jars and a readiness gate that this repo does not have — copy `exeris-kernel`'s `release` profile when that lands. Until then a publish is `.github/workflows/publish.yml`, which defaults to a dry run.

## Scoped bans

- **No `EntityDefinition` / `PropertyDefinition` / `RelationDefinition` / `Project` / `ProjectStatus` records in `exeris-studio-backend`** — the full list of deliberately-deleted Corelio-era types is in that module's `package-info.java`; their reintroduction is a regression.
- **No domain-model REST endpoints in `exeris-studio-backend`** — domain shape comes over LSP, not over backend HTTP.
- **No direct file write-back from Studio frontend** — mutations flow Studio → LSP → `exeris-sdk-source-model-io` writer → disk. The frontend never edits `.java` files directly.
- **No premium / enterprise feature inlined into open-core modules** — multi-environment, RBAC, approval workflows, audit dashboards, multi-tenant — these belong in `exeris-platform-enterprise`.
- **No unprefixed custom LSP methods** — all Exeris extensions live under the `exeris/` namespace.
- **No second metamodel reintroduction** — even "just for the UI" or "just for the workspace tree" is forbidden. Project the canonical `DomainMetadata` to a view-model in the frontend if needed; don't persist a parallel shape.
- **No composition runtime in this repo** — `exeris-platform-composition-runtime` was retired (ADR-024 P0.2, per the 2026-06-25 "Composition Runtime Placement" amendment) and removed from the reactor and the BOM. The boot conductor and stamp assertion live in `exeris-sdk-composition-runtime`; schema and content binding live in `exeris-sdk-composition-spec`. This repo is the **deploy-time control plane** (obligation 8c) that *consumes* those SDK modules for design/deploy-time validation and preview — reintroducing in-jar composition machinery into a Studio/LSP/backend module is a regression. In particular, do not port `CompositionBinding` back here: the retired port silently dropped unversioned-provide normalization (`service@null` vs `service@`) in the very hash that gates SKU boot, and the golden vector now pins the SDK's own `CompositionBindingTest`. Two ADR-024 obligations still bind any composition work this repo touches: the stamp assertion is a correctness/operability check and never a signature/attestation/licence gate (sealed-enterprise concern with its own ADR), and no stamp/manifest/capability awareness may be pushed into the kernel (obligation 9 — the kernel stays cap-blind).

## Cross-repo dependencies

This repo sits at the top of the design-time stack:

- **Reads from:** `exeris-sdk` (`exeris-sdk-source-model-io` for the parser/writer, `exeris-sdk-source-model` for the canonical AST records, annotations for canonical shape) and `exeris-tooling` (DomainMetadata JSON producer). Local `mvn install` of both is required.
- **Reads from (runtime):** the running Exeris kernel via diagnostic surfaces is NOT this repo's job — that's `exeris-ai-bridge`'s `kernel:*` tool family.
- **Read by:** Studio users (Angular + React), IDE plugins (IntelliJ, VS Code) over LSP, `exeris-ai-bridge` over the read-only `exeris/*` slice (ADR-025), and `exeris-platform-enterprise` extension points.
- **Premium counterpart:** `exeris-platform-enterprise` (closed-source, separate repo) consumes the SPI/extension points this repo exposes.

## ADR registry

Platform-specific ADRs (when they appear) live in `docs/adr/`. Per the top-level `~/exeris-systems/CLAUDE.md`, all `exeris-*` ADRs share the single `ADR-NNN` namespace owned by `~/exeris-systems/exeris-docs/adr-index.md`. Reserve numbers there before drafting.

ADRs to consult cross-repo:
- **ADR-006** (Spring-Free Kernel Boundary) — does not apply directly to this repo (no kernel runtime here), but the spirit (don't smuggle implementation details past a contract surface) is the same rule that protects `DomainMetadata` as the canonical shape.
- **ADR-020** (Visibility taxonomy: `public` / `enterprise-private`) — applies when documenting any feature with an enterprise counterpart.
- **ADR-024** (Capability Composition Model) — fixes this repo's role as the deploy-time control plane and keeps composition runtime code out of it. See the scoped ban above.
- **ADR-037** (`exeris-sdk-source-model-io`) — the parser/writer coordinate the LSP must depend on; the AST records stay in `exeris-sdk-source-model`.
- **ADR-042** (Bidirectional mutation surface) — the ADR most directly load-bearing for hard constraint 3: it freezes `exeris/applyMutation`'s wire shape, the `MutationOp` vocabulary, conflict detection and baseline-trust gating. Already realized here; consult before touching the write-back path.
- **ADR-025** (AI Agent Bridge) — `exeris-ai-bridge`'s `lsp:*` tool family consumes the **read-only** `exeris/*` slice of this repo (and only that slice — the bridge is kept off `exeris/applyMutation` by design). Shape changes to those three methods are visible to that ADR.

A change to LSP method surface (add / remove / rename `exeris/*` method, change wire shape of `MutationOp`), to the open-core boundary, or to the idempotent-write-back contract → **trigger an ADR**, don't just edit code.

## Build & test

```bash
mvn clean install                                             # backend + LSP, incl. LauncherIT
mvn -pl exeris-platform-lsp -am test                          # unit tests only (no LauncherIT)
mvn -pl exeris-platform-lsp verify                            # + packages and runs the launcher
cd exeris-studio-frontend && npm install && npm run build     # Angular frontend (separate npm build)
cd exeris-studio-frontend && npm run test                     # Angular unit tests
```

`LauncherIT` needs the shaded jar, so it runs at `verify` (failsafe) — `mvn test` alone will not exercise it. The jar lands at `exeris-platform-lsp/target/exeris-platform-lsp-<version>-standalone.jar`.

Local `mvn install` of `eu.exeris:exeris-sdk-*` and `eu.exeris.tooling:*` is required — until SNAPSHOTs are published, a fresh clone of this repo alone cannot resolve those deps.

**`clean` matters more here than usual.** `SchemaVersion.CURRENT` is a `static final String`, so javac *inlines its value* into every class that reads it — `ApplyMutationTest` and `LauncherIT` both do. Rebuild the SDK at a different version without rebuilding this repo's tests and the stale test classes keep asserting the old literal against a jar that now says something else; the baseline reads as schema skew and three `applyMutation` tests fail with `NO_BASELINE`. It looks exactly like a write-back regression and is not one. If those tests fail locally but CI is green, run `mvn clean test` before believing them.

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
