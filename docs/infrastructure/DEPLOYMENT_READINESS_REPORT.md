# DOOM Deployment Readiness Report

**Inspection date:** 2026-11-23  
**Scope:** Local development stack and checked-in staging/deployment templates. No services were deployed, no DNS or credentials were changed, and no release was published.

## Decision

**Staging deployment: BLOCKED.** The staging Compose template is not configurable yet: required image, origin, proxy, storage and secret-file settings are absent. Its config validation fails on the missing variables listed below.

**Production deployment: BLOCKED.** There is no production Compose/deployment manifest, production domain, approved HTTPS endpoint, configured production credentials, verified production storage policy, backup schedule/restore evidence, or signed release APK.

The local API stack is healthy. That is evidence for local development only. It does not make this stack or the unsigned APK suitable for Internet exposure or distribution.

## Evidence gathered

| Check | Result | Evidence |
|---|---|---|
| Local Compose services | PASS | `docker compose -f marketplace-api/compose.yaml ps -a`: API healthy; PostgreSQL healthy; `minio` service (SeaweedFS image) running; storage initialization exited 0. |
| API liveness | PASS | `GET http://127.0.0.1:8787/health` returned HTTP 200 `{"status":"ok"}`. |
| API readiness | PASS | `GET http://127.0.0.1:8787/ready` returned HTTP 200 `{"status":"ready"}`. |
| PostgreSQL | PASS (local connection) | Compose health was healthy and `pg_isready` reported accepting connections. Existing `marketplace-api_pgdata` volume was present. |
| Local S3-compatible storage | PASS (local smoke) | SeaweedFS was running; storage-init logged successful bucket readiness and its configured put/head/delete smoke check. Existing `marketplace-api_seaweedfsdata` volume was present. |
| Staging Compose configuration | BLOCKED | `docker compose -f deployment/staging.compose.yaml config --quiet` fails because required `DOOM_STAGING_*` values are not configured. |
| Migration currency | BLOCKED | The local database migration table contains 0001–0003. Migration 0004 (`0004_feedback_assignee.sql`) is not recorded as applied. No migration was applied in this audit. |
| Host migration check | BLOCKED | `npm run db:migrate:check` could not load the app config because the host process lacks required service environment. Running the built container command did not produce a conclusive migration summary. The direct migration-table query is the evidence above. |
| Git source revision | UNAVAILABLE | Git reported that this working directory is not a repository; no source commit could be associated with an artifact. |

The local DB migration table was inspected read-only with:

```powershell
docker compose -f marketplace-api/compose.yaml exec -T postgres psql -U doom -d doom_marketplace -Atc "SELECT name FROM schema_migrations ORDER BY name"
```

The environment-specific migration gate is intentional: non-local migration application requires explicit approval after a backup and release review. Do not set that approval in staging or production until the backup and migration plan are accepted.

## Deployment files and readiness

| File | Assessment |
|---|---|
| `marketplace-api/Dockerfile` | Buildable multi-stage API image; final process runs as the non-root Node user. Build context is repository root. Base image uses a version tag, not an immutable digest; pin and review the digest before relying on reproducible release builds. |
| `deployment/staging.compose.yaml` | Hardened staging template: read-only root filesystem, dropped capabilities, no-new-privileges, resource limits, secret-file mounts, TLS-required DB/storage configuration, `/ready` healthcheck, and a separately gated migration service. Not runnable until required variables and secret files are provisioned. |
| `deployment/nginx/marketplace.conf.template` | HTTPS reverse-proxy template with HTTP redirect, TLS 1.2/1.3, request/body limits, security headers, rate limit, and a loopback API upstream. It still has host/certificate placeholders and requires an exact trusted proxy CIDR plan. Nginx was not installed or config-tested in this environment. |
| `marketplace-api/.env.staging.example`, `marketplace-api/.env.production.example` | Safe variable-name/configuration examples; not runtime configuration and contain no provider-specific deployment values. |
| `marketplace-api/migrations/0001_initial.sql` through `0004_feedback_assignee.sql` | Migration source is present. Local DB is at 0003; 0004 remains pending. |
| `marketplace-api/scripts/ops/backup-postgres.sh` | Creates a custom-format `pg_dump`, checks archive listing and writes a SHA-256 sidecar. Requires protected PGPASSFILE and a protected backup directory. Script exists; no backup was made or restore tested. Encryption, off-site copy, schedule, retention and restore drill are not provided by this script. |
| `marketplace-api/scripts/ops/restore-postgres-test.sh` | Requires a restore-test database name and checksum sidecar; restores transactionally without `--clean`/`--create`. Script exists; no restore was run. |
| `developer-portal/` | Vite production build is available through its npm scripts, but there is no portal container/deployment manifest in the inspected deployment directory. Hosting, HTTPS origin, auth callback/API origin and access policy still need provider configuration. |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | Present (22,720,440 bytes, timestamp 2026-11-22). It is explicitly unsigned and must not be distributed. No release signing identity is configured. |

