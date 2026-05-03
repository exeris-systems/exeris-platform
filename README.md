# Exeris Platform

The user-facing platform of Exeris: Studio (Angular shell + embedded React
editor), backend services, and the LSP server that powers bidirectional sync
between Studio, IDE plugins, and on-disk `@ExerisDomain` sources.

> **Status:** skeleton. Most modules are placeholders that scaffold the target
> architecture. Real implementation lands once `exeris-sdk-source-model` ships
> its JavaParser-based parser/writer.

## Architecture (target)

```
Studio (Angular + React)              IntelliJ Plugin           VS Code Extension
       │                                    │                          │
       └────────────── LSP (JSON-RPC) ──────┴──────────────────────────┘
                                  │
                       exeris-platform-lsp
                                  │
                       exeris-sdk-source-model
                       (parser + writer + AST)
                                  │
                       .java sources on disk
                       + exeris-metadata/*.json
```

**One canonical model, three editing surfaces, idempotent write-back.**

## Modules

| Module | Stack | Purpose |
|---|---|---|
| [`exeris-studio-backend`](exeris-studio-backend) | Java 26 | Workspace state and a thin REST/HTTP surface for the Studio frontend. **Holds no domain model** — all domain shape lives in `DomainMetadata` accessed via the LSP server. |
| [`exeris-studio-frontend`](exeris-studio-frontend) | Angular | Studio shell + embedded React editor. Communicates with the LSP server over WebSocket. |
| [`exeris-platform-lsp`](exeris-platform-lsp) | Java 26 | LSP server hosting `DomainMetadata`, exposing custom Exeris extensions (`exeris/entityModel`, `exeris/applyMutation`, `exeris/listCapabilities`). |
| `exeris-platform-bom` | — | Bill of materials. |
| `exeris-platform-parent` | — | Common Maven build configuration. |

## Open-core split

This repository is **open-source** (Apache-2.0). Premium features ship in a
separate, closed-source `exeris-platform-enterprise` repository:

- multi-environment promotion (dev → staging → prod)
- design-time RBAC and approval workflows
- audit dashboards
- multi-tenant org management
- enterprise-only Studio plugins

The split mirrors the kernel `community / enterprise` model and exists for the
same reason: the open core must be a fully usable Studio for any single team.

## Requirements

- JDK 26
- Maven 3.9+
- Node 24+ (`exeris-studio-frontend` — required by `package.json` engines field; Angular 21)
- Local install of `eu.exeris:exeris-sdk-*` and `eu.exeris.tooling:*` (run
  `mvn install` in those repos first — until SNAPSHOTs are published, a fresh
  clone of this repo alone cannot resolve those dependencies).

## Build

```bash
mvn clean install                                            # backend + LSP
cd exeris-studio-frontend && npm install && npm run build    # frontend
```

## Why no metamodel here

Earlier (Corelio-era) iterations of this repo hosted a parallel domain model
(`EntityDefinition`, `PropertyDefinition`, `RelationDefinition`) inside the
Studio backend. It was deliberately deleted during the repo split — having two
metamodels (Studio's vs `DomainMetadata`'s) would have rotted in opposite
directions. Studio now operates **exclusively** on the canonical model defined
in [`exeris-sdk-source-model`](../exeris-sdk).

## License

Apache-2.0. See [LICENSE](LICENSE).
