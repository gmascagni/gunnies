package com.bc1800.ballistics.ble

import com.bc1800.ballistics.engine.BallisticEngine
import com.bc1800.ballistics.model.Atmosphere
import com.bc1800.ballistics.model.DragModel
import com.bc1800.ballistics.model.RifleProfile
import com.bc1800.ballistics.model.TwistDirection
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Native framing & Applied Ballistics Ultralite serial protocol codec for the
 * Bushnell Bone Collector 1800 (LBC1800 / BC-xxxxxx).
 *
 * Verified live against BC-011183:
 * - Service: 49535343-fe7d-4ae5-8fa9-9fafd205e455
 * - Write:   0000fff1-0000-1000-8000-00805f9b34fb (Raw ASCII :DU, :DD, :BP, :AV, :BS)
 * - Notify:  0000fff2-0000-1000-8000-00805f9b34fb (Raw ASCII responses + HDLC-framed :A+ heartbeats)
 */
object Lbc1800Protocol {

    const val SERVICE_UUID = "49535343-fe7d-4ae5-8fa9-9fafd205e455"
    const val WRITE_CHAR_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
    const val NOTIFY_CHAR_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"

    const val HDLC_FLAG: Byte = 0x7E
    private const val HDLC_ESC: Byte = 0x7D
    private const val HDLC_ESC_XOR: Int = 0x20
    private const val CHECKSUM_SEED: Int = 0x8A

    /** Default device unlock token observed on BC-011183. */
    const val DEFAULT_UNLOCK_CODE: Long = 2141604705L

    /**
     * Computes the 2-char uppercase hex checksum for an LBC1800 ASCII command ending with ','.
     * Verified 9/9 against captured TX/RX frames (:DD, :DU, :BP, :AV, :BS, :VR, :GI, :GK).
     */
    fun computeAsciiChecksum(prefixEndingWithComma: String): String {
        var acc = CHECKSUM_SEED
        for (ch in prefixEndingWithComma) {
            acc = acc xor (ch.code and 0xFF)
        }
        return String.format(Locale.US, "%02X", acc and 0xFF)
    }

    /**
     * Verifies the trailing 2-hex-char checksum on a received ASCII line (e.g., ":VR,28,46,90").
     * Short control messages without checksums (":AK", ":A+") return true.
     */
    fun verifyAsciiChecksum(line: String): Boolean {
        val trimmed = line.trimEnd('\r', '\n')
        if (trimmed == ":AK" || trimmed == ":A+") return true
        val lastComma = trimmed.lastIndexOf(',')
        if (lastComma <= 0 || lastComma + 3 != trimmed.length) return false
        val prefix = trimmed.substring(0, lastComma + 1)
        val rxCs = trimmed.substring(lastComma + 1).uppercase(Locale.US)
        return computeAsciiChecksum(prefix) == rxCs
    }

    fun buildCommand(prefixEndingWithComma: String): String {
        val cs = computeAsciiChecksum(prefixEndingWithComma)
        return "$prefixEndingWithComma$cs\r"
    }

