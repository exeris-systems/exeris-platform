---
name: lsp-protocol-purity
description: Audit an LSP wire-surface change for namespacing (`exeris/*`), SDK alignment (`MutationOp`/`MutationResult`), and transport parity (stdio + WebSocket speak the same surface).
disable-model-invocation: true
---

<!-- DO NOT EDIT. Generated from .agents/workflows/lsp-protocol-purity.md by agents_render.py
     (exeris-systems/exeris-agents; agents-md-schema.md rule 7). Edit the source. -->
Audit this LSP wire-surface change.

LSP rules:
- Standard LSP methods (`initialize`, `shutdown`, `textDocument/*`, `workspace/*`) follow the spec — no Exeris-specific divergence.
- Custom Exeris methods MUST be namespaced under `exeris/` (the shipped set is read off the `@JsonRequest` annotations in `ExerisProtocolExtensions.java`).
- `MutationOp` / `MutationResult` wire shape is owned by `exeris-sdk-source-model` — redefining here is a regression.
- Both transports (stdio for IDE plugins and `exeris-ai-bridge`, WebSocket for browser clients such as Studio) speak the same JSON-RPC method surface — don't fork per transport.

Change:
$ARGUMENTS

Please review:
1. Is every custom method under the `exeris/` namespace?
2. If the change uses `MutationOp` or `MutationResult`, are they imported from the SDK or redefined locally?
3. Does either transport (stdio / WebSocket) see a different method set or different wire shape?
4. Are the standard LSP methods still spec-compliant?
5. Is the change consumed by `exeris-ai-bridge` (`lsp:*` tool family per ADR-025)? If yes, mark cross-tool-visibility for ADR review.
6. Minimal correction if the wire surface is at risk.

Wire-surface widening / renaming / removal requires an ADR and a ROADMAP entry; a change to the read-only trio or `exeris/previewMutation`, which the bridge consumes, is an ADR-025 amendment.
