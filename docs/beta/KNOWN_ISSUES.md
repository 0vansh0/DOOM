# Known issues and unverified behavior

**Updated:** 2026-11-22

## Blocking / high priority for beta

- **DOOM-REG-001 — Late authentication response after logout.** Hardened with attempt-generation invalidation and a deterministic unit regression test. The final post-fix test result is recorded in `REGRESSION_TEST_RESULTS.md`; no live identity provider was used.
- **Android-to-marketplace/feedback end-to-end remains unverified.** Backend security/integration tests now pass against isolated PostgreSQL and SeaweedFS, but Android's configured host is `https://marketplace.doom.invalid/` and no trusted HTTPS test service is configured.
- **Connected Android test tooling removed the app package during this run.** The debug APK was reinstalled and launched, but pre-run app-private state preservation cannot be attested. Review device test tooling before using it against valuable installations.
- **Release/security gate remains blocked.** The prior security report cites missing approved release signer/verified signed artifact, missing enforced CI security gates, and untested device security scenarios.

## Coverage gaps

- One Android 16/API 36 Nothing A001 is connected and authorized. Emulator binary 37.3.10 is installed, but there is no AVD or system image; SDK Manager image installation did not complete. Android API 31–35 emulators and other OEMs remain untested.
- The Battery Glow device test covered in-app preview and permission-denied handling only. Real charging broadcasts and external overlay display were not tested.
- Shizuku physical lifecycle, safe-mode device behavior, recovery after process/device restart, mod updates and real marketplace integrity were not exercised on the phone.
- Macrobenchmark ran but both tests failed to observe launch/frame timing. Ad hoc ADB cold/warm timing was captured; no comparable historical baseline or reliable memory/energy profile exists.
- Release APK/AAB verification builds are unsigned. Approved signing material and signer fingerprint are absent; distribution remains blocked.
- Marketplace readiness timeout is now bounded at 3 seconds, and liveness/readiness routes are explicit. Continue monitoring host/PostgreSQL response time; an earlier probe took about 30 seconds before the bound was added.

No external tester feedback or reliability rate is inferred from these results.
