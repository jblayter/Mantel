package com.blayter.mantel.ui.setup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.blayter.mantel.ui.theme.HiveTokens
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

// Sizes from the approved setup boards that are not in tokens.json.
private val QrSize = 374.dp
private val QrQuietMargin = 22.dp
private val QrCaptionGap = 18.dp
private val EmptyTileBorder = 4.dp
private val EmptyTilePadding = 40.dp

/**
 * A QR code to scan with a phone, and under it the same address in words for
 * anyone who would rather type it. The one large light area Mantel allows,
 * and only during setup: the ground is the secondary text color, not white.
 *
 * @param address what the code holds, a full `https://` address.
 * @param caption the address as a person would type it, or null where the words beside the code say it.
 * @param size the side of the tile; the quiet margin keeps its share of it.
 */
@Composable
fun QrTile(address: String, caption: String?, size: Dp = QrSize) {
    val matrix = remember(address) {
        runCatching {
            QRCodeWriter().encode(
                address,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
            )
        }.getOrNull()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(QrCaptionGap)) {
        if (matrix == null) {
            EmptyQrTile("Open the address below instead")
        } else {
            Canvas(
                Modifier
                    .size(size)
                    .clip(RoundedCornerShape(HiveTokens.Card.radius))
                    .background(HiveTokens.Color.textSecondary),
            ) {
                val margin = this.size.width * (QrQuietMargin / QrSize)
                val cell = (this.size.width - 2 * margin) / matrix.width
                for (y in 0 until matrix.height) {
                    var x = 0
                    while (x < matrix.width) {
                        if (!matrix[x, y]) {
                            x++
                            continue
                        }
                        // One rectangle per run of dark squares, so no seams show between them.
                        var end = x
                        while (end + 1 < matrix.width && matrix[end + 1, y]) end++
                        drawRect(
                            HiveTokens.Color.ground,
                            topLeft = Offset(margin + x * cell, margin + y * cell),
                            // A hair taller than a square, so rows meet.
                            size = Size((end - x + 1) * cell, cell + 0.5f),
                        )
                        x = end + 1
                    }
                }
            }
        }
        if (caption != null) Text(caption, color = HiveTokens.Color.textMuted)
    }
}

/** Where the QR code would be when there is nothing to scan: a dashed tile that says why. */
@Composable
fun EmptyQrTile(text: String) {
    Box(
        Modifier
            .size(QrSize)
            .drawBehind {
                val stroke = EmptyTileBorder.toPx()
                drawRoundRect(
                    HiveTokens.Color.Cell.strokeEnded,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(HiveTokens.Card.radius.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(HiveTokens.Hex.dash.map { it.toPx() }.toFloatArray())),
                )
            }
            .padding(EmptyTilePadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = HiveTokens.Color.textDim, textAlign = TextAlign.Center)
    }
}
