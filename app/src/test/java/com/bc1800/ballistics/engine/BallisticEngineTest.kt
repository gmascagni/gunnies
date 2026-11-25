package com.bc1800.ballistics.engine

import com.bc1800.ballistics.model.Atmosphere
import com.bc1800.ballistics.model.DefaultProfiles
import com.bc1800.ballistics.model.DistanceUnit
import com.bc1800.ballistics.model.DragModel
import com.bc1800.ballistics.model.RifleProfile
import com.bc1800.ballistics.model.SuppressorCategory
import com.bc1800.ballistics.model.SuppressorSetup
import com.bc1800.ballistics.model.TwistDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
