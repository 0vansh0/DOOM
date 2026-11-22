# DOOM SDK API 2

Status: first bounded implementation, Phase 2 Milestone 6. The SDK is declarative JSON and a Node CLI. It does not provide an Android process identity, downloadable Kotlin plugins, or unrestricted device modification.

## Version negotiation

| Package manifest | SDK | Definition | Host behavior |
| --- | --- | --- | --- |
| schemaVersion 2 | legacy implicit API 1 | definitionVersion 1 or 2 | Preserved unchanged |
| schemaVersion 3, sdkApiVersion 2 | API 2 | definitionVersion 3 | Existing API 2 semantics |
| schemaVersion 3, sdkApiVersion 2 | API 2 | definitionVersion 4 | Strict typed declarative schema; execution support is deferred |
| Any other combination | unknown | unknown | Reject before installation |

The package container remains `.doommod`. New schema documents live in `packages/manifest-schema/v2/doom-sdk2.schema.json` and `mod-sdk2.schema.json`. The directory preserves the existing archive format family. Schema and SDK numbers describe separate contracts. Existing schema documents are unchanged. A future host may add a supported version explicitly. It must never reinterpret an unknown version or quietly downgrade it.

## Trusted registries

`packages/manifest-schema/v2/registry.json` publishes capability metadata, events, actions, conditions, templates and setting types. Kotlin enums and native adapters are the trusted execution allowlist. Conformance tests check registry agreement and run the same positive and negative fixtures through Kotlin and AJV. A package cannot register a native action or grant itself a capability. Registry extension requires a host release. Developers can combine already supported entries in new packages without rebuilding the host.

Events: `battery.charging.started`, `battery.charging.stopped`, `mod.manual.trigger`, `device.power.changed`. The power event is an observed Battery Saver state change during a foreground session, rather than a replay on activation. Charging retains the existing initial charging-state replay behavior.

Conditions: `always`, `charging`, `not-charging`, `power-saving`, `not-power-saving`, `battery-low`, `battery-not-low`, `battery-below-setting`. Low means at most 20 percent. Unknown battery readings do not satisfy either threshold condition. Conditions only evaluate when a declared event arrives. There is no battery percentage crossing event in this version.

Actions: `overlay.show`, `overlay.dismiss`, `ui.preview.show`, `haptics.pulse`. Show actions require a supported template and the existing color/intensity setting bindings. Dismiss and pulse must omit template and params. A pulse submits one 50 ms vibration, with per-mod and global rate limits. Android may suppress vibration. The diagnostic result is SUBMITTED, which is not physical confirmation.

Templates: `battery-ring`, `floating-card`. The card uses trusted DOOM text. There is no arbitrary HTML, imported script, clock template, timer subscription, notification listener, accessibility automation or system theme modification.

Definition 3 retains its original limits and single-effect-per-trigger behavior. Definition 4 introduces named trigger records, composed condition trees, ordered action arrays, and bounded typed settings. See [Definition Version 4](DEFINITION_V4.md) for limits and compatibility rules. V4 validation does not mean V4 actions execute: the existing runtime currently dispatches V3 definitions only.

## Settings

The established `color`, bounded integer `intensity`, and optional bounded `ring` settings remain supported. API 2 adds `settings.options`, up to eight developer-named integer settings:

```json
"options": [
  {"id":"threshold","type":"integer","min":1,"max":100,"default":25}
]
```

IDs match `[a-z][a-z0-9-]{0,23}`. Values and bounds are integers within 0..100, with min <= default <= max. Duplicate IDs and unknown fields fail validation. The host generates controls and stores values in Room. Reset Settings restores reviewed defaults. Appearance edits preserve option values.

```json
{"event":"mod.manual.trigger","effect":"show","condition":"battery-below-setting","setting":"threshold"}
```

Only `battery-below-setting` accepts `setting`. The reference must resolve to a declared integer option. The comparison is actual battery percent < configured value. Runtime rechecks keys and bounds against the revalidated definition. New option names require no host changes. Boolean, free text, secrets, script values and expressions are deferred.

## Runtime and compatibility

All declared capabilities require explicit per-mod DOOM approval, including capabilities without Android permission dialogs. Minimum API and actual device service/hardware availability are checked. Overlay access additionally requires Android authorization. Capability scanning does not approve a mod. Installed records persist desired state, while ACTIVE remains session-local.

Only focused/resumed foreground sessions receive the ordinary battery and power broadcasts. Existing explicit external overlay controls retain their user-started service lifecycle. New power/haptic support does not create a background service, boot receiver, wake lock or battery optimization exemption. No guaranteed charging behavior from terminated, locked or arbitrary background states is introduced.

## Conformance contract

Run `npm --prefix doom-cli test` and `./gradlew :mod-engine:test :core-domain:test`. Shared fixture cases are in `packages/manifest-schema/conformance/sdk2.json`. Android tests additionally exercise CLI archives, ContentResolver, Room persistence/reopen, settings, grants and uninstall. See `docs/MILESTONE_6.md` for tests actually executed, device evidence and limitations.

## Android API references

Power state reads and registered broadcasts use [PowerManager](https://developer.android.com/reference/android/os/PowerManager). Hardware discovery and bounded vibration use [VibratorManager](https://developer.android.com/reference/android/os/VibratorManager) and [VibrationEffect](https://developer.android.com/reference/android/os/VibrationEffect). API availability is separate from actual device execution results recorded above.
