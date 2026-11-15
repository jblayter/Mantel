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

/** Craftsman card: surface fill, solid wood-toned border, small corners. No shadow, no gradient. */
@Composable
fun HiveCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(HiveTokens.Card.radius)
    Box(
        modifier
            .clip(shape)
            .background(HiveTokens.Color.surface)
            .border(HiveTokens.Card.borderWidth, HiveTokens.Color.surfaceBorder, shape),
        content = content,
    )
}
