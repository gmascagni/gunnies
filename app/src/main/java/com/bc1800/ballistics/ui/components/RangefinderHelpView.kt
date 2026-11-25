package com.bc1800.ballistics.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bc1800.ballistics.R
import kotlin.math.hypot
import kotlin.math.min

data class RangefinderIndicatorItem(
    val number: Int,
    val title: String,
    val badge: String,
    val description: String,
    val normX: Float,
    val normY: Float,
    val pinOffsetX: Float = 0f,
    val pinOffsetY: Float = 0f
)

private val Lbc1800Indicators: List<RangefinderIndicatorItem> = listOf(
    RangefinderIndicatorItem(
        number = 1,
        title = "Bow Operating Mode",
        badge = "BOW",
        description = "Indicates Bow / ARC mode is active, outputting true horizontal distance adjusted for uphill or downhill angle.",
        normX = -0.46f,
        normY = -0.58f,
        pinOffsetX = -0.14f,
        pinOffsetY = -0.12f
    ),
    RangefinderIndicatorItem(
        number = 2,
        title = "Battery Level Indicator",
        badge = "CR2 BATT",
        description = "Shows remaining CR2 3V lithium battery charge (Full, 3/4, 1/2, 1/4). A blinking battery icon means replace soon.",
        normX = -0.16f,
        normY = -0.74f,
        pinOffsetX = 0f,
        pinOffsetY = -0.14f
    ),
    RangefinderIndicatorItem(
        number = 3,
        title = "Applied Ballistics Mode",
        badge = "AB",
        description = "Applied Ballistics Ultralite is active. Uses the Gun, Scope, Ammo, Suppressor, and Weather synced from this app out to 800 yd.",
        normX = 0.16f,
        normY = -0.74f,
        pinOffsetX = 0f,
        pinOffsetY = -0.14f
    ),
    RangefinderIndicatorItem(
        number = 4,
        title = "Bullseye™ Targeting Mode",
        badge = "BULL",
        description = "Acquires the closest small target (such as game or a steel plate) while ignoring larger background trees or brush.",
        normX = -0.66f,
        normY = -0.26f,
        pinOffsetX = -0.14f,
        pinOffsetY = -0.12f
    ),
    RangefinderIndicatorItem(
        number = 5,
        title = "Brush™ Targeting Mode",
        badge = "BRUSH",
        description = "Ignores foreground grass, twigs, and branches so the laser only reports distance to the farther target behind cover.",
        normX = 0.66f,
        normY = -0.26f,
        pinOffsetX = 0.14f,
        pinOffsetY = -0.12f
    ),
    RangefinderIndicatorItem(
        number = 6,
        title = "Aiming Circle / Center Dot",
        badge = "RETICLE",
        description = "Center optical aiming circle and dot. Place this directly on the target and press FIRE to range.",
        normX = 0f,
        normY = 0f,
        pinOffsetX = -0.17f,
        pinOffsetY = -0.11f
    ),
    RangefinderIndicatorItem(
        number = 7,
        title = "Active / Scan Laser Crosshairs",
        badge = "SCAN",
        description = "Four outer crosshairs outside the aiming circle. They flash whenever the laser fires or sweeps targets during continuous SCAN.",
        normX = 0.24f,
        normY = -0.22f,
        pinOffsetX = 0.13f,
        pinOffsetY = -0.10f
    ),
    RangefinderIndicatorItem(
        number = 8,
        title = "Primary Numeric Display",
        badge = "425",
        description = "Displays the main Line-of-Sight (LOS) distance to the ranged target (e.g., 425 yards).",
        normX = -0.02f,
        normY = 0.30f,
        pinOffsetX = -0.26f,
        pinOffsetY = -0.02f
    ),
    RangefinderIndicatorItem(
        number = 9,
        title = "MOA Holdover Units",
        badge = "MOA",
        description = "Secondary ballistic holdover readout is displayed in Minutes of Angle (MOA).",
        normX = 0.50f,
        normY = 0.45f,
        pinOffsetX = 0.15f,
        pinOffsetY = -0.08f
    ),
    RangefinderIndicatorItem(
        number = 10,
        title = "MIL Holdover Units",
        badge = "MIL",
        description = "Secondary ballistic holdover readout is displayed in Milliradians (MRAD / MIL).",
        normX = 0.36f,
        normY = 0.66f,
        pinOffsetX = 0.15f,
        pinOffsetY = 0.08f
    ),
    RangefinderIndicatorItem(
        number = 11,
        title = "Range Units (Yards / Meters)",
        badge = "Y / M",
        description = "Shows whether primary and true horizontal range numbers are in Yards (Y) or Meters (M).",
        normX = 0.36f,
        normY = 0.28f,
        pinOffsetX = 0.14f,
        pinOffsetY = -0.08f
    ),
    RangefinderIndicatorItem(
        number = 12,
        title = "Secondary Numeric Display",
        badge = "U 2.4",
        description = "Shows elevation holdover (e.g., U 2.4 MOA/MIL) in Applied Ballistics mode, or ARC True Horizontal Distance in Bow/Regular mode.",
        normX = 0.02f,
        normY = 0.56f,
        pinOffsetX = 0f,
        pinOffsetY = 0.17f
    ),
    RangefinderIndicatorItem(
        number = 13,
        title = "Setup Mode Indicator",
        badge = "SETUP",
        description = "Illuminates when holding the Mode button to configure units, brightness, Bow/AB mode, or Bullseye/Brush settings.",
        normX = -0.62f,
        normY = 0.24f,
        pinOffsetX = -0.14f,
        pinOffsetY = -0.12f
    ),
    RangefinderIndicatorItem(
        number = 14,
        title = "Angle Indicator",
        badge = "-14°",
        description = "Built-in inclinometer reading in degrees (+ uphill / - downhill), automatically factored into ARC and Applied Ballistics holds.",
        normX = -0.40f,
        normY = 0.52f,
        pinOffsetX = -0.16f,
        pinOffsetY = 0.12f
    ),
    RangefinderIndicatorItem(
        number = 15,
        title = "Bluetooth Indicator",
        badge = "BT",
        description = "Blinks while ready to pair or lights steady when connected to this app to receive profiles (:BP) and weather updates.",
        normX = 0.48f,
        normY = -0.58f,
        pinOffsetX = 0.14f,
        pinOffsetY = -0.12f
    )
)

