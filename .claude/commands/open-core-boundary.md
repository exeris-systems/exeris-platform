---
description: Enforce the open-core boundary — premium features (multi-env, RBAC, approval workflows, audit dashboards, multi-tenant) belong in `exeris-platform-enterprise`, not here.
argument-hint: PR diff or feature description to audit
---

Audit this change against the open-core boundary.

Boundary rules:
- This repo (`exeris-platform`) is Apache-2.0 open-source.
- Premium features ship in a separate, closed-source `exeris-platform-enterprise` repository:
  - multi-environment promotion (dev → staging → prod)
  - design-time RBAC and approval workflows
  - audit dashboards
  - multi-tenant org management
  - enterprise-only Studio plugins
- The split mirrors the kernel `community / enterprise` model and exists for the same reason: the open core must be a fully usable Studio for any single team.
- Extension points belong here; their premium implementations belong over there.

Change:
$ARGUMENTS

Please review:
1. Is this change implementing one of the explicitly premium features (multi-env, RBAC, approval workflow, audit dashboard, multi-tenant)?
2. If yes — is it adding an extension point (open-core) or a concrete implementation (enterprise)?
3. If it's the implementation, the right move is to:
   (a) define the extension point here,
   (b) ship the concrete implementation in `exeris-platform-enterprise`.
4. Is the visibility per ADR-020 `public` (this repo) or `enterprise-private` (the other one)?
5. Minimal correction if the open-core boundary is being violated.

A feature movement between open-core and enterprise requires a new ADR (visibility taxonomy per ADR-020 in the cross-repo registry). Do not silently inline premium-shaped features here.
