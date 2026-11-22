# DOOM 0.1.0 Alpha

## Packages

- [Battery Glow 1.0](../sample-mods/releases/Battery-Glow-1.0.doommod) uses the same public validator, private package store, capability approvals, and runtime as imported packages. Discover installs the bundled copy through the same journaled installer.
- [Amber Card](../sample-mods/releases/Amber-Card.doommod) uses a manual trigger, the floating-card template and only `ui.preview`. It was built from declarative package files through the public CLI.
- Floating Clock needs a clock-display parameter and a timer-trigger format. The current public format supports a clock capability without scheduling or clock text. The demo is deferred.

The ring settings are optional in definition v2. The `ring` object has `thicknessDp` (2–10), `diameterDp` (32–64), `durationSeconds` (1–20), and `position` (three lower anchors). The appearance editor shows a live reading after battery capability approval. Otherwise it uses the explicitly labeled illustrative percentage. The overlay stays conditional on Android special app access and foreground focus.

Existing installations with the same mod ID are preserved. Uninstall the earlier Battery Glow record before installing this package. Setting reset uses the validated install defaults. No prior package version is kept for rollback.

## Recovery and benchmarking

Per-mod runtime failures use a three-failures-in-five-minutes policy. Quarantine clears that mod's desired enablement and blocks a retry during the window. Host startup failures use a private startup marker and get counted when Android reports a matching crash, native crash or ANR, or when host initialization itself fails. Ordinary force-stop and low-memory exits are outside that count. Safe mode remains user accessible. Runtime activity is in memory, and the package cache rehashes the private file before using a cached definition.

**Settings → Export diagnostics** writes JSON to a user-selected location. It contains mod IDs, versions, lifecycle states, runtime events and in-process enable and manual-dispatch latency summaries. It excludes package bytes, paths and setting values. The ring shares one foreground battery event source and each presentation schedules one cancellable expiry. AppOps callbacks handle overlay-permission changes without a polling loop.

Use the `benchmark` build variant for Macrobenchmark. It is separately profileable and inherits release settings. Startup measures repeated cold launches. Installed scrolling requires a seeded library that spans several screens. The alpha APK has its own local test signing identity. A regular debug APK and this alpha may require separate installs because Android requires a matching signer for updates. [Macrobenchmark guide](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)

```powershell
npm --prefix doom-cli test
.\gradlew.bat :app:assembleRelease :app:assembleDebug :app:assembleDebugAndroidTest :core-data:assembleDebugAndroidTest :core-domain:test :mod-engine:test :benchmark:assembleBenchmark
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest
.\tools\sign-alpha.ps1 -KeyStore <PKCS12-keystore>
.\tools\measure-alpha.ps1 -Serial <adb-serial>
```

The signing script reads `DOOM_ALPHA_SIGNING_PASSWORD` from the process environment. Keep the signing key private and reuse it for subsequent builds distributed to these testers.

## Candidate and automated tests

- APK `app/build/outputs/apk/alpha/DOOM-0.1.0-alpha.apk`, version `0.1.0-alpha`, minimum API 31. Android build tools verified its APK Signature Scheme v3 signature.
- Debug, release, benchmark and instrumentation builds passed. Lint reported zero errors and nine dependency-version warnings.
- Six CLI tests, 26 JVM tests, and 15 Android instrumentation tests passed. Android instrumentation ran on the API 31 emulator.
- The APK's bundled Battery Glow archive is byte-identical to the standalone CLI-built release.

APK SHA-256 `f6b9190827772b2e611dc321715785a7c5ade5b04192fdb51dcea67beb246407`

Alpha signer certificate SHA-256 `d3bfc4188d732836ba6d449b401c83a24cd3b1069aa7e88f37fd9a060d8ce2d8`

## API 31 emulator results

The signed candidate displayed a live ring at the simulated 66% battery level with Android overlay access denied. When access was granted, a simulated charging edge created a WindowManager overlay and the configured three-second deadline removed it. Amber Card exercised the public file picker, install flow and manual preview with only `ui.preview` approved. The diagnostics document picker saved local JSON.

| Measurement | Observed |
| --- | --- |
| Signed APK cold launch via `am start -W`, 10 launches | p50 871 ms, p95 1,229 ms |
| Signed APK process PSS after launch | Peak sampled 43,102 KiB |
| Release-like Macrobenchmark cold startup, 7 launches | p50 752 ms, p95 1,032 ms |
| Signed APK animated preview, 4-second gfxinfo sample | 247 frames, p50 19 ms, p95 21 ms, 0 frames flagged janky by gfxinfo |
| Installed scroll Macrobenchmark | Skipped at the scrollable-library assumption. No scroll result. |
| Physical battery drain and memory across toggle stress | Open |

These timings describe this emulator and workload. The diagnostic timing sample is small and useful for instrumentation checks.

