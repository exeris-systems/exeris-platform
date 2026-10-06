# Policy: JDK Baseline

**The JDK floor is 25** — `maven.compiler.release` in the root POM — matching `exeris-kernel`,
`exeris-sdk` and `exeris-tooling`.

## The rule

- Do not raise `maven.compiler.release` ahead of the kernel. It is raised only when the kernel's is.
- CI builds on the floor and on the newest JDK; the floor row also runs `LauncherIT`, which is the
  only proof that the standalone jar starts on a 25 runtime.
- **The floor row is the required check** (`mvn install (JDK 25)` in branch protection on `main`).
  The newest-JDK row reports without gating: nothing here uses preview features, so no change
  needs a JDK above the floor to merge. Renaming the job or the matrix changes the check's name,
  so the protection update ships in the same change (`.github/workflows/build.yml`).
- Exact upstream versions live in `exeris-platform-bom`. Read them there rather than trusting a
  number written in prose.

## Why

This is a downstream constraint, not a style preference. `exeris-ai-bridge` runs this repository's
launcher beside `exeris-kernel-diagnostics-cli`; emitting class-file 70 here would give one consumer
two different JDK floors.
