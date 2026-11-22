# DOOM beta tester guide

**Not currently an invitation or download guide. Beta distribution is blocked.** Use only after the release owner approves a signed artifact and a private channel. Never install the unsigned verification APK/AAB.

## Before installation

- Confirm the invitation is from the project owner and compare the published SHA-256 with the artifact obtained through the approved channel.
- Check application ID `org.doom.app`, version and signer identity. Android requires an update to match the installed package signing identity; do not uninstall or clear data to work around a mismatch.
- Back up data using the device's supported mechanisms before an approved test. Do not run test tooling against a personal installation without an explicit test plan.
- Current declared minimum is Android 12/API 31. This does not establish compatibility for every manufacturer or OS version.

## Permissions and controls

Review each capability before enabling a mod. See [Permissions and capabilities](PERMISSIONS_AND_CAPABILITIES.md) for each declared access, its purpose, denial behavior and revocation path. Shizuku is optional; do not grant it for ordinary mods, and its authorization does not grant imported mods shell or privileged access.

If a permission is denied, dependent features should report unavailable; standard no-root mods should remain usable where their requirements are met. Revoke access in Android Settings. Use Safe Mode to suspend third-party mod execution. Check Recovery Center before restoring a recorded device change.

## Testing and reporting

Use a test mod only after inspecting its requested capabilities. Enable it explicitly and disable it after the scenario. Test charging, overlay, offline, marketplace, update, Safe Mode and recovery behavior only when included in the approved test plan. Never bypass Android protections or perform unapproved privileged writes.

For a report, include app version, Android version/API, device model only if comfortable, mod identifier if relevant, steps, expected result, actual result and severity. Review any screenshot or diagnostic export before sharing. The app does not need private files, installed-app inventories, tokens, or unique hardware identifiers for a normal report. Feedback upload requires sign-in and explicit consent; local export does not imply server submission.

To stop testing, disable test mods and overlays, sign out, and follow the owner-provided build removal/update instructions. Do not uninstall an existing app if doing so would risk user data.
