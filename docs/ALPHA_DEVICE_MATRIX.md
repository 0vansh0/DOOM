# Phase 1 alpha device matrix

Use the device's recorded Android version, API and build. Select only the intended ADB serial for each command. Keep existing app data when a signature mismatch prevents upgrade. Never remove a tester's installation to resolve a signing conflict without approval.

| Target | Purpose | Evidence |
| --- | --- | --- |
| Nothing phone | Real OEM overlay access, charging, renderer, lifecycle, persistence | Blocked at ADB connection. Windows identified Nothing as the manufacturer and `A001` as its USB media label. |
| Motorola Edge 60 Stylus | Originally requested hardware | Substituted by the Nothing phone for this run |
| Android 12 emulator | Minimum API regression, simulated charging, recovery | See the Milestone 5 results |
| Android 13 / API 33 | Permissions and lifecycle | Image unavailable on this host |
| Android 14 / API 34 | Background and service behavior | Image unavailable on this host |
| Android 15 / API 35 | Newer overlays and service behavior | Image unavailable on this host |
| Latest available emulator | Forward compatibility | API 31 is the only installed image |

## Scenarios

1. Fresh install, import, save appearance, force-stop, relaunch, uninstall and reinstall.
2. Real charger connection and disconnection while DOOM is focused, in the background, and with the screen locked.
3. Deny, grant and revoke Android overlay access. Verify the in-app appearance preview remains available.
4. Enable, disable, rapidly toggle, enter safe mode and recover after restart.
5. Test a process kill, resource pressure, and recovery before and after the Room install commit.
6. Install ordinary CLI-generated packages. Check a wrong definition version and corrupt package.
7. Check rotation, larger fonts, light and dark contrast, and animation clipping.
8. Measure repeated cold starts, periodic process memory, frame timing, and unplugged battery behavior. Record hardware, workload and ambient conditions.

## Result record

| Field | Value |
| --- | --- |
| Device and manufacturer | |
| Model | |
| Android version and API | |
| OS build | |
| DOOM version | |
| Package ID | |
| Scenario | |
| Expected result | |
| Actual result | |
| Pass / Fail / Blocked | |
| Log reference | |
| Screenshot or recording | |

Background and locked-screen scenarios expect foreground-only DOOM effects to be absent. Use a physical charger and device observation for OEM behavior. Host-injected battery status is useful for deterministic emulator regression only.

## Recorded environments

The Android 12 run used Android SDK built for x86_64, API 31, build `SE1A.220826.006.A1`. The signed app reported `0.1.0-alpha`. The test installed Battery Glow and Amber Card, exercised preview and overlay behavior with simulated battery state, and exported diagnostics. Timing data and the preview screenshot are in [the alpha report](MILESTONE_5.md).

For the requested Nothing run, Windows exposed a media interface. `adb devices -l` never reported an authorized physical device. The model, Android release, API and OS build therefore remain unrecorded. No app installation or charger test occurred on that phone.
