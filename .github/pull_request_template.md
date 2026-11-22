## Change and evidence

Affected component and issue:

Reproduction and fix:

V3/V4 compatibility and authorization impact:

Regression commands and results:

Device coverage, skipped checks and outstanding risks:

## Community SDK quality gate

- [ ] Definition 3 and Definition 4 semantics are preserved; unsupported versions fail closed.
- [ ] Package uses only registered events, actions and capabilities; no executable payloads or shell/network escape hatches.
- [ ] `doom validate`, `doom test`, and `doom package` were run on changed examples.
- [ ] Shared schema fixtures and Kotlin importer tests were updated when the public contract changed.
- [ ] Simulation, emulator and physical-device evidence are labeled separately; skipped checks are not described as passing.

