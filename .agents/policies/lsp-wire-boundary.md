# Policy: LSP Is the Wire Boundary

`exeris-platform-lsp` is the only way a client asks a model question. Every consumer — IDE plugins,
`exeris-ai-bridge`, and the closed Studio product — speaks JSON-RPC to the LSP; none reads domain
shape any other way.

## The rule

- **Transports.** stdio for IDE plugins and `exeris-ai-bridge`, WebSocket for browser clients such
  as Studio. Both speak the **same** JSON-RPC method surface with the same wire shapes. Do not fork
  the method set per transport, and do not shape a method for one consumer.
- **Standard methods follow the spec.** `initialize`, `shutdown`, `textDocument/*`, `workspace/*`
  carry no Exeris-specific divergence.
- **Custom methods are namespaced under `exeris/`.** No unprefixed custom method, and no
  `workspace/exeris*` style hybrid.
- **The shipped surface is read off the code.** Method names come from the `@JsonRequest`
  annotations in
  `exeris-platform-lsp/src/main/java/eu/exeris/platform/lsp/ExerisProtocolExtensions.java`, never
  from a document. Today that is the read-only trio `exeris/domains`, `exeris/domainDescribe`,
  `exeris/actions`, the single writer `exeris/applyMutation`, and its read-only sibling
  `exeris/previewMutation`, which computes the same write and returns it as a diff.
- **The two halves have different consumers, deliberately.** The read trio and
  `exeris/previewMutation` back `exeris-ai-bridge`'s `lsp:*` tool family, with wire shapes pinned by
  ADR-025: renaming or reshaping any of them means amending ADR-025 and the bridge's tool
  definitions before merging. `exeris/applyMutation` is **not** in that slice — ADR-025 bars the
  bridge from the write path, and a preview never writes. Wiring the bridge to the writer is a
  boundary change that needs its own ADR.
- **The wire projects `DomainMetadata`; it does not define a model of its own**
  ([`no-parallel-metamodel.md`](no-parallel-metamodel.md)).

## The WebSocket transport

ADR-084 (WebSocket Provider SPI) is the transport the WebSocket endpoint is built on, and this
repository is its named consumer. It ships as `preview` at kernel 0.12, so anything bound to it pins
a contract declared to move. A missing transport is not solved with a third-party servlet or
WebSocket container.

Any add / remove / rename of an `exeris/*` method, or a change to a wire shape, triggers an ADR
([`adr-triggers.md`](adr-triggers.md)). Review procedure: `exeris-platform-lsp-protocol-review`.
