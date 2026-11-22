import { readFileSync } from 'node:fs';
import http from 'node:http';
import https from 'node:https';

const listenHost = '127.0.0.1';
const listenPort = Number(process.env.DOOM_GATEWAY_PORT ?? '9443');
const upstream = new URL(process.env.DOOM_GATEWAY_UPSTREAM ?? 'http://127.0.0.1:8787');
const pfxPath = process.env.DOOM_GATEWAY_PFX_PATH;
const pfxPassphrase = process.env.DOOM_GATEWAY_PFX_PASSPHRASE;

if (upstream.protocol !== 'http:' || !['127.0.0.1', 'localhost'].includes(upstream.hostname)) {
  throw new Error('Development gateway upstream must be a loopback HTTP origin.');
}
if (!pfxPath || !pfxPassphrase) throw new Error('Development gateway TLS certificate configuration is missing.');

const server = https.createServer({
  pfx: readFileSync(pfxPath),
  passphrase: pfxPassphrase,
  minVersion: 'TLSv1.2',
}, (request, response) => {
  const host = request.headers.host?.toLowerCase();
  if (host !== `localhost:${listenPort}` && host !== 'localhost') {
    response.writeHead(421, { 'content-type': 'application/json' });
    response.end('{"error":{"code":"MISDIRECTED_REQUEST","message":"Host is not allowed."}}');
    return;
  }

  const proxy = http.request({
    hostname: upstream.hostname,
    port: Number(upstream.port || 80),
    method: request.method,
    path: request.url,
    headers: { ...request.headers, host: upstream.host },
  }, upstreamResponse => {
    response.writeHead(upstreamResponse.statusCode ?? 502, upstreamResponse.headers);
    upstreamResponse.pipe(response);
  });
  proxy.on('error', () => {
    if (!response.headersSent) response.writeHead(502, { 'content-type': 'application/json' });
    response.end('{"error":{"code":"UPSTREAM_UNAVAILABLE","message":"Development API is unavailable."}}');
  });
  request.pipe(proxy);
});

server.listen(listenPort, listenHost, () => {
  process.stdout.write(`Local HTTPS gateway listening on ${listenHost}:${listenPort}; upstream ${upstream.origin}\n`);
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.once(signal, () => server.close(() => process.exit(0)));
}
