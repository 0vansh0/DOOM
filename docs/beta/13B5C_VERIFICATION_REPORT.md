# Milestone 13B.5C Verification Report

**Date:** 2026-11-22 (Asia/Calcutta)  
**Repository:** `E:\DOOM`  
**Application:** `org.doom.app`  
**Device:** Nothing A001 / API 36  
**Status:** **BLOCKED** — exported package/CLI/importer/runtime checks passed on desktop/JVM; emulator performance and physical package-import/effect tests were not completed.

## Acceptance checklist

| Item | Result | Evidence / limitation |
|---|---|---|
| Visual Builder schema inspected | PASS | Builder projects compile Definition V4 manifests/definitions using the copied public schemas and registry. Charging Companion declares `battery.read`, `overlay.render`, a sequential `stopOnFailure` workflow, two ordered actions, and a `color` setting reference. |
| Builder export creates valid `.doommod` | PASS (existing exports) | Four archives exist under `visual-builder/exports/`; all were validated by the real CLI in this run. The GUI was not manually operated to create a new archive in this run. |
| CLI validates exported package | PASS | `doom validate` passed for Battery Glow, Charging Companion, Custom Floating Card, and Low Battery Alert archives. CLI test suite also passed 60/60. |
| Android importer accepts exported package | PASS (JVM importer) | `VisualBuilderExportTest` runs `ArchiveReader` on the four builder-generated archive fixtures and now checks manifest capabilities and preserved setting IDs. |
| Exported mod executes safely | PASS (simulated runtime) | New `DefaultModRuntimeTest.visualBuilderExportExecutesDeclaredWorkflowAndCleansUpOnDisable` imports the builder Charging Companion archive, authorizes only its declared capabilities in the fake runtime, observes its two actions execute, and confirms cleanup at disable. No Android overlay execution is claimed. |
| Dynamic settings preserved | PASS (schema/runtime fixture) | Imported exports retain declared settings; the new runtime test resolves the configured `color` setting reference (`#33CCAA`) in the generated workflow. Existing runtime settings snapshot tests remain in the suite. |
| Capability declarations verified | PASS | New importer assertions compare capabilities against each fixture's manifest; runtime execution uses those package declarations and fake per-mod approval. |
| Performance baseline measured | PASS (historical baseline) | Existing `PERFORMANCE_BASELINE.md` contains seven process-cold A001 launches: p50 620 ms, p95 835 ms, range 561–835 ms; one idle PSS snapshot 114,143 KB. These are historical snapshots, not new 13B.5C measurements. |
| Startup and memory assessed | PASS (historical, limited) | Startup samples and one idle memory snapshot are documented. Active-mod memory and time to first frame remain unmeasured. |
| Overlay and workflow performance tested | BLOCKED | No current-device timing/allocation test was run. Historical Macrobenchmark XML has 2 tests and 2 failures (selector failure and activity-launch-completion failure); it is not usable performance evidence. JVM tests verify behavior/cleanup, not performance. |
| Confirmed regressions fixed | N/A / none found | No production defect was confirmed by this workstream. An initial new test expectation incorrectly assumed a charging-start trigger would fire twice without a matching false event; the test was corrected to assert the actual edge-trigger contract. |
| Emulator installation checked | PASS (availability inspected) | `emulator.exe` exists and an Android 35 x86_64 Google APIs system-image directory exists, but it contains only `.installer`; no complete image or AVD is available. |
| Available emulator tests executed | BLOCKED | No AVD/system-image payload is available. `emulator -list-avds` returned no entries. |
| Nothing A001 results recorded | PARTIAL | ADB confirmed A001/API 36, existing DOOM package version `0.1.0-beta.1` code 2, and a successful app launch (process observed). No builder package was imported and no overlay effect was exercised on device. |
| Final verification report completed | PASS | This report records outcomes and limits. |

## Commands and results

- `npm test -- --run` in `visual-builder`: **11 passed**.
- `npm test` in `doom-cli`: **60 passed**, 0 failed, 0 skipped.
- CLI validation: all four existing `visual-builder/exports/*.doommod` packages **PASS**.
- CLI scenario simulator: four matching scenarios **PASS** (one each for Battery Glow, Charging Companion, Custom Floating Card, and Low Battery Alert). Simulator output explicitly identifies Android effects/hardware as simulated.
- `.\gradlew.bat :mod-engine:test --rerun-tasks :app:testDebugUnitTest :app:assembleDebug`: **BUILD SUCCESSFUL** (1m 33s); mod-engine **76 passed**, app unit **4 passed**, zero failures/errors/skips; debug APK built.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 28,256,997 bytes (2026-11-22 19:50 local time).

## Device and emulator boundary

The A001 was attached during read-only checks. `monkey -p org.doom.app 1` launched the already installed app; no install, uninstall, permission grant, app-data clear, Shizuku operation, or settings write was performed. ADB showed no active network connection at the time of launch. Builder package import, settings persistence across app restart, and external overlay execution remain unverified on the phone.

One exploratory version of the new runtime test expected the charging-start workflow to fire a second time without a matching trigger event to rearm its edge. That assertion failed (2 actions observed, 4 expected); the test was corrected to assert the declared one-shot edge behavior, imported setting resolution, and cleanup. The final test passed. No product defect was inferred from that test-design error.

The emulator executable is installed, but an emulator test environment is not provisioned. The Android 35 system-image directory is incomplete and `-list-avds` is empty. Emulator testing is blocked pending a complete system image and AVD.

## Performance limits and next steps

Use the existing baseline carefully: Macrobenchmark had 2/2 failures, the installed-screen selector returned null, and the cold-start measurement failed its completion criterion. Repair those selectors and benchmark install policy, provision a disposable emulator/device dataset, then record repeated startup, active-mod memory, overlay lifecycle and workflow latency measurements. Do not interpret JVM correctness tests as performance measurements.
