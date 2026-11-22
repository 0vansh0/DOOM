import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { loadConfig } from '../src/config.js';

const base: NodeJS.ProcessEnv = {
  NODE_ENV: 'production', APP_ENVIRONMENT: 'staging',
  DATABASE_URL: 'postgres://doom:credential@database.example.com/doom_staging', DATABASE_SSL_MODE: 'verify-full',
  JWT_SECRET: 'a'.repeat(64), ACCESS_TOKEN_TTL_SECONDS: '900',
  ALLOWED_ORIGINS: 'https://portal.example.com', TRUSTED_PROXY_ADDRESSES: '10.20.0.0/16',
  S3_ENDPOINT: 'https://objects.example.com', S3_ACCESS_KEY_ID: 'staging-access', S3_SECRET_ACCESS_KEY: 'staging-secret',
  S3_QUARANTINE_BUCKET: 'doom-staging-quarantine', S3_PUBLISHED_BUCKET: 'doom-staging-published',
};
let secretDirectory: string | undefined;
afterEach(() => { if (secretDirectory) rmSync(secretDirectory, { recursive: true, force: true }); secretDirectory = undefined; });

describe('deployment configuration', () => {
  it('fails closed when staging/production requirements are incomplete', () => {
    expect(() => loadConfig({ ...base, DATABASE_SSL_MODE: 'disable' })).toThrow(/TLS certificate/);
    expect(() => loadConfig({ ...base, ALLOWED_ORIGINS: 'http://portal.example.com' })).toThrow(/HTTPS origin/);
    expect(() => loadConfig({ ...base, TRUSTED_PROXY_ADDRESSES: '' })).toThrow(/trusted proxy IP/);
    expect(() => loadConfig({ ...base, S3_ENDPOINT: 'http://127.0.0.1:9000' })).toThrow(/trusted HTTPS/);
    expect(() => loadConfig({ ...base, ADMIN_SEED_PASSWORD: 'test-admin-password' })).toThrow(/Bootstrap administrator/);
    expect(() => loadConfig({ ...base, JWT_SECRET: 'local-only-marketplace-jwt-secret-change-before-exposure' })).toThrow(/unique secret/);
  });

  it('accepts complete staging settings without local-development fallbacks', () => {
    const config = loadConfig(base);
    expect(config.APP_ENVIRONMENT).toBe('staging');
    expect(config.DATABASE_SSL_MODE).toBe('verify-full');
    expect(config.ALLOWED_ORIGINS).toBe('https://portal.example.com');
  });

  it('loads protected secret files and rejects inline/file ambiguity', () => {
    secretDirectory = mkdtempSync(join(tmpdir(), 'doom-secret-config-'));
    const jwt = join(secretDirectory, 'jwt');
    writeFileSync(jwt, 'b'.repeat(64) + '\n', { mode: 0o600 });
    const { JWT_SECRET: _unused, ...withoutInlineJwt } = base;
    expect(loadConfig({ ...withoutInlineJwt, JWT_SECRET_FILE: jwt }).JWT_SECRET).toBe('b'.repeat(64));
    expect(() => loadConfig({ ...base, JWT_SECRET_FILE: jwt })).toThrow(/either JWT_SECRET or JWT_SECRET_FILE/);
  });
});
