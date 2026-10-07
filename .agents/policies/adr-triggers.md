# Policy: Changes That Trigger an ADR

Some changes are decisions, and a decision is recorded before it is merged. For these, **trigger an
ADR, do not just edit code.**

## The triggers

- A change to the LSP method surface: adding, removing or renaming an `exeris/*` method, or changing
  the wire shape of a method or of `MutationOp` / `MutationResult`
  ([`lsp-wire-boundary.md`](lsp-wire-boundary.md)).
- Moving a feature across the open-core boundary
  ([`open-core-boundary.md`](open-core-boundary.md)).
- A change to the idempotent write-back contract
  ([`idempotent-writeback.md`](idempotent-writeback.md)).
- Introducing a domain-shaped type beside `DomainMetadata`
  ([`no-parallel-metamodel.md`](no-parallel-metamodel.md)).
- A method surface that differs between transports
  ([`lsp-wire-boundary.md`](lsp-wire-boundary.md)).

## How

- **An amendment is not a new ADR.** A change to the read-only `exeris/*` trio or to
  `exeris/previewMutation` amends ADR-025, as
  `docs/adr/ADR-025.link.md` prescribes.
- **One ecosystem-wide number space.** ADRs are indexed in `exeris-docs/adr-index.md`. Reserve the
  number there first, then write the content. Platform-owned ADRs live in `docs/adr/`; a cross-repo
  ADR leaves a `docs/adr/ADR-NNN.link.md` stub here.
- **Check the question's shape first.** Research (falsifiable, measurement-driven), RFC (several
  options, strategic) and ADR (decision already made) are different documents. A refactor-only
  change is not an ADR; it belongs in the pull request.

Procedure: `exeris-platform-decision-doc-shape`. Where each ADR applies here:
[`../references/adr-map.md`](../references/adr-map.md).
