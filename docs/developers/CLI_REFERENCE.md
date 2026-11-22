# CLI reference

Build/run locally with `npm --prefix .\doom-cli run build` then `node .\doom-cli\dist\cli.js --help`.

Commands: `create --template <name> --out <dir>` scaffolds a project; `validate <project-or-package>` runs the shared manifest/definition/package validators; `test <project> <scenario.json>` evaluates a deterministic declarative scenario (both paths are required); `package <project> --out <file.doommod>` produces a validated archive; `inspect <file.doommod>` prints parsed identity, version, capabilities and checksum. Run `doom <command> --help` for supported options. Failures return nonzero.

Templates currently include `battery-glow`, `power-card`, `focus-pulse`, `low-battery-card`, `basic-event-driven`, `battery-triggered`, `overlay-based`, `conditional-workflow`, `multi-action-workflow`, `dynamic-settings`, `optional-capability`, and `shizuku-safe`. The Shizuku-safe template demonstrates host-side limits and a normal UI fallback; it does not invoke Shizuku from a mod. See each template's README and test-scenario JSON under `sample-mods/templates/`.
