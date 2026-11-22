# Device Compatibility — Final Milestone 13B Audit

## Physical device

| Device | OS/API | Evidence | Result / limit |
|---|---|---|---|
| Nothing A001 (`GalagaIND`) | Android 16 / API 36 | Current ADB read-only check: package installed, versionCode 2, versionName `0.1.0-beta.1`; `monkey -p org.doom.app 1` returned with app PID observed. Stored instrumentation XML: Android feedback repository 1/1 and core-data 22/22. | Launch and recorded instrumentation PASS. No current screen-by-screen audit, mod package import, external overlay, Shizuku transition, Safe Mode recovery or update was performed. User data preservation is UNKNOWN after earlier test tooling removed the app package. |

## Emulator

`D:\Android\Sdk\emulator\emulator.exe` exists. `-list-avds` returned no AVDs. The Android 35 Google APIs x86_64 system-image folder contains only `.installer`, not a usable image. Emulator execution is **BLOCKED**. Other API levels and OEM devices are **NOT TESTED**.

## Compatibility limits

Project values: minSdk 31, targetSdk 35, compileSdk 35. A single A001/API 36 observation does not establish API 31–35 or other OEM compatibility. No app install/update was run in this audit, and no app data was cleared.
