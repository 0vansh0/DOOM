# Create and import a mod

For SDK API 2 templates, developer-defined integer options and current capability metadata, see [Developer Workflow](DEVELOPER_WORKFLOW.md) and [SDK API 2](DOOM_SDK_V2.md). The default create command below preserves the legacy Battery Glow format.

Use Node.js 22.15 or later. From the repository root, run:

```text
npm --prefix doom-cli ci
npm --prefix doom-cli run build
node doom-cli/dist/cli.js create amber-charge
node doom-cli/dist/cli.js validate amber-charge
node doom-cli/dist/cli.js package amber-charge
node doom-cli/dist/cli.js inspect amber-charge.doommod
```

Set a unique dotted ID and enter your own author, license and description in `doom.json`. Edit the color and intensity defaults in `mod.json`. See the [format](MOD_FORMAT.md) and the [Battery Glow source](../packages/manifest-schema/fixtures/battery-glow). A linked local CLI exposes the same commands as `doom`.

Copy the finished archive to the Android device. Open **Discover â†’ Import mod**, select the archive, and review the compatibility result and requested capabilities. Confirm the install to add it to **Installed**. Installing starts the mod disabled. Use **Uninstall mod** on the details screen to remove the archive and Room records. For declarative execution, approve each declared capability, enable the mod, and keep DOOM resumed and focused. Overlay effects also require Android overlay access. Definition v2 can opt into **Run mod test**. See [runtime behavior](MILESTONE_4.md).
