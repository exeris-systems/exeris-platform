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

The Exeris stack (`eu.exeris:exeris-kernel-*`, `eu.exeris:exeris-sdk-*`, `eu.exeris.tooling:*`)
resolves from Maven Central at the versions `exeris-platform-bom` pins, and `@exeris/ui-kit` and
`@exeris/codegen-ts` resolve from npmjs. Building needs no sibling checkout and no credential.

Testing an unreleased upstream change means `mvn install` in that repository and overriding the
pin on the command line (`-Dexeris.sdk.version=…`, `-Dexeris.tooling.version=…`); the pin itself
moves only to a published version.

Publishing this repository's own artifacts to GitHub Packages is the one step that authenticates:
`publish.yml` deploys with `.github/maven-settings.xml` and the job's `GITHUB_TOKEN`.

## Working references in sibling repositories

When a pattern exists elsewhere, start from it:

- CI and reusable-workflow shapes → `exeris-tooling/.github/workflows/`.
- Kernel SPI and community providers → `exeris-kernel/`.
- ADRs, RFCs, standards → `exeris-docs/`.
