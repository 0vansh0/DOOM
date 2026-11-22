# Marketplace Moderation

Automated validation checks conformance and import safety only. It does not establish who authored a package or whether its requested behavior is trustworthy. Public packages are marked with the account's developer profile; no verified-publisher label is created.

1. A developer uploads a `.doommod` package. It is written to private quarantine and structurally validated.
2. The owner may request validation again. A passing result enters `PENDING_REVIEW`; failures remain `VALIDATION_FAILED` with typed, bounded diagnostics.
3. A Reviewer or Admin inspects the validation results and package metadata, then records an approve/reject decision and reason. An author cannot review their own submission.
4. Approval is not publication. The developer explicitly publishes an approved version. Semantic version, schema versions, capabilities, compatibility, checksum and immutable artifact key are recorded.
5. An Admin can quarantine a published release; it immediately disappears from public catalog/download lookup. The immutable database record and audit event remain.
6. Authenticated users may report a mod. Reviewers/admins resolve or action the report, retaining its record.

There are no automatic trust badges or automatic publication paths. A moderation action and a structural validator result are separately auditable.
