package com.gunnies.ballistics.ble

import com.gunnies.ballistics.model.Atmosphere
import com.gunnies.ballistics.model.RifleProfile
import kotlinx.coroutines.flow.StateFlow

enum class BleDeviceKind(val label: String) {
    BUSHNELL_LBC1800("Bushnell LBC1800"),
    KESTREL_WEATHER("Kestrel / ESS Weather"),
    GENERIC_BLE("BLE Device")
}

/**
 * Live atmospheric telemetry received from a Bluetooth weather meter (e.g., Kestrel / SIG ESS)
 * or onboard device sensor. Null fields leave the existing value unchanged.
 */
data class LiveAtmosphereReading(
    val temperatureF: Double? = null,
    val stationPressureInHg: Double? = null,
    val humidityPercent: Double? = null,
    val windSpeedMph: Double? = null,
    val sourceLabel: String = "BLE Weather"
)

/**
 * Hardware abstraction for Bluetooth rangefinders and environmental sensors.
 * Bushnell Bone Collector 1800 (LBC1800) and Bluetooth SIG Environmental Sensing (Kestrel/ESS)
 * are implemented behind this interface so additional rangefinders can be plugged in cleanly.
 */
interface RangefinderClient {
    val state: StateFlow<BleUiState>

    fun setOnReadingListener(listener: (RangefinderReading) -> Unit)
    fun setOnAtmosphereListener(listener: (LiveAtmosphereReading) -> Unit)

    fun startScan()
    fun stopScan()
    fun connect(address: String, displayName: String? = null, autoConnect: Boolean = false)
    fun disconnect()

    fun setKeepaliveEnabled(enabled: Boolean)
    fun setAutoReconnectEnabled(enabled: Boolean)

    fun syncProfile(profile: RifleProfile, atmosphere: Atmosphere, moverSpeedMph: Double = 0.0)
    fun simulateRangePing(distanceYards: Double, angleDegrees: Double)
    fun shutdown()
}
