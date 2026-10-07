---
title: Exeris Platform — Roadmap to 1.0.0 GA
type: roadmap
visibility: public
owning-repo: exeris-platform
status: active
last-verified: 2026-10-07
---

# Exeris Platform — Roadmap to 1.0.0 GA

This repository is the **open-source design-time protocol** of Exeris: the LSP server that keeps
its clients — IDE plugins, `exeris-ai-bridge`, and Studio, Exeris' closed product — in sync with
the on-disk `@ExerisDomain` sources. It holds only what must be open source; Studio, its workspace
state, the CMS and accounts are not tracked here. 1.0.0 GA means: the `exeris/*` surface is a
stable interop contract any client can target, and the standalone server is a released artifact.

This file tracks scope per milestone. Items marked `[ ]` are open; `[x]` shipped.

---

## 0.1.0 — scaffold (shipped)

- [x] Maven multi-module reactor (`bom`, `parent`, `platform-lsp`)
- [x] Open-core boundary documented: this repo is Apache-2.0 and holds only what must be open source
- [x] **No parallel metamodel** — the server operates exclusively on the canonical `DomainMetadata`

## 0.2.0 — quality gates + LSP skeleton

> Goal: green CI from a fresh clone, LSP server speaks the LSP base protocol.

- [x] **CI** — `.github/workflows/build.yml` (`mvn install`, every dependency resolved from public registries)
- [x] **`exeris-platform-lsp` skeleton** — LSP4J server, JSON-RPC over stdio, `initialize`/`shutdown` handlers (Exeris-specific methods followed in 0.3.0)
- [x] **Pre-publish POM metadata** — root POM declares `<url>`, `<organization>`, `<licenses>`, `<developers>`, `<scm>`, `<issueManagement>`. Required by Maven Central, and kept for it. `<distributionManagement>` named the Central Portal when this box was ticked; it now names GitHub Packages, because this repo's own Central release gate (see 1.0.0) is not met and `mvn deploy` was aimed at a repository that would have rejected it
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
- [x] **Sibling-repo orchestration** — solved: the kernel, SDK and tooling resolve from Maven Central at the versions `exeris-platform-bom` pins, so CI clones and installs no sibling repo

## 0.3.0 — LSP custom Exeris methods

> Goal: clients can query the canonical model and apply mutations through one wire surface.

> **Complete, and deliberately never tagged.** Everything below shipped, but no `v0.3.0` release
> was cut: there was no consumer waiting on a 0.3.0 artifact, and a release exists to be consumed,
> not to mark a checkbox. Its content ships inside the first real cut, `v0.5.0`. The trunk line
> therefore went `0.3.0-SNAPSHOT` → `0.4.0-SNAPSHOT` without a tag in between.

- [x] `exeris/domains` — list the domain identities in the workspace
- [x] `exeris/domainDescribe` — full read-only view of one domain, projected from `DomainMetadata`
- [x] `exeris/actions` — enumerate actions with their owning domain
- [x] `exeris/applyMutation` — apply one `MutationOp` and return `MutationResult` (ADR-042)

> The read surface shipped as the `exeris/domains` + `exeris/domainDescribe` + `exeris/actions`
> trio rather than the single `exeris/entityModel` this milestone originally named. Two other
> planned names never shipped: `exeris/diffPreview`'s intent shipped as `exeris/previewMutation`
> at 0.5.0, and `exeris/listCapabilities` has neither a method nor a milestone — reopen it
> deliberately if Studio needs capability enumeration. Method names are authoritative in
> `ExerisProtocolExtensions.java`, not in this file.

## 0.4.0 — WebSocket transport for browser clients

> Goal: a browser client reaches the same `exeris/*` surface IDE plugins reach over stdio.

> **Complete, and deliberately never tagged**, for the reason 0.3.0 was not: no consumer waited on
> a 0.4.0 artifact. The first consumer is `exeris-ai-bridge`, whose mutation preview needs
> `exeris/previewMutation`, so the first cut is `v0.5.0` and carries 0.3.0 and 0.4.0 with it. The
> trunk line moves from `0.4.0-SNAPSHOT` to `0.5.0-SNAPSHOT` without a tag in between.

- [x] WebSocket transport alongside stdio, on the kernel's WebSocket SPI (ADR-084): one language
      server per connection, the same JSON-RPC surface on both transports, never a fork
      (`TransportParityIT`)
- [x] Launcher flags `--stdio | --websocket`, `--host`, `--port`, `--allowed-origin`, and
      `--allow-remote`, which a non-loopback bind requires while `exeris/applyMutation` is
      unauthenticated
- [x] Write-back serialised per file across sessions, so a second op on the same file is judged
      against the bytes the first one wrote
- [x] Optional `relationships[]` on `exeris/domainDescribe` (`{ name, targetEntity, type? }`),
      pinned by ADR-025's 2026-10-06 amendment (see `docs/adr/ADR-025.link.md`)
