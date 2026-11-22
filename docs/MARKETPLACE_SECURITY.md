# Marketplace Security

## Package boundary

Uploads go to a private quarantine bucket using generated UUID keys. Client filenames are only sanitized for display. The API buffers no more than the configured 10 MiB upload limit, calculates SHA-256, then invokes the shared DOOM CLI archive/schema validator inside a bounded worker. Supported archive paths, duplicate entries, ZIP traversal, expanded-size limits, approved assets, schema versions, capabilities and non-executable declarative content are checked by that validator. No imported code is loaded or executed.

Worker concurrency defaults to two, heap is capped at 96 MiB, and validation times out after 15 seconds. Persistent PostgreSQL quota counters default to 20 upload attempts and 100 MiB per authenticated developer per UTC day; a Fastify per-IP limiter is an additional burst control. Per-request multipart limits and API body size are bounded. Unknown capability/action declarations are rejected by the canonical validator.

## Authorization

Passwords use Argon2id. JWT bearer tokens are short-lived and contain the subject, not a client-controlled role. The current account and role are fetched from PostgreSQL on each request, so disabling an account or changing a role takes effect immediately. Registration has a strict allowlist and never accepts role/profile ownership fields. Mod IDs are bound to the first publishing developer; version uniqueness is enforced in PostgreSQL. Reviewer endpoints are role checked on the server, and self-review is forbidden.

Public catalog and download metadata only include published, non-quarantined versions. Package objects are private. Download metadata contains a relative same-origin route and SHA-256; the API resolves the immutable storage key internally, and callers cannot supply an object key or redirect host. Upload names and authorization headers are redacted from logs. Audit details contain action identifiers and hashes, not passwords, bearer tokens, or raw package contents.

## Deployment requirements and limits

- Change all local-only passwords/secrets and restrict `ALLOWED_ORIGINS` before exposure.
- Use TLS at the edge, private buckets, least-privilege S3 credentials, encrypted storage and managed secret storage in deployment.
- Restrict PostgreSQL and object-store ports to trusted networks.
- Run migration and API identities with least privilege in production.
- Add malware scanning, human moderation staffing, abuse-response operations, backups, retention policy, telemetry and object-orphan reconciliation before public launch.
- The included Docker Compose credentials are local-development values, not production secrets.
- This milestone does not implement email verification, account recovery, refresh-token rotation, publisher verification, marketplace UI, CDN delivery or a public deployment.
