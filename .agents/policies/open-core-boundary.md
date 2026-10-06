# Policy: Open-Core Boundary

This repository is Apache-2.0 open source. Premium features ship in a separate, closed-source
`exeris-platform-enterprise` repository. The split mirrors the kernel's community / enterprise
model, for the same reason: the open core must be a fully usable Studio for a single team.

## The rule

- **Premium-shaped features are not implemented here:** multi-environment promotion, design-time
  RBAC, approval workflows, audit dashboards, multi-tenant organisation management, and
  enterprise-only Studio plugins.
- **Extension points belong here; their premium implementations belong there.** An SPI or hook that
  `exeris-platform-enterprise` consumes is open-core work. The concrete implementation is not.
- **Visibility follows ADR-020** — `public` or `enterprise-private`. A document describing a feature
  with an enterprise counterpart states which.

Moving a feature across the boundary, in either direction, triggers an ADR
([`adr-triggers.md`](adr-triggers.md)). Review procedure: `exeris-platform-open-core-boundary-review`.
