# Android Marketplace API Contract

The Android app uses the public Milestone 11A endpoints documented in `marketplace-api/openapi.yaml`:

- `GET /v1/catalog/mods` with search/category/capability/cursor/limit/sort.
- `GET /v1/catalog/mods/{modId}` for details and published versions.
- `GET /v1/catalog/mods/{modId}/versions/{version}/download` for checksum and package identity.
- `GET /v1/catalog/mods/{modId}/versions/{version}/package` for the current public artifact stream.

The package route is same-origin so phones do not receive private object-store hostnames. It checks the release remains published and unquarantined before streaming. No user account or device inventory is sent for public browsing. Current API models do not include image assets, changelogs, profile IDs, ratings or download counts.

The developer portal uses the authenticated developer and moderation routes in `marketplace-api/openapi.yaml`: login/register; owned submission list/detail; draft creation/edit and package attachment; package validation; publication; profile update; pending moderation queue and private review URL; moderation decisions; report decisions; administrator quarantine; and bounded audit history. Role and ownership checks are enforced by the API. No endpoint accepts client-selected roles.

Configure the origin with Gradle property `doomMarketplaceBaseUrl`. The client applies finite connect/read timeouts, disables redirects and uses a 2 MB JSON response cap. HTTPS is mandatory outside debug builds.
