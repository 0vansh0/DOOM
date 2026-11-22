# DOOM controlled launch plan

**State: BLOCKED — preparation only. No distribution is authorized.**

## Evidence-based gate

Latest locally built candidate: `0.1.0-beta.2`, versionCode 3, application ID `org.doom.app`. The Nothing A001 has beta.1/code 2 installed. The candidate APK and AAB are unsigned; see `../release/ARTIFACT_INTEGRITY.md`. Release signing variables, approved signer fingerprint, and production HTTPS marketplace endpoint are absent. Release CI environment approvals are not verified. Android marketplace end-to-end and a same-signer app upgrade have not been verified. Current Android targetSdk is 35; current Google Play requirements require API 36 for new apps and updates since 2026-08-31.

Do not advance to private beta until security and moderation gates are evidenced, an approved signer and trusted backend are configured, API 36 migration is tested if using Play, privacy/support ownership is approved, and a signed upgrade has passed in an isolated test profile. Public distribution needs a separate explicit approval after all release gates pass.

## Staged process after blockers clear

1. **Internal verification:** isolated signed candidate; record checksum, signer certificate, source revision, test results, and device matrix.
2. **Private beta:** small invited group only after an owner-approved channel, support contact, privacy notice, tester consent and rollback instructions are ready.
3. **Expanded beta:** only after triage of first-round issues and a new signed, monotonically versioned artifact.
4. **Stable release:** distinct security, privacy, compatibility and distribution approval.

Each stage requires a human approval record. No workflow in this repository publishes automatically.

## Stop conditions

Pause new distribution for a suspected signing compromise, unauthorized privileged action, unapproved package publication, package-integrity bypass, widespread crash/data loss, or a privacy incident. Preserve evidence and recovery records; do not silently replace artifacts or downgrade users.
