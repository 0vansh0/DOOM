# Marketplace Local Development

## Prerequisites

Node.js 22.15+ (the service dependencies were checked with Node 24), npm, and Docker Engine with Compose. The Android SDK is only needed for the separate Android regression build.

## Start local services

From PowerShell at the repository root:

```powershell
npm.cmd --prefix doom-cli ci
npm.cmd --prefix doom-cli run build
npm.cmd --prefix marketplace-api ci
docker compose -f marketplace-api/compose.yaml up --build -d
```

Compose starts PostgreSQL 16 and the pinned SeaweedFS S3-compatible service, creates private `doom-quarantine` and `doom-published` buckets, applies the SQL migration, then starts the API on `http://127.0.0.1:8787`. The object-store S3 API is local on port 9000; Compose uses local-only credentials, so do not expose these services to an untrusted network.

Create an administrator with environment-provided credentials (never commit them):

```powershell
$env:ADMIN_SEED_EMAIL = 'admin@localhost.test'
$env:ADMIN_SEED_PASSWORD = 'a-long-local-only-password'
docker compose -f marketplace-api/compose.yaml exec -e ADMIN_SEED_EMAIL=$env:ADMIN_SEED_EMAIL -e ADMIN_SEED_PASSWORD=$env:ADMIN_SEED_PASSWORD api npm run seed:admin
```

`npm run db:migrate` is idempotent. SQL migration files in `marketplace-api/migrations` are the migration source of truth. `npm run dev` runs the local API without Compose after setting variables from `.env.example`.

## Verify

```powershell
npm.cmd --prefix marketplace-api run typecheck
npm.cmd --prefix marketplace-api test
Invoke-RestMethod http://127.0.0.1:8787/healthz
```

Integration tests are enabled with `$env:MARKETPLACE_INTEGRATION='1'` after Compose is running. Add `$env:MARKETPLACE_S3_INTEGRATION='1'` to run the artifact path against MinIO rather than the in-memory test adapter. The suite deletes test tables before use: point it only at a disposable database. Stop local services with `docker compose -f marketplace-api/compose.yaml down`; use `-v` only when you intend to delete local development database and object-store volumes.

## Submitting a test package

Create and package with `doom-cli`, register a developer, and upload the generated archive as multipart field `package` to `POST /v1/submissions`. The authenticated owner calls `POST /v1/submissions/{id}/validate`; a separately provisioned reviewer approves it; then the owner calls `POST /v1/submissions/{id}/publish`. Public listing and version download metadata are available under `/v1/catalog`. The HTTP integration test verifies the returned checksum against the original archive and passes the downloaded bytes through DOOM's shared importer validator; enable `MARKETPLACE_S3_INTEGRATION=1` to exercise MinIO-backed object storage.
