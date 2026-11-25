package com.gunnies.ballistics.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gunnies.ballistics.data.ReticleDefinitionCodec
import com.gunnies.ballistics.model.BallisticSolution
import com.gunnies.ballistics.model.ReticleType
import com.gunnies.ballistics.model.ScopeSetup
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class ReticleTargetSize(val label: String, val inches: Double) {
    OFF("No Target", 0.0),
    GONG_6("6\" Gong", 6.0),
    PLATE_10("10\" Plate", 10.0),
    IPSC_18("18\" IPSC", 18.0)
}

/**
 * Precision FFP/SFP Optical Scope Reticle Viewer with:
 * - Pinch-to-zoom & discrete magnification chips
 * - HOLD ALL vs. DIAL ELEV / HOLD WIND workflow toggle
 * - True-scale target size overlay (6", 10", 18") at current range & zoom
 * - True OLED Night-Red optical compliance
 * - Zero per-frame Canvas allocations
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReticleViewCard(
    scope: ScopeSetup,
    solution: BallisticSolution,
    isNightRed: Boolean,
    initiallyExpanded: Boolean = true,
    onChangeScopeReticle: (ReticleType) -> Unit
) {
    val reticle = scope.reticleType
    val reticleDef = remember(reticle) { ReticleDefinitionCodec.forReticleType(reticle) }
    var expanded by rememberSaveable(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    var illuminated by remember(reticleDef) { mutableStateOf(reticleDef.illuminatedByDefault) }
    var dialElevationMode by remember { mutableStateOf(false) }
    var targetSize by remember { mutableStateOf(ReticleTargetSize.PLATE_10) }
    var showReticlePicker by remember { mutableStateOf(false) }

    var zoom by remember(scope.id, reticleDef) {
        mutableFloatStateOf(
            if (reticle == ReticleType.EOTECH_HWS_68 &&
                (scope.name.contains("G33", ignoreCase = true) || scope.scopeModel.contains("G33", ignoreCase = true))
            ) 3f else reticleDef.defaultZoom
        )
    }

    val nativeElev = if (reticle.isMradBased) solution.elevationMrad else solution.elevationMoa
    val nativeWind = if (reticle.isMradBased) solution.windageMrad else solution.windageMoa
    val displayedElev = if (dialElevationMode) 0.0 else nativeElev
    val unitTag = if (reticle.isMradBased) "MIL" else "MOA"

    // True angular radius of the selected target plate at solution.rangeYards
    val targetAngularDiameterNative: Float = if (targetSize == ReticleTargetSize.OFF) {
        0f
    } else {
        val moaDiam = (targetSize.inches / (solution.rangeYards.coerceAtLeast(5.0) * 1.0472)) * 100.0
        val milDiam = (targetSize.inches * 27.778) / (solution.rangeYards.coerceAtLeast(5.0) * 100.0)
        (if (reticle.isMradBased) milDiam else moaDiam).toFloat()
    }

    GunneryHudPanel(
        borderColor = MaterialTheme.colorScheme.outline,
        bracketColor = MaterialTheme.colorScheme.primary
    ) {
            // Header (tap left title area or chevron to expand/collapse)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CenterFocusStrong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "${reticleDef.focalPlane.label} RETICLE • ${reticle.label.uppercase(Locale.US)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = scope.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        onClick = { illuminated = !illuminated },
                        shape = RoundedCornerShape(6.dp),
                        color = if (illuminated) {
                            Color(0xFF450B0B)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(
                            1.dp,
                            if (illuminated) Color(0xFFFF4646) else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Text(
                            text = "ILLUM",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (illuminated) Color(0xFFFF6666) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle Reticle View"
                        )
                    }
                }
            }

            if (expanded) {
                // HOLD ALL vs DIAL ELEV / HOLD WIND Toggle
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !dialElevationMode,
                        onClick = { dialElevationMode = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Hold All", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    SegmentedButton(
                        selected = dialElevationMode,
                        onClick = { dialElevationMode = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Dial Elev / Hold Wind", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }

                // Circular Optical Scope Lens (capped size for unfolded Z Fold + pinch-to-zoom!)
                Box(
                    modifier = Modifier
                        .sizeIn(maxWidth = 336.dp, maxHeight = 336.dp)
                        .fillMaxWidth(0.84f)
                        .aspectRatio(1f)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isNightRed) Color(0xFF040000) else Color(0xFF050706))
                        .pointerInput(reticle.minZoom, reticle.maxZoom) {
                            if (reticle.maxZoom > reticle.minZoom) {
                                detectTransformGestures { _, _, zoomChange, _ ->
                                    zoom = (zoom * zoomChange).coerceIn(reticle.minZoom, reticle.maxZoom)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    ScopeOpticCanvas(
                        reticle = reticle,
                        zoom = zoom,
                        illuminated = illuminated,
                        isNightRed = isNightRed,
                        elevationHoldNative = displayedElev,
                        windageHoldNative = nativeWind,
                        targetDiameterNative = targetAngularDiameterNative,
                        unitTag = unitTag,
                        accentAimColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Crisp 1-line Holdover Summary
                val vStr = when {
                    dialElevationMode -> String.format(Locale.US, "DIAL %.2f %s UP", nativeElev, unitTag)
                    abs(displayedElev) < 0.05 -> "0.00 $unitTag"
                    displayedElev > 0 -> String.format(Locale.US, "%.2f %s DN", displayedElev, unitTag)
                    else -> String.format(Locale.US, "%.2f %s UP", abs(displayedElev), unitTag)
                }
                val hStr = when {
                    abs(nativeWind) < 0.05 -> "0.00 $unitTag"
                    nativeWind > 0 -> String.format(Locale.US, "%.2f %s L (Hold R)", nativeWind, unitTag)
                    else -> String.format(Locale.US, "%.2f %s R (Hold L)", abs(nativeWind), unitTag)
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (dialElevationMode) "TURRET + STADIA" else "RETICLE HOLD",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$vStr  •  $hStr",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Magnification Zoom Controls
                if (reticle.maxZoom > reticle.minZoom) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${if (reticle.isFfp) "FFP" else "ZOOM"} ${zoom.roundToInt()}x",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(64.dp)
                            )
                            Slider(
                                value = zoom,
                                onValueChange = { zoom = it },
                                valueRange = reticle.minZoom..reticle.maxZoom,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        val zoomStops = when {
                            reticle == ReticleType.EOTECH_HWS_68 -> listOf(1f, 3f)
                            reticle.maxZoom >= 16f -> listOf(4f, 8f, 12f, 16f)
                            reticle.maxZoom >= 14f -> listOf(4f, 8f, 10f, 14f)
                            else -> listOf(reticle.minZoom, (reticle.minZoom + reticle.maxZoom) / 2f, reticle.maxZoom)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            zoomStops.forEach { stop ->
                                FilterChip(
                                    selected = abs(zoom - stop) < 0.6f,
                                    onClick = { zoom = stop },
                                    label = { Text("${stop.toInt()}x") }
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            FilterChip(
                                selected = showReticlePicker,
                                onClick = { showReticlePicker = !showReticlePicker },
                                label = { Text("Reticle") }
                            )
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FilterChip(
                            selected = showReticlePicker,
                            onClick = { showReticlePicker = !showReticlePicker },
                            label = { Text("Change Reticle") }
                        )
                    }
                }

                // True-Scale Target Plate Overlay Selector
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ReticleTargetSize.entries.forEach { ts ->
                        FilterChip(
                            selected = targetSize == ts,
                            onClick = { targetSize = ts },
                            label = { Text(ts.label) }
                        )
                    }
                }

                if (showReticlePicker) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ReticleType.entries.forEach { rt ->
                            FilterChip(
                                selected = rt == reticle,
                                onClick = {
                                    onChangeScopeReticle(rt)
                                    showReticlePicker = false
                                },
                                label = { Text(rt.label) }
                            )
                        }
                    }
                }
            }
    }
}

@Composable
private fun ScopeOpticCanvas(
    reticle: ReticleType,
    zoom: Float,
    illuminated: Boolean,
    isNightRed: Boolean,
    elevationHoldNative: Double,
    windageHoldNative: Double,
    targetDiameterNative: Float,
    unitTag: String,
    accentAimColor: Color,
    modifier: Modifier = Modifier
) {
    // Hoisted reusable drawing primitives (zero per-frame allocations inside Canvas!)
    val lensPath = remember { Path() }
    val scratchPath = remember { Path() }
    val numberPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }
    val edgeLabelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }

    val glassColors = remember(isNightRed) {
        if (isNightRed) {
            listOf(Color(0xFF1A0404), Color(0xFF120202), Color(0xFF090101), Color(0xFF030000))
        } else {
            listOf(Color(0xFF28322A), Color(0xFF1E2620), Color(0xFF131915), Color(0xFF090D0B))
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = min(w, h) / 2f
        val center = Offset(w / 2f, h / 2f)
        val innerRadius = radius * 0.91f

        // CNC-knurled ocular housing bezel + multi-coated AR optical glass sheen
        val bezelColor = if (isNightRed) Color(0xFF1A0404) else Color(0xFF141A16)
        val serrationColor = if (isNightRed) Color(0xFF541414) else Color(0xFF2E3A32)
        val arRimColor = if (isNightRed) Color(0xFFFF3838).copy(alpha = 0.42f) else Color(0xFF6EC484).copy(alpha = 0.40f)

        drawCircle(color = bezelColor, radius = radius, center = center)
        for (i in 0 until 60) {
            val rad = Math.toRadians(i * 6.0)
            val cosA = cos(rad).toFloat()
            val sinA = sin(rad).toFloat()
            val tickInner = if (i % 5 == 0) radius * 0.91f else radius * 0.935f
            drawLine(
                color = serrationColor,
                start = Offset(center.x + tickInner * cosA, center.y + tickInner * sinA),
                end = Offset(center.x + radius * 0.985f * cosA, center.y + radius * 0.985f * sinA),
                strokeWidth = if (i % 5 == 0) 1.8.dp.toPx() else 1.0.dp.toPx()
            )
        }

        drawCircle(
            brush = Brush.radialGradient(colors = glassColors, center = center, radius = innerRadius),
            radius = innerRadius,
            center = center
        )
        drawCircle(
            color = arRimColor,
            radius = innerRadius,
            center = center,
            style = Stroke(width = 1.6.dp.toPx())
        )

        lensPath.rewind()
        lensPath.addOval(
            Rect(
                center.x - innerRadius,
                center.y - innerRadius,
                center.x + innerRadius,
                center.y + innerRadius
            )
        )

        val lineColor = when {
            isNightRed && illuminated -> Color(0xFFFF3A3A)
            isNightRed -> Color(0xFFB82424)
            illuminated -> Color(0xFFFF3838)
            else -> Color(0xFF0A0B0A)
        }
        val softLineColor = lineColor.copy(alpha = if (illuminated || isNightRed) 0.78f else 0.85f)
        val markerCoreColor = if (isNightRed) Color(0xFFFF7A7A) else Color.White
        val markerRingColor = if (isNightRed) Color(0xFFFF3333) else accentAimColor

        val pxPerUnit: Float = when (reticle) {
            ReticleType.ARKEN_VPR_MIL, ReticleType.PA_ACSS_RGRID -> (innerRadius / 10.5f) * (zoom / 10f)
            ReticleType.PA_ARC2_MOA -> (innerRadius / 30f) * (zoom / 10f)
            ReticleType.RED_DOT_CIRCLE_65 -> (innerRadius * 0.46f / 32.5f) * zoom
            ReticleType.EOTECH_HWS_68 -> (innerRadius * 0.46f / 34f) * (1f + (zoom - 1f) * 0.32f)
            ReticleType.HANDGUN_THREE_DOT -> (innerRadius / 24f) * zoom
            ReticleType.SIMPLE_DUPLEX -> (innerRadius / 20f) * (zoom / 6f)
        }

        clipPath(lensPath) {
            when (reticle) {
                ReticleType.ARKEN_VPR_MIL -> drawArkenVprMil(center, innerRadius, pxPerUnit, lineColor, softLineColor, numberPaint)
                ReticleType.PA_ARC2_MOA -> drawPrimaryArmsArc2Moa(center, innerRadius, pxPerUnit, lineColor, softLineColor, numberPaint)
                ReticleType.PA_ACSS_RGRID -> drawPrimaryArmsAcssRGrid(center, innerRadius, pxPerUnit, lineColor, softLineColor, scratchPath)
                ReticleType.RED_DOT_CIRCLE_65 -> drawRedDotCircle65(center, innerRadius, pxPerUnit, illuminated, isNightRed)
                ReticleType.EOTECH_HWS_68 -> drawEotechHws68(center, innerRadius, pxPerUnit, illuminated, isNightRed)
                ReticleType.HANDGUN_THREE_DOT -> drawHandgunThreeDot(center, innerRadius, isNightRed, scratchPath)
                ReticleType.SIMPLE_DUPLEX -> drawSimpleDuplex(center, innerRadius, lineColor)
            }

            // Live Target Plate + Precision Ballistic Holdover Marker
            val holdOffsetX = (-windageHoldNative * pxPerUnit).toFloat()
            val holdOffsetY = (elevationHoldNative * pxPerUnit).toFloat()
            val distFromCenter = hypot(holdOffsetX, holdOffsetY)
            val maxVisibleR = innerRadius * 0.86f

            if (distFromCenter <= maxVisibleR) {
                val aimPt = Offset(center.x + holdOffsetX, center.y + holdOffsetY)

                // True-scale steel plate silhouette centered at the target holdover coordinates
                if (targetDiameterNative > 0f) {
                    val targetRadiusPx = (targetDiameterNative * 0.5f * pxPerUnit).coerceIn(3.dp.toPx(), innerRadius * 0.55f)
                    drawCircle(
                        color = markerRingColor.copy(alpha = 0.22f),
                        radius = targetRadiusPx,
                        center = aimPt
                    )
                    drawCircle(
                        color = markerRingColor.copy(alpha = 0.85f),
                        radius = targetRadiusPx,
                        center = aimPt,
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                }

                // High-visibility center aimpoint pip
                drawCircle(color = markerRingColor.copy(alpha = 0.30f), radius = 10.dp.toPx(), center = aimPt)
                drawCircle(color = markerRingColor, radius = 5.dp.toPx(), center = aimPt, style = Stroke(width = 1.8.dp.toPx()))
                drawCircle(color = markerCoreColor, radius = 2.dp.toPx(), center = aimPt)
            } else {
                // Off-FOV directional chevron + numeric indicator
                val angle = atan2(holdOffsetY.toDouble(), holdOffsetX.toDouble())
                val edgePt = Offset(
                    center.x + (maxVisibleR * cos(angle)).toFloat(),
                    center.y + (maxVisibleR * sin(angle)).toFloat()
                )
                val perpX = -sin(angle).toFloat()
                val perpY = cos(angle).toFloat()
                val dirX = cos(angle).toFloat()
                val dirY = sin(angle).toFloat()
                val tip = Offset(edgePt.x + dirX * 7.dp.toPx(), edgePt.y + dirY * 7.dp.toPx())
                val leftWing = Offset(edgePt.x - dirX * 5.dp.toPx() + perpX * 6.dp.toPx(), edgePt.y - dirY * 5.dp.toPx() + perpY * 6.dp.toPx())
                val rightWing = Offset(edgePt.x - dirX * 5.dp.toPx() - perpX * 6.dp.toPx(), edgePt.y - dirY * 5.dp.toPx() - perpY * 6.dp.toPx())

                scratchPath.rewind()
                scratchPath.moveTo(tip.x, tip.y)
                scratchPath.lineTo(leftWing.x, leftWing.y)
                scratchPath.lineTo(rightWing.x, rightWing.y)
                scratchPath.close()
                drawPath(scratchPath, markerRingColor)

                edgeLabelPaint.color = markerCoreColor.toArgb()
                edgeLabelPaint.textSize = 10.dp.toPx()
                val labelPos = Offset(
                    (edgePt.x - dirX * 26.dp.toPx()).coerceIn(center.x - innerRadius * 0.65f, center.x + innerRadius * 0.65f),
                    (edgePt.y - dirY * 18.dp.toPx()).coerceIn(center.y - innerRadius * 0.7f, center.y + innerRadius * 0.7f)
                )
                drawContext.canvas.nativeCanvas.drawText(
                    String.format(Locale.US, "%.1f %s", abs(elevationHoldNative), unitTag),
                    labelPos.x,
                    labelPos.y,
                    edgeLabelPaint
                )
            }
        }

        // Machined anodized bezel ring
        drawCircle(
            color = if (isNightRed) Color(0xFF280808) else Color(0xFF1A211D),
            radius = radius - 3.dp.toPx(),
            center = center,
            style = Stroke(width = 6.dp.toPx())
        )
        drawCircle(
            color = (if (isNightRed) Color(0xFFFF4E4E) else Color.White).copy(alpha = 0.14f),
            radius = innerRadius,
            center = center,
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
}

private fun DrawScope.drawArkenVprMil(
    center: Offset,
    radius: Float,
    pxPerMil: Float,
    color: Color,
    softColor: Color,
    paint: Paint
) {
    val fine = 1.1.dp.toPx()
    val heavy = 3.2.dp.toPx()
    val gap = (0.35f * pxPerMil).coerceIn(4.dp.toPx(), 10.dp.toPx())

    drawCircle(color = color, radius = 2.dp.toPx(), center = center)

    drawLine(color, Offset(center.x - radius, center.y), Offset(center.x - gap, center.y), fine)
    drawLine(color, Offset(center.x + gap, center.y), Offset(center.x + radius, center.y), fine)
    drawLine(color, Offset(center.x, center.y - radius), Offset(center.x, center.y - gap), fine)
    drawLine(color, Offset(center.x, center.y + gap), Offset(center.x, center.y + radius), fine)

    val outerStart = 10f * pxPerMil
    if (outerStart < radius) {
        drawLine(color, Offset(center.x - radius, center.y), Offset(center.x - outerStart, center.y), heavy)
        drawLine(color, Offset(center.x + outerStart, center.y), Offset(center.x + radius, center.y), heavy)
        drawLine(color, Offset(center.x, center.y - radius), Offset(center.x, center.y - outerStart), heavy)
    }

    paint.color = color.toArgb()
    paint.textSize = (pxPerMil * 0.42f).coerceIn(7.dp.toPx(), 11.dp.toPx())

    for (halfMil in -20..20) {
        if (halfMil == 0) continue
        val mil = halfMil * 0.5f
        val x = center.x + mil * pxPerMil
        if (abs(x - center.x) > radius) continue
        val isWhole = halfMil % 2 == 0
        val tickHalf = if (isWhole) 5.dp.toPx() else 2.8.dp.toPx()
        drawLine(softColor, Offset(x, center.y - tickHalf), Offset(x, center.y + tickHalf), fine)
        if (isWhole && abs(mil.toInt()) % 2 == 0 && abs(mil) <= 10f) {
            drawContext.canvas.nativeCanvas.drawText(
                abs(mil.toInt()).toString(),
                x,
                center.y - tickHalf - 3.dp.toPx(),
                paint
            )
        }
    }

    for (halfMil in -8..24) {
        if (halfMil == 0) continue
        val mil = halfMil * 0.5f
        val y = center.y + mil * pxPerMil
        if (abs(y - center.y) > radius) continue
        val isWhole = halfMil % 2 == 0
        val tickHalf = if (isWhole) 5.5.dp.toPx() else 3.dp.toPx()
        drawLine(softColor, Offset(center.x - tickHalf, y), Offset(center.x + tickHalf, y), fine)

        if (isWhole && mil > 0f && mil.toInt() % 2 == 0 && mil <= 12f) {
            drawContext.canvas.nativeCanvas.drawText(
                mil.toInt().toString(),
                center.x + tickHalf + 8.dp.toPx(),
                y + 3.dp.toPx(),
                paint
            )
        }

        if (isWhole && mil >= 1f && mil <= 12f) {
            val maxWindMil = ((mil * 0.45f) + 1.5f).coerceAtMost(6f).toInt()
            for (wHalf in -maxWindMil * 2..maxWindMil * 2) {
                if (wHalf == 0) continue
                val wMil = wHalf * 0.5f
                val x = center.x + wMil * pxPerMil
                if (hypot(x - center.x, y - center.y) > radius * 0.94f) continue
                val dotR = if (wHalf % 2 == 0) 1.7.dp.toPx() else 1.0.dp.toPx()
                drawCircle(softColor, radius = dotR, center = Offset(x, y))
            }
        }
    }
}

private fun DrawScope.drawPrimaryArmsArc2Moa(
    center: Offset,
    radius: Float,
    pxPerMoa: Float,
    color: Color,
    softColor: Color,
    paint: Paint
) {
    val fine = 1.1.dp.toPx()
    val heavy = 3.2.dp.toPx()

    drawLine(color, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), fine)
    drawLine(color, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), fine)

    val outerStart = 24f * pxPerMoa
    if (outerStart < radius) {
        drawLine(color, Offset(center.x - radius, center.y), Offset(center.x - outerStart, center.y), heavy)
        drawLine(color, Offset(center.x + outerStart, center.y), Offset(center.x + radius, center.y), heavy)
        drawLine(color, Offset(center.x, center.y - radius), Offset(center.x, center.y - outerStart), heavy)
    }

    paint.color = color.toArgb()
    paint.textSize = (pxPerMoa * 1.6f).coerceIn(7.dp.toPx(), 10.5.dp.toPx())

    for (moa in -24..24 step 2) {
        if (moa == 0) continue
        val x = center.x + moa * pxPerMoa
        if (abs(x - center.x) > radius) continue
        val isMajor = moa % 4 == 0
        val tickHalf = if (isMajor) 5.5.dp.toPx() else 3.dp.toPx()
        drawLine(softColor, Offset(x, center.y - tickHalf), Offset(x, center.y + tickHalf), fine)
        if (isMajor && abs(moa) % 8 == 0) {
            drawContext.canvas.nativeCanvas.drawText(abs(moa).toString(), x, center.y - tickHalf - 3.dp.toPx(), paint)
        }
    }

    for (moa in -16..36 step 2) {
        if (moa == 0) continue
        val y = center.y + moa * pxPerMoa
        if (abs(y - center.y) > radius) continue
        val isMajor = moa % 4 == 0
        val tickHalf = if (isMajor) 6.dp.toPx() else 3.dp.toPx()
        drawLine(softColor, Offset(center.x - tickHalf, y), Offset(center.x + tickHalf, y), fine)

        if (isMajor && moa > 0 && moa % 4 == 0) {
            val maxW = (moa / 2).coerceIn(4, 16)
            for (wMoa in -maxW..maxW step 4) {
                if (wMoa == 0) continue
                val wx = center.x + wMoa * pxPerMoa
                if (hypot(wx - center.x, y - center.y) < radius * 0.94f) {
                    drawCircle(softColor, radius = 1.4.dp.toPx(), center = Offset(wx, y))
                }
            }
            if (moa % 8 == 0) {
                drawContext.canvas.nativeCanvas.drawText(moa.toString(), center.x + tickHalf + 9.dp.toPx(), y + 3.dp.toPx(), paint)
            }
        }
    }
}

private fun DrawScope.drawPrimaryArmsAcssRGrid(
    center: Offset,
    radius: Float,
    pxPerMil: Float,
    color: Color,
    softColor: Color,
    scratchPath: Path
) {
    val fine = 1.1.dp.toPx()
    val heavy = 3.0.dp.toPx()

    val chevW = (0.45f * pxPerMil).coerceIn(4.dp.toPx(), 10.dp.toPx())
    val chevH = (0.45f * pxPerMil).coerceIn(4.dp.toPx(), 10.dp.toPx())
    scratchPath.rewind()
    scratchPath.moveTo(center.x - chevW, center.y + chevH)
    scratchPath.lineTo(center.x, center.y)
    scratchPath.lineTo(center.x + chevW, center.y + chevH)
    drawPath(scratchPath, color, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))

    val hsR = (1.45f * pxPerMil).coerceIn(11.dp.toPx(), 28.dp.toPx())
    drawArc(
        color = color,
        startAngle = 140f,
        sweepAngle = 260f,
        useCenter = false,
        topLeft = Offset(center.x - hsR, center.y - hsR),
        size = Size(hsR * 2f, hsR * 2f),
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    )

    drawLine(color, Offset(center.x - radius, center.y), Offset(center.x - hsR - 3.dp.toPx(), center.y), fine)
    drawLine(color, Offset(center.x + hsR + 3.dp.toPx(), center.y), Offset(center.x + radius, center.y), fine)
    drawLine(color, Offset(center.x, center.y - radius), Offset(center.x, center.y - hsR - 3.dp.toPx()), heavy)

    for (mil in 2..12) {
        val y = center.y + mil * pxPerMil
        if (y > center.y + radius) break
        drawLine(softColor, Offset(center.x - 4.dp.toPx(), y), Offset(center.x + 4.dp.toPx(), y), fine)
        val span = (mil / 2 + 1).coerceAtMost(5)
        for (w in -span..span) {
            if (w == 0) continue
            val x = center.x + w * pxPerMil
            if (hypot(x - center.x, y - center.y) < radius * 0.94f) {
                drawCircle(softColor, radius = 1.6.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

private fun DrawScope.drawRedDotCircle65(
    center: Offset,
    radius: Float,
    pxPerMoa: Float,
    illuminated: Boolean,
    isNightRed: Boolean
) {
    val dotColor = when {
        isNightRed -> Color(0xFFFF3A3A)
        illuminated -> Color(0xFFFF3030)
        else -> Color(0xFF44FF66)
    }
    val ringR = (32.5f * pxPerMoa).coerceIn(radius * 0.25f, radius * 0.78f)
    val tickLen = 8.dp.toPx()

    drawCircle(color = dotColor.copy(alpha = 0.88f), radius = ringR, center = center, style = Stroke(width = 2.dp.toPx()))
    drawLine(dotColor, Offset(center.x, center.y - ringR - tickLen * 0.4f), Offset(center.x, center.y - ringR + tickLen * 0.6f), 2.2.dp.toPx())
    drawLine(dotColor, Offset(center.x, center.y + ringR - tickLen * 0.6f), Offset(center.x, center.y + ringR + tickLen * 0.4f), 2.2.dp.toPx())
    drawLine(dotColor, Offset(center.x - ringR - tickLen * 0.4f, center.y), Offset(center.x - ringR + tickLen * 0.6f, center.y), 2.2.dp.toPx())
    drawLine(dotColor, Offset(center.x + ringR - tickLen * 0.6f, center.y), Offset(center.x + ringR + tickLen * 0.4f, center.y), 2.2.dp.toPx())

    drawCircle(dotColor.copy(alpha = 0.30f), radius = 6.dp.toPx(), center = center)
    drawCircle(dotColor, radius = 2.6.dp.toPx(), center = center)
}

private fun DrawScope.drawEotechHws68(
    center: Offset,
    radius: Float,
    pxPerMoa: Float,
    illuminated: Boolean,
    isNightRed: Boolean
) {
    val retColor = when {
        isNightRed -> Color(0xFFFF3A3A)
        illuminated -> Color(0xFFFF3030)
        else -> Color(0xFF44FF66)
    }
    val ringR = (34f * pxPerMoa).coerceIn(radius * 0.24f, radius * 0.80f)
    val tickLen = 9.dp.toPx()

    // Signature EOTech 68 MOA dashed/segmented outer halo ring + 4 cardinal ticks + crisp 1 MOA dot
    drawCircle(
        color = retColor.copy(alpha = 0.22f),
        radius = ringR,
        center = center,
        style = Stroke(width = 4.2.dp.toPx())
    )
    drawCircle(
        color = retColor.copy(alpha = 0.92f),
        radius = ringR,
        center = center,
        style = Stroke(width = 1.9.dp.toPx())
    )
    drawLine(retColor, Offset(center.x, center.y - ringR - tickLen * 0.45f), Offset(center.x, center.y - ringR + tickLen * 0.7f), 2.2.dp.toPx())
    drawLine(retColor, Offset(center.x, center.y + ringR - tickLen * 0.7f), Offset(center.x, center.y + ringR + tickLen * 0.45f), 2.2.dp.toPx())
    drawLine(retColor, Offset(center.x - ringR - tickLen * 0.45f, center.y), Offset(center.x - ringR + tickLen * 0.7f, center.y), 2.2.dp.toPx())
    drawLine(retColor, Offset(center.x + ringR - tickLen * 0.7f, center.y), Offset(center.x + ringR + tickLen * 0.45f, center.y), 2.2.dp.toPx())

    drawCircle(retColor.copy(alpha = 0.28f), radius = 4.8.dp.toPx(), center = center)
    drawCircle(retColor, radius = 1.9.dp.toPx(), center = center)
}

private fun DrawScope.drawHandgunThreeDot(
    center: Offset,
    radius: Float,
    isNightRed: Boolean,
    scratchPath: Path
) {
    val steel = Color(0xFF0A0C0B)
    val dotFill = if (isNightRed) Color(0xFFD93636) else Color(0xFFEDF2EE)
    val sightTopY = center.y
    val sightBottom = center.y + radius
    val notchHalf = radius * 0.14f
    val postHalf = radius * 0.085f
    val rearOuter = radius * 0.56f

    drawRect(steel, Offset(center.x - rearOuter, sightTopY), Size(rearOuter - notchHalf, sightBottom - sightTopY))
    drawRect(steel, Offset(center.x + notchHalf, sightTopY), Size(rearOuter - notchHalf, sightBottom - sightTopY))

    val uBottom = sightTopY + radius * 0.24f
    scratchPath.rewind()
    scratchPath.moveTo(center.x - notchHalf - 6.dp.toPx(), sightTopY + 3.dp.toPx())
    scratchPath.lineTo(center.x - notchHalf - 6.dp.toPx(), uBottom)
    scratchPath.lineTo(center.x + notchHalf + 6.dp.toPx(), uBottom)
    scratchPath.lineTo(center.x + notchHalf + 6.dp.toPx(), sightTopY + 3.dp.toPx())
    drawPath(scratchPath, dotFill.copy(alpha = 0.88f), style = Stroke(width = 3.dp.toPx()))

    drawRect(steel, Offset(center.x - postHalf, sightTopY), Size(postHalf * 2f, sightBottom - sightTopY))
    drawCircle(dotFill, radius = postHalf * 0.55f, center = Offset(center.x, sightTopY + postHalf * 1.1f))
}

private fun DrawScope.drawSimpleDuplex(center: Offset, radius: Float, color: Color) {
    val fine = 1.1.dp.toPx()
    val heavy = 3.8.dp.toPx()
    val innerSpan = radius * 0.36f

    drawLine(color, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), fine)
    drawLine(color, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), fine)

    drawLine(color, Offset(center.x - radius, center.y), Offset(center.x - innerSpan, center.y), heavy)
    drawLine(color, Offset(center.x + innerSpan, center.y), Offset(center.x + radius, center.y), heavy)
    drawLine(color, Offset(center.x, center.y - radius), Offset(center.x, center.y - innerSpan), heavy)
    drawLine(color, Offset(center.x, center.y + innerSpan), Offset(center.x, center.y + radius), heavy)
}
