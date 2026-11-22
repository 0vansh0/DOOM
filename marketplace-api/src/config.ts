import { readFileSync } from 'node:fs';
import { z } from 'zod';

const envSchema = z.object({
  APP_ENVIRONMENT: z.enum(['local', 'staging', 'production']).default('local'),
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  HOST: z.string().default('127.0.0.1'),
  PORT: z.coerce.number().int().min(1).max(65535).default(8787),
  DATABASE_URL: z.string().url(),
  MIGRATION_DATABASE_URL: z.string().url().optional(),
  DATABASE_SSL_MODE: z.enum(['disable', 'require', 'verify-full']).default('disable'),
  DB_POOL_SIZE: z.coerce.number().int().min(1).max(64).default(12),
  DB_CONNECT_TIMEOUT_SECONDS: z.coerce.number().int().min(1).max(60).default(10),
  DB_IDLE_TIMEOUT_SECONDS: z.coerce.number().int().min(1).max(600).default(20),
  DB_QUEUE_TIMEOUT_SECONDS: z.coerce.number().int().min(1).max(60).default(10),
  DB_QUERY_TIMEOUT_SECONDS: z.coerce.number().int().min(1).max(120).default(30),
  DB_STATEMENT_TIMEOUT_MS: z.coerce.number().int().min(1000).max(120000).default(30000),
  DB_LOCK_TIMEOUT_MS: z.coerce.number().int().min(100).max(30000).default(5000),
  DB_IDLE_TRANSACTION_TIMEOUT_MS: z.coerce.number().int().min(1000).max(120000).default(30000),
  ARGON2_MEMORY_KIB: z.coerce.number().int().min(8192).max(1048576).default(19456),
  JWT_SECRET: z.string().min(32),
  ACCESS_TOKEN_TTL_SECONDS: z.coerce.number().int().min(60).max(3600).default(900),
  REFRESH_TOKEN_TTL_SECONDS: z.coerce.number().int().min(3600).max(2592000).default(2592000),
  ALLOWED_ORIGINS: z.string().default('http://localhost:5173'),
  TRUSTED_PROXY_ADDRESSES: z.string().default(''),
  LOG_LEVEL: z.enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace', 'silent']).default('info'),
  METRICS_TOKEN: z.string().min(32).optional(),
  S3_ENDPOINT: z.string().url(), S3_REGION: z.string().default('us-east-1'),
  S3_ACCESS_KEY_ID: z.string().min(1), S3_SECRET_ACCESS_KEY: z.string().min(1),
  S3_FORCE_PATH_STYLE: z.enum(['true', 'false']).transform(v => v === 'true').default(true),
  S3_QUARANTINE_BUCKET: z.string().regex(/^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$/),
  S3_PUBLISHED_BUCKET: z.string().regex(/^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$/),
  RATE_LIMIT_MAX: z.coerce.number().int().min(1).max(10000).default(120),
  RATE_LIMIT_WINDOW_MS: z.coerce.number().int().min(1000).max(3600000).default(60000),
  AUTH_REGISTER_RATE_LIMIT: z.coerce.number().int().min(1).max(100).default(5),
  AUTH_LOGIN_RATE_LIMIT: z.coerce.number().int().min(1).max(100).default(10),
  AUTH_RATE_LIMIT_WINDOW_MS: z.coerce.number().int().min(1000).max(3600000).default(60000),
  FEEDBACK_RATE_LIMIT_MAX: z.coerce.number().int().min(1).max(1000).default(10),
  FEEDBACK_RATE_LIMIT_WINDOW_MS: z.coerce.number().int().min(1000).max(3600000).default(60000),
  UPLOAD_MAX_BYTES: z.coerce.number().int().min(1024).max(10 * 1024 * 1024).default(10 * 1024 * 1024),
  UPLOADS_PER_DAY: z.coerce.number().int().min(1).max(1000).default(20),
  UPLOAD_BYTES_PER_DAY: z.coerce.number().int().min(10 * 1024 * 1024).max(2 * 1024 * 1024 * 1024).default(100 * 1024 * 1024),
  VALIDATOR_CONCURRENCY: z.coerce.number().int().min(1).max(8).default(2),
  WORKER_TIMEOUT_MS: z.coerce.number().int().min(1000).max(60000).default(15000),
  ADMIN_SEED_EMAIL: z.string().email().optional(), ADMIN_SEED_PASSWORD: z.string().min(12).optional(),
  DOOM_MIGRATIONS_APPROVED: z.enum(['true', 'false']).default('false'),
}).superRefine((value, context) => {
  if (value.S3_QUARANTINE_BUCKET === value.S3_PUBLISHED_BUCKET)
    context.addIssue({ code: 'custom', path: ['S3_PUBLISHED_BUCKET'], message: 'Quarantine and published buckets must differ.' });
  if (value.APP_ENVIRONMENT === 'local') return;

  if (value.NODE_ENV !== 'production')
    context.addIssue({ code: 'custom', path: ['NODE_ENV'], message: 'Staging and production require NODE_ENV=production.' });
  if (value.JWT_SECRET.length < 64 || /replace-with|change-before-exposure|local-only|test-secret/i.test(value.JWT_SECRET))
    context.addIssue({ code: 'custom', path: ['JWT_SECRET'], message: 'Staging and production require a unique secret of at least 64 characters.' });
  if (value.DATABASE_SSL_MODE !== 'verify-full')
    context.addIssue({ code: 'custom', path: ['DATABASE_SSL_MODE'], message: 'Staging and production require PostgreSQL TLS certificate and hostname verification.' });
  if (!value.TRUSTED_PROXY_ADDRESSES.split(',').map(item => item.trim()).filter(Boolean).length)
    context.addIssue({ code: 'custom', path: ['TRUSTED_PROXY_ADDRESSES'], message: 'Set explicit trusted proxy IP addresses or CIDRs for the deployed network path.' });
  if (value.ACCESS_TOKEN_TTL_SECONDS > 900)
    context.addIssue({ code: 'custom', path: ['ACCESS_TOKEN_TTL_SECONDS'], message: 'Staging and production access tokens may not exceed 15 minutes.' });
  if (value.ADMIN_SEED_EMAIL || value.ADMIN_SEED_PASSWORD)
    context.addIssue({ code: 'custom', path: ['ADMIN_SEED_EMAIL'], message: 'Bootstrap administrator credentials are not accepted by the application service.' });
  const origins = value.ALLOWED_ORIGINS.split(',').map(origin => origin.trim()).filter(Boolean);
  if (origins.length === 0 || origins.some(origin => !isPublicHttpsOrigin(origin)))
    context.addIssue({ code: 'custom', path: ['ALLOWED_ORIGINS'], message: 'Every staging/production CORS origin must be a non-local HTTPS origin.' });
  const database = new URL(value.DATABASE_URL);
  if (!['postgres:', 'postgresql:'].includes(database.protocol))
    context.addIssue({ code: 'custom', path: ['DATABASE_URL'], message: 'DATABASE_URL must use PostgreSQL.' });
  const storage = new URL(value.S3_ENDPOINT);
  if (storage.protocol !== 'https:' || isLoopbackOrLocal(storage.hostname) || storage.hostname.endsWith('.invalid'))
    context.addIssue({ code: 'custom', path: ['S3_ENDPOINT'], message: 'Staging/production object storage must use a trusted HTTPS endpoint.' });
  if (['doom-quarantine', 'doom-published'].includes(value.S3_QUARANTINE_BUCKET) || ['doom-quarantine', 'doom-published'].includes(value.S3_PUBLISHED_BUCKET))
    context.addIssue({ code: 'custom', path: ['S3_QUARANTINE_BUCKET'], message: 'Staging/production must use storage buckets separate from local development.' });
  if (value.APP_ENVIRONMENT === 'production' && !value.METRICS_TOKEN)
    context.addIssue({ code: 'custom', path: ['METRICS_TOKEN'], message: 'Production requires a dedicated token for the internal metrics endpoint.' });
});

