# Phase 1 Milestone 2

## Local lifecycle

Room v2 is the source of truth for the installed library, per-mod configuration, grants, desired enable state, readiness, errors and diagnostic records. A single repository mutex serializes lifecycle writes, including global safe mode, within the app process. Room transactions commit each state change with its log and commit grant changes with their readiness updates. Enable requests go through `ENABLE_REQUESTED` and `CHECKING_REQUIREMENTS` before reaching a result. `READY_TO_ACTIVATE` means the requirements passed. This milestone stops at readiness.

Before exposing the library, the repository rechecks interrupted enable requests and saved readiness against current authorization and compatibility. It completes interrupted uninstalls and preserves explicitly recorded failures for user action. Resuming the activity rechecks system requirements after a visit to Android settings. Failed package removal leaves an actionable error and can be retried. Uninstalling removes settings, grants and logs through foreign-key cascades.

DataStore persists safe mode, theme, diagnostic logging and the existing preview preference. Safe mode suppresses readiness while retaining desired choices. Turning it off rechecks those choices. **Disable all mods** clears them. Failure records are stored even when optional transition logging is off.

## Upgrading

`MIGRATION_1_2` preserves installed records, the old enabled bit as desired state, appearance overrides, capability grants and logs. The old relative package locations are reconstructed from the IDs. Legacy timestamps that were never recorded use zero as the unknown value. Room validates the checked-in exported v2 schema when opening a migrated database. The migration test creates a real v1 database from the checked-in v1 schema.

Register Battery Glow from **Discover**. Repeated registration is idempotent. It is a bundled development sample with a Compose appearance preview. The existing package validator, CLI and renderer remain in source from Milestone 1. The current app exposes the bundled sample and lifecycle controls. Third-party import and native execution are outside this milestone.

## Verification commands

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :core-data:assembleDebugAndroidTest :core-domain:test :mod-engine:test
.\gradlew.bat :app:connectedDebugAndroidTest :core-data:connectedDebugAndroidTest
```

## Results

- The app APK and both instrumentation APKs built successfully with JDK 17, Gradle 8.9, and Android SDK Platform 35.
- The two lifecycle JVM tests and eight retained engine and validator JVM tests passed.
- All ten Android instrumentation tests passed on an API 31 emulator. The seven data tests covered the v1 migration, reactive CRUD, settings durability, grants, denial, compatibility, safe mode, restart reconciliation, concurrent requests, and uninstall cleanup. The three app tests covered navigation, Room reopening, and the Battery Glow permission-required and preview flow.
- Instrumentation ran through `adb shell am instrument -w -r` using `org.doom.core.data.test/androidx.test.runner.AndroidJUnitRunner` and `org.doom.app.test/androidx.test.runner.AndroidJUnitRunner`. This milestone was verified on the emulator.

## Files changed

```text
README.md
docs/ARCHITECTURE.md
docs/MILESTONE_2.md
app/src/main/kotlin/org/doom/app/DoomViewModel.kt
app/src/main/kotlin/org/doom/app/MainActivity.kt
app/src/androidTest/kotlin/org/doom/app/BatteryGlowTest.kt
app/src/androidTest/kotlin/org/doom/app/NavigationTest.kt
app/src/androidTest/kotlin/org/doom/app/PersistenceTest.kt
core-data/build.gradle.kts
core-data/src/main/kotlin/org/doom/data/ForegroundEffects.kt
core-data/src/main/kotlin/org/doom/data/Persistence.kt
core-data/src/androidTest/kotlin/org/doom/data/MigrationTest.kt
core-data/src/androidTest/kotlin/org/doom/data/ModRepositoryTest.kt
core-database/build.gradle.kts
core-database/schemas/org.doom.database.DoomDatabase/2.json
core-database/src/main/kotlin/org/doom/database/DoomDatabase.kt
core-database/src/main/kotlin/org/doom/database/Migrations.kt
core-designsystem/src/main/kotlin/org/doom/core/designsystem/Theme.kt
core-domain/build.gradle.kts
core-domain/src/main/kotlin/org/doom/domain/Mods.kt
core-domain/src/main/kotlin/org/doom/domain/ModLifecycle.kt
core-domain/src/test/kotlin/org/doom/domain/ModLifecycleTest.kt
core-model/src/main/kotlin/org/doom/model/InstalledMod.kt
feature-discover/src/main/kotlin/org/doom/feature/discover/Screen.kt
feature-installed/src/main/kotlin/org/doom/feature/installed/Screen.kt
feature-mod-details/src/main/kotlin/org/doom/feature/mod/details/Screen.kt
feature-settings/src/main/kotlin/org/doom/feature/settings/Screen.kt
```
