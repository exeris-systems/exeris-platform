# Policy: LSP Is the Wire Boundary

`exeris-platform-lsp` is the only way a client asks a model question. Studio frontend and IDE
plugins do **not** call backend Java directly for domain shape; they speak JSON-RPC to the LSP.

## The rule

- **Transports.** stdio for IDE plugins, WebSocket for the Studio frontend. Both speak the **same**
  JSON-RPC method surface with the same wire shapes. Do not fork the method set per transport.
- **Standard methods follow the spec.** `initialize`, `shutdown`, `textDocument/*`, `workspace/*`
  carry no Exeris-specific divergence.
- **Custom methods are namespaced under `exeris/`.** No unprefixed custom method, and no
  `workspace/exeris*` style hybrid.
- **The shipped surface is read off the code.** Method names come from the `@JsonRequest`
  annotations in
  `exeris-platform-lsp/src/main/java/eu/exeris/platform/lsp/ExerisProtocolExtensions.java`, never
  from a document. Today that is the read-only trio `exeris/domains`, `exeris/domainDescribe`,
  `exeris/actions`, and the single writer `exeris/applyMutation`.
- **The two halves have different consumers, deliberately.** The read trio backs
  `exeris-ai-bridge`'s `lsp:*` tool family, with wire shapes pinned by ADR-025: renaming or
  reshaping any of the three means amending ADR-025 and the bridge's tool definitions before
  merging. `exeris/applyMutation` is **not** in that slice — ADR-025 bars the bridge from the write
  path. Wiring the bridge to the writer is a boundary change that needs its own ADR.
- **The backend's HTTP surface is for workspace state and project management only**
  ([`no-parallel-metamodel.md`](no-parallel-metamodel.md)).

## The transport for Studio

ADR-084 (WebSocket Provider SPI) is the transport the Studio ↔ LSP connection is built on, and this
repository is its named consumer. It ships as `preview` at kernel 0.12, so anything bound to it pins
a contract declared to move. A missing transport is not solved with a third-party servlet or
WebSocket container.

Any add / remove / rename of an `exeris/*` method, or a change to a wire shape, triggers an ADR
([`adr-triggers.md`](adr-triggers.md)). Review procedure: `exeris-platform-lsp-protocol-review`.
