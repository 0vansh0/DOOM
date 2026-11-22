# Regression Status — Milestone 13B Final Audit

## Current reruns

- Mod engine: 76 tests passed, no failures/errors/skips (`:mod-engine:test --rerun-tasks`). Coverage includes Definition V3/V4, importer security, workflow order and bounds, capability revocation, Safe Mode/resource cleanup, and fault isolation.
- Core-domain: 22 passed; core-data: 8 passed. Auth/feedback tests cover stale login after logout, consent/auth rejection, trusted-origin bearer mapping, insecure endpoint rejection, expired token, 401 clearing, 429 mapping, and unknown network outcome. Transaction tests cover journal/restore conflicts and interruption.
- App unit: 4 passed, no failures/errors/skips (`:app:testDebugUnitTest`).
- Android debug build: `:app:assembleDebug` passed.
- CLI: 60/60; Visual Builder: 11/11 and production build passed; Developer Portal: 6/6 and production build passed.
- Marketplace API default: 17 passed, 11 skipped. Database/object-store integration was unavailable in this audit.

Total current Android JVM/unit XML results: 110 passed, 0 failures, 0 errors, 0 skipped. This does not count historical connected-device XML as a current test run.

## Confirmed regression inventory

| ID | Finding | Status | Evidence |
|---|---|---|---|
| DOOM-REG-001 | Late authentication response could restore session after logout | Fixed | `MarketplaceAuth.kt` generation invalidation and deterministic `lateSuccessfulResponseCannotRestoreSessionAfterLogout` test; earlier core-data test reports. |
| 13B.5C-REG-001 | None confirmed | Not applicable | 13B.5C found no product defect. A failed exploratory test assertion was a mistaken assumption about charging event edge rearming; the final runtime test passes. |

No source issue was confirmed during this final audit; no additional runtime fix was warranted. The unresolved integration/performance/device items are verification blockers, not accepted regressions.

## Unverified scenarios

Android marketplace installation/update and settings migration; interactive Feedback Center; current DB/S3 test suite; live Shizuku loss/reconnect; physical Safe Mode/recovery; emulator compatibility; active-mod memory/overlay/workflow performance; signed update compatibility.

## Milestone 13B.5E addendum (2026-11-22)

- Marketplace API with opt-in PostgreSQL and SeaweedFS S3 integration: **30 passed, 0 failed, 0 skipped** across 6 files. Includes authenticated upload, moderation approval/rejection, quarantine access denial, immutable publication, exact checksum/byte comparison, update review, and malformed-package validation.
- Mod engine rerun: **76 passed, 0 failed, 0 errors, 0 skipped**. App unit tests: **4 passed, 0 failed, 0 errors, 0 skipped**. Debug APK build passed.
- CLI: **60/60**; Developer Portal: **6/6**. Visual Builder: the concurrent first attempt hit a shared CLI-build `EPIPE`; sequential retry passed **11/11**.
- Current ADB list was empty. Physical Android marketplace installation/update remains unverified; backend tests do not substitute for device verification.
