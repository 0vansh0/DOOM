# DOOM Security Threat Model

## Scope and trust boundaries

Assets include installed package bytes, mod settings and grants, Android capability state, runtime resources, privileged-change journals, marketplace accounts and release artifacts. DOOM treats imported `.doommod` files, their metadata, and all marketplace text as untrusted.

Trust boundaries:

1. Android SAF / marketplace download -> private staging and package validator.
2. Validated declarative data -> trusted Kotlin registries and executors.
3. DOOM app identity -> Android permissions / special access.
4. DOOM -> optional Shizuku Binder identity (ADB shell or root; independently checked).
5. Developer -> backend-owned account and submission records.
6. Reviewer/admin -> moderation endpoints and immutable catalog records.
7. Backend -> PostgreSQL and private S3-compatible object storage.
8. Developer portal -> backend API; browser session token is held in memory.

## STRIDE summary

| Threat | Example attacker goal | Principal controls / residual concerns |
|---|---|---|
| Spoofing | Impersonate a developer, reviewer, or Shizuku service | Argon2id passwords, short-lived JWTs, server-side role lookup, official Shizuku API. Production TLS and signing operations still require deployment-specific verification. |
| Tampering | Change package bytes, capability declarations, or a privileged setting | Archive validation, SHA-256, immutable version keys, Room transaction journal, restore conflict checks. Marketplace metadata is not independently publisher-signed. |
| Repudiation | Deny a moderation or privileged change | Backend audit and moderation records; device transaction journal. Local log retention and tamper resistance are not a secure audit ledger. |
| Information disclosure | Expose tokens, private uploads, device identifiers, or mod files | In-memory portal bearer token, role-gated quarantine download, no broad Android storage permission, private buckets. Production headers, TLS, and operational log review remain unverified. |
| Denial of service | Exhaust memory/CPU through uploads or complex definitions | 10 MiB package cap, worker timeouts/concurrency, rate limits, schema complexity limits. Upload request memory is buffered; production load testing is outstanding. |
| Elevation of privilege | Invoke undeclared actions or run arbitrary code through a mod | Declarative format only, trusted action/capability allowlists, per-mod grants, Android permission checks, no shell/DEX/JAR/SO execution in the standard engine. Shizuku operations are not exposed to package definitions. |

## Attacker assumptions

An attacker may submit malformed archives or definitions, create an ordinary developer account, manipulate their own metadata, race requests, use compromised marketplace text, revoke Android permissions, or cause an optional Shizuku service to disconnect. They do not control the app signing key, backend database/object store, or reviewer account unless explicitly stated as a compromise scenario.

The audit does not claim protection from a fully compromised Android OS, app-signing key, backend administrator, or marketplace origin. Such compromise can replace the software or the authority that supplies package identity.
