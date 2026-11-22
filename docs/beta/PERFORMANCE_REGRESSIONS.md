# Performance regression status

**Date:** 2026-11-21

No comparable performance benchmark was run during Milestone 13B.3. Therefore this report makes no startup, memory, battery, marketplace latency or improvement claim.

The previous baseline in `docs/performance/PERFORMANCE_BASELINE.md` records process-cold launch samples (p50 620 ms, p95 835 ms), one idle PSS snapshot (114,143 KB), and failed Macrobenchmark harness cases (2 failures). Those measurements are historical and are not directly comparable to this test run. Battery/energy and active-mod memory were not measured there or here.

The Visual Builder production build and test suite passed; this is build evidence, not runtime performance evidence. Android instrumentation validated basic navigation and local flows but collected no profiling trace or frame-time distribution.

**Follow-up:** repair the Macrobenchmark launch and Installed navigation selectors, run on a controlled test installation, and collect comparable p50/p95 startup, frame, memory and energy data before asserting performance readiness.
