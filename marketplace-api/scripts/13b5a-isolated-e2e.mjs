import https from 'node:https';
import fs from 'node:fs';
import crypto from 'node:crypto';
import path from 'node:path';
import postgres from 'postgres';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const origin = new URL(process.env.DOOM_TEST_API_URL ?? 'https://localhost:9443/');
const databaseUrl = process.env.DATABASE_URL;
if (origin.protocol !== 'https:' || origin.hostname !== 'localhost') throw new Error('Test API must use trusted https://localhost.');
if (!databaseUrl || !new URL(databaseUrl).pathname.includes('doom_marketplace_13b5a_20261122')) throw new Error('DATABASE_URL must target the isolated 13B.5A test database.');

const sql = postgres(databaseUrl, { max: 1, connect_timeout: 3 });
const agent = new https.Agent({ ca: fs.readFileSync(path.join(root, 'app/src/debug/res/raw/doom_marketplace_test_ca.pem')) });
const request = (route, { method = 'GET', body, token, contentType } = {}) => new Promise((resolve, reject) => {
  const headers = { accept: 'application/json' };
  if (body) { headers['content-type'] = contentType ?? 'application/json'; headers['content-length'] = body.length; }
  if (token) headers.authorization = `Bearer ${token}`;
  const req = https.request(new URL(route, origin), { method, agent, headers }, response => {
    const chunks = [];
    response.on('data', chunk => chunks.push(chunk));
    response.on('end', () => {
      const bytes = Buffer.concat(chunks);
      let json;
      try { json = JSON.parse(bytes.toString('utf8')); } catch { /* Artifact body is binary. */ }
      resolve({ status: response.statusCode, json, bytes, sha256Header: response.headers['x-doom-sha256'] });
    });
  });
  req.on('error', reject);
  if (body) req.write(body);
  req.end();
});
const assert = (ok, message) => { if (!ok) throw new Error(message); };
const json = value => Buffer.from(JSON.stringify(value));

async function register(label) {
  const nonce = crypto.randomUUID();
  const email = `doom-13b5a-${label}-${nonce}@example.test`;
  const password = `${crypto.randomUUID()}${crypto.randomUUID()}`;
  const response = await request('/v1/auth/register', { method: 'POST', body: json({ email, password, displayName: `13B5A ${label}` }) });
  const token = response.json?.data?.accessToken;
  assert(response.status === 201 && token, `${label} account registration failed (HTTP ${response.status}).`);
  return { email, token };
}

