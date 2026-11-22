# DOOM SDK reference

The canonical contracts are `packages/manifest-schema/v2/` and `packages/manifest-schema/v2/registry.json`. Current versions are Manifest Schema 3, SDK API 2, Definition V3 and Definition V4. V3 keeps its existing parser and semantics. Do not relabel a V3 definition as V4 without converting it to the V4 structure.

V4 is a strict declarative JSON contract. Imported packages cannot add executors or registries, and cannot contain executable code. Capabilities are checked by the host at activation and again before effects. Approval in a marketplace is not Android permission or per-mod authorization.

Registry entries are machine-readable; unimplemented operations stay unavailable. In particular `app.launch.approved` is catalogued but not implemented, system-setting operations are app-owned and not mod accessible, and the Shizuku diagnostic identity operation is not mod accessible. No shell or arbitrary network capability is part of this SDK.

See [mod structure](MOD_STRUCTURE.md), [events](EVENTS.md), [actions](ACTIONS.md), [conditions](CONDITIONS.md), [settings](DYNAMIC_SETTINGS.md), and [capabilities](CAPABILITIES.md).
