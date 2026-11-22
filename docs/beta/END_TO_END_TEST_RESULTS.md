# End-to-End Test Results — Milestone 13B.5

| Scenario | Result | Evidence / limitation |
|---|---|---|
| A — Mod development | **PASS (CLI/package/importer layers)** | Generated Visual Builder example scenarios passed simulation; SDK bridge archive passed `doom validate`, `doom inspect`, and Kotlin `ArchiveReader` import. Physical execution of this exact package is not verified. |
| B — Marketplace distribution | **PASS (isolated backend)** / **BLOCKED (Android)** | Backend upload → quarantine → validation → reviewer approval → publication → download/checksum tests ran against disposable DB and SeaweedFS. Android trusted HTTPS endpoint unavailable, so Android catalog/download/import/update is blocked. |
| C — Feedback lifecycle | **PASS (backend auth and ownership tests)** / **BLOCKED (Android live)** | API tests pass for consent, auth/ownership, reviewer permissions and rate limit. No Android report submission over approved HTTPS/admin UI browser verification. |
| D — Runtime recovery | **PARTIAL** | JVM workflow/runtime/recovery tests pass; controlled failure → fault suspension → persistent Safe Mode → recovery on physical Android was not run. |
| E — Update compatibility | **PASS (backend package update tests)** / **BLOCKED (Android)** | Backend immutable version/checksum/quarantine tests pass. Device mod settings preservation across marketplace update was not exercised. |

## Visual Builder archive evidence

The `charging-companion` package exported from the running Vite SDK bridge is at `%TEMP%\doom-visual-builder-export-13b5.doommod`, 1,830 bytes, SHA-256 `61043433a6c488b4aadcec0cbde0e96727dae7739850f752024c44c7e4320b17`. CLI inspection found manifest schema 3 and definition 4, one trigger, two ordered actions, declared settings and sequential stop-on-failure workflow. The same archive was imported by the Android Kotlin `ArchiveReader` regression test. The Vite browser GUI itself was not interactively operated in this run.
