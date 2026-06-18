---
name: exeris-platform-cross-build-validation
description: Validate a change that spans the Maven reactor and the npm frontend (cross-build). Use whenever a change touches both Java modules (exeris-platform-lsp / exeris-studio-backend) and exeris-studio-frontend, alters a contract surface the frontend consumes (an `exeris/*` LSP method or its wire shape), or bumps a cross-repo `eu.exeris:*` / `eu.exeris.tooling:*` dependency. Confirms both builds stay green and the wire contract matches on both sides before the change is called done.
---

# Exeris Platform Cross-Build Validation

## Purpose
The validation gate that `exeris-platform-routing-planner` names for `CROSS_BUILD` work but no other skill owns. The repo is two builds — a Maven reactor (LSP + studio-backend) and a separate npm build (Angular + embedded React) — joined only by the LSP wire surface. A change can leave one side green and the other broken, or leave the two sides' view of an `exeris/*` method out of sync. This skill closes that gap.

## When to Use
- Any change that edits files under **both** a Java module and `exeris-studio-frontend/`.
- Any change to an `exeris/*` method or its wire shape that the frontend (WebSocket transport) consumes.
- Any bump of a cross-repo dependency (`eu.exeris:exeris-sdk-*`, `eu.exeris.tooling:*`) — the local `mvn install` of those upstreams must exist first.
- Before declaring a multi-module task "done".

## Evidence Gathering (do this first)
- `git diff --name-only main...HEAD` — split touched paths into Java vs `exeris-studio-frontend/`. If both buckets are non-empty, this is cross-build.
- Identify the shared surface: which `exeris/*` method / wire shape does the frontend call, and did the Java side change it?
- Check that upstream deps resolve: `eu.exeris:*` come from GitHub Packages, not Maven Central — `PACKAGES_READ_TOKEN` / `GITHUB_TOKEN` must be set (see top-level CLAUDE.md), or a local `mvn install` of the SDK/tooling must already exist.

## Validation Procedure
1. **Backend + LSP reactor** — `mvn -q clean install` (or `mvn -pl exeris-platform-lsp -am test` for an LSP-scoped change). Must be green.
2. **Frontend** — `cd exeris-studio-frontend && npm install && npm run build`, then `npm run test`. Must be green.
3. **Wire-contract parity** — if an `exeris/*` method or its shape changed, confirm the frontend client and the LSP server agree on method name and payload. Disagreement is a failed gate even if both builds compile. Hand off to `exeris-platform-lsp-protocol-review` for the wire details.
4. **Order sanity** — the frontend depends on the wire surface, not vice versa. If the Java side widened/renamed a method, the frontend change must land in the same logical unit, not a follow-up.
5. **Decision and report** — `PASS`, `PASS_WITH_FOLLOWUP`, or `FAIL`.

## Decision Logic
- **PASS**: both builds green; wire contract matches on both sides (or no shared surface changed).
- **PASS_WITH_FOLLOWUP**: both builds green but a non-blocking gap remains (e.g. a test asserting the new wire shape is missing) — name the exact follow-up.
- **FAIL**: either build red, or a shared `exeris/*` surface diverges between the Java and frontend sides.

## Review Output Template
1. **Cross-build scope** (Java paths / frontend paths touched)
2. **Shared surface** (which `exeris/*` method/shape, or "none")
3. **Backend + LSP reactor** (green / red + first failure)
4. **Frontend build + test** (green / red + first failure)
5. **Wire-contract parity** (matches / diverges)
6. **Verdict** (`PASS` / `PASS_WITH_FOLLOWUP` / `FAIL`)
7. **Required actions** (precise and minimal)

## Non-Negotiable Rules
- Never call a both-sides change done with only one build run.
- Never accept a renamed/widened `exeris/*` method on the Java side without the matching frontend update in the same unit.
- A green compile on both sides does not prove wire parity — check the shared shape explicitly.
