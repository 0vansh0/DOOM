# Local DOOM Test Guide

**Distribution status: BLOCKED.** Do not distribute this build to external testers until `docs/security/BETA_SECURITY_GATE.md` is cleared and a release-signed artifact is verified.

## Controlled local testing

1. Use a disposable Android device or test profile and back up anything important before installing a build that may alter app data.
2. Install only a build whose source revision and SHA-256 are recorded by the maintainer.
3. Keep mod imports to trusted test packages. Review capabilities and grant them individually.
4. Test preview and foreground event behavior first. Android does not guarantee background UI launches.
5. To report an issue, include app version, Android API/build, exact steps, expected/actual result, frequency, and sanitized logs. Do not attach account data, private files, tokens, or full device identifiers.
6. Reproduce with safe mode and note whether it changes the result. Do not run privileged writes for diagnosis.
7. Before returning a device to normal use, disable test mods and confirm overlays/resources are gone.

## Issue severity

- **S0 Critical:** data loss, unauthorized privileged operation, or broad security compromise.
- **S1 High:** repeatable crash/data corruption or security boundary failure.
- **S2 Medium:** major feature failure with workaround.
- **S3 Low:** cosmetic or limited-impact defect.

No external feedback collection or telemetry is enabled by this guide. Diagnostics export must be initiated and reviewed by the user.
