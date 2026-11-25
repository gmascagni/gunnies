package com.gunnies.ballistics.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gunnies.ballistics.R
import com.gunnies.ballistics.ble.BleConnectionState
import com.gunnies.ballistics.data.UnitFormatters
import com.gunnies.ballistics.engine.BallisticEngine
import com.gunnies.ballistics.model.AngularUnit
import com.gunnies.ballistics.model.AppThemeMode
import com.gunnies.ballistics.model.Atmosphere
import com.gunnies.ballistics.model.BallisticSolution
import com.gunnies.ballistics.model.HitProbabilityResult
import com.gunnies.ballistics.model.MultiPointTruingResult
import com.gunnies.ballistics.model.ShotUncertaintyInput
import com.gunnies.ballistics.model.TruingObservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.gunnies.ballistics.model.DistanceUnit
import com.gunnies.ballistics.model.DsfPoint
import com.gunnies.ballistics.model.FirearmCategory
import com.gunnies.ballistics.ui.AppUiState
import com.gunnies.ballistics.ui.BallisticsViewModel
import com.gunnies.ballistics.ui.components.GunneryHudPanel
import com.gunnies.ballistics.ui.components.HardwareThumbBadge
import com.gunnies.ballistics.ui.components.InclinationAngleDialog
import com.gunnies.ballistics.ui.components.LogShotDialog
import com.gunnies.ballistics.ui.components.ReticleViewCard
import com.gunnies.ballistics.ui.components.TurretDialGauge
import com.gunnies.ballistics.ui.components.rememberChamferedHudShape
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolverScreen(
    uiState: AppUiState,
    viewModel: BallisticsViewModel,
    onRequestLocationWeather: () -> Unit,
    onOpenWeatherTab: () -> Unit,
    onOpenRangefinderTab: () -> Unit,
    onOpenLogbook: () -> Unit
) {
    val prefs = uiState.preferences
    val active = uiState.activeProfile
    val activeGun = uiState.activeFirearm
    val activeScope = uiState.activeScope
    val activeAmmo = uiState.activeAmmo
    val input = uiState.solverInput
    val sol = uiState.solution

    val isNightRed = prefs.themeMode == AppThemeMode.NIGHT_RED
    val isShortRange = activeAmmo.caliber.contains("22 LR", ignoreCase = true) ||
        activeAmmo.caliber.contains("9mm", ignoreCase = true) ||
        activeGun.category == FirearmCategory.HANDGUN

    var showRigPickerSheet by remember { mutableStateOf(false) }
    var showRangeInputDialog by remember { mutableStateOf(false) }
    var showCalibrationDialog by remember { mutableStateOf(false) }
    var showInclinometerDialog by remember { mutableStateOf(false) }
    var showLogShotDialog by remember { mutableStateOf(false) }
    var showHitProbDialog by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 600.dp

        if (isExpanded) {
            // Unfolded Galaxy Z Fold 2-Column Precision Ballistic Cockpit
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1.06f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        CompactRigStrip(
                            uiState = uiState,
                            onClick = { showRigPickerSheet = true }
                        )
                    }
                    if (sol != null) {
                        item {
                            FiringSolutionPanel(
                                uiState = uiState,
                                sol = sol,
                                isShortRange = isShortRange,
                                onSelectAngularUnit = { viewModel.setAngularUnit(it) },
                                onOpenInclinometer = { showInclinometerDialog = true },
                                onOpenTruing = { showCalibrationDialog = true },
                                onOpenHitProb = { showHitProbDialog = true },
                                onOpenLogShot = { showLogShotDialog = true },
                                onOpenLogbook = onOpenLogbook,
                                onOpenRangefinderTab = onOpenRangefinderTab
                            )
                        }
                    }
                    item {
                        RangeDialPanel(
                            rangeYards = input.rangeYards,
                            inclinationDeg = input.inclinationDeg,
                            moverSpeedMph = input.moverSpeedMph,
                            distanceUnit = prefs.distanceUnit,
                            isShortRange = isShortRange,
                            onRangeYardsChange = { viewModel.setRangeYards(it) },
                            onNudgeYards = { viewModel.nudgeRangeYards(it) },
                            onInclinationChange = { viewModel.setInclinationDeg(it) },
                            onOpenInclinometer = { showInclinometerDialog = true },
                            onMoverChange = { viewModel.setMoverSpeedMph(it) },
                            onTapLoupe = { showRangeInputDialog = true }
                        )
                    }
                    item {
                        WeatherQuickBar(
                            uiState = uiState,
                            onOpenWeatherTab = onOpenWeatherTab,
                            onSyncGps = onRequestLocationWeather
                        )
                    }
                }

                // Pinned Right Column: Live FFP Scope Reticle Viewer
                LazyColumn(
                    modifier = Modifier
                        .weight(0.94f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (sol != null) {
                        item {
                            ReticleViewCard(
                                scope = activeScope,
                                solution = sol,
                                isNightRed = isNightRed,
                                initiallyExpanded = true,
                                onChangeScopeReticle = { newReticle ->
                                    viewModel.saveScope(activeScope.copy(reticleType = newReticle))
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // Folded Cover Screen Single-Column Layout
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    CompactRigStrip(
                        uiState = uiState,
                        onClick = { showRigPickerSheet = true }
                    )
                }
                if (sol != null) {
                    item {
                        FiringSolutionPanel(
                            uiState = uiState,
                            sol = sol,
                            isShortRange = isShortRange,
                            onSelectAngularUnit = { viewModel.setAngularUnit(it) },
                            onOpenInclinometer = { showInclinometerDialog = true },
                            onOpenTruing = { showCalibrationDialog = true },
                            onOpenHitProb = { showHitProbDialog = true },
                            onOpenLogShot = { showLogShotDialog = true },
                            onOpenLogbook = onOpenLogbook,
                            onOpenRangefinderTab = onOpenRangefinderTab
                        )
                    }
                }
                item {
                    RangeDialPanel(
                        rangeYards = input.rangeYards,
                        inclinationDeg = input.inclinationDeg,
                        moverSpeedMph = input.moverSpeedMph,
                        distanceUnit = prefs.distanceUnit,
                        isShortRange = isShortRange,
                        onRangeYardsChange = { viewModel.setRangeYards(it) },
                        onNudgeYards = { viewModel.nudgeRangeYards(it) },
                        onInclinationChange = { viewModel.setInclinationDeg(it) },
                        onOpenInclinometer = { showInclinometerDialog = true },
                        onMoverChange = { viewModel.setMoverSpeedMph(it) },
                        onTapLoupe = { showRangeInputDialog = true }
                    )
                }
                if (sol != null) {
                    item {
                        ReticleViewCard(
                            scope = activeScope,
                            solution = sol,
                            isNightRed = isNightRed,
                            initiallyExpanded = true,
                            onChangeScopeReticle = { newReticle ->
                                viewModel.saveScope(activeScope.copy(reticleType = newReticle))
                            }
                        )
                    }
                }
                item {
                    WeatherQuickBar(
                        uiState = uiState,
                        onOpenWeatherTab = onOpenWeatherTab,
                        onSyncGps = onRequestLocationWeather
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    // Rig Picker Bottom Sheet (Gun | Scope | Ammo | Can)
    if (showRigPickerSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showRigPickerSheet = false },
            sheetState = sheetState
        ) {
            RigPickerSheetContent(
                uiState = uiState,
                onSelectGun = { viewModel.selectActiveFirearm(it) },
                onSelectScope = { viewModel.selectActiveScope(it) },
                onSelectAmmo = { viewModel.selectActiveAmmo(it) },
                onSelectSuppressor = { viewModel.selectActiveSuppressor(it) },
                onDone = { showRigPickerSheet = false }
            )
        }
    }

    // Direct Numeric Range Input Dialog (opened by tapping the Magnifier Loupe!)
    if (showRangeInputDialog) {
        DirectRangeDialog(
            currentRangeYards = input.rangeYards,
            distanceUnit = prefs.distanceUnit,
            onDismiss = { showRangeInputDialog = false },
            onApplyYards = {
                viewModel.setRangeYards(it)
                showRangeInputDialog = false
            }
        )
    }

    // Multi-Point MV/BC, Transonic DSF & Tall-Target Optic SSF Calibration Dialog
    if (showCalibrationDialog && sol != null) {
        BallisticCalibrationDialog(
            uiState = uiState,
            currentSolution = sol,
            onDismiss = { showCalibrationDialog = false },
            onApplyMultiPoint = { result ->
                viewModel.applyMultiPointTruing(result)
                showCalibrationDialog = false
            },
            onApplyTruedDsf = { point ->
                viewModel.applyTruedDsf(point)
                showCalibrationDialog = false
            },
            onClearDsf = {
                viewModel.clearActiveAmmoDsf()
            },
            onApplyScopeSsf = { elevSsf, windSsf ->
                viewModel.applyScopeSsf(elevationSsf = elevSsf, windageSsf = windSsf)
                showCalibrationDialog = false
            }
        )
    }

    // Hit Probability & Uncertainty Analysis Dialog
    if (showHitProbDialog && sol != null) {
        HitProbabilityDialog(
            uiState = uiState,
            currentSolution = sol,
            onDismiss = { showHitProbDialog = false }
        )
    }

    // Phone Sensor Inclinometer + Manual Angle Override Dialog
    if (showInclinometerDialog) {
        InclinationAngleDialog(
            initialAngleDeg = input.inclinationDeg,
            onDismiss = { showInclinometerDialog = false },
            onApplyAngleDeg = { deg ->
                viewModel.setInclinationDeg(deg)
                showInclinometerDialog = false
            }
        )
    }

    // One-Tap Cold Bore / Range Session Shot Logger
    if (showLogShotDialog && sol != null) {
        LogShotDialog(
            uiState = uiState,
            solution = sol,
            onDismiss = { showLogShotDialog = false },
            onSaveLog = { actE, actW, coldBore, notes ->
                viewModel.logShot(actE, actW, coldBore, notes)
                showLogShotDialog = false
            }
        )
    }
}

@Composable
private fun CompactRigStrip(
    uiState: AppUiState,
    onClick: () -> Unit
) {
    val prefs = uiState.preferences
    val gun = uiState.activeFirearm
    val scope = uiState.activeScope
    val ammo = uiState.activeAmmo
    val isNightRed = prefs.themeMode == AppThemeMode.NIGHT_RED
    val gunThumb = if (gun.category == FirearmCategory.HANDGUN) R.drawable.img_cat_handgun else R.drawable.img_cat_rifle

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = rememberChamferedHudShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HardwareThumbBadge(
                drawableResId = gunThumb,
                contentDescription = gun.name,
                isNightRed = isNightRed,
                size = 42.dp,
                accentColor = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "${gun.name}  •  ${scope.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val suppSuffix = uiState.activeSuppressor?.let { "  •  ${it.name}" } ?: ""
                Text(
                    text = "${ammo.name}$suppSuffix  •  ${UnitFormatters.formatVelocity(uiState.activeProfile.muzzleVelocityFps, prefs.velocityUnit)}  •  Zero ${
                        UnitFormatters.formatDistance(scope.zeroRangeYards, prefs.distanceUnit)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Change Rig",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiringSolutionPanel(
    uiState: AppUiState,
    sol: BallisticSolution,
    isShortRange: Boolean,
    onSelectAngularUnit: (AngularUnit) -> Unit,
    onOpenInclinometer: () -> Unit,
    onOpenTruing: () -> Unit,
    onOpenHitProb: () -> Unit,
    onOpenLogShot: () -> Unit,
    onOpenLogbook: () -> Unit,
    onOpenRangefinderTab: () -> Unit
) {
    val prefs = uiState.preferences
    val active = uiState.activeProfile
    val inclDeg = uiState.solverInput.inclinationDeg
    val (elevNum, elevDir) = UnitFormatters.formatElevationHold(sol, prefs.angularUnit)
    val (windNum, windDir) = UnitFormatters.formatWindageHold(sol, prefs.angularUnit)

    GunneryHudPanel(
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
        bracketColor = MaterialTheme.colorScheme.primary
    ) {
            // Top bar: Range + Inclination Angle Pill + MOA|MRAD|IN toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.GpsFixed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = UnitFormatters.formatDistance(sol.rangeYards, prefs.distanceUnit),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    // Compact Inclination Angle (°) Pill (taps open Inclinometer / Manual Angle)
                    val hasAngle = abs(inclDeg) >= 0.5
                    Surface(
                        onClick = onOpenInclinometer,
                        shape = RoundedCornerShape(6.dp),
                        color = if (hasAngle) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (hasAngle) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SwapVert,
                                contentDescription = "Shot Angle",
                                modifier = Modifier.size(12.dp),
                                tint = if (hasAngle) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = if (hasAngle) String.format(Locale.US, "%+.0f°", inclDeg) else "0°",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasAngle) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // Compact Active Drag Model Badge (G1 / G7 / Multi-BC / Custom Cd)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (active.usesCustomDragCurve()) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(
                            1.dp,
                            if (active.usesCustomDragCurve()) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                    ) {
                        Text(
                            text = active.activeDragModelHudLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (active.usesCustomDragCurve()) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(AngularUnit.MOA, AngularUnit.MRAD, AngularUnit.INCHES).forEach { unit ->
                        val selected = prefs.angularUnit == unit
                        Surface(
                            onClick = { onSelectAngularUnit(unit) },
                            shape = RoundedCornerShape(6.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = unit.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Quick Gunnery Action Strip: Calibrate (MV/DSF) + 1-Tap Log Shot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onOpenTruing,
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(4.dp))
                        Text("Calibrate", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                Surface(
                    onClick = onOpenHitProb,
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(4.dp))
                        Text("P(Hit)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                Surface(
                    onClick = onOpenLogShot,
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Log Shot",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                if (uiState.sessionLogs.isNotEmpty()) {
                    Surface(
                        onClick = onOpenLogbook,
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = "${uiState.sessionLogs.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Hero Elevation + Windage Rotary Turret Drum Gauges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TurretDialGauge(
                    modifier = Modifier.weight(1f),
                    title = "ELEVATION",
                    direction = elevDir,
                    value = elevNum,
                    unitLabel = prefs.angularUnit.shortLabel,
                    clicks = sol.elevationClicks,
                    subLabel = "${abs(sol.elevationClicks).roundToInt()} clicks (${active.clickUnit.label})",
                    accentColor = MaterialTheme.colorScheme.primary,
                    isElevation = true
                )
                val windSub = buildString {
                    append("${abs(sol.windageClicks).roundToInt()} clks")
                    val gustRaw = when (prefs.angularUnit) {
                        AngularUnit.MOA -> sol.gustWindageMoa
                        AngularUnit.MRAD -> sol.gustWindageMrad
                        else -> null
                    }
                    if (gustRaw != null) {
                        append(" • Gust ${String.format(Locale.US, "%.2f", abs(gustRaw))}")
                    } else {
                        append(" • ${sol.windValuePercent}% val")
                    }
                }
                TurretDialGauge(
                    modifier = Modifier.weight(1f),
                    title = "WINDAGE",
                    direction = windDir,
                    value = windNum,
                    unitLabel = prefs.angularUnit.shortLabel,
                    clicks = sol.windageClicks,
                    subLabel = windSub,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    isElevation = false
                )
            }

            // Moving Target Lead bar (only shown when Mover > 0)
            if (uiState.solverInput.moverSpeedMph > 0.1) {
                val leadVal = when (prefs.angularUnit) {
                    AngularUnit.MRAD -> String.format(Locale.US, "%.2f MRAD", sol.moverLeadMrad)
                    AngularUnit.INCHES -> String.format(Locale.US, "%.1f in", sol.moverLeadInches)
                    AngularUnit.CENTIMETERS -> String.format(Locale.US, "%.1f cm", sol.moverLeadInches * 2.54)
                    AngularUnit.MOA -> String.format(Locale.US, "%.2f MOA", sol.moverLeadMoa)
                }
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "MOVER LEAD (${uiState.solverInput.moverSpeedMph.roundToInt()} mph)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = leadVal,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // Conditional Alerts (only rendered when at least one alert applies — zero phantom gap!)
            val showAngle = abs(sol.angleDegrees) >= 1.0
            val show800 = sol.exceedsOnboard800YdLimit
            val showTransonic = sol.isTransonicWarning && !isShortRange
            val showUnstable = sol.stabilityFactorSg < 1.15
            val showOutOfRange = !sol.reachedTarget

            if (showAngle || show800 || showTransonic || showUnstable || showOutOfRange) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (showOutOfRange) {
                        InfoPill("Out of effective range (projectile stopped short)", isWarning = true)
                    }
                    if (showAngle) {
                        InfoPill(
                            "ARC ${if (sol.angleDegrees > 0) "+" else ""}${sol.angleDegrees.roundToInt()}° → ${
                                UnitFormatters.formatDistance(sol.trueHorizontalYards, prefs.distanceUnit)
                            }",
                            isWarning = false
                        )
                    }
                    if (show800) {
                        InfoPill("Beyond 800 yd LBC1800 limit (Phone Solved)", isWarning = false)
                    }
                    if (showTransonic) {
                        InfoPill("Transonic (Mach ${String.format(Locale.US, "%.2f", sol.mach)})", isWarning = true)
                    }
                    if (showUnstable) {
                        InfoPill("Marginal Sg ${String.format(Locale.US, "%.2f", sol.stabilityFactorSg)}", isWarning = true)
                    }
                }
            }

            // Rigid 3x2 Hairline-Divided Telemetry Matrix (no orphan FlowRow gaps)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        MatrixCell("DROP", UnitFormatters.formatDrop(sol.dropInches, prefs.distanceUnit), Modifier.weight(1f))
                        VerticalDivider(modifier = Modifier.height(44.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        MatrixCell("VELOCITY", UnitFormatters.formatVelocity(sol.velocityFps, prefs.velocityUnit), Modifier.weight(1f))
                        VerticalDivider(modifier = Modifier.height(44.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        MatrixCell("ENERGY", UnitFormatters.formatEnergy(sol.energyFtLbs, prefs.velocityUnit), Modifier.weight(1f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        MatrixCell("TOF", String.format(Locale.US, "%.2f s", sol.timeOfFlightSec), Modifier.weight(1f))
                        VerticalDivider(modifier = Modifier.height(44.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        MatrixCell("MACH / Sg", String.format(Locale.US, "%.2f / %.1f", sol.mach, sol.stabilityFactorSg), Modifier.weight(1f))
                        VerticalDivider(modifier = Modifier.height(44.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        MatrixCell("6\" MPBR", UnitFormatters.formatDistance(uiState.mpbrYards, prefs.distanceUnit), Modifier.weight(1f))
                    }
                }
            }

            if (uiState.bleState.connectionState == BleConnectionState.CONNECTED) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenRangefinderTab() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Icon(
                        Icons.Default.BluetoothConnected,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${uiState.bleState.connectedDeviceName ?: "LBC1800"} Connected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
    }
}

@Composable
private fun MatrixCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RangeDialPanel(
    rangeYards: Double,
    inclinationDeg: Double,
    moverSpeedMph: Double,
    distanceUnit: DistanceUnit,
    isShortRange: Boolean,
    onRangeYardsChange: (Double) -> Unit,
    onNudgeYards: (Double) -> Unit,
    onInclinationChange: (Double) -> Unit,
    onOpenInclinometer: () -> Unit,
    onMoverChange: (Double) -> Unit,
    onTapLoupe: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TARGET RANGE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Tap loupe to type",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RangeTapeMagnifierDial(
                rangeYards = rangeYards,
                distanceUnit = distanceUnit,
                minYards = 10.0,
                maxYards = if (isShortRange) 400.0 else 1500.0,
                onRangeYardsChange = onRangeYardsChange,
                onTapLoupe = onTapLoupe
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(-10.0 to "-10", -1.0 to "-1", 1.0 to "+1", 10.0 to "+10").forEach { (delta, label) ->
                    OutlinedButton(
                        onClick = { onNudgeYards(UnitFormatters.toYards(delta, distanceUnit)) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val presets = if (isShortRange) {
                    listOf(15, 25, 50, 75, 100, 150, 200, 300)
                } else {
                    listOf(100, 200, 300, 400, 500, 600, 800, 1000)
                }
                presets.forEach { preset ->
                    val targetYd = UnitFormatters.toYards(preset.toDouble(), distanceUnit)
                    val selected = abs(rangeYards - targetYd) < 1.5
                    FilterChip(
                        selected = selected,
                        onClick = { onRangeYardsChange(targetYd) },
                        label = { Text("$preset${distanceUnit.shortLabel}") }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ARC Shot Angle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SwapVert,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "ARC ANGLE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${if (inclinationDeg > 0) "+" else ""}${inclinationDeg.roundToInt()}°",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        onClick = onOpenInclinometer,
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ScreenRotation,
                                contentDescription = "Measure with Phone",
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Measure",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    if (abs(inclinationDeg) >= 1.0) {
                        TextButton(
                            onClick = { onInclinationChange(0.0) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) { Text("Flat") }
                    }
                }
            }
            Slider(
                value = inclinationDeg.toFloat(),
                onValueChange = { onInclinationChange(it.roundToInt().toDouble()) },
                valueRange = -60f..60f
            )

            // Moving Target Lead quick selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MOVER",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.0 to "Off", 3.0 to "3", 5.0 to "5", 8.0 to "8 mph").forEach { (spd, lbl) ->
                        FilterChip(
                            selected = abs(moverSpeedMph - spd) < 0.4,
                            onClick = { onMoverChange(spd) },
                            label = { Text(lbl) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Precision Tape-Measure Range Dial with Center Optical Magnifier Loupe.
 * Fixed stale-closure drag tracking via rememberUpdatedState + zero per-frame allocations.
 */
@Composable
private fun RangeTapeMagnifierDial(
    rangeYards: Double,
    distanceUnit: DistanceUnit,
    minYards: Double,
    maxYards: Double,
    onRangeYardsChange: (Double) -> Unit,
    onTapLoupe: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentDisplayVal = UnitFormatters.distanceValue(rangeYards, distanceUnit)
    val latestDisplayVal by rememberUpdatedState(currentDisplayVal)
    val minDisplay = UnitFormatters.distanceValue(minYards, distanceUnit)
    val maxDisplay = UnitFormatters.distanceValue(maxYards, distanceUnit)

    var lastHapticMark by remember { mutableIntStateOf(currentDisplayVal.roundToInt() / 5) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline

    val pxPerUnit = with(density) { 10.dp.toPx() }
    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }
    val railShadeBrush = remember {
        Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.24f), Color.Transparent, Color.Black.copy(alpha = 0.24f))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(98.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(surfaceVariant)
            .pointerInput(distanceUnit, minDisplay, maxDisplay) {
                var dragPos = latestDisplayVal
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragPos = latestDisplayVal
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragPos = (dragPos - dragAmount / pxPerUnit).coerceIn(minDisplay, maxDisplay)
                        val snappedInt = dragPos.roundToInt()
                        val mark = snappedInt / 5
                        if (mark != lastHapticMark) {
                            lastHapticMark = mark
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onRangeYardsChange(UnitFormatters.toYards(snappedInt.toDouble(), distanceUnit))
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val tapeTop = 16.dp.toPx()
            val tapeBottom = height - 16.dp.toPx()

            drawRoundRect(
                brush = railShadeBrush,
                topLeft = Offset(0f, tapeTop),
                size = Size(width, tapeBottom - tapeTop),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )
            drawLine(outlineColor.copy(alpha = 0.55f), Offset(0f, tapeTop), Offset(width, tapeTop), 1.dp.toPx())
            drawLine(outlineColor.copy(alpha = 0.55f), Offset(0f, tapeBottom), Offset(width, tapeBottom), 1.dp.toPx())

            val visibleHalfUnits = (width / 2f / pxPerUnit).toInt() + 4
            val centerUnit = currentDisplayVal
            val startTick = floor(centerUnit - visibleHalfUnits).toInt().coerceAtLeast(minDisplay.toInt())
            val endTick = (centerUnit + visibleHalfUnits).toInt().coerceAtMost(maxDisplay.toInt())

            labelPaint.textSize = 11.dp.toPx()
            val baseLabelRgb = onSurface.toArgb() and 0x00FFFFFF

            for (u in startTick..endTick) {
                val x = centerX + ((u - centerUnit) * pxPerUnit).toFloat()
                val distFromCenter = abs(x - centerX)
                val edgeFade = (1f - (distFromCenter / (width * 0.52f))).coerceIn(0.12f, 1f)

                val isMajor10 = u % 10 == 0
                val isMid5 = u % 5 == 0 && !isMajor10
                val tickHeight = when {
                    isMajor10 -> 23.dp.toPx()
                    isMid5 -> 15.dp.toPx()
                    else -> 8.dp.toPx()
                }
                val tickColor = when {
                    isMajor10 -> primaryColor.copy(alpha = 0.85f * edgeFade)
                    isMid5 -> onSurface.copy(alpha = 0.55f * edgeFade)
                    else -> onSurfaceVariant.copy(alpha = 0.35f * edgeFade)
                }

                drawLine(
                    color = tickColor,
                    start = Offset(x, tapeBottom),
                    end = Offset(x, tapeBottom - tickHeight),
                    strokeWidth = if (isMajor10) 2.dp.toPx() else 1.1.dp.toPx(),
                    cap = StrokeCap.Round
                )
                if (isMajor10 || isMid5) {
                    drawLine(
                        color = tickColor.copy(alpha = tickColor.alpha * 0.65f),
                        start = Offset(x, tapeTop),
                        end = Offset(x, tapeTop + (if (isMajor10) 9.dp.toPx() else 5.dp.toPx())),
                        strokeWidth = 1.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                if (isMajor10 && distFromCenter > 56.dp.toPx()) {
                    labelPaint.color = ((210 * edgeFade).toInt() shl 24) or baseLabelRgb
                    drawContext.canvas.nativeCanvas.drawText(u.toString(), x, tapeTop + 25.dp.toPx(), labelPaint)
                }
            }

            drawLine(primaryColor, Offset(centerX, 2.dp.toPx()), Offset(centerX, 14.dp.toPx()), 2.5.dp.toPx(), StrokeCap.Round)
            drawLine(primaryColor, Offset(centerX, height - 14.dp.toPx()), Offset(centerX, height - 2.dp.toPx()), 2.5.dp.toPx(), StrokeCap.Round)
        }

        // Tappable Center Optical Magnifier Loupe
        Surface(
            onClick = onTapLoupe,
            shape = RoundedCornerShape(16.dp),
            color = surfaceColor.copy(alpha = 0.96f),
            border = BorderStroke(1.8.dp, primaryColor),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = currentDisplayVal.roundToInt().toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = onSurface
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = distanceUnit.shortLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = primaryColor,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun WeatherQuickBar(
    uiState: AppUiState,
    onOpenWeatherTab: () -> Unit,
    onSyncGps: () -> Unit
) {
    val prefs = uiState.preferences
    val atmo = uiState.solverInput.atmosphere
    val isFetching = uiState.solverInput.isFetchingWeather

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenWeatherTab),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.MyLocation,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            val windTag = if (atmo.isThreeZoneWind) {
                "3-Zone ${UnitFormatters.formatWind(atmo.effectiveSingleWind().speedMph, prefs.windUnit)}"
            } else {
                "${UnitFormatters.formatWind(atmo.windSpeedMph, prefs.windUnit)} @ ${
                    UnitFormatters.windClockHour(atmo.windDirectionDeg)
                }:00"
            }
            Text(
                text = "${UnitFormatters.formatTemp(atmo.temperatureF, prefs.temperatureUnit)} • " +
                    "${UnitFormatters.formatPressure(atmo.pressureInHg, prefs.pressureUnit)} • $windTag",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onSyncGps,
                enabled = !isFetching,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.small
            ) {
                if (isFetching) {
                    CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text("GPS", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun InfoPill(text: String, isWarning: Boolean) {
    Surface(
        color = if (isWarning) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        },
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isWarning) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun RigPickerSheetContent(
    uiState: AppUiState,
    onSelectGun: (String) -> Unit,
    onSelectScope: (String) -> Unit,
    onSelectAmmo: (String) -> Unit,
    onSelectSuppressor: (String?) -> Unit,
    onDone: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val activeGun = uiState.activeFirearm

    // Sort ammo putting caliber-compatible loads first
    val gunHintTokens = remember(activeGun.caliberHint) {
        activeGun.caliberHint.lowercase(Locale.US).split("/", " ").filter { it.length >= 2 }
    }
    val sortedAmmo = remember(uiState.ammoLoads, gunHintTokens) {
        uiState.ammoLoads.sortedByDescending { ammo ->
            val cal = ammo.caliber.lowercase(Locale.US)
            val match = gunHintTokens.any { token -> cal.contains(token) }
            (if (match) 2 else 0) + (if (ammo.isFavorite) 1 else 0)
        }
    }

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
            Text("Select Active Rig", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDone) { Text("Done") }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = tab == 0,
                onClick = { tab = 0 },
                shape = SegmentedButtonDefaults.itemShape(0, 4)
            ) { Text("Gun", maxLines = 1) }
            SegmentedButton(
                selected = tab == 1,
                onClick = { tab = 1 },
                shape = SegmentedButtonDefaults.itemShape(1, 4)
            ) { Text("Scope", maxLines = 1) }
            SegmentedButton(
                selected = tab == 2,
                onClick = { tab = 2 },
                shape = SegmentedButtonDefaults.itemShape(2, 4)
            ) { Text("Ammo", maxLines = 1) }
            SegmentedButton(
                selected = tab == 3,
                onClick = { tab = 3 },
                shape = SegmentedButtonDefaults.itemShape(3, 4)
            ) { Text("Can", maxLines = 1) }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (tab) {
                0 -> items(uiState.firearms, key = { it.id }) { gun ->
                    val selected = gun.id == uiState.activeFirearm.id
                    RigSheetRow(
                        title = (if (gun.isFavorite) "★ " else "") + gun.name,
                        subtitle = "${gun.category.label} • ${gun.caliberHint} • 1:${gun.barrelTwistInches.toInt()}\"",
                        selected = selected,
                        onClick = { onSelectGun(gun.id) }
                    )
                }

                1 -> items(uiState.scopes, key = { it.id }) { scope ->
                    val selected = scope.id == uiState.activeScope.id
                    RigSheetRow(
                        title = (if (scope.isFavorite) "★ " else "") + scope.name,
                        subtitle = "${scope.reticleType.label} • ${scope.clickUnit.label} • Zero ${scope.zeroRangeYards.toInt()} yd",
                        selected = selected,
                        onClick = { onSelectScope(scope.id) }
                    )
                }

                2 -> items(sortedAmmo, key = { it.id }) { ammo ->
                    val selected = ammo.id == uiState.activeAmmo.id
                    RigSheetRow(
                        title = (if (ammo.isFavorite) "★ " else "") + ammo.name,
                        subtitle = "${ammo.caliber} • ${ammo.dragModel.label} ${ammo.ballisticCoefficient} • ${ammo.muzzleVelocityFps.toInt()} fps",
                        selected = selected,
                        onClick = { onSelectAmmo(ammo.id) }
                    )
                }

                else -> {
                    item(key = "unsuppressed") {
                        RigSheetRow(
                            title = "Unsuppressed (Bare Muzzle)",
                            subtitle = "0 fps MV shift • 0\" POI shift",
                            selected = uiState.activeSuppressor == null,
                            onClick = { onSelectSuppressor(null) }
                        )
                    }
                    items(uiState.suppressors, key = { it.id }) { supp ->
                        val selected = supp.id == uiState.activeSuppressor?.id
                        val shiftText = buildList {
                            if (abs(supp.mvShiftFps) > 0.1) add(String.format(Locale.US, "%+.0f fps", supp.mvShiftFps))
                            if (abs(supp.poiShiftVerticalInches) > 0.02 || abs(supp.poiShiftHorizontalInches) > 0.02) {
                                add(String.format(Locale.US, "POI %+.1f\"V/%+.1f\"H", supp.poiShiftVerticalInches, supp.poiShiftHorizontalInches))
                            }
                        }.joinToString(" • ")
                        RigSheetRow(
                            title = (if (supp.isFavorite) "★ " else "") + supp.name,
                            subtitle = listOf("${supp.category.label} • ${supp.caliberRating}", shiftText)
                                .filter { it.isNotEmpty() }
                                .joinToString(" • "),
                            selected = selected,
                            onClick = { onSelectSuppressor(supp.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RigSheetRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DirectRangeDialog(
    currentRangeYards: Double,
    distanceUnit: DistanceUnit,
    onDismiss: () -> Unit,
    onApplyYards: (Double) -> Unit
) {
    val startVal = UnitFormatters.distanceValue(currentRangeYards, distanceUnit).roundToInt()
    var text by remember { mutableStateOf(startVal.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Target Distance (${distanceUnit.fullLabel})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    label = { Text("Distance (${distanceUnit.shortLabel})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val v = text.toDoubleOrNull()
                if (v == null || v < 5.0 || v > 1800.0) {
                    error = "Enter a distance between 5 and 1800 ${distanceUnit.shortLabel}."
                } else {
                    onApplyYards(UnitFormatters.toYards(v, distanceUnit))
                }
            }) { Text("Set Range") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private data class EditableTruingRow(
    val id: String,
    val rangeText: String,
    val holdText: String,
    val inclinationDeg: Double = 0.0,
    val atmosphere: Atmosphere? = null,
    val label: String = "Manual",
    val isColdBore: Boolean = false
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BallisticCalibrationDialog(
    uiState: AppUiState,
    currentSolution: BallisticSolution,
    onDismiss: () -> Unit,
    onApplyMultiPoint: (MultiPointTruingResult) -> Unit,
    onApplyTruedDsf: (DsfPoint) -> Unit,
    onClearDsf: () -> Unit,
    onApplyScopeSsf: (elevationSsf: Double?, windageSsf: Double?) -> Unit
) {
    val prefs = uiState.preferences
    val isMrad = prefs.angularUnit == AngularUnit.MRAD
    val unitLabel = if (isMrad) "MRAD" else "MOA"
    val moaFactor = if (isMrad) 3.43774677 else 1.0
    val predictedHold = if (isMrad) currentSolution.elevationMrad else currentSolution.elevationMoa

    // Tab 0 = MV/BC Multi-Point Fit, Tab 1 = DSF (<=M1.2), Tab 2 = Tall Target (Optic SSF)
    var stageTab by remember { mutableIntStateOf(0) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // --- Tab 0: Multi-Point MV + BC state ---
    var fitBcToggle by remember { mutableStateOf(true) }
    var showLogPicker by remember { mutableStateOf(false) }
    var rows by remember {
        mutableStateOf(
            listOf(
                EditableTruingRow(
                    id = "pt-0",
                    rangeText = currentSolution.rangeYards.roundToInt().toString(),
                    holdText = String.format(Locale.US, "%.2f", abs(predictedHold)),
                    inclinationDeg = uiState.solverInput.inclinationDeg,
                    label = "HUD"
                )
            )
        )
    }

    val matchingLogs = remember(uiState.sessionLogs, uiState.activeFirearm.id, uiState.activeAmmo.id) {
        uiState.sessionLogs.filter { entry ->
            entry.firearmId == uiState.activeFirearm.id &&
                entry.ammoId == uiState.activeAmmo.id &&
                entry.targetRangeYards > uiState.activeScope.zeroRangeYards + 10.0 &&
                entry.actualElevation > 0.05
        }.take(12)
    }

    val parsedObservations = remember(rows, moaFactor) {
        rows.mapNotNull { row ->
            val r = row.rangeText.trim().toDoubleOrNull()
            val h = row.holdText.trim().toDoubleOrNull()
            if (r != null && h != null && r > 10.0 && h > 0.05) {
                TruingObservation(
                    rangeYards = r,
                    observedElevationMoa = h * moaFactor,
                    inclinationDeg = row.inclinationDeg,
                    atmosphere = row.atmosphere,
                    label = row.label,
                    isColdBore = row.isColdBore
                )
            } else null
        }
    }

    val solverOptions = remember(prefs.includeSpinDrift, prefs.includeCoriolis, prefs.includeAeroJump) {
        BallisticEngine.SolverOptions(
            includeSpinDrift = prefs.includeSpinDrift,
            includeCoriolis = prefs.includeCoriolis,
            includeAeroJump = prefs.includeAeroJump
        )
    }

    val multiPointResult by produceState<MultiPointTruingResult?>(
        initialValue = null,
        stageTab,
        parsedObservations,
        fitBcToggle,
        uiState.activeProfile,
        uiState.solverInput.atmosphere,
        solverOptions
    ) {
        value = if (stageTab != 0 || parsedObservations.isEmpty()) {
            null
        } else {
            withContext(Dispatchers.Default) {
                BallisticEngine.fitMvAndBc(
                    profile = uiState.activeProfile,
                    atmosphere = uiState.solverInput.atmosphere,
                    observations = parsedObservations,
                    fitBc = fitBcToggle,
                    options = solverOptions
                )
            }
        }
    }

    // --- Tab 1: DSF state ---
    var dsfDistText by remember { mutableStateOf(currentSolution.rangeYards.roundToInt().toString()) }
    var dsfObsText by remember { mutableStateOf(String.format(Locale.US, "%.2f", abs(predictedHold))) }
    val dsfRangeYd = dsfDistText.toDoubleOrNull() ?: currentSolution.rangeYards
    val dsfObsMoa = dsfObsText.toDoubleOrNull()?.let { it * moaFactor }

    val dsfPreviewSol = remember(dsfRangeYd, uiState.activeProfile, uiState.solverInput.atmosphere, uiState.solverInput.inclinationDeg) {
        BallisticEngine.solve(
            profile = uiState.activeProfile,
            atmosphere = uiState.solverInput.atmosphere,
            rangeYards = dsfRangeYd.coerceAtLeast(10.0),
            inclinationDeg = uiState.solverInput.inclinationDeg
        )
    }
    val dsfIsSupersonic = dsfPreviewSol.mach > BallisticEngine.TRANSONIC_MACH_GATE
    val computedDsf = remember(stageTab, dsfRangeYd, dsfObsMoa, uiState.activeProfile, uiState.solverInput.atmosphere) {
        if (stageTab != 1 || dsfObsMoa == null) null
        else BallisticEngine.trueDropScaleFactor(
            profile = uiState.activeProfile,
            atmosphere = uiState.solverInput.atmosphere,
            rangeYards = dsfRangeYd,
            observedElevationMoa = dsfObsMoa,
            inclinationDeg = uiState.solverInput.inclinationDeg
        )
    }

    // --- Tab 2: Tall-Target Optic SSF state ---
    var ttAxisIsWind by remember { mutableStateOf(false) }
    var ttDistText by remember { mutableStateOf("100") }
    var ttDialedText by remember { mutableStateOf(if (isMrad) "10.0" else "30.0") }
    var ttMeasuredInText by remember { mutableStateOf(if (isMrad) "36.00" else "31.42") }
    val ttResult = remember(stageTab, ttDistText, ttDialedText, ttMeasuredInText, prefs.angularUnit) {
        if (stageTab != 2) null
        else {
            val d = ttDistText.toDoubleOrNull()
            val dial = ttDialedText.toDoubleOrNull()
            val meas = ttMeasuredInText.toDoubleOrNull()
            if (d != null && dial != null && meas != null) {
                BallisticEngine.computeTallTargetCalibration(d, dial, meas, prefs.angularUnit)
            } else null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ballistic Calibration") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = stageTab == 0,
                        onClick = { stageTab = 0; errorMsg = null },
                        shape = SegmentedButtonDefaults.itemShape(0, 3)
                    ) { Text("MV / BC", maxLines = 1) }
                    SegmentedButton(
                        selected = stageTab == 1,
                        onClick = { stageTab = 1; errorMsg = null },
                        shape = SegmentedButtonDefaults.itemShape(1, 3)
                    ) { Text("DSF", maxLines = 1) }
                    SegmentedButton(
                        selected = stageTab == 2,
                        onClick = { stageTab = 2; errorMsg = null },
                        shape = SegmentedButtonDefaults.itemShape(2, 3)
                    ) { Text("Tall Target", maxLines = 1) }
                }

                when (stageTab) {
                    0 -> {
                        Text(
                            text = "Enter 1+ observed drops for ${uiState.activeAmmo.name}. Use 2+ distances (e.g., 400 & 800 yd) to jointly fit MV and BC.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = fitBcToggle,
                                onClick = { fitBcToggle = !fitBcToggle },
                                label = { Text(if (fitBcToggle) "Fit MV + BC" else "Fit MV Only") }
                            )
                            if (matchingLogs.isNotEmpty()) {
                                FilterChip(
                                    selected = showLogPicker,
                                    onClick = { showLogPicker = !showLogPicker },
                                    leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                    label = { Text("Logbook (${matchingLogs.size})") }
                                )
                            }
                        }

                        // Optional Logbook / Cold-Bore Shot Importer
                        if (showLogPicker && matchingLogs.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Tap a logged shot to add its range, hold & atmosphere:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    matchingLogs.forEach { entry ->
                                        val alreadyAdded = rows.any { it.id == "log-${entry.id}" }
                                        val obsDisplay = if (entry.angularUnit == prefs.angularUnit) {
                                            entry.actualElevation
                                        } else if (isMrad) {
                                            entry.actualElevation / 3.43774677
                                        } else {
                                            entry.actualElevation * 3.43774677
                                        }
                                        Surface(
                                            onClick = {
                                                if (!alreadyAdded) {
                                                    val obs = BallisticEngine.logEntryToTruingObservation(
                                                        entry = entry,
                                                        fallbackAtmosphere = uiState.solverInput.atmosphere
                                                    )
                                                    rows = rows + EditableTruingRow(
                                                        id = "log-${entry.id}",
                                                        rangeText = entry.targetRangeYards.roundToInt().toString(),
                                                        holdText = String.format(Locale.US, "%.2f", abs(obsDisplay)),
                                                        inclinationDeg = entry.inclinationDeg,
                                                        atmosphere = obs.atmosphere,
                                                        label = if (entry.isColdBore) "Cold Bore" else "Log",
                                                        isColdBore = entry.isColdBore
                                                    )
                                                }
                                            },
                                            color = if (alreadyAdded) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            },
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    if (entry.isColdBore) {
                                                        Icon(
                                                            Icons.Default.AcUnit,
                                                            contentDescription = "Cold Bore",
                                                            tint = MaterialTheme.colorScheme.tertiary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                    }
                                                    Text(
                                                        text = "${entry.targetRangeYards.roundToInt()} yd • ${String.format(Locale.US, "%.2f", obsDisplay)} $unitLabel • ${entry.temperatureF.roundToInt()}°F",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Text(
                                                    text = if (alreadyAdded) "Added" else "+ Add",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Observation rows
                        rows.forEachIndexed { idx, row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = row.rangeText,
                                    onValueChange = { v ->
                                        rows = rows.toMutableList().also { it[idx] = row.copy(rangeText = v) }
                                    },
                                    label = { Text(if (row.isColdBore) "CB yd" else "Dist (yd)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = row.holdText,
                                    onValueChange = { v ->
                                        rows = rows.toMutableList().also { it[idx] = row.copy(holdText = v) }
                                    },
                                    label = { Text("Obs ($unitLabel)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                if (rows.size > 1) {
                                    IconButton(
                                        onClick = { rows = rows.toMutableList().also { it.removeAt(idx) } },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove point", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        if (rows.size < 8) {
                            OutlinedButton(
                                onClick = {
                                    val lastDist = rows.lastOrNull()?.rangeText?.toDoubleOrNull() ?: currentSolution.rangeYards
                                    val nextDist = (lastDist + 200.0).coerceAtMost(1500.0)
                                    val predSol = BallisticEngine.solve(
                                        profile = uiState.activeProfile,
                                        atmosphere = uiState.solverInput.atmosphere,
                                        rangeYards = nextDist,
                                        inclinationDeg = uiState.solverInput.inclinationDeg,
                                        options = solverOptions
                                    )
                                    val predVal = if (isMrad) predSol.elevationMrad else predSol.elevationMoa
                                    rows = rows + EditableTruingRow(
                                        id = "pt-${System.currentTimeMillis()}-${rows.size}",
                                        rangeText = nextDist.roundToInt().toString(),
                                        holdText = String.format(Locale.US, "%.2f", abs(predVal)),
                                        inclinationDeg = uiState.solverInput.inclinationDeg,
                                        label = "Manual"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Distance Point (${rows.size}/8)")
                            }
                        }

                        // Fit Summary + Residual Table
                        val res = multiPointResult
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (res == null) {
                                    Text(
                                        text = "Enter at least one valid range beyond zero (${uiState.activeScope.zeroRangeYards.toInt() + 10} yd+) and observed hold.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    val mvDelta = res.fittedMvFps - uiState.activeProfile.muzzleVelocityFps
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "Trued MV: %d fps (%+.0f fps)",
                                            res.fittedMvFps.roundToInt(),
                                            mvDelta
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    if (res.fittedBothMvAndBc) {
                                        val bcDelta = res.fittedBc - uiState.activeProfile.primaryBc()
                                        Text(
                                            text = String.format(
                                                Locale.US,
                                                "Trued BC: %.3f (%+.3f • %.1f%%)",
                                                res.fittedBc,
                                                bcDelta,
                                                (res.bcScaleFactor - 1.0) * 100.0
                                            ),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    val beforeRmsDisp = res.beforeRmsErrorMoa / moaFactor
                                    val afterRmsDisp = res.rmsErrorMoa / moaFactor
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "RMS Error: %.2f → %.2f %s",
                                            beforeRmsDisp,
                                            afterRmsDisp,
                                            unitLabel
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Text(
                                        text = "Residuals (Range | Obs | Fit | Δ $unitLabel)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    res.residuals.forEach { pt ->
                                        val obsD = pt.observedMoa / moaFactor
                                        val fitD = pt.predictedAfterMoa / moaFactor
                                        val errD = pt.residualAfterMoa / moaFactor
                                        val tag = if (pt.isColdBore) " [CB]" else ""
                                        Text(
                                            text = String.format(
                                                Locale.US,
                                                "%4d yd | %5.2f | %5.2f | %+.2f (M%.2f)%s",
                                                pt.rangeYards.roundToInt(),
                                                obsD,
                                                fitD,
                                                errD,
                                                pt.mach,
                                                tag
                                            ),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    res.warnings.forEach { warn ->
                                        Text(
                                            text = "• $warn",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        Text(
                            text = "Stage 2 (Transonic / Subsonic): Locks MV and calibrates Drop Scale Factor (DSF) at Mach ≤ 1.2 for ${uiState.activeAmmo.name}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = dsfDistText,
                            onValueChange = { dsfDistText = it; errorMsg = null },
                            label = { Text("Validation Distance (yd)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = dsfObsText,
                            onValueChange = { dsfObsText = it; errorMsg = null },
                            label = { Text("Actual Elevation Needed ($unitLabel UP)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "Target Mach: M%.2f (%s) • Current MV: %d fps",
                                        dsfPreviewSol.mach,
                                        if (dsfIsSupersonic) "Supersonic" else "Sub/Transonic",
                                        uiState.activeAmmo.muzzleVelocityFps.roundToInt()
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = when {
                                        dsfIsSupersonic -> "Move to a transonic/subsonic range (Mach ≤ 1.2) for DSF calibration."
                                        computedDsf != null -> String.format(
                                            Locale.US,
                                            "Trued DSF: %.3f @ Mach %.2f (MV Locked)",
                                            computedDsf.dropScaleFactor,
                                            computedDsf.mach
                                        )
                                        else -> "Trued DSF: Enter a valid hold beyond zero range"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (computedDsf != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (uiState.activeAmmo.dsfTable.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Saved DSF: " + uiState.activeAmmo.dsfTable.joinToString(", ") {
                                        String.format(Locale.US, "M%.2f→%.3f", it.mach, it.dropScaleFactor)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onClearDsf) { Text("Reset DSF") }
                            }
                        }
                    }

                    else -> {
                        Text(
                            text = "Tall-Target Optic Test for ${uiState.activeScope.name}. Fire a plumb baseline group, dial a known amount, and measure the true POI shift in inches.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !ttAxisIsWind,
                                onClick = { ttAxisIsWind = false },
                                label = { Text("Elevation Turret") }
                            )
                            FilterChip(
                                selected = ttAxisIsWind,
                                onClick = { ttAxisIsWind = true },
                                label = { Text("Windage Turret") }
                            )
                        }
                        OutlinedTextField(
                            value = ttDistText,
                            onValueChange = { ttDistText = it },
                            label = { Text("Measured Target Distance (yd)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = ttDialedText,
                                onValueChange = { ttDialedText = it },
                                label = { Text("Dialed ($unitLabel)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = ttMeasuredInText,
                                onValueChange = { ttMeasuredInText = it },
                                label = { Text("Measured Shift (in)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "Current Scope SSF: Elev %.3f • Wind %.3f",
                                        uiState.activeScope.elevationSsf,
                                        uiState.activeScope.windageSsf
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (ttResult == null) {
                                    Text(
                                        text = "Enter valid distance (10–1000 yd), dialed amount, and measured inches.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "Expected Shift: %.2f\" • Tracking Error: %+.2f%%",
                                            ttResult.expectedShiftInches,
                                            ttResult.trackingErrorPercent
                                        ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "Computed %s SSF: %.3f",
                                            if (ttAxisIsWind) "Windage" else "Elevation",
                                            ttResult.scaleFactor
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (ttResult.isWithinValidRange) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                                    )
                                    if (!ttResult.isWithinValidRange) {
                                        Text(
                                            text = "SSF outside 0.800–1.200 — double-check distance, dialed units, and tape measurement.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                        if (abs(uiState.activeScope.elevationSsf - 1.0) > 0.001 ||
                            abs(uiState.activeScope.windageSsf - 1.0) > 0.001
                        ) {
                            TextButton(
                                onClick = { onApplyScopeSsf(1.0, 1.0) }
                            ) { Text("Reset Scope SSF to 1.000") }
                        }
                    }
                }

                errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            when (stageTab) {
                0 -> {
                    val res = multiPointResult
                    Button(
                        enabled = res != null,
                        onClick = {
                            if (res != null) onApplyMultiPoint(res)
                            else errorMsg = "Unable to fit MV/BC for these inputs."
                        }
                    ) { Text(if (res?.fittedBothMvAndBc == true) "Apply MV & BC" else "Apply Trued MV") }
                }
                1 -> {
                    Button(
                        enabled = computedDsf != null,
                        onClick = {
                            if (computedDsf != null) onApplyTruedDsf(computedDsf)
                            else errorMsg = "Unable to solve DSF for these inputs."
                        }
                    ) { Text("Apply DSF") }
                }
                else -> {
                    val res = ttResult
                    Button(
                        enabled = res != null && res.isWithinValidRange,
                        onClick = {
                            if (res != null && res.isWithinValidRange) {
                                if (ttAxisIsWind) onApplyScopeSsf(null, res.scaleFactor)
                                else onApplyScopeSsf(res.scaleFactor, null)
                            }
                        }
                    ) { Text("Save Scope SSF") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HitProbabilityDialog(
    uiState: AppUiState,
    currentSolution: BallisticSolution,
    onDismiss: () -> Unit
) {
    val prefs = uiState.preferences
    var plateDiaText by remember { mutableStateOf("10") }
    var mvSdText by remember { mutableStateOf("10") }
    var windErrText by remember { mutableStateOf("2.0") }
    var rangeErrText by remember { mutableStateOf("2.0") }
    var bcErrText by remember { mutableStateOf("2.0") }
    var sysMoaText by remember { mutableStateOf("0.35") }
    var showModelInfo by remember { mutableStateOf(false) }

    val uncertainty = remember(plateDiaText, mvSdText, windErrText, rangeErrText, bcErrText, sysMoaText) {
        ShotUncertaintyInput(
            mvSdFps = (mvSdText.toDoubleOrNull() ?: 10.0).coerceIn(0.0, 250.0),
            windErrorMph = (windErrText.toDoubleOrNull() ?: 2.0).coerceIn(0.0, 25.0),
            rangeErrorYards = (rangeErrText.toDoubleOrNull() ?: 2.0).coerceIn(0.0, 200.0),
            bcErrorPercent = (bcErrText.toDoubleOrNull() ?: 2.0).coerceIn(0.0, 25.0),
            systemPrecisionMoa = (sysMoaText.toDoubleOrNull() ?: 0.35).coerceIn(0.0, 10.0),
            targetDiameterInches = (plateDiaText.toDoubleOrNull() ?: 10.0).coerceIn(0.5, 120.0)
        )
    }

    val solverOptions = remember(prefs.includeSpinDrift, prefs.includeCoriolis, prefs.includeAeroJump) {
        BallisticEngine.SolverOptions(
            includeSpinDrift = prefs.includeSpinDrift,
            includeCoriolis = prefs.includeCoriolis,
            includeAeroJump = prefs.includeAeroJump
        )
    }

    val result by produceState<HitProbabilityResult?>(
        initialValue = null,
        uncertainty,
        currentSolution.rangeYards,
        uiState.activeProfile,
        uiState.solverInput.atmosphere,
        uiState.solverInput.inclinationDeg,
        solverOptions
    ) {
        value = withContext(Dispatchers.Default) {
            BallisticEngine.computeHitProbability(
                profile = uiState.activeProfile,
                atmosphere = uiState.solverInput.atmosphere,
                rangeYards = currentSolution.rangeYards,
                uncertainty = uncertainty,
                inclinationDeg = uiState.solverInput.inclinationDeg,
                options = solverOptions
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GpsFixed, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text("Hit Probability — ${UnitFormatters.formatDistance(currentSolution.rangeYards, prefs.distanceUnit)}")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Hero P(Hit) Banner
                val res = result
                val hitColor = when {
                    res == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    res.hitProbabilityPercent >= 75.0 -> MaterialTheme.colorScheme.secondary
                    res.hitProbabilityPercent >= 40.0 -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.5.dp, hitColor.copy(alpha = 0.75f)),
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
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "ESTIMATED FIRST-ROUND P(HIT)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = res?.let { String.format(Locale.US, "%.0f%%", it.hitProbabilityPercent) } ?: "…",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = hitColor
                                )
                            }
                            if (res != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", res.targetDiameterInches)}\" Circular Plate",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Dominant: ${res.dominantErrorSource}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                        if (res != null) {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Total 1σ Dispersion: Vert ±%.1f\" • Horiz ±%.1f\"",
                                    res.sigmaVerticalInches,
                                    res.sigmaHorizontalInches
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            val breakdown = listOf(
                                "Wind" to res.sigmaWindInches,
                                "Range" to res.sigmaRangeInches,
                                "MV SD" to res.sigmaMvInches,
                                "BC" to res.sigmaBcInches,
                                "Rifle 1σ" to res.sigmaSystemInches
                            )
                            val maxSigma = breakdown.maxOf { it.second }.coerceAtLeast(0.1)
                            breakdown.forEach { (label, sigIn) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.width(54.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth((sigIn / maxSigma).toFloat().coerceIn(0.02f, 1f))
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(if (label == res.dominantErrorSource) hitColor else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                        )
                                    }
                                    Text(
                                        text = String.format(Locale.US, "±%.1f\"", sigIn),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.width(42.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Target Plate Size
                Text("Target Plate Diameter (inches)", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("4", "6", "10", "18", "24").forEach { sz ->
                        FilterChip(
                            selected = plateDiaText == sz,
                            onClick = { plateDiaText = sz },
                            label = { Text("$sz\"") }
                        )
                    }
                }

                // Uncertainty Inputs (1-sigma)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mvSdText,
                        onValueChange = { mvSdText = it },
                        label = { Text("MV SD (fps)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = windErrText,
                        onValueChange = { windErrText = it },
                        label = { Text("Wind ± (mph)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rangeErrText,
                        onValueChange = { rangeErrText = it },
                        label = { Text("Range ± (yd)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = bcErrText,
                        onValueChange = { bcErrText = it },
                        label = { Text("BC ± (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sysMoaText,
                        onValueChange = { sysMoaText = it },
                        label = { Text("Rifle 1σ (MOA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = plateDiaText,
                        onValueChange = { plateDiaText = it },
                        label = { Text("Plate (in)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Model Explanation Toggle
                Surface(
                    onClick = { showModelInfo = !showModelInfo },
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (showModelInfo) "Hide Model Explanation ▲" else "How This Hit-Probability Model Works ▼",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (showModelInfo) {
                            Text(
                                text = "Gunnies perturbs your active 3-DOF+ trajectory by ±1σ for each independent input (MV SD, crosswind call error, range error relative to your dialed line of sight, and BC/drag %), combines their variances with Rifle/Shooter 1σ (0.35 MOA ≈ 0.8–1.0 MOA 5-shot group), and integrates the resulting 2D elliptical Gaussian over your circular plate. Assumes centered zero (no mean bias) and independent errors.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}
