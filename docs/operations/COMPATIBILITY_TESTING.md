# Android Compatibility Testing

For each test record device model, manufacturer, Android version/API, app version/code and variant, permission state, Shizuku state, marketplace connectivity, mod/package fixture, scenario, result, and evidence path. Label physical device, emulator, and desktop/JVM separately. Mark unrun cells NOT TESTED; API minSdk is not device certification.

Cover new Android releases, account/navigation, permission denial/revocation, overlay allowed/denied, Shizuku absent/disconnected/revoked, background limits/battery optimization, battery events, import/update/settings retention, Safe Mode, process restart, and recovery conflict handling. Use harmless actions and preserve app data.

The latest checked-in matrix records Nothing A001/API 36 instrumentation evidence for a limited scenario set; overlay-granted, Shizuku, marketplace live flow and physical recovery remain untested there. Android 12/API 31 through 15/API 35 emulator coverage and other OEM devices are NOT TESTED in the latest matrix. Update `docs/beta/DEVICE_COMPATIBILITY_MATRIX.md` from actual results.
