# Backup and restore procedures

`marketplace-api/scripts/ops/backup-postgres.sh` creates a custom-format `pg_dump`, runs `pg_restore --list`, and writes a SHA-256 sidecar. It requires `PGHOST`, `PGPORT`, `PGUSER`, `PGDATABASE`, `PGPASSFILE`, and an existing `BACKUP_DIR` on encrypted storage. Credentials are not accepted as command-line arguments. Protect and rotate the passfile through the host secret manager.

`restore-postgres-test.sh` verifies the sidecar and refuses target database names unless they contain `restore_test` or end in `_restore`. It restores transactionally without drop/create flags. Run only against a disposable isolated database, then verify application reads and object references. These scripts were prepared for verification; no production backup or restore was performed.

Provision the runtime role separately from the migration owner, using an administrator-controlled channel. A typical starting point (review against the actual schema before applying) is:

```sql
CREATE ROLE doom_marketplace_app LOGIN;
GRANT CONNECT ON DATABASE doom_marketplace TO doom_marketplace_app;
GRANT USAGE ON SCHEMA public TO doom_marketplace_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO doom_marketplace_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO doom_marketplace_app;
ALTER DEFAULT PRIVILEGES FOR ROLE doom_marketplace_migrator IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO doom_marketplace_app;
ALTER DEFAULT PRIVILEGES FOR ROLE doom_marketplace_migrator IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO doom_marketplace_app;
```

The API `DATABASE_URL_FILE` should use this runtime role; `MIGRATION_DATABASE_URL_FILE` should use a separately controlled migration role and be mounted only into the one-shot migration service. The SQL is a reviewed setup example, not something run by the application.

Proposed initial objectives, pending business approval and measured restore exercises: RPO 24 hours and RTO 8 hours. Retain encrypted daily backups for 30 days and monthly copies for 90 days only after legal, cost, and data-retention review. Object storage requires versioning/replication or an equivalent provider backup and lifecycle policy; no policy is activated here.
