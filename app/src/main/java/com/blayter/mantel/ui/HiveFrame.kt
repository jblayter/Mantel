package com.blayter.mantel.ui

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Gaps between baseline-aligned header items, from the approved mockup.
private val HeaderLeftGap = HiveTokens.Space.s20
private val HeaderRightGap = HiveTokens.Space.s24

/**
 * What every Mantel screen shares: the overscan-safe margin, the slow drift
 * that protects the panel, and the header with the wordmark and clock.
 *
 * @param subtitle beside the wordmark: the CI box, once there is one to name.
 * @param showDay the weekday beside the clock, with [dayNote] after it if set.
 */
@Composable
fun HiveFrame(
    subtitle: String? = null,
    showDay: Boolean = true,
    dayNote: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ScreenFrame {
        Header(subtitle, showDay, dayNote)
        content()
    }
}

/**
 * The part of the frame every layout shares: the overscan-safe margin and the
 * slow drift that protects the panel. Each layout draws its own header in it.
 */
@Composable
fun ScreenFrame(
    gap: Dp = HiveTokens.Space.s24,
    content: @Composable ColumnScope.() -> Unit,
) {
    val drift by rememberInfiniteTransition(label = "burn-in").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(HiveTokens.Motion.driftSeconds * 1000, easing = EaseInOut),
            RepeatMode.Reverse,
        ),
        label = "drift",
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = HiveTokens.Screen.overscanX, vertical = HiveTokens.Screen.overscanY)
            .graphicsLayer {
                translationX = HiveTokens.Motion.driftPx[0].toPx() * drift
                translationY = HiveTokens.Motion.driftPx[1].toPx() * drift
            },
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

@Composable
private fun Header(subtitle: String?, showDay: Boolean, dayNote: String?) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1000)
        }
    }
    Row(
        Modifier.fillMaxWidth().height(HiveTokens.Screen.headerHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(HeaderLeftGap)) {
            Text("Mantel", Modifier.alignByBaseline(), style = HiveType.wordmark, color = HiveTokens.Color.amber)
            if (subtitle != null) {
                Text(subtitle, Modifier.alignByBaseline(), style = HiveType.lead, color = HiveTokens.Color.textMuted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(HeaderRightGap)) {
            if (showDay) {
                val day = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                Text(
                    if (dayNote != null) "$day · $dayNote" else day,
                    Modifier.alignByBaseline(),
                    color = HiveTokens.Color.textMuted,
                )
            }
            Text(now.format(ClockFormat), Modifier.alignByBaseline(), style = HiveType.clock)
        }
    }
}

private val ClockFormat = DateTimeFormatter.ofPattern("h:mm")
