import { beforeEach, describe, expect, it, vi } from 'vitest';

const { begin } = vi.hoisted(() => ({ begin: vi.fn() }));
vi.mock('../src/db/index.js', () => ({ client: { begin }, db: {} }));
vi.mock('../src/feedback.js', () => ({ registerFeedback: vi.fn() }));

import { buildServer } from '../src/server.js';
import type { ArtifactStorage } from '../src/storage/s3.js';

const requiredMigrations = ['0001_initial.sql', '0002_feedback.sql', '0003_feedback_triage.sql', '0004_feedback_assignee.sql'];
const transaction = vi.fn(async (strings: TemplateStringsArray) =>
  strings.join('').includes('SELECT name FROM schema_migrations') ? requiredMigrations.map(name => ({ name })) : [],
);

function storageWithHealth(health: (signal?: AbortSignal) => Promise<void>): ArtifactStorage {
  return {
    putQuarantine: async () => {}, readQuarantine: async () => Buffer.alloc(0),
    publish: async () => {}, readPublished: async () => Buffer.alloc(0),
    deleteQuarantine: async () => {}, quarantineReviewUrl: async () => 'https://invalid.test/object', health,
  };
}

async function createApp(storage = storageWithHealth(async () => {})) {
  begin.mockImplementation((work: (tx: typeof transaction) => Promise<unknown>) => work(transaction));
  return buildServer({ storage });
}

describe('marketplace health endpoints', () => {
  beforeEach(() => {
    begin.mockReset();
    transaction.mockReset().mockImplementation(async (strings: TemplateStringsArray) =>
      strings.join('').includes('SELECT name FROM schema_migrations') ? requiredMigrations.map(name => ({ name })) : [],
    );
  });

  it('GET /health is a lightweight liveness check independent of dependencies', async () => {
    const storageHealth = vi.fn().mockRejectedValue(new Error('must not be called'));
    const app = await createApp(storageWithHealth(storageHealth));
    try {
      const response = await app.inject({ method: 'GET', url: '/health' });
      expect(response.statusCode).toBe(200);
      expect(response.json()).toEqual({ status: 'ok' });
      expect(begin).not.toHaveBeenCalled();
      expect(storageHealth).not.toHaveBeenCalled();
    } finally { await app.close(); }
  });

  it('GET /ready checks PostgreSQL, all required migrations, and storage', async () => {
    const storageHealth = vi.fn().mockResolvedValue(undefined);
    const app = await createApp(storageWithHealth(storageHealth));
    try {
      const response = await app.inject({ method: 'GET', url: '/ready' });
      expect(response.statusCode).toBe(200);
      expect(response.json()).toEqual({ status: 'ready' });
      expect(begin).toHaveBeenCalledTimes(1);
      expect(transaction).toHaveBeenCalledTimes(3);
      expect(storageHealth).toHaveBeenCalledTimes(1);
    } finally { await app.close(); }
  });

  it('returns sanitized 503 when PostgreSQL is unavailable', async () => {
    begin.mockRejectedValueOnce(new Error('postgres://private:secret@host/db'));
    const app = await buildServer({ storage: storageWithHealth(async () => {}) });
    try {
      const response = await app.inject({ method: 'GET', url: '/ready' });
      expect(response.statusCode).toBe(503);
      expect(response.json()).toEqual({ error: { code: 'NOT_READY', message: 'Required dependencies are unavailable.' } });
      expect(response.body).not.toMatch(/private|secret|host|database|bucket|endpoint/i);
    } finally { await app.close(); }
  });

  it('returns sanitized 503 when required migrations are missing', async () => {
    transaction.mockImplementation(async (strings: TemplateStringsArray) =>
      strings.join('').includes('SELECT name FROM schema_migrations') ? [{ name: '0001_initial.sql' }] : [],
    );
    const app = await createApp();
    try {
      const response = await app.inject({ method: 'GET', url: '/ready' });
      expect(response.statusCode).toBe(503);
      expect(response.body).not.toContain('0002_feedback.sql');
    } finally { await app.close(); }
  });

  it('returns sanitized 503 when object storage is unavailable', async () => {
    const app = await createApp(storageWithHealth(async () => { throw new Error('bucket credentials leaked'); }));
    try {
      const response = await app.inject({ method: 'GET', url: '/ready' });
      expect(response.statusCode).toBe(503);
      expect(response.body).not.toContain('bucket credentials leaked');
    } finally { await app.close(); }
  });

  it('returns sanitized 503 on dependency timeout and aborts storage probing', async () => {
    begin.mockImplementationOnce(() => new Promise(() => {}));
    let receivedSignal: AbortSignal | undefined;
    const app = await createApp(storageWithHealth(signal => {
      receivedSignal = signal;
      return new Promise(() => {});
    }));
    try {
      const response = await app.inject({ method: 'GET', url: '/ready' });
      expect(response.statusCode).toBe(503);
      expect(response.body).not.toMatch(/timeout|connection|string|credentials/i);
      expect(receivedSignal?.aborted).toBe(true);
    } finally { await app.close(); }
  }, 5000);
});