Existing workflow files cover Android, CLI, builder, marketplace, portal, security and infrastructure validation. They do not, by themselves, prove a staging deployment or provide configured deployment credentials. No production deployment workflow was found in the inspected `deployment/` directory.

## Missing staging configuration

No `DOOM_*` deployment variables are configured. The staging template requires these names (values intentionally not shown):

- Image/proxy/origin: `DOOM_MARKETPLACE_API_IMAGE`, `DOOM_STAGING_TRUSTED_PROXY_ADDRESSES`, `DOOM_STAGING_PORTAL_ORIGIN`.
- S3 endpoint/buckets: `DOOM_STAGING_S3_ENDPOINT`, `DOOM_STAGING_S3_REGION`, `DOOM_STAGING_QUARANTINE_BUCKET`, `DOOM_STAGING_PUBLISHED_BUCKET`.
- Secret-file paths: `DOOM_STAGING_DATABASE_URL_FILE`, `DOOM_STAGING_MIGRATION_DATABASE_URL_FILE`, `DOOM_STAGING_JWT_SECRET_FILE`, `DOOM_STAGING_METRICS_TOKEN_FILE`, `DOOM_STAGING_S3_ACCESS_KEY_ID_FILE`, `DOOM_STAGING_S3_SECRET_ACCESS_KEY_FILE`.
- Optional/defaulted: `DOOM_STAGING_MIGRATIONS_APPROVED` (must remain false until approved migration execution), `DOOM_STAGING_S3_FORCE_PATH_STYLE`.

The runtime secret files must contain separate application and migration PostgreSQL URLs, a unique high-entropy JWT secret, an internal metrics token, and scoped S3 credentials. Secret contents were not read or recorded. The staging config additionally needs normal service settings from the example: `APP_ENVIRONMENT=staging`, `NODE_ENV=production`, `DATABASE_SSL_MODE=verify-full`, a non-local HTTPS portal origin, HTTPS S3 endpoint, staging-only bucket names, and explicit trusted proxy addresses. Do not reuse local development credentials or bucket names.

Production has the same classes of runtime settings plus production-only metrics configuration, but the repository currently provides no production Compose manifest or approved production-specific variable set. Define those only after provider and domain selection.

## Persistence, migrations and backups

- Local Postgres and SeaweedFS data are persisted in named Docker volumes. They were not removed. A historical `miniodata` volume declaration remains in Compose to avoid orphaning older local data.
- Staging Compose points to external PostgreSQL and S3-compatible services; persistence and backup guarantees therefore depend on the selected providers and their configured retention plans.
- Migrations are numbered, checksum-aware, and protected against recognized destructive SQL. Non-local application is explicitly approval-gated and uses a separate migration DB URL/role.
- The local schema is behind source by migration 0004. Resolve it only through the reviewed migration process, after a verified backup; do not use a destructive reset.
- Backup/restore scripts are present, but no automated schedule, encrypted off-site retention, object-storage backup/retention policy, or successful isolated restore evidence was verified. These are staging acceptance blockers.

## Storage policy and package quarantine

Local `storage-init` confirms the `doom-quarantine` and `doom-published` buckets exist and runs a small write/read/delete check. Local SeaweedFS is configured with its IAM authorization disabled and is bound to loopback on the host. These local credentials and bucket settings are development-only.

The API code keeps quarantine reads behind reviewer routes and exposes catalog downloads only when a release has `publishedAt` set and `quarantinedAt` is null. Publication copies the validated artifact to the published bucket and the API streams downloads from there. This is useful application-level enforcement, but it is not a cloud bucket policy. No production/staging S3 IAM policy or public-access-block configuration is present in the repository. Before staging, provision private buckets, block public ACL/policy access, scope credentials to the minimum required actions and bucket prefixes, and test direct unauthenticated object access is denied. Keep public distribution behind the catalog API, not a public bucket URL.

