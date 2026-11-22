# DOOM incident response runbook

This runbook uses existing controls and explicitly identifies unavailable ones. There is no verified global app kill switch, remote forced update, or automatic privileged rollback. Actions below require an authorized owner/operator and must preserve evidence and recovery journals.

## Initial response

1. Record UTC/timezone, source revision, artifact hash, version, affected service and reporter evidence. Limit access to the incident team.
2. Classify impact and notify the project owner/security maintainer through the private channel they designate. No public security contact has been supplied in this repository.
3. Preserve relevant redacted API, database, CI and moderation audit records. Do not copy access tokens, passwords, full private feedback reports or signing material into the incident ticket.
4. Contain using the narrowest existing control; document every action and its actor.

## Scenario actions

- **Developer account compromised:** disable the account using an authorized backend operator path if available, invalidate credentials/signing sessions, review submissions and audit events, and revalidate affected packages. Do not assume a self-service session-revocation console exists.
- **Malicious mod:** stop new downloads by quarantining/withdrawing the affected release through authorized moderation. Preserve its checksum and validation evidence. Existing installed copies cannot be remotely removed by this app; publish a signed advisory/update only after review.
- **Signing-key compromise:** stop all signing/distribution immediately. Determine whether the Play app-signing key or upload key was affected. Preserve and revoke/reset through the relevant channel owner procedure; never replace keys on a guess.
- **API/storage credential exposure:** restrict/revoke affected credentials at the provider, rotate dependent secrets, audit object access and regenerate only after a safe recovery plan. Do not reveal secrets in reports.
- **Database incident:** restrict service access, preserve snapshots/logs, determine scope, restore only to an isolated environment first, and obtain owner approval before production recovery.
- **Broken app release/widespread crash:** stop further track rollout through the publishing channel if one is active; prepare a higher-code signed fix. Android APK downgrade is not a general rollback mechanism, and this application has no verified silent rollback.
- **Privileged-operation failure:** disable the affected app operation/mod through existing authorization or Safe Mode, retain transaction records, and use compare-and-restore recovery only after rechecking current state and authorization. Never replay interrupted writes automatically.
- **Privacy incident:** stop affected collection/sharing, preserve minimal evidence, consult the accountable owner/legal reviewer and follow applicable notification obligations after facts are established.

## Recovery and closure

Verify containment, integrity, user-data preservation and tests before resuming. Record root cause, corrective change, regression test and remaining risk. No public disclosure or user notification text should be issued until facts and responsible owner approval are confirmed.
