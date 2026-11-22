# Privacy and data-processing review

**Status: draft; owner/legal review required before public beta.** This is a source-based inventory, not legal advice or a final Play Data safety declaration.

| Data | Current behavior evidenced in source | Retention/deletion status |
|---|---|---|
| Account email, password input, display name | Registration uses email/password/display name; backend stores an Argon2id password hash and profile. Login issues a short-lived JWT (`expiresIn`, default configured 900 seconds). Android holds the token in memory; it is not persisted as an ordinary preference. | No self-service account deletion route was found. Owner/developer profile and audit-event lifecycle must be defined. |
| Feedback report | Authenticated POST requires `{consent:true, report}`; report contains category, title, description, steps, expected/actual, app and Android versions, optional mod ID. No diagnostic attachment is automatically added. | Backend prunes feedback after 30 days; owner can delete a report via authenticated deletion route. Reviewer access is role-gated. |
| Marketplace package and listing | Developer package uploads are validated and quarantined; public package exposure follows moderation/publication state. S3-compatible storage holds artifacts. | Published artifact retention, withdrawal, object lifecycle and backup deletion must be specified by production operations. |
| On-device mod state | Installed mod definitions/settings, capability approvals and recovery records are stored locally for app behavior. No automatic diagnostic upload is part of the inspected UI. | User data remains on device; app backup is disabled. Uninstall/data removal consequences must be explained in final policy. |
| Operational logs | Fastify logger redacts authorization/cookies/password/token/report fields in the current server setup. | Production log destination, access, retention and incident procedures are not configured/verified. |

## Permissions and optional processing

The manifest includes INTERNET, VIBRATE, SYSTEM_ALERT_WINDOW, WRITE_SETTINGS, FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS and the Shizuku API permission/provider. Explain each in the in-app flow and the final policy. Overlay and Modify system settings are user-granted special access; Shizuku is optional and must not be described as root or blanket mod authorization. Standard mods remain declarative and do not receive credentials or arbitrary network access.

## Required before publication

- Identify the legal entity/controller, privacy contact and support route; none was provided, so do not invent them.
- Implement and test clear account deletion covering account-linked data, subject to legal retention requirements. Current feedback deletion alone does not satisfy account deletion.
- Complete third-party SDK/dependency data-flow review and verify analytics/telemetry for the final release dependency graph.
- Approve public policy wording, jurisdictional rights process, subprocessors, storage regions, security contact, backup retention, published package retention, and response timelines.
- Publish an immutable, public HTTPS privacy-policy URL and make it accessible in-app before Play submission.
- Complete Data safety answers to match the actual production binary and server processing.
