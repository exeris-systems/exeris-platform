# Policy: Studio Frontend Stack

`exeris-studio-frontend` is **Angular 22 with an embedded React editor**, built by npm outside the
Maven reactor.

## The rule

- **Two UI frameworks, not three.** The embedded React editor is a deliberate choice for the editor
  surface, isolated to its mount point; it is not an invitation. A third UI framework needs an ADR.
- **Node 24+**, as the `engines` field in `package.json` states.
- **The design system is the SDK's UI kit.** Studio inherits it and ships no competing design system.
  Two names that are not interchangeable: `exeris-sdk-ui-kit` is the **directory** in the
  `exeris-sdk` repository; `@exeris/ui-kit` is the **npm package**, installed from npmjs with no
  scoped registry and no token.
- **The Angular major follows the scaffold `@exeris/codegen-ts` emits**, because the generated
  screens under `src/app/generated` are committed and compiled with the hand-written app. A major
  bump is not an ADR trigger; replacing the framework is.
- **Tailwind v4 wiring.** The kit ships a JS preset for v3 and a CSS `@theme` entry for v4. The
  frontend is on v4, so it imports the `@theme` entry; `presets: [...]` was removed in v4.
- **Projection, not persistence.** The frontend may hold view-models projected from `DomainMetadata`
  and never writes sources ([`no-parallel-metamodel.md`](no-parallel-metamodel.md),
  [`idempotent-writeback.md`](idempotent-writeback.md)).

Review procedure: `exeris-platform-frontend-projection-review`.
