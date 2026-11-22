# Feedback triage verification

## Implemented contract

The marketplace API accepts authenticated reports at `POST /v1/feedback`. Its request is `{ consent: true, report }`; the report fields and categories are validated by the strict Zod schema in `marketplace-api/src/feedback.ts`. The caller's owner ID comes only from the verified JWT principal. The response is HTTP 201 with `data.id` and `data.status`.

Report access routes are:

- `GET /v1/feedback?offset=&status=` lists only the caller's non-expired reports.
- `GET /v1/feedback/{id}` returns a report only to its owner; all other accounts receive the same 404 as an unavailable report.
- `POST /v1/feedback/{id}/delete` deletes only an owned report.
- `GET /v1/moderation/feedback?offset=&status=` lists non-expired reports for REVIEWER and ADMIN roles.
- `GET /v1/moderation/feedback/{id}` returns a non-expired report only to REVIEWER or ADMIN.
- `POST /v1/moderation/feedback/{id}` updates triage only for REVIEWER or ADMIN.

The triage body accepts only `priority` (`P0`–`P3`), `status` (`open`, `in-progress`, `resolved`), `component`, `reproduction`, `regression_test`, and `resolution`. It rejects unknown fields and invalid status transitions. Same-status edits are allowed. `resolved` reports can be reopened to `open`; moving directly from `resolved` to `in-progress` returns 409.

The moderation portal displays report fields, created/updated timestamps, status, priority, reproducibility, component, regression test and resolution. It posts triage changes to the reviewer-only route and displays API errors. Maintainer assignment is not available: the account model has DEVELOPER, REVIEWER and ADMIN roles but no maintainer ownership/assignment field.

## Audit and retention

Each successful triage update and its state change are written in one PostgreSQL transaction. The existing `audit_events` table records the reviewer ID, feedback ID, event action, timestamp, and previous/new status, priority and reproduction classification. It does not copy report contents. The moderation audit API exposes these six fields only for `FEEDBACK_TRIAGE_UPDATED`; other event details remain hidden.

Migration `0003_feedback_triage.sql` adds `updated_at`, status/priority checks for new or modified rows, an index for status/update time, and a deletion trigger that removes feedback-linked audit entries when the report is deleted. The hourly retention sweep deletes reports older than 30 days; expired rows are also excluded from reads. The deterministic `pruneExpiredFeedback(cutoff)` function is used by the retention test.

## Verification evidence

On 2026-11-21, `npm test -- --run` in `marketplace-api` completed with 11 passed and 11 skipped. The database integration suite is guarded by `MARKETPLACE_INTEGRATION=1`; it was skipped because Docker was unavailable and no isolated PostgreSQL test database was available. Its source now covers owner isolation, reviewer/admin access, invalid transitions, audit writes, deletion cleanup, expiry and rate limiting, but those database assertions have not been executed in this environment.

The developer portal test suite passed: 2 test files, 6 tests. The portal production build (`npm run build`) passed. Android regression command `:mod-engine:test :core-data:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks` passed: 70 mod-engine tests, 7 feedback repository tests and 4 feedback ViewModel tests, with no failures or skips. `:app:assembleDebug` passed in the same milestone verification run. The current shell did not have `adb` on PATH, so no device install or UI test was performed in this run.

Docker verification was retried on 2026-11-21. The Docker CLI responds, but the `desktop-linux` engine pipe `//./pipe/dockerDesktopLinuxEngine` is absent, so Compose services and migrations could not be checked. Live Android-to-reviewer submission remains BLOCKED until Docker or an approved HTTPS test backend and isolated test accounts/database are available.
