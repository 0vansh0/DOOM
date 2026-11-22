# Milestone 3 verification

## Commands

```powershell
npm --prefix doom-cli ci
npm --prefix doom-cli test
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :core-data:assembleDebugAndroidTest :core-domain:test :mod-engine:test :app:lintDebug :core-data:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest :core-data:connectedDebugAndroidTest
```

The Node test suite packages the public fixture independently and compares the resulting bytes to the checked-in release. The release SHA-256 is `d7e75c24db18ad504f15a71cf4e6b951860d6e10b7ae52a29751cfb6d67591d6`.

## Results

- The debug APK and both test APKs built with JDK 17, Gradle 8.9 and Android SDK Platform 35. Android lint reported zero errors and nine dependency-version warnings.
- Four Node tests passed, including create, validate, package, inspect, reproducibility and malformed input rejection.
- Eighteen JVM tests passed, including eight public format and archive tests. The rest cover the retained prototype and lifecycle rules.
- Thirteen Android instrumentation tests passed on an API 31 emulator. Tests used real ContentResolver input, Android PNG bounds decoding, the exported Room migration chain, duplicate-ID handling, uninstall and journal recovery. Instrumentation ran with `adb shell am instrument -w -r` against the app and data AndroidJUnitRunner targets.

The API 31 emulator also completed an end-to-end file-picker check with a separate CLI-created package. The review showed compatibility and an unverified author. After confirmation, the package appeared as `INSTALLED_DISABLED`. After a force-stop and relaunch, Installed still showed the mod and the private archive SHA-256 matched the original artifact.

## Known limits and Milestone 4

The public registry describes a single charging-triggered battery ring. Icons are validated and retained in the archive. The metadata review is text-based. The older bundled development record and public Battery Glow share an ID, so the existing record must be uninstalled before importing the release. Crash tests reproduce the persisted states before and after the Room commit. The out-of-space test uses a synthetic failing output stream. Hardware and sudden power loss are outside this run.

Milestone 4 needs a trusted interpreter for the declared event, template and effect, runtime grant enforcement, verified package reads at activation, foreground lifecycle cleanup, and physical-device testing of battery and overlay behavior.

## Files changed

```text
.gitignore
app/src/main/kotlin/org/doom/app/DoomViewModel.kt
app/src/main/kotlin/org/doom/app/MainActivity.kt
app/src/main/kotlin/org/doom/app/ModImportDialog.kt
app/src/main/kotlin/org/doom/app/ModImportViewModel.kt
core-data/build.gradle.kts
core-data/src/androidTest/kotlin/org/doom/data/MigrationTest.kt
core-data/src/androidTest/kotlin/org/doom/data/ModImportTest.kt
core-data/src/main/kotlin/org/doom/data/ModImportCoordinator.kt
core-data/src/main/kotlin/org/doom/data/PackageStorage.kt
core-data/src/main/kotlin/org/doom/data/Persistence.kt
core-database/schemas/org.doom.database.DoomDatabase/3.json
core-database/src/main/kotlin/org/doom/database/DoomDatabase.kt
core-database/src/main/kotlin/org/doom/database/Migrations.kt
core-domain/src/main/kotlin/org/doom/domain/ModLifecycle.kt
core-domain/src/main/kotlin/org/doom/domain/Mods.kt
core-model/src/main/kotlin/org/doom/model/InstalledMod.kt
core-model/src/main/kotlin/org/doom/model/PackageReview.kt
docs/ARCHITECTURE.md
docs/MILESTONE_3.md
docs/MOD_DEVELOPER_GUIDE.md
docs/MOD_FORMAT.md
docs/PACKAGE_SECURITY.md
doom-cli/package-lock.json
doom-cli/package.json
doom-cli/scripts/copy-schema.mjs
doom-cli/src/cli.ts
doom-cli/src/format.ts
doom-cli/src/zip.ts
doom-cli/test/cli.test.mjs
doom-cli/tsconfig.json
feature-discover/src/main/kotlin/org/doom/feature/discover/Screen.kt
feature-mod-details/src/main/kotlin/org/doom/feature/mod/details/Screen.kt
mod-engine/build.gradle.kts
mod-engine/src/main/kotlin/org/doom/engine/ArchiveReader.kt
mod-engine/src/main/kotlin/org/doom/engine/PackageFormat.kt
mod-engine/src/main/kotlin/org/doom/engine/PngBounds.kt
mod-engine/src/test/kotlin/org/doom/engine/ArchiveReaderTest.kt
packages/manifest-schema/fixtures/battery-glow/assets/glow.png
packages/manifest-schema/fixtures/battery-glow/doom.json
packages/manifest-schema/fixtures/battery-glow/mod.json
packages/manifest-schema/v2/doom.schema.json
packages/manifest-schema/v2/mod.schema.json
README.md
sample-mods/releases/Battery-Glow.doommod
```
