package com.bc1800.ballistics.ui.screens

import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bc1800.ballistics.data.UnitFormatters
import com.bc1800.ballistics.model.BallisticSolution
import com.bc1800.ballistics.model.DistanceUnit
import com.bc1800.ballistics.ui.AppUiState
import com.bc1800.ballistics.ui.BallisticsViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun RangeCardScreen(
    uiState: AppUiState,
    viewModel: BallisticsViewModel,
    onSelectRangeYards: (Double) -> Unit
) {
    val context = LocalContext.current
    val prefs = uiState.preferences
    val atmo = uiState.solverInput.atmosphere
    val rows = uiState.rangeCard
    val isShortRange = uiState.activeAmmo.caliber.contains("22 LR", ignoreCase = true) ||
        uiState.activeAmmo.caliber.contains("9mm", ignoreCase = true)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 600.dp

        if (isExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        RangeCardControlsPanel(
                            uiState = uiState,
                            isShortRange = isShortRange,
                            onStepChange = { viewModel.setRangeCardStep(it) },
                            onMaxChange = { viewModel.setRangeCardMax(it) },
                            onShare = {
                                val text = UnitFormatters.formatRangeCardText(
                                    rigTitle = "${uiState.activeFirearm.name} • ${uiState.activeScope.name}",
                                    ammoTitle = uiState.activeAmmo.name,
                                    atmosphere = atmo,
                                    solutions = rows,
                                    prefs = prefs
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "DOPE Card — ${uiState.activeAmmo.name}")
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share DOPE Card"))
                            }
                        )
                    }
                    item {
                        TrajectoryDropChartPanel(
                            solutions = rows,
                            currentRangeYards = uiState.solverInput.rangeYards,
                            zeroRangeYards = uiState.activeScope.zeroRangeYards,
                            distanceUnit = prefs.distanceUnit
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                ) {
                    DopeTablePane(
                        uiState = uiState,
                        rows = rows,
                        showExpandedColumns = true,
                        onRowTap = onSelectRangeYards
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RangeCardControlsPanel(
                    uiState = uiState,
                    isShortRange = isShortRange,
                    onStepChange = { viewModel.setRangeCardStep(it) },
                    onMaxChange = { viewModel.setRangeCardMax(it) },
                    onShare = {
                        val text = UnitFormatters.formatRangeCardText(
                            rigTitle = "${uiState.activeFirearm.name} • ${uiState.activeScope.name}",
                            ammoTitle = uiState.activeAmmo.name,
                            atmosphere = atmo,
                            solutions = rows,
                            prefs = prefs
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "DOPE Card — ${uiState.activeAmmo.name}")
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share DOPE Card"))
                    }
                )
                DopeTablePane(
                    uiState = uiState,
                    rows = rows,
                    showExpandedColumns = false,
                    onRowTap = onSelectRangeYards,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RangeCardControlsPanel(
    uiState: AppUiState,
    isShortRange: Boolean,
    onStepChange: (Int) -> Unit,
    onMaxChange: (Int) -> Unit,
    onShare: () -> Unit
) {
    val prefs = uiState.preferences
    val atmo = uiState.solverInput.atmosphere
    val unit = prefs.distanceUnit.shortLabel

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${uiState.activeFirearm.name} • ${uiState.activeScope.name}".uppercase(Locale.US),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = uiState.activeAmmo.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Zero ${UnitFormatters.formatDistance(uiState.activeScope.zeroRangeYards, prefs.distanceUnit)} • " +
                            "Wind ${UnitFormatters.formatWind(atmo.windSpeedMph, prefs.windUnit)} @ ${
                                UnitFormatters.windClockHour(atmo.windDirectionDeg)
                            }:00 • ${UnitFormatters.formatTemp(atmo.temperatureF, prefs.temperatureUnit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onShare,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("STEP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(34.dp))
                listOf(10, 25, 50, 100).forEach { step ->
                    FilterChip(
                        selected = prefs.rangeCardStepYards == step,
                        onClick = { onStepChange(step) },
                        label = { Text("$step$unit") }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("MAX", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(34.dp))
                val maxChoices = if (isShortRange) listOf(200, 300, 500, 800) else listOf(300, 600, 1000, 1500)
                maxChoices.forEach { max ->
                    FilterChip(
                        selected = prefs.rangeCardMaxYards == max,
                        onClick = { onMaxChange(max) },
                        label = { Text("$max$unit") }
                    )
                }
            }
        }
    }
}

@Composable
private fun DopeTablePane(
    uiState: AppUiState,
    rows: List<BallisticSolution>,
    showExpandedColumns: Boolean,
    onRowTap: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = uiState.preferences
    val velUnit = prefs.velocityUnit.shortLabel

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableHeaderCell("RNG (${prefs.distanceUnit.shortLabel})", 0.85f, TextAlign.Start)
                TableHeaderCell("ELEV (${prefs.angularUnit.shortLabel})", 1.1f)
                TableHeaderCell("CLKS", 0.65f)
                TableHeaderCell("WIND", 1.0f)
                if (showExpandedColumns) {
                    TableHeaderCell("DROP", 0.9f)
                }
                TableHeaderCell("VEL ($velUnit)", 0.95f)
                TableHeaderCell("TOF", 0.7f, TextAlign.End)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            itemsIndexed(rows, key = { _, sol -> sol.rangeYards }) { index, sol ->
                val isPrevWithin800 = index > 0 && !rows[index - 1].exceedsOnboard800YdLimit
                if (sol.exceedsOnboard800YdLimit && isPrevWithin800) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "▲ Onboard LBC1800 Limit (800 yd) — Extended Phone Solutions Below ▼",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 5.dp, horizontal = 8.dp)
                        )
                    }
                }

                val isZeroRow = abs(sol.rangeYards - uiState.activeScope.zeroRangeYards) < 4.0
                val isCurrentRow = abs(sol.rangeYards - uiState.solverInput.rangeYards) < 4.0
                val rowBg = when {
                    isCurrentRow -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    isZeroRow -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f)
                    index % 2 == 0 -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                }

                val elevVal = UnitFormatters.elevationValue(sol, prefs.angularUnit)
                val windVal = UnitFormatters.windageValue(sol, prefs.angularUnit)
                val distNum = String.format(
                    Locale.US,
                    "%.0f",
                    UnitFormatters.distanceValue(sol.rangeYards, prefs.distanceUnit)
                )
                val velNum = UnitFormatters.formatVelocity(sol.velocityFps, prefs.velocityUnit).substringBefore(" ")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                        .clickable { onRowTap(sol.rangeYards) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = distNum + if (isZeroRow) "Z" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isZeroRow) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(0.85f)
                    )
                    Text(
                        text = if (sol.reachedTarget) UnitFormatters.formatCompactHold(elevVal, prefs.angularUnit, "U ", "D ") else "---",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1.1f)
                    )
                    Text(
                        text = if (sol.reachedTarget) abs(sol.elevationClicks).roundToInt().toString() else "--",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(0.65f)
                    )
                    Text(
                        text = if (sol.reachedTarget) UnitFormatters.formatCompactHold(windVal, prefs.angularUnit, "R ", "L ") else "---",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1.0f)
                    )
                    if (showExpandedColumns) {
                        Text(
                            text = UnitFormatters.formatDrop(sol.dropInches, prefs.distanceUnit),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(0.9f)
                        )
                    }
                    Text(
                        text = velNum,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (sol.isTransonicWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(0.95f)
                    )
                    Text(
                        text = String.format(Locale.US, "%.2fs", sol.timeOfFlightSec),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.7f)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            }
        }
    }
}

