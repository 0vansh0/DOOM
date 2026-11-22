# Milestone 13B.3 completion report

**Date:** 2026-11-21  
**Application:** `org.doom.app`, debug variant, version `0.1.0-beta.1` / code 2  
**Device:** Nothing A001 (`GalagaIND`), Android API 36, build `B4.1-260928-1137`  
**Source revision:** unavailable because this workspace has no `.git` metadata.

## Source changes

- `core-data/src/main/kotlin/org/doom/data/MarketplaceAuth.kt`: invalidate outstanding authentication attempt generations when logout/session clear occurs; only the current attempt can install an in-memory session.
- `core-data/src/test/kotlin/org/doom/data/MarketplaceAuthRepositoryTest.kt`: delayed login response after logout regression.
- Regression plans/results, device matrix, performance/security status and known issues are captured in the sibling Milestone 13B.3 documents.

## Results

- Mod-engine JVM: 70 passing tests, zero failures/skips.
- Android data/app unit tests: 12 passed (8 core-data feedback/auth repository tests and 4 FeedbackViewModel tests), zero failures/errors/skips. The new delayed-response/logout race test passed.
- Android connected instrumentation: 9 passing tests, zero failures/skips on A001/API 36.
- CLI: 59 passing tests.
- Visual Builder: 11 passing tests; production build passed.
- Developer Portal: 6 passing tests; production build passed.
- Marketplace API: 11 passed and 11 database integration tests skipped. Docker engine unavailable; no DB test was run against non-disposable data.
- Android debug APK: **BUILD SUCCESSFUL** after auth race hardening. `app/build/outputs/apk/debug/app-debug.apk`, 28,256,673 bytes, timestamp 2026-11-21 23:24:14 local.

## Limitations and decision

No emulator images were present. No trusted HTTPS backend was configured. Therefore live account auth, remote feedback submission, marketplace download/update, backend triage/retention and device Shizuku/security lifecycle remain unverified. The release security gate remains blocked per `docs/security/BETA_SECURITY_GATE.md`. Performance metrics were not collected.

Milestone 13B.3 is **BLOCKED** pending isolated backend integration execution, emulator coverage, remaining device security/runtime scenarios, comparable performance measurements and release security gates. The required Gradle runs and connected A001 instrumentation suite completed successfully. This report does not authorize beta distribution or progression to Milestone 13B.4.

## Milestone 13B.5 follow-up (2026-11-22)

The backend integration gap was partially closed: an isolated PostgreSQL database and SeaweedFS buckets were used for 22/22 marketplace API tests, including feedback authorization/ownership and marketplace integrity/moderation. Android feedback submission over trusted HTTPS remains blocked. Current mod-engine suite is 74/74; CLI 60/60; Visual Builder 11/11; Portal 6/6; connected A001 Account/Navigation/Persistence instrumentation 8/8. The benchmark harness still fails (2 tests), no emulator is available, and live Shizuku/Safe Mode recovery remains untested. The phone's app package was removed by connected test tooling and reinstalled; prior app-private state preservation is now unknown. Full final status remains **BLOCKED**; see `13B5_INTEGRATION_REPORT.md`.
## 13B.5B runtime-safety regression addendum (2026-11-22)

The runtime-safety follow-up added interruption/reconciliation cases to `ChangeTransactionCoordinatorTest` and refreshed Shizuku state when the Android activity resumes. The combined Gradle run passed: 74 `mod-engine`, 22 `core-domain`, and 4 app unit tests (100 total; zero failures/errors/skips), plus `:app:assembleDebug`.

This supplements regression evidence only; it does not complete the previously pending 13B.3 categories such as marketplace Android installation/update, emulator coverage, performance measurements, Developer Portal browser workflow, or live Shizuku and physical recovery tests. Device checks were read-only. See `PENDING_TEST_RESOLUTION.md` and `13B5B_COMPLETION_REPORT.md`.

