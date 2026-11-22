# Milestone 13B Final Integration Audit

**Audit date:** 2026-11-22 (Asia/Calcutta)  
**Application:** `org.doom.app`, version `0.1.0-beta.1` / versionCode 2  
**Workspace revision:** unavailable; this checkout has no Git metadata.  
**Overall Milestone 13B:** **BLOCKED** for production preparation and stable distribution.

## Decisions

| Decision | Result | Reason |
|---|---|---|
| Ready to begin production preparation? | **BLOCKED** | Core Android marketplace import/update behavior and physical runtime recovery are not verified; API endpoint defaults to `https://marketplace.doom.invalid/`; signed release identity is unavailable. |
| Ready for stable public distribution? | **BLOCKED** | No release signing variables/approved signer are configured; no usable production HTTPS endpoint; marketplace publisher metadata lacks an independent signature; physical security, update and recovery checks remain incomplete. |

These decisions do not authorize deployment, publication, or distribution.

## Regressions

- `DOOM-REG-001` (late auth response restoring session after logout): previously fixed in `MarketplaceAuth.kt`; deterministic regression `lateSuccessfulResponseCannotRestoreSessionAfterLogout` is documented and covered by core-data tests.
- 13B.5C's “confirmed regressions fixed” item was **not applicable**: no product regression was reproduced in that workstream. One exploratory test incorrectly expected a charging-start edge trigger to execute twice without receiving the required rearming event. The test was corrected; this was not a product defect.
- No confirmed open P0/P1 product defect was found in the reviewed current reports. Release-assurance blockers remain open and are not accepted as safe for distribution.

## Verification executed in this audit

| Command | Result |
|---|---|
| `./gradlew.bat :mod-engine:test --rerun-tasks` | **PASS** — 76 tests; 0 failures/errors/skips. |
| `./gradlew.bat :core-data:testDebugUnitTest :core-domain:test` | **PASS** — core-data 8 and core-domain 22; 0 failures/errors/skips. |
| `./gradlew.bat :app:testDebugUnitTest` | **PASS** — 4 tests; 0 failures/errors/skips. |
| `./gradlew.bat :app:assembleDebug` | **PASS** — debug APK assembled. |
| `npm test` in `doom-cli` | **PASS** — 60/60. |
| `npm test -- --run` and `npm run build` in `visual-builder` | **PASS** — 11/11 tests and Vite production build. |
| `npm test` and `npm run build` in `developer-portal` | **PASS** — 6/6 tests and production build. |
| `npm test` in `marketplace-api` | **PARTIAL** — 17 pass, 11 skipped; database integration is opt-in. |
| `npm audit --audit-level=moderate` in each of four npm projects | **PASS** — zero vulnerabilities reported for CLI, Builder, API and Portal. |
| Docker Compose service check | **BLOCKED** — Docker Desktop Linux engine pipe was unavailable. |

The Builder test first failed once while it and another npm command concurrently rebuilt the shared `doom-cli/dist/schema` directory (`EPIPE`). The serialized rerun passed. This was a test-command execution collision; avoid parallel package scripts that rebuild shared CLI artifacts.

Current Android JVM/unit total across the four XML result directories is **110 passed** (76 mod-engine + 22 core-domain + 8 core-data + 4 app), with no failures, errors or skips. The separate backend DB/S3 tests remain skipped in the default test run.

## Historical evidence reconciled

