# Capability matrix

`DeviceCapabilityScanner` probes Android services and permissions at the time of an explicit diagnostic export or compatibility check. It does not infer support from a model name. Export Diagnostics includes the API level, Battery Saver value and per-capability probe results. Availability is distinct from per-mod grants and from an observed successful effect.

| Capability | Tier | API floor | Device probe | Android consent | DOOM approval |
| --- | --- | --- | --- | --- | --- |
| battery.read | STANDARD | 31 host minimum | Sticky battery broadcast present | None | Required |
| overlay.render | STANDARD | 31 | Settings.canDrawOverlays | Special overlay authorization | Required |
| clock.read | STANDARD | 31 | Trusted java.time.Clock provider | None | Required |
| ui.preview | STANDARD | 31 | Host renderer, eligible foreground UI required | None | Required |
| device.power.read | STANDARD | 31 | PowerManager service | None | Required |
| haptics.vibrate | STANDARD | 31 | VibratorManager default vibrator and hasVibrator | VIBRATE normal manifest permission | Required |

AVAILABLE means the API/service exists and any probed Android overlay access is granted. DENIED means Android authorization is missing. UNAVAILABLE means missing hardware/service or a failed probe. UNSUPPORTED means no trusted registry entry. Per-mod approval remains a separate Room grant. Unknown imported capabilities are rejected before installation. A haptic device may still suppress actual vibrations according to system policy.

clock.read exposes a gated trusted clock provider to native adapters. This release has no public clock display template or timer event. It must not be advertised as a working Floating Clock mod.

Actual Nothing phone observations and test results are recorded in `MILESTONE_6.md`. API 31 and other OS/OEM behavior must be tested independently. Probe results are point-in-time observations, rather than certification of all background states.

## Recorded observations (19 November 2026)

| Probe | Nothing A001, Android 16 API 36 | Android 12 API 31 emulator |
| --- | --- | --- |
| battery.read | AVAILABLE | AVAILABLE |
| overlay.render | AVAILABLE (authorization already granted) | DENIED |
| clock.read | AVAILABLE | AVAILABLE |
| ui.preview | AVAILABLE | AVAILABLE |
| device.power.read | AVAILABLE, Battery Saver false | AVAILABLE, Battery Saver false |
| haptics.vibrate | AVAILABLE hardware probe | AVAILABLE simulated vibrator API |

Sources: [Nothing diagnostic export](qa/milestone-6/nothing-diagnostics.json) and [API 31 runtime probe log](qa/milestone-6/api31-logcat.txt). These are probe results. The physical phone rendered the independent API 2 preview and persisted its custom setting. Native haptic submission/cleanup was exercised on the emulator. Physical vibration and actual Battery Saver state-change events have not been verified.
