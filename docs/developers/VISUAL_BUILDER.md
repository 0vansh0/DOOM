# Visual Builder

Run `npm --prefix .\visual-builder install` and `npm --prefix .\visual-builder run dev`. The Builder uses the CLI/schema artifacts and registry-backed event/action/capability catalog for graph options, validates and simulates the declarative graph, and exports packages for CLI/importer validation.

Builder simulation is a deterministic desktop model. It can record supported effects and scenario results, but does not render Android system overlays, validate device permissions or exercise real battery broadcasts. Unimplemented registry items must not be treated as executable choices. Verify each export with `doom validate`, `doom test`, `doom inspect`, then the Android importer.
