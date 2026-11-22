# SDK Conformance

The Android package validator and `doom validate` use the versioned JSON schemas under `packages/manifest-schema`. Existing manifest schemas 2 and 3 and definition versions 1–4 remain recognized; manifest schema 3 pairs with Definition 3 or 4. Definition 4 adds composed conditions, action arrays, workflow policies and typed settings. The CLI test runner consumes the validated V4 structure and uses no Android APIs.

Conformance cases are under `packages/manifest-schema/conformance`; the CLI tests load the same fixtures. Existing `doom-cli/test/cli.test.mjs` also checks archive safety, deterministic packaging, legacy packages, command behavior and community scenario assertions. Run:

```powershell
npm.cmd --prefix doom-cli test
.\gradlew.bat :mod-engine:test
```

Capability checks in simulation require both declaration and simulated DOOM grant. `overlay.render` also requires an explicit simulated `SYSTEM_ALERT_WINDOW` entry. This is only a policy simulation, never proof of Android authorization.
