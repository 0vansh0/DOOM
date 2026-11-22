# DOOM Milestone 12A Security Audit

**Date:** 2026-11-21  
**Scope:** Android modules, importer/runtime, capability and Shizuku boundaries, privileged transaction coordinator, marketplace API, developer portal, Visual Builder, CLI, dependency locks and available release configuration.

## Repository and tools

Gradle modules: `app`, `feature-home`, `feature-discover`, `feature-installed`, `feature-mod-details`, `feature-settings`, `core-model`, `core-domain`, `core-data`, `core-database`, `core-designsystem`, `mod-engine`, `manifest-schema`, `doom-cli`, `sample-mods`, and `benchmark`. Additional applications: `marketplace-api`, `developer-portal`, `visual-builder`.

Reviewed Android manifest, network configuration, archive reader, capability/runtime enforcement, Shizuku state policy, privileged transaction coordinator, backend authentication/routes/storage, portal token handling, CLI and visual-builder project/test configuration.

Commands executed include:

```text
.\gradlew.bat :mod-engine:test --rerun-tasks
.\gradlew.bat :app:assembleDebug
.\gradlew.bat test
npm test                         # doom-cli, visual-builder, developer-portal, marketplace-api
npm run typecheck                # developer-portal
npm audit --audit-level=moderate # individual npm workspaces
adb devices -l                   # using the Android SDK adb binary
```

The Gradle module test command passed. Test result XML reports 70 mod-engine tests with zero failures, errors or skips. The root Gradle `test` task passed. Debug APK assembly passed. The release variant is not signed by this repository.

Before dependency remediation, the CLI audit found one Ajv moderate advisory and Visual Builder audit found six dependency advisories, including Critical-rated Vitest/tinypool advisories. Ajv was pinned to 8.20.0 and Visual Builder Vitest to 5.1.1. Post-fix `npm audit --audit-level=moderate` reported zero vulnerabilities for all four npm projects (`doom-cli`, `visual-builder`, `developer-portal`, `marketplace-api`).

The backend's default `npm test` completed with 10 passing tests and 8 skipped integration tests. Docker became available for the follow-up run. A fresh isolated database `doom_m12a_security_20261121_1743` was created, migration `0001_initial.sql` was applied, and the suite was run with integration and S3 flags enabled against the Compose SeaweedFS object store: **18 tests passed, 0 failed, 0 skipped**. The suite exercised authenticated upload, validation, approval, immutable publication, exact package byte/SHA-256 consistency, role and ownership denials, concurrent moderation decisions, and quarantine. After quarantine, both the package endpoint and public mod details returned 404. The initial run had one expectation failure because the test expected 200 for public details after the mod's only release was quarantined; the endpoint correctly returned 404, so the assertion was corrected to codify the stricter behavior and the complete suite then passed.

The Compose API migration was also run idempotently against the existing development database without deleting data. Its dependency health endpoint returned 200. PostgreSQL has a Compose healthcheck and reported healthy. SeaweedFS and API have no Compose-level healthcheck; SeaweedFS readiness was verified by the successful bucket initialization put/head/delete check, while API `/healthz` checks PostgreSQL and both S3 buckets. The storage-init container is a one-shot job and exited 0 as expected.

## Verified controls

- `ArchiveReader` limits compressed bytes to 10 MiB, entries to 50, total expanded bytes to 25 MiB, JSON sizes and per-asset size; it checks normalized allowlisted asset names, duplicate names, regular-file mode, ZIP structure, CRC and exact decompression lengths. The Kotlin module suite passed, including its existing archive/security cases.
- Runtime code checks mod active eligibility, safe mode, capability grants and registered effects before dispatch. Workflow limits and cancellation are covered by the JVM suite.
- Imported definitions remain declarative; this audit found no standard mod path to execute DEX, JAR, SO, JavaScript or shell commands.
- Shizuku is isolated behind its adapter and imported definitions do not invoke Binder APIs. This was source-reviewed and covered only by existing JVM fakes in this run.
- Android app backup is disabled, cleartext traffic is disallowed by Android defaults, the debug test CA/network configuration is debug-scoped, and the overlay service is non-exported. The exported launcher is the expected entry point; the Shizuku provider is protected by the platform signature permission.
- Portal bearer tokens are held in React memory rather than persistent browser storage. Untrusted marketplace descriptions and package JSON render as text; no unsafe HTML injection was found in the portal.
- Backend auth resolves current roles from the database, registration schema does not accept role fields, and protected moderation endpoints use role checks.

## Confirmed findings and fixes

See [FINDINGS.md](FINDINGS.md). DOOM-SEC-001 dependency updates were applied. DOOM-SEC-002 moderation race was fixed with a conditional transition and a regression test, but the DB test could not run without Docker.

## Device and release status

ADB found a connected Nothing device (`A001`, product `GalagaIND`), Android 16 / API 36. The existing debug APK was installed as an in-place update (`adb install -r`) and launched; a process was present and `AndroidRuntime:E` returned no crash output. This is a limited smoke check only. No further device security tests were performed: the test runner and app scenarios can alter installed mods/settings, and the safe read-only check set was not fully isolated. Battery Glow, permission revocation, Shizuku disconnect, invalid package behavior, recovery visibility and marketplace HTTPS/package flow remain unverified here.

No CI workflow was found in the repository. Secret-scanner, container-scanner, Android dependency vulnerability scanner and SAST executables were not installed; `npm audit` was available. No secret values were printed. Absence of findings from source searches is not a substitute for a dedicated secret scan.

**Beta gate: BLOCKED.** See [BETA_SECURITY_GATE.md](BETA_SECURITY_GATE.md).

## Follow-up verification attempt — 2026-11-21

This follow-up was run for the marketplace backend integration request. The current execution environment did **not** have the Docker Desktop Linux engine available, despite the earlier successful Compose run documented above:

| Check | Outcome | Evidence |
|---|---|---|
| `docker compose ... ps -a` | **BLOCKED** | Docker client could not connect to `npipe:////./pipe/dockerDesktopLinuxEngine`. |
| API health / DB connectivity | **BLOCKED** | `http://127.0.0.1:8787/healthz` returned connection refused; no live API/database check was possible. |
| SeaweedFS/S3 bucket and object probe | **BLOCKED** | Container engine unavailable, so no container-network S3 request could run. |
| Migration rerun / database integration rerun | **BLOCKED** | No reachable Postgres service in this session. The previously recorded successful isolated-database migration and 18/18 API + S3 integration run remain valid historical evidence, but were not repeated here. |
| Default backend `npm test` | **PASS / SKIPPED** | Build passed; 10 tests passed, 8 database integration tests skipped because `MARKETPLACE_INTEGRATION` was not enabled for the unavailable service. |
| `:mod-engine:test --rerun-tasks` | **PASS** | Gradle reported `BUILD SUCCESSFUL`; 70 mod-engine test cases remain the current XML result with zero failures/errors/skips. |
| `:app:assembleDebug` | **PASS** | Gradle reported `BUILD SUCCESSFUL` (209 tasks up-to-date). |

For this follow-up, developer login/ownership, package upload/validation/checksum, moderation decisions, public catalog and quarantine behavior are **BLOCKED** because the API and database were unreachable. No Docker volumes were removed and no production permissions or validation controls were changed.
