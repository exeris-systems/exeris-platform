# Reference: Build and Testing

Commands and the traps that make them lie. The root POM, `exeris-platform-bom` and
`.github/workflows/build.yml` are authoritative; this summarises them.

## Commands

```bash
mvn clean install                                             # backend + LSP, incl. LauncherIT
mvn -pl exeris-platform-lsp -am test                          # unit tests only (no LauncherIT)
mvn -pl exeris-platform-lsp verify                            # + packages and runs the launcher
cd exeris-studio-frontend && npm install && npm run build     # Angular frontend (separate npm build)
cd exeris-studio-frontend && npm run test                     # Angular unit tests
```

`LauncherIT` needs the shaded jar, so it runs at `verify` under failsafe; `mvn test` alone does not
exercise it. The jar lands at
`exeris-platform-lsp/target/exeris-platform-lsp-<version>-standalone.jar`
([policy](../policies/standalone-launcher-contract.md)).

## Upstreams must be installed first

The reactor depends on `eu.exeris:exeris-sdk-*` and `eu.exeris.tooling:*` at the versions pinned in
`exeris-platform-bom`. Install those upstreams locally at the matching tags before the first build;
CI does the same in-job (clone at the release tag, `mvn install`). A fresh clone of this repository
alone cannot resolve them. Procedure: `exeris-platform-sdk-dep-sync`.

## `clean` matters more here than usual

`SchemaVersion.CURRENT` is a `static final String`, so javac **inlines its value** into every class
that reads it — `ApplyMutationTest` and `LauncherIT` among them. Rebuild the SDK at a different
version without rebuilding this repository's tests, and the stale test classes keep asserting the
old literal against a jar that now says something else: the baseline reads as schema skew and the
`applyMutation` tests fail with `NO_BASELINE`. That looks exactly like a write-back regression and
is not one. If those tests fail locally while CI is green, run `mvn clean test` before believing
them.

## Cross-build

A change that spans the Maven reactor and `exeris-studio-frontend`, or that changes a wire shape the
frontend consumes, is validated on both sides: `mvn install`, then `npm run build` and
`npm run test`. Procedure: `exeris-platform-cross-build-validation`.
