# Phase 1 build report

The workspace started empty. The Windows build used JDK 17, Gradle 8.9, Android SDK Platform 35 and Build Tools 34.0.0. The APK declares a minimum API of 31 and a target API of 35.

## Commands and results

| Check | Command | Result |
| --- | --- | --- |
| Foundation | `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest` | Passed. The app unit-test task had no sources. |
| Engine and CLI | `gradlew.bat :mod-engine:test :doom-cli:installDist` | Passed. Eight JVM tests covered independent parsing, invalid archives and manifests, charging, authorization, and cleanup. |
| Integrated build | `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :core-data:assembleDebugAndroidTest :app:lintDebug :core-data:lintDebug :mod-engine:test :doom-cli:installDist` | Passed. Lint reported zero errors and nine dependency-version warnings across the two modules. |
| Bundled example | `doom-cli validate community.batteryglow.doommod` | Passed. The archive is present inside the debug APK. |
| Independent authoring | `doom-cli pack <source-dir> amber.doommod` followed by `doom-cli validate amber.doommod` | Both passed for an independently authored `independent.amber 2.1.0` package. The CLI was already built and the Android host was untouched. |

Gradle emitted kapt fallback warnings for Kotlin 2.0. The build completed using kapt's Kotlin 1.9 stub generation.

## Outputs

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- `core-data/build/outputs/apk/androidTest/debug/core-data-debug-androidTest.apk`
- `sample-mods/build/bundled/examples/community.batteryglow.doommod`
- `doom-cli/build/install/doom-cli/`

## Device results

Five Android instrumentation tests passed on the API 31 emulator. The app suite covered navigation, database reopening, bundled installation, preview, permission-denied UI and uninstall. The data suite covered independent content-URI import, configuration, grant revocation, uninstall and system overlay denial.

The test APKs were installed with `adb install -r -t <apk>` and run with:

```powershell
adb shell am instrument -w -r org.doom.app.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r org.doom.core.data.test/androidx.test.runner.AndroidJUnitRunner
```

On an Android 12 (API 31) x86_64 emulator, an independently authored archive entered DOOM through the Android document picker. Installed details showed its declared color and intensity. With the mod grant and Android overlay access enabled, WindowManager reported a native application overlay with alpha 0.32. The window disappeared when charging stopped, DOOM paused, or Android overlay access was revoked. It returned after charging resumed and after DOOM came back to the foreground.

Physical-device verification is pending. See the [device procedure](DEVICE_TEST.md).

## Files added

```text
.gitignore
app/build.gradle.kts
app/src/androidTest/kotlin/org/doom/app/BatteryGlowTest.kt
app/src/androidTest/kotlin/org/doom/app/NavigationTest.kt
app/src/androidTest/kotlin/org/doom/app/PersistenceTest.kt
app/src/main/AndroidManifest.xml
app/src/main/kotlin/org/doom/app/DoomApplication.kt
app/src/main/kotlin/org/doom/app/DoomViewModel.kt
app/src/main/kotlin/org/doom/app/MainActivity.kt
app/src/main/res/drawable/ic_doom.xml
app/src/main/res/xml/data_extraction_rules.xml
build.gradle.kts
core-data/build.gradle.kts
core-data/src/androidTest/kotlin/org/doom/data/ModRepositoryTest.kt
core-data/src/main/kotlin/org/doom/data/ForegroundEffects.kt
core-data/src/main/kotlin/org/doom/data/Persistence.kt
core-database/build.gradle.kts
core-database/schemas/org.doom.database.DoomDatabase/1.json
core-database/src/main/kotlin/org/doom/database/DoomDatabase.kt
core-designsystem/build.gradle.kts
core-designsystem/src/main/kotlin/org/doom/core/designsystem/Theme.kt
core-domain/build.gradle.kts
core-domain/src/main/kotlin/org/doom/domain/Mods.kt
core-model/build.gradle.kts
core-model/src/main/kotlin/org/doom/model/InstalledMod.kt
core-model/src/main/kotlin/org/doom/model/ModManifest.kt
docs/ARCHITECTURE.md
docs/BUILD_REPORT.md
docs/DEVICE_TEST.md
docs/MOD_AUTHORING.md
doom-cli/build.gradle.kts
doom-cli/src/main/kotlin/org/doom/cli/Main.kt
feature-discover/build.gradle.kts
feature-discover/src/main/kotlin/org/doom/feature/discover/Screen.kt
feature-home/build.gradle.kts
feature-home/src/main/kotlin/org/doom/feature/home/Screen.kt
feature-installed/build.gradle.kts
feature-installed/src/main/kotlin/org/doom/feature/installed/Screen.kt
feature-mod-details/build.gradle.kts
feature-mod-details/src/main/kotlin/org/doom/feature/mod/details/Screen.kt
feature-settings/build.gradle.kts
feature-settings/src/main/kotlin/org/doom/feature/settings/Screen.kt
gradle.properties
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
gradlew
gradlew.bat
LICENSE
manifest-schema/build.gradle.kts
manifest-schema/v1/doom.schema.json
manifest-schema/v1/mod.schema.json
mod-engine/build.gradle.kts
mod-engine/src/main/kotlin/org/doom/engine/GlowRuntime.kt
mod-engine/src/main/kotlin/org/doom/engine/ModPackage.kt
mod-engine/src/test/kotlin/org/doom/engine/GlowRuntimeTest.kt
mod-engine/src/test/kotlin/org/doom/engine/ModPackageTest.kt
README.md
sample-mods/battery-glow/doom.json
sample-mods/battery-glow/mod.json
sample-mods/build.gradle.kts
settings.gradle.kts
```
