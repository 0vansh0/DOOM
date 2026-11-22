# Device Compatibility Matrix

Updated 2026-11-21. Only the connected device was identified during this work session.

| Device/environment | Android/API | Result | Evidence / limits |
|---|---|---|---|
| Nothing A001 (ADB manufacturer: Nothing, model: A001) | Android 16 / API 36 | PARTIAL | Debug APK installed and launch initiated. Manual process-cold ActivityManager timing sampled. Macrobenchmark tests failed; no functional mod scenarios were rerun. |
| Android 12 emulator / API 31 | NOT RUN | BLOCKED | No emulator test executed in this pass. |
| Android 13 emulator / API 33 | NOT RUN | BLOCKED | No emulator test executed in this pass. |
| Android 14 emulator / API 34 | NOT RUN | BLOCKED | No emulator test executed in this pass. |
| Android 15 emulator / API 35 | NOT RUN | BLOCKED | No emulator test executed in this pass. |

Do not infer manufacturer-wide or model-wide compatibility from the one A001 device. The device display name “Nothing CMF” was supplied by the user; ADB returned model identifier `A001`.
