import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import postgres from 'postgres';
import { loadConfig } from '../config.js';

const migrationNames = ['0001_initial.sql', '0002_feedback.sql', '0003_feedback_triage.sql', '0004_feedback_assignee.sql'] as const;
const migrationLockId = 861927410;
const destructiveSql = /(^|\n)\s*(DROP\s+(TABLE|COLUMN|SCHEMA|TYPE|INDEX|FUNCTION|TRIGGER)|TRUNCATE\s+)/i;

export async function runMigrations(mode: 'check' | 'apply' = 'check') {
  const config = loadConfig();
  if (mode === 'apply' && config.APP_ENVIRONMENT !== 'local' && config.DOOM_MIGRATIONS_APPROVED !== 'true')
    throw new Error('Non-local migrations require DOOM_MIGRATIONS_APPROVED=true after backup and release approval.');

  const sql = postgres(config.MIGRATION_DATABASE_URL ?? config.DATABASE_URL, {
    max: 1,
    connect_timeout: config.DB_CONNECT_TIMEOUT_SECONDS,
    queue_timeout: config.DB_QUEUE_TIMEOUT_SECONDS,
    query_timeout: config.DB_QUERY_TIMEOUT_SECONDS,
    ssl: config.DATABASE_SSL_MODE,
    connection: {
      application_name: 'doom-marketplace-migrations',
      statement_timeout: config.DB_STATEMENT_TIMEOUT_MS,
      lock_timeout: config.DB_LOCK_TIMEOUT_MS,
      idle_in_transaction_session_timeout: config.DB_IDLE_TRANSACTION_TIMEOUT_MS,
    },
  });
  try {
    const files = await Promise.all(migrationNames.map(async name => {
      const text = await readFile(resolve(process.cwd(), 'migrations', name), 'utf8');
      if (destructiveSql.test(text)) throw new Error(`Migration ${name} contains a destructive statement and requires a separately reviewed migration plan.`);
      return { name, text, checksum: createHash('sha256').update(text).digest('hex') };
    }));

    if (mode === 'check') {
      const [state] = await sql`select to_regclass('public.schema_migrations') as migration_table`;
      if (!state?.migration_table) {
        console.log(`Migration check: ${files.length} migrations pending; no database changes made.`);
        return { pending: files.map(file => file.name), drift: [] as string[] };
      }
      const [checksumColumn] = await sql`select 1 from information_schema.columns where table_schema='public' and table_name='schema_migrations' and column_name='checksum'`;
      const applied: Array<{ name: unknown; checksum: unknown }> = checksumColumn
        ? await sql`select name, checksum from schema_migrations order by name`
        : (await sql`select name from schema_migrations order by name`).map(row => ({ name: row.name, checksum: null }));
      const appliedByName = new Map(applied.map(row => [String(row.name), row.checksum == null ? null : String(row.checksum)]));
      const drift = files.filter(file => appliedByName.has(file.name) && appliedByName.get(file.name) !== null && appliedByName.get(file.name) !== file.checksum).map(file => file.name);
      if (drift.length) throw new Error(`Migration checksum mismatch: ${drift.join(', ')}. Refusing to modify the database.`);
      const pending = files.filter(file => !appliedByName.has(file.name)).map(file => file.name);
      const legacy = files.filter(file => appliedByName.has(file.name) && appliedByName.get(file.name) === null).map(file => file.name);
      console.log(`Migration check: ${pending.length} pending; ${legacy.length} previously applied migration(s) have no historical checksum; no database changes made.`);
      return { pending, drift: [], legacy };
    }

    await sql`select pg_advisory_lock(${migrationLockId})`;
    try {
      await sql`create table if not exists schema_migrations (name text primary key, checksum text, applied_at timestamptz not null default now())`;
      await sql`alter table schema_migrations add column if not exists checksum text`;
      for (const file of files) {
        const [done] = await sql`select name, checksum from schema_migrations where name=${file.name}`;
        if (done) {
          if (done.checksum != null && done.checksum !== file.checksum)
            throw new Error(`Migration checksum mismatch for ${file.name}. Refusing to continue.`);
          continue;
        }
        await sql.begin(async tx => {
          await tx.unsafe(file.text);
          await tx`insert into schema_migrations (name, checksum) values (${file.name}, ${file.checksum})`;
        });
        console.log(`Applied ${file.name}`);
      }
      console.log('Migrations are current.');
    } finally {
      await sql`select pg_advisory_unlock(${migrationLockId})`;
    }
  } finally { await sql.end({ timeout: 5 }); }
}

if (process.argv[1] && /(?:^|[\\/])migrate\.(?:ts|js)$/.test(process.argv[1])) {
  const argument = process.argv[2];
  if (argument && argument !== '--check' && argument !== '--apply') throw new Error('Usage: migrate.ts [--check|--apply]');
  await runMigrations(argument === '--apply' ? 'apply' : 'check');
}
