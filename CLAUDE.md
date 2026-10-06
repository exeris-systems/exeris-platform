---
title: "CLAUDE.md — exeris-platform"
type: reference
visibility: public
owning-repo: exeris-platform
status: active
last-verified: 2026-10-06
---

# CLAUDE.md — exeris-platform

This repository's agent contract is [`AGENTS.md`](AGENTS.md), and its detailed semantics live in
[`.agents/`](.agents) — policies, references, skills, role profiles and workflows. Read `AGENTS.md`
first; it is the entry point every compatible agent can discover.

This file exists only because a Claude client looks for it
([`agents-md-schema.md`](https://github.com/exeris-systems/exeris-docs/blob/main/standards/agents-md-schema.md)
rule 7). It states no rule of its own: a rule written here would be a second place to author project
semantics, which is what the schema forbids.

Claude-specific adapters generated from `.agents/` are in [`.claude/`](.claude), each carrying a
do-not-edit marker naming its source.
