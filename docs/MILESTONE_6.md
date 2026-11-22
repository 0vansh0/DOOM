# Phase 2 Milestone 6 verification

Date: 19 November 2026. Existing E:\DOOM project extended in place. Application ID org.doom.app. This is an unreleased SDK extension on the existing 0.1.0 debug host, rather than a newly signed alpha release.

## Delivered

- Repository audit and architecture decisions in MILESTONE_6_AUDIT.md.
- Exact SDK API 2 version negotiation (manifest schema 3, sdkApiVersion 2, definition 3), preserving manifest schema 2 / definition 1 and 2.
- Shipped capability metadata, new power-state event/conditions and bounded native haptic adapter, with explicit per-mod approval and actual device availability checks.
- Developer-named integer settings, bounded defaults/ranges, condition reference resolution, generated settings controls, runtime revalidation and Room persistence.
- Additive Room migration 4 to 5 with a nullable metadata column. Existing installations, settings and grants are preserved.
- CLI capability listing, templates, public schemas, shared conformance fixtures and three reproducibly packaged SDK2 examples.
- User-exported diagnostics containing actual capability probes. No model-based compatibility guesses.
- Design-only privilege broker boundaries for future ADB/Shizuku/root adapters. No privileged transport or commands implemented.

## Build and automated results

| Check actually executed | Result |
| --- | --- |
| npm --prefix doom-cli test | 27 tests passed |
| :mod-engine:test | 30 tests passed |
| :core-domain:test | 3 tests passed |
| :app:assembleDebug | Passed |
| :app:lintDebug and :core-data:lintDebug | Passed, dependency update warnings remain |
| :app:assembleDebugAndroidTest and :core-data:assembleDebugAndroidTest | Passed |
| API 31 data/import instrumentation | 15 tests passed |
| API 31 NavigationTest and PersistenceTest | 2 tests passed |
| API 31 BatteryGlowTest (legacy package and denied overlay preview regression) | 1 test passed |

The initial lint run failed because the data module needed its own VIBRATE declaration. That declaration was added and the full app/data lint run then passed. Kotlin kapt reports its existing fallback to language version 1.9. No warnings were suppressed. The latest direct migration test separately opens schema 4 and verifies settings/grants after migrating to 5. The existing schema 1 migration chain also passes.

The 15 Android data tests include all three actual CLI archives, ContentResolver import, no default grants, blocked activation without approval, setting validation/persistence after closing/reopening Room, package hash revalidation, cancellation/journal recovery and uninstall cleanup. Native haptic submission and cancellation were executed on the emulator. Physical sensation is not asserted.

Evidence: qa/milestone-6/build.txt, test-apk-build.txt, cli-tests.txt, jvm-tests.json, api31-instrumentation.txt, api31-app-tests.txt and api31-logcat.txt. These are actual results, rather than proposed acceptance targets.

## Nothing physical phone

Manufacturer Nothing, model A001, Android 16, API 36, OS build B4.1-260928-1137, USB serial 0019765C6000683. The recorded OS values came from ADB. Existing host installation/data were retained with adb install -r.

| Scenario | Observed result |
| --- | --- |
| Updated host installs and launches | Passed |
| New package authored after the host APK build | Passed, CLI template plus a changed default threshold |
| Import through Android document picker and metadata review | Passed |
| Package starts disabled and unapproved | Passed |
| Enable without all grants | PERMISSION_REQUIRED observed |
| Approve battery.read and ui.preview, then activate | Passed |
| Manual event produces a real runtime floating-card preview | Passed |
| Developer option threshold changed to 21 and saved | Passed |
| Force-stop/relaunch preserves threshold and approvals | Passed |
| Manual condition at current real battery level with threshold 21 | Preview blocked as expected |
| Reset Settings restores threshold 100 and manual preview | Passed |
| Disable cleans preview/session | Passed |
| Uninstall removes Room row, settings, grants and package file | Passed, queried the debug database and verified the private file is absent |
| Existing charging overlay on initial charging-state activation | SUCCESS and cleanup captured in current-process logs |
| Fresh physical connect/disconnect edge, background/locked/terminated charging | Not tested in this milestone |
| Battery Saver transition on physical phone | Not tested; API probe and unit dispatch tested |
| Physical haptic pulse | Hardware availability probed; physical execution/sensation not tested |

