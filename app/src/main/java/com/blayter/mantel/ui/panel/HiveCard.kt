package com.blayter.mantel.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.blayter.mantel.ui.theme.HiveTokens

/** How long the cards take to resize, and their text to fade, when the deploys card changes state. */
val ModeChangeMillis = (HiveTokens.Motion.modeChangeSeconds * 1000).toInt()

/**
 * Craftsman card: surface fill, solid wood-toned border, small corners. No shadow, no gradient.
 *
 * @param focused draws the border in the focus color.
 */
@Composable
fun HiveCard(modifier: Modifier = Modifier, focused: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(HiveTokens.Card.radius)
    Box(
        modifier
            .clip(shape)
            .background(HiveTokens.Color.surface)
            .border(HiveTokens.Card.borderWidth, if (focused) HiveTokens.Focus.color else HiveTokens.Color.surfaceBorder, shape),
        content = content,
    )
}
