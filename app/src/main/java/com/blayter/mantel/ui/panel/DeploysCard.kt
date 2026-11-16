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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import com.blayter.mantel.domain.DeployOutcome
import com.blayter.mantel.domain.DeployRun
import com.blayter.mantel.domain.DeployStatus
import com.blayter.mantel.data.github.GitHubRuns
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.blayter.mantel.domain.formatAge
import com.blayter.mantel.domain.formatTimer
import com.blayter.mantel.domain.formatTook
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType

// Sizes from the approved mockup that are not in tokens.json.
private val PaddingY = 18.dp
private val RowGap = 12.dp
private val BarHeight = 14.dp
private val LastSeenTopGap = 4.dp
private const val NOTICE_LINE_HEIGHT_EM = 1.35f
private const val MAX_LAST_SEEN = 2
private val ClockTime = DateTimeFormatter.ofPattern("h:mm")
private val StripeWidth = 10.dp

/** Stripes travel 40 px in 1.6 s in the mockup. */
private const val STRIPE_PX_PER_SECOND = 25f

/** tan(30°): the stripes lean 30° off vertical (a 120° CSS gradient). */
private const val STRIPE_LEAN = 0.5774f

/**
 * Recent workflow runs: in progress, failed, passed. Each has an icon and words, never color alone.
 * When there is nothing live to list, the card says why instead: still
 * waiting, no recent runs, or a problem with the GitHub token.
 *
 * @param repoNames short names of the watched repositories, for the "no recent runs" sentence.
 */
@Composable
fun DeploysCard(
    deploys: List<DeployRun>,
    modifier: Modifier = Modifier,
    status: DeployStatus = DeployStatus.Live,
    repoNames: List<String> = emptyList(),
) {
    HiveCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = HiveTokens.Card.paddingX, vertical = PaddingY),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            Text("Deploys", style = HiveType.bodyBold, color = HiveTokens.Color.textMuted)
            when (status) {
                DeployStatus.Waiting -> Text("Waiting for the first reading", color = HiveTokens.Color.textDim)
                DeployStatus.Live ->
                    if (deploys.isEmpty()) NoRecentRuns(repoNames) else deploys.forEach { DeployRow(it) }
                is DeployStatus.TokenExpired -> TokenExpired(status.lastSeenAt, deploys)
                DeployStatus.TokenNotStored -> Notice(
                    icon = { TroubleTriangle() },
                    title = "GitHub token was not stored",
                    titleColor = HiveTokens.Color.Status.trouble,
                    text = "This TV could not encrypt the token, so Mantel did not keep it. Deploy status is off.",
                )
            }
        }
    }
}

/** An icon, a headline and a sentence: the shape of every "nothing to list" message on this card. */
@Composable
private fun Notice(icon: @Composable () -> Unit, title: String, titleColor: Color, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(HiveTokens.Space.s16)) {
        icon()
        Column {
            Text(title, style = HiveType.title, color = titleColor)
            Text(text, style = HiveType.body.copy(lineHeight = NOTICE_LINE_HEIGHT_EM.em), color = HiveTokens.Color.textSecondary)
        }
    }
}

@Composable
private fun NoRecentRuns(repoNames: List<String>) {
    val where = when (repoNames.size) {
        0 -> ""
        1 -> " in ${repoNames[0]}"
        else -> " in ${repoNames.dropLast(1).joinToString(", ")} or ${repoNames.last()}"
    }
    Notice(
        icon = { NeutralIcon() },
        title = "No recent runs",
        titleColor = HiveTokens.Color.text,
        text = "Nothing has run$where in the last ${GitHubRuns.RECENT_WINDOW.inWholeDays} days. " +
            "The next run shows here as soon as it starts.",
    )
}

