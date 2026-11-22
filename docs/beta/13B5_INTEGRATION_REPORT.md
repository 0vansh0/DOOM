# Milestone 13B.5 Integration Report

**Execution date:** 2026-11-22 (Asia/Calcutta)  
**Application:** `org.doom.app`, version `0.1.0-beta.1` (code 2)  
**Device:** Nothing A001, Android 16 / API 36  
**Source revision:** unavailable; workspace has no `.git` metadata.

## Outcome

Milestone 13B.5 is **BLOCKED**. This run completed isolated marketplace integration/security tests, SDK/CLI validation, builder package export/import, Android JVM tests, app unit tests, device instrumentation, and unsigned release artifact builds. Live Android marketplace/feedback authentication, emulator coverage, full portal browser workflows, Shizuku lifecycle, and measured Macrobenchmark remain unverified or failed. The phone's DOOM package disappeared during the connected instrumentation/benchmark run and had to be reinstalled; therefore preservation of its pre-run private app data cannot be confirmed.

## Changes made

- `marketplace-api/Dockerfile`: copy SDK template sources needed by the schema-copy build step.
- `marketplace-api/src/feedback.ts`: pass retention cutoff as an ISO timestamp string cast to `timestamptz`, avoiding postgres.js Date serialization failure.
- `marketplace-api/src/server.ts`: return an actual 429 for rate limiting instead of generic 500.
- `marketplace-api/test/marketplace.integration.test.ts`: use the supported ISO timestamp binding in the retention fixture.
- `mod-engine/src/test/kotlin/org/doom/engine/ArchiveReaderTest.kt`: add importer coverage for Visual Builder exports and all generated template archives.
- `mod-engine/src/test/resources/fixtures/visual-builder-export-13b5.doommod`: actual builder bridge output used by importer regression test.
- `visual-builder/examples/`, `visual-builder/exports/`, `visual-builder/kotlin-fixtures/`: four generated builder projects/scenarios/packages/fixtures.

## Verified results

- Compose services: API and SeaweedFS running; PostgreSQL healthy; storage initialization exited 0. API `/healthz` returned `{"data":{"status":"ok"}}`.
- A new isolated PostgreSQL database `doom_marketplace_m6_20261122` was migrated; existing Compose volumes were retained. S3-backed integration used dedicated test buckets.
- Marketplace API: 22/22 tests pass, including DB/S3 integration, feedback auth/ownership, moderation, quarantine, checksum, retention and rate limiting.
- Android mod engine: 74 tests pass, 0 failures/skips (`:mod-engine:test --rerun-tasks`). `:core-domain:test`: 19 pass. `:app:testDebugUnitTest`: 4 pass. `:app:assembleDebug`: pass.
- Connected A001 instrumentation: 8 tests pass, 0 failures/skips for AccountScreen, Navigation and Persistence classes. BatteryGlow destructive install/uninstall test was excluded.
- CLI: 60/60 pass. Visual Builder: 11/11 pass; production build pass. Developer Portal: 6/6 pass; production build pass. Portal dev server returned HTTP 200; graphical browser workflow was not verified.
- Builder export: four generated example scenarios passed CLI simulation/validation/package checks. A Vite SDK bridge export (`charging-companion`) was inspected by CLI and imported by Android `ArchiveReader`; SHA-256 `61043433a6c488b4aadcec0cbde0e96727dae7739850f752024c44c7e4320b17`.
- Device: A001 debug APK reinstalled after test tooling removed the app package; `monkey` launch succeeded and app process was observed. This verifies launch only, not prior user-data preservation.
- Unsigned release APK and AAB builds succeeded with `-PdoomUnsignedVerification=true`. APK: `app/build/outputs/apk/release/app-release-unsigned.apk`, 22,720,436 bytes, SHA-256 `9C2DAC6E9394B16EBC4359C2705A995FC7A49BDFFF74F2C31435F08038809A98`. AAB: `app/build/outputs/bundle/release/app-release.aab`, 7,864,775 bytes, SHA-256 `0E72F06AED4A87788189B4AEC4E5A25BD23BCCDC25B1C48B7C9339FA766641C9`. Metadata reports package `org.doom.app`, version `0.1.0-beta.1`, code `2`. `apksigner` says APK does not verify; `jarsigner` reports the AAB is unsigned.
- ADB launch timings (five cold, three warm `am start -W` samples): cold median 1,321 ms (1,041–1,758); warm median 86 ms (79–174). One post-launch memory snapshot: PSS 94,259 KB, RSS 195,020 KB. These are ad hoc device observations, not Macrobenchmark results, and there is no comparable historical baseline.

## Failures and blocked checks

