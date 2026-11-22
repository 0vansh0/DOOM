# Device Compatibility Scanner

DOOM's Diagnostics screen performs a point-in-time scan using Android public APIs. It displays Android API/release and manufacturer/model as observed metadata; compatibility is never guessed from a device name.

The scan reports:

- Android API compatibility and known runtime executors.
- Battery broadcast and PowerManager availability.
- Vibration hardware where the API can be probed.
- Overlay special access via `Settings.canDrawOverlays`.
- Notification authorization on Android 13 and later (not applicable on earlier releases).
- A conservative foreground-service status. Foreground eligibility does not promise a background service start; Android 12+ restricts such starts.
- Optional adapter status when a trusted adapter provider is registered.
- Optional Shizuku connection/authorization and ADB-shell versus root identity when the official Shizuku service is connected. The connection status does not imply that any standard mod requires Shizuku.

Status values distinguish `AVAILABLE`, `DENIED`, `UNAVAILABLE`, `UNSUPPORTED`, and `UNKNOWN`. Scan errors are shown separately from capability denials. Refresh runs the probes again. Permission remediation opens the relevant Android flow; users must complete it and return to DOOM, where status is checked again.

Mod details show required capability status and the separate DOOM approval state. A package is not activated just because a permission settings screen was opened. Missing or revoked requirements block activation and runtime action execution.

The package model currently has required capabilities only; optional capability declarations are not part of the published V3/V4 contract. A package without an imported, verified runtime definition is marked as not executable even when its basic metadata is viewable.

## Recorded device check (2026-11-20)

On the connected Nothing device reported by Android as manufacturer `Nothing`, model `A001`, Android 16 / API 36, the Diagnostics screen displayed device details and capability rows. Overlay access, notifications, vibration hardware, battery read and other registered baseline executors reported `AVAILABLE` at the initial scan. A temporary ADB AppOps revocation changed the scanner's overlay row to `DENIED`; AppOps was restored to `allow` afterward and DOOM remained running. After the subsequent connected instrumentation run and APK reinstall, a fresh scan showed overlay and notification authorization as `DENIED` (AppOps default, notification runtime permission not granted). No grant was forced from ADB; the Diagnostics remediation controls remain available. This verifies permission status refresh only; it does not constitute a physical charging-trigger or overlay-effect regression test.
