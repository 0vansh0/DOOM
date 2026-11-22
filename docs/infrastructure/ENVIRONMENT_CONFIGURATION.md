# Environment configuration

The API distinguishes `APP_ENVIRONMENT=local|staging|production`. Local defaults and the Compose credentials are development-only. Staging and production require `NODE_ENV=production`, a unique JWT secret of at least 64 characters, PostgreSQL `verify-full`, explicit trusted proxy IPs/CIDRs, non-local HTTPS CORS origins and object storage, distinct non-local buckets, and bounded token lifetime. Production also requires an internal metrics token. Bootstrap administrator credentials are rejected by the application process.

Secrets may be supplied by the existing variable or its `_FILE` counterpart (`DATABASE_URL_FILE`, `MIGRATION_DATABASE_URL_FILE`, `JWT_SECRET_FILE`, S3 key files, and `METRICS_TOKEN_FILE`). Supplying both forms is rejected. Put secret files in a platform secret manager or a protected local path; never commit them. `.env.*.example` files contain names and examples only.

Android release marketplace configuration remains the reserved `https://marketplace.doom.invalid/` until an approved production HTTPS origin is supplied. Debug endpoint configuration does not flow into release builds.
