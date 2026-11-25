package com.gunnies.ballistics.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gunnies.ballistics.data.UnitFormatters
import com.gunnies.ballistics.engine.BallisticEngine
import com.gunnies.ballistics.model.BallisticSolution
import com.gunnies.ballistics.model.SessionLogEntry
import com.gunnies.ballistics.ui.AppUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One-tap Shot & Cold Bore Logger pre-populated from the active Solver HUD snapshot.
 */
@Composable
fun LogShotDialog(
    uiState: AppUiState,
    solution: BallisticSolution,
    onDismiss: () -> Unit,
    onSaveLog: (actualElev: Double, actualWind: Double, isColdBore: Boolean, notes: String) -> Unit
) {
    val prefs = uiState.preferences
    val unit = prefs.angularUnit
    val predElev = UnitFormatters.elevationValue(solution, unit)
    val predWind = UnitFormatters.windageValue(solution, unit)
    val atmo = uiState.solverInput.atmosphere
    val air = remember(atmo) { BallisticEngine.computeAirState(atmo) }

    var actualElevText by remember { mutableStateOf(String.format(Locale.US, "%.2f", predElev)) }
    var actualWindText by remember { mutableStateOf(String.format(Locale.US, "%.2f", predWind)) }
    var isColdBore by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Log Shot / Cold Bore")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "${uiState.activeFirearm.name} • ${uiState.activeAmmo.name}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Range: ${UnitFormatters.formatDistance(solution.rangeYards, prefs.distanceUnit)} • " +
                                "DA: ${air.densityAltitudeFt.roundToInt()} ft • " +
                                "${UnitFormatters.formatTemp(atmo.temperatureF, prefs.temperatureUnit)} • " +
                                UnitFormatters.formatPressure(air.stationPressureInHg, prefs.pressureUnit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Predicted: Elev ${String.format(Locale.US, "%+.2f", predElev)} • " +
                                "Wind ${String.format(Locale.US, "%+.2f", predWind)} ${unit.shortLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = actualElevText,
                        onValueChange = { actualElevText = it },
                        label = { Text("Actual Elev (${unit.shortLabel})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = actualWindText,
                        onValueChange = { actualWindText = it },
                        label = { Text("Actual Wind (${unit.shortLabel})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(
                    color = if (isColdBore) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(
                        1.dp,
                        if (isColdBore) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AcUnit,
                                contentDescription = null,
                                tint = if (isColdBore) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Cold Bore Shot", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Track first-round cold barrel POI shift",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(checked = isColdBore, onCheckedChange = { isColdBore = it })
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (e.g., clean bore, prone, steel center)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val actE = actualElevText.toDoubleOrNull() ?: predElev
                val actW = actualWindText.toDoubleOrNull() ?: predWind
                onSaveLog(actE, actW, isColdBore, notes)
            }) { Text("Save to Logbook") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Range Session & Cold Bore Dope Logbook sheet with per-firearm filtering and cold-bore shift analysis.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogbookSheetContent(
    uiState: AppUiState,
    onDeleteLog: (String) -> Unit,
    onDone: () -> Unit
) {
    val prefs = uiState.preferences
    var selectedFirearmId by remember { mutableStateOf<String?>(uiState.activeFirearm.id) }
    var coldBoreOnly by remember { mutableStateOf(false) }

    val filteredLogs = remember(uiState.sessionLogs, selectedFirearmId, coldBoreOnly) {
        uiState.sessionLogs.filter { entry ->
            (selectedFirearmId == null || entry.firearmId == selectedFirearmId) &&
                (!coldBoreOnly || entry.isColdBore)
        }
    }

    val coldBoreStats = remember(uiState.sessionLogs, selectedFirearmId) {
        val cb = uiState.sessionLogs.filter {
            it.isColdBore && (selectedFirearmId == null || it.firearmId == selectedFirearmId)
        }
        if (cb.isEmpty()) null
        else {
            val avgElevShift = cb.map { it.actualElevation - it.predictedElevation }.average()
            val avgWindShift = cb.map { it.actualWindage - it.predictedWindage }.average()
            Triple(cb.size, avgElevShift, avgWindShift)
        }
    }

    val dateFormatter = remember { SimpleDateFormat("MMM d • HH:mm", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Cold Bore & Session Logbook", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${uiState.sessionLogs.size} total shots logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onDone) { Text("Done") }
        }

        // Per-firearm filter chips + Cold Bore Only toggle
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = selectedFirearmId == null,
                onClick = { selectedFirearmId = null },
                label = { Text("All Guns") }
            )
            uiState.firearms.forEach { gun ->
                FilterChip(
                    selected = selectedFirearmId == gun.id,
                    onClick = { selectedFirearmId = gun.id },
                    label = { Text(gun.name, maxLines = 1) }
                )
            }
            FilterChip(
                selected = coldBoreOnly,
                onClick = { coldBoreOnly = !coldBoreOnly },
                leadingIcon = { Icon(Icons.Default.AcUnit, contentDescription = null, modifier = Modifier.size(15.dp)) },
                label = { Text("Cold Bore Only") }
            )
        }

        // Cold Bore Shift Summary Banner
        coldBoreStats?.let { (count, avgElevDelta, avgWindDelta) ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "COLD BORE AVG SHIFT ($count shot${if (count == 1) "" else "s"})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "Elev Δ %+.2f  •  Wind Δ %+.2f %s",
                                avgElevDelta,
                                avgWindDelta,
                                prefs.angularUnit.shortLabel
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Icon(
                        Icons.Default.AcUnit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        if (filteredLogs.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No shots logged for this filter yet. Tap \"Log Shot\" on the Solver HUD after firing to track real DOPE and cold-bore shifts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredLogs, key = { it.id }) { entry ->
                    SessionLogCard(
                        entry = entry,
                        formattedDate = dateFormatter.format(Date(entry.timestampMs)),
                        distanceUnit = prefs.distanceUnit,
                        tempUnit = prefs.temperatureUnit,
                        pressureUnit = prefs.pressureUnit,
                        onDelete = { onDeleteLog(entry.id) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun SessionLogCard(
    entry: SessionLogEntry,
    formattedDate: String,
    distanceUnit: com.gunnies.ballistics.model.DistanceUnit,
    tempUnit: com.gunnies.ballistics.model.TemperatureUnit,
    pressureUnit: com.gunnies.ballistics.model.PressureUnit,
    onDelete: () -> Unit
) {
    val elevDelta = entry.actualElevation - entry.predictedElevation
    val windDelta = entry.actualWindage - entry.predictedWindage

    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (entry.isColdBore) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (entry.isColdBore) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "COLD BORE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = UnitFormatters.formatDistance(entry.targetRangeYards, distanceUnit),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• $formattedDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete log", modifier = Modifier.size(16.dp))
                }
            }

            val suppTag = entry.suppressorName?.let { " • $it" } ?: ""
            Text(
                text = "${entry.firearmName} • ${entry.ammoName}$suppTag",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${UnitFormatters.formatTemp(entry.temperatureF, tempUnit)} • " +
                    "${UnitFormatters.formatPressure(entry.stationPressureInHg, pressureUnit)} • " +
                    "DA ${entry.densityAltitudeFt.roundToInt()} ft • " +
                    "Wind ${entry.windSpeedMph.roundToInt()} mph @ ${UnitFormatters.windClockHour(entry.windDirectionDeg)}:00",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format(
                        Locale.US,
                        "Pred: E %+.2f / W %+.2f",
                        entry.predictedElevation,
                        entry.predictedWindage
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "Actual: E %+.2f / W %+.2f %s",
                        entry.actualElevation,
                        entry.actualWindage,
                        entry.angularUnit.shortLabel
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = String.format(
                    Locale.US,
                    "Shift Δ: Elev %+.2f • Wind %+.2f %s",
                    elevDelta,
                    windDelta,
                    entry.angularUnit.shortLabel
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            if (entry.notes.isNotBlank()) {
                Text(
                    text = entry.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
