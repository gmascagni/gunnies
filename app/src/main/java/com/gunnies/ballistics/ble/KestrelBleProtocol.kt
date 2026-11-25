package com.gunnies.ballistics.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Decoder for Bluetooth SIG Environmental Sensing Service (ESS, 0x181A) characteristics used by
 * Kestrel weather meters (and compatible BLE environmental sensors) for live atmospheric telemetry.
 *
 * Uses strictly public Bluetooth SIG GATT specification UUIDs and binary encodings:
 * - Service 0x181A: Environmental Sensing
 * - 0x2A6E Temperature: sint16, resolution 0.01 °C
 * - 0x2A6F Humidity: uint16, resolution 0.01 % RH
 * - 0x2A6D Pressure: uint32, resolution 0.1 Pa (converted to station inHg)
 * - 0x2A70 True Wind Speed / 0x2A72 Apparent Wind Speed: uint16, resolution 0.01 m/s (converted to mph)
 *
 * No proprietary or unlicensed encryption keys are used.
 */
object KestrelBleProtocol {

    const val ESS_SERVICE_UUID = "0000181a-0000-1000-8000-00805f9b34fb"
    const val CHAR_TEMPERATURE_UUID = "00002a6e-0000-1000-8000-00805f9b34fb"
    const val CHAR_HUMIDITY_UUID = "00002a6f-0000-1000-8000-00805f9b34fb"
    const val CHAR_PRESSURE_UUID = "00002a6d-0000-1000-8000-00805f9b34fb"
    const val CHAR_TRUE_WIND_SPEED_UUID = "00002a70-0000-1000-8000-00805f9b34fb"
    const val CHAR_APPARENT_WIND_SPEED_UUID = "00002a72-0000-1000-8000-00805f9b34fb"

    private const val PA_PER_INHG = 3386.38866667
    private const val MPS_TO_MPH = 2.2369362921

    private val KESTREL_KEYWORDS = listOf("kestrel", "5700", "5500", "drop", "d3", "weather", "ess")

    fun isLikelyWeatherDevice(name: String, serviceUuids: List<String>): Boolean {
        val lower = name.lowercase(Locale.US)
        return KESTREL_KEYWORDS.any { lower.contains(it) } ||
            serviceUuids.any { it.equals(ESS_SERVICE_UUID, ignoreCase = true) }
    }

    fun isEssCharacteristic(charUuid: String): Boolean {
        val lower = charUuid.lowercase(Locale.US)
        return lower == CHAR_TEMPERATURE_UUID ||
            lower == CHAR_HUMIDITY_UUID ||
            lower == CHAR_PRESSURE_UUID ||
            lower == CHAR_TRUE_WIND_SPEED_UUID ||
            lower == CHAR_APPARENT_WIND_SPEED_UUID
    }

    /**
     * Decodes a Bluetooth SIG ESS characteristic payload into a [LiveAtmosphereReading].
     * Returns null if the UUID is unrecognized or the payload is too short / out of physical bounds.
     */
    fun decodeEssCharacteristic(
        charUuid: String,
        bytes: ByteArray,
        sourceLabel: String = "Kestrel / ESS"
    ): LiveAtmosphereReading? {
        val lower = charUuid.lowercase(Locale.US)
        return when (lower) {
            CHAR_TEMPERATURE_UUID -> {
                if (bytes.size < 2) return null
                val raw = ByteBuffer.wrap(bytes, 0, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                val tempC = raw * 0.01
                val tempF = tempC * 9.0 / 5.0 + 32.0
                if (tempF !in -60.0..160.0) return null
                LiveAtmosphereReading(temperatureF = tempF, sourceLabel = sourceLabel)
            }

            CHAR_HUMIDITY_UUID -> {
                if (bytes.size < 2) return null
                val raw = ByteBuffer.wrap(bytes, 0, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                val rh = raw * 0.01
                if (rh !in 0.0..100.0) return null
                LiveAtmosphereReading(humidityPercent = rh, sourceLabel = sourceLabel)
            }

            CHAR_PRESSURE_UUID -> {
                if (bytes.size < 4) return null
                val raw = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                val pascals = raw * 0.1
                val inHg = pascals / PA_PER_INHG
                if (inHg !in 15.0..34.0) return null
                LiveAtmosphereReading(stationPressureInHg = inHg, sourceLabel = sourceLabel)
            }

            CHAR_TRUE_WIND_SPEED_UUID, CHAR_APPARENT_WIND_SPEED_UUID -> {
                if (bytes.size < 2) return null
                val raw = ByteBuffer.wrap(bytes, 0, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
                val mps = raw * 0.01
                val mph = mps * MPS_TO_MPH
                if (mph !in 0.0..120.0) return null
                LiveAtmosphereReading(windSpeedMph = mph, sourceLabel = sourceLabel)
            }

            else -> null
        }
    }
}
