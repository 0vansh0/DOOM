# Marketplace Security Verification — Milestone 13B.5E

**Date:** 2026-11-22 (Asia/Calcutta)  
**Scope:** Local development services and isolated integration database. No production services or catalog were used.

## Environment

- Compose services: API healthy on `127.0.0.1:8787`; PostgreSQL healthy on `127.0.0.1:5438`; SeaweedFS S3 service (`minio` service name) running on `127.0.0.1:9000`; storage initializer exited 0 after initializing the private buckets.
- `GET /health`: HTTP 200, `{"status":"ok"}`.
- `GET /ready`: HTTP 200, `{"status":"ready"}`. This endpoint checks PostgreSQL, required migrations, both buckets, and bounded dependency checks.
- The service readiness response contains no connection strings or credentials.
- Migrations `0001_initial.sql`, `0002_feedback.sql`, and `0003_feedback_triage.sql` were applied to the newly created isolated database `doom_marketplace_13b5e_20261122_final`. The existing `doom_marketplace` database and Docker volumes were not reset or removed.
- Actual S3-backed integration exercised quarantine upload/read, publication copy, published package read, and checksum comparison using private SeaweedFS buckets.

## Security and lifecycle results

`npm test -- --reporter=dot` in `marketplace-api` was run with database and S3 integration enabled, the isolated database above, and the local Battery Glow 1.0/2.0 sample packages. Final result: **6 test files passed; 30 tests passed; 0 failed; 0 skipped**. The final rerun includes API-level malformed-upload persistence and duplicate-version publication assertions.

Verified paths include unauthenticated upload rejection; report and submission ownership isolation; unapproved catalog/download denial; package validation; role-escalation rejection; self-review denial; reviewer approval; rejection with persisted reason; immutable publication; exact byte and SHA-256 agreement between upload and download; fresh review for updates; admin quarantine and public download/detail denial; and concurrent moderation decisions returning one success and one conflict.

The API-level malformed archive test receives HTTP 422, records `VALIDATION_FAILED`, and verifies no quarantine object reference is persisted. The update integration test also submits a different valid package with an already-published `(mod ID, version)` and verifies publication returns HTTP 409 `VERSION_EXISTS`; no existing release is overwritten. The owner+SHA duplicate submission protection remains enforced by the server route.

The shared validator also rejected a malformed ZIP and an archive over the compressed-size limit. No package payload was executed by the backend.

## Limitations

- This is host-side testing against a local HTTP API and private local object store. It is not a physical-device HTTPS/catalog/import/runtime test.
- The Android marketplace Discover-to-install/update workflow remains unverified in this run. ADB currently reports no connected devices, and the production app endpoint remains the reserved `https://marketplace.doom.invalid/` unless explicitly overridden for a debug build.
- The isolated test database is retained for evidence. Test-created object data was written only to the configured development buckets; no production objects were touched.

**Gate:** Backend marketplace security and moderation integration **PASS**. Overall marketplace/client integration **BLOCKED** pending trusted HTTPS device verification.
