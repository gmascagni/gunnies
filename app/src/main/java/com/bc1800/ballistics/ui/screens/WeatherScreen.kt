package com.bc1800.ballistics.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bc1800.ballistics.data.UnitFormatters
import com.bc1800.ballistics.data.WeatherService
import com.bc1800.ballistics.engine.BallisticEngine
import com.bc1800.ballistics.model.Atmosphere
import com.bc1800.ballistics.model.PressureUnit
import com.bc1800.ballistics.model.TemperatureUnit
import com.bc1800.ballistics.model.UserPreferences
import com.bc1800.ballistics.model.WindMode
import com.bc1800.ballistics.model.WindProfile
import com.bc1800.ballistics.model.WindUnit
import com.bc1800.ballistics.model.WindZone
import com.bc1800.ballistics.ui.AppUiState
import com.bc1800.ballistics.ui.BallisticsViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun WeatherScreen(
    uiState: AppUiState,
    viewModel: BallisticsViewModel,
    onRequestLocationWeather: () -> Unit
) {
    val prefs = uiState.preferences
    val input = uiState.solverInput
    val atmo = input.atmosphere
    val air = remember(atmo) { BallisticEngine.computeAirState(atmo) }
    val stationPressInHg = remember(atmo) { BallisticEngine.stationPressureInHg(atmo) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GpsWeatherStationCard(
                        atmo = atmo,
                        stationPressInHg = stationPressInHg,
                        air = air,
                        prefs = prefs,
                        isFetchingWeather = input.isFetchingWeather,
                        weatherStatus = input.weatherStatus,
                        onRequestLocationWeather = onRequestLocationWeather,
                        onSaveDefault = { viewModel.saveCurrentAtmosphereAsDefault() }
                    )
                    ManualAtmosphereCard(
                        atmo = atmo,
                        prefs = prefs,
                        viewModel = viewModel
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LocalWindCard(
                        atmo = atmo,
                        prefs = prefs,
                        viewModel = viewModel
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    GpsWeatherStationCard(
                        atmo = atmo,
                        stationPressInHg = stationPressInHg,
                        air = air,
                        prefs = prefs,
                        isFetchingWeather = input.isFetchingWeather,
                        weatherStatus = input.weatherStatus,
                        onRequestLocationWeather = onRequestLocationWeather,
                        onSaveDefault = { viewModel.saveCurrentAtmosphereAsDefault() }
                    )
                }
                item {
                    LocalWindCard(
                        atmo = atmo,
                        prefs = prefs,
                        viewModel = viewModel
                    )
                }
                item {
                    ManualAtmosphereCard(
                        atmo = atmo,
                        prefs = prefs,
                        viewModel = viewModel
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun GpsWeatherStationCard(
    atmo: Atmosphere,
    stationPressInHg: Double,
    air: BallisticEngine.AirState,
    prefs: UserPreferences,
    isFetchingWeather: Boolean,
    weatherStatus: String?,
    onRequestLocationWeather: () -> Unit,
    onSaveDefault: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "GPS WEATHER STATION",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (atmo.gpsLatitude != null && atmo.gpsLongitude != null) {
                            String.format(
                                Locale.US,
                                "%.4f°, %.4f° • %d ft",
                                atmo.gpsLatitude,
                                atmo.gpsLongitude,
                                atmo.altitudeFeet.roundToInt()
                            )
                        } else {
                            "No GPS fix synced"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                atmo.weatherUpdatedAt?.let { ts ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = ts,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRequestLocationWeather,
                    enabled = !isFetchingWeather,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    if (isFetchingWeather) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Sync GPS")
                }

                OutlinedButton(
                    onClick = onSaveDefault,
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save Default")
                }
            }

            if (!weatherStatus.isNullOrBlank()) {
                Text(
                    text = weatherStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WeatherMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "TEMP",
                    value = UnitFormatters.formatTemp(atmo.temperatureF, prefs.temperatureUnit)
                )
                WeatherMetricBox(
                    modifier = Modifier.weight(1f),
                    label = if (atmo.pressureIsStation) "STN PRESS" else "BARO→STN",
                    value = UnitFormatters.formatPressure(stationPressInHg, prefs.pressureUnit)
                )
                WeatherMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "HUMIDITY",
                    value = "${atmo.humidityPercent.roundToInt()}% RH"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WeatherMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "DENSITY ALT",
                    value = "${air.densityAltitudeFt.roundToInt()} ft"
                )
                WeatherMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "AIR DENSITY",
                    value = String.format(Locale.US, "%.1f%%", air.densityRatio * 100.0)
                )
                WeatherMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "MACH 1",
                    value = "${air.speedOfSoundFps.roundToInt()} fps"
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalWindCard(
    atmo: Atmosphere,
    prefs: UserPreferences,
    viewModel: BallisticsViewModel
) {
    val windUnit = prefs.windUnit
    val maxWindDisplay = remember(windUnit) {
        UnitFormatters.windValue(35.0, windUnit).toFloat().coerceAtLeast(10f)
    }
    val currentWindDisplay = remember(atmo.windSpeedMph, windUnit) {
        UnitFormatters.windValue(atmo.windSpeedMph, windUnit).toFloat().coerceIn(0f, maxWindDisplay)
    }
    val windPresetsMph = remember { listOf(0.0, 5.0, 10.0, 15.0, 20.0) }

    val derivedCompassFromDeg = remember(atmo.shootingBearingDeg, atmo.windDirectionDeg) {
        ((atmo.shootingBearingDeg + atmo.windDirectionDeg) % 360.0 + 360.0) % 360.0
    }

    var bearingText by remember { mutableStateOf(atmo.shootingBearingDeg.roundToInt().toString()) }
    LaunchedEffect(atmo.shootingBearingDeg) {
        val parsed = bearingText.toDoubleOrNull()
        if (parsed == null || abs(parsed - atmo.shootingBearingDeg) > 0.5) {
            bearingText = atmo.shootingBearingDeg.roundToInt().toString()
        }
    }

    var windClockText by remember { mutableStateOf(atmo.windDirectionDeg.roundToInt().toString()) }
    LaunchedEffect(atmo.windDirectionDeg) {
        val parsed = windClockText.toDoubleOrNull()
        if (parsed == null || abs(parsed - atmo.windDirectionDeg) > 0.5) {
            windClockText = atmo.windDirectionDeg.roundToInt().toString()
        }
    }

    var windCompassText by remember { mutableStateOf(derivedCompassFromDeg.roundToInt().toString()) }
    LaunchedEffect(derivedCompassFromDeg) {
        val parsed = windCompassText.toDoubleOrNull()
        if (parsed == null || abs(parsed - derivedCompassFromDeg) > 0.7) {
            windCompassText = derivedCompassFromDeg.roundToInt().toString()
        }
    }

    var gustText by remember(windUnit) {
        mutableStateOf(
            if (atmo.windGustMph > 0.5) String.format(Locale.US, "%.0f", UnitFormatters.windValue(atmo.windGustMph, windUnit))
            else ""
        )
    }
    LaunchedEffect(atmo.windGustMph, windUnit) {
        val dispGust = UnitFormatters.windValue(atmo.windGustMph, windUnit)
        val parsed = gustText.toDoubleOrNull() ?: 0.0
        if (atmo.windGustMph <= 0.5 && gustText.isNotEmpty() && parsed > 0.5) {
            gustText = ""
        } else if (atmo.windGustMph > 0.5 && abs(parsed - dispGust) > 0.6) {
            gustText = String.format(Locale.US, "%.0f", dispGust)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (atmo.manualWindOverride) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            else MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Air,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "WIND & TARGET BEARING",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = "${UnitFormatters.windClockHour(atmo.windDirectionDeg)} o'clock",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }

            // GPS Auto vs Manual Wind Override mode switch
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !atmo.manualWindOverride && !atmo.isThreeZoneWind,
                    onClick = { viewModel.setManualWindOverride(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("GPS Auto Wind", maxLines = 1) }
                SegmentedButton(
                    selected = atmo.manualWindOverride || atmo.isThreeZoneWind,
                    onClick = { viewModel.setManualWindOverride(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Manual Override", maxLines = 1) }
            }

            // Single Wind vs 3-Zone Wind (Near 0-33% / Mid 33-66% / Far 66-100%)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !atmo.isThreeZoneWind,
                    onClick = { viewModel.setWindMode(WindMode.SINGLE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Single Wind", maxLines = 1) }
                SegmentedButton(
                    selected = atmo.isThreeZoneWind,
                    onClick = { viewModel.setWindMode(WindMode.THREE_ZONE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("3-Zone (Near/Mid/Far)", maxLines = 1) }
            }

            if (atmo.isThreeZoneWind) {
                val wp = atmo.windProfile ?: WindProfile(
                    mode = WindMode.THREE_ZONE,
                    near = WindZone(atmo.windSpeedMph, atmo.windDirectionDeg),
                    mid = WindZone(atmo.windSpeedMph, atmo.windDirectionDeg),
                    far = WindZone(atmo.windSpeedMph, atmo.windDirectionDeg)
                )
                val avg = wp.rangeWeightedAverage()
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "3-ZONE BALLISTIC WIND ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "LBC1800 Range-Weighted Avg: ${UnitFormatters.formatWind(avg.speedMph, windUnit)} @ ${
                                UnitFormatters.windClockHour(avg.directionDeg)
                            }:00 (${avg.directionDeg.roundToInt()}°)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                listOf(
                    Triple(0, "NEAR (0–33%)", wp.near),
                    Triple(1, "MID (33–66%)", wp.mid),
                    Triple(2, "FAR (66–100%)", wp.far)
                ).forEach { (idx, title, zone) ->
                    WindZoneEditorRow(
                        title = title,
                        zone = zone,
                        windUnit = windUnit,
                        onSpeedMphChange = { spd -> viewModel.updateWindZone(idx, speedMph = spd) },
                        onDirectionDegChange = { deg -> viewModel.updateWindZone(idx, directionDeg = deg) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WeatherMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "WIND",
                    value = UnitFormatters.formatWind(atmo.windSpeedMph, windUnit)
                )
                WeatherMetricBox(
                    modifier = Modifier.weight(1f),
                    label = "GUSTS",
                    value = if (atmo.windGustMph > 0.5) {
                        UnitFormatters.formatWind(atmo.windGustMph, windUnit)
                    } else "—"
                )
                WeatherMetricBox(
                    modifier = Modifier.weight(1f),
                    label = if (atmo.manualWindOverride) "MANUAL FROM" else "GPS FROM",
                    value = if (atmo.manualWindOverride) {
                        "${WeatherService.compassCardinal(derivedCompassFromDeg)} ${derivedCompassFromDeg.roundToInt()}°"
                    } else {
                        atmo.meteoWindFromDeg?.let {
                            "${WeatherService.compassCardinal(it)} ${it.roundToInt()}°"
                        } ?: "Manual"
                    }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Target Bearing (${WeatherService.compassCardinal(atmo.shootingBearingDeg)} ${atmo.shootingBearingDeg.roundToInt()}°)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        0.0 to "N", 45.0 to "NE", 90.0 to "E", 135.0 to "SE",
                        180.0 to "S", 225.0 to "SW", 270.0 to "W", 315.0 to "NW"
                    ).forEach { (deg, label) ->
                        FilterChip(
                            selected = abs(atmo.shootingBearingDeg - deg) < 1.0,
                            onClick = { viewModel.setShootingBearingDeg(deg) },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = bearingText,
                    onValueChange = { raw ->
                        bearingText = raw
                        raw.toDoubleOrNull()?.let { deg ->
                            viewModel.setShootingBearingDeg(deg)
                        }
                    },
                    label = { Text("Exact Target Bearing (° True / Azimuth 0–359°)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                WeatherWindClock(
                    windDirectionDeg = atmo.windDirectionDeg,
                    onDirectionSelected = { deg -> viewModel.setManualWindDirectionDeg(deg) },
                    modifier = Modifier.size(128.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Wind Speed (${windUnit.shortLabel})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = currentWindDisplay,
                        onValueChange = { displayVal ->
                            val mph = UnitFormatters.windToMph(displayVal.toDouble(), windUnit)
                            viewModel.updateAtmosphere {
                                it.copy(windSpeedMph = mph.coerceIn(0.0, 40.0), manualWindOverride = true)
                            }
                        },
                        valueRange = 0f..maxWindDisplay
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        windPresetsMph.forEach { presetMph ->
                            val chipLabel = if (presetMph == 0.0) {
                                "Calm"
                            } else {
                                val converted = UnitFormatters.windValue(presetMph, windUnit)
                                val num = if (windUnit == WindUnit.MPS) {
                                    String.format(Locale.US, "%.1f", converted)
                                } else {
                                    converted.roundToInt().toString()
                                }
                                "$num ${windUnit.shortLabel}"
                            }
                            AssistChip(
                                onClick = {
                                    viewModel.updateAtmosphere {
                                        it.copy(windSpeedMph = presetMph, manualWindOverride = true)
                                    }
                                },
                                label = { Text(chipLabel) }
                            )
                        }
                    }
                }
            }

            // Direct Numeric Local Spot Wind & Gust Override
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = windClockText,
                    onValueChange = { raw ->
                        windClockText = raw
                        raw.toDoubleOrNull()?.let { deg -> viewModel.setManualWindDirectionDeg(deg) }
                    },
                    label = { Text("Clock (°)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = windCompassText,
                    onValueChange = { raw ->
                        windCompassText = raw
                        raw.toDoubleOrNull()?.let { deg -> viewModel.setManualWindFromCompassDeg(deg) }
                    },
                    label = { Text("From (°)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = gustText,
                    onValueChange = { raw ->
                        gustText = raw
                        if (raw.isBlank()) {
                            viewModel.updateAtmosphere { it.copy(windGustMph = 0.0, manualWindOverride = true) }
                        } else {
                            raw.toDoubleOrNull()?.let { dispVal ->
                                val mph = UnitFormatters.windToMph(dispVal, windUnit).coerceIn(0.0, 60.0)
                                viewModel.updateAtmosphere { it.copy(windGustMph = mph, manualWindOverride = true) }
                            }
                        }
                    },
                    label = { Text("Gust (${windUnit.shortLabel})") },
                    placeholder = { Text("0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun WindZoneEditorRow(
    title: String,
    zone: WindZone,
    windUnit: WindUnit,
    onSpeedMphChange: (Double) -> Unit,
    onDirectionDegChange: (Double) -> Unit
) {
    var speedText by remember(windUnit) {
        mutableStateOf(String.format(Locale.US, "%.1f", UnitFormatters.windValue(zone.speedMph, windUnit)))
    }
    LaunchedEffect(zone.speedMph, windUnit) {
        val disp = UnitFormatters.windValue(zone.speedMph, windUnit)
        val parsed = speedText.toDoubleOrNull()
        if (parsed == null || abs(parsed - disp) > 0.25) {
            speedText = String.format(Locale.US, "%.1f", disp)
        }
    }

    var dirText by remember { mutableStateOf(zone.directionDeg.roundToInt().toString()) }
    LaunchedEffect(zone.directionDeg) {
        val parsed = dirText.toDoubleOrNull()
        if (parsed == null || abs(parsed - zone.directionDeg) > 0.7) {
            dirText = zone.directionDeg.roundToInt().toString()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "${UnitFormatters.formatWind(zone.speedMph, windUnit)} @ ${UnitFormatters.windClockHour(zone.directionDeg)}:00",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = speedText,
                    onValueChange = { raw ->
                        speedText = raw
                        raw.toDoubleOrNull()?.let { v ->
                            onSpeedMphChange(UnitFormatters.windToMph(v, windUnit).coerceIn(0.0, 50.0))
                        }
                    },
                    label = { Text("Speed (${windUnit.shortLabel})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = dirText,
                    onValueChange = { raw ->
                        dirText = raw
                        raw.toDoubleOrNull()?.let { deg -> onDirectionDegChange(deg) }
                    },
                    label = { Text("Clock (° 0–359)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ManualAtmosphereCard(
    atmo: Atmosphere,
    prefs: UserPreferences,
    viewModel: BallisticsViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Thermostat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "MANUAL ATMOSPHERE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            ManualAtmosphereInputs(
                tempF = atmo.temperatureF,
                pressureInHg = atmo.pressureInHg,
                humidity = atmo.humidityPercent,
                weatherUpdatedAt = atmo.weatherUpdatedAt,
                tempUnit = prefs.temperatureUnit,
                pressureUnit = prefs.pressureUnit,
                onTempFChange = { v -> viewModel.updateAtmosphere { it.copy(temperatureF = v) } },
                onPressureInHgChange = { v ->
                    viewModel.updateAtmosphere { it.copy(pressureInHg = v, pressureIsStation = true) }
                },
                onHumidityChange = { v ->
                    viewModel.updateAtmosphere { it.copy(humidityPercent = v.coerceIn(0.0, 100.0)) }
                }
            )
        }
    }
}

@Composable
private fun WeatherMetricBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun WeatherWindClock(
    windDirectionDeg: Double,
    onDirectionSelected: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val ringColor = MaterialTheme.colorScheme.outline
    val arrowColor = MaterialTheme.colorScheme.secondary
    val dotColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.surfaceVariant
    val latestOnDirectionSelected by rememberUpdatedState(onDirectionSelected)

    Box(
        modifier = modifier
            .background(bgColor, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val dx = offset.x - cx
                    val dy = offset.y - cy
                    val angleRad = atan2(dx.toDouble(), -dy.toDouble())
                    val deg = (Math.toDegrees(angleRad) + 360.0) % 360.0
                    val snapped = ((deg / 30.0).roundToInt() * 30.0) % 360.0
                    latestOnDirectionSelected(snapped)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(ringColor, radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))
            for (h in 0 until 12) {
                val rad = Math.toRadians(h * 30.0)
                val inner = if (h % 3 == 0) radius * 0.76f else radius * 0.86f
                drawLine(
                    color = ringColor,
                    start = Offset(center.x + inner * sin(rad).toFloat(), center.y - inner * cos(rad).toFloat()),
                    end = Offset(center.x + radius * sin(rad).toFloat(), center.y - radius * cos(rad).toFloat()),
                    strokeWidth = if (h % 3 == 0) 2.5.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            val rad = Math.toRadians(windDirectionDeg)
            val outerPt = Offset(center.x + radius * 0.85f * sin(rad).toFloat(), center.y - radius * 0.85f * cos(rad).toFloat())
            val innerPt = Offset(center.x - radius * 0.25f * sin(rad).toFloat(), center.y + radius * 0.25f * cos(rad).toFloat())
            drawLine(arrowColor, start = outerPt, end = innerPt, strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(dotColor, radius = 6.dp.toPx(), center = outerPt)
            drawCircle(arrowColor, radius = 3.5.dp.toPx(), center = center)
        }
    }
}

@Composable
private fun ManualAtmosphereInputs(
    tempF: Double,
    pressureInHg: Double,
    humidity: Double,
    weatherUpdatedAt: String?,
    tempUnit: TemperatureUnit,
    pressureUnit: PressureUnit,
    onTempFChange: (Double) -> Unit,
    onPressureInHgChange: (Double) -> Unit,
    onHumidityChange: (Double) -> Unit
) {
    val displayTemp = if (tempUnit == TemperatureUnit.CELSIUS) (tempF - 32.0) * 5.0 / 9.0 else tempF
    val displayPress = if (pressureUnit == PressureUnit.HPA) pressureInHg * UnitFormatters.INHG_TO_HPA else pressureInHg

    // Key only on unit switches and new GPS weather syncs so typing decimals like "29." isn't overwritten.
    var tempText by remember(tempUnit, weatherUpdatedAt) {
        mutableStateOf(String.format(Locale.US, "%.0f", displayTemp))
    }
    var pressText by remember(pressureUnit, weatherUpdatedAt) {
        mutableStateOf(
            if (pressureUnit == PressureUnit.HPA) String.format(Locale.US, "%.0f", displayPress)
            else String.format(Locale.US, "%.2f", displayPress)
        )
    }
    var humText by remember(weatherUpdatedAt) {
        mutableStateOf(String.format(Locale.US, "%.0f", humidity))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = tempText,
            onValueChange = { raw ->
                tempText = raw
                raw.toDoubleOrNull()?.let { v ->
                    val inF = if (tempUnit == TemperatureUnit.CELSIUS) v * 9.0 / 5.0 + 32.0 else v
                    onTempFChange(inF.coerceIn(-60.0, 140.0))
                }
            },
            label = { Text("Temp (${tempUnit.shortLabel})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )

        OutlinedTextField(
            value = pressText,
            onValueChange = { raw ->
                pressText = raw
                raw.toDoubleOrNull()?.let { v ->
                    val inHg = if (pressureUnit == PressureUnit.HPA) v / UnitFormatters.INHG_TO_HPA else v
                    if (inHg in 15.0..34.0) onPressureInHgChange(inHg)
                }
            },
            label = { Text("Press (${pressureUnit.shortLabel})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )

        OutlinedTextField(
            value = humText,
            onValueChange = { raw ->
                humText = raw
                raw.toDoubleOrNull()?.let { onHumidityChange(it) }
            },
            label = { Text("RH %") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(0.8f)
        )
    }
}
