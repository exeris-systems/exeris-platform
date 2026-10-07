---
name: exeris-platform-open-core-boundary-review
description: Deep, evidence-gathering review of the open-core boundary — finds the diff itself, decides whether the change belongs in this open LSP repository at all, distinguishes an extension point (open-core OK) from a concrete premium or closed-product implementation (belongs outside this repository), and issues a verdict. Use whenever a change proposes or touches Studio, workspace state, the CMS, accounts or login, or a feature with a premium counterpart — multi-env promotion, RBAC, approval workflows, audit dashboards, multi-tenant/org concepts. (For a quick inline audit with a diff in hand, the `/open-core-boundary` command is the lighter path.)
---

# Exeris Platform Open-Core Boundary Review

## Purpose
Enforce: this repo (`exeris-platform`) is Apache-2.0 open source and holds only what must be open — the LSP server `exeris-platform-lsp`, its `exeris/*` protocol, `exeris-platform-bom` and `exeris-platform-parent`. Studio (graph editor, its backend and workspace state, the visual CMS, accounts, registration, login) is Exeris' closed product and consumes the LSP from outside. Premium features never land here. An extension point a closed consumer needs lives here; its implementation does not.

## When to Use
- Any PR adding a module, directory or code for Studio, workspace state, the CMS, accounts, registration or login.
- Any PR mentioning multi-environment promotion, RBAC, approval workflows, audit trails, multi-tenancy.
- Any PR adding a "premium", "team", "org" or "account" concept.
- Any PR that adds an extension point — verify the concrete implementation does NOT also land here, and that the open server works without one.

## Required Inputs
- PR diff and stated motivation.
- ADR-020 visibility classification claim (`public` / `enterprise-private`).
- Whether the change is the extension point or the concrete implementation.

## Evidence Gathering (do this first)
When no diff is handed in (autodispatch), find the change yourself:
- `git diff origin/main...HEAD` plus the stated motivation; `git diff --name-only origin/main...HEAD` for new top-level modules or directories.
- Closed-product and premium-shape smell: grep added lines for `role|rbac|permission|approval|audit|tenant|org|environment|promote|staging|prod|account|login|register|cms`. (`workspace` and `session` are ordinary LSP vocabulary — `workspace/*` methods, per-connection sessions — so they are no signal on their own.)
- Extension vs implementation: an `interface` / SPI / `ServiceLoader` seam is open-core; a concrete authenticator, promoter, RBAC enforcer, approval engine, account store or audit sink is the closed side.

## Review Procedure
1. **Placement first** — does the change belong to the LSP server or its protocol at all? Studio, workspace state, CMS and account code is refused here regardless of quality.
2. **Identify premium-shape features** — multi-env, RBAC, approval workflows, audit dashboards, multi-tenant.
3. **Classify the change** — extension point (open-core OK) or concrete implementation (closed product)?
4. **For extension points**: confirm the open server is fully usable with no implementation present, and that no premium default is baked in.
5. **For concrete implementations**: route outside this repository. This PR should not land here.
6. **Visibility check** — does the PR claim `public` visibility (per ADR-020) for something closed?
7. **ADR check** — any feature movement between this open repository and a closed product requires a NEW ADR.
8. **Decision and report** — produce one of: `APPROVE`, `CONDITIONAL`, `REJECT`.

## Decision Logic
- **APPROVE**: LSP/protocol work with no closed-product coupling; or an extension point only, with the open server unchanged when no implementation is present.
- **CONDITIONAL**: Extension point with subtle premium or closed-product coupling — recommend the coupling be moved out before merge.
- **REJECT**: Studio / workspace / CMS / account code landing here; a concrete premium implementation landing here; or an extension point that only works with a closed implementation.

## Completion Criteria
- Placement decided.
- Premium-shape and closed-product features identified.
- Extension vs implementation classified.
- Visibility claim checked.
- ADR requirement determined.
- Verdict and remediation recorded.

## Review Output Template
1. **Scope analysed** (modules / files touched)
2. **Placement** (LSP / protocol / build, or closed product)
3. **Premium-shape detection** (which premium set members are touched)
4. **Extension vs implementation classification**
5. **Visibility claim audit** (`public` / `enterprise-private`)
6. **ADR requirement** (none / new ADR required)
7. **Verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
8. **Required actions** (precise and minimal)

## Non-Negotiable Rules
- Never approve Studio, workspace-state, CMS or account code in this repository.
- Never approve a concrete premium-shape implementation here.
- Never approve an extension point the open server cannot run without.
- Always require a new ADR for feature movement between this repository and a closed product.
