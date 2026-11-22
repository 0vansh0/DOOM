# Dependency Management

## Current controls

- Android dependencies use Gradle version catalogs and dependency locking/configuration in the repository; Gradle wrapper is checked in.
- `doom-cli`, `visual-builder`, `marketplace-api`, and `developer-portal` have checked-in npm lockfiles and use `npm ci` in their CI workflows.
- `.github/workflows/security.yml` runs `npm audit --audit-level=high` on pull requests/manual dispatch. The local `tools/maintenance/run-regression.ps1` runs audits and suites serially.
- `marketplace-api/compose.yaml` pins PostgreSQL/SeaweedFS/CLI images; the S3-compatible SeaweedFS and AWS CLI image references use digests. Image scanning is not demonstrated by this checkout.

## Update review

For a proposed dependency change, inspect the advisory and package provenance, compare the lockfile diff, assess license and platform support, and run the directly affected tests plus complete relevant regressions. Avoid broad major-version updates. Keep lockfiles synchronized and reject unexpected install scripts. For container changes, rebuild and scan with an approved image scanner before staging.

No recurring Dependabot or external update job is configured here. CI audit is event-driven; maintainers may run `npm audit` manually. Do not automatically deploy dependency updates.
