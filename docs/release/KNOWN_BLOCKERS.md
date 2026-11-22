# Known Release Blockers — Final Milestone 13B Audit

| Blocker | Priority / impact | Evidence | Needed to clear |
|---|---|---|---|
| No approved release signing identity | Release gate | Four DOOM signing environment variables and approved signer fingerprint absent; release build enforces the check. | Configure approved external keystore and independently approved signer fingerprint; verify APK/AAB signatures and update compatibility. |
| Marketplace production endpoint unavailable | Core integration | Android defaults to `https://marketplace.doom.invalid/`; current Docker engine is unavailable. | Configure and verify an approved HTTPS endpoint; keep authenticated transport and checksum enforcement. |
| Marketplace DB/S3 suite skipped in current audit | Integration assurance | API test run: 17 pass, 11 opt-in tests skipped; Compose cannot connect to Docker Desktop engine pipe. | Rerun migrations and integration tests with isolated DB/storage without deleting volumes. |
| Physical Shizuku/Safe Mode/recovery incomplete | Runtime safety | JVM tests pass; no physical authorization or service transitions were performed. | Controlled non-destructive A001 verification with explicit authorization, preserving app data. |
| Android marketplace mod import/update unverified | Core feature | Prior docs block Discover/download/import/execute/update; current A001 check was launch-only. | Install/import an isolated package on device, exercise approved action, update, settings retention and cleanup. |
| Performance profile incomplete | Readiness | Historical 2/2 Macrobenchmark failures; no active-mod, energy, overlay/workflow performance samples. | Repair benchmark selector/runner policy and collect comparable measurements. |
| Emulator environment incomplete | Coverage | Emulator binary exists; no AVD and image payload incomplete. | Install complete SDK image and create a disposable AVD; run representative tests. |
| Marketplace publisher provenance | Security | SHA-256 is backend-authenticated over HTTPS, but no independent publisher signature exists. | Design and verify signed release metadata, rotation and revocation before public ecosystem distribution. |
| CI run enforcement not evidenced | Assurance | Workflows exist; remote execution history/branch protections not accessible. Wrapper validation uses `@v4` tag. | Verify protected CI status checks and pin the remaining action immutably. |

No unresolved Critical/High product defect was confirmed in the reviewed findings; the high release-assurance gap remains a blocker and is not waived.

## 13B.5E status update (2026-11-22)

- The prior “marketplace DB/S3 suite skipped” blocker is **resolved for backend integration scope**: local Compose was started without deleting volumes, required migrations were applied to a new isolated audit database, and the S3-backed marketplace suite passed 29/29. See `MARKETPLACE_SECURITY_VERIFICATION.md`.
- The Android marketplace client E2E remains **BLOCKED**: `adb devices -l` returned no connected device, so physical Discover/download/import/execute/update was not run.
- Production signing, approved production HTTPS, independent publisher authenticity, CI evidence, and physical recovery verification remain open.
