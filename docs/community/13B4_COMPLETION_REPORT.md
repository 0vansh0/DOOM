# Milestone 13B.4 evidence report

## Scope

This milestone adds CLI starter templates, documented public SDK boundaries and six locally packaged community examples. A manual-only V4 runtime defect found during device verification was fixed and covered by a regression test. It does not publish packages or claim full physical-device runtime verification.

## Executed evidence

- Final verification on 2026-11-21: `npm test` in `doom-cli` — PASS, 60/60. `.\gradlew.bat :mod-engine:test --rerun-tasks :app:testDebugUnitTest :app:assembleDebug` — PASS; mod-engine 72/72, app unit tests 4/4, debug APK exists at `app/build/outputs/apk/debug/app-debug.apk`.
- `doom-cli`: `npm test` — 60 tests passed, 0 failed; includes all eight expanded template create/validate/simulate/package checks. A separate final run validated and simulated each of the six community projects, validated and inspected each generated `.doommod`, and computed each archive's SHA-256; all six passed all four checks. The CLI requires both project and scenario paths for `test`; its quick-start and reference now show that syntax.
- Visual Builder: `npm test` — 11 passed.
- Developer Portal: `npm test` — 6 passed.
- Marketplace API: `npm test` — 11 passed, 11 skipped. Database-backed integration checks remain skipped; no public publishing occurred.
- Android `:mod-engine:test --rerun-tasks` — PASS, 72 tests, 0 failures, 0 skipped. The first run exposed a Windows case-insensitive classpath collision with the legacy Battery Glow fixture; community archive resources now retain their subdirectory. Device diagnostics then exposed a runtime bug where unrelated shared battery events faulted a V4 mod with only a manual trigger. The dispatcher now filters to declared matching triggers before capability checks, with a regression test; the full rerun passed.
- Android `:app:testDebugUnitTest :app:assembleDebug` — PASS; 4 app unit tests, 0 failures, 0 skipped; debug APK assembly succeeded.
- Nothing A001 / Android 16 (API 36) — PASS for debug install/update, app launch, importing the CLI-produced `community.capabilityshowcase` Definition V4 archive, reviewing and granting its declared `ui.preview` and `haptics.vibrate` capabilities, enabling it, manually triggering the in-app preview, disabling it (preview no longer shown), and uninstalling the test mod. The first attempt against the pre-fix APK failed and the mod was fault-suspended because a shared battery event was delivered to a manual-only mod; after the dispatcher fix and APK update, the repeated flow passed. The test package and its two temporary device copies were removed afterward.

Generated example archive SHA-256 values:

| Example | SHA-256 |
|---|---|
| Battery Glow | `3a046ef5a7235026ebbbdbccbcafd34aaeef02a697639d31a2710d25a77b987a` |
| Battery Guardian | `4bd65a8b75a921e4dd72ea307c8a4c7c50f7023e65073df90398891cea26017e` |
| Capability Showcase | `075ccfa78f4b7bd5283f50a7b187df8294225044f8c37d25136a9090daf7209e` |
| Dynamic Settings Showcase | `3770954b2080dc2852ce5d744b45aec1dbac7811460fd32485d28d19cef6978b` |
| Edge Aura | `83e728e3bf7e867ffab5af5e48182f6681190070d96243636da18940dc9b683a` |
| Workflow Showcase | `8e08d576bc37f522532caf3cfe50a2c980f123e6a013d7f7f20d060fd04fa844` |

## Not verified / still open

The pending Milestone 13B.3 checks remain pending: live feedback submission and admin authorization, database-backed marketplace integration, Shizuku lifecycle, physical Safe Mode/recovery behavior, performance measurements, and emulator coverage. Device verification covered one local capability example and a debug build, not every community example, external overlay behavior, Shizuku or marketplace install/update. No package was published publicly.

## Milestone 13B.5 follow-up (2026-11-22)

Four Visual Builder-generated projects/scenarios/packages were generated. Their scenarios passed CLI validation/simulation/package checks. A package from the live Vite SDK bridge was validated and inspected by CLI and passed Android `ArchiveReader` import; the expanded importer suite passes as part of mod-engine 74/74. Builder tests pass 11/11. The visual authoring/export GUI, physical install/execute of this exact package, and authenticated marketplace distribution remain unverified. Current marketplace backend integration tests pass 22/22 against isolated local services. This does not resolve emulator, performance, Shizuku or physical Safe Mode/recovery gaps. Overall status remains **BLOCKED**; see `../beta/13B5_INTEGRATION_REPORT.md`.