The newly authored local.m6community archive was created after the APK build and imported without another host rebuild. APK SHA-256 before and after package creation/import:

`a20145bb0bf2cba228e77ad542dacbd615b5bb65759cbd15f9065d2ffd80453c`

Package SHA-256: `3c90a1f837081a0b4bfd9c92f5bb7c2d469f6998f4564cada349420baa17b51d`.

See independent-build.json, nothing-community-preview.png, nothing-settings-restart.json, nothing-reset-preview.json, nothing-disable.json and nothing-uninstall.json under qa/milestone-6. The temporary test mod was removed, preserving the pre-existing Battery Glow installation. No DOOM fatal exception appeared in the captured current-process log. That capture does not certify every crash/ANR scenario.

Android rejected the separate physical-phone instrumentation APK with INSTALL_FAILED_VERIFICATION_FAILURE. The user reported no approval prompt. Instrumentation was therefore run on the installed API 31 emulator. Device security settings were left unchanged. The failed physical attempt is recorded in qa/milestone-6/instrumentation.txt and is not reported as a pass.

## Actual capability observations

The physical in-app diagnostic export reports battery.read, overlay.render, clock.read, ui.preview, device.power.read and haptics.vibrate AVAILABLE. Overlay authorization was already granted. Battery Saver was false. API 31 probes report the same service availability, with overlay.render DENIED for the test app. Availability does not imply grants, continuous background eligibility or a physically observed vibration. See CAPABILITY_MATRIX.md and the exported JSON.

The diagnostic export contains two manual-dispatch latency samples. This is not a repeatable startup/rendering/battery benchmark. No performance budget is certified by this milestone.

## Commands

```powershell
npm --prefix doom-cli test
.\gradlew.bat :app:assembleDebug :mod-engine:test :core-domain:test :app:lintDebug :core-data:lintDebug
.\gradlew.bat :app:assembleDebugAndroidTest :core-data:assembleDebugAndroidTest
adb -s emulator-5554 install -r core-data/build/outputs/apk/androidTest/debug/core-data-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w org.doom.core.data.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am instrument -w -e class org.doom.app.NavigationTest,org.doom.app.PersistenceTest org.doom.app.test/androidx.test.runner.AndroidJUnitRunner
```

This host uses the local DOOM-toolchain JDK 17 / Android SDK, Gradle 8.9, compile/target API 35 and minimum API 31. Builds used in-process Kotlin compilation and two workers. The APK is app/build/outputs/apk/debug/app-debug.apk.

## Limits and next work

Validate actual Battery Saver transitions and physical haptic submission on the phone, plus fresh charger events in foreground, background, locked and terminated states. Repeat tests across API 33+ and other OEMs. No always-on automatic background guarantee is introduced. The existing explicit overlay service lifecycle remains intact.

Integer options are the first developer-defined settings type. Free text/boolean options, general expressions, multi-action graphs, timer events and a live clock template remain deferred. clock.read has a trusted provider but no public clock-display effect. SDK additions use a closed native allowlist and require a host release when adding new APIs. Supported combinations/new settings IDs need no host rebuild.

Three ordinary SDK2 packages were tested, authored from this repository's public workflow. This demonstrates package independence, rather than a claim that three outside developers audited or authored them. Marketplace/publisher verification, visual builder and privileged bridges remain future work.

Exact source, schema, sample and evidence file inventory: qa/milestone-6/changed-files.txt. The workspace has no Git metadata, so this inventory records the inspected edited/generated files rather than claiming a Git diff.
