---
name: exeris-platform-sdk-dep-sync
description: Confirm the upstream kernel/SDK dependencies this repo needs are resolvable before a build — the `eu.exeris:*` versions the BOM pins are published on Maven Central, or installed locally when building against an unreleased upstream. Use on a fresh clone, when `mvn` fails with an unresolved `eu.exeris:*` artifact, when bumping an SDK/kernel version, or before the first reactor build in a session.
---

# Exeris Platform SDK Dependency Sync

## Purpose
This repo reads `exeris-kernel` (the WebSocket SPI and its community provider) and `exeris-sdk` (`exeris-sdk-source-model`, `-io`), which resolve from Maven Central at the versions `exeris-platform-bom` pins, with no credential (`.agents/references/cross-repo-dependencies.md`). The common cold-start failures are (a) a pin naming a version not yet released upstream, and (b) a command-line override pointing at an unreleased upstream that was never `mvn install`-ed locally. This skill catches both before a build burns time on a resolution error.

## When to Use
- A fresh clone, or the first reactor build in a session.
- `mvn` fails with `Could not resolve dependencies ... eu.exeris:...`.
- Bumping a version of `eu.exeris:exeris-sdk-*` or `eu.exeris:exeris-kernel-*`.

## Evidence Gathering (do this first)
- Required versions: read the `eu.exeris:*` coordinates from `pom.xml` / `exeris-platform-bom` / `exeris-platform-parent`.
- Local install present?  list `eu/exeris/` in the local Maven repository (`mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout`) — confirm the exact version directories exist, not just the groupId.
- Published?  the pinned versions are expected on Maven Central (see `.agents/references/cross-repo-dependencies.md`); no credential is involved in resolving them.

## Sync Procedure
1. **Version match** — every `eu.exeris:*` coordinate the reactor wants has a matching version under `eu/exeris/...` in the local Maven repository. A present-but-wrong-version artifact is still a miss.
2. **Central path (default)** — a pin names a published version, so a missing coordinate normally means a network or mirror problem, or a pin to a version not yet released upstream. The pin moves only to a published version.
3. **Unreleased-upstream path** — to build against an unreleased change, `mvn install` it in the sibling repo (`../exeris-sdk`, `../exeris-kernel`) and override the pin on the command line (`-Dexeris.sdk.version=…`, `-Dexeris.kernel.version=…`); never commit that override.
4. **Dry resolve** — `mvn -q -o dependency:resolve` (offline) tells you if the local `.m2` already satisfies the reactor; a clean offline resolve means no remote auth is needed this session.
5. **Decision and report** — `READY`, `NEEDS_INSTALL`, or `UNPUBLISHED_PIN`.

## Decision Logic
- **READY**: every required `eu.exeris:*` coordinate resolves (offline resolve clean, or present on Maven Central) at the matching version.
- **NEEDS_INSTALL**: a coordinate/version is absent from `.m2` — name the upstream sibling repo and the `mvn install` to run there.
- **UNPUBLISHED_PIN**: the BOM pins a version Maven Central does not have — the pin is wrong, or the upstream release has not happened yet.

## Review Output Template
1. **Required upstream coordinates** (`eu.exeris:*` + versions the reactor wants)
2. **Local `.m2` status** (present / missing / version-mismatch, per coordinate)
3. **Publication status** (pinned version present on Maven Central, per coordinate)
4. **Verdict** (`READY` / `NEEDS_INSTALL` / `UNPUBLISHED_PIN`)
5. **Exact next command** (the `mvn install` in which sibling, or the pin to correct)

## Non-Negotiable Rules
- Never "fix" an unresolved `eu.exeris:*` by vendoring, redefining, or stubbing the type in this repo — the fix is a published pin or an upstream `mvn install`, never a local shim.
- A present groupId is not enough — verify the exact version the reactor requests.
- Don't recommend a remote-pull workaround when a local sibling install is the cheaper, offline-clean path.
