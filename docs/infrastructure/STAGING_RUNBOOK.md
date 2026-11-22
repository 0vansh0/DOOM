# Staging runbook

1. Obtain isolated staging PostgreSQL and S3-compatible HTTPS services and create separate quarantine/published buckets.
2. Store app DB, migration DB, JWT, metrics and S3 credentials as protected files. Configure the `DOOM_STAGING_*_FILE` paths; never use local Compose credentials.
3. Set an immutable reviewed image reference, exact portal HTTPS origin, S3 endpoint/region, staging-only bucket names and trusted proxy IP/CIDRs.
4. Render with `docker compose -f deployment/staging.compose.yaml config` and review the result for leaked values and unintended ports.
5. Take and verify a backup. Run the migration profile only with the separate migration role and explicit `DOOM_STAGING_MIGRATIONS_APPROVED=true` after review.
6. Start the API in the isolated network; verify `/health`, `/ready`, private metrics access, authentication, moderation, quarantine denial and package checksum behavior.
7. Record image digest, migration result and test evidence. Tear down only approved staging resources; do not remove data volumes without a separate retention decision.

This runbook is not an execution record. Staging was not deployed in Milestone 13C.1.