/** GitHub rejected the token. What was last seen stays, dimmed, with clock times instead of ages. */
@Composable
private fun TokenExpired(lastSeenAt: LocalTime?, lastRuns: List<DeployRun>) {
    Notice(
        icon = { TroubleTriangle() },
        // A token that never worked has not "expired"; say what is known.
        title = if (lastSeenAt != null) "GitHub token has expired" else "GitHub token was not accepted",
        titleColor = HiveTokens.Color.Status.trouble,
        text = if (lastSeenAt != null) {
            "Deploy status stopped updating at ${lastSeenAt.format(ClockTime)}. " +
                "Push a config file with a new token to bring it back."
        } else {
            "There is no deploy status until it is replaced. Push a config file with a working token."
        },
    )
    if (lastSeenAt == null || lastRuns.isEmpty()) return
    Text(
        "Last seen at ${lastSeenAt.format(ClockTime)}",
        Modifier.padding(top = LastSeenTopGap),
        style = HiveType.bodyBold,
        color = HiveTokens.Color.textMuted,
    )
    lastRuns.take(MAX_LAST_SEEN).forEach { run ->
        val outcome = run.outcome
        val (what, age) = when (outcome) {
            is DeployOutcome.InProgress -> "was running" to outcome.elapsed
            is DeployOutcome.Failed -> (outcome.failedStep?.let { "failed at $it" } ?: "failed") to outcome.ago
            is DeployOutcome.Passed -> "passed" to outcome.ago
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(HiveTokens.Space.s16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusIcon(outcome, tint = HiveTokens.Color.textDim)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(run.repo) }
                    append(" ${run.workflow} · $what")
                },
                Modifier.weight(1f),
                color = HiveTokens.Color.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(lastSeenAt.minusSeconds(age.inWholeSeconds).format(ClockTime), color = HiveTokens.Color.textDim)
        }
    }
}

/** The warning triangle at status-icon size. */
@Composable
private fun TroubleTriangle() {
    val color = HiveTokens.Color.Status.trouble
    Canvas(Modifier.size(StatusIconSize)) {
        val u = size.width / STATUS_ICON_GRID
        val stroke = Stroke(STATUS_ICON_STROKE * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val triangle = Path().apply {
            moveTo(12 * u, 3 * u)
            lineTo(22 * u, 21 * u)
            lineTo(2 * u, 21 * u)
            close()
        }
        drawPath(triangle, color, style = stroke)
        drawLine(color, Offset(12 * u, 10 * u), Offset(12 * u, 15 * u), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(12 * u, 18 * u), Offset(12 * u, 18.5f * u), stroke.width, StrokeCap.Round)
    }
}

/** A dim circle with a dash: nothing happening, nothing wrong. */
@Composable
private fun NeutralIcon() {
    val color = HiveTokens.Color.textDim
    Canvas(Modifier.size(StatusIconSize)) {
        val u = size.width / STATUS_ICON_GRID
        val stroke = Stroke(STATUS_ICON_STROKE * u, cap = StrokeCap.Round)
        drawOval(color, Offset(3 * u, 3 * u), Size(18 * u, 18 * u), style = stroke)
        drawLine(color, Offset(8 * u, 12 * u), Offset(16 * u, 12 * u), stroke.width, StrokeCap.Round)
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
                append(outcome.failedStep?.let { "Failed at $it" } ?: "Failed")
            }
            append(" · ${run.commitTitle}")
        }
        is DeployOutcome.Passed -> append("Passed in ${formatTook(outcome.took)} · ${run.commitTitle}")
    }
}

/** Spinner, cross or tick inside a circle, drawn on the mockup's 24-unit grid. */
@Composable
private fun StatusIcon(outcome: DeployOutcome, tint: Color? = null) {
    val status = HiveTokens.Color.Status
    if (outcome is DeployOutcome.InProgress && tint == null) {
        Spinner()
        return
    }
    val trouble = tint ?: status.trouble
    val success = tint ?: status.success
    Canvas(Modifier.size(StatusIconSize)) {
        val u = size.width / STATUS_ICON_GRID
        val stroke = Stroke(STATUS_ICON_STROKE * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val ringTopLeft = Offset(3 * u, 3 * u)
        val ringSize = Size(18 * u, 18 * u)
        fun line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), stroke.width, StrokeCap.Round)
        when (outcome) {
            // Only reached dimmed, for a run that was still going when updates stopped: a bare ring.
            is DeployOutcome.InProgress -> drawOval(trouble, ringTopLeft, ringSize, style = stroke)
            is DeployOutcome.Failed -> {
                drawOval(trouble, ringTopLeft, ringSize, style = stroke)
                line(trouble, 8.5f, 8.5f, 15.5f, 15.5f)
                line(trouble, 15.5f, 8.5f, 8.5f, 15.5f)
            }
            is DeployOutcome.Passed -> {
                drawOval(success, ringTopLeft, ringSize, style = stroke)
                val tick = Path().apply {
                    moveTo(7.5f * u, 12.5f * u)
                    lineTo(10.5f * u, 15.5f * u)
                    lineTo(16.5f * u, 9f * u)
                }
                drawPath(tick, success, style = stroke)
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