## HTTPS, reverse proxy and domain blockers

No marketplace hostname or HTTPS certificate has been selected. The Nginx template still needs an approved hostname and provisioned certificate paths; its upstream assumes the API is on the same host at `127.0.0.1:8788`. The deployment must use a trusted HTTPS origin for the API and package downloads, and the exact ingress proxy IP/CIDRs must be supplied to the backend. Never set a trust-all proxy range. CORS must contain only the exact portal origin. Keep the API port private behind the HTTPS proxy/platform gateway.

The Android release marketplace endpoint is not configured as an approved production endpoint. Until one exists, remote marketplace use in the distributable release remains blocked; do not substitute localhost or HTTP.

## Recommended small private-beta architecture

For the lowest operational burden, use a managed container web service for the Fastify API, that provider's managed PostgreSQL in the same region/private network, and a private S3-compatible object store such as Cloudflare R2 for quarantine and published objects. Host the Developer Portal as a static HTTPS site on the same platform or another managed static host. Use the provider's managed TLS/custom-domain routing rather than maintaining Nginx if its ingress and forwarding-header behavior can be securely represented in `TRUSTED_PROXY_ADDRESSES`. If not, select a host with stable documented proxy ranges or place a controlled reverse proxy with fixed ingress addresses in front. Do not accept arbitrary forwarded headers.

Suggested initial shape:

1. One immutable-image Fastify service, reachable only through provider HTTPS ingress; use the existing `/health` and `/ready` checks.
2. One managed PostgreSQL database with TLS verification, application role and separate migration role, private connection, automated backups/PITR appropriate to the selected plan.
3. Separate private quarantine and published buckets (or separately isolated prefixes with equivalent IAM policy), with no public bucket access and least-privilege service credentials.
4. Static portal hosting with HTTPS and exact CORS origin; backend remains authoritative for developer/reviewer roles.
5. Provider secret manager for credentials; no secret values in Compose files, source control, build args, browser bundles or shell history.
6. A monitored backup schedule and a documented isolated restore drill before inviting any tester.

### Indicative monthly service cost

Current public list pricing checked 2026-11-23 (USD, before taxes, domain, overages and optional workspace/team plan):

- Render web service at 1 CPU / 2 GB: about **$25/month**; this matches the staging template's CPU limit and exceeds its 768 MB memory limit.
- Render managed PostgreSQL at less than 1 CPU / 1 GB: about **$19/month**, with storage charged separately at the current listed rate.
- Cloudflare R2 Standard: first 10 GB-month, 1 million Class A operations and 10 million Class B operations included; beyond that, listed standard storage is $0.015/GB-month, Class A $4.50/million, Class B $0.36/million, and Internet egress is free.
- **Indicative total: ~$44/month** at the stated Render sizes while R2 stays within included usage. Add Render's optional Pro workspace ($25/month flat) if team/workspace features are needed, plus domain, storage overage, backups outside included database retention, and network usage. A lower-cost $7 web instance / $6 database combination exists but is below the current staging resource profile and is not the recommended starting point.

