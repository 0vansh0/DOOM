package org.doom.feature.mod.details

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.doom.model.GlowAppearance
import org.doom.model.RingStyle

@Composable
internal fun RingPreview(appearance: GlowAppearance, percent: Int?, modifier: Modifier = Modifier) {
    val style = appearance.ring ?: RingStyle()
    val transition = rememberInfiniteTransition(label = "ring")
    val angle by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "rotation"
    )
    val diameter = style.diameterDp.dp
    val color = Color(android.graphics.Color.parseColor(appearance.color)).copy(alpha = appearance.intensity / 100f)
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding((style.thicknessDp / 2f).dp)) {
            drawArc(color, angle, percent?.let { 90f + it * 2.6f } ?: 252f, false,
                style = Stroke(style.thicknessDp.dp.toPx(), cap = StrokeCap.Round))
        }
        percent?.let { Text("$it%") }
    }
}
