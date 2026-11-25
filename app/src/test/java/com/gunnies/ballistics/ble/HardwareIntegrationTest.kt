package com.gunnies.ballistics.ble

import com.gunnies.ballistics.data.BarometerSensorService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HardwareIntegrationTest {

    @Test
    fun kestrelEss_decodesStandardSigEnvironmentalCharacteristics() {
        // Temperature: 20.00 °C = 2000 (0.01 °C) -> 68.0 °F
        val tempBytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(2000).array()
        val tempReading = KestrelBleProtocol.decodeEssCharacteristic(KestrelBleProtocol.CHAR_TEMPERATURE_UUID, tempBytes)
        assertNotNull(tempReading)
        assertEquals(68.0, tempReading!!.temperatureF!!, 0.05)

        // Humidity: 45.50 % = 4550 (0.01 %)
        val humBytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(4550).array()
        val humReading = KestrelBleProtocol.decodeEssCharacteristic(KestrelBleProtocol.CHAR_HUMIDITY_UUID, humBytes)
        assertNotNull(humReading)
        assertEquals(45.5, humReading!!.humidityPercent!!, 0.05)

        // Pressure: 101325.0 Pa = 1013250 (0.1 Pa) -> 29.921 inHg
        val pressBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(1013250).array()
        val pressReading = KestrelBleProtocol.decodeEssCharacteristic(KestrelBleProtocol.CHAR_PRESSURE_UUID, pressBytes)
        assertNotNull(pressReading)
        assertEquals(29.921, pressReading!!.stationPressureInHg!!, 0.01)

        // Wind Speed: 4.47 m/s = 447 (0.01 m/s) -> ~10.0 mph
        val windBytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(447).array()
        val windReading = KestrelBleProtocol.decodeEssCharacteristic(KestrelBleProtocol.CHAR_TRUE_WIND_SPEED_UUID, windBytes)
        assertNotNull(windReading)
        assertEquals(10.0, windReading!!.windSpeedMph!!, 0.05)
    }

    @Test
    fun kestrelEss_rejectsTruncatedOrOutOfRangePayloads() {
        assertNull(KestrelBleProtocol.decodeEssCharacteristic(KestrelBleProtocol.CHAR_TEMPERATURE_UUID, byteArrayOf(0x01)))
        assertNull(KestrelBleProtocol.decodeEssCharacteristic(KestrelBleProtocol.CHAR_PRESSURE_UUID, byteArrayOf(0x01, 0x02)))
        assertNull(KestrelBleProtocol.decodeEssCharacteristic("00001800-0000-1000-8000-00805f9b34fb", byteArrayOf(0x00, 0x00)))

        assertTrue(KestrelBleProtocol.isLikelyWeatherDevice("Kestrel 5700 Elite", emptyList()))
        assertTrue(KestrelBleProtocol.isLikelyWeatherDevice("Sensor", listOf(KestrelBleProtocol.ESS_SERVICE_UUID)))
        assertFalse(KestrelBleProtocol.isLikelyWeatherDevice("BC-1800A1", emptyList()))
    }

    @Test
    fun phoneBarometer_convertsHpaToStationInHgAccurately() {
        assertEquals(29.9213, BarometerSensorService.hpaToInHg(1013.25), 0.001)
        assertEquals(24.90, BarometerSensorService.hpaToInHg(843.2), 0.01)
    }
}
