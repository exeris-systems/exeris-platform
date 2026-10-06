# Reference: ADRs That Bind This Repository

The authoritative text of each ADR is in `exeris-docs` or in the repository that owns it; the
`docs/adr/*.link.md` stubs here point to it. This page says where each one applies, and yields to
the ADR wherever the two differ.

| ADR | Applies when |
|:--|:--|
| ADR-006 Spring-Free Kernel Boundary | Not directly — no kernel runtime here — but the same rule protects `DomainMetadata`: no implementation detail smuggled past a contract surface. |
| ADR-020 Visibility taxonomy | Documenting a feature with an enterprise counterpart: `public` or `enterprise-private`. |
| ADR-024 Capability Composition Model | Anything touching composition: this repository is the deploy-time control plane and holds no composition runtime ([policy](../policies/composition-runtime-placement.md)). |
| ADR-025 AI Agent Bridge | Any change to `exeris/domains`, `exeris/domainDescribe` or `exeris/actions` — an amendment to ADR-025, not a new ADR. The bridge never reaches `exeris/applyMutation`. |
| ADR-037 `exeris-sdk-source-model-io` | The parser/writer coordinate the LSP depends on; AST records stay in `exeris-sdk-source-model`. |
| ADR-042 Bidirectional mutation surface | The write-back path: `exeris/applyMutation`'s wire shape, the `MutationOp` vocabulary, conflict detection and baseline-trust gating. Realised here. |
| ADR-084 WebSocket Provider SPI | The Studio ↔ LSP transport. This repository is its named consumer; it ships `preview` at kernel 0.12. |
| ADR-085 Documentation and repo hygiene | `AGENTS.md`, `.agents/`, frontmatter, commit and pull-request conventions. |
| ADR-087 Review publication | The organisation's review and its verdict, run by `guardrails.yml`. |

## The next amendment

Widening `exeris/domainDescribe` past `fields` / `actions` / `artefacts` — 0.4.0's entity detail
view needs `relationships` — is an amendment to ADR-025. The "none versus not carried" distinction
it must preserve is already the canonical model's: `DomainMetadata` is `@JsonInclude(NON_NULL)`, so
null is omitted and `[]` means none. The job is to avoid flattening that convention, not to invent
one — `projections` and `eventHandlers` are reserved upstream and arrive unpopulated. The full
argument is at `domainDescribe` in `ExerisProtocolExtensions.java`.

## Precedence when documents disagree

1. `docs/adr/*` and the cross-repo ADRs they link.
2. `README.md` — target architecture, module table, the "no metamodel here" rationale.
3. `ROADMAP.md` — milestone scope.
4. `AGENTS.md` and `.agents/`.

A higher source wins; the lower one is a documentation-drift task. The code settles what has
shipped: read `exeris-platform-lsp` sources rather than assuming a document kept up.
