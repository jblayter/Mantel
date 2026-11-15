package com.blayter.mantel.ui.panel

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.blayter.mantel.domain.DeployOutcome
import com.blayter.mantel.domain.DeployRun
import com.blayter.mantel.domain.formatAge
import com.blayter.mantel.domain.formatTimer
import com.blayter.mantel.domain.formatTook
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType

// Sizes from the approved mockup that are not in tokens.json.
private val PaddingY = 18.dp
private val RowGap = 12.dp
private val IconSize = 44.dp
private val BarHeight = 14.dp
private val StripeWidth = 10.dp
private const val ICON_GRID = 24f
private const val ICON_STROKE = 2.6f
private const val SPIN_MILLIS = 1600

/** Stripes travel 40 px in 1.6 s in the mockup. */
private const val STRIPE_PX_PER_SECOND = 25f

/** tan(30°): the stripes lean 30° off vertical (a 120° CSS gradient). */
private const val STRIPE_LEAN = 0.5774f

/** Recent workflow runs: in progress, failed, passed. Each has an icon and words, never color alone. */
@Composable
fun DeploysCard(deploys: List<DeployRun>, modifier: Modifier = Modifier) {
    HiveCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = HiveTokens.Card.paddingX, vertical = PaddingY),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            Text("Deploys", style = HiveType.bodyBold, color = HiveTokens.Color.textMuted)
            deploys.forEach { DeployRow(it) }
        }
    }
}

@Composable
private fun DeployRow(run: DeployRun) {
    val c = HiveTokens.Color
    val outcome = run.outcome
    Column(verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s8)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HiveTokens.Space.s16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusIcon(outcome)
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        append(run.repo)
                        withStyle(SpanStyle(color = c.textMuted, fontWeight = HiveTokens.Font.Weight.regular)) {
                            append(" ${run.workflow}")
                        }
                    },
                    style = HiveType.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(detail(run), color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when (outcome) {
                is DeployOutcome.InProgress ->
                    Text(formatTimer(outcome.elapsed), style = HiveType.deployTimer, color = HiveTokens.Color.Status.inProgress)
                is DeployOutcome.Failed -> Text("${formatAge(outcome.ago)} ago", color = c.textMuted)
                is DeployOutcome.Passed -> Text("${formatAge(outcome.ago)} ago", color = c.textMuted)
            }
        }
        if (outcome is DeployOutcome.InProgress && outcome.progress != null) ProgressBar(outcome.progress)
    }
}

private fun detail(run: DeployRun): AnnotatedString = buildAnnotatedString {
    when (val outcome = run.outcome) {
        is DeployOutcome.InProgress -> append("Going out now · ${run.commitTitle} · ${run.branch}")
        is DeployOutcome.Failed -> {
            withStyle(SpanStyle(color = HiveTokens.Color.Status.trouble, fontWeight = FontWeight.Bold)) {
                append("Failed at ${outcome.failedStep}")
            }
            append(" · ${run.commitTitle}")
        }
        is DeployOutcome.Passed -> append("Passed in ${formatTook(outcome.took)} · ${run.commitTitle}")
    }
}

/** Spinner, cross or tick inside a circle, drawn on the mockup's 24-unit grid. */
@Composable
private fun StatusIcon(outcome: DeployOutcome) {
    val status = HiveTokens.Color.Status
    val angle = if (outcome is DeployOutcome.InProgress) {
        rememberInfiniteTransition(label = "deploy").animateFloat(
            0f, 360f, infiniteRepeatable(tween(SPIN_MILLIS, easing = LinearEasing)), label = "spin",
        ).value
    } else {
        0f
    }
    Canvas(Modifier.size(IconSize)) {
        val u = size.width / ICON_GRID
        val stroke = Stroke(ICON_STROKE * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val ringTopLeft = Offset(3 * u, 3 * u)
        val ringSize = Size(18 * u, 18 * u)
        fun line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), stroke.width, StrokeCap.Round)
        when (outcome) {
            is DeployOutcome.InProgress -> {
                drawOval(status.inProgress, ringTopLeft, ringSize, alpha = 0.35f, style = stroke)
                rotate(angle) {
                    drawArc(status.inProgress, -90f, 90f, useCenter = false, ringTopLeft, ringSize, style = stroke)
                }
            }
            is DeployOutcome.Failed -> {
                drawOval(status.trouble, ringTopLeft, ringSize, style = stroke)
                line(status.trouble, 8.5f, 8.5f, 15.5f, 15.5f)
                line(status.trouble, 15.5f, 8.5f, 8.5f, 15.5f)
            }
            is DeployOutcome.Passed -> {
                drawOval(status.success, ringTopLeft, ringSize, style = stroke)
                val tick = Path().apply {
                    moveTo(7.5f * u, 12.5f * u)
                    lineTo(10.5f * u, 15.5f * u)
                    lineTo(16.5f * u, 9f * u)
                }
                drawPath(tick, status.success, style = stroke)
            }
        }
    }
}

/** Track with a striped, slowly moving fill: the "in progress" line style. */
@Composable
private fun ProgressBar(progress: Float) {
    val shift by rememberInfiniteTransition(label = "deploy").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "stripes",
    )
    Box(Modifier.fillMaxWidth().height(BarHeight).clip(CircleShape).background(HiveTokens.Color.track)) {
        Canvas(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(BarHeight).clip(CircleShape)) {
            drawStripes(shift)
        }
    }
}

private fun DrawScope.drawStripes(shift: Float) {
    drawRect(HiveTokens.Color.amber)
    val period = StripeWidth.toPx() * 2 / 0.866f
    val lean = size.height * STRIPE_LEAN
    // The animation runs 0..1 once a second; wrap the travelled distance into one period.
    val offset = (shift * STRIPE_PX_PER_SECOND * density) % period
    var x = -period + offset
    while (x < size.width + lean) {
        val stripe = Path().apply {
            moveTo(x, 0f)
            lineTo(x + period / 2, 0f)
            lineTo(x + period / 2 - lean, size.height)
            lineTo(x - lean, size.height)
            close()
        }
        drawPath(stripe, HiveTokens.Color.amberDeep)
        x += period
    }
}
