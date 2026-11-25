package com.gunnies.ballistics.engine

import com.gunnies.ballistics.ble.Lbc1800Protocol
import com.gunnies.ballistics.data.BallisticsCatalog
import com.gunnies.ballistics.data.CustomDragCurveCodec
import com.gunnies.ballistics.data.ProfileImportCodec
import com.gunnies.ballistics.model.Atmosphere
import com.gunnies.ballistics.model.ClickUnit
import com.gunnies.ballistics.model.CustomDragCurve
import com.gunnies.ballistics.model.DragCurvePoint
import com.gunnies.ballistics.model.DragModel
import com.gunnies.ballistics.model.RifleProfile
import com.gunnies.ballistics.model.TwistDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Phase 2 unit tests:
 * - Custom Cd-vs-Mach drag curve physics equivalence against standard G1/G7 tables
 * - Custom drag curve JSON & CSV import/export + readable validation error messages
 * - Bushnell LBC1800 BLE protocol fallback for Custom Cd loads
 * - Strelok-style profile import (XML, key=value, JSON with Multi-BC, CSV)
 * - Expanded attributed catalog integrity
 */
class CustomDragAndImportTest {

    @Test
    fun customDragCurveMatchingG7TableEqualsStandardG7WhenBcEqualsSectionalDensity() {
        val baseProfile = RifleProfile(
            name = "6.5 CM 140gr Reference",
            caliber = "6.5 Creedmoor",
            dragModel = DragModel.G7,
            ballisticCoefficient = 0.2869, // Will be set to exact SD below
            muzzleVelocityFps = 2710.0,
            bulletWeightGrains = 140.0,
            bulletDiameterInches = 0.264,
            sightHeightInches = 1.75,
            zeroRangeYards = 100.0,
            twistDirection = TwistDirection.RIGHT
        )
        val sd = BallisticEngine.computeSectionalDensity(baseProfile)
        val g7Profile = baseProfile.copy(ballisticCoefficient = sd)

        // Build full-resolution custom curve from standard G7 Mach knots (Mach 0.00..4.00)
        val machKnots = (0..80).map { it * 0.05 }
        val g7Curve = CustomDragCurve(
            name = "Exact G7 Curve",
            source = "Unit test G7 equivalence",
            points = machKnots.map { m -> DragCurvePoint(m, DragTables.getCd(DragModel.G7, m)) }
        )
        val customProfile = g7Profile.copy(
            dragModel = DragModel.CUSTOM,
            customDragCurve = g7Curve
        )
        val atmo = Atmosphere(
            temperatureF = 59.0,
            pressureInHg = 29.92,
            humidityPercent = 50.0,
            windSpeedMph = 10.0,
            windDirectionDeg = 90.0
        )

        for (rangeYd in listOf(100.0, 300.0, 600.0, 1000.0)) {
            val g7Sol = BallisticEngine.solve(g7Profile, atmo, rangeYd)
            val customSol = BallisticEngine.solve(customProfile, atmo, rangeYd)
            assertEquals("Elevation mismatch at $rangeYd yd", g7Sol.elevationMoa, customSol.elevationMoa, 0.015)
            assertEquals("Windage mismatch at $rangeYd yd", g7Sol.windageMoa, customSol.windageMoa, 0.015)
            assertEquals("Velocity mismatch at $rangeYd yd", g7Sol.velocityFps, customSol.velocityFps, 1.0)
        }
    }

    @Test
    fun customDragCurveJsonAndCsvRoundTripAndAutoSort() {
        val unsorted = CustomDragCurve(
            name = "140gr Match Custom",
            source = "Test Lab",
            points = listOf(
                DragCurvePoint(2.5, 0.270),
                DragCurvePoint(0.5, 0.120),
                DragCurvePoint(1.0, 0.380),
                DragCurvePoint(1.5, 0.344)
            )
        )

        val jsonStr = CustomDragCurveCodec.exportJson(unsorted)
        val parsedJson = CustomDragCurveCodec.parse(jsonStr, "curve.json").getOrThrow()
        assertEquals("140gr Match Custom", parsedJson.name)
        assertEquals("Test Lab", parsedJson.source)
        assertEquals(listOf(0.5, 1.0, 1.5, 2.5), parsedJson.points.map { it.mach })

        val csvStr = CustomDragCurveCodec.exportCsv(parsedJson)
        val parsedCsv = CustomDragCurveCodec.parse(csvStr, "curve.csv").getOrThrow()
        assertEquals(parsedJson.name, parsedCsv.name)
        assertEquals(parsedJson.points.size, parsedCsv.points.size)
        for (i in parsedJson.points.indices) {
            assertEquals(parsedJson.points[i].mach, parsedCsv.points[i].mach, 1e-4)
            assertEquals(parsedJson.points[i].cd, parsedCsv.points[i].cd, 1e-4)
        }
    }

