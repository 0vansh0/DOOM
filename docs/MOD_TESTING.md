# Declarative Mod Testing

`doom test <source-directory> <scenario.json>` runs deterministic desktop simulations over the same validated source package. It evaluates supported conditions against supplied metadata and records the action sequence. It does **not** show overlays, access Android permissions, subscribe to hardware events, or prove Android behavior.

Scenario files use `formatVersion: 1`, contain 1–100 scenarios, and are limited to 1 MiB. Example:

```json
{
  "formatVersion": 1,
  "scenarios": [{
    "id": "charging-runs-ring-first",
    "event": { "event": "battery.charging.started", "metadata": { "charging": true } },
    "androidPermissions": ["SYSTEM_ALERT_WINDOW"],
    "expected": {
      "conditionResult": true,
      "actionSequence": ["charging-start:ring", "charging-start:message"],
      "suppressedActions": []
    }
  }]
}
```

Metadata supports normalized `batteryLevel` / `charging` aliases and canonical `battery.level` / `battery.charging` fields. `precedingEvents` feed the deterministic event timeline before the final `event`. `initialSettings` overrides declared defaults. `grantedCapabilities` and `androidPermissions` simulate policy decisions; omitted capability grants default to the package's declared set, while Android permissions default to none. `failActions` injects deterministic action failures. Expectations may assert `conditionResult`, ordered `actionSequence`, `suppressedActions`, and `failureAction`.

Simulation cannot establish real charger delivery, lifecycle eligibility, overlay visibility, Android permission state, service behavior, animation smoothness or battery impact. Those require Android tests and a device.
