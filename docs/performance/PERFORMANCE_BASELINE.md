# DOOM Performance Baseline

**Run date:** 2026-11-21  
**Application:** `org.doom.app`  
**Device:** Nothing A001, Android 16 / API 36 (ADB-reported)  
**Build:** local debug APK; no release-like signed build was available.

## Measurements

| Measurement | Result | Method and limits |
|---|---:|---|
| ActivityManager process-cold start, p50 | 620 ms | Seven `am force-stop` + `am start -W` trials; `TotalTime`; not a cold boot or Macrobenchmark result. |
| ActivityManager process-cold start, p95 | 835 ms | Nearest-rank p95 over seven trials (therefore the maximum). |
| Process-cold range | 561–835 ms | Same seven trials. |
| Macrobenchmark cold startup | BLOCKED | Both instrumented Macrobenchmark tests failed; see below. |
| Installed screen scroll | BLOCKED | Benchmark selector did not find the Installed navigation item. |
| Idle memory snapshot | 114,143 KB PSS (~111.5 MiB) | One `dumpsys meminfo org.doom.app` snapshot after reinstall, on the home screen, with no active mods. Not an average or peak. RSS was 220,330 KB; Java heap 13,392 KB; graphics 26,394 KB. |
| Active-mod memory | NOT MEASURED | No reliable runtime workload profile was collected. |
| Battery/energy | NOT MEASURED | No Battery Historian or repeatable energy test was run. |
| Time to first frame / usable UI | NOT MEASURED | ActivityManager timings do not establish either metric. |

The manual start samples in milliseconds were `835, 656, 561, 620, 589, 601, 655`. These are process-cold launches after force-stop, with device state and caches otherwise uncontrolled.

## Instrumented benchmark result

`:app:assembleBenchmark :benchmark:assembleBenchmark` succeeded. `:benchmark:connectedBenchmarkAndroidTest` ran on `A001 - 16` but failed both cases (2 tests, 2 failures, 0 skipped): `installedScrolling` failed because its UI selector returned null; `coldStartup` failed Macrobenchmark's activity-launch-completion check. The device ActivityTaskManager log did report `Displayed org.doom.app/.MainActivity`, so the Macrobenchmark result is inconclusive, not a measured startup failure.

The benchmark runner configuration had `uninstall_after_test: true`; UTP uninstalled `org.doom.app`. The debug APK was reinstalled and launched afterward. App-private state may have been cleared by this uninstall and cannot be confirmed or restored from available evidence. No further instrumented tests were run against the phone.

## Reproduction

```powershell
.
adb shell am force-stop org.doom.app
adb shell am start -W -n org.doom.app/.MainActivity
adb shell dumpsys meminfo org.doom.app
```

For meaningful cold-start and scrolling percentiles, first repair the benchmark selectors and runner install policy, then run on a disposable test install/data set and retain the Macrobenchmark output and trace files.
