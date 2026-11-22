# DOOM Visual Mod Builder

`visual-builder/` is a local-first React, Vite, TypeScript, Tailwind and XYFlow authoring tool. It edits only DOOM's declarative public schema. It does not execute developer code or request Android privileges.

## Run locally

Requirements: Node.js 22.15 or later. From `E:\DOOM` on Windows:

```powershell
npm.cmd --prefix visual-builder ci --force
npm.cmd --prefix visual-builder run dev
```

Open `http://127.0.0.1:4173`. The Vite server is bound to loopback and exposes the local SDK bridge; validation, scenario testing and export invoke the existing `doom-cli` implementation. `npm.cmd --prefix visual-builder run build` creates a static UI bundle, but SDK validation/testing/package export require the loopback bridge, so use `npm run dev` for the complete local builder.

Projects autosave to browser localStorage. **Save project** downloads a versioned `.doomproject.json`; **Open** imports that project format. The import path explicitly rejects unsupported project versions. Browser storage remains on the local device; clearing browser data removes autosaved copies.

## Editing workflow

Choose one of four templates, edit metadata and requested capabilities, add triggers and ordered actions, edit nested ALL/ANY conditions, and design typed settings. Select a node to edit its properties. Action move buttons set the emitted V4 array order. The canvas accepts only forward event-to-condition or condition-to-action connections within the same trigger. Undo/redo uses Ctrl+Z / Ctrl+Shift+Z (or Ctrl+Y); Fit view and XYFlow zoom controls are available.

Run **Validate** to invoke the shared CLI schema and cross-reference validation. **Test** sends an explicit deterministic scenario to `doom test`; it records simulated results only. **Export .doommod** invokes `doom package`, then downloads the actual validated deterministic archive. Generated package files are accepted by `ArchiveReader` in the Android importer test.

## Supported editor API

The builder currently emits Manifest Schema 3, SDK API 2, Definition V4. Supported events: `battery.charging.started`, `battery.charging.stopped`, `battery.level.changed`, `mod.manual.trigger`. Conditions use literal numeric, boolean or string comparisons and nested ALL/ANY groups. Actions are `overlay.show`, `overlay.dismiss`, `ui.preview.show`, `haptics.pulse`, with the `battery-ring` and `floating-card` templates. Settings: boolean, integer, number, color, enum, bounded text, and duration. All capability requests remain explicit; Android still controls permission and runtime availability.

Templates: Battery Glow customization, Low Battery Alert, Charging Companion, and Custom Floating Card workflow. Current V4 conditions do not accept setting references as expected comparison values, so Low Battery Alert uses a visible fixed 20% threshold. Existing V3 packages remain a CLI/runtime compatibility concern; the builder project editor itself imports only builder project format 1 and compiles V4.

## Limits

The canvas mirrors V4's fixed trigger → one condition tree → ordered actions structure. It is not an arbitrary graph language. The simulator does not render Android UI, dispatch hardware broadcasts, validate real Android permission state, or measure device effects. External overlays require DOOM approval plus Android `SYSTEM_ALERT_WINDOW`; background behavior remains governed by Android. No marketplace, login, cloud save, scripting or privileged capability is included.
