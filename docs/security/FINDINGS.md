# Security Findings

Findings are based on local source review and executed tests. No OWASP certification is claimed.

## DOOM-SEC-001 — Vulnerable developer/test dependency tree (fixed)

- **Severity:** High for developer workstations and CI; affected packages are build/test dependencies, not Android runtime code.
- **Components:** `doom-cli` (Ajv), `visual-builder` (Vitest/Vite/tinypool).
- **Evidence:** Before remediation, `npm audit --audit-level=moderate` reported Ajv ReDoS advisory GHSA-2g4f-4pwh-qvx6 and a Vitest dependency tree including tinypool advisories rated Critical by npm, plus Vite/esbuild advisories.
- **Impact:** A malicious schema/test fixture or test-runner interaction could affect a developer process under applicable advisory preconditions.
- **Fix:** Pin Ajv to 8.20.0 and Visual Builder Vitest to 5.1.1; refresh the lockfiles.
- **Regression verification:** Post-fix npm audits report zero known vulnerabilities; CLI and Visual Builder tests/builds must pass on the updated versions.
- **Verification status:** Post-fix `npm audit --audit-level=moderate` completed with zero vulnerabilities in all four npm projects; CLI (59 tests), Visual Builder (11 tests/build), portal (4 tests/typecheck/build), and backend typecheck/tests completed. Backend integration cases are separately blocked as recorded below.
- **Residual risk:** Advisory databases change; audit all lockfiles in CI on every release.

## DOOM-SEC-002 — Concurrent moderation decisions could race (fixed and verified)

- **Severity:** Medium. A reviewer/admin request race could record conflicting decisions and leave the submission state dependent on update ordering.
- **Component:** Marketplace moderation decision route.
- **Evidence:** The prior code selected `PENDING_REVIEW` then updated by submission ID only, without an atomic expected-state predicate.
- **Fix:** The state update now includes `status = PENDING_REVIEW` and checks the returned row before writing the decision and audit record. A losing request returns 409.
- **Regression test:** Concurrent approve/reject test added to `marketplace-api/test/marketplace.integration.test.ts` and expects one 200, one 409, and exactly one persisted decision.
- **Verification status:** The isolated PostgreSQL and Compose S3 integration suite passed 18/18 tests, including this concurrent approval/rejection race test.
- **Residual risk:** Database isolation/race behavior should remain covered in CI with PostgreSQL, not only the developer integration environment.

## Accepted residual: marketplace publisher authenticity

The marketplace binds artifact bytes to a SHA-256 stored by the backend and served over HTTPS, and the Android client recomputes the digest before import. There is no independent publisher signature on release metadata. A compromise of the backend authority could publish a different valid declarative package and matching digest. Capability approvals, importer validation and the absence of executable-code loading limit impact, but do not provide cryptographic publisher provenance.
