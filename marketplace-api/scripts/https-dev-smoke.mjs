import https from 'node:https';
import fs from 'node:fs';
import crypto from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const baseUrl = new URL(process.env.DOOM_TEST_API_URL ?? 'https://localhost:9443/');
if (baseUrl.protocol !== 'https:' || baseUrl.hostname !== 'localhost') {
  throw new Error('The local smoke test requires the trusted https://localhost development origin.');
}
const caPath = path.join(repoRoot, 'app', 'src', 'debug', 'res', 'raw', 'doom_marketplace_test_ca.pem');
const ca = fs.readFileSync(caPath);
const agent = new https.Agent({ ca });

function request(route, { method = 'GET', body, token, contentType } = {}) {
  return new Promise((resolve, reject) => {
    const headers = { accept: 'application/json' };
    if (body) {
      headers['content-type'] = contentType ?? 'application/json';
      headers['content-length'] = body.length;
    }
    if (token) headers.authorization = `Bearer ${token}`;
    const req = https.request(new URL(route, baseUrl), { method, agent, headers }, response => {
      let text = '';
      response.setEncoding('utf8');
      response.on('data', chunk => { text += chunk; });
      response.on('end', () => {
        let json;
        try { json = JSON.parse(text); } catch { /* Non-JSON error is reported by status only. */ }
        resolve({ status: response.statusCode, json });
      });
    });
    req.on('error', reject);
    if (body) req.write(body);
    req.end();
  });
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function registerDeveloper(label) {
  const id = crypto.randomUUID();
  const email = `doom-13b5a-${label}-${id}@example.test`;
  const password = `${crypto.randomUUID()}${crypto.randomUUID()}`;
  const response = await request('/v1/auth/register', {
    method: 'POST',
    body: Buffer.from(JSON.stringify({ email, password, displayName: `13B5A ${label} test` })),
  });
  const token = response.json?.data?.accessToken;
  assert(response.status === 201 && token, `${label} test account registration failed (HTTP ${response.status}).`);
  return { email, password, token };
}

async function main() {
  const owner = await registerDeveloper('owner');
  const wrongPassword = await request('/v1/auth/login', {
    method: 'POST', body: Buffer.from(JSON.stringify({ email: owner.email, password: `wrong-${crypto.randomUUID()}` })),
  });
  assert(wrongPassword.status === 401, `Invalid credentials returned HTTP ${wrongPassword.status}.`);
  const login = await request('/v1/auth/login', {
    method: 'POST', body: Buffer.from(JSON.stringify({ email: owner.email, password: owner.password })),
  });
  const token = login.json?.data?.accessToken;
  assert(login.status === 200 && token, `Login failed (HTTP ${login.status}).`);

  const report = {
    category: 'Marketplace', title: '13B5A local HTTPS smoke report',
    description: 'Synthetic local development integration verification.',
    steps: 'Run the local HTTPS smoke script.', expected: 'Private authenticated report is stored.',
    actual: 'Checking the development API contract.', appVersion: '0.1.0-beta.1',
    androidVersion: 'API 36', modId: '',
  };
  const noConsent = await request('/v1/feedback', {
    method: 'POST', token, body: Buffer.from(JSON.stringify({ consent: false, report })),
  });
  assert(noConsent.status === 400, `Missing consent returned HTTP ${noConsent.status}.`);
  const submitted = await request('/v1/feedback', {
    method: 'POST', token, body: Buffer.from(JSON.stringify({ consent: true, report })),
  });
  const reportId = submitted.json?.data?.id;
  assert(submitted.status === 201 && reportId, `Feedback submission failed (HTTP ${submitted.status}).`);
  const ownReport = await request(`/v1/feedback/${reportId}`, { token });
  assert(ownReport.status === 200, `Owner report lookup returned HTTP ${ownReport.status}.`);

  const other = await registerDeveloper('other');
  const crossUserRead = await request(`/v1/feedback/${reportId}`, { token: other.token });
  const developerTriage = await request('/v1/moderation/feedback', { token });
  const invalidJwt = await request('/v1/feedback', { token: 'not-a-valid-jwt' });
  assert(crossUserRead.status === 404, `Cross-user report lookup returned HTTP ${crossUserRead.status}.`);
  assert(developerTriage.status === 403, `Developer triage access returned HTTP ${developerTriage.status}.`);
  assert(invalidJwt.status === 401, `Invalid JWT returned HTTP ${invalidJwt.status}.`);

  const archive = fs.readFileSync(path.join(repoRoot, 'sample-mods', 'community', 'low-battery-alert.doommod'));
  const boundary = `doom-${crypto.randomUUID()}`;
  const quote = String.fromCharCode(34);
  const start = Buffer.from(`--${boundary}\r\nContent-Disposition: form-data; name=${quote}package${quote}; filename=${quote}low-battery-alert.doommod${quote}\r\nContent-Type: application/octet-stream\r\n\r\n`);
  const end = Buffer.from(`\r\n--${boundary}--\r\n`);
  const uploadBody = Buffer.concat([start, archive, end]);
  const upload = await request('/v1/submissions', {
    method: 'POST', token, body: uploadBody, contentType: `multipart/form-data; boundary=${boundary}`,
  });
  const submission = upload.json?.data;
  assert(upload.status === 201 && submission?.status === 'UPLOADED', `Package upload failed (HTTP ${upload.status}).`);
  const submissionDetails = await request(`/v1/submissions/${submission.id}`, { token });
  const reviewerQueue = await request('/v1/moderation/submissions', { token });
  const publicDetails = await request(`/v1/catalog/mods/${encodeURIComponent(submission.modId)}`);
  assert(submissionDetails.status === 200, `Owner submission view returned HTTP ${submissionDetails.status}.`);
  assert(reviewerQueue.status === 403, `Developer moderator queue access returned HTTP ${reviewerQueue.status}.`);
  assert(publicDetails.status === 404, `Unreviewed package appeared in catalog (HTTP ${publicDetails.status}).`);

  console.log(JSON.stringify({
    tls: 'verified CA chain and localhost hostname; redirects are not followed',
    registration: 201, invalidCredentials: wrongPassword.status, login: login.status,
    expiresInSeconds: login.json.data.expiresIn, missingConsent: noConsent.status,
    feedbackSubmission: submitted.status, reportId, ownerRead: ownReport.status,
    crossUserRead: crossUserRead.status, developerTriage: developerTriage.status,
    invalidJwt: invalidJwt.status, packageUpload: upload.status,
    submissionId: submission.id, submissionStatus: submission.status,
    sha256: submission.sha256, ownerSubmissionRead: submissionDetails.status,
    developerReviewerQueue: reviewerQueue.status, unpublishedCatalogLookup: publicDetails.status,
  }));
}

main().catch(error => { console.error(error.message); process.exitCode = 1; }).finally(() => agent.destroy());
