# Change journal and rollback

Room database version 7 adds `change_transactions` through additive migration 6→7. It records transaction ID, owner, operation/version, fixed target identity, original/requested/applied values, timestamps, state, failure reason and non-sensitive recovery metadata. It intentionally has no foreign key to installed mods so uninstall preserves recovery information.

The coordinator serializes operations per target and rate-limits operation IDs. For screen timeout it reads the original value, commits `PREPARED`, checks permission and request bounds, writes through `Settings.System`, reads back, then commits `APPLIED`. Errors after the prepared journal is stored become `UNCERTAIN`; no failed or unverified write is called applied.

Restore is a separate explicit confirmation. DOOM restores only if current equals the recorded applied value. A mismatch becomes `CONFLICT`, leaves the user's current value unchanged and remains visible. Safe mode blocks new setting writes but does not block a user-confirmed rollback. `PREPARED`, `RESTORING`, and `UNCERTAIN` entries are reconciled against live state at app startup. Reconciliation never deletes unresolved rows.

The dashboard is in Settings. A missing Android `WRITE_SETTINGS` grant links to Android's management screen; returning to DOOM rechecks the grant. Opening settings does not grant permission. Reboot/process death/permission revocation can prevent automatic recovery. Manual recovery is to use Android Display/Screen timeout settings; DOOM retains its journal entry for review.
