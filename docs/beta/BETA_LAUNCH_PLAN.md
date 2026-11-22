# Controlled beta launch plan

**Hold distribution. No artifact is selected.** The release owner records a decision in BETA_RELEASE_GATE.md after verifying the actual signed bytes and testing the intended update path. Capture the source revision, package ID, increasing version code, signing certificate fingerprint and file SHA-256. Keep artifacts and evidence private.

For direct APK delivery, retain the verified signed APK unchanged in restricted storage. After explicit launch approval, grant access only to opted-in testers and provide the expected checksum and signing fingerprint through a trusted channel. A maintainer checks the first install and upgrade on a disposable device before expanding the cohort.

For Play internal testing, verify the registered upload-signed AAB and the Play app signing identity used for installs. Prepare an internal-track draft, tester list and release notes. Upload, enrollment and rollout each await owner authorization. Account setup, a support contact and a reviewed privacy notice are still needed.

Stop intake and suspend rollout for a P0 or reproducible P1. Preserve evidence and journals. Withdraw access to affected artifacts. Deliver a tested higher-code corrective update signed for the same channel. Removing an artifact from distribution leaves existing installations on testers' devices.
