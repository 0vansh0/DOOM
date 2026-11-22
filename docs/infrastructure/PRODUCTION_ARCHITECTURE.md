# DOOM marketplace production architecture

Status: preparation only. No public infrastructure, DNS, certificates, accounts, or real user data were provisioned for Milestone 13C.1.

The prepared topology separates the React developer portal, Fastify API, PostgreSQL, private quarantine object storage, and published artifact storage. A TLS reverse proxy is the sole public ingress in the proposed topology. PostgreSQL and S3 endpoints remain private. The API trusts explicitly configured proxy addresses and refuses staging/production startup when TLS, origins, secrets, or separated buckets are incomplete.

`deployment/staging.compose.yaml` is a configuration template, not a deployed service. Production requires a selected provider, reviewed network policy, verified images, managed secret delivery, backup/restore evidence, approved HTTPS origins, and the security/release gates recorded in `docs/release/13B_FINAL_GATE_RESULT.md`.