- **12A — Security audit: BLOCKED.** Local findings record tool advisories fixed and moderation race fixed, while independent publisher authenticity, physical runtime checks and release assurance remain open (`../security/FINDINGS.md`, `../security/BETA_SECURITY_GATE.md`). The prior “no CI” statement is stale: workflow files now exist, but remote enforcement is unverified.
- **12B — Performance: PARTIAL/BLOCKED.** A limited seven-sample startup baseline and one idle PSS snapshot exist; Macrobenchmark failed 2/2, with active-mod memory, energy, frame and workflow/overlay latency unmeasured (`../performance/PERFORMANCE_BASELINE.md`).
- **12C — Release engineering: BLOCKED.** Unsigned APK/AAB verification exists; approved signer values are absent and unsigned artifacts are not distributable (`RELEASE_GATE_REPORT.md`).
- **13A — Beta preparation: BLOCKED.** Beta/security gates prohibit external distribution; tester materials do not constitute authorization or a signed release (`../beta/BETA_RELEASE_GATE.md`).
- **13B.1A/1B/1C — Account and feedback: implementation/unit evidence PASS, device evidence PARTIAL.** Current core-data auth/feedback unit tests pass 8/8. Stored A001 HTTPS repository instrumentation passes 1/1 and records a real report ID; it did not automate the Feedback Center UI or prove the current default endpoint works (`../beta/FEEDBACK_END_TO_END.md`).
- **13B.2 — Admin triage: historical host/API PASS, current DB rerun BLOCKED.** Prior report records reviewer update, cross-user denial and audit persistence; current marketplace integration cases were skipped due unavailable Docker (`../beta/FEEDBACK_TRIAGE_VERIFICATION.md`).
- **13B.3 — Regression: PARTIAL/BLOCKED.** Current mod-engine/app tests pass, but emulator, physical recovery, Android marketplace install/update and measured performance are not complete (`../beta/13B3_COMPLETION_REPORT.md`, `../beta/PENDING_TEST_RESOLUTION.md`).
- **13B.4 — Developer ecosystem: PARTIAL.** Current CLI/Builder tests and Builder package/runtime JVM checks pass; no current physical import/effect or live portal browser workflow was completed (`../beta/13B5C_VERIFICATION_REPORT.md`).
- **13B.5A — Backend integration: historical isolated suite PASS, current service BLOCKED.** Prior report records DB/S3 and HTTPS feedback tests; current Docker Compose cannot connect to the engine, so the database suite was not reproduced (`../beta/13B5_INTEGRATION_REPORT.md`).
- **13B.5B — Runtime safety: JVM PASS, physical lifecycle BLOCKED.** Deterministic authorization and journal tests pass; the milestone report itself leaves live Shizuku and physical Safe Mode/recovery unverified (`../beta/13B5B_COMPLETION_REPORT.md`).
- **13B.5C — Builder/export: PASS in CLI/JVM, performance/device portions BLOCKED.** Four exports validate and simulate; the report records failed historical benchmarks, no usable AVD, and no physical package execution (`../beta/13B5C_VERIFICATION_REPORT.md`).

- Stored A001 instrumentation XML reports 1/1 Android HTTPS feedback repository test and 22/22 core-data instrumentation tests passing. The feedback report ID and host-side reviewer/ownership outcomes are recorded in `../beta/FEEDBACK_END_TO_END.md`. Those do not verify the current interactive feedback UI or Android marketplace install/update flow.
- Prior isolated backend evidence records a successful DB/S3 integration suite (28/28 in the 13B.5A follow-up). It is historical evidence; this audit could not rerun those cases because Docker was unavailable. Current default backend result is 17 pass / 11 skip.
- Visual Builder exports and CLI scenarios were revalidated: four `.doommod` archives pass CLI validation and four desktop scenarios pass. Android importer/runtime checks use generated fixtures and a fake executor, not physical overlay execution.
- Historical performance measurements are limited to seven process-cold launches (p50 620 ms, p95 835 ms) and one idle memory snapshot (PSS 114,143 KB). Macrobenchmark has 2 failures; active-mod memory, first-frame, energy and overlay/workflow latency remain unmeasured.
- A001/API 36 was attached. Read-only ADB launch succeeded, and the installed package reports code 2/version `0.1.0-beta.1`. No current install/update or mod action was performed. App-private data preservation remains unknown after earlier test tooling removed the package.
- `emulator.exe` exists, but no AVD is listed and the Android 35 x86_64 image directory contains only `.installer`.
- Release signing variables and the approved signer fingerprint are not configured. The release URL defaults to the reserved `.invalid` hostname. Existing unsigned APK/AAB evidence is not distributable.
- Exact absent signing configuration: `DOOM_KEYSTORE`, `DOOM_STORE_PASSWORD`, `DOOM_KEY_ALIAS`, `DOOM_KEY_PASSWORD`, and `DOOM_APPROVED_SIGNER_SHA256`.

## Source changes in this audit

No runtime or application source defect was confirmed, so no production source change was made. The evidence and gates were reconciled in the reports linked below. The preceding 13B.5C importer/runtime tests remain the focused source test changes for that milestone.

See `REGRESSION_STATUS.md`, `SECURITY_GATE.md`, `DEVICE_COMPATIBILITY.md`, `PERFORMANCE_SUMMARY.md`, `KNOWN_BLOCKERS.md` and `RELEASE_GATE_REPORT.md` for details.

## Milestone 13B.5E addendum (2026-11-22)

The earlier Docker-unavailable and backend-integration-skipped status is historical and is superseded for host-side marketplace verification: Compose is running, `/health` and `/ready` both returned 200, three migrations were applied to isolated `doom_marketplace_13b5e_20261122_final`, and real PostgreSQL/SeaweedFS API integration passed 29/29. The test now explicitly verifies rejection-reason persistence and public invisibility; see `MARKETPLACE_SECURITY_VERIFICATION.md`.

The marketplace/client gate remains **BLOCKED** because ADB currently returns no devices, so Android Discover/download/import/execute/update was not performed. Production preparation and stable distribution remain blocked. No confirmed open P0/P1 product defect was found in reviewed evidence; separate release-assurance risks remain blockers.