async function main() {
  const owner = await register('publisher');
  const reviewerCandidate = await register('reviewer');
  const other = await register('ordinary');
  const roleUpdate = await sql`update users set role='REVIEWER' where email=${reviewerCandidate.email} and role='DEVELOPER' returning id`;
  assert(roleUpdate.length === 1, 'Could not promote the isolated test reviewer account.');
  // The API authorizes from the current database role on each request; tokens contain identity only.
  // The isolated test account's registered JWT therefore exercises the normal role middleware.
  const reviewerToken = reviewerCandidate.token;

  const feedback = {
    category: 'Marketplace', title: '13B5A marketplace reviewer authorization',
    description: 'Synthetic integration verification report.', steps: 'Run isolated test.',
    expected: 'Reviewer-only triage succeeds.', actual: 'Checking the authenticated service.',
    appVersion: '0.1.0-beta.1', androidVersion: 'API 36', modId: '',
  };
  const submitted = await request('/v1/feedback', { method: 'POST', token: owner.token, body: json({ consent: true, report: feedback }) });
  const reportId = submitted.json?.data?.id;
  assert(submitted.status === 201 && reportId, `Feedback submission failed (HTTP ${submitted.status}).`);
  const crossUser = await request(`/v1/feedback/${reportId}`, { token: other.token });
  const developerTriage = await request(`/v1/moderation/feedback/${reportId}`, { token: owner.token });
  const reviewerRead = await request(`/v1/moderation/feedback/${reportId}`, { token: reviewerToken });
  assert(crossUser.status === 404, `Cross-user feedback returned HTTP ${crossUser.status}.`);
  assert(developerTriage.status === 403, `Developer triage returned HTTP ${developerTriage.status}.`);
  assert(reviewerRead.status === 200, `Reviewer report read returned HTTP ${reviewerRead.status}.`);
  const triage = await request(`/v1/moderation/feedback/${reportId}`, {
    method: 'POST', token: reviewerToken,
    body: json({ priority: 'P2', status: 'in-progress', component: 'marketplace-api', reproduction: 'reproduced', regression_test: '13B5A isolated integration', resolution: '' }),
  });
  assert(triage.status === 200 && triage.json?.data?.status === 'in-progress', `Reviewer triage update failed (HTTP ${triage.status}).`);
  const audit = await sql`select count(*)::int as count from audit_events where action='FEEDBACK_TRIAGE_UPDATED' and subject_id=${reportId}`;
  assert(audit[0].count >= 1, 'Triage audit event was not persisted.');

  const archive = fs.readFileSync(path.join(root, 'sample-mods/community/charging-companion.doommod'));
  const boundary = `doom-${crypto.randomUUID()}`;
  const quote = String.fromCharCode(34);
  const uploadBody = Buffer.concat([
    Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name=${quote}package${quote}; filename=${quote}charging-companion.doommod${quote}\r\nContent-Type: application/octet-stream\r\n\r\n`),
    archive, Buffer.from(`\r\n--${boundary}--\r\n`),
  ]);
  const upload = await request('/v1/submissions', { method: 'POST', token: owner.token, body: uploadBody, contentType: `multipart/form-data; boundary=${boundary}` });
  const submission = upload.json?.data;
  assert(upload.status === 201 && submission?.status === 'UPLOADED', `Upload/quarantine failed (HTTP ${upload.status}).`);
  const unpublished = await request(`/v1/catalog/mods/${encodeURIComponent(submission.modId)}`);
  assert(unpublished.status === 404, `Unapproved package was publicly listed (HTTP ${unpublished.status}).`);
  const validation = await request(`/v1/submissions/${submission.id}/validate`, { method: 'POST', token: owner.token, body: json({}) });
  assert(validation.status === 200 && validation.json?.data?.status === 'PENDING_REVIEW', `Validation failed (HTTP ${validation.status}).`);
  const ownerReviewAttempt = await request(`/v1/moderation/submissions/${submission.id}/decision`, { method: 'POST', token: owner.token, body: json({ decision: 'APPROVE', reason: 'Must be denied: self-review.' }) });
  assert(ownerReviewAttempt.status === 403, `Self-review was not blocked (HTTP ${ownerReviewAttempt.status}).`);
  const approval = await request(`/v1/moderation/submissions/${submission.id}/decision`, { method: 'POST', token: reviewerToken, body: json({ decision: 'APPROVE', reason: 'Isolated test approval.' }) });
  assert(approval.status === 200 && approval.json?.data?.status === 'APPROVED', `Reviewer approval failed (HTTP ${approval.status}).`);
  const publication = await request(`/v1/submissions/${submission.id}/publish`, { method: 'POST', token: owner.token, body: json({ category: 'utilities' }) });
  assert(publication.status === 201 && publication.json?.data?.publicationStatus === 'PUBLISHED', `Test-only publication failed (HTTP ${publication.status}).`);
  const catalog = await request(`/v1/catalog/mods/${encodeURIComponent(submission.modId)}`);
  assert(catalog.status === 200, `Published catalog lookup failed (HTTP ${catalog.status}).`);
  const downloadMeta = await request(`/v1/catalog/mods/${encodeURIComponent(submission.modId)}/versions/${encodeURIComponent(publication.json.data.version)}/download`);
  assert(downloadMeta.status === 200, `Download metadata failed (HTTP ${downloadMeta.status}).`);
  const downloaded = await request(downloadMeta.json.data.downloadUrl);
  const actualHash = crypto.createHash('sha256').update(downloaded.bytes).digest('hex');
  assert(downloaded.status === 200 && actualHash === publication.json.data.sha256 && actualHash === downloaded.sha256Header, 'Published package SHA-256 verification failed.');

  console.log(JSON.stringify({
    feedback: { submit: submitted.status, id: reportId, crossUserRead: crossUser.status, developerTriage: developerTriage.status, reviewerRead: reviewerRead.status, reviewerTriage: triage.status, auditEvents: audit[0].count },
    package: { upload: upload.status, quarantinedStatus: submission.status, unpublishedCatalog: unpublished.status, validation: validation.status, selfReview: ownerReviewAttempt.status, reviewApproval: approval.status, testOnlyPublish: publication.status, catalog: catalog.status, download: downloaded.status, sha256: actualHash },
    releaseScope: 'local isolated development database and local trusted HTTPS origin only',
  }));
}

main().catch(error => { console.error(error.message); process.exitCode = 1; }).finally(async () => { agent.destroy(); await sql.end({ timeout: 2 }); });
