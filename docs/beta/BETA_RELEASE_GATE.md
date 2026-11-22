# Internal beta decision, 2026-11-21

**BLOCKED. No APK or bundle selected for distribution.** The source directory has no Git revision available. A release owner must record new evidence for the exact signed candidate and explicitly authorize any external launch.

| Release variant artifact (repository-relative path) | Bytes | SHA-256 |
|---|---:|---|
| app/build/outputs/apk/release/app-release-unsigned.apk | 22589364 | c2ca7a7642640a5e8f01840fd28f22571c1237452af2037b7c3adcb26b8f15af |
| app/build/outputs/bundle/release/app-release.aab | 7816664 | 2d06acc4732d1e034937491bc3bb5b9c36c370412565c77d9bfd2b696ea42e87 |

Android aapt and bundletool both report org.doom.app, 0.1.0-beta.1, version code 2, minimum API 31 and target API 35. apksigner rejected the unsigned APK. jarsigner identified the unsigned AAB. Neither has a certificate fingerprint. All four release signing variables were absent from the verification environment. Credential exposure outside that environment was not audited here.

## Evidence

| Area | Result |
|---|---|
| Android build | PASS for debug assembly and unsigned release APK/AAB verification |
| Engine and domain | PASS, 70 and 19 JVM cases respectively, zero skipped. Includes archive validation, lifecycle and recovery tests. |
| App and core-data debug JVM tasks | NO-SOURCE. Device persistence, update confirmation and recovery preservation remain unverified. |
| CLI and portal | PASS, 59 and 4 cases respectively |
| Visual Builder | Initial FAIL, 3 of 11 timed out or failed under load. Isolated rerun PASS, 11/11. |
| Marketplace | Default run 10 passed and 8 skipped. Disposable PostgreSQL with memory object storage and update fixture passed all 18. Includes publishing, quarantine, update review and download checksum. |
| Nothing CMF | Debug launch and offline/permission UI observed. See DEVICE_COMPATIBILITY.md. Signed upgrade and runtime scenarios pending. |
| Security | Audit lists remediated findings. Overall gate remains BLOCKED pending device checks, release identity, marketplace trust review and enforced CI evidence. |
| Performance | Existing gate BLOCKED. Startup and scroll instrumentation failed previously. Representative energy, stress and active-mod memory measurements remain outstanding. |
| Release marketplace | BLOCKED. Release configuration specifies HTTPS with a reserved invalid domain. An approved reachable endpoint and on-device integrity-checked download remain outstanding. |

Before approval, verify the channel certificate and hashes, install and upgrade a disposable signed installation, compare stored records, complete the device matrix, and obtain security and performance decisions. Keep this decision independent of publication and tester enrollment.
