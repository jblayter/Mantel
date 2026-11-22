package com.blayter.mantel.ui.theme

import android.content.Context

/**
 * Remembers which theme this display uses. A plain preference, not the
 * encrypted config: a color choice is not a secret, and it survives a config
 * pushed by file. "Start over" forgets it.
 */
class ThemeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mantel_theme", Context.MODE_PRIVATE)

    /** Puts the stored theme in use. Call before the first frame. */
    fun apply() {
        ActiveTheme.theme = themeById(prefs.getString(KEY, null))
    }

    /** Uses [theme] now and from now on. */
    fun choose(theme: HiveTheme) {
        ActiveTheme.theme = theme
        prefs.edit().putString(KEY, theme.id).apply()
    }

    /** Back to the default theme. */
    fun forget() {
        prefs.edit().remove(KEY).apply()
        ActiveTheme.theme = HiveThemes.default
    }

    private companion object {
        const val KEY = "theme"
    }
}
