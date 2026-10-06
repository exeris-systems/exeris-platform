# Policy: No Parallel Metamodel

Studio operates **exclusively** on the canonical `DomainMetadata` model defined in
`exeris-sdk-source-model`. This repository holds no second shape for a user's domain, in any
module, under any name.

## The rule

- **No domain shape in `exeris-studio-backend` or `exeris-platform-lsp`.** A record or class that
  could answer "what fields does this entity have" without asking the LSP is a parallel metamodel.
  `EntityDefinition`, `PropertyDefinition`, `RelationDefinition`, `Project` and `ProjectStatus` were
  deleted deliberately; the full list, and why, is in the backend's `package-info.java`.
  Reintroducing any of them, or a renamed equivalent, needs an ADR that overrides that deletion.
- **No domain-model REST endpoints in `exeris-studio-backend`.** Domain shape reaches every client
  over the LSP ([`lsp-wire-boundary.md`](lsp-wire-boundary.md)), never over backend HTTP.
- **"Just for the UI" and "just for the workspace tree" are not exceptions.** A frontend may
  **project** `DomainMetadata` into an in-memory view-model; it may not **persist** one.

## What the backend may hold

The test is *whose shape the data describes*, not how many classes there are.
`exeris-studio-backend` may hold as much of its **own** operational state as its job needs —
workspaces, sessions, deployment history, control-plane state — and may persist it. A `Workspace`
record is the backend's own state and is fine; an `EntityDefinition` beside it is the regression.

The backend is also not a runtime for user domains: no transaction coordination and no request path
for someone else's entities.

## Why

Two metamodels rot in opposite directions. Every surface that edits a domain — Studio, an IDE
plugin, the source on disk — must converge on one shape, and the only shape all three can share is
the one the SDK owns.

Review procedure: `exeris-platform-no-parallel-metamodel-review` (backend/LSP),
`exeris-platform-frontend-projection-review` (frontend).
