# DOOM Developer Publishing Portal

`developer-portal/` is a local-first React/Vite client for the existing Marketplace API. Run it against the backend described in [Marketplace Development](MARKETPLACE_DEVELOPMENT.md).

## Run locally

```powershell
Copy-Item developer-portal/.env.example developer-portal/.env.local
docker compose -f marketplace-api/compose.yaml up -d
npm.cmd --prefix developer-portal ci
npm.cmd --prefix developer-portal run dev
```

Open `http://127.0.0.1:5173`. The API's `ALLOWED_ORIGINS` must include the exact portal origin. Configure `VITE_MARKETPLACE_API_URL` before starting Vite if the API uses a different local URL. Local HTTP is only appropriate for loopback development. Production must use HTTPS and a configured HTTPS API origin; this repository does not deploy the portal.

## Authentication and roles

Sign in or register through `/v1/auth/login` and `/v1/auth/register`. The access token remains in component memory, is not written to localStorage or cookies, and is discarded on sign out, tab close, or its server-provided expiry. The API re-reads the account role on each request. The portal only hides moderator controls for usability; every moderator endpoint also enforces its role on the server. Registration cannot select a role. The current API has no refresh token, account recovery, or server logout endpoint, so an expired session requires signing in again.

## Developer workflow

The portal lists owned submission records, creates and edits draft mod IDs, accepts `.doommod` uploads up to 10 MB, shows progress and cancellation, and attaches uploads to drafts through owner-only endpoints. The backend remains authoritative: it validates the archive and stores it in quarantine. Developers can revalidate an uploaded package, review returned diagnostics, and publish an approved version. Each publication is an immutable version; uploading the same bytes again is rejected as a duplicate.

Package `doom.json` and `mod.json` remain authoritative for title, ID, description, versions, compatibility, and capabilities. Category is selected on publish. The current API does not persist screenshots, icons, changelog, support/documentation links, or download statistics, so the portal does not expose nonfunctional editors or fabricate those values. A new version is a new package submission and requires a fresh review.

## Moderator workflow

Reviewer/Admin accounts see the pending queue, full manifest/definition and validation history, a short-lived private package review URL, prior decisions, and approve/reject controls with required reasons. Admins can quarantine a published version. Reviewers and admins can resolve reports and read a bounded, sanitized audit-event list. Backend status transitions and role checks remain authoritative; the portal asks for browser confirmation before moderation decisions.

The portal uses text rendering for untrusted package metadata and report content. It does not render descriptions as HTML, execute package content, or inspect an uploaded package in the browser.

## Verify

```powershell
npm.cmd --prefix developer-portal run typecheck
npm.cmd --prefix developer-portal test
npm.cmd --prefix developer-portal run build
npm.cmd --prefix marketplace-api run typecheck
npm.cmd --prefix marketplace-api test
```

For integration tests, use a disposable database only: the marketplace integration suite clears its tables. The portal's API integration tests in this milestone run against an isolated test database and in-memory artifact storage. No production endpoint is configured or deployed here.
