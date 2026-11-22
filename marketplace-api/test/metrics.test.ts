import Fastify from 'fastify';
import { describe, expect, it } from 'vitest';
import { registerRuntimeMetrics, runtimeMetrics } from '../src/metrics.js';

describe('internal operational metrics', () => {
  it('requires an internal bearer token and exports bounded route metrics', async () => {
    const app = Fastify();
    registerRuntimeMetrics(app, 'unit-test-metrics-token-at-least-32-characters');
    runtimeMetrics.recordHttp('/v1/catalog/mods/:modId', 200, 24);
    try {
      const denied = await app.inject({ method: 'GET', url: '/internal/metrics' });
      expect(denied.statusCode).toBe(404);
      const authorized = await app.inject({ method: 'GET', url: '/internal/metrics', headers: { authorization: 'Bearer unit-test-metrics-token-at-least-32-characters' } });
      expect(authorized.statusCode).toBe(200);
      expect(authorized.body).toContain('doom_http_request_duration_ms_bucket');
      expect(authorized.body).toContain('/v1/catalog/mods/:modId');
      expect(authorized.body).not.toContain('unit-test-metrics-token');
    } finally { await app.close(); }
  });
});
