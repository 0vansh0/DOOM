# Marketplace Mod Installation Flow

1. Discover requests only public published listings; local examples remain visibly separate.
2. Details show publisher-provided metadata, published version, declared schema/API requirements, checksum prefix and capabilities.
3. The client obtains current download metadata from the API and accepts only a same-origin package endpoint.
4. Download bytes are size-limited and hashed in private temporary storage.
5. `ModImportCoordinator` stages and validates the archive and checks its manifest identity against the published mod and version.
6. The user sees an explicit confirmation with requested capabilities and an unverified-author warning.
7. The existing Room repository installs the package disabled. It does not grant capabilities or start the runtime.
8. The user reviews and authorizes capabilities separately before activation.

Cancellation or validation failure removes temporary and staging files. Duplicate mod IDs are rejected by the existing importer. Cached catalog records cannot authorize a download or installation.
