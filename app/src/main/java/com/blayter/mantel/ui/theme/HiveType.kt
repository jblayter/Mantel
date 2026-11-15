package com.blayter.mantel.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.blayter.mantel.R

/**
 * Hive type styles. Sizes and weights come from tokens.json. Both fonts are
 * bundled variable fonts, set up as the mockup loads them: Fraunces follows
 * its optical-size axis (Android does not do that on its own, so each style
 * pins `opsz` to its own size) and DM Sans stays at its default optical size.
 */
@OptIn(ExperimentalTextApi::class)
object HiveType {
    private val regular = HiveTokens.Font.Weight.regular
    private val bold = HiveTokens.Font.Weight.bold

    private fun family(resId: Int, opticalSize: Float?) = FontFamily(
        listOf(regular, bold).map { weight ->
            val settings = listOfNotNull(
                FontVariation.weight(weight.weight),
                opticalSize?.let { FontVariation.Setting("opsz", it) },
            )
            Font(resId, weight, variationSettings = FontVariation.Settings(*settings.toTypedArray()))
        },
    )

    /** Fraunces with tabular numerals: wordmark, clock and every key number. */
    private fun display(size: TextUnit, weight: FontWeight = bold) = TextStyle(
        fontFamily = family(R.font.fraunces, size.value.coerceIn(9f, 144f)),
        fontWeight = weight,
        fontSize = size,
        fontFeatureSettings = "tnum",
    )

    private val dmSans = family(R.font.dm_sans, opticalSize = null)

    /** DM Sans: everything else. */
    private fun text(size: TextUnit, weight: FontWeight = regular) =
        TextStyle(fontFamily = dmSans, fontWeight = weight, fontSize = size)

    val displayXl = display(HiveTokens.TypeScale.displayXl)
    val displayL = display(HiveTokens.TypeScale.displayL)
    val displayM = display(HiveTokens.TypeScale.displayM)
    val title = text(HiveTokens.TypeScale.title, bold)
    val lead = text(HiveTokens.TypeScale.lead)
    val body = text(HiveTokens.TypeScale.body)
    val cellLabel = text(HiveTokens.TypeScale.cellLabel, bold)
    val cellLabelSmall = text(HiveTokens.TypeScale.cellLabelSmall)

    // Mockup-specific sizes that sit beside the main scale.
    val wordmark = display(HiveTokens.TypeScale.wordmark)
    val deployTimer = display(HiveTokens.TypeScale.deployTimer)
    val hostNumber = display(HiveTokens.TypeScale.hostNumber)
    val caption = text(HiveTokens.TypeScale.caption)

    val clock = displayL.copy(fontWeight = regular)
    val leadBold = lead.copy(fontWeight = bold)
    val bodyBold = body.copy(fontWeight = bold)
    val cellLabelSmallBold = cellLabelSmall.copy(fontWeight = bold)
}
