# Exeris Platform

The open-source design-time protocol of Exeris: the LSP server that keeps its clients and the
on-disk `@ExerisDomain` sources in sync, over the `exeris/*` surface, with idempotent write-back.

Its clients are IDE plugins over stdio, `exeris-ai-bridge` (the read surface and
`exeris/previewMutation`, never the writer — ADR-025), and Studio over WebSocket. Studio — the
graph editor, its workspace state, the visual CMS and accounts — is Exeris' own product behind
registration and is not in this repository. This repository holds only what must be open source.

> **Status:** `exeris-platform-lsp` depends on `exeris-sdk-source-model-io` (ADR-037), ships the
> read-only `exeris/*` trio, the writer `exeris/applyMutation` (ADR-042) and its write-free sibling
> `exeris/previewMutation`, and ships as a standalone launcher that runs with no source tree (see
> [Running the LSP server](#running-the-lsp-server)). The launcher serves the same surface over
> stdio and over WebSocket (`--websocket`).

## Architecture (target)

```
Studio (closed)      exeris-ai-bridge      IntelliJ Plugin      VS Code Extension
       │                     │                    │                    │
       └─────────────────────┴── LSP (JSON-RPC) ──┴────────────────────┘
                                  │
                       exeris-platform-lsp
                                  │
                       exeris-sdk-source-model-io
                       (JavaParser parser + writer)
                                  │
                       exeris-sdk-source-model
                       (canonical AST records)
                                  │
                       .java sources on disk
                       + exeris-metadata/*.json
```

**One canonical model, one protocol for every client, idempotent write-back.**

## Modules

| Module | Stack | Purpose |
|---|---|---|
| [`exeris-platform-lsp`](exeris-platform-lsp) | Java 25 | LSP server hosting `DomainMetadata`, exposing the custom Exeris extensions declared in [`ExerisProtocolExtensions`](exeris-platform-lsp/src/main/java/eu/exeris/platform/lsp/ExerisProtocolExtensions.java): read-only `exeris/domains`, `exeris/domainDescribe`, `exeris/actions`, the single writer `exeris/applyMutation`, and `exeris/previewMutation`, which returns the writer's change as a diff without writing. Also publishes a `-standalone` shaded launcher. |
| `exeris-platform-bom` | — | Bill of materials. |
| `exeris-platform-parent` | — | Common Maven build configuration. |

> **No composition runtime lives here.** `exeris-platform-composition-runtime` was retired
> (ADR-024 §Engineering Protocol P0.2): the 2026-06-25 "Composition Runtime Placement" amendment
> moved the boot conductor and stamp assertion into `exeris-sdk-composition-runtime`, with the
> manifest schema and the one canonical content-binding implementation in
> `exeris-sdk-composition-spec` (obligation 8b). The module here was the superseded parallel port,
> and had silently drifted — it emitted `service@null` where the producer emits `service@` for an
> unversioned provide, a divergence in the very hash that gates SKU boot, invisible because its
> golden fixture used only versioned provides. The golden vector survives as the cross-module
> conformance pin in the SDK's own `CompositionBindingTest`.
>
> This repo is the **deploy-time control plane** (obligation 8c) — it *consumes* the composition
> library for multi-SKU / mesh / multi-host composition; it does not host the in-jar boot runtime.
> Build-time composition (DAG validation, stamp emission) stays in `exeris-tooling`.

## Open-core split

This repository is **open-source** (Apache-2.0) and holds only what must be: the LSP server and its
protocol, which every client — open or closed — speaks. Studio, its workspace state, the visual
CMS and accounts are a closed product. Premium features never land here either:

- multi-environment promotion (dev → staging → prod)
- design-time RBAC and approval workflows
- audit dashboards
- multi-tenant org management

An extension point a closed client needs from the server — a transport authentication seam, for
example — is open-core work; its implementations are not.

## Requirements

- JDK 25+ (the reactor compiles to `release 25`; CI builds on 25 and 26)
- Maven 3.9+
- No credentials: the Exeris stack (kernel, SDK) resolves from Maven Central at the versions
  `exeris-platform-bom` pins.

## Build

```bash
mvn install
```

## Running the LSP server

Two ways, and which one you want depends on whether you have this repo checked out.

**From a source tree** — what a contributor uses:

```bash
mvn -pl exeris-platform-lsp exec:java
```

**From the launcher** — one self-contained jar, no source tree, no Maven, JDK 25 or newer:

```bash
java -jar exeris-platform-lsp-<version>-standalone.jar
```

By default it speaks JSON-RPC over stdio, which is what every LSP client ultimately consumes.
`mvn install` builds the launcher into `exeris-platform-lsp/target/`; released versions
are attached to the [GitHub release](https://github.com/exeris-systems/exeris-platform/releases)
and published to GitHub Packages under classifier `standalone`. Fetch it from Packages
with `-Dtransitive=false`: everything it runs on is inside the jar.

**WebSocket (Studio).** `--websocket` serves the same JSON-RPC surface over a WebSocket at
`ws://<host>:<port>/lsp`, one language server per connection, on the kernel's own WebSocket
server (ADR-084, `preview` at kernel 0.12):

| Option | Meaning |
|:--|:--|
| `--websocket` / `--stdio` | Pick the transport; stdio is the default |
| `--host <addr>` | Interface to bind; default `127.0.0.1` |
| `--port <n>` | Port to bind; default `5007`, which is where Studio connects by default |
| `--allowed-origin <origin>` | Browser origin admitted by the handshake; repeatable, and it **replaces** the default `http://localhost:4200` + `http://127.0.0.1:4200` rather than adding to it |
| `--allow-remote` | Required to bind a non-loopback `--host` |

The default bind is loopback only, and that is deliberate: `exeris/applyMutation` writes to the
workspace's sources and the endpoint authenticates nobody, so exposing it beyond the machine
must be an explicit `--allow-remote`. Invalid or inconsistent options exit with status 2.

```bash
java -jar exeris-platform-lsp-<version>-standalone.jar --websocket
```

`LauncherIT` runs that jar on every build. A launcher whose output is never executed is
how the neighbouring `exeris-kernel-diagnostics-cli` shipped a 0.11.0 that initialised
and then died on its first call.

## Why no metamodel here

The server holds no domain model of its own. It reads `@ExerisDomain` sources into the canonical
`DomainMetadata` defined in
[`exeris-sdk-source-model`](https://github.com/exeris-systems/exeris-sdk/tree/main/exeris-sdk-source-model),
projects it onto the wire, and writes changes back through the SDK writer. A second model beside
it would drift from the first, and every client would have to know which one it was talking to.

## License

Apache-2.0. See [LICENSE](LICENSE).
