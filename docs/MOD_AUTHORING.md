# Author a DOOM mod

A version 1 `.doommod` is a ZIP with `doom.json` and `mod.json` at its root. Create a new source folder and copy these two files from [Battery Glow](../sample-mods/battery-glow). Replace the mod ID, title, description, version and appearance defaults with your own values. Keep the schema version, capability, trigger and effect identifiers from the example. See the [envelope schema](../manifest-schema/v1/doom.schema.json) and [mod schema](../manifest-schema/v1/mod.schema.json).

- Use a unique lowercase dotted ID such as `yourname.amber`. Each part starts with a letter. The full ID is limited to 80 characters.
- Use a numeric major.minor.patch version with up to six digits per component and no leading zeroes.
- Set `minAndroidApi` to at least 31. The device must meet this value to install the mod.
- Set `settings.color` to `#RRGGBB` and `settings.intensity` to an integer from 10 through 60. The host provides these two appearance controls.
- Version 1 supports `battery.charging` → `overlay.glow`, with `overlay` authorization.
- Optional PNG assets may be included as `assets/<name>.png`. Use lowercase letters, digits, hyphens and underscores, with a leading letter or digit and at most 40 characters before the extension. The host checks their headers and keeps them as inert package data. The current renderer uses the declared color and intensity.

The ZIP is limited to 1 MiB compressed, 256 KiB expanded and ten entries total. Each UTF-8 manifest is limited to 16 KiB and each asset to 64 KiB. Use unique JSON keys and ZIP names, plain integer literals and no ZIP directory entries. Duplicate IDs are rejected at install time. Uninstall a previous version before installing its replacement.

## Package and validate

Build the CLI once using `./gradlew :doom-cli:installDist` or `.\gradlew.bat :doom-cli:installDist`.

```powershell
$cli = ".\doom-cli\build\install\doom-cli\bin\doom-cli.bat"
& $cli pack C:\mods\amber C:\mods\amber.doommod
& $cli validate C:\mods\amber.doommod
```

The pack command takes a source directory and a new output filename. An installed Android host can import the finished archive from **Discover → Import .doommod**. Open the mod in **Installed** to preview it and change its settings. To activate the native effect, grant the mod's overlay capability, authorize DOOM in Android special app access, and enable the mod while the battery is charging and DOOM is resumed. Use **Settings → Disable all mods** to turn off all enabled mods.

## Runtime behavior

Only one native glow is shown at a time. The engine selects the first eligible mod in library order. The renderer uses a small non-interactive application overlay. Android can move, resize, or hide application overlays and may deny or revoke access. DOOM stops the effect when the activity pauses, the battery stops charging, or authorization becomes unavailable. The Compose preview uses the same configurable color and intensity and can run independently of charging and access. [Android overlay window documentation](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)

The app has no foreground service. Android constrains work from the background and the OS can terminate the process. DOOM saves desired enabled state and rebuilds its live state from the device and user grants when resumed. [Android background execution limits](https://developer.android.com/about/versions/oreo/background)
