package com.blayter.mantel.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * The Hive theme: walnut ground, cream body text in DM Sans, and a density
 * override so the screen is always the design width (1920) in dp. One design
 * px is then one dp: 1:1 with the mockup on a 1080p panel, scaled evenly on
 * 4K. Font scale is fixed at 1 so a system font-size setting cannot break the
 * fixed layout.
 */
@Composable
fun MantelTheme(content: @Composable () -> Unit) {
    val c = HiveTokens.Color
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = c.amber,
            onPrimary = c.textOnAmber,
            background = c.ground,
            onBackground = c.text,
            surface = c.surface,
            onSurface = c.text,
            onSurfaceVariant = c.textMuted,
            border = c.surfaceBorder,
            error = HiveTokens.Color.Status.trouble,
        ),
    ) {
        var widthPx by remember { mutableIntStateOf(0) }
        Box(
            Modifier
                .fillMaxSize()
                .background(c.ground)
                .onSizeChanged { widthPx = it.width },
        ) {
            if (widthPx > 0) {
                CompositionLocalProvider(
                    LocalDensity provides Density(widthPx / HiveTokens.Screen.width.value, fontScale = 1f),
                    LocalContentColor provides c.text,
                    LocalTextStyle provides HiveType.body,
                    content = content,
                )
            }
        }
    }
}
