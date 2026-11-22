# Verify on Android hardware

Connect an Android 12 or later phone with USB debugging enabled. Run `adb devices -l` and approve the debugging prompt on the phone. Build and install the debug APK. Run the connected tests with overlay access initially disabled.

```powershell
.\gradlew.bat :app:installDebug
.\gradlew.bat :app:connectedDebugAndroidTest :core-data:connectedDebugAndroidTest
```

1. Open **Discover**, install **Battery Glow**, and open it from **Installed**. Check that the preview is visible and reacts to a new color and intensity. Save the appearance, then reopen the screen and check the values.
2. Grant the mod's overlay capability while Android overlay access remains denied. Check that DOOM prevents enabling it. Open Android overlay settings, allow access for DOOM, and return to the app.
3. Enable the mod and plug the phone into a charger when the battery is below full. Check for the small glow while DOOM is resumed. Verify that touches near the glow still reach the underlying interface.
4. Unplug the charger, switch away from DOOM, revoke the mod grant, and use **Disable all mods** in separate activation cycles. Verify the window disappears in each case. Repeat with Android overlay access revoked before returning to DOOM.
5. Save settings and force-stop DOOM. Reopen it with the charger disconnected and confirm the desired configuration persists while the runtime reports the effect inactive.
6. Follow the author guide to build a package with a different ID and color. Copy the archive to Downloads and import it in **Discover**. Check the details and preview, then uninstall it and confirm it is removed from **Installed**.

Record the device model, Android build, each observed result, and any vendor-specific behavior. An emulator run covers integration behavior and simulated charging. Physical testing also checks charging state, overlay positioning and system restrictions on the actual phone.
