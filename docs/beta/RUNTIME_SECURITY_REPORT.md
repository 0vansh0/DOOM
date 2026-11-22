# Runtime Security Report — 13B.5B

## Reviewed controls

- Shizuku operations are registered by operation ID and restricted to an explicit policy/allowlist; mod definitions do not receive a shell-command interface or direct Shizuku API access.
- Runtime authorization and adapter availability are rechecked before privileged execution.
- Safe Mode blocks privileged writes and third-party execution through existing runtime flows.
- Reversible writes use the existing persisted change transaction coordinator and compare-and-restore conflict checks.
- Workflow cancellation and resource ownership/cleanup use the existing mod runtime and coordinator.

## Change made

`MainActivity.onResume()` now refreshes Shizuku state so the UI reconciles after returning from Android settings. The execution gate remains authoritative and rechecks before use.

## Verification boundary

JVM unit tests and debug compilation provide deterministic implementation evidence. ADB confirmed the A001/API 36 device, installed DOOM package/process, and installed Shizuku package. Package installation does not establish that Shizuku is running or authorized. No physical privileged action, permission revocation, or forced process interruption was performed. These live checks remain unverified.
