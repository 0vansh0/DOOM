# DOOM device compatibility matrix

**Updated:** 2026-11-21

| Environment | OS/API | Build | Result | Scope / gaps |
|---|---|---|---|---|
| Nothing A001 (`GalagaIND`) | Android 16 / API 36 (instrumentation reports `A001 - 16`) | `B4.1-260928-1137`; app debug `0.1.0-beta.1` code 2 | **PASS — 9/9 instrumentation tests** | Account UI, navigation, diagnostics, Feedback Center local route, isolated DB persistence, Battery Glow in-app preview and mod cleanup while overlay access is denied. No live server, charging trigger or external overlay test. |
| Android 12 / API 31 emulator | API 31 | N/A | **NOT TESTED** | No emulator system image available. |
| Android 13 / API 33 emulator | API 33 | N/A | **NOT TESTED** | No emulator system image available. |
| Android 14 / API 34 emulator | API 34 | N/A | **NOT TESTED** | No emulator system image available. |
| Android 15 / API 35 emulator | API 35 | N/A | **NOT TESTED** | No emulator system image available. |
| Additional physical OEMs | Unknown | N/A | **NOT TESTED** | No devices supplied/connected. |

The configured app SDK values are minSdk 31, targetSdk 35, compileSdk 35. API 36 was exercised on one Nothing device only; this does not establish general Android 12–16 or OEM compatibility.

## Capability scenario status on A001

| Scenario | Status |
|---|---|
| Overlay denied: in-app preview and permission-required state | **PASS** in instrumentation |
| Overlay granted: external overlay rendering | **NOT TESTED** |
| Shizuku unavailable/disconnected/granted/revoked | **NOT TESTED** in this run |
| Marketplace online/offline and downloads | **NOT TESTED** on device |
| Live authentication and feedback submission | **NOT TESTED**; no trusted test endpoint |
| Safe mode, process death, reboot and recovery | **NOT TESTED** by device instrumentation |
