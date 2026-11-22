# Deployment preparation guide

No deployment was performed. The staging Compose template expects an immutable reviewed API image, external PostgreSQL and S3-compatible HTTPS endpoints, separate app/migration database credentials, distinct staging buckets, and mounted secret files. Render and inspect it with `docker compose -f deployment/staging.compose.yaml config`; do not start it until an isolated staging environment and secrets have been approved.

Build the API image locally with `docker compose -f marketplace-api/compose.yaml build api`. Apply migrations only through the explicit migration command/profile after backup and review. API startup performs no schema writes. The migration runner supports `--check` and `--apply`, checksum drift detection, a database advisory lock, and blocks obvious DROP/TRUNCATE statements pending separate review. Non-local apply requires `DOOM_MIGRATIONS_APPROVED=true`.

Deployment still requires provider-specific image scanning/signing and provenance, TLS certificate issuance, ingress review, external secrets, monitoring, backup and isolated restore evidence, plus a passing release gate. This guide does not authorize production deployment.
