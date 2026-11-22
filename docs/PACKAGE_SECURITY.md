# Package security

The file picker grants access to a single content URI. DOOM copies the stream to private staging, enforcing its byte limit before validation and computing SHA-256. The archive reader reconciles the local headers with the central directory and checks lengths, CRC-32, path names and file attributes. Package entries are read as data. Their paths are never used as filesystem extraction destinations.

The review screen identifies the author as unverified. Installation requires explicit confirmation and an Android compatibility check. The app stores one immutable archive per ID in private storage, with the digest in Room. It rejects an existing ID regardless of version.

## Crash recovery

The repository serializes operations on the package store. It writes a durable per-ID pending marker before moving a reviewed archive to its final path on the same filesystem. Room publishes an installed-disabled row after the move. Startup treats Room as the commit point. If a pending marker has a matching installed row, the repository checks the final file and its digest. With no row it removes the unpublished archive. It cleans abandoned review files on startup. Uninstall keeps the existing Room lifecycle and resumes interrupted deletion before exposing the library. Temporary files and markers stay private.

Capability approval remains per mod. The app has no package code loading, package execution, or exported provider for these files. SHA-256 detects content changes and does not establish publisher identity. Process recovery is covered by tests. The protocol does not promise atomicity across sudden storage or device failure.
