# Beta Security Gate

**Status: BLOCKED**

This audit does not authorize public beta. The codebase has useful protections, but the following release gates are not yet satisfied:

- Device security scenarios (permission revocation, Shizuku loss, invalid archive import, marketplace package verification and recovery state) were not exercised in this audit.
- Published release metadata has no independent publisher signature; HTTPS plus a same-origin checksum protects transport integrity, not against compromise of the marketplace authority.
- CI workflow files now exist for Android, developer tools, marketplace and security scans. Their remote execution and branch-protection enforcement were not verified in this audit; one action is tag-pinned rather than immutable.
- Release signing is not configured in the repository; only debug APK build verification is available here.

The detected dependency advisories were addressed in the CLI and Visual Builder lockfiles. That does not clear the blocked gates above. A successful Android debug build is not a signed release artifact or a device security test.

## Final audit update — 2026-11-22

The gate remains **BLOCKED**. Current `npm audit --audit-level=moderate` reported zero vulnerabilities in all four npm projects; Android mod-engine/app tests passed and the debug APK built. Docker-backed marketplace cases were skipped because the Linux engine was unavailable. Signing identity, production HTTPS endpoint, publisher authenticity and physical Shizuku/Safe Mode/recovery verification remain unresolved. No Critical/High runtime exploit was confirmed in the reviewed findings; the High release-assurance gap remains open and is not accepted.
