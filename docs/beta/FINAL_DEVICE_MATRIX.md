# Final Device Matrix — Milestone 13B.5

| Environment | API / build | Executed coverage | Status |
|---|---|---|---|
| Nothing A001 (`GalagaIND`) | Android 16 / API 36; debug 0.1.0-beta.1 (code 2) | Connected AccountScreen/Navigation/Persistence tests: 8 pass. After test tooling removed the app package, debug APK was reinstalled; monkey launch succeeded and PID observed. | **PARTIAL**; app-private data preservation from before instrumentation is unknown. |
| Android emulator | App minSdk 31, targetSdk 35 | Emulator binary 37.3.10 present. `-list-avds` returned none; no system image is installed. SDK Manager image acquisition did not complete. | **BLOCKED** |
| Other physical devices / OEMs | — | Not available. | **NOT TESTED** |

The connected test did not include external overlays, live charging behavior, Shizuku, real HTTPS account/feedback/marketplace, full Safe Mode recovery, or marketplace update. Do not infer support for other APIs/OEMs from this single phone.
