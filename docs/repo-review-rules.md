---
title: "Review rules for exeris-platform"
type: reference
visibility: public
owning-repo: exeris-platform
status: active
last-verified: 2026-10-06
---

# Review rules for `exeris-platform`

The `repo-routine` extension of `exeris-systems/.github`'s `docs-guardrails-review.md`, applied
**after** its parts and under its severity tags, output format and verdict schema. It adds checks
and raises severities; it lowers nothing and skips nothing. One review, one verdict, one publisher.

**The criteria are not authored here.** They are owned by the policies under `.agents/policies/`,
which `AGENTS.md` names, and most have a review skill under `.agents/skills/` that says how to apply
them. This file names the questions a reviewer must reach for and the severity each answer carries.
Read the policy a rule names before applying it: a rule copied here would be a second place to
author it, which `agents-md-schema.md` rule 2 forbids.

## What this repository is answerable for

The design-time platform: Studio, the LSP server that keeps Studio, IDE plugins and on-disk sources
in sync, and the backend that holds the platform's own state. Three surfaces edit one canonical
model, so the characteristic defect here is not a wrong line but a second source of truth — a
shape, a writer or a wire contract that lets the surfaces disagree. The shared `code` part judges
whether a change is correct; what follows is what it cannot know.

## Step P — rules of this repository

P1. **No parallel metamodel** (`no-parallel-metamodel.md`). A type in `exeris-studio-backend` or
    `exeris-platform-lsp` that carries a user domain's shape, a backend HTTP endpoint that returns
    one, or frontend state that persists one → `[HARD BLOCK]`. A frontend view-model projected from
    an LSP response, and the backend's own operational state, are not findings.

P2. **The LSP is the wire boundary** (`lsp-wire-boundary.md`). An unprefixed custom LSP method, a
    method or wire shape that differs between stdio and WebSocket, or a client asking backend Java
    for domain shape → `[HARD BLOCK]`. Adding, removing, renaming or reshaping an `exeris/*` method
    without the ADR or the ADR-025 amendment it needs (`adr-triggers.md`) → `[HARD BLOCK]`. Wiring
    `exeris-ai-bridge` to `exeris/applyMutation` → `[HARD BLOCK]`.

P3. **Idempotent write-back** (`idempotent-writeback.md`). A write to source that bypasses
    `exeris/applyMutation` and the SDK writer, or a platform-side redefinition of `MutationOp`,
    `MutationResult` or their conflict semantics → `[HARD BLOCK]`. A change to the mutation path
    with no test applying the same operation twice and comparing the bytes → `[CONTRACT]`.

P4. **Open-core boundary** (`open-core-boundary.md`). A concrete implementation of multi-environment
    promotion, design-time RBAC, approval workflows, audit dashboards or multi-tenant management →
    `[HARD BLOCK]`. An extension point with no implementation here is not a finding.

P5. **No composition runtime** (`composition-runtime-placement.md`). Composition machinery in a
    Studio, LSP or backend module, a port of `CompositionBinding`, a stamp assertion used as a
    signature or licence gate, or capability awareness pushed into the kernel → `[HARD BLOCK]`.

P6. **Where a Studio surface gets its shape** (`studio-surface-sourcing.md`, `frontend-stack.md`). A
    generated screen used as the source of truth for a user's domain → `[HARD BLOCK]`. A third UI
    framework, or a design system beside `@exeris-systems/ui-kit`, without an ADR → `[CONTRACT]`.

P7. **The standalone launcher is a consumer contract** (`standalone-launcher-contract.md`).
    Deleting or skipping `LauncherIT`, or moving it out of failsafe → `[HARD BLOCK]`. Raising
    `maven.compiler.release` above the kernel's (`jdk-baseline.md`) → `[HARD BLOCK]`.

P8. **Release and distribution** (`release-and-distribution.md`). A workflow that publishes on a
    push to `main`, or a release version committed on `main` → `[HARD BLOCK]`. A Maven Central
    publication path wired before the stack ahead of this repository is on Central → `[CONTRACT]`.

## Where this does not apply, and what it costs

Not to correctness, scope, or the pull request's body and commits. The shared `code` and `pr` parts
judge those, and they are not restated here: a rule in two places drifts in one of them.

The cost is that P1 to P8 are prose a reviewer applies, not a program. The review is handed
`repo-checks` output for the two scripts `.agents/manifest.yaml` names: `hook-deny-check.sh`, which
runs the deny hook against the commands it must refuse and let through, and
`eval-consistency-check.sh`, which holds `scenarios.yaml`'s `negative` tags to its cases. The tests
the policies name — `ApplyMutationTest`, `ExerisLanguageServerTest`, `LauncherIT` — run only in the
build checks on the pull request, which the reviewer cannot read. A rule the reviewer could only
check by running one of those tests is reported as unchecked, not as passing.
