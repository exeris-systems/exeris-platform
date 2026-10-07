# Reference: Build and Testing

Commands and the traps that make them lie. The root POM, `exeris-platform-bom` and
`.github/workflows/build.yml` are authoritative; this summarises them.

## Commands

```bash
mvn install                                                   # BOM, parent, LSP, incl. LauncherIT
mvn -pl exeris-platform-lsp -am test                          # unit tests only (no LauncherIT)
mvn -pl exeris-platform-lsp verify                            # + packages and runs the launcher
```

The reactor is Maven only: `exeris-platform-bom`, `exeris-platform-parent` and
`exeris-platform-lsp`. There is no npm build in this repository.

`LauncherIT` needs the shaded jar, so it runs at `verify` under failsafe; `mvn test` alone does not
exercise it. The jar lands at
`exeris-platform-lsp/target/exeris-platform-lsp-<version>-standalone.jar`
([policy](../policies/standalone-launcher-contract.md)). `TransportParityIT` drives the same
session over stdio and WebSocket and asserts the two answer alike
([policy](../policies/lsp-wire-boundary.md)).

## Upstreams resolve from Maven Central

The reactor depends on `eu.exeris:exeris-kernel-*` and `eu.exeris:exeris-sdk-*` at the versions
`exeris-platform-bom` pins (the SDK BOM is imported, so it also manages Jackson and the test
libraries). All of them resolve from Maven Central with no credential; a fresh clone builds on its
own, and CI clones nothing. Building against an unreleased upstream is the exception: install it
from the sibling repository and override the pin on the command line, never in a commit.
Procedure: `exeris-platform-sdk-dep-sync`.

A pin bump needs no `mvn clean`. From SDK 0.12.0 on, `SchemaVersion.CURRENT` is initialised by a
method rather than a constant expression, so its class file carries no `ConstantValue` attribute
and javac does not inline the value into the tests that read it: they see the new jar's value
without a recompile. Pinning an SDK older than 0.12.0 brings the inlining back, and with it the
need for `clean` after the bump.

## A wire-shape change

A change to an `exeris/*` method or its wire shape is validated in this repository by the LSP
tests (`ExerisLanguageServerTest`, `ProtocolProjectionsTest`, `ApplyMutationTest`) and `LauncherIT`.
Its consumers — IDE plugins, `exeris-ai-bridge`, Studio — are validated in their own repositories;
the ADR the change triggers is what tells them ([policy](../policies/adr-triggers.md)).
