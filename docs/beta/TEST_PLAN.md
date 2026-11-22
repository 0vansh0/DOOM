# Closed Beta Test Plan

## Gate

External closed-beta distribution is **BLOCKED** by the current [security beta gate](../security/BETA_SECURITY_GATE.md). This plan is for controlled local verification only until the gate is cleared.

## Scenarios

1. Install/update/uninstall while preserving existing mod records and settings.
2. Import valid and adversarial `.doommod` packages; verify rejection and staging cleanup.
3. Enable/disable mods, revoke permissions, enable safe mode, and verify owned resources stop.
4. Exercise Battery Glow preview, foreground charging events, overlay denial and teardown.
5. Exercise Shizuku absent, authorized, disconnected, and reconnected states without privileged writes.
6. Interrupt download/import and verify partial files are removed and installed state is unchanged.
7. Exercise marketplace timeout/offline cache and checksum mismatch using local fixtures.
8. Run Android unit/integration tests, CLI, Visual Builder, portal and backend tests.
9. Run startup/scroll benchmarks on disposable test data and record p50/p95, traces, device/build/API and thermal state.
10. Run screen-size, font-scale and API-level checks on available emulators/devices.

## Evidence required per run

Record exact application revision/version, APK checksum, test device/API/build, scenario, command or steps, pass/fail/blocked result, logs, and known impact. Never capture private user data in diagnostic attachments.
