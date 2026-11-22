# Distribution and recovery rollback plan

Application distribution rollback depends on the channel. Play Console can halt/stop rollout where available, but devices may retain installed versions and app data; a corrected update generally needs a higher versionCode and the same app-signing identity. Private APK distribution cannot remotely revoke an installed artifact. Do not promise automatic downgrade or data restoration.

Before any rollout, keep the exact signed artifact, certificate fingerprint, checksum manifest, source revision, migration notes and tested previous version. Test forward recovery with a higher code and preserve app data. Never uninstall/clear data as a rollback shortcut.

Marketplace mods are separate from APK distribution. Authorized moderators can quarantine/withdraw a release to block new catalog downloads. That does not guarantee removal from devices or caches. Prepare a reviewed compatible replacement and explicit user notice; preserve mod settings and recovery journals. Failed updates must leave a visible recoverable state rather than silently claiming rollback.

For privileged settings, use the existing recorded original/applied state and compare-and-restore behavior. If current state differs from DOOM's applied value, surface a conflict and do not overwrite the external change. Recovery depends on Android authorization and service availability.

No release is currently deployed, so this is a procedure only. Do not execute a rollback against production without incident-owner authorization.
