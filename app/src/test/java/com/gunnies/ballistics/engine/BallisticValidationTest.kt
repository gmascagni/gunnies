package com.gunnies.ballistics.engine

import com.gunnies.ballistics.model.Atmosphere
import com.gunnies.ballistics.model.DragModel
import com.gunnies.ballistics.model.RifleProfile
import com.gunnies.ballistics.model.TwistDirection
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

/**
 * External reference validation harness comparing [BallisticEngine] against live-captured
 * benchmark trajectories stored in `src/test/resources/validation/reference_fixtures.json`.
 *
 * Fails CI if drop (MOA), wind drift (MOA), remaining velocity (%), or time of flight (s)
 * regress beyond documented per-fixture tolerances.
 */
class BallisticValidationTest {

    @Serializable
    data class FixtureSuite(
        val schemaVersion: Int,
        val signConvention: String,
        val fixtures: List<ValidationFixture>
    )

    @Serializable
    data class ValidationFixture(
        val id: String,
        val title: String,
        val source: String,
        val sourceUrl: String,
        val capturedDate: String,
        val notes: String = "",
        val inputs: FixtureInputs,
        val tolerances: FixtureTolerances,
        val points: List<ReferencePoint>
    )

    @Serializable
    data class FixtureInputs(
        val dragModel: String,
        val bc: Double,
        val bulletWeightGrains: Double,
        val bulletDiameterInches: Double,
        val bulletLengthInches: Double? = null,
        val muzzleVelocityFps: Double,
        val sightHeightInches: Double,
        val zeroRangeYards: Double,
        val temperatureF: Double,
        val pressureInHg: Double,
        val pressureIsStation: Boolean,
        val altitudeFeet: Double,
        val humidityPercent: Double,
        val windSpeedMph: Double,
        val windDirectionDeg: Double,
        val inclinationDeg: Double,
        val includeSpinDrift: Boolean = false,
        val includeAeroJump: Boolean = false,
        val includeCoriolis: Boolean = false
    )

    @Serializable
    data class FixtureTolerances(
        val maxElevationMoaError: Double,
        val maxWindageMoaError: Double,
        val maxVelocityPctError: Double,
        val maxTofSecError: Double
    )

    @Serializable
    data class ReferencePoint(
        val rangeYards: Double,
        val dropInches: Double,
        val elevationComeUpMoa: Double,
        val windDriftInches: Double,
        val windHoldMoa: Double,
        val velocityFps: Double,
        val tofSec: Double
    )

    private val json = Json { ignoreUnknownKeys = false }

    private fun loadSuite(): FixtureSuite {
        val stream = javaClass.classLoader!!.getResourceAsStream("validation/reference_fixtures.json")
            ?: error("Missing validation/reference_fixtures.json in test resources")
        return json.decodeFromString(stream.bufferedReader().readText())
    }

    @Test
    fun allReferenceFixturesMatchWithinDocumentedTolerances() {
        val suite = loadSuite()
        assertTrue("Expected at least 6 benchmark fixtures", suite.fixtures.size >= 6)

        val failures = mutableListOf<String>()

        for (fixture in suite.fixtures) {
            val inp = fixture.inputs
            val dragModel = DragModel.valueOf(inp.dragModel)
            val profile = RifleProfile(
                name = fixture.id,
                caliber = fixture.title,
                dragModel = dragModel,
                ballisticCoefficient = inp.bc,
                muzzleVelocityFps = inp.muzzleVelocityFps,
                bulletWeightGrains = inp.bulletWeightGrains,
                bulletDiameterInches = inp.bulletDiameterInches,
                bulletLengthInches = inp.bulletLengthInches,
                sightHeightInches = inp.sightHeightInches,
                zeroRangeYards = inp.zeroRangeYards,
                twistDirection = TwistDirection.RIGHT,
                zeroTempF = 59.0,
                zeroPressureInHg = 29.92,
                zeroHumidityPercent = inp.humidityPercent
            )
            val atmosphere = Atmosphere(
                temperatureF = inp.temperatureF,
                pressureInHg = inp.pressureInHg,
                pressureIsStation = inp.pressureIsStation,
                altitudeFeet = inp.altitudeFeet,
                humidityPercent = inp.humidityPercent,
                windSpeedMph = inp.windSpeedMph,
                windDirectionDeg = inp.windDirectionDeg
            )
            val options = BallisticEngine.SolverOptions(
                includeSpinDrift = inp.includeSpinDrift,
                includeCoriolis = inp.includeCoriolis,
                includeAeroJump = inp.includeAeroJump
            )
            val cachedZero = BallisticEngine.computeZeroAngleRad(profile)

            println("=== ${fixture.id} (${fixture.source}, ${fixture.capturedDate}) ===")
            println("Range | GunElev RefElev dElev | GunWind RefWind dWind | GunVel RefVel dVel% | GunTOF RefTOF dTOF")

            for (pt in fixture.points) {
                val sol = BallisticEngine.solve(
                    profile = profile,
                    atmosphere = atmosphere,
                    rangeYards = pt.rangeYards,
                    inclinationDeg = inp.inclinationDeg,
                    cachedZeroAngleRad = cachedZero,
                    options = options
                )
                val dElev = sol.elevationMoa - pt.elevationComeUpMoa
                val dWind = sol.windageMoa - pt.windHoldMoa
                val dVelPct = ((sol.velocityFps - pt.velocityFps) / pt.velocityFps) * 100.0
                val dTof = sol.timeOfFlightSec - pt.tofSec

                println(
                    String.format(
                        Locale.US,
                        "%5.0f | %7.2f %7.2f %+5.2f | %7.2f %7.2f %+5.2f | %6.0f %6.0f %+5.2f%% | %6.2f %6.2f %+5.2f",
                        pt.rangeYards,
                        sol.elevationMoa, pt.elevationComeUpMoa, dElev,
                        sol.windageMoa, pt.windHoldMoa, dWind,
                        sol.velocityFps, pt.velocityFps, dVelPct,
                        sol.timeOfFlightSec, pt.tofSec, dTof
                    )
                )

                if (abs(dElev) > fixture.tolerances.maxElevationMoaError) {
                    failures += "${fixture.id} @ ${pt.rangeYards.toInt()}yd: elevation delta ${"%.2f".format(Locale.US, dElev)} MOA exceeds ±${fixture.tolerances.maxElevationMoaError} MOA"
                }
                if (abs(dWind) > fixture.tolerances.maxWindageMoaError) {
                    failures += "${fixture.id} @ ${pt.rangeYards.toInt()}yd: windage delta ${"%.2f".format(Locale.US, dWind)} MOA exceeds ±${fixture.tolerances.maxWindageMoaError} MOA"
                }
                if (abs(dVelPct) > fixture.tolerances.maxVelocityPctError) {
                    failures += "${fixture.id} @ ${pt.rangeYards.toInt()}yd: velocity delta ${"%.2f".format(Locale.US, dVelPct)}% exceeds ±${fixture.tolerances.maxVelocityPctError}%"
                }
                if (abs(dTof) > fixture.tolerances.maxTofSecError) {
                    failures += "${fixture.id} @ ${pt.rangeYards.toInt()}yd: TOF delta ${"%.3f".format(Locale.US, dTof)}s exceeds ±${fixture.tolerances.maxTofSecError}s"
                }
            }
        }

        assertTrue(
            "Validation regressions detected:\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }
}
