# Security regression results

**Date:** 2026-11-21

## Executed

- Mod-engine importer/runtime regression suite: 70/70 passed. Existing `ArchiveReaderTest` contributes 9 cases.
- DOOM CLI suite: 59/59 passed, including traversal/deceptive asset rejection and manifest/definition/capability conformance fixtures.
- Marketplace API suite: 11 passed, 11 skipped. Build/typecheck passed; database-backed authentication, ownership, moderation, quarantine and retention assertions were not executed in this run.
- Device Account UI and Feedback Center navigation/local-export tests passed as part of 9/9 instrumentation tests. No credentials were entered and no network submission was attempted.

## Not verified in this run

- PostgreSQL-backed feedback triage authorization, append-only audit persistence, retention deletion and ownership isolation.
- Live HTTPS marketplace/package checksum flow.
- Physical Shizuku connection/revocation or privileged operation tests.
- Release signature verification, signed update compatibility or external distribution gate.
- Dedicated dependency/secret/SAST/container scans.

The prior `docs/security/BETA_SECURITY_GATE.md` remains **BLOCKED**. This regression run does not change that decision. No security certification or broad security guarantee is implied.
