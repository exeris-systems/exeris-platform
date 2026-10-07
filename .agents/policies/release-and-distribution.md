# Policy: Release and Distribution

## The rule

- **`mvn deploy` goes to GitHub Packages, not Maven Central.** Central needs signing, sources and
  javadoc jars and a readiness gate this repository does not have. The upstream half of the
  sequencing is met — the kernel and SDK this repository builds on are on Central — so
  what remains is this repository's own gate (ROADMAP). "Add a `release` profile" is not a ready
  task until that gate is; when it is, copy `exeris-kernel`'s.
- **A release is a tag and nothing else.** Pushing `v<x.y.z>` runs `.github/workflows/publish.yml`,
  which deploys. `workflow_dispatch` on that workflow is a dry run that touches nothing remote.
- **Never publish on a push to `main`.** A development line shares one `-SNAPSHOT` coordinate, so
  publishing per merge produces artefacts that share a coordinate and differ in content. Trunk sits
  on `<next>-SNAPSHOT`, and `main` never carries a release version. The cut procedure is in
  `ROADMAP.md`.
- **When Central is wired, it rides the same tag**: one cut, both registries. The channels differ in
  reversibility, not in trigger — a Packages coordinate can be republished, a Central version is
  spent forever — which is why only Central needs the signing and readiness machinery.
- **An agent does not cut, tag or deploy.** Those are outward-facing and irreversible
  (`bundle:agent-safety-and-autonomy`); the deny hook refuses them.