@Composable
private fun TrajectoryDropChartPanel(
    solutions: List<BallisticSolution>,
    currentRangeYards: Double,
    zeroRangeYards: Double,
    distanceUnit: DistanceUnit
) {
    if (solutions.size < 2) return
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outline
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val path = remember { Path() }
    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "TRAJECTORY PATH VS LINE OF SIGHT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                val valid = solutions.filter { it.reachedTarget }
                if (valid.size < 2) return@Canvas
                val minDrop = (valid.minOf { it.dropInches }).coerceAtMost(-6.0)
                val maxDrop = (valid.maxOf { it.dropInches }).coerceAtLeast(3.0)
                val maxRange = valid.last().rangeYards.coerceAtLeast(50.0)
                val padLeft = 36.dp.toPx()
                val padRight = 10.dp.toPx()
                val padTop = 10.dp.toPx()
                val padBot = 20.dp.toPx()
                val plotW = size.width - padLeft - padRight
                val plotH = size.height - padTop - padBot

                fun xFor(rangeYd: Double): Float = padLeft + ((rangeYd / maxRange) * plotW).toFloat()
                fun yFor(dropIn: Double): Float {
                    val norm = (dropIn - minDrop) / (maxDrop - minDrop).coerceAtLeast(1.0)
                    return padTop + ((1.0 - norm) * plotH).toFloat()
                }

                // Line-of-Sight (0" drop) dashed reference
                val zeroY = yFor(0.0)
                drawLine(
                    color = outline,
                    start = Offset(padLeft, zeroY),
                    end = Offset(size.width - padRight, zeroY),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )

                // Trajectory curve
                path.rewind()
                valid.forEachIndexed { idx, s ->
                    val px = xFor(s.rangeYards)
                    val py = yFor(s.dropInches)
                    if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(path, primary, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))

                // Zero crossing marker
                if (zeroRangeYards <= maxRange) {
                    drawCircle(secondary, radius = 4.5.dp.toPx(), center = Offset(xFor(zeroRangeYards), zeroY))
                }

                // Current solver range vertical cursor
                if (currentRangeYards <= maxRange) {
                    val cx = xFor(currentRangeYards)
                    drawLine(secondary.copy(alpha = 0.6f), Offset(cx, padTop), Offset(cx, size.height - padBot), 1.5.dp.toPx())
                }

                labelPaint.color = onSurfaceVariant.toArgb()
                labelPaint.textSize = 9.5.dp.toPx()
                labelPaint.textAlign = Paint.Align.RIGHT
                drawContext.canvas.nativeCanvas.drawText("0\"", padLeft - 4.dp.toPx(), zeroY + 3.dp.toPx(), labelPaint)
                drawContext.canvas.nativeCanvas.drawText(
                    String.format(Locale.US, "%.0f\"", minDrop),
                    padLeft - 4.dp.toPx(),
                    size.height - padBot,
                    labelPaint
                )
                labelPaint.textAlign = Paint.Align.RIGHT
                drawContext.canvas.nativeCanvas.drawText(
                    UnitFormatters.formatDistance(maxRange, distanceUnit),
                    size.width - padRight,
                    size.height - 4.dp.toPx(),
                    labelPaint
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TableHeaderCell(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.Center
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.weight(weight)
    )
}
