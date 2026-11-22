# Milestone 13C.1 completion report

**Execution date:** 2026-11-22 (Asia/Calcutta)  
**Application:** `org.doom.app`  
**Workspace revision:** unavailable; this workspace has no `.git` metadata.

## Implemented preparation

- Backend environment configuration now separates local, staging and production; staging/production require verified PostgreSQL TLS, HTTPS origins and object storage, explicit trusted proxy IP/CIDRs, long unique JWT secret material, distinct buckets and bounded token lifetime. Secret values can be injected through `_FILE` paths and ambiguous inline+file configuration fails closed.
- PostgreSQL pool/query/connect/statement/lock timeouts are configurable. Runtime startup no longer runs migrations. Explicit migration check/apply scripts compare checksums, lock application, use a separate migration URL if configured, and require approval for non-local apply.
- API container now has separate build/runtime stages, Node 24.22.2 LTS version-tagged base images, pruned production dependencies, non-root execution and graceful signal handling. The staging template adds read-only filesystem, dropped capabilities, no-new-privileges, resource limits and health checks. Immutable image digests and vulnerability scans remain deployment tasks.
- TLS reverse-proxy template covers TLS 1.2/1.3, default-host rejection, HTTPS redirect, request limits/timeouts, security headers and explicit forwarding-header overwrite. Real DNS/certificates/upstream CIDRs are intentionally absent.
- Added protected-file backup and isolated restore scripts. Proposed RPO/RTO are planning values and have not been demonstrated by a restore drill.
- Added bounded in-process request/error/latency metrics behind a timing-safe bearer check and expanded log redaction. Metrics are ephemeral and no external monitoring service is connected.
- Android release configuration accepts only a provided HTTPS endpoint and retains the reserved `.invalid` endpoint until an approved domain is supplied. Portal API configuration no longer falls back to localhost in production and rejects cross-origin redirects.
- Added a pull-request/manual infrastructure validation workflow. No workflow deploys staging or production.

## Verification evidence

| Check | Result | Evidence |
|---|---|---|
| Backend typecheck/build and unit suite | **PASS** | `npm test` in `marketplace-api`: build succeeded; 8 test files, 21 passed, 13 skipped (integration-gated), 0 failed. |
| Portal tests | **PASS** | `npm test` in `developer-portal`: 3 files, 8 tests passed. |
| Portal production build | **PASS** | `npm run build` completed; Vite output generated. |
| CLI regression | **PASS** | `npm test` in `doom-cli`: 60 passed, 0 failed/skipped. |
| Android mod-engine | **PASS** | `gradlew.bat :mod-engine:test --rerun-tasks --no-daemon --max-workers=2`: 76 passed, 0 failures/errors/skips. |
| Android app unit tests | **PASS** | `gradlew.bat :app:testDebugUnitTest --no-daemon --max-workers=2`: 4 passed, 0 failures/errors/skips. |
| Android debug APK | **PASS** | `gradlew.bat :mod-engine:test --rerun-tasks :app:assembleDebug --no-daemon --max-workers=2`: `BUILD SUCCESSFUL`; artifact at `app/build/outputs/apk/debug/app-debug.apk` (28,256,997 bytes). |
| Local Compose syntax | **PASS** | `docker compose -f marketplace-api/compose.yaml config --quiet` returned 0. |
| Staging Compose syntax | **PASS** | `docker compose -f deployment/staging.compose.yaml config --quiet` returned 0 using temporary config-only placeholder secret files and reserved example hosts; not deployed. |
| Docker services/image build | **BLOCKED** | Docker CLI could not connect to `dockerDesktopLinuxEngine` named pipe. No containers were inspected or started, and Dockerfile image build/scanning did not run. |
| PostgreSQL/S3/API integration and migrations | **BLOCKED** | TCP checks to `127.0.0.1:5438`, `:9000`, and `:8787` all failed; no migrations or storage round trip were run. 13 backend tests were skipped by integration gating. |
| Nginx proxy validation | **BLOCKED** | Nginx is not available in the local environment; template awaits real host/certificate/network values. |
| Physical Android test | **BLOCKED** | Device/ADB availability was not established during this infrastructure task; no installation or network/device test was performed. |
| Production credentials/provider/staging deployment | **BLOCKED** | No provider, trusted endpoints, credentials, DNS, certificate or approval was supplied; no deployment attempted. |

The first Android build attempt exposed a Kotlin DSL `java.net.URI` resolution failure; adding the explicit `java.net.URI` import fixed it. Concurrent Gradle invocations also collided in shared Kotlin compiler caches; the tasks were rerun serially and passed.

## Readiness decisions

- **Offline infrastructure preparation:** **PASS** for configuration/template implementation and the checks above.
- **Production infrastructure preparation:** **BLOCKED** until provider-specific secrets/endpoints, image scanning/provenance, migrations, backup/restore and staging verification are completed. Milestone 13B.5E already records the marketplace and overall production-preparation gates as blocked.
- **Public deployment readiness:** **BLOCKED**. No public deployment, DNS change, production credential, production database change or publication was performed.
- **Stable Android distribution:** **BLOCKED** by the unresolved approved signing identity and production HTTPS endpoint in the existing release gate.
