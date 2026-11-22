# Performance Results — Milestone 13B.5

**Device:** Nothing A001, Android 16 / API 36, debug variant, version 0.1.0-beta.1. **Date:** 2026-11-22. No historical comparable numeric baseline was available.

## ADB launch observations

Method: repeated `adb shell am start -W` launch; cold samples preceded app process launch, warm samples followed an already-started process. These are wall-clock ActivityManager start timings and not frame-render or Macrobenchmark metrics.

| Measure | Samples (ms) | Median | Range |
|---|---:|---:|---:|
| Cold start | 1321, 1717, 1240, 1758, 1041 | 1321 | 1041–1758 |
| Warm start | 174, 79, 86 | 86 | 79–174 |

One post-launch snapshot: PSS 94,259 KB; RSS 195,020 KB. A single observation is not a memory profile and cannot quantify idle/active-mod deltas.

## Benchmark harness

`:benchmark:connectedBenchmarkAndroidTest` failed both benchmark tests because the harness could not confirm activity launch completion or retrieve graphics frame statistics. Installing the separate benchmark target did not resolve it. Startup frame latency, UI jank, active-mod memory, workflow latency, marketplace latency, background CPU and battery energy remain **unmeasured**. No optimization claim is made.
