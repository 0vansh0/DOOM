# Supported events

Use only events accepted by `mod-v4.schema.json` and the runtime registry:

| Event | Availability |
| --- | --- |
| `battery.charging.started` | Dynamic Android battery state while the DOOM runtime is eligible |
| `battery.charging.stopped` | Dynamic Android battery state while the DOOM runtime is eligible |
| `battery.level.changed` | Battery percentage/state changes while subscribed |
| `mod.manual.trigger` | Explicit in-app/manual trigger path |

Battery state uses Android's dynamically registered sticky battery broadcast; the source is shared and lifecycle-bound. It is not a promise of force-stop survival or continuous background execution. Event metadata is normalized and duplicate state updates are suppressed. CLI scenarios inject deterministic events and do not represent hardware broadcasts.

Do not infer that every ID in a broader capability catalog is accepted by V4. `device.power.changed`, for example, is catalog metadata but is not currently in the V4 schema event enum.
