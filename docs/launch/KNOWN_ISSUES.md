# Launch known issues and blockers

Updated 2026-11-22 from existing release evidence.

## Distribution blockers

- No approved release keystore, signing credentials or pinned certificate; current APK/AAB are unsigned.
- The release marketplace URL remains the safe `.invalid` placeholder; no approved production HTTPS service is available.
- Current candidate targets Android API 35; Google Play submissions require API 36 as of 2026-08-31.
- No same-signer application upgrade test has been performed. A001 still contains beta.1/code 2; beta.2/code 3 is not installed.
- Android live marketplace and feedback submission remain unverified against a trusted HTTPS backend.
- No self-service account deletion endpoint or final legal privacy contact is present.
- Signed release CI environment approvals and secrets have not been configured/verified.

## Reliability/coverage limitations

- Emulator/system image is unavailable in the recorded test environment; OEM coverage beyond Nothing A001 is not established.
- Physical Shizuku grant/revoke/disconnect, Safe Mode recovery, external overlay execution and update behaviors remain incomplete in historical coverage.
- Existing performance reports note Macrobenchmark launch/frame measurement failures and no reliable comparable memory/energy baseline.
- No external tester feedback or reliability statistics are available.

Refer to `../release/13C2_COMPLETION_REPORT.md`, `../release/13B_FINAL_GATE_RESULT.md`, `../beta/KNOWN_ISSUES.md`, and `../beta/PENDING_TEST_RESOLUTION.md` for source evidence and historical detail.
