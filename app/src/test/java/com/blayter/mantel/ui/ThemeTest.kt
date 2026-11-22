package com.blayter.mantel.ui

import com.blayter.mantel.ui.theme.HiveThemes
import com.blayter.mantel.ui.theme.MantelLayout
import com.blayter.mantel.ui.theme.defaultThemeFor
import com.blayter.mantel.ui.theme.layoutsOf
import com.blayter.mantel.ui.theme.themeById
import com.blayter.mantel.ui.theme.themesFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The themes themselves (every color role, a dark ground, readable text) are
 * checked by `generateHiveTokens` when the app is built; these are the rules
 * for choosing one.
 */
class ThemeTest {
    @Test
    fun theDefaultComesFirst() {
        assertEquals("hive", HiveThemes.default.id)
        assertEquals(HiveThemes.default, HiveThemes.all.first())
    }

    @Test
    fun idsAreUnique() {
        val ids = HiveThemes.all.map { it.id }
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun aStoredThemeIsFoundById() {
        for (theme in HiveThemes.all) assertEquals(theme, themeById(theme.id))
    }

    @Test
    fun anUnknownOrMissingThemeFallsBackToTheDefault() {
        assertEquals(HiveThemes.default, themeById(null))
        assertEquals(HiveThemes.default, themeById("a-theme-that-was-removed"))
    }

    @Test
    fun theExampleThemesAreBuiltIn() {
        val ids = HiveThemes.all.map { it.id }.toSet()
        assertTrue(ids.containsAll(setOf("hive", "moss", "nightfall", "noc", "neon")))
        assertTrue(ids.containsAll(setOf("noc-amber", "noc-contrast", "noc-solar", "neon-synthwave", "neon-ice", "neon-phosphor")))
    }

    @Test
    fun eachNewLayoutHasAtLeastThreeThemes() {
        for (layout in listOf(MantelLayout.NOC, MantelLayout.NEON)) assertTrue(themesFor(layout).size >= 3)
    }

    @Test
    fun aLayoutListsItsOwnThemeFirst() {
        for (layout in MantelLayout.entries) assertEquals(layout.id, themesFor(layout).first().id)
    }

    @Test
    fun everyLayoutHasATheme() {
        assertEquals(MantelLayout.entries.toSet(), layoutsOf().toSet())
        for (layout in MantelLayout.entries) assertTrue(themesFor(layout).all { it.layout == layout })
    }

    @Test
    fun aLayoutOpensOnTheThemeNamedForIt() {
        for (layout in MantelLayout.entries) assertEquals(layout.id, defaultThemeFor(layout)!!.id)
    }

    @Test
    fun aLayoutWithoutAThemeNamedForItOpensOnItsFirst() {
        val themes = HiveThemes.all.filter { it.id != "hive" }
        assertEquals(themesFor(MantelLayout.HIVE, themes).first(), defaultThemeFor(MantelLayout.HIVE, themes))
        assertEquals(null, defaultThemeFor(MantelLayout.NOC, themes.filter { it.layout != MantelLayout.NOC }))
    }
}
