# Policy: The Standalone Launcher Is a Consumer Contract

`exeris-platform-lsp` attaches a shaded `-standalone` jar that runs with no source tree.
`exeris-ai-bridge` launches it as a process. `LauncherIT` is what makes that a contract.

## The rule

- **`LauncherIT` starts the shipped jar** as a separate `java -jar` process and drives a real LSP
  session through it. Do not delete it, do not move it to surefire — it needs `package` to have run,
  so it belongs to failsafe at `verify` — and do not fix a red build by skipping it.
- **Shading is where breakage hides.** The SDK's Jackson-3 polymorphic `MutationOp` /
  `MutationResult` vocabulary is the part of the jar most likely to break under relocation, and the
  IT exercises exactly that path.

## Why

A build that never runs the artefact it ships cannot see an artefact that initialises and then dies
on its first call. Unit tests run against the reactor's classpath, not against the shaded jar a
consumer receives.
