# Marketplace Integration Results — Milestone 13B.5A

## Host HTTPS package workflow — PASS

Executed by `marketplace-api/scripts/13b5a-isolated-e2e.mjs` against the dedicated local API and isolated PostgreSQL database:

| Stage | Observed result |
|---|---|
| Developer registration and authenticated package upload | HTTP 201 |
| Initial package state | `UPLOADED` in quarantine |
| Before review, public mod lookup | HTTP 404 |
| Owner validation | HTTP 200, `PENDING_REVIEW` |
| Author attempting self-review | HTTP 403 |
| Independent reviewer approval | HTTP 200, `APPROVED` |
| Owner publication into local test catalog | HTTP 201, `PUBLISHED` |
| Published catalog lookup | HTTP 200 |
| Published artifact download | HTTP 200 |
| SHA-256 | Download bytes matched both publication metadata and `x-doom-sha256`: `856d25665a0594f0659c6d064b85821ef471048e6d5f2a727be6069b16637f10` |

The publication was confined to a local isolated development database/object-storage namespace; no public service was used.

## Android result — PARTIAL / BLOCKED

The A001 instrumentation test verified Android HTTPS auth and feedback repositories. It did **not** browse the Android catalog, download/import the package, authorize capabilities, execute the mod, or validate an update/settings migration. Those device marketplace checks remain BLOCKED. Do not interpret the successful host package lifecycle as Android importer/runtime verification.

## Automated tests

- Marketplace API default: 17 passed, 11 opt-in integration tests skipped.
- Marketplace API with isolated PostgreSQL and SeaweedFS: 28 passed, 0 skipped. The update fixture is version 1.0.1; the test seeds or confirms version 1.0.0 before requiring new validation and review. Admin quarantine then hides the published test release.
- CLI: 60 passed.
- Visual Builder: 11 passed.
- Developer Portal: 6 passed; production TypeScript/Vite build passed.
- Android mod-engine: 74 passed; app unit tests: 4 passed; debug APK build passed.

## Update verification

The host backend integration now verifies a second version through fresh validation, review and publication after confirming the prior release. Android update detection, install, settings preservation and failed-update recovery remain **BLOCKED**.
