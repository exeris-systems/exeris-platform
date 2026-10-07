# Policy: Composition Runtime Placement

This repository is the **deploy-time control plane** of ADR-024 (obligation 8c): it *consumes* the
SDK's composition modules for design-time and deploy-time validation and preview. It holds no
composition runtime.

## The rule

- **No composition runtime here.** The boot conductor and the stamp assertion live in
  `exeris-sdk-composition-runtime`; schema and content binding live in
  `exeris-sdk-composition-spec`. `exeris-platform-composition-runtime` was retired from the reactor
  and the BOM; reintroducing in-jar composition machinery into any module here is a regression.
- **Do not port `CompositionBinding` back.** The hash that gates SKU boot must be computed by one
  implementation. The golden vector is pinned by the SDK's own `CompositionBindingTest`, including
  the normalisation of unversioned provides (`service@null` and `service@` are the same provide).
- **The stamp assertion is a correctness and operability check**, never a signature, attestation or
  licence gate. Those are a sealed-enterprise concern with an ADR of their own.
- **The kernel stays capability-blind** (ADR-024 obligation 9). No stamp, manifest or capability
  awareness is pushed into the kernel from here.
