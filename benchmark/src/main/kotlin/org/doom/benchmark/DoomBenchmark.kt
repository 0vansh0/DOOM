package org.doom.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DoomBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()
    private val targetPackage = "org.doom.app.perf"

    @Test fun coldStartup() = benchmark.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 7,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
    }

    @OptIn(ExperimentalMacrobenchmarkApi::class)
    @Test fun installedScrolling() = benchmark.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Ignore(),
        iterations = 5,
        setupBlock = {
            startActivityAndWait()
            checkNotNull(device.wait(Until.findObject(By.text("Installed").clickable(true)), 5_000)
                ?: device.wait(Until.findObject(By.text("Installed")), 5_000)).click()
            device.wait(Until.hasObject(By.textStartsWith("Version ")), 5_000)
            device.waitForIdle()
            val list = device.findObject(By.scrollable(true))
            assumeTrue("Seed Installed with a library spanning several screens before measuring.", list?.scroll(Direction.DOWN, .65f) == true)
            list?.scroll(Direction.UP, .65f)
        }
    ) {
        val list = checkNotNull(device.findObject(By.scrollable(true)))
        list.fling(Direction.DOWN)
        device.waitForIdle()
        list.fling(Direction.UP)
        device.waitForIdle()
    }
}
