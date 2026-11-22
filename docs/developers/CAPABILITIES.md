# Capabilities and authorization

Capabilities are stable names from `packages/manifest-schema/v2/registry.json`. A package declaration is a request, not a grant. DOOM separately checks supported host executor, Android API/permission or special access, hardware where applicable, adapter state, and explicit per-mod approval. The runtime rechecks authorization before action execution.

Battery reads use supported Android APIs. External overlays require Android overlay access plus DOOM approval. Haptics require available vibrator hardware. In-app preview requires the foreground DOOM UI. Unknown or unimplemented capabilities fail closed.

Shizuku is optional for DOOM as a host adapter, but its diagnostic identity operation is marked `modAccessible: false`; third-party definitions cannot invoke it or inspect authentication tokens. Screen-timeout recovery operations and `app.launch.approved` are likewise not public mod actions. Never claim a Shizuku requirement unless a registered, mod-accessible capability actually declares it.
