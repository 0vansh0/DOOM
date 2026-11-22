# DOOM CLI Reference

The CLI is the TypeScript package in `doom-cli` and requires Node.js 22.15 or later.

```powershell
npm.cmd --prefix doom-cli install
npm.cmd --prefix doom-cli run build
node doom-cli/dist/cli.js --help
```

Commands:

| Command | Purpose |
| --- | --- |
| `doom create <name> [--template <name>]` | Create a source project from a bundled template. |
| `doom validate <directory-or-doommod>` | Validate the manifest, definition and package files. |
| `doom package <directory>` | Validate and create a deterministic `.doommod` archive. Existing output is never overwritten. |
| `doom inspect <package.doommod>` | Validate and print metadata, SHA-256 and per-file hashes. |
| `doom test <project-directory> <scenario.json>` | Run deterministic host-side declarative scenarios. No Android APIs are invoked. |
| `doom capabilities` | Print the public SDK registry. |

Exit status is 0 for success, 1 for scenario assertion failures, and 2 for usage, input or validation errors. `npm.cmd --prefix doom-cli test` builds and runs CLI conformance tests on Windows.

Package roots contain `doom.json`, `mod.json`, and optional approved `assets/*.png` files only. Test scenarios live outside the package and are never included in an archive.
