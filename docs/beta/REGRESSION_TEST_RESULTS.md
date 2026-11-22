# DOOM regression test results

**Execution date:** 2026-11-21  
**Workspace:** `E:\DOOM`  
**Source revision:** unavailable; this checkout has no `.git` metadata.  
**Android:** Nothing A001, API 36, build `B4.1-260928-1137`; debug variant.

## Executed results

| Command / suite | Result |
|---|---|
| `:mod-engine:test --rerun-tasks` | **PASS** — 70 tests, 0 failures/errors/skips. |
| `:core-data:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks` | **PASS** — 12 tests: 8 core-data feedback/auth repository tests and 4 app FeedbackViewModel tests, 0 failures/errors/skips. |
| `:core-data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --rerun-tasks` | **PASS** — 228 Gradle tasks executed; debug APK assembled. |
| `:app:connectedDebugAndroidTest` (rerun after auth hardening) | **PASS** — 9 tests, 0 failures, 0 skipped on A001/API 36. XML: `app/build/outputs/androidTest-results/connected/debug/TEST-A001 - 16-_app-.xml`. |
| `npm test` (`doom-cli`) | **PASS** — 59 tests, 0 failures/skips. Includes CLI create/validate/package/inspect/test, reproducible package and V3/V4 shared conformance fixtures. |
| `npm test` (`visual-builder`) | **PASS** — 11 tests, 0 failures/skips. |
| `npm run build` (`visual-builder`) | **PASS** — TypeScript and Vite production build. |
| `npm test` (`developer-portal`) | **PASS** — 6 tests, 0 failures/skips. |
| `npm run build` (`developer-portal`) | **PASS** — TypeScript and Vite production build. |
| `npm test -- --run` (`marketplace-api`) | **PARTIAL** — 11 tests passed, 11 DB integration tests skipped. TypeScript and CLI build passed. |
| Docker Desktop Linux engine | **BLOCKED** — Docker client could not open `//./pipe/dockerDesktopLinuxEngine`. |
| Android emulator matrix | **NOT TESTED** — no SDK system images were present under `D:\Android\Sdk\system-images`. |

The backend integration tests are not counted as passed. They require PostgreSQL/object storage and clear/reseed their configured tables. No migration or destructive database test was run during this verification.

## Device test detail

All 9 instrumented tests passed:

- Account login form validation and callback.
- Signed-in summary/logout callback.
- Authentication error presentation.
- Registration mode and expired-session notice.
- Battery Glow installation, in-app preview, permission-required state, and uninstall with overlay permission denied.
- Top-level navigation and diagnostics screen.
- Settings → Account and back navigation.
- Feedback route, local export affordance and sign-in guidance.
- Persistence via an isolated temporary Room database.

These are UI and local persistence tests. They do not prove live authentication, remote report submission, actual charging-trigger delivery or external overlay behavior.

## Defect hardening

Source inspection found a stale-auth-response race: the repository established a successful session before its caller could observe that logout had occurred. Added a session-attempt generation guard and deterministic delayed-response regression `MarketplaceAuthRepositoryTest.lateSuccessfulResponseCannotRestoreSessionAfterLogout`. It passed in the final core-data run and protects against late responses restoring the in-memory session after logout. It is a source-derived risk; no pre-fix failing run was captured.

No other reproducible regression was found by the executed suites. Uncovered cases remain unverified, not passed.

## 13B.5B runtime-safety addendum (2026-11-22)

The follow-up added interruption/reconciliation cases to `ChangeTransactionCoordinatorTest` and refreshed Shizuku state from `MainActivity.onResume()`. The combined Gradle run passed: mod-engine 74, core-domain 22, and app unit tests 4 (100 total; 0 failures, 0 errors, 0 skipped), with `:app:assembleDebug` successful. Read-only ADB confirmed Nothing A001/API 36, DOOM installed/running, and the Shizuku package installed. No live authorization transition, privileged write, physical Safe Mode/recovery sequence, emulator run, or app-data reset was performed. Those checks remain unverified. See `PENDING_TEST_RESOLUTION.md` and `13B5B_COMPLETION_REPORT.md`.
