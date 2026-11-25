package com.gunnies.ballistics.engine

import com.gunnies.ballistics.data.CustomDragCurveCodec
import com.gunnies.ballistics.model.AngularUnit
import com.gunnies.ballistics.model.Atmosphere
import com.gunnies.ballistics.model.BcBand
import com.gunnies.ballistics.model.DragModel
import com.gunnies.ballistics.model.RifleProfile
import com.gunnies.ballistics.model.SessionLogEntry
import com.gunnies.ballistics.model.TruingObservation
import com.gunnies.ballistics.model.TwistDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CalibrationTest {

    private val stdAtmo = Atmosphere(
        temperatureF = 59.0,
        pressureInHg = 29.92,
        humidityPercent = 0.0,
        altitudeFeet = 0.0,
        pressureIsStation = true,
        windSpeedMph = 0.0,
        windDirectionDeg = 90.0
    )

    private val base65Cm = RifleProfile(
        id = "cal-65cm",
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
    fun multiPointFit_recoversBothMvAndBcFromPerturbedProfile() {
        val ranges = listOf(400.0, 700.0, 1000.0)
        val observations = ranges.map { r ->
            val truthSol = BallisticEngine.solve(base65Cm, stdAtmo, r)
            TruingObservation(
                rangeYards = r,
                observedElevationMoa = truthSol.elevationMoa,
                label = "${r.toInt()} yd"
            )
        }

        val perturbed = base65Cm.copy(
            muzzleVelocityFps = 2700.0,
            ballisticCoefficient = 0.318
        )

        val fit = BallisticEngine.fitMvAndBc(perturbed, stdAtmo, observations, fitBc = true)
        assertNotNull("Expected valid multi-point MV+BC fit", fit)
        fit!!

        assertTrue("Should fit both MV and BC for 3 ranges spanning 600 yd", fit.fittedBothMvAndBc)
        assertEquals("Recovered MV should match truth within 6 fps", 2780.0, fit.fittedMvFps, 6.0)
        assertEquals("Recovered BC should match truth within 0.004", 0.295, fit.fittedBc, 0.004)
        assertTrue("Post-fit RMS should be < 0.03 MOA (was ${fit.rmsErrorMoa})", fit.rmsErrorMoa < 0.03)
        assertTrue("Post-fit RMS must beat pre-fit RMS", fit.rmsErrorMoa < fit.beforeRmsErrorMoa)
        assertEquals("Should return one residual per observation", 3, fit.residuals.size)
    }

    @Test
    fun multiPointFit_scalesMultiBcBandsProportionally() {
        val truthMulti = base65Cm.copy(
            muzzleVelocityFps = 2760.0,
            ballisticCoefficient = 0.300,
            bcBands = listOf(
                BcBand(2200.0, 0.300),
                BcBand(1600.0, 0.285),
                BcBand(0.0, 0.265)
            )
        )
        val observations = listOf(350.0, 650.0, 950.0).map { r ->
            TruingObservation(r, BallisticEngine.solve(truthMulti, stdAtmo, r).elevationMoa)
        }

        val perturbed = truthMulti.copy(
            muzzleVelocityFps = 2710.0,
            ballisticCoefficient = 0.324,
            bcBands = truthMulti.bcBands.map { it.copy(bc = it.bc * 1.08) }
        )

        val fit = BallisticEngine.fitMvAndBc(perturbed, stdAtmo, observations, fitBc = true)
        assertNotNull(fit)
        fit!!
        assertTrue(fit.fittedBothMvAndBc)
        assertEquals(3, fit.fittedBcBands.size)
        assertTrue("Multi-BC post-fit RMS should be < 0.03 MOA (was ${fit.rmsErrorMoa})", fit.rmsErrorMoa < 0.03)
        assertEquals(2760.0, fit.fittedMvFps, 8.0)
    }

    @Test
    fun multiPointFit_singlePointAndCustomCurveGracefullyFitMvOnly() {
        val targetRange = 600.0
        val truthSol = BallisticEngine.solve(base65Cm, stdAtmo, targetRange)
        val perturbed = base65Cm.copy(muzzleVelocityFps = 2700.0)

        val singleFit = BallisticEngine.fitMvAndBc(
            perturbed,
            stdAtmo,
            listOf(TruingObservation(targetRange, truthSol.elevationMoa)),
            fitBc = true
        )
        val legacyMv = BallisticEngine.trueMuzzleVelocity(perturbed, stdAtmo, targetRange, truthSol.elevationMoa)
        assertNotNull(singleFit)
        assertNotNull(legacyMv)
        assertFalse("Single point must fit MV only", singleFit!!.fittedBothMvAndBc)
        assertEquals("Single-point fit should match bisection MV within 2 fps", legacyMv!!, singleFit.fittedMvFps, 2.0)

        val customCurve = CustomDragCurveCodec.buildTemplateCurve(DragModel.G7, "Test Custom")
        val customProfile = base65Cm.copy(
            dragModel = DragModel.CUSTOM,
            customDragCurve = customCurve
        )
        val obs2 = listOf(400.0, 800.0).map { r ->
            TruingObservation(r, BallisticEngine.solve(customProfile, stdAtmo, r).elevationMoa)
        }
        val customFit = BallisticEngine.fitMvAndBc(
            customProfile.copy(muzzleVelocityFps = 2710.0),
            stdAtmo,
            obs2,
            fitBc = true
        )
        assertNotNull(customFit)
        assertFalse("Custom Cd curve must preserve drag table and fit MV only", customFit!!.fittedBothMvAndBc)
        assertTrue(customFit.warnings.any { it.contains("Custom Cd", ignoreCase = true) })
        assertEquals(2780.0, customFit.fittedMvFps, 3.0)
    }

    @Test
    fun logEntryToTruingObservation_preservesAtmosphereUnitAndColdBoreFlag() {
        val coldAtmo = stdAtmo.copy(temperatureF = 35.0, pressureInHg = 25.10, pressureIsStation = true)
        val sol = BallisticEngine.solve(base65Cm, coldAtmo, 600.0)
        val entry = SessionLogEntry(
            firearmId = "gun-1",
            firearmName = "Tikka T3x",
            scopeId = "scope-1",
            scopeName = "Arken EP5",
            ammoId = "ammo-1",
            ammoName = "6.5 CM 140gr",
            temperatureF = 35.0,
            stationPressureInHg = 25.10,
            densityAltitudeFt = 4800.0,
            windSpeedMph = 0.0,
            windDirectionDeg = 90.0,
            targetRangeYards = 600.0,
            angularUnit = AngularUnit.MRAD,
            predictedElevation = sol.elevationMrad,
            predictedWindage = 0.0,
            actualElevation = sol.elevationMrad,
            actualWindage = 0.0,
            isColdBore = true
        )

        val obs = BallisticEngine.logEntryToTruingObservation(entry, stdAtmo)
        assertTrue(obs.isColdBore)
        assertEquals(sol.elevationMoa, obs.observedElevationMoa, 0.01)
        assertEquals(35.0, obs.atmosphere!!.temperatureF, 0.01)
        assertEquals(25.10, obs.atmosphere!!.pressureInHg, 0.01)

        val fit = BallisticEngine.fitMvAndBc(base65Cm, stdAtmo, listOf(obs), fitBc = false)
        assertNotNull(fit)
        assertTrue("Should warn when Cold Bore shot is included", fit!!.warnings.any { it.contains("Cold Bore", ignoreCase = true) })
        assertTrue("Zero error when shot matches cold-altitude truth", abs(fit.fittedMvFps - 2780.0) < 3.0)
    }

    @Test
    fun tallTargetCalibration_computesExactMradAndMoaScaleFactors() {
        // 10.0 MRAD at 100 yd -> expected 36.00 in; measured 35.28 in (-2.0% under-tracking -> SSF = 1.020)
        val mradRes = BallisticEngine.computeTallTargetCalibration(
            distanceYards = 100.0,
            dialedAmount = 10.0,
            measuredShiftInches = 35.28,
            angularUnit = AngularUnit.MRAD
        )
        assertNotNull(mradRes)
        assertEquals(36.00, mradRes!!.expectedShiftInches, 0.01)
        assertEquals(1.020, mradRes.scaleFactor, 0.001)
        assertEquals(-2.0, mradRes.trackingErrorPercent, 0.05)
        assertTrue(mradRes.isWithinValidRange)

        // 30.0 MOA at 100 yd -> expected 31.416 in; measured 32.044 in (+2.0% over-tracking -> SSF = 0.980)
        val moaRes = BallisticEngine.computeTallTargetCalibration(
            distanceYards = 100.0,
            dialedAmount = 30.0,
            measuredShiftInches = 32.044,
            angularUnit = AngularUnit.MOA
        )
        assertNotNull(moaRes)
        assertEquals(31.416, moaRes!!.expectedShiftInches, 0.01)
        assertEquals(0.980, moaRes.scaleFactor, 0.001)
        assertEquals(2.0, moaRes.trackingErrorPercent, 0.05)
        assertTrue(moaRes.isWithinValidRange)

        // Out-of-bounds sanity check (measured 20 in vs 36 in expected -> SSF 1.800 invalid)
        val badRes = BallisticEngine.computeTallTargetCalibration(100.0, 10.0, 20.0, AngularUnit.MRAD)
        assertNotNull(badRes)
        assertFalse(badRes!!.isWithinValidRange)
    }
}
