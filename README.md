# DOOM

DOOM is an offline Android mod platform for Android 12 and later. Phase 1 uses local, declarative packages and a host-owned renderer.

## Build

Install JDK 17, Android SDK Platform 35, Build Tools 34.0.0 and Platform Tools. Set `JAVA_HOME` and `ANDROID_HOME`, or record the SDK location in an untracked `local.properties` file. Use `gradlew.bat` on Windows and `./gradlew` on macOS or Linux.

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :core-data:assembleDebugAndroidTest :mod-engine:test :doom-cli:installDist
adb devices -l
.\gradlew.bat :app:connectedDebugAndroidTest :core-data:connectedDebugAndroidTest
```

The connected test task requires a running API 31+ emulator or Android device with USB debugging. Overlay behavior also needs verification on a physical device.

See [architecture and decisions](docs/ARCHITECTURE.md).

## Use DOOM

Install the debug APK from `app/build/outputs/apk/debug/` on Android 12 or later. Open **Discover** to install the bundled Battery Glow package or import a local `.doommod`. Review the author's unverified identity, Android compatibility and requested capabilities before confirming. Imported mods start disabled. Use **Installed** to manage settings, approvals, execution and uninstall. The declarative session starts while DOOM is resumed and focused. Explicit external controls and temporary charging handoff can use a foreground service.

- [Create and import a package](docs/MOD_DEVELOPER_GUIDE.md)
- [Package format](docs/MOD_FORMAT.md) and [security and recovery](docs/PACKAGE_SECURITY.md)
- [Battery Glow 1.0](sample-mods/releases/Battery-Glow-1.0.doommod) and [Amber Card](sample-mods/releases/Amber-Card.doommod)
- [0.1.0 Alpha release report](docs/MILESTONE_5.md) and [device test matrix](docs/ALPHA_DEVICE_MATRIX.md)
- [Runtime Glow example](sample-mods/releases/Runtime-Glow.doommod) and [Milestone 4 runtime](docs/MILESTONE_4.md)
- [Milestone 3 verification](docs/MILESTONE_3.md)
- [Milestone 2](docs/MILESTONE_2.md) and [Milestone 1](docs/BUILD_REPORT.md) verification reports

The bundled and standalone Battery Glow 1.0 archives are the same package. An earlier Battery Glow installation with the same ID must be removed before installing 1.0.

External overlays have explicit temporary and floating controls. See the [external overlay lifecycle and Nothing device test report](docs/EXTERNAL_OVERLAY_FIX.md).

## Phase 2 SDK API 2

The versioned declarative SDK adds Battery Saver events/conditions, configurable battery-threshold conditions, developer-defined integer options and bounded haptic submissions. Existing Phase 1 packages remain accepted. All six standard capabilities require explicit per-mod approval.

- [SDK API 2 contract](docs/DOOM_SDK_V2.md) and [developer workflow](docs/DEVELOPER_WORKFLOW.md)
- [Architecture audit](docs/MILESTONE_6_AUDIT.md), [observed capability probes](docs/CAPABILITY_MATRIX.md) and [verification](docs/MILESTONE_6.md)
- [Security boundaries](docs/SECURITY_BOUNDARIES.md) and [future privilege design](docs/PRIVILEGE_ARCHITECTURE.md)

The Shizuku host adapter is optional, but its diagnostic operation is not exposed to imported mods; system-setting operations are also app-only. Root hooks and marketplace deployment are outside this SDK. The [13B.4 report](docs/community/13B4_COMPLETION_REPORT.md) distinguishes desktop validation from device verification.

## Marketplace backend (Milestone 11A)

`marketplace-api/` contains a local-first Fastify/PostgreSQL service for developer accounts, private `.doommod` submissions, shared CLI validation, reviewer decisions, immutable releases and a public catalog. It does not add Android marketplace UI or deploy any service publicly. Start with [local marketplace setup](docs/MARKETPLACE_DEVELOPMENT.md); review the [API](docs/MARKETPLACE_API.md), [security model](docs/MARKETPLACE_SECURITY.md), [moderation lifecycle](docs/MARKETPLACE_MODERATION.md) and [architecture](docs/MARKETPLACE_ARCHITECTURE.md) before operating it.

## Developer portal (Milestone 11C)

Run the local publishing and moderation client with `npm.cmd --prefix developer-portal ci` and `npm.cmd --prefix developer-portal run dev`; open `http://127.0.0.1:5173`. It uses real backend authentication and submission/review endpoints; tokens stay in memory. Read the [portal guide](docs/DEVELOPER_PORTAL.md), [publishing workflow](docs/MOD_PUBLISHING_GUIDE.md), and [moderation dashboard guide](docs/MODERATION_DASHBOARD.md). Portal/API local HTTP is for loopback development only; production requires HTTPS and separate deployment/security review.

## Developer CLI and community packages

Build the Node.js CLI with `npm.cmd --prefix doom-cli install` and `npm.cmd --prefix doom-cli run build`. Use `node doom-cli/dist/cli.js --help` for Windows-friendly commands. Twelve starter templates are in `sample-mods/templates/`; `doom test` runs deterministic desktop condition/action simulations and does not execute Android overlays or read hardware. Six CLI-packaged examples are under `sample-mods/community-examples/`, with distributable test archives in its `releases/` folder. See the [developer quick start](docs/developers/QUICKSTART.md), [CLI reference](docs/developers/CLI_REFERENCE.md), [test guide](docs/developers/TESTING_GUIDE.md), and [13B.4 evidence report](docs/community/13B4_COMPLETION_REPORT.md).

## Visual Mod Builder

Run the local Visual Mod Builder with `npm.cmd --prefix visual-builder ci --force` followed by `npm.cmd --prefix visual-builder run dev`, then open `http://127.0.0.1:4173`. It compiles Manifest 3 / Definition V4 projects and uses the existing `doom-cli` validator, test simulator and packager through a loopback-only bridge. See [builder guide](docs/VISUAL_BUILDER.md), [architecture](docs/BUILDER_ARCHITECTURE.md), and [developer workflow](docs/BUILDER_DEVELOPER_GUIDE.md). Templates and generated test packages are under `visual-builder/examples/` and `visual-builder/exports/`.
