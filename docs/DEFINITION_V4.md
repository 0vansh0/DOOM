# Definition Version 4

Definition version 4 is an additive declarative format for API 2 packages. The package manifest remains `schemaVersion: 3` with `sdkApiVersion: 2`; only `definitionVersion` changes. Versions 1, 2, and 3 retain their existing schemas and parsing paths. No installed database records or settings are migrated by this format addition.

The canonical JSON Schema is `packages/manifest-schema/v2/mod-v4.schema.json`. The root object requires `definitionVersion`, `triggers`, and `settings` and rejects unknown fields. A definition may contain 1–16 named triggers, up to 8 ordered actions per trigger, up to 24 settings, and condition trees with at most 32 nodes and depth 4. Input is additionally limited by the existing package limits: `mod.json` 256 KiB, package 10 MiB compressed/25 MiB expanded, and 50 archive entries.

Each trigger has a unique developer-selected ID, an allowlisted event, one condition tree, and an ordered action list. Conditions use `all`, `any`, or `compare` nodes. Comparisons are explicit: numeric battery-level `below`, `above`, and `equals`; boolean charging `equals`; and event-name `equals`. Battery conditions and charging events require the `battery.read` manifest capability. Actions and parameters are allowlisted declarative data only; they cannot register code. Supported action names, templates, and required capabilities are enforced by the trusted Kotlin and CLI validators. Settings accept bounded boolean, integer, color, enum, and text forms.

## Compatibility policy

| Manifest schema | SDK API | Definition | Behavior |
| --- | --- | --- | --- |
| 2 | legacy implicit API 1 | 1 or 2 | Existing validator/parser |
| 3 | 2 | 3 | Existing API 2 validator/parser, unchanged |
| 3 | 2 | 4 | Strict V4 schema and semantic validator; parsed into typed V4 models |
| Any other pairing | any | any | Rejected with an unsupported-version diagnostic |

V4 does not reinterpret V3 fields or translate old definitions. Existing V3 archives remain valid as V3. V4 model parsing and package validation are included in this milestone; execution dispatch for V4 conditions/action arrays is not yet enabled by the V3 runtime. A V4 package must not be advertised as executable until a later runtime milestone adds that path.

The Kotlin validator intentionally implements only the JSON Schema keywords present in the trusted checked-in schemas. `$defs` is schema metadata and `$ref` resolves only within the bundled V4 schema; unsupported validator keywords fail closed. CLI and Android share the V4 positive/negative conformance fixture at `packages/manifest-schema/conformance/v4.json`.