Prices change; confirm provider checkout and included backup/retention terms before choosing. Sources: [Render pricing](https://render.com/pricing), [Render Postgres documentation](https://render.com/docs/postgresql), [Cloudflare R2 pricing](https://developers.cloudflare.com/r2/pricing/).

## Staging deployment checklist

- [ ] Select provider, region, staging hostname, portal hostname, and a supported HTTPS/custom-domain route.
- [ ] Confirm the provider's ingress proxy source addresses and configure only those in `TRUSTED_PROXY_ADDRESSES`.
- [ ] Provision staging-only managed PostgreSQL with TLS hostname verification; create separate least-privilege application/migration roles; keep it private.
- [ ] Provision staging-only private S3 buckets; configure no-public-access policy and least-privilege credentials; test direct object access is denied.
- [ ] Create secrets through the provider secret manager (or protected host-mounted files for Compose); do not write values in this report or commit them.
- [ ] Build the API image, scan it, pin the deployed image by immutable digest, and verify the source revision and provenance.
- [ ] Confirm staging and production are isolated in databases, buckets, secrets, portal origins, JWT keys and API origins.
- [ ] Review the pending migration 0004 against a verified backup; test on an isolated staging DB; approve only the one-off migration job.
- [ ] Run and inspect `db:migrate:check`; back up before applying; confirm resulting migration set is current.
- [ ] Validate Compose/provider configuration, start API, then verify `/health` and `/ready` over trusted HTTPS.
- [ ] Verify private package upload, quarantine access control, moderation, approved catalog download, checksum and quarantine denial using isolated accounts.
- [ ] Configure API and portal with exact HTTPS origins; verify Android points only to approved HTTPS API/package hosts.
- [ ] Configure database backups/PITR, object-storage recovery/retention and alerts; complete an isolated restore drill.
- [ ] Review logs/metrics for JWT, password, feedback-body and object credential redaction; verify monitoring alerts and owner access.
- [ ] Keep the unsigned APK out of distribution; complete signing and signature verification independently before any tester delivery.

## Commands after provider selection

These are templates, not commands executed here. Replace the example registry/reference and run only after provider approval, staging secrets are provisioned securely, and the migration backup/review gate is satisfied.

```powershell
# Build from the repository root. Tag is a temporary build label; deploy the immutable digest.
docker build -f marketplace-api/Dockerfile -t "REGISTRY/doom/marketplace-api:STAGING_TAG" .

# Registry login should prompt securely; do not pass a password/token as a command argument.
docker login REGISTRY
docker push "REGISTRY/doom/marketplace-api:STAGING_TAG"
docker buildx imagetools inspect "REGISTRY/doom/marketplace-api:STAGING_TAG"

# After setting the DOOM_STAGING_* values in the provider/deployment secret mechanism,
# validate the Compose file. Do not put secret contents in this shell.
docker compose -f deployment/staging.compose.yaml config --quiet

# Back up using a protected PGPASSFILE and encrypted/access-controlled BACKUP_DIR.
bash marketplace-api/scripts/ops/backup-postgres.sh

# Inspect migration state on the staging image/database before any apply.
docker compose -f deployment/staging.compose.yaml --profile migration run --rm --entrypoint node migrate dist/db/migrate.js --check

# Only after backup, review and explicit staging migration approval:
# set DOOM_STAGING_MIGRATIONS_APPROVED=true in the protected deployment environment.
docker compose -f deployment/staging.compose.yaml --profile migration run --rm migrate

# Start/check the API after migrations.
docker compose -f deployment/staging.compose.yaml up -d api
docker compose -f deployment/staging.compose.yaml ps -a
curl.exe --fail --show-error https://MARKETPLACE_HOST/health
curl.exe --fail --show-error https://MARKETPLACE_HOST/ready

# If self-hosting the supplied Nginx template, render approved host/certificate paths,
# validate before reload, and configure only explicit proxy CIDRs.
nginx -t
nginx -s reload

# Restore only into a newly created isolated restore-test database, never over staging/prod.
bash marketplace-api/scripts/ops/restore-postgres-test.sh
```

Provider-specific image push, managed-database provisioning, secret-manager writes, bucket IAM policy, domain/certificate setup, Android endpoint values, backup retention, and Nginx rendering cannot be made exact until a provider, account, hostnames and deployment topology are selected. No such commands were executed.

## Blockers

1. Provider, region, registry and deployment identity not selected.
2. Required staging `DOOM_*` variables and protected secret files are absent; staging Compose validation fails.
3. No staging/production domains, HTTPS certificate, portal origin or approved Android release API endpoint.
4. No provider proxy address plan matching the backend's explicit trusted-proxy requirement.
5. No immutable API image in a registry, provenance record, or deployment scan evidence.
6. Migration 0004 is pending locally; staging migration check/apply and backup precondition are unverified.
7. No verified automated database backup schedule, encrypted off-site retention, object-store backup plan or isolated restore test.
8. No staging/production S3 IAM/bucket policy proving private quarantine and published artifact access boundaries.
9. No production deployment manifest; the supplied staging Compose and Nginx files are templates only.
10. Developer Portal hosting and HTTPS origin are not configured.
11. Android APK is unsigned; signed artifact and update identity verification remain a distribution blocker.
12. Source revision is unavailable from this working directory, so artifact provenance cannot be tied to a commit.

**Overall: local development PASS; staging deployment BLOCKED; production deployment BLOCKED; distribution of the current APK BLOCKED.**
