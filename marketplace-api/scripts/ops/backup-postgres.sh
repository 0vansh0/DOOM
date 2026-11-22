#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

: "${PGHOST:?Set PostgreSQL host through the approved secret/configuration channel}"
: "${PGDATABASE:?Set the database name}"
: "${PGUSER:?Set the least-privilege backup role}"
: "${PGPASSFILE:?Set a protected PostgreSQL password file path}"
: "${BACKUP_DIR:?Set an encrypted, access-controlled backup directory}"

if [[ ! -f "$PGPASSFILE" || ! -r "$PGPASSFILE" ]]; then
  echo 'PGPASSFILE must reference a readable protected file.' >&2
  exit 2
fi
if [[ -L "$BACKUP_DIR" ]]; then
  echo 'Refusing a symlink backup directory.' >&2
  exit 2
fi
mkdir -p -- "$BACKUP_DIR"
chmod 700 -- "$BACKUP_DIR"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
tmp="$BACKUP_DIR/.doom-marketplace-$stamp.dump.partial"
out="$BACKUP_DIR/doom-marketplace-$stamp.dump"
trap 'rm -f -- "$tmp"' EXIT

pg_dump --no-password --format=custom --no-owner --no-privileges --file="$tmp"
pg_restore --list "$tmp" >/dev/null
mv -- "$tmp" "$out"
sha256sum -- "$out" > "$out.sha256"
printf 'Backup created and archive listing validated: %s\n' "$out"
printf 'Checksum file: %s.sha256\n' "$out"
