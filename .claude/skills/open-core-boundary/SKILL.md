---
name: open-core-boundary
description: Enforce the open-core boundary — this repository holds only the LSP server and its protocol; Studio, its workspace state, the CMS, accounts and premium features (multi-env, RBAC, approval workflows, audit dashboards, multi-tenant) are closed and live elsewhere.
disable-model-invocation: true
---

<!-- DO NOT EDIT. Generated from .agents/workflows/open-core-boundary.md by agents_render.py
     (exeris-systems/exeris-agents; agents-md-schema.md rule 7). Edit the source. -->
Audit this change against the open-core boundary.

Boundary rules:
- This repo (`exeris-platform`) is Apache-2.0 open source and holds only what must be open: the LSP server `exeris-platform-lsp`, its `exeris/*` protocol, `exeris-platform-bom` and `exeris-platform-parent`.
- Studio — the graph editor, its backend and workspace state, the visual CMS, accounts, registration and login — is Exeris' closed product. It consumes the LSP over WebSocket; its code is not here.
- Premium features never land here:
  - multi-environment promotion (dev → staging → prod)
  - design-time RBAC and approval workflows
  - audit dashboards
  - multi-tenant org management
- An extension point a closed consumer needs (for example a transport authentication seam) belongs here, provided the open server is fully usable without an implementation; the implementations belong in the closed product.

Change:
$ARGUMENTS

Please review:
1. Does this change belong to the LSP server or its protocol at all, or is it Studio / workspace / CMS / account code?
2. Is it implementing one of the premium features (multi-env, RBAC, approval workflow, audit dashboard, multi-tenant)?
3. If either — is it adding an extension point (open-core) or a concrete implementation (closed)?
4. If it's the implementation, the right move is to:
   (a) define the extension point here, if the open server needs a seam at all,
   (b) ship the concrete implementation in the closed product.
5. Is the visibility per ADR-020 `public` (this repo) or `enterprise-private`?
6. Minimal correction if the open-core boundary is being violated.

A feature movement between this open repository and a closed product requires a new ADR (visibility taxonomy per ADR-020 in the cross-repo registry). Do not silently inline closed or premium-shaped features here.
