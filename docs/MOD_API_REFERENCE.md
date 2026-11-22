# DOOM Mod API Reference

All package actions are data interpreted by a fixed trusted host registry. Mod authors cannot supply executable code.

## Workflow declaration (Definition 4)

Add a `workflow` object to a trigger to opt into multi-action execution:

| Field | Type | Values / bounds |
| --- | --- | --- |
| `id` | string | Stable ID matching `[a-z][a-z0-9-]{0,31}`; unique within a definition |
| `executionMode` | string | `sequential` or `parallel` |
| `failurePolicy` | string | `stopOnFailure` or `continueOnFailure` |
| `timeoutMs` | integer | 1–30000 |
| `maxConcurrency` | integer | Optional, 1–3; default 3 |

Each action in an opted-in workflow must have a stable unique `id`. For parallel execution or continue-on-failure, mark every action after the first with `independent: true`. This is an author declaration that it is safe to execute without prior action success. Workflow definitions are bounded to 16 actions, two active workflows per mod, and 30 seconds per workflow.

## Trusted actions

| Action | Required capability | Supported template | Supported parameters |
| --- | --- | --- | --- |
| `overlay.show` | `overlay.render` | `battery-ring`, `floating-card` | Optional `title`, `message` (1–80 characters), `durationMs` (1–10000) |
| `overlay.dismiss` | `overlay.render` | None | None |
| `ui.preview.show` | `ui.preview` | `battery-ring`, `floating-card` | Optional `title`, `message`, `durationMs` (1–10000) |
| `haptics.pulse` | `haptics.vibrate` | None | None |

`overlay.show` also requires Android's user-granted overlay access. External overlays are limited by DOOM's overlay resource cap and remain subject to Android system policy. Effect completion means the host accepted or created the effect; it does not promise hardware display or vibration.

## Events and conditions

Events: `battery.charging.started`, `battery.charging.stopped`, `battery.level.changed`, `mod.manual.trigger`. Battery events require `battery.read` and are delivered only in an eligible foreground runtime session.

Conditions use typed `all`/`any` groups and numeric, boolean, or string comparisons. Missing metadata does not match. See `CONDITION_ENGINE.md` and `EVENT_ENGINE_V4.md` for comparison names, threshold rearming, and resource limits.

## Dynamic settings

V4 definitions can declare up to 24 settings with types `boolean`, `integer`, `number`, `color`, `enum`, `text`, or `duration`. Schemas include explicit defaults and type-appropriate bounds, labels and choices. DOOM supplies fixed Compose controls; package data cannot inject UI. Actions reference values with `{"$setting":"id"}` and conditions use the `setting.id` source. Values are validated and persisted per mod. See `DYNAMIC_MOD_SETTINGS.md` for field rules and a complete example.

## Backward compatibility

Definition 3 semantics are unchanged. Definition 4 triggers without a workflow block retain the V4 first-action behavior. Multi-action workflows require explicit workflow metadata and action IDs. Unknown schema fields and unsupported policy values are rejected.
# Capability requirements and diagnostics

Mods may declare only capability IDs in the shipped SDK registry. A capability requires a user grant inside DOOM, and platform access may also be required (for example, Android overlay special access). Device compatibility, Android authorization and per-mod approval are independent checks. Runtime checks are repeated before activation and before actions. See [Capability Manager](CAPABILITY_MANAGER.md) for statuses and current platform probes.
