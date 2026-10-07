# Reference: Cross-Repo Dependencies

This repository is the open protocol layer of the design-time stack: one LSP server, the BOM and
the parent. Versions are in `exeris-platform-bom`.

## Reads from

- **`exeris-sdk`** — `exeris-sdk-source-model-io` for the JavaParser parser and the idempotent
  writer (ADR-037), `exeris-sdk-source-model` for the canonical AST records and the
  `MutationOp` / `MutationResult` vocabulary (ADR-042).
- **`exeris-kernel`** — `exeris-kernel-spi` for the WebSocket Provider SPI (ADR-084) and
  `exeris-kernel-community` for its community provider, which hosts the WebSocket transport.
- **The running kernel is not read from here.** Diagnostic surfaces of a live kernel are
  `exeris-ai-bridge`'s `kernel:*` tool family.

## Read by

- IDE plugins (IntelliJ, VS Code), over stdio.
- `exeris-ai-bridge`, launching the standalone jar and using the read trio and
  `exeris/previewMutation` only — never `exeris/applyMutation` (ADR-025).
- Studio, Exeris' closed product, over WebSocket. It is a consumer of the protocol like the others;
  its code is not in this repository ([policy](../policies/open-core-boundary.md)).

## Orchestration

The Exeris stack (`eu.exeris:exeris-kernel-*`, `eu.exeris:exeris-sdk-*`) resolves from Maven Central
at the versions `exeris-platform-bom` pins. Building needs no sibling checkout and no credential.

Testing an unreleased upstream change means `mvn install` in that repository and overriding the
pin on the command line (`-Dexeris.sdk.version=…`, `-Dexeris.kernel.version=…`); the pin itself
moves only to a published version.

Publishing this repository's own artifacts to GitHub Packages is the one step that authenticates:
`publish.yml` deploys with `.github/maven-settings.xml` and the job's `GITHUB_TOKEN`.

## Working references in sibling repositories

When a pattern exists elsewhere, start from it:

- CI and reusable-workflow shapes → `exeris-tooling/.github/workflows/`.
- Kernel SPI and community providers → `exeris-kernel/`.
- ADRs, RFCs, standards → `exeris-docs/`.
