---
description: Audit an LSP wire-surface change for namespacing (`exeris/*`), SDK alignment (`MutationOp`/`MutationResult`), and transport parity (stdio + WebSocket speak the same surface).
argument-hint: LSP server diff or `exeris/*` method change to audit
---

Audit this LSP wire-surface change.

LSP rules:
- Standard LSP methods (`initialize`, `shutdown`, `textDocument/*`, `workspace/*`) follow the spec — no Exeris-specific divergence.
- Custom Exeris methods MUST be namespaced under `exeris/` (e.g. `exeris/entityModel`, `exeris/applyMutation`, `exeris/listCapabilities`, `exeris/diffPreview`).
- `MutationOp` / `MutationResult` wire shape is owned by `exeris-sdk-source-model` — redefining here is a regression.
- Both transports (stdio for IDE plugins, WebSocket for Studio frontend) speak the same JSON-RPC method surface — don't fork per transport.

Change:
$ARGUMENTS

Please review:
1. Is every custom method under the `exeris/` namespace?
2. If the change uses `MutationOp` or `MutationResult`, are they imported from the SDK or redefined locally?
3. Does either transport (stdio / WebSocket) see a different method set or different wire shape?
4. Are the standard LSP methods still spec-compliant?
5. Is the change consumed by `exeris-ai-bridge` (`lsp:*` tool family per ADR-025)? If yes, mark cross-tool-visibility for ADR review.
6. Minimal correction if the wire surface is at risk.

Wire-surface widening / renaming / removal typically requires a ROADMAP entry. Cross-tool-visible changes typically require a cross-repo ADR.
