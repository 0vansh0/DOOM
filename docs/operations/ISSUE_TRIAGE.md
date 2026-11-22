# Issue Triage Operations

Use the existing Feedback Center, `/v1/moderation/feedback` API, and Developer Portal Issue Triage. Do not create a parallel issue database. Feedback reports expire after 30 days; transfer only sanitized engineering facts to the long-lived project tracker with the reporter's privacy in mind.

## Priority

- **P0:** active compromise, destructive behavior, or serious data loss. Contain first; stop affected release/mod distribution while facts are established.
- **P1:** broad crash, authorization bypass, unsafe package install/update, or core workflow unavailable without a viable workaround.
- **P2:** significant defect with a workable path around it.
- **P3:** minor polish or low-impact improvement.

Do not lower severity to clear a release gate. Security incidents use the private process in `SECURITY.md`.

## Required record

Record a stable issue ID, priority, affected component, app/mod versions, Android API level, sanitized reproduction steps, expected/observed result, reproduction classification, owner (active reviewer/admin for private feedback), linked fix, regression test, resolution, and verification result. Do not put report contents, tokens, or attachments into audit events. The existing portal/API support priority, status, component, reproduction, regression test, resolution, and reviewer assignment; Android version/app version are fields in the submitted report.

Status transitions are `open` → `in-progress`/`resolved`, `in-progress` → `open`/`resolved`, and `resolved` → `open`. Backend authorization and transition checks are authoritative. Reviewer assignment is limited to active REVIEWER/ADMIN accounts and is audited.