export type AppConfig = z.infer<typeof envSchema>;

const secretFiles: ReadonlyArray<readonly [string, string]> = [
  ['DATABASE_URL', 'DATABASE_URL_FILE'],
  ['MIGRATION_DATABASE_URL', 'MIGRATION_DATABASE_URL_FILE'],
  ['JWT_SECRET', 'JWT_SECRET_FILE'],
  ['S3_ACCESS_KEY_ID', 'S3_ACCESS_KEY_ID_FILE'],
  ['S3_SECRET_ACCESS_KEY', 'S3_SECRET_ACCESS_KEY_FILE'],
  ['METRICS_TOKEN', 'METRICS_TOKEN_FILE'],
];

function loadSecretFiles(env: NodeJS.ProcessEnv): NodeJS.ProcessEnv {
  const resolved = { ...env };
  for (const [valueName, fileName] of secretFiles) {
    const file = env[fileName]?.trim();
    if (!file) continue;
    if (env[valueName] !== undefined)
      throw new Error(`Set either ${valueName} or ${fileName}, not both.`);
    try {
      resolved[valueName] = readFileSync(file, 'utf8').replace(/[\r\n]+$/, '');
    } catch {
      throw new Error(`Unable to read required secret file configured by ${fileName}.`);
    }
  }
  return resolved;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): AppConfig {
  let secretResolved: NodeJS.ProcessEnv;
  try {
    secretResolved = loadSecretFiles(env);
  } catch (error) {
    if (error instanceof Error) throw error;
    throw new Error('Secret configuration could not be loaded.');
  }
  const result = envSchema.safeParse(secretResolved);
  if (!result.success) throw new Error(`Invalid environment configuration: ${result.error.issues.map(i => `${i.path.join('.')}: ${i.message}`).join('; ')}`);
  return result.data;
}

function isPublicHttpsOrigin(origin: string): boolean {
  try {
    const parsed = new URL(origin);
    return parsed.protocol === 'https:' && parsed.origin === origin && !isLoopbackOrLocal(parsed.hostname) && !parsed.hostname.endsWith('.invalid');
  } catch { return false; }
}

function isLoopbackOrLocal(host: string): boolean {
  const lower = host.toLowerCase();
  return lower === 'localhost' || lower === '::1' || lower === '0.0.0.0' || lower.startsWith('127.') || lower.endsWith('.local');
}
