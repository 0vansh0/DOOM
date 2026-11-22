# DOOM mod developer quick start

This guide uses the checked-in CLI, the V4 schema and the host's trusted registries. Node.js 22.15.0 or newer is required by `doom-cli`.

```powershell
npm --prefix .\doom-cli install
npm --prefix .\doom-cli run build
node .\doom-cli\dist\cli.js create --template battery-triggered --out .\my-mod
node .\doom-cli\dist\cli.js validate .\my-mod
node .\doom-cli\dist\cli.js test .\my-mod .\my-mod\test-scenario.json
node .\doom-cli\dist\cli.js package .\my-mod --out .\my-mod.doommod
node .\doom-cli\dist\cli.js inspect .\my-mod.doommod
```

The commands above run the local CLI implementation. The package includes only supported manifest, definition and declared assets; README and scenario files remain project-side. Import the archive in DOOM, review compatibility and requested capabilities, grant approvals explicitly, then enable it. Disable the mod before uninstalling to release runtime-owned effects.

See [CLI reference](CLI_REFERENCE.md), [testing guide](TESTING_GUIDE.md), and [publishing guide](PUBLISHING_GUIDE.md). A desktop simulation records allowlisted effects; it does not run Android overlays or verify device hardware.
