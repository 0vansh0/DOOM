# Build a community mod

From the repository root with Node 22.15 or later:

```powershell
npm.cmd --prefix doom-cli ci
npm.cmd --prefix doom-cli run build
node doom-cli/dist/cli.js create my-mod
node doom-cli/dist/cli.js validate my-mod
node doom-cli/dist/cli.js capabilities
node doom-cli/dist/cli.js test sample-mods/community/low-battery-alert sample-mods/community-tests/low-battery-alert.json
node doom-cli/dist/cli.js package my-mod
node doom-cli/dist/cli.js inspect my-mod.doommod
```

Adapt a scenario to your own triggers before packaging. For visual authoring use the [Visual Builder](../VISUAL_BUILDER.md). Test on-device through Discover → Import mod, review capabilities, activate, trigger and disable. A denied overlay or unavailable Shizuku service must produce a recoverable result. Standard packages cannot execute shell commands or directly invoke Shizuku.

Use the [event reference](../EVENT_ENGINE_V4.md), [workflow actions](../WORKFLOW_ENGINE.md), [settings controls](../DYNAMIC_MOD_SETTINGS.md), and [capability catalog](../CAPABILITY_REGISTRY_V2.md). Check schema, SDK and definition versions together. Preserve the existing V3 and V4 interpretations. An unknown operation needs a versioned trusted implementation. Publication requires a new immutable version, validation and moderation as described in the [publishing guide](../MOD_PUBLISHING_GUIDE.md).
