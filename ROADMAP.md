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
- [x] **No parallel metamodel** — backend `package-info` documents the deliberate deletion of `EntityDefinition`/`PropertyDefinition`/`RelationDefinition`/`Project`. Studio operates exclusively on canonical `DomainMetadata` via LSP

## 0.2.0 — quality gates + LSP skeleton

> Goal: green CI from a fresh clone, LSP server speaks the LSP base protocol.

- [x] **CI** — `.github/workflows/build.yml` (clones SDK + tooling, installs both, then `mvn install` + `npm run build` in parallel jobs)
- [x] **`exeris-platform-lsp` skeleton** — LSP4J server, JSON-RPC over stdio, `initialize`/`shutdown` handlers (Exeris-specific methods followed in 0.3.0)
- [x] **Pre-publish POM metadata** — root POM declares `<url>`, `<organization>`, `<licenses>`, `<developers>`, `<scm>`, `<issueManagement>`. Required by Maven Central, and kept for it. `<distributionManagement>` named the Central Portal when this box was ticked; it now names GitHub Packages, because Central is not reachable for these coordinates before 1.0.0 and `mvn deploy` was aimed at a repository that would have rejected it
- [x] **Standalone LSP launcher** — `exeris-platform-lsp` attaches a shaded `-standalone` jar
      (`Main-Class: eu.exeris.platform.lsp.LspMain`) that runs as `java -jar` with no source tree
      and no Maven. `LauncherIT` starts that jar in a separate process on every build and drives a
      real LSP session through it, including two applies of the same `MutationOp`
- [x] **Publish pipeline** — `.github/workflows/publish.yml`. **A release is a tag and nothing
      else**: pushing `v<x.y.z>` builds, gates on `LauncherIT`, deploys to GitHub Packages and cuts
      the GitHub Release with the launcher attached. `workflow_dispatch` is a dry run that touches
      nothing remote. Deliberately not on push to `main` — a whole development line shares one
      `-SNAPSHOT` coordinate, so publishing per merge yields artifacts that share a coordinate and
      differ in content. Maven Central rides the same tag when it is wired (see 1.0.0)
- [x] **JDK floor at 25** — the reactor compiles to `release 25`, matching `exeris-kernel`,
      `exeris-sdk` v0.11.0 and `exeris-tooling` v0.8.0, so a consumer running the launcher beside
      `exeris-kernel-diagnostics-cli` has one JDK requirement rather than two. CI builds 25 and 26
- [ ] **Sibling-repo orchestration** — documented or solved (currently CI does in-job clone+install per repo; longer-term consider SNAPSHOT registry)

## 0.3.0 — LSP custom Exeris methods

> Goal: Studio + IDE plugins can query the canonical model and apply mutations through one wire surface.

> **Complete, and deliberately never tagged.** Everything below shipped, but no `v0.3.0` release
> was cut: there was no consumer waiting on a 0.3.0 artifact, and a release exists to be consumed,
> not to mark a checkbox. Its content ships inside the first real cut, `v0.4.0`. The trunk line
> therefore went `0.3.0-SNAPSHOT` → `0.4.0-SNAPSHOT` without a tag in between — which is the one
> case the cut procedure below does not otherwise cover.

- [x] `exeris/domains` — list the domain identities in the workspace
- [x] `exeris/domainDescribe` — full read-only view of one domain, projected from `DomainMetadata`
- [x] `exeris/actions` — enumerate actions with their owning domain
- [x] `exeris/applyMutation` — apply one `MutationOp` and return `MutationResult` (ADR-042)

> The read surface shipped as the `exeris/domains` + `exeris/domainDescribe` + `exeris/actions`
> trio rather than the single `exeris/entityModel` this milestone originally named. Two other
> planned names never shipped: `exeris/diffPreview`'s intent is now tracked as pre-apply preview
> at 0.8.0, and `exeris/listCapabilities` has neither a method nor a milestone — reopen it
> deliberately if Studio needs capability enumeration. Method names are authoritative in
> `ExerisProtocolExtensions.java`, not in this file.

## 0.4.0 — Studio frontend wired to LSP

> Goal: Studio shows entities and lets users do read-only inspection.

- [ ] WebSocket transport (Studio frontend) alongside stdio transport (IDE plugins) — moved here
      from 0.3.0: 0.3.0's goal is the method surface, and the transport exists to serve the very
      frontend this milestone wires up. Same JSON-RPC surface on both transports, never a fork
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
- [ ] Plugins surface the shipped `exeris/*` read methods (`exeris/domains`, `exeris/domainDescribe`, `exeris/actions`)
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
- [ ] Maven Central release (studio-backend + platform-lsp) — **0.6.0 at the earliest, and gated
      upstream**: `exeris-kernel` 0.12.0 (in development), `exeris-sdk` 0.12.0 and `exeris-tooling`
      0.9.0 have to be on Central before these coordinates can be, or a consumer resolves a
      published artifact whose dependencies are published nowhere. Tag-triggered when it lands, and
      disabled until then. It rides the same tag trigger as the Packages deploy — one cut, both
      registries. GitHub Packages carries releases in the meantime (0.2.0)
- [ ] npm registry release for `@exeris/studio-frontend` (and Studio Docker image)

---

## Versioning policy

- **0.x** — LSP custom methods may change in any release; plugin authors track main
- **1.x** — `exeris/*` LSP methods semver-stable; Studio internal APIs may still evolve

### Cutting a release

The reactor version names the release the current line will *become*, so trunk sits on
`<next>-SNAPSHOT` and `main` never carries a release version.

1. Finish the milestone's scope and tick its boxes here.
   A completed milestone does **not** have to be tagged — 0.3.0 was not. Tag when something
   downstream needs the artifact; otherwise let the content ride the next cut and move the trunk
   line straight on, as `0.3.0-SNAPSHOT` → `0.4.0-SNAPSHOT` did.
2. Push the tag: `git tag v<x.y.z> && git push origin v<x.y.z>`. `publish.yml` refuses a tag that
   does not match the trunk's line, that is not on `main`, or whose `LauncherIT` fails — so a
   mistyped tag costs nothing.
3. Enter the next line: `mvn versions:set -DnewVersion=<next>-SNAPSHOT -DgenerateBackupPoms=false`,
   bump `exeris-studio-frontend/package.json` to match, commit as
   `chore: enter <next> development`.

`workflow_dispatch` on `publish.yml` runs the whole path without publishing; use it before a cut
rather than discovering a broken release path with a version already spent.

## Tracking

- Per-milestone follow-ups: see open issues with `milestone: 0.X.0` label
- Round-1 review deferrals: [issue #2](https://github.com/exeris-systems/exeris-platform/issues/2)
