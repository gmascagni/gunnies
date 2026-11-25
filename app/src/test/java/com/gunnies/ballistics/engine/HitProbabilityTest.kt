package com.gunnies.ballistics.engine

import com.gunnies.ballistics.model.Atmosphere
import com.gunnies.ballistics.model.DragModel
import com.gunnies.ballistics.model.RifleProfile
import com.gunnies.ballistics.model.ShotUncertaintyInput
import com.gunnies.ballistics.model.TwistDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

class HitProbabilityTest {

    private val stdAtmo = Atmosphere(
        temperatureF = 59.0,
        pressureInHg = 29.92,
        humidityPercent = 0.0,
        altitudeFeet = 0.0,
        pressureIsStation = true,
        windSpeedMph = 10.0,
        windDirectionDeg = 90.0
    )

    private val rifle65Cm = RifleProfile(
        id = "prob-65cm",
        name = "6.5 CM 140gr",
        caliber = "6.5 Creedmoor",
        dragModel = DragModel.G7,
        ballisticCoefficient = 0.295,
        muzzleVelocityFps = 2780.0,
        sightHeightInches = 1.85,
        zeroRangeYards = 100.0,
        bulletWeightGrains = 140.0,
        bulletDiameterInches = 0.264,
        barrelTwistInches = 8.0,
        twistDirection = TwistDirection.RIGHT
    )

    @Test
    fun circularIsotropicGaussian_matchesExactRayleighFormula() {
        // Pure isotropic rifle 1-sigma = 1.0 MOA at 100 yd -> sigma = 1.0472 in.
        // For circular plate radius R = 1.0472 in (diameter = 2.0944 in), exact Rayleigh CDF:
        // P(hit) = 1 - exp(-R^2 / (2 * sigma^2)) = 1 - exp(-0.5) = 39.3469%
        val dia = 2.0 * 1.0471975512
        val res = BallisticEngine.computeHitProbability(
            profile = rifle65Cm,
            atmosphere = stdAtmo,
            rangeYards = 100.0,
            uncertainty = ShotUncertaintyInput(
                mvSdFps = 0.0,
                windErrorMph = 0.0,
                rangeErrorYards = 0.0,
                bcErrorPercent = 0.0,
                systemPrecisionMoa = 1.0,
                targetDiameterInches = dia
            )
        )
        val expectedPct = (1.0 - exp(-0.5)) * 100.0
        assertEquals("Isotropic circular hit probability must match exact Rayleigh CDF", expectedPct, res.hitProbabilityPercent, 0.15)
        assertEquals(res.sigmaVerticalInches, res.sigmaHorizontalInches, 1e-4)
        assertEquals("Rifle 1σ", res.dominantErrorSource)
    }

    @Test
    fun largerPlateAndLowerUncertainty_increaseHitProbabilityMonotonically() {
        val baseInput = ShotUncertaintyInput(
            mvSdFps = 12.0,
            windErrorMph = 2.5,
            rangeErrorYards = 3.0,
            bcErrorPercent = 2.0,
            systemPrecisionMoa = 0.35,
            targetDiameterInches = 10.0
        )
        val smallPlate = BallisticEngine.computeHitProbability(rifle65Cm, stdAtmo, 600.0, baseInput.copy(targetDiameterInches = 6.0))
        val medPlate = BallisticEngine.computeHitProbability(rifle65Cm, stdAtmo, 600.0, baseInput.copy(targetDiameterInches = 10.0))
        val largePlate = BallisticEngine.computeHitProbability(rifle65Cm, stdAtmo, 600.0, baseInput.copy(targetDiameterInches = 18.0))

        assertTrue("10\" plate P(hit) must exceed 6\" plate", medPlate.hitProbabilityPercent > smallPlate.hitProbabilityPercent)
        assertTrue("18\" plate P(hit) must exceed 10\" plate", largePlate.hitProbabilityPercent > medPlate.hitProbabilityPercent)

        val windyErr = BallisticEngine.computeHitProbability(rifle65Cm, stdAtmo, 800.0, baseInput.copy(windErrorMph = 5.0))
        val calmErr = BallisticEngine.computeHitProbability(rifle65Cm, stdAtmo, 800.0, baseInput.copy(windErrorMph = 0.5))
        assertTrue("Lower wind uncertainty must yield higher P(hit)", calmErr.hitProbabilityPercent > windyErr.hitProbabilityPercent)
        assertEquals("Wind should dominate at 800 yd with ±5 mph error", "Wind", windyErr.dominantErrorSource)
    }

    @Test
    fun subsonicRimfire_rangeUncertaintyDominatesAtExtendedDistance() {
        val rimfire = RifleProfile(
            id = "prob-22lr",
            name = ".22 LR Subsonic 40gr",
            caliber = ".22 LR",
            dragModel = DragModel.G1,
            ballisticCoefficient = 0.120,
            muzzleVelocityFps = 1070.0,
            sightHeightInches = 1.75,
            zeroRangeYards = 50.0,
            bulletWeightGrains = 40.0,
            bulletDiameterInches = 0.223,
            barrelTwistInches = 16.0,
            twistDirection = TwistDirection.RIGHT
        )
        val res = BallisticEngine.computeHitProbability(
            profile = rimfire,
            atmosphere = stdAtmo.copy(windSpeedMph = 0.0),
            rangeYards = 200.0,
            uncertainty = ShotUncertaintyInput(
                mvSdFps = 8.0,
                windErrorMph = 0.5,
                rangeErrorYards = 5.0,
                bcErrorPercent = 1.0,
                systemPrecisionMoa = 0.35,
                targetDiameterInches = 6.0
            )
        )
        assertEquals("Range", res.dominantErrorSource)
        assertTrue("Vertical 1σ should exceed horizontal 1σ when range error dominates", res.sigmaVerticalInches > res.sigmaHorizontalInches)
        assertTrue("Hit probability must be strictly within 0..100%", res.hitProbabilityPercent in 0.0..100.0)
    }
}
