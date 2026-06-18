---
name: exeris-platform-open-core-boundary-review
description: Deep, evidence-gathering review of the open-core boundary — finds the diff itself, distinguishes an extension point (open-core OK) from a concrete premium implementation (belongs in `exeris-platform-enterprise`), and issues a verdict. Use whenever a change proposes or touches a feature with a premium counterpart — multi-env promotion, RBAC, approval workflows, audit dashboards, multi-tenant/org concepts. (For a quick inline audit with a diff in hand, the `/open-core-boundary` command is the lighter path.)
---

# Exeris Platform Open-Core Boundary Review

## Purpose
Enforce: this repo (`exeris-platform`) is Apache-2.0 open-source; premium features ship in a separate, closed-source `exeris-platform-enterprise` repo. Extension points live here; concrete enterprise implementations live there.

The split mirrors the kernel `community / enterprise` model. The open core must remain a fully usable Studio for any single team.

## When to Use
- Any PR mentioning multi-environment promotion (dev → staging → prod).
- Any PR mentioning RBAC, approval workflows, audit trails, multi-tenancy.
- Any PR adding a "premium" or "team" or "org" concept.
- Any PR that adds an extension point — verify the concrete enterprise implementation does NOT also land here.

## Required Inputs
- PR diff and stated motivation.
- ADR-020 visibility classification claim (`public` / `enterprise-private`).
- Whether the change is the extension point or the concrete implementation.

## Evidence Gathering (do this first)
When no diff is handed in (autodispatch), find the change yourself:
- `git diff origin/main...HEAD` plus the stated motivation.
- Premium-shape smell: grep added lines for `role|rbac|permission|approval|audit|tenant|org|environment|promote|staging|prod`.
- Extension vs implementation: an `interface` / SPI / extension point / hook is open-core; a concrete promoter, RBAC enforcer, approval engine, or audit sink is the enterprise side.

## Review Procedure
1. **Identify premium-shape features** — multi-env, RBAC, approval workflows, audit dashboards, multi-tenant, enterprise-only Studio plugins. These are the explicit premium set per `README.md`.
2. **Classify the change** — is this an extension point (open-core OK) or a concrete implementation (enterprise repo)?
3. **For extension points**: confirm they don't bake in premium-shape defaults. A "multi-env aware" hook is OK; a multi-env promoter shipped here is not.
4. **For concrete implementations**: route to `exeris-platform-enterprise`. This PR should not land here.
5. **Visibility check** — does the PR claim `public` visibility (per ADR-020) for something that the README explicitly lists as premium?
6. **ADR check** — any feature movement between open-core and enterprise requires a NEW ADR.
7. **Decision and report** — produce one of: `APPROVE`, `CONDITIONAL`, `REJECT`.

## Decision Logic
- **APPROVE**: Extension point only, no premium-shape defaults baked in.
- **CONDITIONAL**: Extension point with subtle premium-shape coupling — recommend the coupling be moved out before merge.
- **REJECT**: Concrete premium implementation landing here; or extension point that defaults to premium behaviour.

## Completion Criteria
- Premium-shape features identified.
- Extension vs implementation classified.
- Visibility claim checked.
- ADR requirement determined.
- Verdict and remediation recorded.

## Review Output Template
1. **Scope analysed** (modules / files touched)
2. **Premium-shape detection** (which premium set members are touched)
3. **Extension vs implementation classification**
4. **Visibility claim audit** (`public` / `enterprise-private`)
5. **ADR requirement** (none / new ADR required)
6. **Verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
7. **Required actions** (precise and minimal)

## Non-Negotiable Rules
- Never approve a concrete premium-shape implementation here. Route to `exeris-platform-enterprise`.
- Never approve an extension point that bakes in premium-shape defaults.
- Always require a new ADR for feature movement between open-core and enterprise.
