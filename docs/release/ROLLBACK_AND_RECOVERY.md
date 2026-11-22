# Update rollback and recovery

Mod update is allowed only when the installed mod is disabled and the downloaded candidate has passed identity, checksum and archive validation. `PackageStorage.replaceForUpdate` records a marker with old/new package hashes, atomically moves the original package to a private previous-version file, and places the staged package at the installed path. Room metadata commits in a transaction. Startup reconciliation compares the database hash and on-disk hashes; it restores the old package if metadata is old and the journal shows a safe recoverable state, keeps the new package if metadata committed to the new hash, and preserves uncertain evidence rather than overwriting unrelated bytes.

This filesystem/Room sequence is journaled but not a single cross-resource atomic transaction. Process interruption, disk/provider errors, and external modifications may require manual recovery. Privileged device changes are not replayed automatically. Application update and mod update are separate operations.

The release's Android instrumentation recovery tests were not executed in this task. Run them on a disposable signed test installation before claiming upgrade or rollback readiness.