- [Signed startup and PSS samples](qa/alpha-api31-startup.json)
- [Cold-start Macrobenchmark data](qa/api31-macrobenchmark.json)
- [Runtime diagnostic timing aggregates](qa/api31-runtime-latency.json)
- [Battery Glow preview screenshot](qa/battery-glow-api31.png)

## Closed-alpha checklist

- [x] Release APK built, signed and signature-verified.
- [x] CLI compatibility, Room migration, repository, UI and runtime regression tests passed.
- [x] Signed Battery Glow and Amber Card smoke checks on API 31.
- [x] Startup and animated-preview measurements captured.
- [x] Changelog, format, recovery and device procedures written.
- [ ] Nothing hardware checkout. Windows initially exposed the USB media interface, but ADB never enumerated the phone.
- [ ] Real charging and battery drain, OEM overlay behavior, rotation, font scaling and memory stress.
- [ ] Newer Android emulator matrix and a completed seeded Installed-scroll benchmark.

## Files changed

```text
app/build.gradle.kts
app/src/androidTest/kotlin/org/doom/app/BatteryGlowTest.kt
app/src/benchmark/AndroidManifest.xml
app/src/main/kotlin/org/doom/app/DoomViewModel.kt
app/src/main/kotlin/org/doom/app/MainActivity.kt
benchmark/build.gradle.kts
benchmark/src/main/kotlin/org/doom/benchmark/DoomBenchmark.kt
CHANGELOG.md
core-data/src/androidTest/kotlin/org/doom/data/MigrationTest.kt
core-data/src/androidTest/kotlin/org/doom/data/ModRepositoryTest.kt
core-data/src/main/kotlin/org/doom/data/AndroidEffects.kt
core-data/src/main/kotlin/org/doom/data/DiagnosticExporter.kt
core-data/src/main/kotlin/org/doom/data/ForegroundEffects.kt
core-data/src/main/kotlin/org/doom/data/PackageStorage.kt
core-data/src/main/kotlin/org/doom/data/Persistence.kt
core-data/src/main/kotlin/org/doom/data/RuntimeMetrics.kt
core-data/src/main/kotlin/org/doom/data/RuntimeRecovery.kt
core-database/schemas/org.doom.database.DoomDatabase/4.json
core-database/src/main/kotlin/org/doom/database/DoomDatabase.kt
core-database/src/main/kotlin/org/doom/database/Migrations.kt
core-domain/src/main/kotlin/org/doom/domain/ModLifecycle.kt
core-domain/src/main/kotlin/org/doom/domain/Mods.kt
core-domain/src/main/kotlin/org/doom/domain/RuntimeRecoveryPolicy.kt
core-domain/src/test/kotlin/org/doom/domain/RuntimeRecoveryPolicyTest.kt
core-model/src/main/kotlin/org/doom/model/ModManifest.kt
core-model/src/main/kotlin/org/doom/model/ModRuntimeState.kt
core-model/src/main/kotlin/org/doom/model/RingStyle.kt
docs/ALPHA_DEVICE_MATRIX.md
docs/ARCHITECTURE.md
docs/MILESTONE_5.md
docs/MOD_FORMAT.md
docs/qa/alpha-api31-startup.json
docs/qa/api31-macrobenchmark.json
docs/qa/api31-runtime-latency.json
docs/qa/battery-glow-api31.png
doom-cli/src/format.ts
doom-cli/test/cli.test.mjs
feature-mod-details/src/main/kotlin/org/doom/feature/mod/details/RingPreview.kt
feature-mod-details/src/main/kotlin/org/doom/feature/mod/details/Screen.kt
feature-settings/src/main/kotlin/org/doom/feature/settings/Screen.kt
mod-engine/src/main/kotlin/org/doom/engine/ArchiveReader.kt
mod-engine/src/main/kotlin/org/doom/engine/PackageFormat.kt
mod-engine/src/main/kotlin/org/doom/engine/runtime/DefaultModRuntime.kt
mod-engine/src/main/kotlin/org/doom/engine/runtime/ModRuntime.kt
mod-engine/src/test/kotlin/org/doom/engine/runtime/DefaultModRuntimeTest.kt
packages/manifest-schema/fixtures/battery-glow-alpha/doom.json
packages/manifest-schema/fixtures/battery-glow-alpha/mod.json
packages/manifest-schema/fixtures/community-demo/doom.json
packages/manifest-schema/fixtures/community-demo/mod.json
packages/manifest-schema/v2/mod-v2.schema.json
README.md
sample-mods/build.gradle.kts
sample-mods/releases/Amber-Card.doommod
sample-mods/releases/Battery-Glow-1.0.doommod
settings.gradle.kts
tools/measure-alpha.ps1
tools/sign-alpha.ps1
```
