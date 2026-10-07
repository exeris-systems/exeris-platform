# Policy: No Parallel Metamodel

The LSP server operates **exclusively** on the canonical `DomainMetadata` model defined in
`exeris-sdk-source-model`. It **projects** that model onto the wire; it holds no second shape for a
user's domain, in any module, under any name.

## The rule

- **No domain shape in `exeris-platform-lsp`.** A record or class that could answer "what fields
  does this entity have" without going through `DomainMetadata` is a parallel metamodel.
  `EntityDefinition`, `PropertyDefinition`, `RelationDefinition`, `Project` and `ProjectStatus` are
  absent from this repository on purpose; introducing any of them, or a renamed equivalent, needs an
  ADR.
- **Wire projections are derived, never authoritative.** `ProtocolProjections` maps
  `DomainMetadata` to JSON; a projection type that is cached, persisted or mutated
  independently of the SDK model is the regression, whatever it is called.
- **Domain shape is never redefined here.** When the wire needs a facet the SDK does not carry, the
  facet is added to the SDK and projected here — not modelled locally to fill the gap.
- **"Just for performance" and "just for one client" are not exceptions.** A client that wants a
  cheaper or differently shaped view asks the LSP for it; the LSP still answers from
  `DomainMetadata`.

## What the LSP may hold

The test is *whose shape the data describes*, not how many classes there are. The LSP may hold its
own operational state — per-connection sessions, open documents and their versions, an index that
wraps `DomainMetadata` with a source path and digest (`WorkspaceIndex.IndexedDomain`). A wrapper
that carries a `DomainMetadata` is fine; a record that re-declares its fields beside it is the
regression.

## Why

Two metamodels rot in opposite directions. Every surface that edits a domain — an IDE plugin,
Studio, an AI agent, the source on disk — must converge on one shape, and the only shape all of them
can share is the one the SDK owns.

Review procedure: `exeris-platform-no-parallel-metamodel-review`.
