# Community Developer Guide

## Quick start

```powershell
node doom-cli/dist/cli.js create my-glow --template battery-glow
# edit my-glow/doom.json and my-glow/mod.json
node doom-cli/dist/cli.js validate my-glow
node doom-cli/dist/cli.js test my-glow path\to\scenarios.json
node doom-cli/dist/cli.js package my-glow
node doom-cli/dist/cli.js inspect my-glow.doommod
```

Read [CLI reference](CLI_REFERENCE.md), [testing](MOD_TESTING.md), the [mod format](MOD_FORMAT.md), and [SDK API 2](DOOM_SDK_V2.md). Authors and package contents are unverified local input; users review requested capabilities and approve them individually. `battery.read` is needed for battery events and `overlay.render` requires both DOOM approval and Android's overlay authorization. `ui.preview` is rendered only while DOOM UI is available.

## Community examples

Sources and CLI-generated packages are in `sample-mods/community/`:

- Low Battery Alert: battery level at or below a documented fixed 20% threshold while unplugged, displaying an in-app floating card.
- Charging Companion: sequential ring and card actions on charging start, plus a dismiss action on disconnect. Overlay authorization is required at runtime.
- Custom Glow Theme: a manual in-app ring preview with color, thickness, size, and duration settings.

Desktop scenarios are in `sample-mods/community-tests/`. The current V4 condition contract supports a setting as a comparison source but not a setting reference as the comparison value. Therefore Low Battery Alert's threshold is fixed in its condition; making it user-configurable requires a future versioned schema/runtime extension. Packages are public declarative examples, not proof of device execution.
