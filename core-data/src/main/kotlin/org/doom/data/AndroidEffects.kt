package org.doom.data

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.os.VibratorManager
import android.util.DisplayMetrics
import android.os.VibrationEffect
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import org.doom.engine.runtime.ModEffect
import org.doom.engine.runtime.ModEffectExecutor
import org.doom.engine.runtime.ModExecutionContext
import org.doom.model.EffectSurface
import org.doom.model.EffectTemplate
import org.doom.model.RuntimePresentation
import org.doom.model.RingPosition
import org.doom.model.RingStyle
import org.doom.model.DisplayMetadata
import kotlin.math.roundToInt

internal class AndroidEffects(private val context: Context) : ModEffectExecutor {
    override val supportedActions = ModEffect.entries.toSet()

    private val windowContext by lazy {
        val display = checkNotNull(context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY))
        context.createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }
    private data class OwnedView(val modId: String, val view: EffectView)
    private val views = mutableMapOf<String, OwnedView>()
    private val windows by lazy { windowContext.getSystemService(WindowManager::class.java) }

    override fun pulse(context: ModExecutionContext): AutoCloseable? {
        check("haptics.vibrate" in context.grantedCapabilities)
        val vibrator = checkNotNull(this.context.getSystemService(VibratorManager::class.java)).defaultVibrator
        check(vibrator.hasVibrator())
        // A shared device actuator needs a global bound as well as the per-mod dispatch limit.
        val now = SystemClock.elapsedRealtime()
        if (lastPulse?.let { now - it < 500 } == true) return null
        lastPulse = now
        val token = Any()
        pulseOwner = token
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (error: RuntimeException) {
            pulseOwner = null
            throw error
        }
        return AutoCloseable {
            if (pulseOwner === token) {
                vibrator.cancel()
                pulseOwner = null
            }
        }
    }

    override fun show(context: ModExecutionContext, presentation: RuntimePresentation, slot: Int): AutoCloseable {
        if (presentation.surface == EffectSurface.PREVIEW) {
            check("ui.preview" in context.grantedCapabilities)
            return AutoCloseable { }
        }
        check("overlay.render" in context.grantedCapabilities && Settings.canDrawOverlays(this.context))
        check(slot in 0..1)
        val externalSlot = (0..1).firstOrNull { it !in ownedSlots }
            ?: error("The two external overlay slots are occupied.")
        val style = presentation.appearance.ring ?: RingStyle()
        val ring = presentation.template == EffectTemplate.BATTERY_RING
        val view = EffectView(windowContext, presentation).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val density = windowContext.resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            ((if (ring) style.diameterDp else 76) * density).roundToInt(),
            ((if (ring) style.diameterDp else 56) * density).roundToInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or when (style.position) {
                RingPosition.BOTTOM_START -> Gravity.START
                RingPosition.BOTTOM_CENTER -> Gravity.CENTER_HORIZONTAL
                RingPosition.BOTTOM_END -> Gravity.END
            }
            x = if (style.position == RingPosition.BOTTOM_CENTER) 0 else (18 * density).roundToInt()
            y = ((100 + 80 * externalSlot) * density).roundToInt()
            alpha = presentation.appearance.intensity / 100f
            setTitle("DOOM effect slot $externalSlot")
        }
        try {
            windows.addView(view, params)
        } catch (failure: RuntimeException) {
            runCatching { remove(view) }
            throw failure
        }
        ownedSlots.add(externalSlot)
        views[context.effectOwnerId] = OwnedView(context.modId, view)
        return AutoCloseable {
            views.remove(context.effectOwnerId)
            ownedSlots.remove(externalSlot)
            remove(view)
        }
    }

    override fun updateBattery(modId: String, percent: Int?) {
        views.values.filter { it.modId == modId }.forEach {
            it.view.percent = percent
            it.view.invalidate()
        }
    }

    override fun readDisplay(context: ModExecutionContext): DisplayMetadata {
        check("device.display.read" in context.grantedCapabilities)
        val display = checkNotNull(this.context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY))
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return DisplayMetadata(metrics.widthPixels.coerceAtLeast(0), metrics.heightPixels.coerceAtLeast(0),
            metrics.densityDpi.coerceAtLeast(0), display.rotation)
    }

    private fun remove(view: View) {
        view.visibility = View.GONE
        try {
            windows.removeViewImmediate(view)
        } catch (_: IllegalArgumentException) {
            // The system already removed the window or never attached it.
        }
    }

    private companion object {
        // All executors are confined to the main thread, including service sessions.
        val ownedSlots = mutableSetOf<Int>()
        var pulseOwner: Any? = null
        var lastPulse: Long? = null
    }

    private class EffectView(context: Context, private val frame: RuntimePresentation) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val color = Color.parseColor(frame.appearance.color)
        var percent = frame.batteryPercent
        private val style = frame.appearance.ring ?: RingStyle()
        private val start = SystemClock.elapsedRealtime()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val span = minOf(width, height).toFloat()
            paint.color = color
            when (frame.template) {
                EffectTemplate.BATTERY_RING -> {
                    val thickness = style.thicknessDp * resources.displayMetrics.density
                    val radius = (span - thickness * 1.5f) / 2f
                    val cx = width / 2f
                    val cy = height / 2f
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = thickness
                    paint.strokeCap = Paint.Cap.ROUND
                    val animate = ValueAnimator.areAnimatorsEnabled()
                    val angle = if (animate) (SystemClock.elapsedRealtime() - start) % 1200 * .3f else 270f
                    val sweep = percent?.let { 90f + it * 2.6f } ?: 252f
                    canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius, angle, sweep, false, paint)
                    percent?.let { level ->
                        paint.style = Paint.Style.FILL
                        paint.textSize = span * .24f
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText("$level%", cx, cy - (paint.ascent() + paint.descent()) / 2, paint)
                    }
                    if (animate) postInvalidateOnAnimation()
                }
                EffectTemplate.FLOATING_CARD -> {
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), span / 5, span / 5, paint)
                    paint.color = Color.BLACK
                    paint.textSize = span * .24f
                    paint.textAlign = Paint.Align.CENTER
                    canvas.drawText(frame.contentText ?: "DOOM", width / 2f, height * .58f, paint)
                }
            }
        }
    }
}
