# Beta support workflow

**Status: no public or staffed support service is configured.** Do not invite testers until the owner provides an approved support contact and access-controlled intake process.

## In-app feedback path

The existing Help & Feedback flow collects a user-authored title/category/description, reproduction details, app and Android versions, and optional mod identifier. Server submission requires an authenticated session and explicit consent. The backend limits submission rate, isolates report ownership, grants triage access only to reviewers/admins, and prunes reports after 30 days. Local document export is separate from submission. It does not silently upload diagnostics.

## Triage

1. Verify requester authorization and report ID; do not move private reports into public tickets with identifying content.
2. Assign severity: P0 security compromise/destructive data loss; P1 core crash or broad installation failure; P2 significant defect with workaround; P3 minor issue.
3. Record component, affected version, reproduction result and linked regression test in the protected admin desk.
4. Redact tokens, account data, private files and unique identifiers from any reproduction artifact.
5. Escalate safety/security findings using the repository's responsible disclosure instructions. Do not invent an email address or promise a response time.
6. Close only with a verified fix or documented disposition; preserve audit history and retention policy.

## Before external beta

Choose and staff an approved support channel, publish contact/privacy disclosures, define response ownership and retention, test the reporter's deletion path, and document emergency escalation. Current backend live availability is not configured for the mobile client.
