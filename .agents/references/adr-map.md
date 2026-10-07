# Reference: ADRs That Bind This Repository

The authoritative text of each ADR is in `exeris-docs` or in the repository that owns it; the
`docs/adr/*.link.md` stubs here point to it. This page says where each one applies, and yields to
the ADR wherever the two differ.

| ADR | Applies when |
|:--|:--|
| ADR-006 Spring-Free Kernel Boundary | Not directly — no kernel runtime here — but the same rule protects `DomainMetadata`: no implementation detail smuggled past a contract surface. |
| ADR-020 Visibility taxonomy | Documenting a feature with a closed counterpart: `public` or `enterprise-private` ([policy](../policies/open-core-boundary.md)). |
| ADR-024 Capability Composition Model | Anything touching composition: this repository is the deploy-time control plane and holds no composition runtime ([policy](../policies/composition-runtime-placement.md)). |
| ADR-025 AI Agent Bridge | Any change to `exeris/domains`, `exeris/domainDescribe` or `exeris/actions` — an amendment to ADR-025, not a new ADR. The bridge never reaches `exeris/applyMutation`. |
| ADR-037 `exeris-sdk-source-model-io` | The parser/writer coordinate the LSP depends on; AST records stay in `exeris-sdk-source-model`. |
| ADR-042 Bidirectional mutation surface | The write-back path: `exeris/applyMutation`'s wire shape, the `MutationOp` vocabulary, conflict detection and baseline-trust gating. Realised here. |
| ADR-084 WebSocket Provider SPI | The LSP's WebSocket transport, which browser clients such as Studio connect to. This repository is its named consumer; it ships `preview` at kernel 0.12. |
| ADR-085 Documentation and repo hygiene | `AGENTS.md`, `.agents/`, frontmatter, commit and pull-request conventions. |
| ADR-087 Review publication | The organisation's review and its verdict, run by `guardrails.yml`. |

## `relationships` on `exeris/domainDescribe`

`exeris/domainDescribe` carries an optional `relationships[]` of `{ name, targetEntity, type? }`,
which is the SDK's `RelationshipMetadata` under its own names. This widens ADR-025's pinned read
trio, so it is an ADR-025 amendment owned by `exeris-ai-bridge`; its status is tracked in
[`ADR-025.link.md`](../../docs/adr/ADR-025.link.md).

Absent and `[]` stay distinct on the wire: a facet the pipeline does not carry is a null
component, LSP4J's Gson (built without `serializeNulls`) omits it, and a carried facet with no
entries is `[]`. `ProtocolProjectionsTest` pins this through LSP4J's own `MessageJsonHandler`, so a
projection must propagate null rather than flatten it to an empty list. `projections` and
`eventHandlers` are reserved upstream but never populated by the pipeline, so they are not
projected.

## Precedence when documents disagree

1. `docs/adr/*` and the cross-repo ADRs they link.
2. `README.md` — target architecture and module table.
3. `ROADMAP.md` — milestone scope.
4. `AGENTS.md` and `.agents/`.

A higher source wins; the lower one is a documentation-drift task. The code settles what has
shipped: read `exeris-platform-lsp` sources rather than assuming a document kept up.
