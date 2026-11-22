# Milestone 13C.2 release candidate

**Date:** 2026-11-22 (Asia/Calcutta)  
**Application ID:** `org.doom.app`  
**Version:** `0.1.0-beta.2` (versionCode 3; minSdk 31; targetSdk 35)  
**Source revision:** unavailable; workspace has no Git metadata.

This is an unsigned, local verification build, not a signed release candidate and not distributable. The version was advanced from the device-observed beta.1/code 2 to beta.2/code 3. Unsigned APK and AAB builds succeeded with `-PdoomUnsignedVerification=true`; see `ARTIFACT_INTEGRITY.md` for hashes. Signed release is blocked first by the missing approved production HTTPS endpoint and also by unset signing credentials. No production keystore was created.

Automated debug regression: mod-engine 76/76 passed; app debug unit tests 4/4 passed; debug APK assembled. Marketplace API 21 passed/13 integration-gated skipped; portal 8/8 passed. No live backend integration was run in this turn. Nothing A001 is connected and already contains `org.doom.app` versionCode 2/version `0.1.0-beta.1`; it was inspected read-only. No upgrade was attempted, preserving its data.

**Release candidate status: BLOCKED. Distribution readiness: BLOCKED.** Approved signing identity, trusted production HTTPS endpoint, independent release provenance, live integration tests, and isolated signed upgrade verification remain outstanding. No file was published or distributed.
