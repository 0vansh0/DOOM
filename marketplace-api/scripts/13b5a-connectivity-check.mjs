import https from 'node:https';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import postgres from 'postgres';
import { S3Client, ListBucketsCommand, PutObjectCommand, GetObjectCommand, DeleteObjectCommand } from '@aws-sdk/client-s3';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const origin = new URL(process.env.DOOM_TEST_API_URL ?? 'https://localhost:9443/');
const databaseUrl = process.env.DATABASE_URL;
const endpoint = process.env.S3_ENDPOINT;
const quarantine = process.env.S3_QUARANTINE_BUCKET;
const published = process.env.S3_PUBLISHED_BUCKET;
if (origin.protocol !== 'https:' || origin.hostname !== 'localhost') throw new Error('Health probes require trusted https://localhost.');
if (!databaseUrl || !databaseUrl.includes('doom_marketplace_13b5a_20261122')) throw new Error('Use the isolated 13B.5A database.');
if (!endpoint || !quarantine || !published) throw new Error('S3 test endpoint and buckets must be explicitly configured.');

const agent = new https.Agent({ ca: fs.readFileSync(path.join(root, 'app/src/debug/res/raw/doom_marketplace_test_ca.pem')) });
const getJson = route => new Promise((resolve, reject) => {
  https.get(new URL(route, origin), { agent, headers: { accept: 'application/json' } }, response => {
    const chunks = [];
    response.on('data', chunk => chunks.push(chunk));
    response.on('end', () => resolve({ status: response.statusCode, body: Buffer.concat(chunks).toString('utf8') }));
  }).on('error', reject);
});

const sql = postgres(databaseUrl, { max: 1, connect_timeout: 3 });
const s3 = new S3Client({ endpoint, region: process.env.S3_REGION ?? 'us-east-1', forcePathStyle: process.env.S3_FORCE_PATH_STYLE !== 'false', credentials: { accessKeyId: process.env.S3_ACCESS_KEY_ID, secretAccessKey: process.env.S3_SECRET_ACCESS_KEY } });
const key = `.13b5a-check/${crypto.randomUUID()}`;
const bytes = Buffer.from('DOOM local S3 roundtrip');

try {
  const health = await getJson('/health');
  const ready = await getJson('/ready');
  if (health.status !== 200 || JSON.parse(health.body).status !== 'ok') throw new Error('Liveness endpoint did not report healthy.');
  if (ready.status !== 200 || JSON.parse(ready.body).status !== 'ready') throw new Error('Readiness endpoint did not report ready.');
  const dbRows = await sql`select 1 as connected`;
  const migrations = await sql`select name from schema_migrations order by name`;
  if (dbRows[0]?.connected !== 1 || migrations.length < 3) throw new Error('PostgreSQL or required migrations are unavailable.');
  const buckets = await s3.send(new ListBucketsCommand({}));
  const names = new Set((buckets.Buckets ?? []).map(bucket => bucket.Name));
  if (!names.has(quarantine) || !names.has(published)) throw new Error('Required private buckets are missing.');
  await s3.send(new PutObjectCommand({ Bucket: quarantine, Key: key, Body: bytes }));
  const object = await s3.send(new GetObjectCommand({ Bucket: quarantine, Key: key }));
  const downloaded = Buffer.from(await object.Body.transformToByteArray());
  const matches = downloaded.equals(bytes);
  await s3.send(new DeleteObjectCommand({ Bucket: quarantine, Key: key }));
  if (!matches) throw new Error('S3 read/write roundtrip returned mismatched bytes.');
  console.log(JSON.stringify({ health, ready, postgres: 'connected', appliedMigrations: migrations.length, requiredBuckets: 'present', s3Roundtrip: 'PASS', s3Bytes: downloaded.length, temporaryObjectDeleted: true }));
} finally {
  agent.destroy();
  s3.destroy();
  await sql.end({ timeout: 2 });
}
