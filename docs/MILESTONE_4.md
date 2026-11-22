# Milestone 4 declarative runtime

## Try it

Import [Runtime Glow](../sample-mods/releases/Runtime-Glow.doommod), open it in **Installed**, approve its declared capabilities, grant DOOM Android overlay access, and enable the mod. **Run mod test** displays the floating-card preview inside its details screen. Start charging while DOOM is resumed and focused to see the battery ring. **Dismiss effect**, **Enable mod**, and **Safe mode** control the active effect or its eligibility.

The [Battery Glow release](../sample-mods/releases/Battery-Glow.doommod) still executes its original definition v1 charging rule. The older bundled development record has no published archive digest. It retains its static appearance preview. Uninstall that record before importing the public Battery Glow release with the same ID.

## Runtime scope

Room maintains desired enablement, readiness, grants, failures and logs. The foreground controller projects each ready row into a live session. It reopens the private archive, verifies SHA-256 on the bytes it parses, checks metadata and setting bounds, then subscribes to the declared events. **ACTIVE** means the current session has verified and registered the mod. The details screen reports its current effect separately. Disabling, grant changes, settings changes and safe mode refresh the session.

The activity stops the runtime when paused or unfocused. A protected battery-state receiver dispatches charging edges. Activating while already charging evaluates the start binding once. Charging-stop closes charging-bound effects before evaluating an explicit stop binding. Conditions are limited to always, charging, and not-charging. Manual dispatch targets one installed mod. The clock provider is available to authorized native code. Timer subscriptions remain deferred.

Native calls run serially on the main thread. The receiver delivers protected events without an application-level event queue. The controller caps sessions at 16, rate-limits each event binding to one invocation per 500 ms, and expires each presentation after 20 seconds. It allows one presentation per mod and two non-overlapping overlay slots globally. Overlay dimensions, positions, opacity bounds, shapes and text come from trusted code. Overlay windows pass through touches, contain no developer-controlled text, and are removable from DOOM. Log delivery uses a bounded queue. Android permission changes are checked at dispatch, on AppOps notifications, and by a 250 ms foreground watchdog. Package verification runs on the I/O dispatcher.

Android application overlays depend on special app access and system window policy. DOOM scopes the engine to a resumed, focused activity. There is no foreground service or background execution claim. [Android overlay permission](https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW) · [AppOps callbacks](https://developer.android.com/reference/android/app/AppOpsManager#startWatchingMode(java.lang.String,java.lang.String,android.app.AppOpsManager.OnOpChangedListener))

## Verification commands

```powershell
npm --prefix doom-cli test
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :core-data:assembleDebugAndroidTest :core-domain:test :mod-engine:test :app:lintDebug :core-data:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest :core-data:connectedDebugAndroidTest
```

## Automated results

- The debug APK and both test APKs built with JDK 17, Gradle 8.9 and Android SDK 35. Lint reported zero errors and nine dependency-version warnings.
- Five Node CLI tests, 23 JVM tests, and 14 Android instrumentation tests passed. Instrumentation ran on the API 31 emulator using both AndroidJUnitRunner packages. The runtime tests cover event dispatch, resource limits and expiration, grant and Android permission loss, filtering disabled mods, manual preview, failure isolation, and rejection of modified package bytes.

## Emulator runtime checks

On the API 31 emulator, both CLI-generated releases were selected and installed through Android's document picker. Runtime Glow rendered and dismissed its manual floating-card preview. A simulated charging edge created a native WindowManager overlay and stopping charging removed it. Enabling both imported mods produced two distinct overlay slots. Disabling Battery Glow removed only its window. Pressing Home cleared the remaining window, returning to DOOM restored eligibility, and revoking Android overlay access removed it again.

After force-stop, a new foreground session verified the enabled package before recreating its effect. Safe mode cleared the overlay and suppressed a subsequent charging edge. Turning safe mode off rechecked the saved desired state and restored eligibility.

Physical-device charging and overlay behavior remains unverified.

## Files changed

```text
app/src/main/kotlin/org/doom/app/DoomViewModel.kt
app/src/main/kotlin/org/doom/app/MainActivity.kt
core-data/src/androidTest/kotlin/org/doom/data/ModImportTest.kt
core-data/src/main/kotlin/org/doom/data/AndroidEffects.kt
core-data/src/main/kotlin/org/doom/data/ForegroundEffects.kt
core-data/src/main/kotlin/org/doom/data/Persistence.kt
core-model/src/main/kotlin/org/doom/model/InstalledMod.kt
core-model/src/main/kotlin/org/doom/model/ModRuntimeState.kt
docs/ARCHITECTURE.md
docs/MILESTONE_4.md
docs/MOD_DEVELOPER_GUIDE.md
docs/MOD_FORMAT.md
doom-cli/src/format.ts
doom-cli/test/cli.test.mjs
feature-installed/src/main/kotlin/org/doom/feature/installed/Screen.kt
feature-mod-details/src/main/kotlin/org/doom/feature/mod/details/Screen.kt
mod-engine/build.gradle.kts
mod-engine/src/main/kotlin/org/doom/engine/ArchiveReader.kt
mod-engine/src/main/kotlin/org/doom/engine/PackageFormat.kt
mod-engine/src/main/kotlin/org/doom/engine/runtime/DefaultModRuntime.kt
mod-engine/src/main/kotlin/org/doom/engine/runtime/Definition.kt
mod-engine/src/main/kotlin/org/doom/engine/runtime/ModRuntime.kt
mod-engine/src/test/kotlin/org/doom/engine/ArchiveReaderTest.kt
mod-engine/src/test/kotlin/org/doom/engine/runtime/DefaultModRuntimeTest.kt
packages/manifest-schema/fixtures/runtime-glow/doom.json
packages/manifest-schema/fixtures/runtime-glow/mod.json
packages/manifest-schema/v2/doom.schema.json
packages/manifest-schema/v2/mod-v2.schema.json
README.md
sample-mods/releases/Runtime-Glow.doommod
```
