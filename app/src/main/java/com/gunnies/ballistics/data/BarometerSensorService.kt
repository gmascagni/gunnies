package com.gunnies.ballistics.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BarometerReading(
    val pressureHpa: Double,
    val stationPressureInHg: Double,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Optional onboard Android barometer (`Sensor.TYPE_PRESSURE`) reader.
 * Directly measures local station pressure (no altitude conversion needed) without requiring
 * location or network permissions.
 */
class BarometerSensorService(context: Context) {

    companion object {
        const val HPA_PER_INHG = 33.8638866667

        fun hpaToInHg(hpa: Double): Double = hpa / HPA_PER_INHG
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val pressureSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val isAvailable: Boolean
        get() = pressureSensor != null

    private val _latestReading = MutableStateFlow<BarometerReading?>(null)
    val latestReading: StateFlow<BarometerReading?> = _latestReading.asStateFlow()

    private var activeListener: SensorEventListener? = null

    fun startListening(onReading: (BarometerReading) -> Unit) {
        val mgr = sensorManager ?: return
        val sensor = pressureSensor ?: return
        stopListening()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val hpa = event.values.firstOrNull()?.toDouble() ?: return
                val inHg = hpaToInHg(hpa)
                if (inHg in 15.0..34.0) {
                    val reading = BarometerReading(pressureHpa = hpa, stationPressureInHg = inHg)
                    _latestReading.value = reading
                    onReading(reading)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        activeListener = listener
        mgr.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stopListening() {
        val mgr = sensorManager ?: return
        activeListener?.let { mgr.unregisterListener(it) }
        activeListener = null
    }
}
