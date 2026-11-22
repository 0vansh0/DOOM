# Android application upgrade tests

| Scenario | Result | Evidence/limitation |
|---|---|---|
| Same-signer signed upgrade | **BLOCKED** | No approved signing identity or previous signed test APK is configured. Candidate beta.2/code 3 is monotonic over device beta.1/code 2. |
| Room migration and persistent app data across signed upgrade | **BLOCKED** | Requires an isolated installation and same-signer signed APKs. No user's installation was replaced or cleared. |
| Installed mods, settings, recovery journal, safe mode, account state | **NOT TESTED** | No before/after upgrade was attempted. |
| Device package inventory | **PASS (read-only inspection only)** | ADB identified Nothing A001, Android 16/API 36. `org.doom.app` remained versionCode 2/versionName `0.1.0-beta.1` with the same `lastUpdateTime` after the isolated core-data instrumentation suite; no install/update occurred. |

The `:core-data:connectedDebugAndroidTest` suite ran 22 tests on A001 with 0 failures/errors/skips. It exercises Room/import/update fixtures in the test module. It does not prove that a signed whole-app APK upgrade preserves the user's production database or account state.

Use a disposable emulator/device with a signed predecessor and candidate using the exact same authorized certificate. Install with `adb install -r` only after verifying application ID and signer equality. Capture database/settings/recovery fixtures and compare them after app launch. Do not uninstall the user's current app as a setup shortcut.
