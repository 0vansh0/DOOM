# Release Gate Report — Milestone 13B.5

**Decision: BLOCKED for beta/stable distribution.**

## Verified

- App identity from release output metadata: `org.doom.app`, `0.1.0-beta.1`, versionCode 2, minSdk 31, targetSdk 35.
- Debug APK assembles and was launched on A001 after reinstall.
- Unsigned verification builds succeeded:
  - APK: `app/build/outputs/apk/release/app-release-unsigned.apk`, 22,720,436 bytes, SHA-256 `9C2DAC6E9394B16EBC4359C2705A995FC7A49BDFFF74F2C31435F08038809A98`.
  - AAB: `app/build/outputs/bundle/release/app-release.aab`, 7,864,775 bytes, SHA-256 `0E72F06AED4A87788189B4AEC4E5A25BD23BCCDC25B1C48B7C9339FA766641C9`.
- Signature inspection: `apksigner verify` returned `DOES NOT VERIFY` for the explicitly unsigned APK; `jarsigner -verify` reported the AAB unsigned. These are not distribution artifacts.
- Android/module, CLI, Builder, portal and marketplace automated test counts are in `../beta/FINAL_REGRESSION_RESULTS.md`.

## Blocking conditions

- Approved production signing credentials and independently approved signer fingerprint are absent. No signed release or update-signature compatibility can be verified.
- Release marketplace base URL is `https://marketplace.doom.invalid/`; no usable trusted HTTPS backend was configured. Live Android marketplace/auth/feedback is unavailable.
- Macrobenchmark failed; performance readiness is incomplete.
- Emulator unavailable; one connected device only.
- Physical Shizuku and Safe Mode/recovery coverage incomplete.
- Connected test tooling removed the installed package; it was reinstalled, but pre-run user data preservation cannot now be attested.
- Developer Portal browser end-to-end flow not verified.

No key was created, no artifact was published and no service was deployed publicly. A successful debug build or unsigned release build does not satisfy this gate.

## Final 13B audit update (2026-11-22)

- Decision A, ready to begin production preparation: **BLOCKED** pending complete Android marketplace/import/update integration and physical runtime recovery verification.
- Decision B, ready for stable public distribution: **BLOCKED**. No signing environment variables or approved signer fingerprint are configured; Android uses `https://marketplace.doom.invalid/`; physical security/recovery and emulator/performance evidence remain incomplete.
- Current Android verification: 76 mod-engine, 22 core-domain, 8 core-data and 4 app unit tests passed (110 total); `:app:assembleDebug` passed. CLI 60/60, Visual Builder 11/11/build, Portal 6/6/build passed. Marketplace API default: 17 pass, 11 integration tests skipped because Docker Desktop was unavailable.
- Current A001 read-only launch succeeded. No APK install/update, data clear, privileged operation, external overlay test or marketplace mod execution was performed in the final audit.
- CI workflow files now exist (Android, CLI, Builder, API, Portal and security), superseding the older statement that none were present. Remote CI execution/branch-protection status is still unverified.

See `13B_FINAL_AUDIT.md` and `KNOWN_BLOCKERS.md` for the full evidence inventory. These decisions do not authorize deployment or distribution.

## 13B.5E final verification addendum (2026-11-22)

Local marketplace API/PostgreSQL/SeaweedFS integration passed **29/29** with no skips using a fresh isolated database. `/health` and `/ready` both returned 200; migrations 0001–0003 applied to the isolated database. Backend publication, rejection, quarantine, role/ownership enforcement and checksum behavior passed. See `MARKETPLACE_SECURITY_VERIFICATION.md` and `MODERATION_TEST_RESULTS.md`.

The Android Marketplace UI-to-import workflow remains unverified because `adb devices -l` returned no attached device. Therefore marketplace/client readiness and production preparation remain **BLOCKED**. Stable distribution remains **BLOCKED** for the documented signing, trusted production HTTPS, and publisher-authenticity gaps.