- `:benchmark:connectedBenchmarkAndroidTest`: 2/2 failed because the harness could not confirm launch completion / obtain graphics frame timing, including after installing its separate benchmark target. No benchmark result is claimed.
- First parallel CLI/Builder test invocation collided while both rebuilt shared CLI output (`EPIPE`); serial reruns passed (CLI 60/60, Builder 11/11).
- Emulator binary is installed (`D:\Android\Sdk\emulator\emulator.exe`, version 37.3.10), but `emulator -list-avds` is empty and no system image is installed. Emulator execution remains BLOCKED pending a compatible API 35 Google APIs x86_64 image and AVD creation.
- Trusted HTTPS test endpoint: BLOCKED; configured Android marketplace host is `https://marketplace.doom.invalid/`. Credential-bearing Android feedback/catalog/download tests were not run against HTTP.
- Android marketplace publication/install/update and feedback submission/reviewer end-to-end: BLOCKED, despite backend service integration tests passing.
- Shizuku live lifecycle and device Safe Mode/recovery scenarios: BLOCKED/not executed.
- Browser-based portal flows: BLOCKED; only portal tests/build and HTTP availability were verified.
- Signed release verification: BLOCKED; no approved signing credentials/certificate. Unsigned artifacts are not distributable.
- App data preservation on phone: UNKNOWN after instrumentation/benchmark tooling removed the app package; no destructive app-data reset was intentionally run.

See `PENDING_TEST_RESOLUTION.md`, `FINAL_REGRESSION_RESULTS.md`, `END_TO_END_TEST_RESULTS.md`, `FINAL_DEVICE_MATRIX.md`, `PERFORMANCE_RESULTS.md` and `../release/RELEASE_GATE_REPORT.md`.

## 13B.5A secure backend connectivity update (2026-11-22)

This update supersedes the historical HTTPS/device/portal blockers above where current evidence differs. The release gate remains BLOCKED.

- **Changed files:** `marketplace-api/start-dev-https-gateway.ps1` accepts a constrained loopback upstream; `app/src/androidTest/kotlin/org/doom/app/MarketplaceHttpsDeviceTest.kt` adds a real Android repository-to-HTTPS test; `marketplace-api/scripts/13b5a-connectivity-check.mjs` verifies health, migrations, buckets and temporary S3 read/write; `marketplace-api/scripts/13b5a-isolated-e2e.mjs` verifies feedback roles and package review/publication/hash; five requested `docs/beta` reports were updated.
- **Service readiness:** Docker API healthy, PostgreSQL healthy, SeaweedFS running, storage init exited 0. Over CA-verified HTTPS: `/health` HTTP 200 `{"status":"ok"}` and `/ready` HTTP 200 `{"status":"ready"}`. A dedicated connectivity helper confirmed three migrations, both required S3 buckets, and a 23-byte put/get/delete check. Docker volumes were retained.
- **Tests/builds:** `:mod-engine:test --rerun-tasks`, `:app:testDebugUnitTest`, `:app:assembleDebug` passed in one Gradle invocation (74, 4, and successful APK build respectively). Developer Portal `npm test`: 6/6 and `npm run build`: pass. Marketplace API default `npm test`: 17 pass, 11 opt-in tests skipped. With fresh isolated PostgreSQL/S3 integration and a generated 1.0.1 update fixture: 28/28 pass, 0 skipped, including prior-release seeding, new review, publication and admin quarantine. CLI: 60/60. Visual Builder: 11/11.
- **Physical device:** A001/API 36 was authorized; USB reverse was active. Android HTTPS instrumentation `registersLogsInAndSubmitsConsentedFeedbackThroughTrustedHttps`: 1/1 pass. It verified registration, logout, invalid credentials, login, 900-second expiration, missing-consent denial, server-confirmed feedback, and HTTP 401 session clearing. Device report ID: `de9da699-cd80-46ae-8246-0ac13d5fcc11`.
- **Host feedback/admin integration:** server submitted report `f27aa935-deb7-4fe1-9da9-db98f4c6618c`; cross-user read 404; developer triage 403; reviewer read/update 200; one audit event persisted.
- **Host marketplace lifecycle:** upload 201 into `UPLOADED` quarantine; public catalog before review 404; owner validation 200; self-review 403; independent reviewer approval 200; local test publication 201; catalog/download 200. Downloaded artifact matched immutable metadata and response checksum: `856d25665a0594f0659c6d064b85821ef471048e6d5f2a727be6069b16637f10`. This was a local isolated publication, not public.
- **Backend update lifecycle:** the 28/28 DB/S3 suite used the existing `low-battery-alert` version 1.0.0 as the prior release and a generated version 1.0.1 archive. The update remained unavailable until upload, validation, reviewer approval and publication. The published package passed SHA-256 verification; the subsequent admin quarantine test hid it from public catalog/package routes. This was backend/API verification, not an Android update or settings migration test.
- **Portal UI blocker:** portal automated tests/build pass. The in-app browser rendered the registration form but failed its API request with a network error. CORS allowed `http://localhost:5173`; explicit CA-pinned host HTTPS and Android HTTPS passed. Do not claim live browser publishing/triage.
- **App-data caveat:** after the connected instrumentation task, `pm path org.doom.app` was empty. The same APK was reinstalled using `adb install -r` without a data-clear command, and the launcher process was seen. The test runner's package removal means preexisting private app-data preservation is **UNKNOWN**.
- **Still blocked:** Android Discover/download/import/execute/update and settings migration; portal live browser workflow; Shizuku live disconnect/revoke; device Safe Mode/recovery; emulator testing; formal performance remeasurement; signed release readiness.

