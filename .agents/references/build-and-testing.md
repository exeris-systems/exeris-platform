# Reference: Build and Testing

Commands and the traps that make them lie. The root POM, `exeris-platform-bom` and
`.github/workflows/build.yml` are authoritative; this summarises them.

## Commands

```bash
mvn install                                                   # backend + LSP, incl. LauncherIT
mvn -pl exeris-platform-lsp -am test                          # unit tests only (no LauncherIT)
mvn -pl exeris-platform-lsp verify                            # + packages and runs the launcher
cd exeris-studio-frontend && npm ci && npm run build          # Angular frontend (separate npm build)
cd exeris-studio-frontend && npm run test                     # Angular unit tests
cd exeris-studio-frontend && npm run codegen                  # regenerate src/app/generated
```

`LauncherIT` needs the shaded jar, so it runs at `verify` under failsafe; `mvn test` alone does not
exercise it. The jar lands at
`exeris-platform-lsp/target/exeris-platform-lsp-<version>-standalone.jar`
([policy](../policies/standalone-launcher-contract.md)).

## Upstreams resolve from Maven Central

The reactor depends on `eu.exeris:exeris-kernel-*`, `eu.exeris:exeris-sdk-*` and
`eu.exeris.tooling:*` at the versions `exeris-platform-bom` pins (the SDK BOM is imported, so it
also manages Jackson and the test libraries). All of them resolve from Maven Central with no
credential; a fresh clone builds on its own, and CI clones nothing. Building against an unreleased
upstream is the exception: install it from the sibling repository and override the pin on the
command line, never in a commit. Procedure: `exeris-platform-sdk-dep-sync`.

## Generated frontend code

`exeris-studio-frontend/src/app/generated` is the output of `@exeris/codegen-ts` (an exact
devDependency kept at `exeris.tooling.version`) run over the
`exeris-studio-backend` metadata corpus. It is committed and never edited by hand: a change belongs
in the `@ExerisDomain` source, the generator configuration, or hand-written code outside
`generated/`. CI rebuilds the corpus, reruns `npm run codegen` and fails on any difference.

## Cross-build

A change that spans the Maven reactor and `exeris-studio-frontend`, or that changes a wire shape the
frontend consumes, is validated on both sides: `mvn install`, then `npm run build` and
`npm run test`. Procedure: `exeris-platform-cross-build-validation`.
