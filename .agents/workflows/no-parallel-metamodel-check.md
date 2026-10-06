---
name: no-parallel-metamodel-check
description: Refuse any reintroduction of a parallel metamodel in studio-backend or platform-lsp. Domain shape is `DomainMetadata` (from exeris-sdk-source-model), accessed via LSP — full stop.
argument-hint: PR diff or files in studio-backend / platform-lsp touched by the change
steps:
  - {skill: exeris-platform-no-parallel-metamodel-review}
  - {agent: exeris-platform-architect}
gates:
  - hook:guardrails-gate-on-stop
---

Audit this change for parallel-metamodel regression.

The contract:
- The `EntityDefinition`, `PropertyDefinition`, `RelationDefinition`, `Project` records were deliberately deleted during the repo split. The backend `package-info` documents this.
- Studio operates exclusively on canonical `DomainMetadata` (from `exeris-sdk-source-model`), accessed via LSP.
- Frontend view-model projection of `DomainMetadata` is OK — only **persisting** a parallel shape is forbidden.
- "Just for the UI" or "just for the workspace tree" is not a sufficient justification.

Change:
$ARGUMENTS

Please review:
1. Does any new class/record in `exeris-studio-backend` or `exeris-platform-lsp` carry domain shape (entity / property / relationship / action / field / validation)?
2. Does any new REST/HTTP endpoint in `exeris-studio-backend` return domain shape?
3. Is the change a projection (in-memory view for UI) or a persistence (stored, queried, mutated)?
4. If domain shape is genuinely needed, is the right move "read it over the LSP (`exeris/domains`, `exeris/domainDescribe`)" rather than "add a backend record"?
5. Minimal correction if a parallel metamodel is being reintroduced.

A genuine reintroduction requires a NEW ADR overriding the deletion recorded in the backend `package-info`. Do not silently allow it through a backend PR.
