package com.blayter.mantel.ui.hive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.blayter.mantel.domain.ContainerStatus
import com.blayter.mantel.domain.HiveSort
import com.blayter.mantel.domain.MAX_WARNINGS
import com.blayter.mantel.domain.Severity
import com.blayter.mantel.domain.StaleReading
import com.blayter.mantel.domain.formatAge
import com.blayter.mantel.domain.Warning
import com.blayter.mantel.domain.sortForHive
import com.blayter.mantel.domain.warningsFor
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType
import java.time.format.DateTimeFormatter

// Sizes from the approved mockup that are not in tokens.json.
val CaptionRowHeight = 44.dp
private val ChipPaddingX = 18.dp
private val ChipPaddingY = 4.dp
private val WarningRowHeight = 38.dp
private val WarningIconSize = 28.dp
private val WarningIconGap = 14.dp
private val ReadingTime = DateTimeFormatter.ofPattern("h:mm")

/**
 * Left column: caption and sort chips, the honeycomb, then up to three warnings.
 * While the CI box is not answering ([stale]), the cells show the last reading
 * dimmed and the warnings give way to the one that matters.
 */
@Composable
fun HiveSection(
    containers: List<ContainerStatus>,
    hostName: String,
    stale: StaleReading?,
    modifier: Modifier = Modifier,
    /** Smaller hive and fewer warning lines, when the machine shelf needs the room. */
    hiveScale: Float = 1f,
    maxWarnings: Int = MAX_WARNINGS,
) {
    var sort by rememberSaveable { mutableStateOf(HiveSort.Busiest) }
    val sorted = remember(containers, sort) { sortForHive(containers, sort) }
    val warnings = remember(containers, maxWarnings) {
        warningsFor(containers, HiveTokens.ThresholdsProposed.memoryNearCapPercent, maxWarnings)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s16)) {
        Row(
            Modifier.fillMaxWidth().height(CaptionRowHeight),
            horizontalArrangement = Arrangement.spacedBy(HiveTokens.Space.s16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The chips keep their full width; the caption gives way if the row is tight.
            Text(
                "The hive · one cell per container, glow = cores in use",
                Modifier.weight(1f),
                style = HiveType.leadBold,
                color = HiveTokens.Color.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(HiveTokens.Space.s8)) {
                HiveSort.entries.forEach { SortChip(it.label, selected = it == sort) { sort = it } }
            }
        }
        HiveGrid(sorted, stale = stale != null, scale = hiveScale)
        Column(verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s8)) {
            if (stale == null) {
                warnings.forEach { WarningLine(it) }
            } else {
                WarningLine(Warning(hostName, "has not answered for ${formatAge(stale.unansweredFor)}", Severity.Wrong))
                Text(
                    "Showing the last reading, from ${stale.lastReadingAt.format(ReadingTime)}. Trying again every few seconds.",
                    Modifier.padding(start = WarningIconSize + WarningIconGap),
                    color = HiveTokens.Color.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Changes only how cells are ordered on screen; it never touches a container. */
@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(if (selected) HiveTokens.Color.amber else HiveTokens.Color.surface)
            .border(
                HiveTokens.Focus.borderWidth,
                if (focused) HiveTokens.Focus.color else Color.Transparent,
                CircleShape,
            )
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = ChipPaddingX, vertical = ChipPaddingY),
    ) {
        Text(
            label,
            style = HiveType.bodyBold,
            color = if (selected) HiveTokens.Color.textOnAmber else HiveTokens.Color.textSoft,
        )
    }
}

@Composable
fun WarningLine(warning: Warning) {
    val color = when (warning.severity) {
        Severity.Busy -> HiveTokens.Color.amber
        Severity.Wrong -> HiveTokens.Color.Status.trouble
    }
    Row(
        Modifier.height(WarningRowHeight),
        horizontalArrangement = Arrangement.spacedBy(WarningIconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WarningTriangle(color)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(warning.who) }
                append(" ")
                append(warning.what)
            },
            style = HiveType.body,
            color = HiveTokens.Color.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The mockup's warning icon: a triangle with an exclamation mark, on a 24-unit grid. */
@Composable
private fun WarningTriangle(color: Color) {
    Canvas(Modifier.size(WarningIconSize)) {
        val u = size.width / 24f
        val stroke = Stroke(2.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
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
