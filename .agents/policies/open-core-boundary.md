# Policy: Open-Core Boundary

This repository is Apache-2.0 open source, and it holds **only what must be open**: the LSP server
`exeris-platform-lsp` and its `exeris/*` protocol, plus `exeris-platform-bom` and
`exeris-platform-parent`. Every client that edits or reads an Exeris domain — an IDE plugin, an AI
agent, Studio — needs the same protocol, so the protocol is open.

Studio is Exeris' closed product, behind registration: the graph editor, its backend and workspace
state, the visual CMS, accounts, registration and login. It consumes the LSP over WebSocket like any
other client. It is not in this repository, and nothing here is built for it alone.

## The rule

- **Only the LSP server and its protocol live here.** Studio, its workspace state, the CMS and
  accounts are the closed product; code for them does not land here in any form — no module, no
  directory, no "temporary" copy.
- **Premium and enterprise features never land here:** multi-environment promotion, design-time
  RBAC, approval workflows, audit dashboards, multi-tenant organisation management, and anything
  else that exists to differentiate a paid product.
- **An extension point a closed consumer needs is open-core work; its implementations are not.** A
  seam in the LSP — for example a transport authentication hook on the WebSocket endpoint — belongs
  here when a closed consumer needs it, provided the open distribution stays fully usable without
  any implementation. The concrete authenticator, policy engine or account store lives in the
  closed product.
- **An extension point carries no premium default.** With no implementation present, the server
  behaves as an ordinary open LSP server; a seam that only works with a closed implementation is
  the implementation in disguise.
- **Visibility follows ADR-020** — `public` or `enterprise-private`. A document describing a feature
  with a closed counterpart states which.

## Why

The open half must be a complete, usable language server for any client, not a demo of a closed
one. Keeping the repository to the protocol is what lets IDE plugins, `exeris-ai-bridge` and Studio
rely on one surface without any of them depending on the others.

Moving a feature across the boundary, in either direction, triggers an ADR
([`adr-triggers.md`](adr-triggers.md)). Review procedure: `exeris-platform-open-core-boundary-review`.
