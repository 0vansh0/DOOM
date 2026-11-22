import { pathToFileURL } from 'node:url';
import { registerFeedback } from './feedback.js';
import Fastify from 'fastify';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import cors from '@fastify/cors';
import jwt from '@fastify/jwt';
import multipart from '@fastify/multipart';
import rateLimit from '@fastify/rate-limit';
import { loadConfig } from './config.js';
import { registerAuth } from './security/auth.js';
import { registerRoutes } from './routes.js';
import { ArtifactStorage, S3ArtifactStorage } from './storage/s3.js';
import { client } from './db/index.js';
import { registerRuntimeMetrics, runtimeMetrics } from './metrics.js';

export async function buildServer(options: { storage?: ArtifactStorage; config?: ReturnType<typeof loadConfig> } = {}) {
  const config = options.config ?? loadConfig();
  const app = Fastify({ logger: { level: config.NODE_ENV === 'test' ? 'silent' : config.LOG_LEVEL, redact: ['req.headers.authorization','req.headers.cookie','*.authorization','*.cookie','*.password','*.passwordHash','*.accessToken','*.token','*.jwt','*.secret','*.report','err.parameters'] },
    bodyLimit: 1024 * 1024, trustProxy: config.TRUSTED_PROXY_ADDRESSES.split(',').map(value => value.trim()).filter(Boolean), requestIdHeader: 'x-request-id', genReqId: () => crypto.randomUUID() });
  registerRuntimeMetrics(app, config.METRICS_TOKEN);
  const requestStarts = new WeakMap<object, number>();
  app.addHook('onRequest', async request => { requestStarts.set(request, performance.now()); });
  app.addHook('onResponse', async (request, reply) => {
    const route = request.routeOptions.url ?? 'unmatched';
    const status = reply.statusCode;
    runtimeMetrics.recordHttp(route, status, Math.max(0, performance.now() - (requestStarts.get(request) ?? performance.now())));
    if (route === '/ready' && status !== 200) runtimeMetrics.increment('doom_readiness_failures_total');
    if (route === '/v1/submissions' && status >= 400) runtimeMetrics.increment('doom_package_upload_failures_total');
    if (route.startsWith('/v1/moderation/') && status >= 400) runtimeMetrics.increment('doom_moderation_operation_failures_total');
  });
  const origins = config.ALLOWED_ORIGINS.split(',').map(value => value.trim()).filter(Boolean);
  await app.register(cors, { origin: origins, credentials: false, methods: ['GET','POST','OPTIONS'] });
  await app.register(rateLimit, { max: config.RATE_LIMIT_MAX, timeWindow: config.RATE_LIMIT_WINDOW_MS, errorResponseBuilder: () => ({ error:{code:'RATE_LIMITED',message:'Too many requests. Retry shortly.'} }) });
  await app.register(jwt, { secret: config.JWT_SECRET, sign: { iss:'doom-marketplace', aud:'doom-clients' }, verify: { allowedIss:'doom-marketplace', allowedAud:'doom-clients' } });
  await app.register(multipart, { attachFieldsToBody:false, limits:{fileSize:config.UPLOAD_MAX_BYTES,files:1,fields:0,parts:1} });
  await registerAuth(app);
  registerFeedback(app);
  await registerRoutes(app, options.storage ?? new S3ArtifactStorage(config));
  app.get('/openapi.yaml', async (_request,reply) => reply.type('application/yaml; charset=utf-8').send(await readFile(resolve(process.cwd(),'openapi.yaml'),'utf8')));
  app.setNotFoundHandler((_request, reply) => reply.code(404).send({error:{code:'NOT_FOUND',message:'Route was not found.'}}));
  app.setErrorHandler((error, request, reply) => {
    request.log.error({err:error,requestId:request.id},'request failed');
    if (reply.sent) return;
    const payload = error as { statusCode?: unknown; code?: unknown; error?: { code?: unknown } };
    // @fastify/rate-limit's configured errorResponseBuilder emits a structured
    // payload without statusCode. Preserve its 429 semantics instead of
    // turning an expected limit response into INTERNAL_ERROR.
    const rateLimited = payload?.code === 'RATE_LIMITED' || payload?.error?.code === 'RATE_LIMITED';
    const statusCode = rateLimited ? 429 : error && typeof error === 'object' && 'statusCode' in error ? Number((error as {statusCode?:unknown}).statusCode) : 500;
    const status = statusCode >= 400 && statusCode < 500 ? statusCode : 500;
    if (rateLimited) return reply.code(429).send({error:{code:'RATE_LIMITED',message:'Too many requests. Retry shortly.'}});
    reply.code(status).send({error:{code:status===500?'INTERNAL_ERROR':'REQUEST_ERROR',message:status===500?'The request could not be completed.':'The request is invalid.'}});
  });
  return app;
}

export function isMainModule(url: string, entry?: string) {
  return Boolean(entry && pathToFileURL(resolve(entry)).href === url);
}

if (isMainModule(import.meta.url, process.argv[1])) {
  const config = loadConfig();
  const app = await buildServer();
  await app.listen({host:config.HOST,port:config.PORT});
  let shuttingDown = false;
  const shutdown = (signal: string) => {
    if (shuttingDown) return;
    shuttingDown = true;
    app.log.info({ signal }, 'graceful shutdown started');
    void (async () => {
      try {
        await app.close();
        await client.end({ timeout: 5 });
        app.log.info({ signal }, 'graceful shutdown complete');
      } catch (error) {
        app.log.error({ signal, err: error }, 'graceful shutdown failed');
        process.exitCode = 1;
      }
    })();
  };
  process.once('SIGINT', () => shutdown('SIGINT'));
  process.once('SIGTERM', () => shutdown('SIGTERM'));
}