- [x] The kernel and SDK resolve from Maven Central; the build needs no sibling checkout and no
      credential

## 0.5.0 — the mutation surface for live clients

> Goal: a client that edits from what it just read — Studio, an agent through `exeris-ai-bridge`,
> an IDE plugin — can apply and preview mutations over the LSP without a codegen baseline, and
> safely.

> Studio's editing experience — forms, optimistic updates, conflict resolution, undo/redo — is
> built on this surface inside Studio, Exeris' closed product, and is tracked there; this
> repository ships the server side every client needs.

- [x] `exeris/applyMutation` judges a request that carries a `concurrencyToken` and no
      `baselineJson` against the source the token names: the baseline is derived from those bytes
      under the per-file lock, a moved source is `STALE_DIGEST`, and build output is never read as
      a baseline (ADR-042's 2026-10-07 amendment)
- [x] `exeris/domainDescribe` carries the optional `sourceDigest` a client passes as that token
- [x] `exeris/previewMutation` — the read-only sibling of `exeris/applyMutation`: the same request,
      computed against the source as it is, answered with the `MutationResult` and a unified diff
      relative to the workspace root, and written nowhere. It is how `exeris-ai-bridge` reaches the
      canonical writer without writing (its `lsp-preview_mutation`; ADR-025's 2026-10-07 amendment)

## 0.6.0 — code-level round-trip

> Goal: edits below the model's granularity (an action body, a custom validator) round-trip through
> the server the way model edits do.

- [ ] LSP sync for code-level edits, through the SDK writer, idempotent like model edits

## 0.7.0 — IDE plugin support

> Goal: IntelliJ + VS Code plugins use the same LSP server, with Exeris extensions.

- [ ] IntelliJ plugin scaffold (`com.intellij.plugin.lsp` integration)
- [ ] VS Code extension scaffold
- [ ] Plugins surface the shipped `exeris/*` read methods (`exeris/domains`, `exeris/domainDescribe`, `exeris/actions`)
- [ ] Hot-reload of LSP server during plugin development

## 0.8.0 — multi-file transactions

> Goal: design-time refactors (rename entity, split aggregate) apply safely across many files.

- [ ] Multi-file mutation transactions
- [ ] Preview of a multi-file transaction; a single op is previewed by `exeris/previewMutation`
      since 0.5.0
- [ ] A verdict that names the files of a transaction that collided with concurrent edits

## 0.9.0 — clients beyond the local machine

> Goal: a client that is not on the server's machine — a hosted client among them — connects safely.

- [ ] Transport authentication seam: an SPI on the WebSocket handshake, with a single-user default.
      Identity-provider and authorization implementations live with the clients that need them
- [ ] WebSocket reconnection and session resumption

## 1.0.0 GA — stable protocol, released server

> Goal: the `exeris/*` surface is stable enough for third-party clients to target, and the server
> ships from Maven Central.

- [ ] LSP custom-method API frozen (`exeris/*` methods are semver-stable)
- [ ] `MIGRATION-0.x-to-1.0.md` for client authors
- [ ] Maven Central release of `exeris-platform-lsp` — **0.6.0 at the earliest**. The upstream half
      of the gate is met: `exeris-kernel` and `exeris-sdk` are on Central and this build resolves
      them from there. What remains is this repo's own: signing, sources/javadoc jars and the
      `-P release` profile copied from `exeris-kernel`. Tag-triggered when it lands, and disabled
      until then. It rides the same tag trigger as the Packages deploy — one cut, both registries.
      GitHub Packages carries releases in the meantime (0.2.0)

---

## Versioning policy

- **0.x** — LSP custom methods may change in any release; client authors track main
- **1.x** — `exeris/*` LSP methods semver-stable

### Cutting a release

The reactor version names the release the current line will *become*, so trunk sits on
`<next>-SNAPSHOT` and `main` never carries a release version.

1. Finish the milestone's scope and tick its boxes here.
   A completed milestone does **not** have to be tagged — 0.3.0 was not. Tag when something
   downstream needs the artifact; otherwise let the content ride the next cut and move the trunk
   line straight on, as 0.3.0 and 0.4.0 did.
2. Push the tag: `git tag v<x.y.z> && git push origin v<x.y.z>`. `publish.yml` refuses a tag that
   does not match the trunk's line, that is not on `main`, or whose `LauncherIT` fails — so a
   mistyped tag costs nothing.
3. Enter the next line: `mvn versions:set -DnewVersion=<next>-SNAPSHOT -DgenerateBackupPoms=false`,
   commit as `chore: enter <next> development`.

`workflow_dispatch` on `publish.yml` runs the whole path without publishing; use it before a cut
rather than discovering a broken release path with a version already spent.

## Tracking

- Per-milestone follow-ups: see open issues with `milestone: 0.X.0` label
- Round-1 review deferrals: [issue #2](https://github.com/exeris-systems/exeris-platform/issues/2)
