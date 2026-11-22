# Changelog

## Unreleased - Phase 2 Milestone 6

- Added explicit SDK API 2 negotiation while preserving legacy public packages.
- Added Battery Saver events/conditions, battery threshold conditions and bounded haptic submissions.
- Added developer-defined integer options with generated controls and durable settings.
- Added additive Room migration 4 to 5, actual capability probes and diagnostic export metadata.
- Added shared Kotlin/CLI conformance fixtures, CLI templates and three ordinary SDK2 packages.
- Documented privilege boundaries and future ADB/Shizuku/root architecture.
- Device verification and limitations are recorded in docs/MILESTONE_6.md.


## 0.1.0-alpha

- Battery Glow 1.0 is a public-format package with an animated charging ring, live battery percentage, optional Android overlay, and a live appearance preview.
- Declarative ring settings support color, thickness, size, lifetime, and position. The editor can reset settings to the package defaults and copy preview settings JSON.
- Per-mod quarantine and crash-aware startup recovery complement the existing Safe mode and Disable all controls.
- Diagnostic export includes local runtime events and timing summaries. No telemetry is sent.
- Added a signed local test build workflow and Macrobenchmark coverage for startup and Installed scrolling.
