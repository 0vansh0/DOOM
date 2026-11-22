# Marketplace Download Security

The client obtains an immutable mod/version and SHA-256 from the public catalog, then obtains download metadata from the same API origin. The package URL must be a relative `/v1/catalog/.../package` path; the client rejects host changes, redirects, non-HTTPS release URLs, unexpected content types, files larger than 10 MB, truncated bodies, mismatching response checksum headers and mismatching computed SHA-256 values.

Only app-private cache storage is used for temporary downloaded bytes. Failed and cancelled downloads are deleted. The verified file is re-staged by `ModImportCoordinator` and passed to `ArchiveReader`, which applies the existing ZIP path, entry, expansion and manifest limits. Mod ID and version must match the published release identity. Android's document picker and no broad storage permission are used for manually selected packages.

The API streams artifacts from private object storage only while the database version remains published and not quarantined. The API response supplies the expected SHA-256 over the authenticated API channel. Marketplace moderation and checksums establish artifact integrity and publication state; they do not establish that the author is trustworthy or that a mod is safe.

Release requests require HTTPS. Debug builds may use cleartext solely to connect to a developer-configured local API. Never use a public cleartext marketplace origin.
