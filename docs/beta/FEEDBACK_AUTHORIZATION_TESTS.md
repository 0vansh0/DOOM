# Feedback authorization tests

Backend authorization is enforced in Fastify pre-handlers, then report ownership is bound to `request.principal.id` from the verified JWT. Client-supplied owner identifiers are not accepted by the feedback schema.

| Check | Expected result | Evidence status |
|---|---|---|
| Guest submits or reads feedback | 401 | Present in `marketplace-api/test/marketplace.integration.test.ts`; database integration not run |
| Missing consent or extra report fields | 400 | Present in integration test; database integration not run |
| Owner lists/reads their report | 200 | Present in integration test; database integration not run |
| Different ordinary account reads owner report | 404 | Present in integration test; database integration not run |
| Developer opens reviewer list/detail or changes triage | 403 | Present in integration test; database integration not run |
| Reviewer opens queue/detail and updates triage | 200 | Present in integration test; database integration not run |
| Admin opens review detail and updates triage | 200 by shared REVIEWER/ADMIN guard | Present in integration test; database integration not run |
| Invalid JWT / expired JWT | 401 | Present in integration test; database integration not run |
| Invalid status transition | 409; no update/audit write | Present in integration test; database integration not run |
| Feedback per-IP rate limit | 429 after configured limit | Present in integration test; database integration not run |
| Triage audit access | Reviewer/admin only; only sanitized triage details are returned | Route uses `requireRoles`; integration not run |

The portal hides triage navigation from DEVELOPER accounts, but this is presentation only; backend authorization remains authoritative. The feedback API rate limit is 10 submissions per IP per minute. Login and JWT authentication remain governed by the existing API middleware.

Database integration execution is BLOCKED in this workspace: Docker Desktop's Linux engine is unavailable, and the integration suite deletes/reseeds its configured tables. It must only be run with an explicitly isolated disposable test database, never the developer or production database. The test suite itself was run; it reports 11 passing non-database tests and 11 skipped database integration tests. Portal tests passed (6 tests), and Android's feedback repository/ViewModel tests passed (7 + 4 tests). These deterministic tests do not replace execution of the backend authorization assertions against PostgreSQL.
