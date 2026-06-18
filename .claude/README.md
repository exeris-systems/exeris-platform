# `.claude/` — Claude Code workspace for `exeris-platform`

This directory is loaded automatically when a Claude Code session opens inside
`~/exeris-systems/exeris-platform/`. It exists alongside the repo-root [`CLAUDE.md`](../CLAUDE.md)
and works as the operating context for AI assistants on Studio + LSP + studio-backend.

## Layout

- `agents/` — sub-agents Claude can launch via the `Agent` tool (or the user can invoke directly):
  - `exeris-platform-router.md` — entrypoint triage; classifies work and routes to the right specialist
  - `exeris-platform-architect.md` — open-core boundary, no-parallel-metamodel discipline, module placement
  - `exeris-platform-implementer.md` — concrete code changes in studio-backend / LSP / studio-frontend
  - `exeris-platform-lsp-protocol.md` — LSP wire surface, `exeris/*` method namespacing, idempotent-writeback contract
  - `exeris-platform-docs-adr.md` — ADR drift / README target-architecture sync / ROADMAP milestone bookkeeping
- `commands/` — slash commands invocable as `/<command-name>`:
  - `no-parallel-metamodel-check.md`, `lsp-protocol-purity.md`, `idempotent-writeback-check.md`, `open-core-boundary.md`
- `skills/` — invocable skills (`/<skill-name>`), and also auto-dispatched by the model from their `description` triggers:
  - Triage / planning: `exeris-platform-task-classifier`, `exeris-platform-routing-planner`
  - Contract reviews (deep, evidence-gathering): `exeris-platform-no-parallel-metamodel-review`, `exeris-platform-lsp-protocol-review`, `exeris-platform-idempotent-writeback-review`, `exeris-platform-open-core-boundary-review`, `exeris-platform-frontend-projection-review` (frontend projection vs persistence)
  - Sweep / gates / authoring: `exeris-platform-contract-sweep` (all five contracts in one pass), `exeris-platform-cross-build-validation` (Maven↔npm gate), `exeris-platform-sdk-dep-sync` (upstream `eu.exeris:*` resolvability), `exeris-platform-decision-doc-shape` (Research/RFC/ADR selector)

### Commands vs review skills — when each fires

The four contract concerns exist as both a `commands/` entry and a `skills/…-review` entry, by design — they are two entry points, not a duplication to collapse:

- **Command** (`/no-parallel-metamodel-check`, `/lsp-protocol-purity`, `/idempotent-writeback-check`, `/open-core-boundary`) — a fast, user-typed audit that takes the diff as `$ARGUMENTS`. Reach for it when you already have the change in hand.
- **Review skill** (`exeris-platform-*-review`) — the deeper path the model auto-dispatches from its `description`. It gathers the diff and grep evidence itself, then walks a full procedure to an `APPROVE / CONDITIONAL / REJECT` verdict. Reach for it when the change should be reviewed without someone pasting a diff.

## Doctrine — single source

Project doctrine is **not** duplicated under `.claude/` to avoid drift:

- **`/CLAUDE.md`** (repo root) — auto-loaded operating context (target architecture, hard constraints, scoped bans, build commands, open-core boundary).
- **`README.md`** — target architecture diagram, module table, "no metamodel here" rationale (Corelio-era deletion).
- **`ROADMAP.md`** — milestone scope (0.1.0 scaffold shipped, 0.2.0+ in flight, 1.0.0 GA = Studio replaces the IDE for design-time work).
- **Backend `package-info`** — canonical record of the Corelio-era metamodel deletion.

When skills/agents need policy context, they reference these — they do not restate them.
