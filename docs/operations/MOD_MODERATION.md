# Marketplace Moderation Operations

## Submission handling

Developer authentication and server-side ownership checks are required. Uploads go to private quarantine, pass archive/schema/capability validation, and enter `PENDING_REVIEW`; validation is not a trust decision. A reviewer/admin must inspect the submission and record an approve/reject reason. Developers cannot approve their own submissions. Publication is a separate owner-authorized operation after approval. Published artifact keys and package bytes are immutable.

## Unsafe or disputed releases

An authorized administrator can quarantine a published version. Verify that public catalog/download routes omit the quarantined version, preserve its artifact and moderation/audit records, and record the incident. Do not delete evidence to hide a failure. Suspend a developer only if the current account management API supports that action; no dedicated suspension workflow was verified in the latest audit.

## Routine checks

For each new release: verify the recorded checksum against stored bytes, inspect validation findings/capability declarations, review changelog and compatibility, and require a new human decision for every version. For a reported unsafe release: quarantine first when warranted, then reproduce in an isolated environment and review related versions. There is no automated malware guarantee or independent publisher signature; communicate this limitation.

## Authorization and audit

Moderation endpoints enforce REVIEWER/ADMIN or ADMIN roles server-side. Review decisions and quarantine actions use the existing append-only audit events. Never rely on hidden portal buttons as authorization. See `marketplace-api/openapi.yaml` and the security verification reports for the actual route contract.
