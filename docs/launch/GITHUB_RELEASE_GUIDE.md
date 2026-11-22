# Private GitHub release preparation

This is a maintainer checklist, not authorization to create or publish a GitHub Release. Current unsigned beta.2 artifacts are not eligible for upload.

Before drafting a release, confirm the version and monotonic versionCode against all distribution tracks; preserve the stable package ID `org.doom.app`; select the approved signing identity; build from a reviewed source revision; run required tests; verify APK/AAB signatures with Android tools; and record SHA-256 values for the exact files being distributed.

The draft should include signed artifact(s), a checksum manifest, source revision, supported Android range, installation/update instructions, privacy notice, support route, known issues, and tester opt-in instructions. Exclude keystores, passwords, JWTs, debug certificates, unsigned verification outputs, and private diagnostic data.

Use a private draft and access-limited asset storage if owner-approved. Require a human to compare artifact hashes and signer fingerprints before making any release visible. GitHub release creation, upload and publication remain manual and require separate owner authorization.
