---
title: "exeris-platform: the LSP server and the exeris/* protocol"
type: reference
visibility: public
owning-repo: exeris-platform
status: active
last-verified: 2026-10-07
---

# exeris-platform

Guardrails for AI assistants working inside this repository — the contract a session must respect,
and an index to where each rule lives. Human onboarding is [`README.md`](README.md) and
[`CONTRIBUTING.md`](CONTRIBUTING.md).

## Mission and scope

`exeris-platform` is the **open-source design-time protocol** of Exeris: the LSP server that keeps
its clients and the on-disk `@ExerisDomain` sources in sync. The clients are IDE plugins (stdio),
`exeris-ai-bridge` and Studio (WebSocket). Studio — the graph editor, its workspace state, the
visual CMS and accounts — is Exeris' closed product behind registration; it consumes this server and
is not in this repository. The repository holds only what must be open source.

> **One canonical model, one protocol for every client, idempotent write-back.**

The canonical model is `DomainMetadata`, defined in `exeris-sdk-source-model`. This repository holds
no second one. `exeris-platform-lsp` ships the read-only `exeris/*` trio, the writer
`exeris/applyMutation` with its write-free preview `exeris/previewMutation`, and a standalone
launcher. Where a document and the code disagree about what has shipped, the code wins.

Coordinates: groupId `eu.exeris.platform`, packages `eu.exeris.platform.*`. Licence: Apache-2.0.

## Operating contract

**Non-negotiable, whatever the task:**

- **No parallel metamodel.** The server projects `DomainMetadata` onto the wire and holds no
  domain shape of its own ([policy](.agents/policies/no-parallel-metamodel.md)).
- **The LSP is the wire boundary.** Clients ask model questions over JSON-RPC only; custom methods
  live under `exeris/`, and stdio and WebSocket speak one surface. The read trio and
  `exeris/previewMutation` are `exeris-ai-bridge`'s, pinned by ADR-025; the bridge never reaches
  the writer
  ([policy](.agents/policies/lsp-wire-boundary.md)).
- **Idempotent write-back.** The LSP is the only writer to disk, through the SDK writer, with the
  SDK-owned `MutationOp` / `MutationResult` vocabulary of ADR-042. The same mutation twice converges
  to the same bytes ([policy](.agents/policies/idempotent-writeback.md)).
- **Open-core boundary.** Only what must be open lives here: the server and its protocol. Studio,
  its workspace state, the CMS and accounts are the closed product; premium features —
  multi-environment promotion, design-time RBAC, approval workflows, audit dashboards, multi-tenant
  management — never land here. An extension point a closed client needs is open-core work, its
  implementation is not ([policy](.agents/policies/open-core-boundary.md)).
- **No composition runtime here.** This is ADR-024's deploy-time control plane; the runtime lives in
  the SDK ([policy](.agents/policies/composition-runtime-placement.md)).
- **The standalone jar is a consumer contract**, and `LauncherIT` is what makes it one
  ([policy](.agents/policies/standalone-launcher-contract.md)). **JDK floor 25**, raised only with
  the kernel ([policy](.agents/policies/jdk-baseline.md)).
- **A release is a tag**, published to GitHub Packages; Central waits on this repository's own
  release gate ([policy](.agents/policies/release-and-distribution.md)).
- **Some changes are decisions** — the LSP surface, the open-core boundary, the write-back contract:
  trigger an ADR, do not just edit code ([policy](.agents/policies/adr-triggers.md)).

## Architecture and documentation entry points

1. [`README.md`](README.md) — target architecture, module table, the "no metamodel here" rationale.
2. [`ROADMAP.md`](ROADMAP.md) — milestone scope, and the release-cut procedure.
3. [`docs/adr/`](docs/adr/) — link stubs to the ADRs that bind this repository; where each applies is
   in [`adr-map.md`](.agents/references/adr-map.md), with the precedence order when documents
   disagree.
4. `ExerisProtocolExtensions.java` — the `@JsonRequest` annotations are the shipped `exeris/*`
   surface.

## `.agents/` — the canonical semantic source

Detailed rules are authored once under [`.agents/`](.agents) and nowhere else.

| Path | What it holds |
|:--|:--|
| [`.agents/policies/`](.agents/policies) | Non-negotiable boundaries: metamodel, LSP wire, write-back, open core, composition placement, launcher, JDK, release, ADR triggers. |
| [`.agents/references/`](.agents/references) | Build and testing, cross-repo dependencies, the ADR map. |
| [`.agents/skills/`](.agents/skills) | Triage and planning, the four contract reviews and their sweep, upstream dependency sync, decision-document shape. |
| [`.agents/agents/`](.agents/agents) | Role profiles: router, architect, implementer, LSP protocol, docs/ADR, evaluator. |
| [`.agents/workflows/`](.agents/workflows) | User-invoked audits: write-back idempotency, LSP purity, parallel metamodel, open-core boundary. |
| [`.agents/schemas/`](.agents/schemas), [`hooks/`](.agents/hooks), [`evals/`](.agents/evals) | Decision schemas, the L0 hooks, behaviour scenarios. |
| [`.agents/manifest.yaml`](.agents/manifest.yaml) | Composition metadata; imports `exeris-agents` 2.1.0, vendored under `.agents/vendor/`. |

Instruction sources resolve broad to narrow: organisation bundle → repository → subtree → workflow.
A narrower file may restrict behaviour; it may never relax a higher-order rule.

## Verification and reporting

- `mvn install` — the LSP, `LauncherIT` included
  ([reference](.agents/references/build-and-testing.md)).
- `mvn -pl exeris-platform-lsp verify` — packages the shaded jar and runs it.
- The kernel and SDK resolve from Maven Central at the versions in `exeris-platform-bom`;
  no sibling install and no credential.

Report outcomes first. A claim names the command that proves it; a check that did not run is
reported as not run.

## Conventions and contribution terms

Binding standards live in
[`exeris-docs/standards/`](https://github.com/exeris-systems/exeris-docs/tree/main/standards):
commit and pull-request conventions, Javadoc, the docs style guide, ADR conventions, the
[agent-file schema](https://github.com/exeris-systems/exeris-docs/blob/main/standards/agents-md-schema.md)
and [AI provenance](https://github.com/exeris-systems/exeris-docs/blob/main/standards/ai-provenance.md).
English in every committed artefact.

An AI-assisted commit keeps its `Co-authored-by:` trailer, a named human is accountable for every
line, and an agent does not open pull requests or file issues unattended.

## Provider adapters

[`.claude/`](.claude) holds Claude Code adapters generated from `.agents/`, each carrying a
do-not-edit marker naming its source, plus provider-owned configuration. **This repository carries
no renderer**: the shared one lives in `exeris-systems/exeris-agents` and is pinned — mechanics in
[`.claude/README.md`](.claude/README.md). Never edit an adapter directly; an adapter that differs
from its source fails CI. [`CLAUDE.md`](CLAUDE.md) points here.
