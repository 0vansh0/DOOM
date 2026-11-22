#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

: "${PGHOST:?Set an isolated PostgreSQL test host}"
: "${PGDATABASE:?Set a newly created isolated restore-test database name}"
: "${PGUSER:?Set an isolated restore-test role}"
: "${PGPASSFILE:?Set a protected PostgreSQL password file path}"
: "${BACKUP_FILE:?Set the backup archive path}"

if [[ "$PGDATABASE" != *restore_test* && "$PGDATABASE" != *_restore* ]]; then
  echo 'Refusing restore: target database name must explicitly contain restore_test or end in _restore.' >&2
  exit 2
fi
if [[ ! -f "$BACKUP_FILE" || ! -r "$BACKUP_FILE" || -L "$BACKUP_FILE" ]]; then
  echo 'Backup archive must be a readable regular file.' >&2
  exit 2
fi
if [[ -f "$BACKUP_FILE.sha256" ]]; then
  (cd -- "$(dirname -- "$BACKUP_FILE")" && sha256sum --check -- "$(basename -- "$BACKUP_FILE").sha256")
else
  echo 'A matching SHA-256 sidecar is required before restore verification.' >&2
  exit 2
fi
pg_restore --list "$BACKUP_FILE" >/dev/null
# Intentionally no --clean/--create: restore must target a fresh isolated DB.
pg_restore --no-password --exit-on-error --no-owner --no-privileges --single-transaction --dbname="$PGDATABASE" "$BACKUP_FILE"
psql --no-password --set=ON_ERROR_STOP=1 --dbname="$PGDATABASE" --command='SELECT current_database(), count(*) AS restored_tables FROM information_schema.tables WHERE table_schema = '\''public'\'';'
printf 'Restore verification completed only on isolated target: %s\n' "$PGDATABASE"
