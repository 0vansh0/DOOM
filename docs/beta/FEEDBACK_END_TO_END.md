# Feedback End-to-End — Milestone 13B.5A

## Results

- Physical Nothing A001 / API 36 instrumentation `MarketplaceHttpsDeviceTest.registersLogsInAndSubmitsConsentedFeedbackThroughTrustedHttps`: **PASS (1/1)**. It checked register/login/logout, invalid credentials, the server's 900-second `expiresIn`, consent rejection, consented submission, a non-empty server response ID, invalid-token HTTP 401 and in-memory session clearing.
- The device's server-confirmed report ID is `de9da699-cd80-46ae-8246-0ac13d5fcc11`. It was submitted to the isolated local test API over the trusted HTTPS gateway. The test report contains no unique device identifier.
- Host-side reviewer integration (`marketplace-api/scripts/13b5a-isolated-e2e.mjs`): report submission HTTP 201; another developer's read HTTP 404; developer access to triage HTTP 403; reviewer detail HTTP 200; reviewer update HTTP 200; one audit event persisted.
- The S3-backed backend integration suite passed 26 tests with 2 skipped. The default API suite passed 17 tests with 11 opt-in integration tests skipped.
- Local export after logout and interactive Feedback Center Compose navigation were not exercised on the device. The instrumented test calls the repository, not UI automation.

## Reproduction

Run `MarketplaceHttpsDeviceTest` on an approved connected device with `adb reverse tcp:9443 tcp:9443` and debug property `doomMarketplaceBaseUrl=https://localhost:9443/`. Run the host-only role/ownership test with `DATABASE_URL` restricted to the isolated `doom_marketplace_13b5a_20261122` test database and the same local HTTPS origin.

No credentials, JWTs or diagnostic attachments are written to logs by the test helper. No anonymous submission or TLS bypass is used.
