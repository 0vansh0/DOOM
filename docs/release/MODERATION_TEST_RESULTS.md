# Moderation Test Results — Milestone 13B.5E

**Date:** 2026-11-22 (Asia/Calcutta)

## Executed evidence

The opt-in marketplace integration suite ran against a fresh PostgreSQL database and the configured SeaweedFS S3 storage. The complete API suite passed **30/30 tests across 6 files**, with no skips.

| Check | Result | Evidence |
|---|---|---|
| Guest moderation access | PASS | Protected routes return 401 without a bearer token. |
| Regular developer moderation access | PASS | Direct decision, moderation queue, audit and report triage calls are denied (403). |
| Author self-approval | PASS | A developer is denied after a controlled role change to reviewer; no decision is recorded. |
| Client role escalation | PASS | Registration with `role: ADMIN` is rejected (400). |
| Reviewer inspection/approval | PASS | Reviewer sees pending package, approves it, and an authorized owner publishes it. |
| Reviewer rejection and reason | PASS | Added regression test records `REJECTED` and the reviewer reason; public mod detail and version download return 404. |
| Quarantine | PASS | Reviewer cannot use the admin quarantine route; admin quarantine makes package download and, after all versions are quarantined, public mod details unavailable. |
| Immutable package identity | PASS | Downloaded bytes match the submitted bytes and SHA-256 metadata. |
| Concurrent decisions | PASS | One racing decision returns 200, the other 409, and exactly one decision persists. |
| Ownership checks | PASS | Cross-account submission access returns 404; owner-scoped feedback behavior is covered. |
| Malformed package upload | PASS | API returns 422, records `VALIDATION_FAILED`, and stores no quarantine key. |

## Scope boundaries

Accounts and artifacts were created only in the isolated local test database. No live marketplace publication occurred. Android client visibility of a test listing was not exercised because ADB found no connected device. Reviewer UI/browser workflow was not used; authorization was tested directly against backend routes, which is the enforcement boundary.

**Result:** Backend moderation authorization and state transitions **PASS**; end-to-end Android marketplace verification **BLOCKED**.
