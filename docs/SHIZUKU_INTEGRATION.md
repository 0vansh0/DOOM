# Shizuku integration

DOOM includes the official Shizuku API and provider as an optional adapter. The app remains usable when Shizuku is absent, stopped, or unauthorized. DOOM does not start Developer Options, USB debugging, or wireless debugging for the user.

The app observes the Shizuku Binder and permission result callbacks while its process-scoped `DoomViewModel` exists. It probes Binder liveness and authorization again when refreshing or performing its diagnostic. Binder death invalidates the current adapter generation; DOOM does not retain an operation Binder. Permission is requested only from the Settings button.

Settings distinguishes not installed, installed/not running, authorization required, connected as ADB shell, connected as root, disconnected, and error. ADB-shell UID is 2000; root UID is 0. Shizuku authorization grants access to the Shizuku service, but does not convert an ADB-shell session into root.

The only operation exposed in this milestone is `shizuku.diagnostic.identity`: a user-clicked, read-only report of Shizuku API version and caller UID/privilege. It accepts no user parameters and executes no shell command. It is not a capability in the imported mod schema and cannot be invoked by a mod. No ADB/Shizuku capabilities are currently required by standard DOOM mods.

The screen-timeout recovery operation added in Milestone 10B is not a Shizuku operation. It uses Android's public `Settings.System` API and the user's separate Modify system settings grant. Shizuku authorization is not used as a substitute for Android authorization and does not imply that ADB shell can perform every operation. No settings write is exposed to third-party mods.

The operation coordinator can require an adapter and minimum privilege tier when a future reviewed operation declares one; it checks current registered adapter status and fails closed on missing authorization or insufficient identity. The current screen-timeout contracts require no Shizuku adapter and have `modAccessible: false`.

The Registry v2 adapter catalog records the supported ADB/ROOT identity levels and this diagnostic operation with `modAccessible=false`; this metadata does not create an authorization path for mod packages.

For non-root Shizuku, the user starts the service using Shizuku's documented wireless debugging or ADB setup flow. A normal process restart does not guarantee the service has restarted; revisit Settings and refresh. DOOM does not silently reconnect or bypass platform restrictions.

References: [Shizuku-API](https://github.com/RikkaApps/Shizuku-API), [Shizuku setup documentation](https://shizuku.rikka.app/).
