---
name: no-parallel-metamodel-check
description: Refuse any parallel metamodel in exeris-platform-lsp. Domain shape is `DomainMetadata` (from exeris-sdk-source-model), projected onto the wire by the LSP — full stop.
disable-model-invocation: true
---

<!-- DO NOT EDIT. Generated from .agents/workflows/no-parallel-metamodel-check.md by agents_render.py
     (exeris-systems/exeris-agents; agents-md-schema.md rule 7). Edit the source. -->
Audit this change for parallel-metamodel regression.

The contract:
- `EntityDefinition`, `PropertyDefinition`, `RelationDefinition`, `Project` and any renamed equivalent are absent from this repository on purpose.
- The LSP operates exclusively on canonical `DomainMetadata` (from `exeris-sdk-source-model`) and projects it onto the wire.
- A projection derived from `DomainMetadata` is OK, and so is the LSP's own operational state (sessions, open documents, an index that wraps `DomainMetadata`) — only an independent, authoritative second shape is forbidden.
- "Just for performance" or "just for one client" is not a sufficient justification.

Change:
$ARGUMENTS

Please review:
1. Does any new class/record in `exeris-platform-lsp` carry domain shape (entity / property / relationship / action / field / validation)?
2. Does it wrap `DomainMetadata`, or re-declare its fields?
3. Is it a derived projection, the LSP's own state, or a shape held (cached, persisted, mutated) independently of the SDK model?
4. If a facet is genuinely missing, is the right move "add it to the SDK and project it" rather than "model it here"?
5. Minimal correction if a parallel metamodel is being introduced.

Introducing a domain-shaped type beside `DomainMetadata` requires a NEW ADR. Do not silently allow it through an LSP PR.
