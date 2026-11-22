# DOOM Final Launch Readiness

**Verification date:** 2026-11-23 (Asia/Calcutta)  
**Application ID:** `org.doom.app`  
**Requested version:** `0.1.0-beta.1`  
**Source revision:** UNAVAILABLE — this checkout has no `.git` metadata (`git rev-parse HEAD` failed).

## Decision summary

| Gate | Result | Evidence |
|---|---|---|
| A. Initial development roadmap complete | **BLOCKED** | The recorded 13B.5E and 13C.1–13C.4 reports retain unresolved marketplace, live-device, signing, operations and release-assurance verification. Several old PASS statements are historical and not reproducible in the current environment. |
| B. Signed release candidate verified | **BLOCKED** | No signed release candidate exists. The available APK fails `apksigner verify`; the AAB is unsigned. No approved signer fingerprint or signing credentials are configured. |
| C. Private beta technically ready | **BLOCKED** | No signed artifact, approved live HTTPS backend, current device verification, final privacy policy or staffed support channel. The current requested beta.1 version also conflicts with the checked-in build, which is beta.2/code 3. |
| D. Public stable release ready | **BLOCKED** | In addition to beta blockers: production endpoint, independent publisher signatures, verified operational backups/monitoring, protected CI evidence, and complete security/device gate evidence are absent. |

These are technical readiness decisions only. No publication, deployment, upload, tester invitation, purchase or production-system modification occurred.

## Release artifact

The only release APK present at verification time is an **unsigned verification artifact**, not suitable for distribution.

| Item | Current result |
|---|---|
| APK | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| APK size | 22,720,440 bytes |
| APK SHA-256 | `cdef06bb17970e591029371ec59305a3b4e88285c595c3a7ec888ce5d4e4cccb` |
| APK signature | **FAIL — unsigned.** Android SDK Build Tools 35.0.0 `apksigner verify --verbose --print-certs` returned `DOES NOT VERIFY`, missing `META-INF/MANIFEST.MF` (exit 1). No signing certificate fingerprint is available. |
| AAB | `app/build/outputs/bundle/release/app-release.aab` |
| AAB size | 7,864,955 bytes |
| AAB SHA-256 | `c0c1808d1d701617e0b59e819ca4ecfee661356918ddb1c494077b684f37413f` |
| AAB signature | **FAIL — unsigned.** `jarsigner -verify -verbose -certs` reported `jar is unsigned`. |
| Manifest identity | Android `aapt dump badging`: package `org.doom.app`, versionCode `3`, versionName `0.1.0-beta.2`, minSdk `31`, targetSdk `35`. |
| Requested version match | **FAIL.** Requested beta.1 does not match current source/artifact beta.2/code 3. Do not relabel the existing artifact. |
| Build provenance | **UNVERIFIED.** No Git revision exists in this checkout, and the release artifacts are unsigned local outputs. |
| Artifact timestamp | APK last-write time is 2026-11-22 23:00:28 +05:30; it was not rebuilt as a release artifact during this verification. |
| Signing configuration | Four keystore environment variables and `DOOM_APPROVED_SIGNER_SHA256` were absent. Gradle requires a non-local HTTPS endpoint, those four variables, and the independent approved fingerprint. No key was created or changed. |

Commands used:

```powershell
& 'D:\Android\Sdk\build-tools\35.0.0\apksigner.bat' verify --verbose --print-certs app\build\outputs\apk\release\app-release-unsigned.apk
& 'D:\Android\Sdk\build-tools\35.0.0\aapt.exe' dump badging app\build\outputs\apk\release\app-release-unsigned.apk
jarsigner -verify -verbose -certs app\build\outputs\bundle\release\app-release.aab
Get-FileHash app\build\outputs\apk\release\app-release-unsigned.apk,app\build\outputs\bundle\release\app-release.aab -Algorithm SHA256
```

`aapt` confirms the APK package metadata; it does not verify signing. No unsigned artifact is selected as a beta package.

## Automated verification executed now

| Command / suite | Result |
|---|---|
| `.\gradlew.bat :mod-engine:test --rerun-tasks` | **PASS** — 76 tests; 0 failures, errors or skips. Gradle build successful. |
| `.\gradlew.bat :app:testDebugUnitTest` | **PASS** — 4 tests; 0 failures, errors or skips. Gradle task successful (outputs up-to-date). |
| `.\gradlew.bat :app:assembleDebug` | **PASS** — debug APK build successful/up-to-date. This is not release signing evidence. |
| `npm test` in `doom-cli` | **PASS** — 60 passed, 0 failed/skipped. |
| `npm test` in `visual-builder` | **PASS** — 11 passed, 0 failed/skipped. |
| `npm test` in `marketplace-api` | **PARTIAL** — 7 test files passed, 1 skipped; 21 passed, 13 skipped, 0 failed. Integration-gated database/storage cases did not run. |
| `npm test` in `developer-portal` | **PASS** — 8 passed, 0 failed/skipped. |
| `npm test` in `website` | **PASS** — 9 passed, 0 failed/skipped. |

The first Gradle command was run with `--rerun-tasks`; app unit tests and debug assembly were run serially after it. Backend test success does not establish current PostgreSQL/S3 readiness because integration cases were skipped.

