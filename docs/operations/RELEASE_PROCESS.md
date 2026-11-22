# Release and Update Process

## Android application

1. Review the release/security gates and open issue register.
2. Bump `versionCode` monotonically and document `versionName`.
3. Run Android, CLI, Visual Builder, API, portal, security, and documentation checks; capture skips separately.
4. Build APK/AAB only from the approved source revision. Release signing requires the approved external keystore and signer fingerprint; unsigned verification is not distributable.
5. Verify signatures with Android tooling and record APK/AAB SHA-256, package ID, version, signer fingerprint, and source revision.
6. Test a same-signer upgrade in an isolated device/profile; preserve app data and verify Room, mods, settings, and recovery state.
7. Require explicit human approval before any private/public distribution, store upload, or deployment.

Patch releases should contain focused fixes; minor releases may add compatible features; security hotfixes follow `SECURITY_RESPONSE.md`. Every channel remains subject to its signing identity. Android application installation/update is user mediated; DOOM must not silently install APK updates.

## Community mod releases

Build with the current CLI, validate schema/capabilities and archive paths, upload to quarantine, review validation output, obtain an authorized human decision, publish an immutable version, and verify downloaded bytes against SHA-256. Updates require a new submission/review, explicit user installation, capability review, and settings migration validation. Do not automatically enable new capabilities.

## Failure and rollback

Stop distribution when a gate fails; quarantine affected marketplace versions where authorized. APK rollback is constrained by Android signing and versionCode/update rules; do not promise an atomic rollback or tell testers to erase app data. Mod rollback is supported only through the actual recovery path and must retain user settings and recovery journals. See `docs/release/UPDATE_COMPATIBILITY.md` and `docs/release/ROLLBACK_AND_RECOVERY.md`.
