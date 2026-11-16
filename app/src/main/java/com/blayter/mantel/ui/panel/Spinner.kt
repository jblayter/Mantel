package com.blayter.mantel.ui.panel

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blayter.mantel.ui.theme.HiveTokens

/** Status icons are 44 px in the mockups, drawn on a 24-unit grid with a 2.6-unit stroke. */
val StatusIconSize: Dp = 44.dp
const val STATUS_ICON_GRID = 24f
const val STATUS_ICON_STROKE = 2.6f
private const val SPIN_MILLIS = 1600

/** The amber "in progress" ring: a faint circle with a quarter arc turning on it. */
@Composable
fun Spinner(modifier: Modifier = Modifier) {
    val angle by rememberInfiniteTransition(label = "spinner").animateFloat(
        0f, 360f, infiniteRepeatable(tween(SPIN_MILLIS, easing = LinearEasing)), label = "spin",
    )
    val color = HiveTokens.Color.Status.inProgress
    Canvas(modifier.size(StatusIconSize)) {
        val u = size.width / STATUS_ICON_GRID
        val stroke = Stroke(STATUS_ICON_STROKE * u, cap = StrokeCap.Round)
        val topLeft = Offset(3 * u, 3 * u)
        val ring = Size(18 * u, 18 * u)
        drawOval(color, topLeft, ring, alpha = 0.35f, style = stroke)
        rotate(angle) { drawArc(color, -90f, 90f, useCenter = false, topLeft, ring, style = stroke) }
    }
}
