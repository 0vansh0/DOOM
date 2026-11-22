# Milestone 13B.5 Pending Test Resolution — 13B.5A update

Verification date: 2026-11-22 (Asia/Calcutta). This table supersedes earlier 13B.5 notes that said ADB was absent and no trusted HTTPS endpoint existed.

| Previously pending item | Current result | Evidence / remaining blocker |
|---|---|---|
| Feedback submission tests | **PASS (Android repository over HTTPS + backend)** | A001 instrumentation 1/1 passed through trusted HTTPS. Server report `de9da699-cd80-46ae-8246-0ac13d5fcc11`; host reviewer/ownership test also passed. Compose UI/local export after logout remain unverified. |
| Admin authorization tests | **PASS (API integration)** | Host test confirmed developer denied (403), reviewer can read/update (200), cross-user read denied (404), audit event persisted. Android-to-portal reviewer UI not verified. |
| Marketplace integration tests | **PASS (host backend/update lifecycle); BLOCKED (Android install/update)** | Isolated package upload→quarantine→validation→review→publication→catalog→download/hash passed. DB/S3 tests now verify seeded v1.0.0→reviewed v1.0.1 and quarantine (28/28). Android Discover/download/import/execute/update not run. |
| Shizuku lifecycle tests | **PARTIAL / BLOCKED** | No connected Shizuku lifecycle/revocation was tested. Existing deterministic runtime tests are not a device Shizuku test. |
| Safety Mode and recovery tests | **PARTIAL / BLOCKED** | Full device effects/recovery were not tested. Do not claim physical recovery verification. |
| Developer Portal tests | **PASS (automated); BLOCKED (live browser)** | 6/6 tests and production build pass. Portal rendered, but in-app browser could not fetch the HTTPS API, so no live browser registration/upload/moderation was completed. |
| Performance measurements | **FAIL (Macrobenchmark); PARTIAL (ad hoc)** | Prior Macrobenchmark had 2 failures; device launch/memory samples are not an equivalent formal baseline. |
| Emulator testing | **BLOCKED** | Emulator executable present but no AVD or installed system image. |
| Visual Builder export verification | **PASS (prior CLI/importer verification); BLOCKED (Android execution)** | Existing package validation/importer evidence applies; this run did not execute a newly exported package on device. |
| Developer Portal verification | **PASS (tests/build/API); BLOCKED (full portal workflow)** | Browser API call failed; backend route integration itself passed. |

## Additional observed limitation

The connected Gradle instrumentation task unexpectedly left `org.doom.app` absent. The same debug APK was reinstalled with `adb install -r`; no explicit `pm clear` or uninstall command was issued. Prior private app-data preservation is **UNKNOWN**. Further connected Gradle tests were not run.

The release gate remains **BLOCKED** due to the unverified portal browser workflow, Android marketplace install/update path, Shizuku/recovery device checks, emulator/performance failures, and unsigned release signing requirements. See `13B5_INTEGRATION_REPORT.md` for current consolidated evidence.

## 13B.5B runtime safety follow-up (2026-11-22)

| Workstream | Result | Evidence / remaining blocker |
|---|---|---|
| Shizuku state refresh | **PASS (source/build)** | `MainActivity.onResume()` calls the existing state refresh. A001/API 36 has DOOM and the Shizuku package installed, but no live grant/revoke/disconnect transition was performed. |
| Shizuku lifecycle | **PARTIAL / BLOCKED (device)** | JVM coverage passes for mapped states and operation policy. Package presence is not evidence of service running or authorization; live lifecycle remains unverified. |
| Safe Mode and fault isolation | **PASS (JVM suite); BLOCKED (physical UI/effects)** | Existing mod-engine tests pass; no on-device activation/restart/recovery sequence was performed. |
| Write journal and rollback interruption | **PASS (deterministic tests)** | Added three tests for cancellation after apply, cancellation after restore, and revoked Android authorization before recovery. Reconciliation observes state without replaying a write; revoked access preserves the journal. |
| Required Android command | **PASS** | Combined `:mod-engine:test --rerun-tasks :core-domain:test :app:testDebugUnitTest :app:assembleDebug`: 100 tests, 0 failures/errors/skips; build successful. |

No physical privileged write, app-data reset, or Shizuku authorization change was performed. 13B.5B is **BLOCKED** for live Shizuku lifecycle and physical Safe Mode/Recovery verification; this does not change the existing overall 13B.5 release blockers.
