---
name: exeris-platform-sdk-dep-sync
description: Confirm the upstream SDK/tooling dependencies this repo needs are resolvable before a build — local `mvn install` of `eu.exeris:exeris-sdk-*` and `eu.exeris.tooling:*` present at matching versions, and GitHub Packages auth set for any `eu.exeris:*` snapshot pulls. Use on a fresh clone, when `mvn` fails with an unresolved `eu.exeris:*` artifact, when bumping an SDK/tooling version, or before the first reactor build in a session.
---

# Exeris Platform SDK Dependency Sync

## Purpose
This repo sits at the top of the design-time stack and cannot build from a fresh clone alone: it reads from `exeris-sdk` (`exeris-sdk-source-model`) and `exeris-tooling`, and `eu.exeris:*` artifacts resolve from GitHub Packages, not Maven Central (per top-level CLAUDE.md). The two most common cold-start failures are (a) the upstreams were never `mvn install`-ed locally, and (b) the GitHub Packages token isn't exported. This skill catches both before a build burns time on a resolution error.

## When to Use
- A fresh clone, or the first reactor build in a session.
- `mvn` fails with `Could not resolve dependencies ... eu.exeris:...` or a 401/403 from `maven.pkg.github.com`.
- Bumping a version of `eu.exeris:exeris-sdk-*` or `eu.exeris.tooling:*`.
- Before handing off to `exeris-platform-cross-build-validation` (it assumes deps resolve).

## Evidence Gathering (do this first)
- Required versions: read the `eu.exeris:*` / `eu.exeris.tooling:*` coordinates from `pom.xml` / `exeris-platform-bom` / `exeris-platform-parent`.
- Local install present?  `ls ~/.m2/repository/eu/exeris/` and the matching `tooling` path — confirm the exact version directories exist, not just the groupId.
- Auth present (only needed for remote snapshot pulls): are `GITHUB_TOKEN` and `PACKAGES_READ_TOKEN` exported? For local dev they may be the same PAT (`export PACKAGES_READ_TOKEN=$GITHUB_TOKEN`); the PAT needs `read:packages`.
- Settings wiring: confirm the Maven settings file points at the `github-exeris-*` server IDs this build expects (per CLAUDE.md / the repo's settings doc).

## Sync Procedure
1. **Version match** — every `eu.exeris:*` coordinate the reactor wants has a matching version under `~/.m2/repository/eu/exeris/...`. A present-but-wrong-version artifact is still a miss.
2. **Local-install path (preferred)** — if a coordinate is missing, the fix is a local `mvn install` in the upstream sibling repo (`../exeris-sdk`, `../exeris-tooling`), not a workaround in this repo. Confirm the sibling exists and is on a compatible ref.
3. **Remote-pull path** — if relying on GitHub Packages snapshots instead, confirm both tokens are exported and the settings server IDs match; otherwise expect a 401.
4. **Dry resolve** — `mvn -q -o dependency:resolve` (offline) tells you if the local `.m2` already satisfies the reactor; a clean offline resolve means no remote auth is needed this session.
5. **Decision and report** — `READY`, `NEEDS_INSTALL`, or `NEEDS_AUTH`.

## Decision Logic
- **READY**: every required `eu.exeris:*` coordinate resolves (offline resolve clean, or auth verified) at the matching version.
- **NEEDS_INSTALL**: a coordinate/version is absent from `.m2` — name the upstream sibling repo and the `mvn install` to run there.
- **NEEDS_AUTH**: artifacts must come from GitHub Packages but a token/settings-server is missing — name exactly what to export.

## Review Output Template
1. **Required upstream coordinates** (`eu.exeris:*` + versions the reactor wants)
2. **Local `.m2` status** (present / missing / version-mismatch, per coordinate)
3. **Auth status** (`GITHUB_TOKEN` / `PACKAGES_READ_TOKEN` / settings server IDs)
4. **Verdict** (`READY` / `NEEDS_INSTALL` / `NEEDS_AUTH`)
5. **Exact next command** (the `mvn install` in which sibling, or the export to run)

## Non-Negotiable Rules
- Never "fix" an unresolved `eu.exeris:*` by vendoring, redefining, or stubbing the type in this repo — the fix is upstream `mvn install` or auth, never a local shim.
- A present groupId is not enough — verify the exact version the reactor requests.
- Don't recommend a remote-pull workaround when a local sibling install is the cheaper, offline-clean path.
