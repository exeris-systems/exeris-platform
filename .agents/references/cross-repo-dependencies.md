# Reference: Cross-Repo Dependencies

This repository sits at the top of the design-time stack. Versions are in `exeris-platform-bom`.

## Reads from

- **`exeris-sdk`** — `exeris-sdk-source-model-io` for the JavaParser parser and the idempotent
  writer (ADR-037), `exeris-sdk-source-model` for the canonical AST records (kept dependency-light),
  the annotations for canonical shape, and `exeris-sdk-composition-*` for deploy-time validation
  (ADR-024).
- **`exeris-tooling`** — `exeris-processor` produces the `exeris-metadata` JSON corpus;
  `@exeris/codegen-ts` generates Studio's screens over the platform's own domain.
- **The running kernel is not read from here.** Diagnostic surfaces of a live kernel are
  `exeris-ai-bridge`'s `kernel:*` tool family.

## Read by

- Studio users — Angular plus the embedded React editor.
- IDE plugins (IntelliJ, VS Code) over stdio LSP.
- `exeris-ai-bridge`, over the read-only `exeris/*` slice only (ADR-025), launching the standalone
  jar.
- `exeris-platform-enterprise`, through the extension points this repository exposes.

## Orchestration

CI clones each upstream at its release tag and runs `mvn install` in-job. A SNAPSHOT registry is a
future option, not a near-term commitment.

`eu.exeris:*` snapshots, where they are needed, resolve from GitHub Packages
(`https://maven.pkg.github.com/exeris-systems/*`), with `GITHUB_TOKEN` and `PACKAGES_READ_TOKEN`
holding a PAT with `read:packages`. The settings file is `.github/maven-settings.xml`.

## Working references in sibling repositories

When a pattern exists elsewhere, start from it:

- CI and reusable-workflow shapes → `exeris-tooling/.github/workflows/`.
- Kernel SPI and community providers → `exeris-kernel/`.
- ADRs, RFCs, standards → `exeris-docs/`.
