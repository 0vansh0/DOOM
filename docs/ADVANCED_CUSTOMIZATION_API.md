# Advanced customization API

The current no-root customization API remains declarative. V3 and existing V4 actions continue to use the same capability IDs and checks. The registry catalog is versioned independently from SDK API 2.

New V4 action `device.display.read` takes no parameters or template. DOOM checks the mod's declared `device.display.read` requirement and per-mod approval, then reads the default display through public `DisplayManager` APIs and records a bounded diagnostic containing pixel dimensions, density and rotation. Unique display IDs are never collected.

New V4 action `overlay.preset.apply` accepts an existing allowlisted `template` (`battery-ring` or `floating-card`). It uses the same renderer, overlay slot limits, approval checks, Android `SYSTEM_ALERT_WINDOW` access and cleanup as `overlay.show`. The package must declare and the user must approve both `overlay.preset.apply` and `overlay.render`.

`app.launch.approved` is intentionally unavailable. A trusted target picker, exported-component validation and foreground-only launch confirmation have not been implemented. It has no executor; DOOM reports it unsupported and blocks activation. Mods cannot provide arbitrary package or component names.

No Shizuku capability is available to mod definitions. Shizuku authorization and ADB-shell/root identity remain adapter status, not blanket permission to execute privileged operations. The identity diagnostic is user initiated, read only and app-settings-only.
