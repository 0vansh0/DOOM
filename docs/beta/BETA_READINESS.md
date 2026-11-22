# Beta Readiness

**Assessment date:** 2026-11-21  
**External closed beta: BLOCKED**

## Gate status

The existing security gate remains **BLOCKED**. It lists untested device security scenarios, lack of independent publisher signatures, missing enforced CI security/test/signing gates, and absent release signing configuration. This Milestone 12B work does not resolve those release blockers.

## Performance/reliability evidence

- Android debug build: PASS (`:app:assembleDebug`, BUILD SUCCESSFUL).
- Mod-engine JVM tests: PASS (70/70).
- Macrobenchmark APK compilation: PASS.
- Device Macrobenchmark execution: FAIL (2/2 harness failures; no valid metrics).
- Manual process-cold ActivityManager launch samples: 7; p50 620 ms, p95 835 ms. These are not Macrobenchmark or time-to-first-frame values.
- Idle memory: one PSS sample 114,143 KB; insufficient to claim stability or active-mod cost.
- Battery/energy and active workload: NOT MEASURED.
- Backend performance: BLOCKED; local Docker Compose API was unavailable.
- Emulator matrix: BLOCKED; not run.

## Decision

Keep external beta blocked. Before reconsideration, repair benchmark isolation/selectors, collect representative runtime and energy data, run the security-gate device scenarios, verify CI/security checks, and produce a release-signed artifact. See `docs/beta/TEST_PLAN.md` for the required evidence.
