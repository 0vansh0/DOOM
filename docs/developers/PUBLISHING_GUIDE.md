# Publishing guide

Use the existing Developer Portal and marketplace backend. Uploads are quarantined and validated server-side; validation does not equal trust or publication. A release remains private until an authorized moderator reviews and publishes it. Every new version requires a new review, uses the same stable mod identity, and has immutable artifact/checksum metadata after publication.

Do not include credentials, executable payloads, undeclared assets or private diagnostics. Describe requested capabilities accurately and document Android permission/adapter prerequisites. Test with an isolated developer account and test-only visibility. Do not treat local marketplace tests as public release approval.
