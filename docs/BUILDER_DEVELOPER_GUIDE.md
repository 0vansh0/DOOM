# Builder Developer Guide

## Try a template

1. Start `npm.cmd --prefix visual-builder run dev` from the repository root.
2. Choose Battery Glow, Low Battery Alert, Charging Companion, or Custom Floating Card in the project sidebar.
3. Select metadata, a trigger, condition, action or setting in the inspector and edit supported controls.
4. Run Validate, then Test. The test panel supplies battery level, charging state, capability approvals, Android overlay permission simulation, setting overrides and expected action order.
5. Export `.doommod`; the loopback bridge calls the project CLI validator and deterministic packager.
6. Copy the archive to the Android device and import it through DOOM Discover. Review and approve requested capabilities in the Android app. The builder cannot grant them.

## Graph and settings

V4 uses named triggers. Each trigger owns one condition tree and an ordered action list. Conditions support ALL/ANY groups and the comparisons supported by the existing SDK. Action parameters may be literals or references to declared settings. Settings controls are declarative only: Boolean, integer, decimal, color, enum, bounded text and duration.

Use drag to position nodes; valid directed connection types are event-to-condition and condition-to-action inside a trigger. Use Add Trigger, Add Action, nested group controls and action order buttons to modify canonical semantics. Unsupported free-form nodes, cycles, code snippets and custom templates are intentionally absent.

## Project interchange

`.doomproject.json` uses `projectFormatVersion: 1`. It preserves editor fields and graph positions separately from package manifests. Import rejects unknown project format versions or missing Definition V4 content rather than silently dropping fields. Browser localStorage autosaves each project; save an interchange file for backup.

## Current incompatibilities

The current Definition V4 expected comparison value is a primitive literal, not a `$setting` reference. Authors needing a configurable battery threshold must wait for a future explicitly versioned contract. The simulator is a host-side deterministic test tool; actual overlays, hardware events, Android permissions, persistence after force-stop and background delivery need device verification.
