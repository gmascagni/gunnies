package com.bc1800.ballistics.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure OLED red-spectrum filter so raster hardware renders emit zero green/blue light
 * when Night Red mode is active.
 */
private val NightRedRasterFilter: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.35f, 0.55f, 0.10f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

/** Creates a 45-degree CNC-chamfered instrument bezel shape. */
@Composable
fun rememberChamferedHudShape(cutSize: Dp = 10.dp): Shape {
    val cutPx = with(LocalDensity.current) { cutSize.toPx() }
    return remember(cutPx) {
        GenericShape { size, _ ->
            val c = cutPx.coerceAtMost(minOf(size.width, size.height) * 0.35f)
            moveTo(c, 0f)
            lineTo(size.width - c, 0f)
            lineTo(size.width, c)
            lineTo(size.width, size.height - c)
            lineTo(size.width - c, size.height)
            lineTo(c, size.height)
            lineTo(0f, size.height - c)
            lineTo(0f, c)
            close()
        }
    }
}

/**
 * Machined gunnery fire-control HUD bezel with 45° chamfered corners and corner reticle brackets.
 */
@Composable
fun GunneryHudPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    bracketColor: Color = borderColor,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    cutSize: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = rememberChamferedHudShape(cutSize)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shape = shape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val b = 9.dp.toPx()
                    val stroke = 1.6.dp.toPx()
                    // Subtle top specular instrument hairline
                    drawLine(
                        color = bracketColor.copy(alpha = 0.28f),
                        start = Offset(b, 1.dp.toPx()),
                        end = Offset(size.width - b, 1.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                    // Corner calibration ticks
                    drawLine(bracketColor, Offset(0f, b * 1.4f), Offset(0f, b * 0.7f), stroke)
                    drawLine(bracketColor, Offset(size.width, b * 1.4f), Offset(size.width, b * 0.7f), stroke)
                    drawLine(bracketColor, Offset(0f, size.height - b * 1.4f), Offset(0f, size.height - b * 0.7f), stroke)
                    drawLine(bracketColor, Offset(size.width, size.height - b * 1.4f), Offset(size.width, size.height - b * 0.7f), stroke)
                }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/**
 * High-precision mechanical Elevation / Windage Turret Drum gauge with live-rotating knurled ribs
 * and tabular numeric readout.
 */
@Composable
fun TurretDialGauge(
    modifier: Modifier = Modifier,
    title: String,
    direction: String,
    value: String,
    unitLabel: String,
    clicks: Double,
    subLabel: String,
    accentColor: Color,
    isElevation: Boolean
) {
    val pointerPath = remember { Path() }
    val drumDark = MaterialTheme.colorScheme.surfaceVariant
    val drumEdge = MaterialTheme.colorScheme.outline
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f)),
        shape = rememberChamferedHudShape(7.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top strip: label + discrete click badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = accentColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    val arrow = when {
                        abs(clicks) < 0.5 -> "•"
                        isElevation && clicks > 0 -> "▲"
                        isElevation -> "▼"
                        clicks > 0 -> "▶"
                        else -> "◀"
                    }
                    Text(
                        text = "$arrow ${abs(clicks).roundToInt()} CLK",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

            // Machined Knurled Rotary Turret Drum Strip
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
            ) {
                val w = size.width
                val h = size.height
                val drumTop = 6.dp.toPx()
                val drumH = h - drumTop

                // Cylindrical metallic sheen gradient
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            drumDark.copy(alpha = 0.9f),
                            accentColor.copy(alpha = 0.16f),
                            drumDark.copy(alpha = 0.9f)
                        )
                    ),
                    topLeft = Offset(0f, drumTop),
                    size = Size(w, drumH)
                )
                drawRect(
                    color = drumEdge.copy(alpha = 0.7f),
                    topLeft = Offset(0f, drumTop),
                    size = Size(w, 1.dp.toPx())
                )

                // Moving knurling & turret ticks synchronized to click count
                val spacing = 10.dp.toPx()
                val phase = ((clicks * 3.5f) % spacing).toFloat().let { if (it < 0f) it + spacing else it }
                var x = phase
                var tickIdx = 0
                while (x < w) {
                    val isMajor = tickIdx % 3 == 0
                    val tickTop = if (isMajor) drumTop + 1.dp.toPx() else drumTop + drumH * 0.35f
                    drawLine(
                        color = if (isMajor) accentColor.copy(alpha = 0.72f) else drumEdge.copy(alpha = 0.55f),
                        start = Offset(x, tickTop),
                        end = Offset(x, h - 1.dp.toPx()),
                        strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    x += spacing
                    tickIdx++
                }

                // Glowing 12-o'clock index witness pointer above the drum
                val cx = w / 2f
                val triW = 4.dp.toPx()
                pointerPath.rewind()
                pointerPath.moveTo(cx - triW, 0f)
                pointerPath.lineTo(cx + triW, 0f)
                pointerPath.lineTo(cx, drumTop + 1.dp.toPx())
                pointerPath.close()
                drawPath(pointerPath, color = accentColor)
            }

            // Primary Ballistic Readout (tabular numerals)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (direction.isNotEmpty() && direction != "ZERO") {
                    Text(
                        text = direction,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = unitLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall,
                color = textMuted,
                maxLines = 1
            )
        }
    }
}

/**
 * Displays a generated studio hardware PNG framed in a chamfered anodized bezel,
 * preserving OLED Night-Red compliance automatically.
 */
@Composable
fun HardwareThumbBadge(
    @DrawableRes drawableResId: Int,
    contentDescription: String,
    isNightRed: Boolean,
    size: Dp = 52.dp,
    accentColor: Color = MaterialTheme.colorScheme.outlineVariant,
    modifier: Modifier = Modifier
) {
    val shape = rememberChamferedHudShape(7.dp)
    Surface(
        modifier = modifier.size(size),
        color = if (isNightRed) Color(0xFF0A0101) else Color(0xFF080B09),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.65f)),
        shape = shape
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(if (isNightRed) Color(0xFF080101) else Color(0xFF080B09)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = drawableResId),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                colorFilter = if (isNightRed) NightRedRasterFilter else null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
