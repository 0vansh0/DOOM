# Package review

- Run doom validate on both the source and final archive. Resolve missing manifests, unsupported definition versions, unsafe entry paths and unsupported assets.
- Run doom inspect and compare identity, version, checksum, Android API and capability declarations to the documentation.
- Supply deterministic doom test scenarios for matching and suppressed events, settings overrides and denied capabilities.
- Exercise bounded parameters, timeouts, workflow cancellation and cleanup on disable. Record physical device and emulator results separately.
- Confirm settings defaults, limits and persistence. Document API requirements, optional overlay access and privileged-operation restrictions.
- Include a README, changelog, authorship and license. Only allowlisted package files belong inside the archive. Use the existing CLI validators and simulator for all submissions.
