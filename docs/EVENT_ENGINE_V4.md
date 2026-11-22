# Definition V4 event engine

The host supports the V4 event identifiers `battery.charging.started`, `battery.charging.stopped`, `battery.level.changed`, and `mod.manual.trigger`. Existing V3 battery and power subscriptions keep their original behavior. `battery.level.changed` is sourced from one dynamically registered sticky `ACTION_BATTERY_CHANGED` receiver while DOOM has an eligible foreground runtime session; it is not registered in the manifest and does not survive force-stop or process death.

The source normalizes `EXTRA_LEVEL / EXTRA_SCALE` only when both values are present, scale is positive, and level falls within the scale. Charging is true for `BATTERY_STATUS_CHARGING`, false for discharging/not-charging, and derived from plug state for `BATTERY_STATUS_FULL`. Missing or invalid values are unknown and do not match typed conditions. Equal normalized battery readings are suppressed. The receiver is unregistered when the foreground runtime stops. DOOM does not poll or start a service to keep battery events alive in the background.

Events are delivered only to mods already activated by the trusted runtime. Every event and effect capability is checked against the current reviewed grant. Safe mode removes runtime plans, and disabling a mod releases its owned effects. V4 dispatch reports success, denial, or engine failure through the existing bounded diagnostics channel.

Milestone 6B executes the first action of each matching trigger. Additional ordered actions per trigger and cancellation/timeout workflow semantics belong to Milestone 6C. Action parameters are validated by the importer/CLI but are not yet dynamically bound to rendered effects.
