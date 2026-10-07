---
title: "Contributing to Exeris Platform"
type: reference
visibility: public
owning-repo: exeris-platform
status: active
last-verified: 2026-10-07
---

# Contributing to Exeris Platform

This document describes how to build, test and contribute to `exeris-platform`: the LSP server
and its `exeris/*` protocol.

## Build and test

```bash
mvn install                                                   # LSP, incl. LauncherIT
mvn -pl exeris-platform-lsp -am test                          # unit tests only (no LauncherIT)
mvn -pl exeris-platform-lsp verify                            # + packages and runs the launcher
```

**JDK 25 is the baseline** (`maven.compiler.release` in the root POM).

The reactor depends on `exeris-kernel` and `exeris-sdk` at the versions pinned in
`exeris-platform-bom`; both resolve from Maven Central with no credential. A fresh clone builds on
its own.

`LauncherIT` runs the shaded `-standalone` jar as a separate process, so it needs `verify`; a plain
`mvn test` does not exercise it.

## Agent files

Agent instructions are authored in [`AGENTS.md`](AGENTS.md) and [`.agents/`](.agents); `.claude/`
is generated from them, so edit the source and re-render rather than editing an adapter
([`.claude/README.md`](.claude/README.md) has the commands). Each skill under `.claude/skills/` is
a symlink into `.agents/skills/`. On Windows without symlink support (Developer Mode off), enable
`core.symlinks` with Developer Mode, or render with `--skills-copy`; the manifest records that
fallback under `degradations`.

## Architectural invariants

`exeris-platform` is published under **Apache-2.0**. The rules a change is reviewed against are in
[`AGENTS.md`](AGENTS.md) and [`.agents/policies/`](.agents/policies); in short:

1. **No parallel metamodel.** `DomainMetadata` from `exeris-sdk-source-model` is the only model of a
   user's domain; the server projects it and holds no shape of its own.
2. **The LSP is the wire boundary.** Clients ask model questions over JSON-RPC; custom methods live
   under `exeris/`.
3. **Idempotent write-back.** The LSP is the only writer to disk; the same mutation applied twice
   converges to the same bytes.
4. **Open-core boundary.** Only what must be open lives here: the server and its protocol. Studio
   and premium features are not in this repository.
5. **Some changes are decisions.** A change to the LSP surface, the open-core boundary or the
   write-back contract needs an ADR before it merges.

## Contributor terms and DCO sign-off

External contributions require a Developer Certificate of Origin sign-off via the `Signed-off-by:`
trailer (`git commit -s`), per [ADR-085 §K](docs/adr/ADR-085.link.md).

The sign-off certifies that you have the right to submit the contribution under the Apache-2.0
licence published in `LICENSE`. Organisation members are exempt from the trailer, but remain
accountable for every merged change.

## AI provenance

Exeris is developed with AI assistance and states the terms openly per
[`ai-provenance.md`](https://github.com/exeris-systems/exeris-docs/blob/main/standards/ai-provenance.md):

- **Provenance is kept.** An AI-assisted commit carries a `Co-authored-by:` trailer naming the
  model. Stripping it is a defect; adding it where no AI was involved is prohibited.
- **A named human is accountable for every line.** The pull-request author must be able to explain
  and defend any part of the change. "The agent produced it" is never an explanation.
- **Agents do not open pull requests, file issues or post comments unattended.** Automated review
  comments are allowed; automated contributions without human review are not.
- **Verification is stated, not assumed.** A pull-request description names the commands it ran.
- **Hollow tests are rejected.** A test that asserts nothing observable, or only restates a mock,
  is not a test.

## Conventions

Development standards are binding per [ADR-085](docs/adr/ADR-085.link.md) and hosted in
[`exeris-docs/standards/`](https://github.com/exeris-systems/exeris-docs/tree/main/standards):

- [`commit-conventions.md`](https://github.com/exeris-systems/exeris-docs/blob/main/standards/commit-conventions.md) — Conventional Commits with a Netty-form body (`Motivation:`, `Modification:`, `Result:`).
- [`pr-conventions.md`](https://github.com/exeris-systems/exeris-docs/blob/main/standards/pr-conventions.md) — structured pull-request bodies and scope classification.
- [`javadoc-conventions.md`](https://github.com/exeris-systems/exeris-docs/blob/main/standards/javadoc-conventions.md) — doc-comment standards.
- [`docs-style-guide.md`](https://github.com/exeris-systems/exeris-docs/blob/main/standards/docs-style-guide.md) — validated frontmatter and naming.
- [`agents-md-schema.md`](https://github.com/exeris-systems/exeris-docs/blob/main/standards/agents-md-schema.md) — [`AGENTS.md`](AGENTS.md) as the canonical entry point and [`.agents/`](.agents) as the semantic source.
- Language: English in code, identifiers, comments, commit messages, pull requests and documentation.