    @Test
    fun customDragCurveRejectsInvalidInputsWithReadableMessages() {
        // Duplicate Mach
        val dupCsv = "mach,cd\n0.5,0.12\n1.0,0.38\n1.0,0.39\n"
        val dupErr = CustomDragCurveCodec.parse(dupCsv).exceptionOrNull()
        assertTrue("Expected duplicate Mach error, got $dupErr", dupErr?.message?.contains("Duplicate Mach", ignoreCase = true) == true)

        // Bad numeric row with line number
        val badRow = "# name: Bad\nmach,cd\n0.5,0.12\n1.0,abc\n"
        val rowErr = CustomDragCurveCodec.parse(badRow).exceptionOrNull()
        assertTrue("Expected line number in error, got $rowErr", rowErr?.message?.contains("Line 4") == true)

        // Out-of-range Cd
        val badCd = "mach,cd\n0.5,-0.10\n1.5,0.34\n"
        val cdErr = CustomDragCurveCodec.parse(badCd).exceptionOrNull()
        assertTrue("Expected Cd range error, got $cdErr", cdErr?.message?.contains("Cd", ignoreCase = true) == true)

        // Too few points
        val tooFew = "mach,cd\n1.0,0.35\n"
        val fewErr = CustomDragCurveCodec.parse(tooFew).exceptionOrNull()
        assertTrue("Expected minimum points error, got $fewErr", fewErr?.message?.contains("at least 2") == true)
    }

    @Test
    fun lbc1800ProtocolSafelySerializesCustomDragCurveProfiles() {
        val tpl = CustomDragCurveCodec.buildTemplateCurve(DragModel.G7)
        val profile = RifleProfile(
            name = "Custom 6.5",
            caliber = "6.5 Creedmoor",
            dragModel = DragModel.CUSTOM,
            ballisticCoefficient = 0.326,
            customDragCurve = tpl,
            muzzleVelocityFps = 2710.0,
            bulletWeightGrains = 140.0,
            bulletDiameterInches = 0.264
        )
        val cmd = Lbc1800Protocol.buildBallisticProfileCommand(profile, Atmosphere())
        assertTrue("Expected valid checksum on :BP command", Lbc1800Protocol.verifyAsciiChecksum(cmd))
        assertTrue("Expected G7 fallback (00001) for high-BC custom curve", cmd.contains(",00001,"))
        assertEquals("Custom Cd", profile.activeDragModelHudLabel())
    }

    @Test
    fun strelokStyleProfileImportsXmlKeyValueJsonAndCsv() {
        // 1. Strelok XML export
        val xml = """
            <rifle>
              <rifle_name>Tikka T3x 6.5 CM</rifle_name>
              <scope_name>Arken SH4 4-16x50</scope_name>
              <cartridge_name>Hornady 140gr ELD-M</cartridge_name>
              <caliber>6.5 Creedmoor</caliber>
              <drag_function>G7</drag_function>
              <bc1>0.326</bc1><v1>2400</v1>
              <bc2>0.312</bc2><v2>1800</v2>
              <bc3>0.295</bc3><v3>0</v3>
              <muzzle_velocity>2710</muzzle_velocity>
              <bullet_weight>140</bullet_weight>
              <bullet_diameter>0.264</bullet_diameter>
              <sight_height>1.85</sight_height>
              <zero_distance>100</zero_distance>
              <twist>8.0</twist>
              <click_unit>0.1 MRAD</click_unit>
            </rifle>
        """.trimIndent()
        val xmlBundle = ProfileImportCodec.parse(xml, "tikka.srl").getOrThrow()
        assertEquals("Tikka T3x 6.5 CM", xmlBundle.firearm.name)
        assertEquals(ClickUnit.TENTH_MRAD, xmlBundle.scope.clickUnit)
        assertEquals(DragModel.G7, xmlBundle.ammo.dragModel)
        assertEquals(3, xmlBundle.ammo.bcBands.size)
        assertEquals(0.326, xmlBundle.ammo.primaryBc(), 1e-4)

        // 2. Key-value .srl text
        val kv = """
            rifle_name = CZ 457 MTR
            cartridge_name = Lapua Center-X
            caliber = .22 LR
            drag_function = G1
            bc = 0.172
            mv = 1073
            bullet_weight = 40
            bullet_diameter = 0.223
            sight_height = 1.75
            zero_distance = 50
        """.trimIndent()
        val kvBundle = ProfileImportCodec.parse(kv, "cz457.srl").getOrThrow()
        assertEquals("CZ 457 MTR", kvBundle.firearm.name)
        assertEquals(50.0, kvBundle.scope.zeroRangeYards, 1e-3)
        assertEquals(1073.0, kvBundle.ammo.muzzleVelocityFps, 1e-3)

        // 3. CSV table
        val csv = "rifle_name,cartridge_name,caliber,drag_function,bc,mv,bullet_weight,bullet_diameter\n" +
            "Bergara B14,175gr SMK,.308 Win,G7,0.243,2600,175,0.308\n"
        val csvBundle = ProfileImportCodec.parse(csv, "bergara.csv").getOrThrow()
        assertEquals("Bergara B14", csvBundle.firearm.name)
        assertEquals(DragModel.G7, csvBundle.ammo.dragModel)
        assertTrue(abs(csvBundle.ammo.ballisticCoefficient - 0.243) < 1e-4)

        // 4. Missing required MV returns readable error
        val bad = ProfileImportCodec.parse("rifle_name=Test\nbc=0.300\n")
        assertFalse(bad.isSuccess)
        assertTrue(bad.exceptionOrNull()?.message?.contains("muzzle velocity", ignoreCase = true) == true)
    }

    @Test
    fun expandedCatalogHasAttributedRimfireSubsonicAndCenterfireLoads() {
        assertTrue("Expected >= 124 ammo loads in catalog", BallisticsCatalog.ammo.size >= 124)
        val p2Loads = BallisticsCatalog.ammo.filter { it.id.startsWith("cat-am-p2-") }
        assertEquals(10, p2Loads.size)
        assertTrue("Every Phase 2 load must include manufacturer attribution", p2Loads.all { it.notes.contains("Source:") })
    }
}
