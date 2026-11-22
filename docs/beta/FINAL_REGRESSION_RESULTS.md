# Final Regression Results — Milestone 13B.5

**Date:** 2026-11-22 (Asia/Calcutta). Results below are the commands executed in this workspace. The repository has no Git metadata, so no commit/revision is available.

| Suite / command | Outcome |
|---|---|
| `.\gradlew.bat :mod-engine:test --rerun-tasks` | PASS, 74 tests, 0 failures/errors/skips |
| `.\gradlew.bat :core-domain:test :app:testDebugUnitTest :app:assembleDebug` | PASS; core-domain 19 tests, app 4 tests, debug assemble successful |
| Connected `AccountScreenTest`, `NavigationTest`, `PersistenceTest` on A001/API 36 | PASS, 8 tests, 0 failures/skips; BatteryGlow destructive test excluded |
| `npm test` in `doom-cli` | PASS, 60/60 |
| `npm test` in `visual-builder` | PASS, 11/11 |
| Visual Builder `npm run build` | PASS (run earlier in this workstream) |
| `npm test` in `developer-portal` | PASS, 6/6 |
| Developer Portal `npm run build` | PASS (run earlier in this workstream) |
| `npm test` in `marketplace-api` (default) | PASS, 14; 11 opt-in DB/S3 tests skipped |
| `npm test` in `marketplace-api` (isolated DB/S3 environment) | PASS, 25/25 including health, DB and SeaweedFS integration |
| `npm run db:migrate` against isolated DB | PASS, migrations applied |
| `:benchmark:connectedBenchmarkAndroidTest` | FAIL, 2 tests; activity launch/frame timing could not be observed |
| First concurrent CLI/Builder `npm test` | FAIL due Windows EPIPE shared build output contention; serialized reruns both PASS |
| `:app:assembleRelease -PdoomUnsignedVerification=true` | PASS, unsigned APK build |
| `:app:bundleRelease -PdoomUnsignedVerification=true` | PASS, unsigned AAB build |

No test files were deleted or weakened for these results. Backend defects discovered in rate limiting and retention were fixed, and Android importer tests were added for generated builder exports.
