package com.blayter.mantel.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The theme in use. `HiveTokens.Color` reads [colors] on every access, and
 * this is Compose state, so everything drawn with a theme color redraws when
 * the theme changes. Set it through [ThemeStore], which also remembers it.
 */
object ActiveTheme {
    var theme: HiveTheme by mutableStateOf(HiveThemes.default)

    val colors: HiveColors get() = theme.colors
}

/** The theme stored as [id], or the default when there is none or it no longer exists. */
fun themeById(id: String?, themes: List<HiveTheme> = HiveThemes.all): HiveTheme =
    themes.firstOrNull { it.id == id } ?: themes.first()

/** The layouts that have at least one theme, in the order [MantelLayout] lists them. */
fun layoutsOf(themes: List<HiveTheme> = HiveThemes.all): List<MantelLayout> = themes.map { it.layout }.distinct().sortedBy { it.ordinal }

/** The themes made for [layout]: the one named after the layout first, then in the order of [themes]. */
fun themesFor(layout: MantelLayout, themes: List<HiveTheme> = HiveThemes.all): List<HiveTheme> =
    themes.filter { it.layout == layout }.sortedBy { if (it.id == layout.id) 0 else 1 }

/**
 * The theme a layout opens on when it is chosen: the one whose id is the
 * layout's id (hive.json, noc.json, neon.json), or else its first theme.
 */
fun defaultThemeFor(layout: MantelLayout, themes: List<HiveTheme> = HiveThemes.all): HiveTheme? {
    val mine = themesFor(layout, themes)
    return mine.firstOrNull { it.id == layout.id } ?: mine.firstOrNull()
}
