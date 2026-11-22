# Public developer workflow

Install Node.js, then from the repository:

```powershell
npm --prefix doom-cli ci
npm --prefix doom-cli run build
node doom-cli/dist/cli.js capabilities
node doom-cli/dist/cli.js create my-power-card --template power-card
node doom-cli/dist/cli.js validate my-power-card
node doom-cli/dist/cli.js package my-power-card
node doom-cli/dist/cli.js inspect my-power-card.doommod
```

Available templates are battery-glow (legacy public format), power-card, focus-pulse and low-battery-card (API 2). Change the generated unverified author, ID, description and appearance. The API 2 low-battery-card template demonstrates a developer-defined threshold setting. Creation never overwrites an existing directory. Packaging refuses an existing output file. Keep a previous package separately and remove only a deliberately selected output before repackaging.

Move the archive to the phone, open DOOM > Discover > Import Mod, use the Android document picker, review metadata and requested capabilities, then confirm Install. Approve each capability in Mod Details and enable the mod. Use the manual event action when the template declares it. Power Card responds to Battery Saver changes during an eligible foreground session. Charging effects still have Android foreground/background limits. Local authors have no verified publisher status.

The CLI performs schema, semantic binding, file and archive checks. inspect reports SHA-256 hashes. Packaging uses deterministic paths, timestamps, ordering and bytes. Validation is not an Android execution simulator. Test actual behavior in DOOM and export diagnostics to inspect runtime submissions and failures.

New packages using existing registry entries need no Android host rebuild. New native actions or privilege tiers require a host release and security review. There is no downloaded executable extension mechanism. Existing IDs cannot be upgraded in place yet. Explicitly uninstall before importing a replacement, which removes associated settings/grants.

Run SDK conformance with `npm --prefix doom-cli test` and `./gradlew :mod-engine:test`. Three API 2 source examples and reproducible CLI archives are under `sample-mods/sdk2`. `docs/DOOM_SDK_V2.md` is the authoritative version and limits reference.
