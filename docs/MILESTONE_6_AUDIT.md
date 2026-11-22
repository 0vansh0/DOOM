# Milestone 6 repository audit

The existing repository uses flat Gradle modules at its root, rather than an apps/android subtree. app owns Activity navigation and Hilt composition. Feature modules expose Compose screens. core-model contains shared types, core-domain repository/use-case contracts, core-data Room/DataStore implementations and Android adapters, core-database entities/DAOs/migrations, and mod-engine the pure JVM archive validator/runtime. UI does not execute imported definitions directly.

The functional public Node/TypeScript CLI is doom-cli/src/cli.ts. An earlier JVM CLI and v1 manifest prototype remain for historical compatibility and are not the public API 2 developer workflow. Public Phase 1 archives use schemaVersion 2 with definitionVersion 1 or 2. The secure importer uses PackageFormat, ArchiveReader, ModImportCoordinator and journaled PackageStorage. Installed bytes are hashed again when loaded, including cached definitions.

Runtime truth lives in DefaultModRuntime StateFlow. Room stores desired state and requirement states, not a durable ACTIVE promise. ForegroundEffects owns regular subscriptions. AndroidEffects owns WindowManager application overlays. Explicit external sessions use ExternalOverlayService, with a user-started, non-sticky lifecycle. Phase 1 overlay evidence exists in docs/EXTERNAL_OVERLAY_FIX.md. That evidence does not prove a fresh charger connection automatically works from arbitrary background or killed states.

Existing capabilities were battery.read, overlay.render, clock.read and ui.preview. Events were charging started/stopped and manual. Effects were overlay show/dismiss and preview show. Templates were battery-ring and floating-card. Settings were fixed color, intensity and optional ring geometry. Clock subscriptions and live clock displays were absent.

## Decisions

1. Preserve old schema documents and route explicit new version tuples to new schemas. Reject unknown/mixed versions.
2. Ship a public metadata registry, with trusted Kotlin handler enums and cross-language conformance tests. Packages can compose public handlers but cannot add native handlers.
3. Extend the existing runtime/lifecycle and Room settings instead of creating a second engine or grant store.
4. Add safe PowerManager reads and bounded VibratorManager actions. Keep privileged adapters at design stage.
5. Add developer-defined integer options in the existing settings JSON and nullable definition metadata in installed_mods. Room migration 4 to 5 adds a column, preserving installed rows and child records. No destructive migration is configured.
6. Export actual API probes with explicit status semantics. Record device tests separately from API availability.
7. Keep timers, arbitrary expressions, multi-action graphs, marketplace, visual builder, root and ADB/Shizuku implementation out of this first API 2 increment.
