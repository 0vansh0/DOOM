# External overlay lifecycle

The previous native renderer already used `TYPE_APPLICATION_OVERLAY` with `SYSTEM_ALERT_WINDOW` declared. It created a WindowManager window from an application window context. The static/live Compose preview is a separate surface. Activity `onPause` and focus loss called `ForegroundEffects.stop()`, which cancelled the runtime and closed the native window. Pressing Home therefore deliberately removed the overlay in the original foreground-only design.

## User controls

Enable a public-format mod, approve its declared capabilities, and grant Android overlay access. On its details page, **Test External Overlay** starts a 12-second test using the package's declared overlay action. **Start Persistent Floating Glow** starts a user-controlled battery indicator. These sessions live in a non-exported foreground service with an ongoing notification and a Stop action. Notification authorization is required so the Stop control remains available. A process restart requires the user to start floating mode again.

The service verifies the private package digest through the repository and selects an existing allowlisted `overlay.show` action. Its execution context is constructed by the same trusted runtime as foreground mods. The explicit external test replaces event timing with a trusted manual dispatch. It preserves the declared template, settings and capability checks. Persistent mode replaces the presentation deadline with an explicit stop lifetime.

There is one user-started service session at a time. The foreground controller excludes that mod while the service owns it. A native overlay pool limits the entire process to two windows. Windows remain small, touch-through and placed above the lower system controls. Disable, uninstall, capability revocation, Safe Mode and Android overlay permission loss remove the service window. In-app preview remains Activity-owned.

A charging-bound overlay triggered during the eligible foreground session can transfer to the service for its remaining configured duration when notification authorization is already available. Disconnecting its charger closes that temporary session. Automatic charging subscriptions retain the existing foreground eligibility. The external test and floating mode are started by visible user actions. The app does not start a foreground service from a background battery broadcast. Android may hide overlays on protected windows, terminate the process, or stop the service through its foreground service controls. Distribution through an app store may require review of the declared `specialUse` service subtype. [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) · [Android 15 overlay start restrictions](https://developer.android.com/about/versions/15/behavior-changes-15#fgs-system-alert-window)

## Hardware

The USB device reports manufacturer `Nothing`, model `A001`, Android `16`, API `36`, OS build `B4.1-260928-1137`. Existing app data is retained during installation. The debug APK upgraded the existing installation successfully with `adb install -r`.

## Verification evidence

The existing installation upgraded in place. The Nothing phone showed the external window over DOOM, Home and Calculator. The explicit test expired at 12 seconds and removed its foreground service. Persistent mode remained beyond the temporary deadline, and DOOM's Stop control removed it. Disable Mod, Safe Mode and Android permission revocation each left zero DOOM overlay windows. Three repeated start/stop cycles produced no duplicate or leaked windows. The notification contained a Stop action, but that action was not located reliably through this OEM's notification UI automation. The in-app Stop control was exercised.

The native overlay retained its package-defined ring size, position and opacity. The actual live battery reading was rendered in the ring. The process log showed overlay success and cleanup events with no AndroidRuntime fatal exception in these tests.

- [Structured hardware results](qa/external-overlay/nothing-results.json)
- [Device Logcat](qa/external-overlay/nothing-logcat.txt)
- [Persistent overlay above Calculator](qa/external-overlay/persistent-calculator.png)

## Changed source files

- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/org/doom/app/MainActivity.kt`
- `app/src/main/kotlin/org/doom/app/DoomViewModel.kt`
- `core-data/src/main/kotlin/org/doom/data/AndroidEffects.kt`
- `core-data/src/main/kotlin/org/doom/data/ForegroundEffects.kt`
- `core-data/src/main/kotlin/org/doom/data/ExternalOverlayController.kt`
- `core-data/src/main/kotlin/org/doom/data/ExternalOverlayService.kt`
- `core-model/src/main/kotlin/org/doom/model/ModRuntimeState.kt`
- `mod-engine/src/main/kotlin/org/doom/engine/runtime/DefaultModRuntime.kt`
- `mod-engine/src/test/kotlin/org/doom/engine/runtime/DefaultModRuntimeTest.kt`
- `feature-mod-details/src/main/kotlin/org/doom/feature/mod/details/Screen.kt`
- `README.md`
- This report and its QA evidence.

## Build and tests

```powershell
.\gradlew.bat :app:assembleDebug :mod-engine:test :core-domain:test :app:lintDebug :core-data:lintDebug
```

The build and validation tasks passed. The JVM reports contain 27 passing tests, including a new test for explicit external deadlines, persistent lifetime and cleanup. Lint reported zero errors and nine dependency-version warnings. No Android instrumentation suite was rerun for this fix; the external window and foreground service behavior were tested directly on the connected phone.

During investigation, overlapping Gradle jobs collided in generated Hilt output and two simultaneous UIAutomator dumps collided in Android's automation service. Sequential build and device checks resolved those test-tool failures.

The final APK also passed the handoff check using the phone's real USB charging state. The existing saved duration was 19 seconds, and that value was preserved. Its charging overlay remained after Home and expired without starting another background session. Final cleanup left no service and no native windows. The phone's automatic rotation and USB stay-awake setting were restored after testing.

The tested debug APK SHA-256 is `505dec682b5782fe302123f173ae6a179a4b8994150e4e3608dcedb455add4b8`.
