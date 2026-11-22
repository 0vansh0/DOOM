# Backup and Recovery Operations

## Available scripts

`marketplace-api/scripts/ops/backup-postgres.sh` creates a custom-format dump, validates its listing, and writes a SHA-256 sidecar. It requires `PGPASSFILE` and an existing encrypted backup directory; passwords are not CLI arguments.

`marketplace-api/scripts/ops/restore-postgres-test.sh` validates the sidecar and restricts target database names to `restore_test` or `_restore`; it does not drop/create a target. Use a new disposable isolated database and verify records/object references after restore.

Object-store backup is a provider-side versioning/replication or backup responsibility. The local Compose volume is development state, not a verified backup. Keep `doom-quarantine` private and restore objects alongside the corresponding database snapshot.

## Verification status

The scripts and procedures exist. No production backup/restore was performed, and a live isolated restore drill was not evidenced by the current task. Treat recoverability and measured RPO/RTO as **UNVERIFIED**. Existing infrastructure notes propose 24-hour RPO/8-hour RTO as targets only, not guarantees. Never run restore over an existing production database or delete Docker volumes for testing.

## Recovery sequence

Preserve source snapshots, restore into an isolated environment, verify checksums and migrations, run `/health` and `/ready`, validate catalog/download checksums, then obtain separate approval before any production traffic change. Secret recovery/rotation must use the configured external secret manager; production secret recovery has not been configured here.
