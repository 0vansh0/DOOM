# Security boundaries

Untrusted inputs include all archive names, metadata, JSON values, assets and installed package bytes. The existing bounded ZIP reader, path checks, strict JSON parser, schema/binding validator, private staging, SHA-256 verification and install journal remain in force for API 2. Schemas reject unknown fields, versions, triggers, conditions, actions, capabilities and settings. Imported files cannot register handlers or load DEX/JAR/SO/JavaScript.

The trusted boundary consists of shipped registries, native adapters, Room grants, Android permission checks and runtime-created execution contexts. A mod's declared capability is a request. Capability discovery never grants it. Runtime revalidates package bytes and settings, checks event/condition/action grants, and creates the context. Device probes distinguish denied, unavailable and unsupported states. Scope remains the Android application sandbox.

Haptic effects are one bounded 50 ms pulse, with a shared actuator owner and global 500 ms minimum spacing (excess submissions are skipped) in addition to per-mod throttling. Cleanup only cancels the current owned pulse. Pulse completion, disable, safe mode and session shutdown release ownership. No waveform/repetition/command can be supplied by a package. Android may suppress vibration even after submission.

Power state is read via PowerManager, with one shared registered receiver during a foreground host session. There is no polling, privileged power setting write or implicit background start. Battery threshold conditions fail closed when no battery reading is available. Developer options are bounded integers with validated IDs, references and defaults. Stored settings are rechecked against the verified definition before activation.

External overlays retain their bounded non-touchable windows, explicit Android authorization, per-mod grants, resource limits, service notification/stop controls and immediate teardown paths. API 2 does not grant a new way to start persistent overlays from the background.

The future privilege architecture is documented separately. No bridge, unrestricted shell, root, hooking, accessibility automation or exported asset provider is added. Diagnostics are user-exported through SAF. They include capability probes and bounded runtime logs without private package paths or setting values. Failure isolation and safe mode remain available.
