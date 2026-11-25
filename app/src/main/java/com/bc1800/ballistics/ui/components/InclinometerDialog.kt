package com.bc1800.ballistics.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Precision Shooting Inclination Modal combining:
 * 1) Live Phone Sensor Inclinometer (TYPE_ROTATION_VECTOR with accelerometer fallback) + Hold-to-Lock
 * 2) Direct manual numeric entry, presets, and cosine factor readout
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InclinationAngleDialog(
    initialAngleDeg: Double,
    onDismiss: () -> Unit,
    onApplyAngleDeg: (Double) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var selectedAngle by remember { mutableDoubleStateOf(initialAngleDeg.coerceIn(-60.0, 60.0)) }
    var manualText by remember { mutableStateOf(String.format(Locale.US, "%.0f", initialAngleDeg)) }
    var isHoldingMeasure by remember { mutableStateOf(false) }
    var sightMode by remember { mutableIntStateOf(0) } // 0 = Top Edge / Rail, 1 = Back Sight / Upright
    var sensorAvailable by remember { mutableStateOf(true) }
    var liveSensorDeg by remember { mutableDoubleStateOf(initialAngleDeg) }

    DisposableEffect(isHoldingMeasure, sightMode) {
        if (!isHoldingMeasure) {
            return@DisposableEffect onDispose {}
        }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val activeSensor = rotSensor ?: accSensor
        if (sensorManager == null || activeSensor == null) {
            sensorAvailable = false
            return@DisposableEffect onDispose {}
        }
        sensorAvailable = true

        val rotMatrix = FloatArray(9)
        var filtered = selectedAngle
        var initialized = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rawDeg = when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                        // Row 2 of R is world Up vector in device frame: [R[6], R[7], R[8]]
                        val sinPitch = if (sightMode == 0) {
                            rotMatrix[7].toDouble() // +Y (top edge) aimed at target
                        } else {
                            -rotMatrix[8].toDouble() // -Z (back of phone) aimed at target
                        }
                        Math.toDegrees(asin(sinPitch.coerceIn(-1.0, 1.0)))
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        val ax = event.values[0].toDouble()
                        val ay = event.values[1].toDouble()
                        val az = event.values[2].toDouble()
                        if (sightMode == 0) {
                            Math.toDegrees(atan2(ay, sqrt(ax * ax + az * az)))
                        } else {
                            Math.toDegrees(atan2(-az, sqrt(ax * ax + ay * ay)))
                        }
                    }
                    else -> return
                }.coerceIn(-60.0, 60.0)

                filtered = if (!initialized) {
                    initialized = true
                    rawDeg
                } else {
                    filtered + 0.22 * (rawDeg - filtered)
                }
                liveSensorDeg = filtered
                selectedAngle = (filtered * 10.0).roundToInt() / 10.0
                manualText = String.format(Locale.US, "%.1f", selectedAngle)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, activeSensor, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    val cosineFactor = cos(Math.toRadians(selectedAngle))
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outline

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SwapVert, contentDescription = null, tint = primaryColor)
                Spacer(Modifier.width(8.dp))
                Text("Shot Inclination Angle")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Live Pitch Clinometer Gauge
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(
                        1.dp,
                        if (isHoldingMeasure) secondaryColor else outlineColor
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (isHoldingMeasure) "MEASURING LIVE..." else "LOCKED ANGLE",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isHoldingMeasure) secondaryColor else primaryColor
                            )
                            Text(
                                text = String.format(Locale.US, "%+.1f°", selectedAngle),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = String.format(Locale.US, "Cosine: %.3f (%.0f%% horiz)", cosineFactor, cosineFactor * 100.0),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Compact clinometer horizon reticle
                        Box(modifier = Modifier.size(64.dp)) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val r = size.minDimension * 0.44f
                                drawLine(
                                    outlineColor.copy(alpha = 0.6f),
                                    Offset(cx - r, cy),
                                    Offset(cx + r, cy),
                                    1.5.dp.toPx()
                                )
                                val rad = Math.toRadians(selectedAngle)
                                val dx = (r * cos(rad)).toFloat()
                                val dy = (-r * sin(rad)).toFloat()
                                drawLine(
                                    if (isHoldingMeasure) secondaryColor else primaryColor,
                                    Offset(cx - dx * 0.35f, cy - dy * 0.35f),
                                    Offset(cx + dx, cy + dy),
                                    3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                drawCircle(
                                    color = if (isHoldingMeasure) secondaryColor else primaryColor,
                                    radius = 3.5.dp.toPx(),
                                    center = Offset(cx, cy)
                                )
                            }
                        }
                    }
                }

                // Sensor orientation mode
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = sightMode == 0,
                        onClick = { sightMode = 0 },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("Rail / Top Edge", maxLines = 1) }
                    SegmentedButton(
                        selected = sightMode == 1,
                        onClick = { sightMode = 1 },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("Sight / Upright", maxLines = 1) }
                }

                // Hold-to-Lock Sensor Button
                Surface(
                    color = if (isHoldingMeasure) secondaryColor else MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.5.dp, if (isHoldingMeasure) secondaryColor else primaryColor),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isHoldingMeasure = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    tryAwaitRelease()
                                    isHoldingMeasure = false
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ScreenRotation,
                            contentDescription = null,
                            tint = if (isHoldingMeasure) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isHoldingMeasure) "Release to Lock Angle" else "Hold to Measure with Phone",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isHoldingMeasure) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (!sensorAvailable) {
                    Text(
                        text = "Phone orientation sensor unavailable on this device — use manual entry below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Manual Numeric Entry + Presets + Slider
                OutlinedTextField(
                    value = manualText,
                    onValueChange = { raw ->
                        manualText = raw
                        raw.toDoubleOrNull()?.let { v ->
                            selectedAngle = v.coerceIn(-60.0, 60.0)
                        }
                    },
                    label = { Text("Manual Angle (-60° down to +60° up)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Slider(
                    value = selectedAngle.toFloat(),
                    onValueChange = {
                        val snapped = it.roundToInt().toDouble()
                        selectedAngle = snapped
                        manualText = String.format(Locale.US, "%.0f", snapped)
                    },
                    valueRange = -60f..60f
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(-30.0, -15.0, 0.0, 15.0, 30.0).forEach { preset ->
                        FilterChip(
                            selected = kotlin.math.abs(selectedAngle - preset) < 0.5,
                            onClick = {
                                selectedAngle = preset
                                manualText = String.format(Locale.US, "%.0f", preset)
                            },
                            label = { Text(if (preset == 0.0) "0° Flat" else String.format(Locale.US, "%+.0f°", preset)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApplyAngleDeg(selectedAngle) }) {
                Text("Apply Angle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
