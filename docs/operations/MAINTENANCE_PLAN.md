# DOOM Maintenance Plan

## Operating principles

- Treat Android, the mod engine, package importer, CLI, Visual Builder, marketplace API, and portal as separate release surfaces. Run their existing suites serially when they share generated CLI output.
- Review dependency advisories and lockfile changes in pull requests. Do not merge automated version updates without compatibility and security review.
- Run `tools/maintenance/run-regression.ps1` for a local consolidated check. The script does not deploy, sign, publish, or access production data.
- Run `node tools/maintenance/check-doc-links.mjs` for local Markdown links. CI runs it with its focused unit test.
- Generate a release evidence worksheet with `node tools/maintenance/release-checklist.mjs`. Hashes are not signature or release approval.
- Keep physical-device and emulator results separate in `docs/beta/DEVICE_COMPATIBILITY_MATRIX.md`.

## Change cadence

Review platform and dependency advisories when a security advisory arrives, before a release candidate, and during dependency-change pull requests. Re-run relevant suites after each update. Do not create scheduled external jobs as part of this repository workflow.

## Evidence and approvals

Record the tested source revision when Git metadata is available, exact commands, failures/skips, device/API level, and artifact hashes. Require independent approval for production signing, deployment, live-data migrations, reviewer actions, and public publication. A green local build does not authorize any of those actions.

## Current readiness reference

`docs/release/13B_FINAL_GATE_RESULT.md`, `docs/release/13C2_COMPLETION_REPORT.md`, `docs/launch/13C3_COMPLETION_REPORT.md`, and `docs/infrastructure/13C1_COMPLETION_REPORT.md` retain the evidence and blockers. The 13B marketplace/mobile verification, approved signing identity, production HTTPS service, independent publisher authenticity, and broad device/recovery coverage remain blockers in the latest recorded audit.
