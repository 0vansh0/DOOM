# Privileged adapter boundary

The Milestone 8 `CapabilityAdapter` reporting contract is reused. The Shizuku adapter reports current availability, privilege level, operation IDs and a user-readable state. Adapter registration is trusted Android application code; imported packages cannot register adapters or executors.

The read-only identity diagnostic checks service liveness, Shizuku authorization, explicit user initiation and the allowlisted operation ID immediately before calling the official API. It checks identity again and returns a cancellation result if the connection generation changes. Unsupported identity, permission loss and Binder disconnection fail closed. No arbitrary command, settings mutation, package operation or hook is implemented.

This adapter is diagnostic-only. It is not wired into Definition V3/V4 action registries. All ordinary capability gates and no-root execution paths remain unchanged. Future adapter-backed mod APIs require a separately versioned public contract and per-mod authorization policy.
