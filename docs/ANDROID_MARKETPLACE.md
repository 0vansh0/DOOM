# Android Marketplace Client

DOOM's Discover tab keeps local bundled examples in their own section and requests community catalog data from the Milestone 11A public API. Marketplace responses are cached in Room with a bounded 100-row catalog table. Offline results are labeled stale and are informational only; installing requires an online release metadata request.

Set the API origin at build time with `-PdoomMarketplaceBaseUrl=https://your-api.example/`. HTTPS is required for package installation in every build because the published checksum and immutable identity must arrive over an authenticated channel. Debug builds allow cleartext for catalog-only local development. An emulator can reach an HTTP API through `http://10.0.2.2:8787/`; a physical phone requires a reachable LAN host address. Cleartext catalog access is not trusted for installation. The release manifest does not enable cleartext.

Current backend catalog fields are the ones in `marketplace-api/openapi.yaml`: listing identity, summary/category/developer display name, version, manifest/definition versions, minimum Android API, capabilities, compatibility metadata, checksum, publication timestamp and version identity. The API does not currently expose icons, screenshots, release notes, rating/download counts, or a stable developer profile ID, so the client does not invent those fields.

The detail sheet identifies requested capabilities and warns that local developer identity is unverified and moderation is not a safety guarantee. The downloaded archive is staged and checked by the existing importer. Install confirmation is separate; installation creates a disabled mod. Capability approval remains a separate action in the installed mod details screen.

## Current limitations

- Updates can be downloaded and installed only after an explicit review, only when the existing mod is disabled, and only when the published semantic version is newer. Replacement uses a filesystem journal and retains the old archive until the Room metadata commit is confirmed. Settings rows and existing approvals are preserved; newly requested capabilities have no approval and the updated mod stays disabled. The prior version is not retained as a user-selectable rollback after a successful commit.
- Compatibility display currently includes the package-declared Android minimum and requested capability IDs. A full per-capability device compatibility report in the marketplace confirmation is still pending.
- The development API origin must be configured at build time. The default is deliberately non-routable and does not point at a fabricated catalog. Local HTTP backend upload-to-install testing remains blocked until a trusted HTTPS endpoint is configured.
