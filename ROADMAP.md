# Exeris Platform — Roadmap to 1.0.0 GA

The platform is the **user-facing Exeris experience**: Studio (Angular shell +
embedded React editor), backend services, and the LSP server that powers
bidirectional sync between Studio, IDE plugins, and on-disk `@ExerisDomain`
sources. 1.0.0 GA means: **Studio replaces the IDE for design-time work** on
real production projects, and the LSP is a stable interop surface third-party
tools can target.

This file tracks scope per milestone. Items marked `[ ]` are open; `[x]` shipped.

---

## 0.1.0 — scaffold (shipped)

- [x] Maven multi-module reactor (`bom`, `parent`, `studio-backend`, `platform-lsp`)
- [x] Angular 21 frontend workspace (`exeris-studio-frontend`, separate npm build)
- [x] Bootstrap stubs that compile (`main.ts`, `app.{component,config,routes}.ts`, `index.html`)
- [x] Open-core boundary documented: this repo Apache-2.0; premium features in `exeris-platform-enterprise`
- [x] **No parallel metamodel** — backend `package-info` documents the deliberate deletion of Corelio-era `EntityDefinition`/`PropertyDefinition`/`RelationDefinition`/`Project`. Studio operates exclusively on canonical `DomainMetadata` via LSP

## 0.2.0 — quality gates + LSP skeleton

> Goal: green CI from a fresh clone, LSP server speaks the LSP base protocol.

- [ ] **CI** — `.github/workflows/build.yml` (clones SDK + tooling, installs both, then `mvn install` + `npm run build` in parallel jobs)
- [ ] **`exeris-platform-lsp` skeleton** — LSP4J server, JSON-RPC over stdio, `initialize`/`shutdown` handlers, no Exeris-specific methods yet
- [x] **Pre-publish POM metadata** — root POM now declares `<url>`, `<organization>`, `<licenses>`, `<developers>`, `<scm>`, `<issueManagement>`, `<distributionManagement>` (Sonatype Central Portal). Required by Maven Central
- [ ] **Sibling-repo orchestration** — documented or solved (currently CI does in-job clone+install per repo; longer-term consider SNAPSHOT registry)

## 0.3.0 — LSP custom Exeris methods

> Goal: Studio + IDE plugins can query the canonical model and apply mutations through one wire surface.

- [ ] `exeris/entityModel` — return current `DomainMetadata` for a workspace path
- [ ] `exeris/applyMutation` — apply a `MutationOp` (from SDK 0.5.0) and return `MutationResult`
- [ ] `exeris/listCapabilities` — enumerate `@Capability` annotations in workspace (from SDK 0.4.0)
- [ ] `exeris/diffPreview` — pre-mutation diff against on-disk sources
- [ ] WebSocket transport (Studio frontend) alongside stdio transport (IDE plugins)

## 0.4.0 — Studio frontend wired to LSP

> Goal: Studio shows entities and lets users do read-only inspection.

- [ ] Workspace tree view (entities, capabilities, sagas)
- [ ] Entity detail view (fields, actions, relationships) — read-only
- [ ] Tailwind-based component library (using `exeris-sdk-ui-kit` preset)
- [ ] Routing for `/workspace/:path/entity/:name`

## 0.5.0 — Studio editing

> Goal: Studio applies safe mutations and Studio-changes round-trip back to disk.

- [ ] Inline edit forms for fields, actions, relationships
- [ ] Mutation-builder UI (translates user intent → `MutationOp`)
- [ ] Optimistic updates with conflict resolution UI
- [ ] Undo/redo at workspace scope

## 0.6.0 — embedded React editor for code

> Goal: Studio supports code-level edits where the model is too coarse (e.g. action body).

- [ ] React-based code editor (Monaco) embedded in Angular shell
- [ ] Per-element (action body, custom validator) edit panes
- [ ] LSP sync — code edits round-trip the same way as model edits

## 0.7.0 — IDE plugin support

> Goal: IntelliJ + VS Code plugins use the same LSP server, with Exeris extensions.

- [ ] IntelliJ plugin scaffold (`com.intellij.plugin.lsp` integration)
- [ ] VS Code extension scaffold
- [ ] Plugins surface `exeris/listCapabilities`, `exeris/diffPreview`
- [ ] Hot-reload of LSP server during plugin development

## 0.8.0 — multi-file change preview, undo/redo

> Goal: design-time refactors (rename entity, split aggregate) work safely across many files.

- [ ] Multi-file mutation transactions
- [ ] Pre-apply preview (Studio + IDE plugins)
- [ ] Workspace-scoped undo/redo
- [ ] Conflict UI when concurrent mutations collide

## 0.9.0 — multi-tenant + collaboration polish

> Goal: enterprise-feature parity — but the OSS surface should still be usable for single-team workflows.

- [ ] Workspace state persistence (PostgreSQL backing for studio-backend)
- [ ] WebSocket reconnection + state sync
- [ ] (enterprise: multi-tenant org management, design-time RBAC, audit dashboards — split to `exeris-platform-enterprise`)

## 1.0.0 GA — production Studio + stable LSP

> Goal: budgetHQ team works exclusively in Studio for design-time changes; LSP is stable enough for third-party plugins to target.

- [ ] LSP custom-method API frozen (`exeris/*` methods are semver-stable)
- [ ] Studio UX polish — performance, accessibility, keyboard navigation
- [ ] `MIGRATION-0.x-to-1.0.md` for plugin authors
- [ ] Maven Central release (studio-backend + platform-lsp)
- [ ] npm registry release for `@exeris/studio-frontend` (and Studio Docker image)

---

## Versioning policy

- **0.x** — LSP custom methods may change in any release; plugin authors track main
- **1.x** — `exeris/*` LSP methods semver-stable; Studio internal APIs may still evolve

## Tracking

- Per-milestone follow-ups: see open issues with `milestone: 0.X.0` label
- Round-1 review deferrals: [issue #2](https://github.com/exeris-systems/exeris-platform/issues/2)
