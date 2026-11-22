# Security Response

## Private intake

Follow [`SECURITY.md`](../../SECURITY.md). The repository does not identify a verified security email or staffed private intake endpoint. If GitHub's private vulnerability reporting is enabled for the actual repository, use it; otherwise request a private maintainer channel without posting exploit details publicly. Do not invent or guess a contact address.

## Response sequence

1. Preserve a sanitized report and acknowledge privately through the verified channel.
2. Classify impact and severity; identify affected releases, API services, package versions, and Android API/device conditions.
3. Contain: disable affected publishing/download paths or quarantine a release using the existing authorized moderation controls. Do not claim a global kill switch; none is evidenced.
4. Reproduce with isolated accounts, fixtures, and databases. Preserve audit evidence; never copy secrets or private reports into public issues.
5. Prepare a minimal patch, regression test, migration/rollback plan where applicable, and signed build from the approved signer.
6. Review secret exposure. Rotate credentials using the real provider/secret manager if affected; never put replacement secrets in Git or logs.
7. Verify tests, artifact identity, migration safety, and mod compatibility. Notify affected users through an approved channel with owner/legal review.
8. Coordinate responsible disclosure with the reporter; publish technical detail only after a fix and agreed disclosure timing.

## Signing-key incident

Pause release signing/distribution. Preserve signing audit evidence and inventory affected artifacts. Follow Android channel-specific key recovery/rotation rules with the account owner and Google Play if applicable; do not replace the key or claim existing installs can accept a differently signed APK. Reissue only after the channel identity and update path are verified.

This document is an operational procedure, not a response SLA or promise of staffed coverage.
