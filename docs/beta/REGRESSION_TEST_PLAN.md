# DOOM regression test plan

**Date:** 2026-11-21  
**Application:** `org.doom.app` (`minSdk 31`, `targetSdk 35`, version `0.1.0-beta.1`, code 2)

## Test inventory and commands

| Area | Existing runner | Coverage / limitation |
|---|---|---|
| Mod engine | `:mod-engine:test` | Parser/importer, V3/V4, conditions, workflows, capability compatibility, runtime lifecycle and SDK conformance JVM tests. |
| Android data/app | `:core-data:testDebugUnitTest`, `:app:testDebugUnitTest` | Feedback repository and ViewModel unit tests; auth repository race regression. |
| Android UI/device | `:app:connectedDebugAndroidTest` | Compose tests on connected hardware; not an emulator test. |
| CLI | `npm test` in `doom-cli` | Build plus CLI tests and shared conformance fixtures. |
| Visual Builder | `npm test`, `npm run build` in `visual-builder` | Vitest suite and production Vite/TypeScript build. |
| Developer Portal | `npm test`, `npm run build` in `developer-portal` | Portal component tests and production build. |
| Marketplace API | `npm test -- --run` in `marketplace-api` | TypeScript/CLI build and Vitest; DB integration cases are gated by `MARKETPLACE_INTEGRATION=1`. The suite resets database tables, so only run it against a disposable database. |
| Performance | `:benchmark:connectedBenchmarkAndroidTest` | Existing Macrobenchmark harness; previous report records selector/launch harness failures. No new comparable benchmark was measured in this run. |

Run device tests only on an explicitly connected target. Never clear application data to make a test pass. Do not enable the database integration flag unless the configured database is disposable and isolated.

## Device scenarios executed

Connected target: Nothing A001, reported as `A001 - 16`, API 36, build `B4.1-260928-1137`. The Android test suite covers account form behavior, top-level navigation and diagnostics, Settings → Account/back, Feedback Center local export/sign-in guidance, a temporary Room persistence database, and Battery Glow install/in-app preview/disable/uninstall with overlay access denied.

The suite does not establish real account authentication, remote feedback submission, charging-broadcast behavior, external overlay display, Shizuku authorization, marketplace download integrity or recovery after process/device restart.

## Safety and evidence rules

- Use generated fixture IDs and isolated databases.
- Preserve user data and installed packages.
- Treat skipped integration tests as unverified.
- Record device model/API/build and APK variant for hardware results.
- Do not present API-level support as device compatibility without execution evidence.
- Keep debug builds distinct from signed release artifacts.