@Composable
fun RangefinderHelpView(
    isNightRed: Boolean,
    onBack: () -> Unit
) {
    var selectedNumber by rememberSaveable { mutableIntStateOf(6) }
    val selectedItem = remember(selectedNumber) {
        Lbc1800Indicators.firstOrNull { it.number == selectedNumber } ?: Lbc1800Indicators.first()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header strip
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Setup")
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "LBC1800 DISPLAY GUIDE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Tap any eyepiece icon or item below",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 600.dp

            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.95f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        EyepieceDiagramCard(
                            selectedNumber = selectedNumber,
                            selectedItem = selectedItem,
                            isNightRed = isNightRed,
                            onSelectNumber = { selectedNumber = it }
                        )
                        RangefinderHardwareCard(isNightRed = isNightRed)
                        RangefinderQuickControlsCard()
                        Spacer(Modifier.height(12.dp))
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1.05f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(Lbc1800Indicators, key = { it.number }) { item ->
                            IndicatorLegendCard(
                                item = item,
                                isSelected = item.number == selectedNumber,
                                onClick = { selectedNumber = item.number }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "hardware_unit") {
                        RangefinderHardwareCard(isNightRed = isNightRed)
                    }
                    item(key = "diagram") {
                        EyepieceDiagramCard(
                            selectedNumber = selectedNumber,
                            selectedItem = selectedItem,
                            isNightRed = isNightRed,
                            onSelectNumber = { selectedNumber = it }
                        )
                    }

                    item(key = "legend_header") {
                        Text(
                            text = "ALL 15 DISPLAY INDICATORS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(Lbc1800Indicators, key = { it.number }) { item ->
                        IndicatorLegendCard(
                            item = item,
                            isSelected = item.number == selectedNumber,
                            onClick = { selectedNumber = item.number }
                        )
                    }

                    item(key = "quick_guide") {
                        RangefinderQuickControlsCard()
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EyepieceDiagramCard(
    selectedNumber: Int,
    selectedItem: RangefinderIndicatorItem,
    isNightRed: Boolean,
    onSelectNumber: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.RemoveRedEye,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "BONE COLLECTOR 1800 EYEPIECE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "#${selectedItem.number} / 15",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .sizeIn(maxWidth = 340.dp, maxHeight = 340.dp)
                    .fillMaxWidth(0.92f)
                    .aspectRatio(1f)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                RangefinderEyepieceCanvas(
                    selectedNumber = selectedNumber,
                    isNightRed = isNightRed,
                    onSelectNumber = onSelectNumber,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Selected Indicator Detail Callout
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedItem.number}. ${selectedItem.title}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = selectedItem.badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = selectedItem.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RangefinderEyepieceCanvas(
    selectedNumber: Int,
    isNightRed: Boolean,
    onSelectNumber: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnSelect by rememberUpdatedState(onSelectNumber)

    // Hoisted drawing tools (zero per-frame allocations)
    val scratchPath = remember { Path() }
    val lcdBigPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }
    val lcdMedPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }
    val lcdSmallPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }
    val badgePaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }

    val highlightColor = MaterialTheme.colorScheme.primary
    val bezelOuter = if (isNightRed) Color(0xFF1F0606) else Color(0xFF171D1A)
    val lensBgColors = remember(isNightRed) {
        if (isNightRed) {
            listOf(Color(0xFF1D0404), Color(0xFF120202), Color(0xFF060000))
        } else {
            listOf(Color(0xFF28342C), Color(0xFF19221C), Color(0xFF0A0F0C))
        }
    }
    val lcdColor = if (isNightRed) Color(0xFFFF4A4A) else Color(0xFFD6FFE0)
    val lcdMuted = if (isNightRed) Color(0xFFCC3333) else Color(0xFF8BC49A)
    val pinBg = if (isNightRed) Color(0xFF3A0A0A) else Color(0xFF1C2420)
    val pinActiveText = MaterialTheme.colorScheme.onPrimary

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val r = min(size.width, size.height) / 2f * 0.90f
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val nx = (offset.x - cx) / r
                    val ny = (offset.y - cy) / r

                    var bestItem: RangefinderIndicatorItem? = null
                    var bestDist = 0.25f
                    for (item in Lbc1800Indicators) {
                        val iconDist = hypot(nx - item.normX, ny - item.normY)
                        val pinDist = hypot(nx - (item.normX + item.pinOffsetX), ny - (item.normY + item.pinOffsetY))
                        val d = min(iconDist, pinDist)
                        if (d < bestDist) {
                            bestDist = d
                            bestItem = item
                        }
                    }
                    bestItem?.let { latestOnSelect(it.number) }
                }
            }
    ) {
        val outerR = min(size.width, size.height) / 2f
        val r = outerR * 0.90f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(cx, cy)

        // Optical barrel + glass gradient
        drawCircle(color = bezelOuter, radius = outerR, center = center)
        drawCircle(
            brush = Brush.radialGradient(colors = lensBgColors, center = center, radius = r),
            radius = r,
            center = center
        )
        drawCircle(
            color = highlightColor.copy(alpha = 0.45f),
            radius = r,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Font sizing scaled to lens radius
        lcdBigPaint.textSize = r * 0.21f
        lcdMedPaint.textSize = r * 0.135f
        lcdSmallPaint.textSize = r * 0.088f
        badgePaint.textSize = r * 0.072f

        fun pt(nx: Float, ny: Float) = Offset(cx + nx * r, cy + ny * r)

        // Draw each LCD element nicely inside the eyepiece
        fun iconColor(num: Int) = if (num == selectedNumber) highlightColor else lcdColor
        fun mutedOrActive(num: Int) = if (num == selectedNumber) highlightColor else lcdMuted

        // 1. BOW icon
        run {
            val p = pt(-0.46f, -0.58f)
            lcdSmallPaint.color = mutedOrActive(1).toArgb()
            drawContext.canvas.nativeCanvas.drawText("BOW", p.x, p.y + r * 0.03f, lcdSmallPaint)
        }

        // 2. Battery bar icon
        run {
            val p = pt(-0.16f, -0.74f)
            val bw = r * 0.18f
            val bh = r * 0.085f
            val c = iconColor(2)
            drawRect(c, topLeft = Offset(p.x - bw / 2f, p.y - bh / 2f), size = Size(bw, bh), style = Stroke(width = 1.6.dp.toPx()))
            drawRect(c, topLeft = Offset(p.x + bw / 2f, p.y - bh * 0.25f), size = Size(r * 0.025f, bh * 0.5f))
            // 3 charge bars
            for (b in 0..2) {
                drawRect(
                    c,
                    topLeft = Offset(p.x - bw / 2f + r * (0.022f + b * 0.052f), p.y - bh * 0.30f),
                    size = Size(r * 0.036f, bh * 0.60f)
                )
            }
        }

        // 3. AB (Applied Ballistics)
        run {
            val p = pt(0.16f, -0.74f)
            val c = iconColor(3)
            drawRoundRect(
                color = c,
                topLeft = Offset(p.x - r * 0.085f, p.y - r * 0.055f),
                size = Size(r * 0.17f, r * 0.11f),
                style = Stroke(width = 1.4.dp.toPx())
            )
            lcdSmallPaint.color = c.toArgb()
            drawContext.canvas.nativeCanvas.drawText("AB", p.x, p.y + r * 0.032f, lcdSmallPaint)
        }

        // 4. BULL (Bullseye concentric icon)
        run {
            val p = pt(-0.66f, -0.26f)
            val c = mutedOrActive(4)
            drawCircle(c, radius = r * 0.075f, center = p, style = Stroke(width = 1.3.dp.toPx()))
            drawCircle(c, radius = r * 0.03f, center = p)
        }

        // 5. BRUSH (tree/branch silhouette)
        run {
            val p = pt(0.66f, -0.26f)
            val c = mutedOrActive(5)
            val st = 1.8.dp.toPx()
            drawLine(c, Offset(p.x, p.y - r * 0.06f), Offset(p.x, p.y + r * 0.07f), st, cap = StrokeCap.Round)
            drawLine(c, Offset(p.x, p.y), Offset(p.x - r * 0.055f, p.y - r * 0.045f), st, cap = StrokeCap.Round)
            drawLine(c, Offset(p.x, p.y + r * 0.02f), Offset(p.x + r * 0.055f, p.y - r * 0.035f), st, cap = StrokeCap.Round)
        }

        // 6. Central Aiming Circle + Dot
        run {
            val c = iconColor(6)
            drawCircle(c, radius = r * 0.145f, center = center, style = Stroke(width = 1.8.dp.toPx()))
            drawCircle(c, radius = r * 0.024f, center = center)
        }

        // 7. Active / Scan Laser 4 outer crosshairs
        run {
            val c = iconColor(7)
            val innerGap = r * 0.185f
            val outerTip = r * 0.275f
            val st = 2.0.dp.toPx()
            drawLine(c, Offset(cx - outerTip, cy), Offset(cx - innerGap, cy), st, cap = StrokeCap.Round)
            drawLine(c, Offset(cx + innerGap, cy), Offset(cx + outerTip, cy), st, cap = StrokeCap.Round)
            drawLine(c, Offset(cx, cy - outerTip), Offset(cx, cy - innerGap), st, cap = StrokeCap.Round)
            drawLine(c, Offset(cx, cy + innerGap), Offset(cx, cy + outerTip), st, cap = StrokeCap.Round)
        }

        // 8. Primary Numeric Display ("425")
        run {
            val p = pt(-0.02f, 0.30f)
            lcdBigPaint.color = iconColor(8).toArgb()
            drawContext.canvas.nativeCanvas.drawText("425", p.x, p.y + r * 0.07f, lcdBigPaint)
        }

        // 9. MOA indicator
        run {
            val p = pt(0.50f, 0.45f)
            lcdSmallPaint.color = iconColor(9).toArgb()
            drawContext.canvas.nativeCanvas.drawText("MOA", p.x, p.y + r * 0.03f, lcdSmallPaint)
        }

        // 10. MIL indicator
        run {
            val p = pt(0.36f, 0.66f)
            lcdSmallPaint.color = mutedOrActive(10).toArgb()
            drawContext.canvas.nativeCanvas.drawText("MIL", p.x, p.y + r * 0.03f, lcdSmallPaint)
        }

        // 11. Range Units ("Y")
        run {
            val p = pt(0.36f, 0.28f)
            lcdMedPaint.color = iconColor(11).toArgb()
            drawContext.canvas.nativeCanvas.drawText("Y", p.x, p.y + r * 0.045f, lcdMedPaint)
        }

        // 12. Secondary Numeric Display ("U 2.4")
        run {
            val p = pt(0.02f, 0.56f)
            lcdMedPaint.color = iconColor(12).toArgb()
            drawContext.canvas.nativeCanvas.drawText("U 2.4", p.x, p.y + r * 0.045f, lcdMedPaint)
        }

        // 13. SETUP indicator
        run {
            val p = pt(-0.62f, 0.24f)
            lcdSmallPaint.color = mutedOrActive(13).toArgb()
            drawContext.canvas.nativeCanvas.drawText("SETUP", p.x, p.y + r * 0.03f, lcdSmallPaint)
        }

        // 14. Angle Indicator ("-14°")
        run {
            val p = pt(-0.40f, 0.52f)
            lcdMedPaint.color = iconColor(14).toArgb()
            drawContext.canvas.nativeCanvas.drawText("-14°", p.x, p.y + r * 0.045f, lcdMedPaint)
        }

        // 15. Bluetooth Rune Icon
        run {
            val p = pt(0.48f, -0.58f)
            val c = iconColor(15)
            val h = r * 0.075f
            val w = r * 0.045f
            scratchPath.rewind()
            scratchPath.moveTo(p.x - w * 0.6f, p.y - h * 0.45f)
            scratchPath.lineTo(p.x + w, p.y + h * 0.45f)
            scratchPath.lineTo(p.x, p.y + h)
            scratchPath.lineTo(p.x, p.y - h)
            scratchPath.lineTo(p.x + w, p.y - h * 0.45f)
            scratchPath.lineTo(p.x - w * 0.6f, p.y + h * 0.45f)
            drawPath(scratchPath, color = c, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
        }

        // Selected highlight ring around the active LCD icon
        Lbc1800Indicators.firstOrNull { it.number == selectedNumber }?.let { sel ->
            val iconPt = pt(sel.normX, sel.normY)
            drawCircle(
                color = highlightColor.copy(alpha = 0.24f),
                radius = r * 0.14f,
                center = iconPt
            )
            drawCircle(
                color = highlightColor,
                radius = r * 0.14f,
                center = iconPt,
                style = Stroke(width = 1.8.dp.toPx())
            )
        }

        // Numbered Callout Pins (1..15) + short leader ticks
        val pinR = r * 0.068f
        for (item in Lbc1800Indicators) {
            val iconPt = pt(item.normX, item.normY)
            val pinPt = pt(item.normX + item.pinOffsetX, item.normY + item.pinOffsetY)
            val isSel = item.number == selectedNumber

            drawLine(
                color = (if (isSel) highlightColor else lcdMuted).copy(alpha = if (isSel) 0.85f else 0.40f),
                start = iconPt,
                end = pinPt,
                strokeWidth = if (isSel) 1.5.dp.toPx() else 0.9.dp.toPx()
            )
            drawCircle(
                color = if (isSel) highlightColor else pinBg,
                radius = pinR,
                center = pinPt
            )
            drawCircle(
                color = if (isSel) highlightColor else lcdMuted.copy(alpha = 0.65f),
                radius = pinR,
                center = pinPt,
                style = Stroke(width = 1.dp.toPx())
            )
            badgePaint.color = (if (isSel) pinActiveText else lcdColor).toArgb()
            drawContext.canvas.nativeCanvas.drawText(
                item.number.toString(),
                pinPt.x,
                pinPt.y + pinR * 0.36f,
                badgePaint
            )
        }
    }
}

@Composable
private fun IndicatorLegendCard(
    item: RangefinderIndicatorItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.number.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = item.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RangefinderHardwareCard(isNightRed: Boolean) {
    GunneryHudPanel(
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        bracketColor = MaterialTheme.colorScheme.primary
    ) {
        Text(
            text = "BONE COLLECTOR 1800 HARDWARE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HardwareThumbBadge(
                drawableResId = R.drawable.img_rangefinder_bc1800,
                contentDescription = "Bushnell Bone Collector 1800 Rangefinder",
                isNightRed = isNightRed,
                size = 112.dp,
                accentColor = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("• Top FIRE: Power on, range, hold for SCAN", style = MaterialTheme.typography.bodySmall)
                Text("• Side MODE: Cycle Bullseye / Brush / Setup", style = MaterialTheme.typography.bodySmall)
                Text("• Rear Eyepiece: Twist diopter to focus reticle", style = MaterialTheme.typography.bodySmall)
                Text("• Dual Lenses: Laser emitter & receiver optics", style = MaterialTheme.typography.bodySmall)
                Text("• Bottom Cap: CR2 3V lithium battery compartment", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RangefinderQuickControlsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "CONTROLS & APP SYNC QUICK GUIDE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            QuickGuideBullet(
                title = "1. Power & Wake Bluetooth",
                body = "Tap FIRE on the Bone Collector 1800 to wake the display and Bluetooth (#15). Hold FIRE while panning for continuous SCAN (#7)."
            )
            QuickGuideBullet(
                title = "2. Sync Your Active Rig (:BP + :AV)",
                body = "Tap the top-bar BLE pill → Scan BLE → Connect → Sync. This uploads your Gun, Scope, Ammo, Suppressor, and GPS Weather into AB mode (#3)."
            )
            QuickGuideBullet(
                title = "3. Eyepiece Limits vs. Phone Solver",
                body = "The unit's built-in AB solver shows Elevation holds up to 800 yd inside the eyepiece (#12). Use this app's Solver & Reticle for full 1,800 yd holds, Coriolis, Mover leads, and wind trees."
            )
        }
    }
}

@Composable
private fun QuickGuideBullet(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(text = body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
