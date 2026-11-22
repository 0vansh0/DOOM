# Shizuku Lifecycle Verification — 13B.5B

## Automated evidence

`mod-engine:test --rerun-tasks` exercises the Shizuku connection-state mapping and the privileged-operation policy using deterministic state inputs. The covered states include unavailable/not installed, not running, permission required or denied, disconnected, and connected privilege levels. Policy cases verify the explicit operation allowlist, user-initiated diagnostic requirement, and privilege checks. These are JVM tests; they do not prove the physical service or authorization state.

The app now refreshes Shizuku state from `MainActivity.onResume()`. This addresses the stale UI state possible when a user returns from Shizuku after changing authorization while DOOM was backgrounded. The runtime still rechecks authorization at operation time.

## Physical device

Read-only ADB inspection on Nothing A001 (API 36) found DOOM installed and its process running. The Shizuku package was also installed. No permission request, revocation, service stop, privileged operation, or device setting write was performed. Consequently, live transitions for grant, denial, revocation, disconnect, reconnection, and process restart remain **BLOCKED / NOT VERIFIED**.

## Limits

Do not treat package presence as proof that the Shizuku service is running or that DOOM is authorized. No device test in this report makes that inference.