    /** CRC-16/X.25 (poly 0x1021, init 0xFFFF, refin=true, refout=true, xorout=0xFFFF). */
    fun computeCrc16X25(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            for (bit in 0 until 8) {
                crc = if ((crc and 1) != 0) (crc ushr 1) xor 0x8408 else crc ushr 1
            }
        }
        return (crc xor 0xFFFF) and 0xFFFF
    }

    /** Wraps an ASCII payload into an LBC1800 HDLC frame (used by RX heartbeats & protocol tests). */
    fun encodeHdlcFrame(asciiCommand: String, channelLo: Int = 0x5E, channelHi: Int = 0x00): ByteArray {
        val asciiBytes = asciiCommand.toByteArray(Charsets.US_ASCII)
        val payloadLen = 2 + asciiBytes.size

        val unescaped = ByteArray(4 + payloadLen)
        unescaped[0] = 0x00
        unescaped[1] = 0x00
        unescaped[2] = (payloadLen and 0xFF).toByte()
        unescaped[3] = ((payloadLen ushr 8) and 0xFF).toByte()
        unescaped[4] = (channelLo and 0xFF).toByte()
        unescaped[5] = (channelHi and 0xFF).toByte()
        System.arraycopy(asciiBytes, 0, unescaped, 6, asciiBytes.size)

        val crc = computeCrc16X25(unescaped)
        val withCrc = ByteArray(unescaped.size + 2)
        System.arraycopy(unescaped, 0, withCrc, 0, unescaped.size)
        withCrc[unescaped.size] = (crc and 0xFF).toByte()
        withCrc[unescaped.size + 1] = ((crc ushr 8) and 0xFF).toByte()

        val out = ByteArrayOutputStream(withCrc.size + 8)
        out.write(HDLC_FLAG.toInt())
        for (b in withCrc) {
            if (b == HDLC_FLAG || b == HDLC_ESC) {
                out.write(HDLC_ESC.toInt())
                out.write((b.toInt() and 0xFF) xor HDLC_ESC_XOR)
            } else {
                out.write(b.toInt() and 0xFF)
            }
        }
        out.write(HDLC_FLAG.toInt())
        return out.toByteArray()
    }

    data class DecodedFrame(
        val channel: Int,
        val asciiMessage: String,
        val crcValid: Boolean
    )

    /** Stateful HDLC stream deframer safely reassembling multi-packet BLE notifications. */
    class StreamDeframer {
        private val buffer = ByteArrayOutputStream()
        var isInFrame: Boolean = false
            private set
        private var escapeNext = false

        fun feed(chunk: ByteArray): List<DecodedFrame> {
            val frames = mutableListOf<DecodedFrame>()
            for (b in chunk) {
                if (b == HDLC_FLAG) {
                    if (isInFrame && buffer.size() >= 8) {
                        decodeUnescapedBody(buffer.toByteArray())?.let { frames.add(it) }
                        isInFrame = false
                    } else {
                        isInFrame = true
                    }
                    buffer.reset()
                    escapeNext = false
                    continue
                }
                if (!isInFrame) continue
                if (b == HDLC_ESC) {
                    escapeNext = true
                    continue
                }
                val actual = if (escapeNext) {
                    escapeNext = false
                    ((b.toInt() and 0xFF) xor HDLC_ESC_XOR).toByte()
                } else {
                    b
                }
                buffer.write(actual.toInt() and 0xFF)
                if (buffer.size() > 1024) {
                    reset()
                }
            }
            return frames
        }

        fun reset() {
            buffer.reset()
            isInFrame = false
            escapeNext = false
        }

        private fun decodeUnescapedBody(body: ByteArray): DecodedFrame? {
            if (body.size < 8) return null
            val payload = body.copyOfRange(0, body.size - 2)
            val declaredLen = (payload[2].toInt() and 0xFF) or ((payload[3].toInt() and 0xFF) shl 8)
            val rxCrc = (body[body.size - 2].toInt() and 0xFF) or
                ((body[body.size - 1].toInt() and 0xFF) shl 8)
            val calcCrc = computeCrc16X25(payload)
            val lenOk = (declaredLen == payload.size - 4)
            val channel = (payload[4].toInt() and 0xFF) or ((payload[5].toInt() and 0xFF) shl 8)
            val ascii = String(payload, 6, payload.size - 6, Charsets.US_ASCII)
                .trimEnd('\r', '\n', '\u0000')
            return DecodedFrame(
                channel = channel,
                asciiMessage = ascii,
                crcValid = (rxCrc == calcCrc) && lenOk
            )
        }
    }

    fun buildQueryDeviceDataCommand(): String = buildCommand(":DD,")

    fun buildDeviceUnlockCommand(unlockCode: Long = DEFAULT_UNLOCK_CODE): String =
        buildCommand(":DU,$unlockCode,")

    /**
     * Builds the :AV 6-pair (Temp °F, MV fps) powder-temperature sensitivity table.
     * Falls back to 12 zeros when sensitivity is 0.0 fps/°F.
     */
    fun buildAuxVelocityCommand(profile: RifleProfile? = null): String {
        if (profile == null || abs(profile.mvTempSensitivityFpsPerF) < 0.001) {
            return buildCommand(":AV,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,")
        }
        val temps = listOf(0, 25, 50, 75, 100, 120)
        val pairs = temps.joinToString(",") { tF ->
            val mv = BallisticEngine.effectiveMuzzleVelocityFps(profile, tF.toDouble()).roundToInt().coerceIn(0, 99999)
            String.format(Locale.US, "%05d,%05d", tF, mv)
        }
        return buildCommand(":AV,$pairs,")
    }

    /**
     * Builds the 34-field :BP,... ballistic profile + live GPS atmosphere command matching the
     * exact Bushnell Bone Collector 1800 Applied Ballistics Ultralite wire specification.
     */
    fun buildBallisticProfileCommand(
        profile: RifleProfile,
        atmosphere: Atmosphere,
        latitudeDeg: Double = atmosphere.gpsLatitude ?: 35.0,
        moverSpeedMph: Double = 0.0
    ): String {
        val bc = String.format(Locale.US, "%.3f", profile.primaryBc().coerceIn(0.01, 2.0))
        val dragIdx = if (profile.dragModel == DragModel.G7) "00001" else "00000"
        val weight = String.format(Locale.US, "%05d", profile.bulletWeightGrains.roundToInt().coerceIn(1, 99999))
        val dia = String.format(Locale.US, "%.3f", profile.bulletDiameterInches.coerceIn(0.1, 1.0))
        val lenStr = String.format(Locale.US, "%.3f", BallisticEngine.estimateBulletLengthInches(profile))
        val effMv = BallisticEngine.effectiveMuzzleVelocityFps(profile, atmosphere.temperatureF)
        val mv = String.format(Locale.US, "%05d", effMv.roundToInt().coerceIn(100, 99999))
        val zero = String.format(Locale.US, "%05d", profile.zeroRangeYards.roundToInt().coerceIn(10, 9999))
        val sh = String.format(Locale.US, "%.3f", profile.sightHeightInches.coerceIn(0.0, 9.999))

        val twist = if (profile.twistDirection == TwistDirection.NONE || profile.barrelTwistInches <= 0.0) {
            "+00.00"
        } else {
            val twistSign = if (profile.twistDirection == TwistDirection.LEFT) "-" else "+"
            String.format(Locale.US, "%s%05.2f", twistSign, abs(profile.barrelTwistInches).coerceIn(1.0, 99.99))
        }

        val sortedDsf = profile.dsfTable
            .filter { it.mach in 0.1..5.0 && it.dropScaleFactor in 0.5..1.5 }
            .sortedBy { it.mach }
            .take(6)
        val dsf = (0 until 6).joinToString(",") { idx ->
            val pt = sortedDsf.getOrNull(idx)
            if (pt != null) {
                String.format(Locale.US, "%.3f,%.3f", pt.mach, pt.dropScaleFactor)
            } else {
                "0.000,1.000"
            }
        }
        val gunSlot = "00001"
        val zVertSign = if (profile.zeroOffsetVerticalInches < 0) "-" else "+"
        val zHorizSign = if (profile.zeroOffsetHorizontalInches < 0) "-" else "+"
        val zeroOffset = String.format(
            Locale.US,
            "%s%04.1f,%s%04.1f",
            zVertSign,
            abs(profile.zeroOffsetVerticalInches).coerceIn(0.0, 99.9),
            zHorizSign,
            abs(profile.zeroOffsetHorizontalInches).coerceIn(0.0, 99.9)
        )
        val ssf = String.format(
            Locale.US,
            "%.3f,%.3f",
            profile.elevationSsf.coerceIn(0.5, 1.5),
            profile.windageSsf.coerceIn(0.5, 1.5)
        )

        val stationInHg = BallisticEngine.stationPressureInHg(atmosphere)
        val tempF = String.format(Locale.US, "%.2f", atmosphere.temperatureF.coerceIn(-40.0, 140.0))
        val pressInHg = String.format(Locale.US, "%.2f", stationInHg)
        val humid = String.format(Locale.US, "%.2f", atmosphere.humidityPercent.coerceIn(0.0, 100.0))
        val effectiveWind = atmosphere.effectiveSingleWind()
        val windSpd = String.format(Locale.US, "%.2f", effectiveWind.speedMph.coerceIn(0.0, 99.0))
        val normWindDir = ((effectiveWind.directionDeg % 360.0) + 360.0) % 360.0
        val windDir = String.format(Locale.US, "%.2f", normWindDir)
        val normAz = ((atmosphere.shootingBearingDeg % 360.0) + 360.0) % 360.0
        val azimuth = String.format(Locale.US, "%.2f", normAz)
        val lat = String.format(Locale.US, "%.2f", latitudeDeg.coerceIn(-90.0, 90.0))
        val targetSpd = String.format(Locale.US, "%.2f", abs(moverSpeedMph).coerceIn(0.0, 99.0))

        val prefix = ":BP,$bc,$dragIdx,$weight,$dia,$lenStr,$mv,$zero,$sh,$twist,$dsf,$gunSlot,$zeroOffset,$ssf,$tempF,$pressInHg,$humid,$windSpd,$windDir,$azimuth,$lat,$targetSpd,"
        return buildCommand(prefix)
    }
}
