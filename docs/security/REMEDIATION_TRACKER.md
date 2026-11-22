# Security Remediation Tracker

| ID | Issue | Severity | State | Verification / next step |
|---|---|---:|---|---|
| DOOM-SEC-001 | Vulnerable CLI and Visual Builder dependency versions | High (development tooling) | Fixed | Ajv 8.20.0, Vitest 5.1.1; confirm clean `npm audit` and run affected suites. |
| DOOM-SEC-002 | Concurrent moderation decision race | Medium | Fixed and verified | Isolated PostgreSQL + S3 integration suite passed 18/18, including concurrent review race. |
| RISK-SEC-003 | No independent release signature | Medium | Open design risk | Define signing key custody, signature format, key rotation/revocation, and client verification before public beta. |
| RISK-SEC-004 | Release signing and CI security pipeline not demonstrated | High release assurance gap | Open | Configure protected release signing outside the repository; add CI dependency/secret/static analysis gates and signed artifact provenance. |
| RISK-SEC-005 | Full physical-device permission, Shizuku, overlay, importer and recovery checks | Unknown | Blocked | Run controlled tests on Nothing CMF without changing privileged settings or erasing app data. |
