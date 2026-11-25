package com.gunnies.ballistics.ble

import com.gunnies.ballistics.model.Atmosphere
import com.gunnies.ballistics.model.BcBand
import com.gunnies.ballistics.model.DefaultProfiles
import com.gunnies.ballistics.model.DsfPoint
import com.gunnies.ballistics.model.TwistDirection
import com.gunnies.ballistics.model.WindMode
import com.gunnies.ballistics.model.WindProfile
import com.gunnies.ballistics.model.WindZone
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Lbc1800ProtocolTest {

    @Test
    fun checksumMatchesAllCapturedLbc1800Frames() {
        val cases = listOf(
            ":DD," to "9C",
            ":DU,2141604705," to "A7",
            ":AV,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000,00000," to "8B",
            ":BS,3,2,2,1,1,3,0,1," to "8C",
            ":BS,3,2,2,1,1,3,0," to "91",
            ":VR,28,46," to "90",
            ":GK,1461766314,1," to "A2",
            ":GI,874498477EB51F587825FA4447D38BAA,1," to "D0",
            ":BP,0.118,00000,00040,0.224,0.503,00000,00100,2.000,+10.00,0.000,1.000,0.000,1.000,0.000,1.000,0.000,1.000,0.000,1.000,0.000,1.000,00001,-00.0,+00.0,1.000,1.000,60.17,29.07,49.00,5.01,0.00,0.00,34.07,0.00," to "B5"
        )
        for ((prefix, expected) in cases) {
            assertEquals("Checksum mismatch for $prefix", expected, Lbc1800Protocol.computeAsciiChecksum(prefix))
        }
    }

    @Test
    fun verifyAsciiChecksumValidatesGoodAndRejectsTamperedFrames() {
        assertTrue(Lbc1800Protocol.verifyAsciiChecksum(":VR,28,46,90"))
        assertTrue(Lbc1800Protocol.verifyAsciiChecksum(":AK"))
        assertTrue(Lbc1800Protocol.verifyAsciiChecksum(":A+"))
        assertFalse(Lbc1800Protocol.verifyAsciiChecksum(":VR,28,46,91"))
        assertFalse(Lbc1800Protocol.verifyAsciiChecksum(":VR,28,46"))
    }

    @Test
    fun hdlcEncoderAndDeframerMatchCapturedAlivePacketByteForByte() {
        val captured = byteArrayOf(
            0x7E, 0x00, 0x00, 0x06, 0x00, 0x5E, 0x00,
            0x3A, 0x41, 0x2B, 0x0D,
            0x82.toByte(), 0xD7.toByte(), 0x7E
        )

        val encoded = Lbc1800Protocol.encodeHdlcFrame(":A+\r")
        assertArrayEquals(captured, encoded)

        val deframer = Lbc1800Protocol.StreamDeframer()
        val decoded = deframer.feed(captured)
        assertEquals(1, decoded.size)
        assertEquals(":A+", decoded[0].asciiMessage)
        assertEquals(0x005E, decoded[0].channel)
        assertTrue(decoded[0].crcValid)
    }

    @Test
    fun ballisticProfileCommandRoundTripsThroughHdlcDeframer() {
        val profile = DefaultProfiles.starterProfiles.first()
        val cmd = Lbc1800Protocol.buildBallisticProfileCommand(profile, Atmosphere())
        assertTrue(cmd.startsWith(":BP,"))
        assertTrue(cmd.endsWith("\r"))
        assertTrue(Lbc1800Protocol.verifyAsciiChecksum(cmd))

        val frame = Lbc1800Protocol.encodeHdlcFrame(cmd)
        val deframer = Lbc1800Protocol.StreamDeframer()
        val results = mutableListOf<Lbc1800Protocol.DecodedFrame>()
        var offset = 0
        while (offset < frame.size) {
            val end = (offset + 20).coerceAtMost(frame.size)
            results.addAll(deframer.feed(frame.copyOfRange(offset, end)))
            offset = end
        }
        assertEquals(1, results.size)
        assertTrue(results[0].crcValid)
        assertEquals(cmd.trimEnd('\r'), results[0].asciiMessage)
    }

    @Test
    fun ballisticProfileCommandEncodesStationPressureTwistNoneAzimuthOffsetsAndMover() {
        val profile = DefaultProfiles.starterProfiles.first().copy(
            twistDirection = TwistDirection.NONE,
            zeroOffsetVerticalInches = -0.5,
            zeroOffsetHorizontalInches = 0.25,
            elevationSsf = 1.015,
            windageSsf = 0.990
        )
        val atmo = Atmosphere(
            pressureInHg = 29.92,
            pressureIsStation = false,
            altitudeFeet = 5000.0,
            windSpeedMph = 12.0,
            windDirectionDeg = -90.0, // Should normalize to 270.00
            shootingBearingDeg = 135.0
        )
        val cmd = Lbc1800Protocol.buildBallisticProfileCommand(
            profile = profile,
            atmosphere = atmo,
            latitudeDeg = 42.5,
            moverSpeedMph = 4.5
        )
        assertTrue(Lbc1800Protocol.verifyAsciiChecksum(cmd))
        val fields = cmd.trimEnd('\r').split(',')
        // Field 9 is twist
        assertEquals("+00.00", fields[9])
        // Fields 23..26 are zeroOffsetVert, zeroOffsetHoriz, elevSsf, windSsf
        assertEquals("-00.5", fields[23])
        assertEquals("+00.3", fields[24])
        assertEquals("1.015", fields[25])
        assertEquals("0.990", fields[26])
        // Field 28 is station pressure (< 29.92 at 5000 ft)
        assertTrue("Expected station pressure < 26.0 inHg at 5000ft, got ${fields[28]}", fields[28].toDouble() < 26.0)
        // Field 31 is normalized wind direction (270.00), 32 is azimuth (135.00), 33 is lat (42.50), 34 is mover (4.50)
        assertEquals("270.00", fields[31])
        assertEquals("135.00", fields[32])
        assertEquals("42.50", fields[33])
        assertEquals("4.50", fields[34])
    }

    @Test
    fun auxVelocityCommandEncodesSixPairPowderTempTable() {
        val flatCmd = Lbc1800Protocol.buildAuxVelocityCommand(null)
        assertTrue(flatCmd.startsWith(":AV,00000,00000,"))
        assertTrue(Lbc1800Protocol.verifyAsciiChecksum(flatCmd))

        val sensitive = DefaultProfiles.starterProfiles.first().copy(
            muzzleVelocityFps = 2700.0,
            mvTempSensitivityFpsPerF = 1.0,
            chronoTempF = 60.0
        )
        val tableCmd = Lbc1800Protocol.buildAuxVelocityCommand(sensitive)
        assertTrue(Lbc1800Protocol.verifyAsciiChecksum(tableCmd))
        // 0°F -> 2640 fps, 100°F -> 2740 fps
        assertTrue(tableCmd.contains(",00000,02640,"))
        assertTrue(tableCmd.contains(",00100,02740,"))
    }

    @Test
    fun ballisticProfileCommandUsesPrimaryMultiBcDsfAndRangeWeightedThreeZoneWind() {
        val profile = DefaultProfiles.starterProfiles.first().copy(
            ballisticCoefficient = 0.450,
            bcBands = listOf(
                BcBand(2500.0, 0.525),
                BcBand(1800.0, 0.490),
                BcBand(0.0, 0.440)
            ),
            dsfTable = listOf(
                DsfPoint(0.95, 1.045)
            )
        )
        val atmo = Atmosphere(
            windSpeedMph = 5.0,
            windDirectionDeg = 90.0,
            windProfile = WindProfile(
                mode = WindMode.THREE_ZONE,
                near = WindZone(9.0, 90.0),
                mid = WindZone(9.0, 90.0),
                far = WindZone(0.0, 90.0)
            )
        )
        val cmd = Lbc1800Protocol.buildBallisticProfileCommand(profile, atmo)
        assertTrue(Lbc1800Protocol.verifyAsciiChecksum(cmd))
        val fields = cmd.trimEnd('\r').split(',')
        // Field 1 is primary (highest-velocity) BC = 0.525
        assertEquals("0.525", fields[1])
        // Fields 10..11 are first DSF pair: 0.950, 1.045
        assertEquals("0.950", fields[10])
        assertEquals("1.045", fields[11])
        // Field 30 is range-weighted wind speed: (5/9*9 + 3/9*9 + 1/9*0) = 8.00 mph
        assertEquals("8.00", fields[30])
        assertEquals("90.00", fields[31])
    }
}
