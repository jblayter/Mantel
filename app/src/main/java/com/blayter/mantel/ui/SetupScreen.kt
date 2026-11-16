package com.blayter.mantel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import com.blayter.mantel.data.config.ConfigFile
import com.blayter.mantel.domain.Severity
import com.blayter.mantel.domain.Warning
import com.blayter.mantel.ui.hive.EmptyHive
import com.blayter.mantel.ui.hive.WarningLine
import com.blayter.mantel.ui.panel.HiveCard
import com.blayter.mantel.ui.panel.Spinner
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType

// From the approved "Not set up yet" mockup.
private const val TITLE_LINE_HEIGHT_EM = 1.1f
private const val TEXT_LINE_HEIGHT_EM = 1.4f
private const val DECOR_CELLS = 2

/**
 * Shown until a config file has been pushed. An interim screen: the QR and
 * phone-form setup is not designed yet. Nothing here can be focused or
 * pressed; the app notices the file by itself.
 *
 * @param fileProblem why a pushed file was not used, if one was found and rejected.
 */
@Composable
fun SetupScreen(fileProblem: String?) {
    HiveFrame(showDay = false) {
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(HiveTokens.Screen.columnGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.width(HiveTokens.Screen.hiveColumnWidth),
                verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s24),
            ) {
                Text(
                    "This display isn’t set up yet",
                    style = HiveType.displayL.copy(lineHeight = TITLE_LINE_HEIGHT_EM.em),
                    color = HiveTokens.Color.textBright,
                )
                Text(
                    "Mantel needs the address of your CI box. From a computer on the same network, " +
                        "push a config file to this TV:",
                    style = HiveType.lead.copy(lineHeight = TEXT_LINE_HEIGHT_EM.em),
                    color = HiveTokens.Color.textSecondary,
                )
                HiveCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(horizontal = HiveTokens.Card.paddingX, vertical = HiveTokens.Card.paddingY),
                        verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s8),
                    ) {
                        Text("Run these on your computer", style = HiveType.bodyBold, color = HiveTokens.Color.textMuted)
                        val command = HiveType.body.copy(lineHeight = TEXT_LINE_HEIGHT_EM.em)
                        Text(ConfigFile.PUSH_COMMAND, style = command, color = HiveTokens.Color.textBright)
                        Text(ConfigFile.INSTALL_COMMAND, style = command, color = HiveTokens.Color.textBright)
                    }
                }
                if (fileProblem != null) {
                    WarningLine(Warning(ConfigFile.NAME, "was not used: $fileProblem", Severity.Wrong))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HiveTokens.Space.s16),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spinner()
                    Text(
                        "Looking for the file every few seconds. Mantel starts on its own when it arrives.",
                        color = HiveTokens.Color.textSecondary,
                    )
                }
                Text("The README shows what goes in the file.", color = HiveTokens.Color.textMuted)
            }
            // Decoration only: a corner of empty honeycomb, where the hive will be.
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { EmptyHive(DECOR_CELLS, DECOR_CELLS) }
        }
    }
}
