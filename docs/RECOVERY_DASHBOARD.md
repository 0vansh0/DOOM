# Recovery dashboard

Settings contains the system-change recovery panel. It displays the original and proposed/applied values, owner, timestamp and transaction state, and exposes Restore only for a verified applied change while Android still grants `WRITE_SETTINGS` access. Apply and restore both require a foreground confirmation dialog.

The available diagnostic action applies a two-minute timeout only after the user confirms it. The panel re-reads the current value after a write/restore. `CONFLICT`, `UNCERTAIN`, or unavailable access is reported without attempting a blind overwrite. If DOOM cannot restore, use Android Settings → Display → Screen timeout and retain the DOOM record until the state has been reviewed.

This action is app initiated, not a mod action. Imported mods cannot ask DOOM to change system settings. The optional Shizuku status and identity diagnostic remain separate and do not authorize these writes.