## Environment blocker resolution follow-up (2026-11-22)

- Backend source and OpenAPI confirm `GET /healthz` is the existing dependency-readiness endpoint. It performs PostgreSQL `SELECT 1` and checks both configured S3 buckets with `HeadBucket`. `GET /health` is not defined and returns 404. Readiness returns only `{"data":{"status":"ok"}}` or a generic 503 `DEPENDENCY_UNAVAILABLE`; it does not expose connection details, bucket names or exception text.
- Added `marketplace-api/test/health.test.ts`: three tests verify readiness checks both dependencies and database/storage failures return sanitized 503. Targeted suite passes 3/3. Full default backend tests pass 14 and skip 11 opt-in integration cases. After setting the explicitly isolated PostgreSQL database, dedicated test buckets and fixture packages, the complete backend suite passes 25/25 including the three health tests and all DB/S3 integration scenarios.
- Current Compose status: API up, SeaweedFS up, PostgreSQL healthy, storage-init exited 0. `GET http://127.0.0.1:8787/healthz` returned HTTP 200 with the generic healthy body, confirming the API's DB query and both S3 bucket checks. On the final probe it took 29.68 seconds; a 5-second probe timed out. PostgreSQL accepted connections and `SELECT 1` eventually returned, but took tens of seconds. This is a dependency responsiveness issue and can exceed normal readiness probe deadlines. SeaweedFS root responded HTTP 403 in 0.90 seconds (expected without auth); bucket-specific readiness was confirmed by `/healthz`. No volume deletion occurred.
- ADB reports Nothing A001 authorized. At check time `org.doom.app` was absent; `adb install -r` of the existing debug APK succeeded without an uninstall or data-clear command. Metadata is version `0.1.0-beta.1`, code 2; launcher intent injection succeeded and app PID was observed. This verifies install/launch but cannot establish or recover private data after the earlier test tooling removed the package.
- Emulator binary was located, correcting the earlier “executable unavailable” assumption. No AVD or system image is present. SDK Manager image acquisition did not complete and was interrupted; emulator tests remain blocked.

## Marketplace health route repair (2026-11-22)

- Replaced the single slow legacy readiness handler with `GET /health` liveness (`{"status":"ok"}`) and `GET /ready` readiness (`{"status":"ready"}` or sanitized 503). `/healthz` remains a readiness alias for compatibility. Readiness verifies PostgreSQL reachability, the three required migration ledger entries, and both quarantine/published S3 buckets. It checks dependencies concurrently, applies a 2.5 second PostgreSQL statement timeout and a 3 second total deadline, and aborts S3 requests on timeout.
- Added six focused health tests for liveness, ready dependencies, PostgreSQL failure, missing migrations, object storage failure, timeout/abort and sanitized errors. `npm test` default: 17 passed, 11 opt-in integration skipped. Full isolated database/S3 mode: 28/28 passed.
- Updated OpenAPI and Compose API healthcheck (Node HTTP probe to `/ready`, 5 second probe timeout). Rebuilt only the API image and recreated only the API container using `docker compose up -d --no-deps api`; PostgreSQL and SeaweedFS containers and volumes were left in place.
- Final live probes: `GET /health` → HTTP 200 `{"status":"ok"}` in 0.014 s; `GET /ready` → HTTP 200 `{"status":"ready"}` in 0.045 s. Compose reported API `healthy`, PostgreSQL `healthy`, SeaweedFS `up`, storage initializer `Exited (0)`.

## Runtime safety / Shizuku follow-up (2026-11-22)

- Updated `MainActivity.onResume()` to refresh the existing Shizuku connection state after returning from Android settings. Runtime execution already rechecks authorization before privileged operations.
- Added regression coverage for cancellation after a reversible write, cancellation during restore, and revoked Android access before recovery. Journal reconciliation does not replay an already-applied write; unavailable authorization keeps the recovery record unresolved.
- Combined Gradle command passed: `:mod-engine:test --rerun-tasks :core-domain:test :app:testDebugUnitTest :assembleDebug`. XML counts: mod-engine 74, core-domain 22, app 4; 100 total, 0 failures, 0 errors, 0 skipped. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 28,256,997 bytes.
- Read-only ADB confirmed Nothing A001/API 36, `org.doom.app` installed with a running process, and the Shizuku package installed. No permission transitions, service interruption, privileged write, or destructive app action was performed. Therefore physical Shizuku grant/revoke/disconnect and Safe Mode/Recovery UI behavior remain **BLOCKED / NOT VERIFIED**. This follow-up does not resolve the other 13B.5A blockers listed above.
