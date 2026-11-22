import { timingSafeEqual } from 'node:crypto';
import type { FastifyInstance } from 'fastify';

const durationBuckets = [5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000];

class MarketplaceRuntimeMetrics {
  private readonly counters = new Map<string, number>();
  private readonly requests = new Map<string, { count: number; failures: number; sumMs: number; buckets: number[] }>();

  increment(name: string, amount = 1) {
    this.counters.set(name, (this.counters.get(name) ?? 0) + amount);
  }

  recordHttp(route: string, status: number, durationMs: number) {
    const normalizedRoute = route.startsWith('/') ? route : 'unmatched';
    const key = `${normalizedRoute}\u0000${Math.floor(status / 100)}xx`;
    const current = this.requests.get(key) ?? { count: 0, failures: 0, sumMs: 0, buckets: Array(durationBuckets.length).fill(0) as number[] };
    current.count++;
    if (status >= 500) current.failures++;
    current.sumMs += Math.max(0, durationMs);
    durationBuckets.forEach((bound, index) => { if (durationMs <= bound) current.buckets[index]++; });
    this.requests.set(key, current);
    this.increment('doom_http_requests_total');
    if (status >= 500) this.increment('doom_http_server_errors_total');
  }

  prometheus(): string {
    const lines = [
      '# HELP doom_http_requests_total Total HTTP requests handled by this process.',
      '# TYPE doom_http_requests_total counter',
      `doom_http_requests_total ${this.counters.get('doom_http_requests_total') ?? 0}`,
      '# HELP doom_http_server_errors_total HTTP responses with status 5xx.',
      '# TYPE doom_http_server_errors_total counter',
      `doom_http_server_errors_total ${this.counters.get('doom_http_server_errors_total') ?? 0}`,
    ];
    for (const name of ['doom_readiness_failures_total', 'doom_package_upload_failures_total', 'doom_moderation_operation_failures_total', 'doom_storage_operation_failures_total']) {
      lines.push(`# TYPE ${name} counter`, `${name} ${this.counters.get(name) ?? 0}`);
    }
    lines.push('# TYPE doom_http_request_duration_ms histogram');
    for (const [key, result] of this.requests) {
      const [route, statusClass] = key.split('\u0000');
      const labels = `route="${escapeLabel(route!)}",status_class="${statusClass}"`;
      let cumulative = 0;
      durationBuckets.forEach((bound, index) => {
        cumulative += result.buckets[index]!;
        lines.push(`doom_http_request_duration_ms_bucket{${labels},le="${bound}"} ${cumulative}`);
      });
      lines.push(`doom_http_request_duration_ms_bucket{${labels},le="+Inf"} ${result.count}`);
      lines.push(`doom_http_request_duration_ms_sum{${labels}} ${result.sumMs.toFixed(3)}`);
      lines.push(`doom_http_request_duration_ms_count{${labels}} ${result.count}`);
    }
    return `${lines.join('\n')}\n`;
  }
}

export const runtimeMetrics = new MarketplaceRuntimeMetrics();

export function registerRuntimeMetrics(app: FastifyInstance<any, any, any, any>, token?: string) {
  if (!token) return;
  app.get('/internal/metrics', async (request, reply) => {
    const supplied = request.headers.authorization ?? '';
    const expected = `Bearer ${token}`;
    const left = Buffer.from(supplied);
    const right = Buffer.from(expected);
    if (left.length !== right.length || !timingSafeEqual(left, right))
      return reply.code(404).send({ error: { code: 'NOT_FOUND', message: 'Route was not found.' } });
    reply.header('Cache-Control', 'no-store');
    return reply.type('text/plain; version=0.0.4; charset=utf-8').send(runtimeMetrics.prometheus());
  });
}

function escapeLabel(value: string) { return value.replaceAll('\\', '\\\\').replaceAll('"', '\\"').replaceAll('\n', '\\n'); }
