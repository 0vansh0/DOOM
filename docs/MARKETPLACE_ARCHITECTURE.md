# DOOM Marketplace Backend Architecture

The local Marketplace API is a separate TypeScript service in `marketplace-api/`. It does not change the Android application ID or add a marketplace UI to the Android app.

```text
CLI / future clients
        |
   Fastify API ── JWT authentication / role policy / Zod validation
        |                         |
   PostgreSQL (Drizzle)       bounded worker_threads
        |                     doom-mod-cli/marketplace
   audit + lifecycle       ZIP + Manifest 3 + Definition 3/4 validation
        |
   private S3-compatible quarantine ── approved publication bucket
```

The package worker imports the shared `doom-mod-cli/marketplace` entry point, which reuses the canonical archive reader and `validateFiles` implementation. It never loads or executes package code. Workers have a 96 MiB old-generation heap, a 4 MiB stack, a bounded queue and a configurable timeout. API-side compressed size is capped at 10 MiB; the shared importer enforces its 25 MiB expanded, 50-entry, path, schema, capability and asset restrictions.

The service uses Fastify 5, TypeScript 5, Drizzle ORM 0.45, PostgreSQL 16, Zod 4, Argon2id, JWT bearer access tokens, and private S3-compatible storage. The SQL migration is authoritative; `drizzle-orm` provides typed queries. Do not use `drizzle push` against deployed data.

## Lifecycle

```text
DRAFT -> UPLOADED -> VALIDATING -> PENDING_REVIEW -> APPROVED -> PUBLISHED -> QUARANTINED
                                  |                 |
                                  v                 v
                           VALIDATION_FAILED      REJECTED
```

Validation failure and rejection can be resubmitted through a new upload. Published artifacts are content-addressed under generated keys and versions are unique and immutable. Quarantine hides a release from catalog and download responses. Moderation approval is distinct from publication; publication is an explicit developer request. Validation is a structural/security check and never certifies publisher trust.

PostgreSQL and object storage cannot share a transaction. Publishing copies to a generated immutable key before committing the version record. An interrupted copy/commit can leave an unreferenced object; it is not exposed by the catalog and is retained for an operator reconciliation/cleanup process. The quarantine source is retained if the database commit fails. Published bytes are streamed only through a same-origin API route after checking that the version remains published and not quarantined.

## Identity and trust

Registration always creates `DEVELOPER`. The API reads the current role from PostgreSQL on every authenticated request; role claims are not accepted from clients or trusted from stale JWT claims. Reviewers/admins must be provisioned out of band. Local admin seeding is an explicit command. Developers cannot review their own packages. Public routes return only published, non-quarantined releases.

No Shizuku, raw Binder operation, root capability or executable package format is accepted. Marketplace artifact validation accepts Manifest Schema 2 with Definition 1 or 2, and Manifest Schema 3 with Definition 3 or 4, matching the existing CLI validator. Other manifest/definition pairings receive explicit validation errors.
