package com.bc1800.ballistics.engine

import com.bc1800.ballistics.data.ArsenalBackupCodec
import com.bc1800.ballistics.data.BallisticsCatalog
import com.bc1800.ballistics.model.AmmoLoad
import com.bc1800.ballistics.model.AngularUnit
import com.bc1800.ballistics.model.ArsenalBackup
import com.bc1800.ballistics.model.Atmosphere
import com.bc1800.ballistics.model.BackupImportMode
import com.bc1800.ballistics.model.BcBand
import com.bc1800.ballistics.model.DefaultProfiles
import com.bc1800.ballistics.model.DistanceUnit
import com.bc1800.ballistics.model.DragModel
import com.bc1800.ballistics.model.DsfPoint
import com.bc1800.ballistics.model.RifleProfile
import com.bc1800.ballistics.model.SessionLogEntry
import com.bc1800.ballistics.model.StageCard
import com.bc1800.ballistics.model.StageTarget
import com.bc1800.ballistics.model.SuppressorCategory
import com.bc1800.ballistics.model.SuppressorSetup
import com.bc1800.ballistics.model.TwistDirection
import com.bc1800.ballistics.model.UserPreferences
import com.bc1800.ballistics.model.WindMode
import com.bc1800.ballistics.model.WindProfile
import com.bc1800.ballistics.model.WindZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BallisticEngineTest {

    private val icaoAtmosphere = Atmosphere(
        temperatureF = 59.0,
        pressureInHg = 29.92,
        pressureIsStation = true,
        humidityPercent = 0.0,
        windSpeedMph = 0.0,
        windDirectionDeg = 90.0
    )

    @Test
    fun icaoSeaLevelAtmosphereMatchesStandard() {
        val air = BallisticEngine.computeAirState(icaoAtmosphere)
        assertEquals(1.0, air.densityRatio, 0.002)
        assertEquals(1116.45, air.speedOfSoundFps, 1.0)
        assertEquals(0.0, air.densityAltitudeFt, 50.0)
    }

    @Test
    fun g1AndG7ExactKnotAlignment() {
        val expectedG1 = mapOf(
            0.60 to 0.2034,
            0.70 to 0.2165,
            0.975 to 0.4448,
            1.00 to 0.4805,
            1.15 to 0.6191,
            1.40 to 0.6625,
            2.00 to 0.5934,
            3.00 to 0.5099
        )
        for ((mach, cd) in expectedG1) {
            assertEquals("G1 Cd mismatch at M=$mach", cd, DragTables.getCd(DragModel.G1, mach), 1e-6)
        }

        val expectedG7 = mapOf(
            0.60 to 0.1194,
            0.70 to 0.1202,
            0.975 to 0.2993,
            1.00 to 0.3803,
            1.15 to 0.3955,
            1.40 to 0.3580,
            2.00 to 0.2980,
            3.00 to 0.2442
        )
        for ((mach, cd) in expectedG7) {
            assertEquals("G7 Cd mismatch at M=$mach", cd, DragTables.getCd(DragModel.G7, mach), 1e-6)
        }
    }

    @Test
    fun zeroRangeProducesZeroDrop() {
        val profile = RifleProfile(
            name = ".308 Test",
            caliber = ".308 Win",
            dragModel = DragModel.G1,
            ballisticCoefficient = 0.462,
            muzzleVelocityFps = 2650.0,
            sightHeightInches = 1.75,
            zeroRangeYards = 100.0,
            zeroHumidityPercent = 0.0
        )
        val sol = BallisticEngine.solve(
            profile,
            icaoAtmosphere,
            rangeYards = 100.0,
            options = BallisticEngine.SolverOptions(includeSpinDrift = false, includeCoriolis = false, includeAeroJump = false)
        )
        assertEquals(0.0, sol.dropInches, 0.01)
        assertEquals(0.0, sol.elevationMoa, 0.01)
    }

    @Test
    fun dot308_168gr_G1_matchesRealWorldBallistics() {
        val profile = RifleProfile(
            name = ".308 168gr SMK",
            caliber = ".308 Win",
            dragModel = DragModel.G1,
            ballisticCoefficient = 0.462,
            muzzleVelocityFps = 2650.0,
            bulletWeightGrains = 168.0,
            sightHeightInches = 1.75,
            zeroRangeYards = 100.0,
            zeroHumidityPercent = 0.0
        )
        val opts = BallisticEngine.SolverOptions(includeSpinDrift = false, includeCoriolis = false, includeAeroJump = false)
        val sol500 = BallisticEngine.solve(profile, icaoAtmosphere, rangeYards = 500.0, options = opts)
        assertTrue("Expected 500 yd elevation between 10.5 and 12.5 MOA, got ${sol500.elevationMoa}", sol500.elevationMoa in 10.5..12.5)
        assertTrue("Expected 500 yd velocity between 1700 and 1850 fps, got ${sol500.velocityFps}", sol500.velocityFps in 1700.0..1850.0)
        assertFalse(sol500.isTransonicWarning)
        assertFalse(sol500.exceedsOnboard800YdLimit)

        val sol900 = BallisticEngine.solve(profile, icaoAtmosphere, rangeYards = 900.0, options = opts)
        assertTrue(sol900.exceedsOnboard800YdLimit)
    }

    @Test
    fun creedmoor_143gr_G7_matchesRealWorldBallistics() {
        val profile = RifleProfile(
            name = "6.5 CM 143gr ELD-X",
            caliber = "6.5 Creedmoor",
            dragModel = DragModel.G7,
            ballisticCoefficient = 0.315,
            muzzleVelocityFps = 2700.0,
            bulletWeightGrains = 143.0,
            bulletDiameterInches = 0.264,
            sightHeightInches = 1.8,
            zeroRangeYards = 100.0,
            zeroHumidityPercent = 0.0
        )
        val opts = BallisticEngine.SolverOptions(includeSpinDrift = false, includeCoriolis = false, includeAeroJump = false)
        val sol500 = BallisticEngine.solve(profile, icaoAtmosphere, rangeYards = 500.0, options = opts)
        assertTrue("Expected 500 yd MRAD between 2.4 and 3.1, got ${sol500.elevationMrad}", sol500.elevationMrad in 2.4..3.1)
    }

    @Test
    fun crosswindFromRightRequiresRightHoldAndGustBracket() {
        val profile = RifleProfile(
            name = ".308 Wind Test",
            caliber = ".308 Win",
            dragModel = DragModel.G1,
            ballisticCoefficient = 0.462,
            muzzleVelocityFps = 2650.0,
            twistDirection = TwistDirection.NONE,
            zeroHumidityPercent = 0.0
        )
        val windy = icaoAtmosphere.copy(windSpeedMph = 10.0, windGustMph = 18.0, windDirectionDeg = 90.0)
        val opts = BallisticEngine.SolverOptions(includeSpinDrift = false, includeCoriolis = false, includeAeroJump = false)
        val sol = BallisticEngine.solve(profile, windy, rangeYards = 500.0, options = opts)
        assertTrue("Expected negative (left) drift, got ${sol.windDriftInches}", sol.windDriftInches < -15.0)
        assertTrue("Expected positive (right) windage MOA, got ${sol.windageMoa}", sol.windageMoa > 2.5)
        assertEquals(100, sol.windValuePercent)
        assertTrue("Expected gust windage > sustained windage", (sol.gustWindageMoa ?: 0.0) > sol.windageMoa)
    }

    @Test
    fun steepAngleReducesElevationHoldAndDecomposesWindIn3D() {
        val profile = RifleProfile(
            name = ".308 Angle Test",
            caliber = ".308 Win",
            dragModel = DragModel.G1,
            ballisticCoefficient = 0.462,
            muzzleVelocityFps = 2650.0,
            twistDirection = TwistDirection.NONE,
            zeroHumidityPercent = 0.0
        )
        val opts = BallisticEngine.SolverOptions(includeSpinDrift = false, includeCoriolis = false, includeAeroJump = false)
        // 1. Calm air: both uphill & downhill require less hold than flat shot at slant range
        val flat = BallisticEngine.solve(profile, icaoAtmosphere, rangeYards = 400.0, inclinationDeg = 0.0, options = opts)
        val uphill30 = BallisticEngine.solve(profile, icaoAtmosphere, rangeYards = 400.0, inclinationDeg = 30.0, options = opts)
        val downhill30 = BallisticEngine.solve(profile, icaoAtmosphere, rangeYards = 400.0, inclinationDeg = -30.0, options = opts)

        assertTrue("Uphill 30 deg should need less elevation hold than flat", uphill30.elevationMoa < flat.elevationMoa)
        assertTrue("Downhill 30 deg should need less elevation hold than flat", downhill30.elevationMoa < flat.elevationMoa)
        assertEquals(400.0 * kotlin.math.cos(Math.toRadians(30.0)), uphill30.trueHorizontalYards, 0.01)

        // 2. 3D horizontal headwind decomposition: headwind lifts uphill shot and pushes downhill shot down
        val headwind = icaoAtmosphere.copy(windSpeedMph = 12.0, windDirectionDeg = 0.0)
        val uphillWind = BallisticEngine.solve(profile, headwind, rangeYards = 400.0, inclinationDeg = 30.0, options = opts)
        val downhillWind = BallisticEngine.solve(profile, headwind, rangeYards = 400.0, inclinationDeg = -30.0, options = opts)
        assertTrue("Headwind on uphill shot should push bullet up (less UP hold)", uphillWind.elevationMoa < uphill30.elevationMoa)
        assertTrue("Headwind on downhill shot should push bullet down (more UP hold)", downhillWind.elevationMoa > downhill30.elevationMoa)
    }

    @Test
    fun singlePassRangeCardMatchesIndividualSolve() {
        val profile = RifleProfile(
            name = "6.5 CM",
            caliber = "6.5 Creedmoor",
            dragModel = DragModel.G7,
            ballisticCoefficient = 0.315,
            muzzleVelocityFps = 2700.0,
            bulletWeightGrains = 143.0,
            bulletDiameterInches = 0.264,
            sightHeightInches = 1.8,
            zeroRangeYards = 100.0
        )
        val windy = icaoAtmosphere.copy(windSpeedMph = 10.0, windDirectionDeg = 60.0)
        val opts = BallisticEngine.SolverOptions()
        val card = BallisticEngine.generateRangeCard(
            profile = profile,
            atmosphere = windy,
            stepUnits = 100,
            maxUnits = 800,
            distanceUnit = DistanceUnit.YARDS,
            options = opts
        )
        assertEquals(8, card.size)
        for (row in card) {
            val single = BallisticEngine.solve(profile, windy, row.rangeYards, options = opts)
            assertEquals("Elevation mismatch at ${row.rangeYards}yd", single.elevationMoa, row.elevationMoa, 0.05)
            assertEquals("Windage mismatch at ${row.rangeYards}yd", single.windageMoa, row.windageMoa, 0.05)
            assertEquals("Velocity mismatch at ${row.rangeYards}yd", single.velocityFps, row.velocityFps, 1.0)
        }
    }

    @Test
    fun trueMuzzleVelocityRecoversKnownVelocityWithinThreeFps() {
        val trueProfile = RifleProfile(
            name = "Truing Rifle",
            caliber = "6.5 Creedmoor",
            dragModel = DragModel.G7,
            ballisticCoefficient = 0.315,
            muzzleVelocityFps = 2750.0,
            bulletWeightGrains = 143.0,
            bulletDiameterInches = 0.264,
            sightHeightInches = 1.8,
            zeroRangeYards = 100.0
        )
        val opts = BallisticEngine.SolverOptions(includeSpinDrift = false, includeCoriolis = false, includeAeroJump = false)
        val sol600 = BallisticEngine.solve(trueProfile, icaoAtmosphere, rangeYards = 600.0, options = opts)

        val guessedProfile = trueProfile.copy(muzzleVelocityFps = 2630.0)
        val recoveredMv = BallisticEngine.trueMuzzleVelocity(
            profile = guessedProfile,
            atmosphere = icaoAtmosphere,
            rangeYards = 600.0,
            observedElevationMoa = sol600.elevationMoa,
            options = opts
        ) ?: error("Expected non-null trued MV")
        assertTrue("Expected ~2750 fps, got $recoveredMv", abs(recoveredMv - 2750.0) < 3.0)
    }

    @Test
    fun coriolisAeroJumpZeroOffsetPowderTempAndMoverLeadWorkCorrectly() {
        val base = RifleProfile(
            name = "ELR Test",
            caliber = "6.5 Creedmoor",
            dragModel = DragModel.G7,
            ballisticCoefficient = 0.315,
            muzzleVelocityFps = 2700.0,
            bulletWeightGrains = 143.0,
            bulletDiameterInches = 0.264,
            barrelTwistInches = 8.0,
            twistDirection = TwistDirection.RIGHT,
            sightHeightInches = 1.8,
            zeroRangeYards = 100.0,
            mvTempSensitivityFpsPerF = 1.0,
            chronoTempF = 59.0
        )

        // 1. Powder sensitivity
        assertEquals(2740.0, BallisticEngine.effectiveMuzzleVelocityFps(base, 99.0), 0.01)

        // 2. Coriolis East vs West (Eötvös effect)
        val eastAtmo = icaoAtmosphere.copy(gpsLatitude = 45.0, shootingBearingDeg = 90.0)
        val westAtmo = icaoAtmosphere.copy(gpsLatitude = 45.0, shootingBearingDeg = 270.0)
        val solEast = BallisticEngine.solve(base, eastAtmo, 1000.0)
        val solWest = BallisticEngine.solve(base, westAtmo, 1000.0)
        assertTrue("Shooting East should drop less than West due to Eötvös", solEast.coriolisElevMoa < solWest.coriolisElevMoa)

        // 3. Aero jump with right crosswind + right twist
        val windSol = BallisticEngine.solve(base, icaoAtmosphere.copy(windSpeedMph = 15.0, windDirectionDeg = 90.0), 600.0)
        assertTrue("Aero jump should be non-zero with crosswind", abs(windSol.aeroJumpMoa) > 0.01)

        // 4. Positive zero offset (1.0" high at zero) reduces hold UP
        val highZero = base.copy(zeroOffsetVerticalInches = 1.0)
        val stdSol = BallisticEngine.solve(base, icaoAtmosphere, 300.0)
        val highSol = BallisticEngine.solve(highZero, icaoAtmosphere, 300.0)
        assertTrue("Positive zero offset should reduce required UP hold", highSol.elevationMoa < stdSol.elevationMoa)

        // 5. Mover lead
        val moverSol = BallisticEngine.solve(
            base,
            icaoAtmosphere,
            400.0,
            options = BallisticEngine.SolverOptions(moverSpeedMph = 5.0)
        )
        assertTrue("Mover lead should be > 1.0 MOA at 400yd @ 5mph", moverSol.moverLeadMoa > 1.0)
        assertTrue("Mover lead inches should be > 4\"", moverSol.moverLeadInches > 4.0)
    }

    @Test
    fun tallSightHeightMpbrSubsonicNoWarningAndTargetMaxRange() {
        val ar15 = RifleProfile(
            name = "AR-15 Tall Mount",
            caliber = ".223 Rem",
            dragModel = DragModel.G1,
            ballisticCoefficient = 0.250,
            muzzleVelocityFps = 3100.0,
            sightHeightInches = 2.6,
            zeroRangeYards = 50.0
        )
        val mpbr = BallisticEngine.computeMpbrYards(ar15, icaoAtmosphere, vitalDiameterInches = 6.0)
        assertTrue("AR-15 6\" MPBR should be in 200..330 yd, got $mpbr", mpbr in 200.0..330.0)

        val quiet22 = RifleProfile(
            name = "CCI Quiet .22",
            caliber = ".22 LR",
            dragModel = DragModel.G1,
            ballisticCoefficient = 0.115,
            muzzleVelocityFps = 710.0,
            bulletWeightGrains = 40.0,
            bulletDiameterInches = 0.223,
            sightHeightInches = 1.85,
            zeroRangeYards = 25.0
        )
        val subSol = BallisticEngine.solve(quiet22, icaoAtmosphere, rangeYards = 100.0)
        assertFalse("Subsonic-launched round should not raise transonic instability warning", subSol.isTransonicWarning)

        val outOfRange = BallisticEngine.solve(quiet22, icaoAtmosphere, rangeYards = 2500.0)
        assertFalse("Weak subsonic .22 at 2500 yd should report reachedTarget=false", outOfRange.reachedTarget)
    }

    @Test
    fun suppressorMvShiftAndPoiShiftAffectBallisticSolution() {
        val gun = DefaultProfiles.starterFirearms.first()
        val scope = DefaultProfiles.starterScopes.first()
        val ammo = DefaultProfiles.starterAmmo[1] // 1090 fps .22 LR
        val gm22WithShift = SuppressorSetup(
            id = "test-gm22",
            name = "Gemtech GM-22",
            category = SuppressorCategory.RIMFIRE,
            mvShiftFps = 25.0,
            poiShiftVerticalInches = 0.5,
            poiShiftHorizontalInches = -0.25
        )

        val bareProfile = RifleProfile.combine(gun, scope, ammo, suppressor = null)
        val suppressedProfile = RifleProfile.combine(gun, scope, ammo, suppressor = gm22WithShift)

        assertEquals(ammo.muzzleVelocityFps + 25.0, suppressedProfile.muzzleVelocityFps, 0.01)
        assertEquals("Gemtech GM-22", suppressedProfile.suppressorName)

        val bareSol = BallisticEngine.solve(bareProfile, icaoAtmosphere, rangeYards = 150.0)
        val suppSol = BallisticEngine.solve(suppressedProfile, icaoAtmosphere, rangeYards = 150.0)
        assertTrue("Suppressor +25 fps should raise downrange velocity", suppSol.velocityFps > bareSol.velocityFps)
        assertTrue("Suppressor +25 fps & +0.5\" high POI shift should reduce UP hold", suppSol.elevationMoa < bareSol.elevationMoa)
        assertTrue("Suppressor left POI shift (-0.25\") should shift windage hold right", suppSol.windageMoa > bareSol.windageMoa)
    }

    @Test
    fun steppedMultiBcTransitionsCorrectlyVsSingleBcBaseline() {
        val opts = BallisticEngine.SolverOptions(includeSpinDrift = false, includeCoriolis = false, includeAeroJump = false)
        val singleHigh = RifleProfile(
            name = ".308 175gr SMK Single High BC",
            caliber = ".308 Win",
            dragModel = DragModel.G1,
            ballisticCoefficient = 0.505,
            muzzleVelocityFps = 2650.0,
            bulletWeightGrains = 175.0,
            bulletDiameterInches = 0.308,
            sightHeightInches = 1.8,
            zeroRangeYards = 100.0,
            zeroHumidityPercent = 0.0
        )
        // Identical constant BC expressed via 3 velocity bands must match single-BC baseline
        val uniformBands = singleHigh.copy(
            bcBands = listOf(
                BcBand(2400.0, 0.505),
                BcBand(1800.0, 0.505),
                BcBand(0.0, 0.505)
            )
        )
        // Realistic Sierra-style stepped BC that drops as velocity falls
        val steppedMultiBc = singleHigh.copy(
            bcBands = listOf(
                BcBand(2400.0, 0.505),
                BcBand(1800.0, 0.460),
                BcBand(0.0, 0.415)
            )
        )
        val singleLow = singleHigh.copy(ballisticCoefficient = 0.415, bcBands = emptyList())

        val solSingleHigh = BallisticEngine.solve(singleHigh, icaoAtmosphere, 800.0, options = opts)
        val solUniformBands = BallisticEngine.solve(uniformBands, icaoAtmosphere, 800.0, options = opts)
        val solStepped = BallisticEngine.solve(steppedMultiBc, icaoAtmosphere, 800.0, options = opts)
        val solSingleLow = BallisticEngine.solve(singleLow, icaoAtmosphere, 800.0, options = opts)

        assertEquals("Uniform Multi-BC should match single-BC elevation", solSingleHigh.elevationMoa, solUniformBands.elevationMoa, 0.005)
        assertEquals("Uniform Multi-BC should match single-BC velocity", solSingleHigh.velocityFps, solUniformBands.velocityFps, 0.2)

        assertTrue(
            "Stepped Multi-BC elevation (${solStepped.elevationMoa}) should sit strictly between singleHigh (${solSingleHigh.elevationMoa}) and singleLow (${solSingleLow.elevationMoa})",
            solStepped.elevationMoa > solSingleHigh.elevationMoa + 0.5 && solStepped.elevationMoa < solSingleLow.elevationMoa - 0.5
        )
        assertTrue(
            "Stepped Multi-BC retained velocity (${solStepped.velocityFps}) should sit between low and high BC baselines",
            solStepped.velocityFps < solSingleHigh.velocityFps - 20.0 && solStepped.velocityFps > solSingleLow.velocityFps + 20.0
        )
        assertEquals("primaryBc() should return highest-velocity band BC", 0.505, steppedMultiBc.primaryBc(), 1e-6)
    }

    @Test
    fun dsfSubsonicDropScalingAndDsfTruingSolverAccuracy() {
        val opts = BallisticEngine.SolverOptions(includeSpinDrift = false, includeCoriolis = false, includeAeroJump = false)

        // 1. Subsonic .22 LR calibration at 200 yd (Mach ~0.68)
        val sub22 = RifleProfile(
            name = "CZ 457 Subsonic .22",
            caliber = ".22 LR",
            dragModel = DragModel.G1,
            ballisticCoefficient = 0.120,
            muzzleVelocityFps = 1070.0,
            bulletWeightGrains = 40.0,
            bulletDiameterInches = 0.223,
            sightHeightInches = 1.85,
            zeroRangeYards = 50.0,
            zeroHumidityPercent = 0.0
        )
        val baseSol = BallisticEngine.solve(sub22, icaoAtmosphere, 200.0, options = opts)
        assertTrue("Expected subsonic .22 LR at 200 yd to be below Mach 1.2", baseSol.mach < 1.2)
        assertEquals(1.0, baseSol.appliedDsf, 1e-6)

        // Apply known DSF = 1.080
        val trueDsfVal = 1.080
        val scaledProfile = sub22.copy(dsfTable = listOf(DsfPoint(mach = baseSol.mach, dropScaleFactor = trueDsfVal)))
        val scaledSol = BallisticEngine.solve(scaledProfile, icaoAtmosphere, 200.0, options = opts)
        assertEquals(trueDsfVal, scaledSol.appliedDsf, 1e-4)
        assertTrue(
            "DSF 1.08 should increase drop magnitude by ~8% (base=${baseSol.dropInches}, scaled=${scaledSol.dropInches})",
            abs(scaledSol.dropInches / baseSol.dropInches - trueDsfVal) < 0.005
        )

        // Now run trueDropScaleFactor on the uncalibrated profile given observed elevation
        val recovered = BallisticEngine.trueDropScaleFactor(
            profile = sub22,
            atmosphere = icaoAtmosphere,
            rangeYards = 200.0,
            observedElevationMoa = scaledSol.elevationMoa,
            options = opts
        )
        assertNotNull("Expected non-null DSF calibration point", recovered)
        assertEquals("Recovered DSF should match 1.080 within 0.005", trueDsfVal, recovered!!.dropScaleFactor, 0.005)

        // Re-solve with recovered DSF and verify elevation matches observed within 0.02 MOA
        val verifiedSol = BallisticEngine.solve(
            sub22.copy(dsfTable = BallisticEngine.mergeDsfPoint(emptyList(), recovered)),
            icaoAtmosphere,
            200.0,
            options = opts
        )
        assertEquals(scaledSol.elevationMoa, verifiedSol.elevationMoa, 0.02)

        // 2. Verify supersonic regime (Mach > 1.2) stays completely unscaled (DSF = 1.0)
        val supersonicRifle = RifleProfile(
            name = "6.5 CM",
            caliber = "6.5 Creedmoor",
            dragModel = DragModel.G7,
            ballisticCoefficient = 0.315,
            muzzleVelocityFps = 2700.0,
            sightHeightInches = 1.8,
            zeroRangeYards = 100.0,
            zeroHumidityPercent = 0.0,
            dsfTable = listOf(DsfPoint(mach = 0.95, dropScaleFactor = 1.12))
        )
        val super500 = BallisticEngine.solve(supersonicRifle, icaoAtmosphere, 500.0, options = opts)
        assertTrue("Expected Mach > 1.2 at 500 yd, got ${super500.mach}", super500.mach > 1.2)
        assertEquals("Supersonic flight > Mach 1.2 must keep DSF = 1.0", 1.0, super500.appliedDsf, 1e-6)
    }

    @Test
    fun threeZoneWindMatchesUniformBaselineAndWeightsNearWindHigherThanFarWind() {
        val opts = BallisticEngine.SolverOptions(includeSpinDrift = false, includeCoriolis = false, includeAeroJump = false)
        val profile = RifleProfile(
            name = "6.5 CM Wind Test",
            caliber = "6.5 Creedmoor",
            dragModel = DragModel.G7,
            ballisticCoefficient = 0.315,
            muzzleVelocityFps = 2700.0,
            bulletWeightGrains = 143.0,
            bulletDiameterInches = 0.264,
            twistDirection = TwistDirection.NONE,
            zeroHumidityPercent = 0.0
        )

        val uniformSingle = icaoAtmosphere.copy(windSpeedMph = 10.0, windDirectionDeg = 90.0)
        val uniformThreeZone = icaoAtmosphere.copy(
            windSpeedMph = 10.0,
            windDirectionDeg = 90.0,
            windProfile = WindProfile(
                mode = WindMode.THREE_ZONE,
                near = WindZone(10.0, 90.0),
                mid = WindZone(10.0, 90.0),
                far = WindZone(10.0, 90.0)
            )
        )

        val solSingle = BallisticEngine.solve(profile, uniformSingle, 800.0, options = opts)
        val solUniform3 = BallisticEngine.solve(profile, uniformThreeZone, 800.0, options = opts)
        assertEquals("Uniform 3-zone wind must match single wind windage", solSingle.windageMoa, solUniform3.windageMoa, 0.01)

        // Near-only 12 mph vs Far-only 12 mph: near wind has much greater ballistic deflection leverage!
        val nearOnly = icaoAtmosphere.copy(
            windProfile = WindProfile(
                mode = WindMode.THREE_ZONE,
                near = WindZone(12.0, 90.0),
                mid = WindZone(0.0, 90.0),
                far = WindZone(0.0, 90.0)
            )
        )
        val farOnly = icaoAtmosphere.copy(
            windProfile = WindProfile(
                mode = WindMode.THREE_ZONE,
                near = WindZone(0.0, 90.0),
                mid = WindZone(0.0, 90.0),
                far = WindZone(12.0, 90.0)
            )
        )
        val solNear = BallisticEngine.solve(profile, nearOnly, 800.0, options = opts)
        val solFar = BallisticEngine.solve(profile, farOnly, 800.0, options = opts)
        assertTrue(
            "Near-zone wind (${solNear.windageMoa} MOA) must produce substantially more drift than far-zone wind (${solFar.windageMoa} MOA)",
            solNear.windageMoa > solFar.windageMoa * 2.0
        )

        // Opposing near (+90°) and mid/far (-90° / 270°) partially cancel
        val opposing = icaoAtmosphere.copy(
            windProfile = WindProfile(
                mode = WindMode.THREE_ZONE,
                near = WindZone(10.0, 90.0),
                mid = WindZone(10.0, 270.0),
                far = WindZone(10.0, 270.0)
            )
        )
        val solOpp = BallisticEngine.solve(profile, opposing, 800.0, options = opts)
        assertTrue(
            "Opposing wind bands should partially cancel vs uniform 10 mph wind",
            abs(solOpp.windageMoa) < abs(solSingle.windageMoa) * 0.5
        )
    }

    @Test
    fun stageTargetSolverAppliesTargetSpecificWindInclinationAndMover() {
        val profile = DefaultProfiles.starterProfiles.first()
        val target = StageTarget(
            id = "t-test",
            label = "T1",
            rangeYards = 150.0,
            inclinationDeg = 15.0,
            windSpeedMph = 12.0,
            windDirectionDeg = 90.0,
            moverSpeedMph = 4.0
        )
        val sol = BallisticEngine.solveStageTarget(profile, icaoAtmosphere, target)
        assertEquals(150.0, sol.rangeYards, 0.01)
        assertEquals(15.0, sol.angleDegrees, 0.01)
        assertTrue("Expected positive right windage from 12 mph 3 o'clock override", sol.windageMoa > 0.5)
        assertTrue("Expected non-zero mover lead for 4 mph mover", sol.moverLeadMoa > 0.5)
    }

    @Test
    fun arsenalBackupJsonRoundTripAndMergeOverwritePreserveAllData() {
        val customAmmo = AmmoLoad(
            id = "ammo-custom-65",
            name = "6.5 CM Custom 140 ELD-M",
            caliber = "6.5 Creedmoor",
            dragModel = DragModel.G7,
            ballisticCoefficient = 0.326,
            bcBands = listOf(BcBand(2500.0, 0.326), BcBand(1800.0, 0.312), BcBand(0.0, 0.298)),
            dsfTable = listOf(DsfPoint(0.92, 1.045), DsfPoint(1.08, 1.018)),
            muzzleVelocityFps = 2745.0,
            bulletWeightGrains = 140.0,
            bulletDiameterInches = 0.264
        )
        val customStage = StageCard(
            id = "stage-match-1",
            name = "PRS Match Stage 4",
            targets = listOf(
                StageTarget(id = "st1", label = "T1", rangeYards = 420.0, inclinationDeg = 5.0, windSpeedMph = 9.0, windDirectionDeg = 90.0),
                StageTarget(id = "st2", label = "T2", rangeYards = 735.0, inclinationDeg = -3.0, moverSpeedMph = 3.0)
            )
        )
        val customLog = SessionLogEntry(
            id = "log-cb-1",
            timestampMs = 1795570000000L,
            firearmId = "gun-cz-457",
            firearmName = "CZ 457 (.22 LR)",
            scopeId = "scope-arken-sh4-gen2",
            scopeName = "Arken SH-4 Gen II 4-16x50",
            ammoId = customAmmo.id,
            ammoName = customAmmo.name,
            suppressorId = "supp-gemtech-gm22",
            suppressorName = "Gemtech GM-22",
            temperatureF = 64.0,
            stationPressureInHg = 29.45,
            densityAltitudeFt = 1150.0,
            windSpeedMph = 7.5,
            windDirectionDeg = 90.0,
            targetRangeYards = 100.0,
            inclinationDeg = 0.0,
            angularUnit = AngularUnit.MRAD,
            predictedElevation = 1.85,
            predictedWindage = 0.42,
            actualElevation = 1.95,
            actualWindage = 0.40,
            isColdBore = true,
            notes = "Clean bore cold shot +0.1 MRAD high"
        )
        val customPrefs = UserPreferences(
            angularUnit = AngularUnit.MRAD,
            defaultAtmosphere = Atmosphere(
                temperatureF = 64.0,
                windProfile = WindProfile(
                    mode = WindMode.THREE_ZONE,
                    near = WindZone(6.0, 90.0),
                    mid = WindZone(9.0, 75.0),
                    far = WindZone(11.0, 60.0)
                )
            )
        )

        val original = ArsenalBackup(
            schemaVersion = 1,
            exportedAtMs = 1795570000000L,
            firearms = DefaultProfiles.starterFirearms,
            scopes = DefaultProfiles.starterScopes,
            ammoLoads = DefaultProfiles.starterAmmo + customAmmo,
            suppressors = DefaultProfiles.starterSuppressors,
            stageCards = listOf(customStage),
            sessionLogs = listOf(customLog),
            preferences = customPrefs
        )

        val encoded = ArsenalBackupCodec.encode(original)
        assertTrue(encoded.contains("\"schemaVersion\": 1"))
        val decoded = ArsenalBackupCodec.decode(encoded).getOrThrow()

        assertEquals(original.schemaVersion, decoded.schemaVersion)
        assertEquals(original.firearms.size, decoded.firearms.size)
        assertEquals(original.ammoLoads.size, decoded.ammoLoads.size)
        val restoredAmmo = decoded.ammoLoads.first { it.id == customAmmo.id }
        assertEquals(customAmmo.bcBands, restoredAmmo.bcBands)
        assertEquals(customAmmo.dsfTable, restoredAmmo.dsfTable)
        assertEquals(original.stageCards, decoded.stageCards)
        assertEquals(original.sessionLogs, decoded.sessionLogs)
        assertEquals(customPrefs.defaultAtmosphere.windProfile, decoded.preferences.defaultAtmosphere.windProfile)

        // Verify non-destructive MERGE preserves existing items and adds only new ones
        val localExisting = ArsenalBackup(
            schemaVersion = 1,
            firearms = DefaultProfiles.starterFirearms,
            scopes = DefaultProfiles.starterScopes,
            ammoLoads = DefaultProfiles.starterAmmo,
            suppressors = DefaultProfiles.starterSuppressors,
            stageCards = DefaultProfiles.starterStageCards,
            sessionLogs = emptyList(),
            preferences = UserPreferences()
        )
        val (merged, mergeSummary) = ArsenalBackupCodec.applyImport(localExisting, decoded, BackupImportMode.MERGE)
        assertEquals(BackupImportMode.MERGE, mergeSummary.mode)
        assertEquals(1, mergeSummary.ammoCount)
        assertEquals(1, mergeSummary.stageCardsCount)
        assertEquals(1, mergeSummary.sessionLogsCount)
        assertEquals(DefaultProfiles.starterStageCards.size + 1, merged.stageCards.size)

        // Verify OVERWRITE replaces entire dataset cleanly
        val (overwritten, overwriteSummary) = ArsenalBackupCodec.applyImport(localExisting, decoded, BackupImportMode.OVERWRITE)
        assertEquals(BackupImportMode.OVERWRITE, overwriteSummary.mode)
        assertEquals(1, overwritten.stageCards.size)
        assertEquals(customStage.id, overwritten.stageCards.first().id)
        assertEquals(1, overwritten.sessionLogs.size)
    }

    @Test
    fun hotPathPerformanceWithMultiBcDsfAndThreeZoneWindIsFast() {
        val profile = RifleProfile(
            name = "Perf Rifle",
            caliber = "6.5 Creedmoor",
            dragModel = DragModel.G7,
            ballisticCoefficient = 0.315,
            bcBands = listOf(BcBand(2400.0, 0.315), BcBand(1800.0, 0.300), BcBand(0.0, 0.285)),
            dsfTable = listOf(DsfPoint(0.90, 1.05), DsfPoint(1.05, 1.02)),
            muzzleVelocityFps = 2700.0,
            sightHeightInches = 1.8,
            zeroRangeYards = 100.0
        )
        val atmo = icaoAtmosphere.copy(
            windProfile = WindProfile(
                mode = WindMode.THREE_ZONE,
                near = WindZone(8.0, 90.0),
                mid = WindZone(10.0, 60.0),
                far = WindZone(12.0, 120.0)
            )
        )
        val zeroAngle = BallisticEngine.computeZeroAngleRad(profile)
        // Warmup
        repeat(20) {
            BallisticEngine.solve(profile, atmo, 1000.0, cachedZeroAngleRad = zeroAngle)
        }
        val startNs = System.nanoTime()
        repeat(200) {
            val sol = BallisticEngine.solve(profile, atmo, 1000.0, cachedZeroAngleRad = zeroAngle)
            assertTrue(sol.reachedTarget)
        }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
        // 200 full 1000-yd RK4 solves with Multi-BC + DSF + 3-Zone Wind should easily finish well under 1500 ms on JVM
        assertTrue("Expected 200 solves < 1500 ms, took $elapsedMs ms", elapsedMs < 1500.0)
    }

    @Test
    fun expandedBallisticsCatalogHasUniqueIdsAndValidPhysicsSpecs() {
        assertTrue("Expected >= 90 catalog firearms, got ${BallisticsCatalog.firearms.size}", BallisticsCatalog.firearms.size >= 90)
        assertTrue("Expected >= 75 catalog scopes, got ${BallisticsCatalog.scopes.size}", BallisticsCatalog.scopes.size >= 75)
        assertTrue("Expected >= 100 catalog ammo loads, got ${BallisticsCatalog.ammo.size}", BallisticsCatalog.ammo.size >= 100)
        assertTrue("Expected >= 60 catalog suppressors, got ${BallisticsCatalog.suppressors.size}", BallisticsCatalog.suppressors.size >= 60)

        assertEquals(BallisticsCatalog.firearms.size, BallisticsCatalog.firearms.map { it.id }.toSet().size)
        assertEquals(BallisticsCatalog.scopes.size, BallisticsCatalog.scopes.map { it.id }.toSet().size)
        assertEquals(BallisticsCatalog.ammo.size, BallisticsCatalog.ammo.map { it.id }.toSet().size)
        assertEquals(BallisticsCatalog.suppressors.size, BallisticsCatalog.suppressors.map { it.id }.toSet().size)

        BallisticsCatalog.ammo.forEach { load ->
            assertTrue("Invalid BC for ${load.name}", load.primaryBc() in 0.05..1.5)
            assertTrue("Invalid MV for ${load.name}", load.muzzleVelocityFps in 400.0..4500.0)
            assertTrue("Invalid weight for ${load.name}", load.bulletWeightGrains in 10.0..800.0)
            assertTrue("Invalid diameter for ${load.name}", load.bulletDiameterInches in 0.15..0.60)
        }
    }
}
