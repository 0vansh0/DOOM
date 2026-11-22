# DOOM Capability Manager

DOOM resolves capability requirements from the trusted registry shipped in `packages/manifest-schema/v2/registry.json`. Imported packages may request registry IDs; they cannot register executors, adapter implementations, permissions, or approvals.

## Separate gates

1. **Platform / hardware availability** reports whether the public Android API or device feature can be detected.
2. **Android authorization** reports special access or runtime permission state, such as `Settings.canDrawOverlays` and Android 13+ notification permission.
3. **DOOM approval** is the user grant stored for that specific installed mod. It is never inferred from Android permission.
4. **Trusted executor availability** reports whether this DOOM build contains an allowlisted native implementation.
5. **Runtime eligibility** rechecks authorization and capability availability at activation and immediately before each supported action. Permission loss cancels affected mod workflows and cleans its owned resources; other mods remain isolated.

Availability is one of `AVAILABLE`, `DENIED`, `UNAVAILABLE`, `UNSUPPORTED`, or `UNKNOWN`. `UNKNOWN` is used when an API probe fails or cannot reliably answer. A missing permission is never described as missing hardware.

## Current standard capabilities

The app probes battery and power services, overlay special access, vibration hardware, and standard clock/foreground UI APIs. The registry remains the trusted source of supported IDs and executor metadata. API compatibility is checked independently from permission and per-mod approval.

Android notification authorization is shown as a device diagnostic for DOOM's user-facing foreground-service controls. It is not treated as a mod execution capability. Overlay settings launch the package-specific Android special-access screen; returning from Settings does not itself mean access was granted.

## Optional adapters

`CapabilityAdapter` and `AdapterStatus` expose identity, connection state, supported IDs, required permissions, availability, privilege level, operation IDs, and failure explanation. The optional Shizuku adapter reports `shizuku.diagnostic.identity`, a user-initiated read-only settings diagnostic only. It is not an imported-mod capability or action. No privileged operation is available to third-party definitions.

Capability metadata is loaded from Registry v2 at `packages/manifest-schema/v2/registry.json`. Registry versioning is separate from SDK API versioning. The catalog describes risk, resource bounds, required permissions, executor IDs, hardware and adapter requirements; `implemented=false` always makes the capability unsupported, even when a package is structurally valid.
