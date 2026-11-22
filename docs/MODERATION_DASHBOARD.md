# Moderation Dashboard

The dashboard uses the existing reviewer/admin API routes. The server role is loaded from PostgreSQL for each authenticated request; client-supplied roles have no authority.

## Submission review

The queue includes only `PENDING_REVIEW` submissions. Inspect package owner ID, manifest and definition, versions, API requirement, SHA-256, requested capabilities, validation findings, and earlier moderation decisions. Reviewer-only endpoints provide a short-lived private review URL. Approve and reject require a reason and are auditable. An author cannot review their own submission. The author separately publishes an approved version.

## Reports and quarantine

Reviewers and administrators may resolve open user reports with a reason. Quarantine is restricted to administrators by the API and removes that version from public catalog and download queries. Portal actions require confirmation. Quarantine does not delete the immutable stored artifact or erase its audit history.

## Audit

`GET /v1/moderation/audit?limit=1..100` returns event ID, actor ID, action, subject type/ID, and timestamp. It omits free-form details and credentials. Access is restricted to reviewer/admin roles.

Moderation does not imply malware-free or publisher-verified. Packages are declarative and are not executed during validation or review.