## Current backend and security status

- `docker info --format '{{.ServerVersion}}'` returned Docker Engine `29.9.3`.
- `docker compose -f marketplace-api/compose.yaml ps -a` showed API, PostgreSQL and SeaweedFS exited with code 255; storage initialization had exited 0.
- Current `GET http://127.0.0.1:8787/health` and `/ready` both failed to connect. PostgreSQL port 5438 was unreachable, and the Compose object-storage container was exited, so no live storage health/read-write check was possible. No migration, upload, download or backup/restore operation was attempted.
- Android release endpoint configuration requires an approved production HTTPS URL and explicitly rejects local/HTTP endpoints before signing. Historical reports identify the configured fallback as `https://marketplace.doom.invalid/`; no reachable approved beta/production HTTPS service is available now.
- Historical 13B.5E isolated backend evidence reports 29/29 marketplace/security integration checks passing. That is useful historical evidence, not current service readiness; the present integration suite is partial and the Compose services are stopped.
- `P0_P1_ISSUE_REGISTER.md` (reviewed 2026-11-22) found no confirmed open P0/P1 **product defect** in its reviewed scope. This is not exhaustive security clearance. Release-assurance risks remain open: protected CI/provenance evidence, independent publisher signatures and physical recovery/security checks.
- Current mod-engine regression suite passes 76/76 and contains deterministic runtime enforcement coverage. Physical Shizuku grant/revoke/disconnect, Safe Mode UI/recovery, live marketplace authorization, and rollback against actual device state remain **UNVERIFIED**. No arbitrary privileged write was attempted.
- Feedback ownership/reviewer controls and moderation are documented and have prior isolated backend evidence. Current DB integration tests were skipped, so live report privacy/triage and current moderation state are **UNVERIFIED**.

## Device verification

The specified ADB executable `D:\platform-tools-latest-windows\platform-tools\adb.exe` was present, but `adb devices -l` returned `no devices/emulators found`. The installed package identity, signer, app launch, navigation, import, enable/disable, dynamic settings, marketplace, account, feedback, Safe Mode, Recovery Center and Shizuku behavior were therefore **NOT TESTED in this verification**. No install/update, uninstall, app-data clear, or privileged device action was attempted.

Historical 13C.2 records Nothing A001 API 36 with beta.1/code 2 installed and beta.2/code 3 candidate not installed. That historical observation is not a current ADB observation.

## Security and P0/P1 findings

**Confirmed open P0/P1 product defects:** None identified in the dated 13B.5E issue register, and no failure appeared in the Android suites executed here. **Confidence is limited** by absent device/backend integration, unsigned release, and incomplete physical Shizuku/recovery scenarios. This is not a PASS for the overall security gate.

Current local verification demonstrates:

- Release signing gate remains configured to fail closed on missing credentials/fingerprint/approved HTTPS endpoint.
- APK signature verification rejects the available unsigned artifact.
- Mod-engine regression tests pass.
- Backend suite has integration skips; moderation/authorship cannot be fully reverified now.

Marketplace moderation, cross-user report isolation, package integrity through the live mobile flow, Shizuku lifecycle and physical Recovery Center persistence are **UNVERIFIED/BLOCKED**, not passed by documentation.

## Tester materials and privacy

- Tester guide explicitly says it is not an invitation/download guide and warns against installing unsigned outputs.
- Permissions and optional Shizuku behavior are documented; the guide says not to grant Shizuku for ordinary mods.
- Known issues and rollback/support procedures exist.
- The privacy review remains a **draft** requiring owner/legal review; there is no final public policy, no self-service account deletion, and no supplied privacy/support contact.
- The support workflow states that no public or staffed support channel is configured. Installation/stop-testing guidance depends on an owner-provided approved channel, so tester materials are incomplete for an actual beta.

## Blockers

1. Provide an approved release signing identity and independently approved SHA-256 fingerprint, then verify signed APK/AAB and same-signer update compatibility. Do not use the unsigned outputs above.
2. Resolve the version discrepancy: the current source is beta.2/code 3, while this launch request proposes beta.1. Confirm the release ledger before any future version change.
3. Configure a trusted HTTPS beta endpoint; start/check the isolated backend and run migrations, PostgreSQL/S3 readiness, auth, feedback, moderation and artifact download/integrity tests.
4. Reconnect Nothing A001 and verify the installed build/signature before any update; run the approved non-destructive device test plan without clearing user data.
5. Finish privacy/legal review, support ownership/contact, deletion disclosures, backup/restore drill, and protected CI/signature provenance evidence.
6. Complete live Shizuku, Safe Mode and recovery checks; do not infer physical behavior from JVM tests.

## Final readiness

- **Initial development roadmap complete:** **BLOCKED** — integration, operations and device verification remain outstanding in the milestone evidence.
- **Signed release candidate verified:** **BLOCKED** — unsigned APK/AAB only; signature verification failed.
- **Private beta technically ready:** **BLOCKED** — no signed artifact, reachable approved HTTPS service, current device evidence, final privacy policy, or staffed support channel.
- **Public stable release ready:** **BLOCKED** — all private beta blockers plus production operations, independent publisher provenance and comprehensive release assurance remain unresolved.

No external distribution or deployment was performed.
