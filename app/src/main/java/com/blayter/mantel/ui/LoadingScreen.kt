package com.blayter.mantel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.blayter.mantel.domain.DeployStatus
import com.blayter.mantel.ui.hive.CaptionRowHeight
import com.blayter.mantel.ui.hive.EmptyHive
import com.blayter.mantel.ui.hive.HiveHeight
import com.blayter.mantel.ui.panel.CountCardPlaceholder
import com.blayter.mantel.ui.panel.DeploysCard
import com.blayter.mantel.ui.panel.HostChartsPlaceholder
import com.blayter.mantel.ui.panel.Spinner
import com.blayter.mantel.ui.panel.StatusIconSize
import com.blayter.mantel.ui.theme.HiveTokens
import com.blayter.mantel.ui.theme.HiveType

// From the approved "Waiting for the first reading" mockup.
private val SpinnerGap = 14.dp
private const val EMPTY_ROWS = 3

/**
 * Shown from the moment there is a config until the CI box first answers:
 * the dashboard's own layout with nothing in it yet.
 *
 * @param address where Mantel is asking, as written in the config file.
 */
@Composable
fun LoadingScreen(address: String) {
    HiveFrame(subtitle = address) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(HiveTokens.Screen.columnGap)) {
            Column(
                Modifier.width(HiveTokens.Screen.hiveColumnWidth),
                verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s16),
            ) {
                Box(Modifier.height(CaptionRowHeight), contentAlignment = Alignment.CenterStart) {
                    Text("The hive · one cell per container", style = HiveType.leadBold, color = HiveTokens.Color.textMuted)
                }
                Box(Modifier.height(HiveHeight)) { EmptyHive(EMPTY_ROWS, HiveTokens.Hex.perRow) }
                Row(
                    Modifier.height(StatusIconSize),
                    horizontalArrangement = Arrangement.spacedBy(SpinnerGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spinner()
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = HiveTokens.Color.amber, fontWeight = FontWeight.Bold)) { append("Waiting") }
                            append(" for the first reading from $address")
                        },
                        color = HiveTokens.Color.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HiveTokens.Space.s20)) {
                CountCardPlaceholder()
                HostChartsPlaceholder(Modifier.height(HostChartsHeight))
                DeploysCard(emptyList(), Modifier.weight(1f), status = DeployStatus.Waiting)
            }
        }
    }
}
