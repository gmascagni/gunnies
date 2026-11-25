package com.bc1800.ballistics.engine

import com.bc1800.ballistics.model.Atmosphere
import com.bc1800.ballistics.model.BallisticSolution
import com.bc1800.ballistics.model.DistanceUnit
import com.bc1800.ballistics.model.DragModel
import com.bc1800.ballistics.model.RifleProfile
import com.bc1800.ballistics.model.TwistDirection
import com.bc1800.ballistics.model.WeaponMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Zero-allocation, single-pass 3-DOF+ External Ballistics Engine (RK4 integration).
 *
 * Physics features:
 * - Exact BRL/JBM G1 & G7 drag curves
 * - Humid ICAO air density, station pressure conversion, speed of sound, and density altitude
 * - True 3D inclined-fire gravity AND horizontal wind vector decomposition
 * - Powder temperature sensitivity (fps/°F) + Zero POI vertical/horizontal offset + Scope SSF
 * - Density-corrected Miller gyroscopic stability (Sg), Bryan Litz Spin Drift, Aerodynamic Jump
 * - Full horizontal & vertical (Eötvös) Coriolis acceleration from GPS latitude + shooting bearing
 * - Moving target lead ("Mover") + live Wind Gust Bracket
 * - Muzzle Velocity (MV) Truing root solver from observed impact drop
 * - True O(N) single-pass range card & MPBR trajectory sweep
 */
object BallisticEngine {

    private const val GRAVITY_FPS2 = 32.17405
    private const val EARTH_OMEGA_RAD_S = 7.2921159e-5
    private const val ICAO_STD_DENSITY_KG_M3 = 1.2250
    private const val ICAO_DRAG_CONST = 2.085510e-4 // (pi * rho0_lb_ft3) / (8 * 144)
    private const val RD_AIR = 287.058 // J/(kg*K)
    private const val RV_VAPOR = 461.495 // J/(kg*K)
    private const val GRAINS_PER_LB = 7000.0
    private const val BC1800_ONBOARD_LIMIT_YARDS = 800.0
    private const val METERS_TO_YARDS = 1.0936133

    data class AirState(
        val stationPressureInHg: Double,
        val densityRatio: Double, // rho / rho_ICAO
        val speedOfSoundFps: Double,
        val densityAltitudeFt: Double
    )

    data class SolverOptions(
        val includeSpinDrift: Boolean = true,
        val includeCoriolis: Boolean = true,
        val includeAeroJump: Boolean = true,
        val moverSpeedMph: Double = 0.0
    )

    fun stationPressureInHg(atmo: Atmosphere): Double {
        return if (atmo.pressureIsStation) {
            atmo.pressureInHg
        } else {
            val factor = (1.0 - 6.8755856e-6 * atmo.altitudeFeet).coerceAtLeast(0.1)
            atmo.pressureInHg * factor.pow(5.25588)
        }.coerceIn(10.0, 35.0)
    }

    fun computeAirState(atmo: Atmosphere): AirState {
        val tempC = (atmo.temperatureF - 32.0) * 5.0 / 9.0
        val tempK = (tempC + 273.15).coerceAtLeast(180.0)
        val stationInHg = stationPressureInHg(atmo)

        val pressurePa = stationInHg * 3386.38866667
        val satVaporPa = 610.78 * exp((17.27 * tempC) / (tempC + 237.3))
        val rh = (atmo.humidityPercent / 100.0).coerceIn(0.0, 1.0)
        val vaporPa = rh * satVaporPa
        val dryPa = (pressurePa - vaporPa).coerceAtLeast(1000.0)

        val densityKgM3 = (dryPa / (RD_AIR * tempK)) + (vaporPa / (RV_VAPOR * tempK))
        val densityRatio = densityKgM3 / ICAO_STD_DENSITY_KG_M3

        val speedOfSoundMs = sqrt(1.4 * RD_AIR * tempK) * (1.0 + 0.14 * (vaporPa / pressurePa))
        val speedOfSoundFps = speedOfSoundMs * 3.280839895

        val daFt = (1.0 - densityRatio.pow(0.234969)) / 6.8755856e-6

        return AirState(
            stationPressureInHg = stationInHg,
            densityRatio = densityRatio,
            speedOfSoundFps = speedOfSoundFps,
            densityAltitudeFt = daFt
        )
    }

    /** Effective muzzle velocity adjusted for powder temperature sensitivity (fps/°F). */
    fun effectiveMuzzleVelocityFps(profile: RifleProfile, tempF: Double): Double {
        val deltaTemp = tempF - profile.chronoTempF
        return (profile.muzzleVelocityFps + deltaTemp * profile.mvTempSensitivityFpsPerF)
            .coerceIn(50.0, 6000.0)
    }

    /** Bullet length in inches (explicit if provided, otherwise caliber-appropriate estimate). */
    fun estimateBulletLengthInches(profile: RifleProfile): Double {
        profile.bulletLengthInches?.let { if (it in 0.2..3.0) return it }
        val d = profile.bulletDiameterInches.coerceIn(0.17, 0.55)
        return (profile.bulletWeightGrains / (d * d * 1550.0)).coerceIn(0.32, 2.20)
    }

    /**
     * Density-corrected Miller Gyroscopic Stability Factor (Sg).
     * Sg >= 1.5 = fully stable, 1.0..1.5 = marginal, < 1.0 = unstable.
     */
    fun computeStabilityFactor(profile: RifleProfile, air: AirState): Double {
        if (profile.weaponMode != WeaponMode.RIFLE ||
            profile.twistDirection == TwistDirection.NONE ||
            profile.barrelTwistInches <= 0.0
        ) return 1.5

        val d = profile.bulletDiameterInches.coerceIn(0.17, 0.55)
        val twistCalibers = (profile.barrelTwistInches / d).coerceIn(10.0, 90.0)
        val lengthCalibers = (estimateBulletLengthInches(profile) / d).coerceIn(1.3, 6.5)
        val m = profile.bulletWeightGrains.coerceAtLeast(10.0)

        val sgBase = (30.0 * m) / (
            twistCalibers * twistCalibers * d * d * d * lengthCalibers * (1.0 + lengthCalibers * lengthCalibers)
            )
        val velCorrection = (profile.muzzleVelocityFps / 2800.0).pow(1.0 / 3.0)
        val densityCorrection = (1.0 / air.densityRatio.coerceIn(0.3, 1.5))
        return (sgBase * velCorrection * densityCorrection).coerceIn(0.4, 5.0)
    }

    /**
     * Solves for the bore launch elevation angle (radians above LOS) required so the projectile
     * hits [profile.zeroOffsetVerticalInches] at [profile.zeroRangeYards] under zero conditions.
     */
    fun computeZeroAngleRad(profile: RifleProfile): Double {
        val zeroRangeFt = (profile.zeroRangeYards * 3.0).coerceAtLeast(15.0)
        val targetZeroYFt = profile.zeroOffsetVerticalInches / 12.0
        val sightHeightFt = profile.sightHeightInches / 12.0
        val mv = effectiveMuzzleVelocityFps(profile, profile.zeroTempF)
        val bc = profile.ballisticCoefficient.coerceAtLeast(0.01)

        val zeroAtmo = Atmosphere(
            temperatureF = profile.zeroTempF,
            pressureInHg = profile.zeroPressureInHg,
            pressureIsStation = true,
            humidityPercent = profile.zeroHumidityPercent,
            windSpeedMph = 0.0
        )
        val air = computeAirState(zeroAtmo)
        val state = MutableState()

        val tApprox = zeroRangeFt / mv
        var angle0 = atan2(sightHeightFt + targetZeroYFt + 0.5 * GRAVITY_FPS2 * tApprox * tApprox, zeroRangeFt)
        var angle1 = angle0 + 0.0005

        integrateSinglePoint(
            targetRangeFt = zeroRangeFt,
            launchAngleRad = angle0,
            sightHeightFt = sightHeightFt,
            mv = mv,
            bc = bc,
            dragModel = profile.dragModel,
            air = air,
            angleRad = 0.0,
            windX = 0.0,
            windY = 0.0,
            windZ = 0.0,
            out = state
        )
        var err0 = state.y - targetZeroYFt

        for (iter in 0 until 8) {
            integrateSinglePoint(
                targetRangeFt = zeroRangeFt,
                launchAngleRad = angle1,
                sightHeightFt = sightHeightFt,
                mv = mv,
                bc = bc,
                dragModel = profile.dragModel,
                air = air,
                angleRad = 0.0,
                windX = 0.0,
                windY = 0.0,
                windZ = 0.0,
                out = state
            )
            val err1 = state.y - targetZeroYFt
            if (abs(err1) < 1e-5) return angle1
            val denom = err1 - err0
            if (abs(denom) < 1e-12) return angle1
            val nextAngle = angle1 - err1 * (angle1 - angle0) / denom
            angle0 = angle1
            err0 = err1
            angle1 = nextAngle
        }
        return angle1
    }

    /**
     * Computes a full precision firing solution at a single target range, including optional
     * Wind Gust Bracket, Coriolis, Aerodynamic Jump, Spin Drift, and Moving Target Lead.
     */
    fun solve(
        profile: RifleProfile,
        atmosphere: Atmosphere,
        rangeYards: Double,
        inclinationDeg: Double = 0.0,
        includeSpinDrift: Boolean = true,
        cachedZeroAngleRad: Double? = null,
        options: SolverOptions = SolverOptions(includeSpinDrift = includeSpinDrift)
    ): BallisticSolution {
        val clampedRangeYd = rangeYards.coerceAtLeast(5.0)
        val zeroAngle = cachedZeroAngleRad ?: computeZeroAngleRad(profile)
        val air = computeAirState(atmosphere)
        val base = integrateMultiRange(
            profile = profile,
            atmosphere = atmosphere,
            air = air,
            zeroAngleRad = zeroAngle,
            targetRangesYards = doubleArrayOf(clampedRangeYd),
            inclinationDeg = inclinationDeg,
            options = options
        ).first()

        // If gust speed exceeds sustained wind, compute gust windage bracket in one fast pass
        return if (atmosphere.windGustMph > atmosphere.windSpeedMph + 1.0) {
            val gustAtmo = atmosphere.copy(windSpeedMph = atmosphere.windGustMph, windGustMph = 0.0)
            val gustSol = integrateMultiRange(
                profile = profile,
                atmosphere = gustAtmo,
                air = air,
                zeroAngleRad = zeroAngle,
                targetRangesYards = doubleArrayOf(clampedRangeYd),
                inclinationDeg = inclinationDeg,
                options = options
            ).first()
            base.copy(
                gustWindageMoa = gustSol.windageMoa,
                gustWindageMrad = gustSol.windageMrad
            )
        } else {
            base
        }
    }

    /**
     * True single-pass O(N) DOPE Range Card generator.
     * Supports clean unit-native steps (e.g., 50m/100m in Meters mode or 50yd/100yd in Yards mode).
     */
    fun generateRangeCard(
        profile: RifleProfile,
        atmosphere: Atmosphere,
        stepUnits: Int = 50,
        maxUnits: Int = 1000,
        distanceUnit: DistanceUnit = DistanceUnit.YARDS,
        inclinationDeg: Double = 0.0,
        cachedZeroAngleRad: Double? = null,
        options: SolverOptions = SolverOptions()
    ): List<BallisticSolution> {
        val safeStep = stepUnits.coerceIn(5, 250)
        val safeMax = maxUnits.coerceIn(safeStep, 1800)
        val count = safeMax / safeStep
        val targetYards = DoubleArray(count) { i ->
            val u = (i + 1) * safeStep.toDouble()
            if (distanceUnit == DistanceUnit.METERS) u * METERS_TO_YARDS else u
        }

        val zeroAngle = cachedZeroAngleRad ?: computeZeroAngleRad(profile)
        val air = computeAirState(atmosphere)
        return integrateMultiRange(
            profile = profile,
            atmosphere = atmosphere,
            air = air,
            zeroAngleRad = zeroAngle,
            targetRangesYards = targetYards,
            inclinationDeg = inclinationDeg,
            options = options
        )
    }

    /**
     * Single-pass Maximum Point Blank Range (MPBR) calculation checking BOTH high-apex overshoot
     * (+halfVital) and low-drop exit (-halfVital) after the bullet rises past the initial climb.
     */
    fun computeMpbrYards(
        profile: RifleProfile,
        atmosphere: Atmosphere,
        vitalDiameterInches: Double = 6.0,
        cachedZeroAngleRad: Double? = null
    ): Double {
        val halfVital = vitalDiameterInches / 2.0
        val zeroAngle = cachedZeroAngleRad ?: computeZeroAngleRad(profile)
        val air = computeAirState(atmosphere)

        val ranges = DoubleArray(60) { (it + 1) * 10.0 }
        val pts = integrateMultiRange(
            profile = profile,
            atmosphere = atmosphere.copy(windSpeedMph = 0.0, windGustMph = 0.0),
            air = air,
            zeroAngleRad = zeroAngle,
            targetRangesYards = ranges,
            inclinationDeg = 0.0,
            options = SolverOptions(includeSpinDrift = false, includeCoriolis = false, includeAeroJump = false)
        )

        var enteredCorridor = false
        var lastInsideRange = profile.zeroRangeYards
        for (p in pts) {
            if (!p.reachedTarget) break
            val drop = p.dropInches
            if (!enteredCorridor) {
                if (drop >= -halfVital) {
                    enteredCorridor = true
                    lastInsideRange = p.rangeYards
                }
            } else {
                if (drop > halfVital || drop < -halfVital) {
                    return lastInsideRange.coerceAtLeast(profile.zeroRangeYards)
                }
                lastInsideRange = p.rangeYards
            }
        }
        return lastInsideRange
    }

    /**
     * Guided Muzzle Velocity (MV) Truing Solver.
     * Given an observed elevation hold (in MOA, positive = UP) at [rangeYards], solves via
     * bisection for the trued Muzzle Velocity (fps) that matches real-world impact.
     */
    fun trueMuzzleVelocity(
        profile: RifleProfile,
        atmosphere: Atmosphere,
        rangeYards: Double,
        observedElevationMoa: Double,
        inclinationDeg: Double = 0.0,
        options: SolverOptions = SolverOptions()
    ): Double? {
        if (rangeYards <= profile.zeroRangeYards + 10.0 || observedElevationMoa <= 0.1) return null

        var lowMv = (profile.muzzleVelocityFps * 0.65).coerceAtLeast(350.0)
        var highMv = (profile.muzzleVelocityFps * 1.40).coerceAtMost(5000.0)

        fun evalError(mv: Double): Double {
            val testProfile = profile.copy(muzzleVelocityFps = mv)
            val sol = solve(testProfile, atmosphere, rangeYards, inclinationDeg, options = options)
            return sol.elevationMoa - observedElevationMoa
        }

        val errLow = evalError(lowMv)
        val errHigh = evalError(highMv)
        if (errLow < 0.0 || errHigh > 0.0) return null

        for (i in 0 until 22) {
            val mid = 0.5 * (lowMv + highMv)
            val errMid = evalError(mid)
            if (abs(errMid) < 0.005) return mid
            if (errMid > 0.0) lowMv = mid else highMv = mid
        }
        return 0.5 * (lowMv + highMv)
    }

    private fun integrateMultiRange(
        profile: RifleProfile,
        atmosphere: Atmosphere,
        air: AirState,
        zeroAngleRad: Double,
        targetRangesYards: DoubleArray,
        inclinationDeg: Double,
        options: SolverOptions
    ): List<BallisticSolution> {
        val clampedAngleDeg = inclinationDeg.coerceIn(-75.0, 75.0)
        val angleRad = Math.toRadians(clampedAngleDeg)
        val cosAngle = cos(angleRad)
        val sinAngle = sin(angleRad)

        val mv = effectiveMuzzleVelocityFps(profile, atmosphere.temperatureF)
        val bc = profile.ballisticCoefficient.coerceAtLeast(0.01)
        val sightHeightFt = profile.sightHeightInches / 12.0
        val launchedSupersonic = (mv / air.speedOfSoundFps) >= 1.2

        // True 3D horizontal wind vector decomposed into LOS-tilted frame
        val windFps = atmosphere.windSpeedMph * 5280.0 / 3600.0
        val windDirRad = Math.toRadians(atmosphere.windDirectionDeg)
        val windGroundLong = -windFps * cos(windDirRad)
        val windX = windGroundLong * cosAngle
        val windY = -windGroundLong * sinAngle
        val windZ = -windFps * sin(windDirRad)

        val windValuePct = (abs(sin(windDirRad)) * 100.0).roundToInt()

        val sg = computeStabilityFactor(profile, air)
        val latRad = Math.toRadians((atmosphere.gpsLatitude ?: 35.0).coerceIn(-90.0, 90.0))
        val azRad = Math.toRadians(atmosphere.shootingBearingDeg)
        val coriolisActive = options.includeCoriolis && profile.weaponMode == WeaponMode.RIFLE && atmosphere.gpsLatitude != null

        val crosswindMph = atmosphere.windSpeedMph * sin(windDirRad)
        val aeroJumpMoa = if (options.includeAeroJump &&
            profile.weaponMode == WeaponMode.RIFLE &&
            profile.twistDirection != TwistDirection.NONE
        ) {
            val twistSign = if (profile.twistDirection == TwistDirection.RIGHT) 1.0 else -1.0
            0.012 * crosswindMph * twistSign * (1.5 / sg.coerceAtLeast(1.0))
        } else 0.0

        val gx = -GRAVITY_FPS2 * sinAngle
        val gy = -GRAVITY_FPS2 * cosAngle
        val dragPrefix = ICAO_DRAG_CONST * air.densityRatio / bc

        val sinLat = sin(latRad)
        val cosLat = cos(latRad)
        val sinAz = sin(azRad)
        val twoOmega = 2.0 * EARTH_OMEGA_RAD_S

        var x = 0.0
        var y = -sightHeightFt
        var z = 0.0
        var vx = mv * cos(zeroAngleRad)
        var vy = mv * sin(zeroAngleRad)
        var vz = 0.0
        var t = 0.0

        val dt = 0.001
        val maxTime = 16.0
        val outList = ArrayList<BallisticSolution>(targetRangesYards.size)

        val a1 = DoubleArray(3)
        val a2 = DoubleArray(3)
        val a3 = DoubleArray(3)
        val a4 = DoubleArray(3)

        fun evalAccel(cvx: Double, cvy: Double, cvz: Double, out: DoubleArray) {
            val rvx = cvx - windX
            val rvy = cvy - windY
            val rvz = cvz - windZ
            val vRel = sqrt(rvx * rvx + rvy * rvy + rvz * rvz)
            if (vRel < 1e-6) {
                out[0] = gx
                out[1] = gy
                out[2] = 0.0
                return
            }
            val mach = vRel / air.speedOfSoundFps
            val cd = DragTables.getCd(profile.dragModel, mach)
            val k = dragPrefix * cd * vRel
            val ax = -k * rvx + gx
            var ay = -k * rvy + gy
            var az = -k * rvz
            if (coriolisActive) {
                ay += twoOmega * cosLat * sinAz * cvx
                az += twoOmega * sinLat * cvx
            }
            out[0] = ax
            out[1] = ay
            out[2] = az
        }

        var nextIdx = 0
        val maxTargetFt = (targetRangesYards.lastOrNull() ?: 0.0) * 3.0

        fun buildSolution(
            reqRangeYd: Double,
            sx: Double,
            sy: Double,
            sz: Double,
            svx: Double,
            svy: Double,
            svz: Double,
            st: Double,
            reached: Boolean
        ): BallisticSolution {
            val effectiveFt = if (sx > 1.0) sx else (reqRangeYd * 3.0)
            val spinDriftIn = if (options.includeSpinDrift &&
                profile.weaponMode == WeaponMode.RIFLE &&
                profile.twistDirection != TwistDirection.NONE &&
                profile.barrelTwistInches > 0.0
            ) {
                val mag = 1.25 * (sg + 1.2) * st.pow(1.83)
                if (profile.twistDirection == TwistDirection.RIGHT) mag else -mag
            } else 0.0

            val coriolisElevIn = if (coriolisActive) 0.5 * twoOmega * cosLat * sinAz * mv * st * st * 12.0 else 0.0
            val coriolisWindIn = if (coriolisActive) 0.5 * twoOmega * sinLat * mv * st * st * 12.0 else 0.0
            val coriolisElevMoa = -atan2(coriolisElevIn / 12.0, effectiveFt) * (180.0 / PI) * 60.0
            val coriolisWindMoa = -atan2(coriolisWindIn / 12.0, effectiveFt) * (180.0 / PI) * 60.0

            val dropInches = sy * 12.0
            val elevRad = atan2(-sy, effectiveFt)
            val rawElevMoa = elevRad * (180.0 / PI) * 60.0 + aeroJumpMoa
            val elevMoa = rawElevMoa * profile.elevationSsf
            val elevMrad = elevMoa / 3.43774677
            val elevClicks = profile.clickUnit.toClicks(elevMoa, elevMrad)

            val horizZeroOffsetIn = profile.zeroOffsetHorizontalInches * (reqRangeYd / profile.zeroRangeYards.coerceAtLeast(10.0))
            val totalLateralIn = (sz * 12.0) + spinDriftIn + horizZeroOffsetIn
            val windRad = atan2(-(totalLateralIn / 12.0), effectiveFt)
            val windMoa = windRad * (180.0 / PI) * 60.0 * profile.windageSsf
            val windMrad = windMoa / 3.43774677
            val windClicks = profile.clickUnit.toClicks(windMoa, windMrad)

            val moverFps = options.moverSpeedMph * 5280.0 / 3600.0
            val moverLeadIn = moverFps * st * 12.0
            val moverLeadRad = atan2(moverLeadIn / 12.0, effectiveFt)
            val moverLeadMoa = moverLeadRad * (180.0 / PI) * 60.0
            val moverLeadMrad = moverLeadRad * 1000.0

            val speedFps = sqrt(svx * svx + svy * svy + svz * svz)
            val energyFtLbs = (profile.bulletWeightGrains * speedFps * speedFps) / (2.0 * GRAVITY_FPS2 * GRAINS_PER_LB)
            val mach = speedFps / air.speedOfSoundFps

            return BallisticSolution(
                rangeYards = reqRangeYd,
                trueHorizontalYards = reqRangeYd * cosAngle,
                angleDegrees = clampedAngleDeg,
                elevationMoa = elevMoa,
                elevationMrad = elevMrad,
                dropInches = dropInches,
                elevationClicks = elevClicks,
                windageMoa = windMoa,
                windageMrad = windMrad,
                windDriftInches = totalLateralIn,
                windageClicks = windClicks,
                windValuePercent = windValuePct,
                spinDriftInches = spinDriftIn,
                aeroJumpMoa = aeroJumpMoa,
                coriolisElevMoa = coriolisElevMoa,
                coriolisWindMoa = coriolisWindMoa,
                moverLeadMoa = moverLeadMoa,
                moverLeadMrad = moverLeadMrad,
                moverLeadInches = moverLeadIn,
                velocityFps = speedFps,
                energyFtLbs = energyFtLbs,
                timeOfFlightSec = st,
                mach = mach,
                stabilityFactorSg = sg,
                densityAltitudeFt = air.densityAltitudeFt,
                reachedTarget = reached,
                isTransonicWarning = launchedSupersonic && mach < 1.2,
                exceedsOnboard800YdLimit = reqRangeYd > BC1800_ONBOARD_LIMIT_YARDS
            )
        }

        while (nextIdx < targetRangesYards.size && x < maxTargetFt && t < maxTime && vx > 15.0) {
            val prevX = x
            val prevY = y
            val prevZ = z
            val prevVx = vx
            val prevVy = vy
            val prevVz = vz
            val prevT = t

            evalAccel(vx, vy, vz, a1)

            val vx2 = vx + 0.5 * dt * a1[0]
            val vy2 = vy + 0.5 * dt * a1[1]
            val vz2 = vz + 0.5 * dt * a1[2]
            evalAccel(vx2, vy2, vz2, a2)

            val vx3 = vx + 0.5 * dt * a2[0]
            val vy3 = vy + 0.5 * dt * a2[1]
            val vz3 = vz + 0.5 * dt * a2[2]
            evalAccel(vx3, vy3, vz3, a3)

            val vx4 = vx + dt * a3[0]
            val vy4 = vy + dt * a3[1]
            val vz4 = vz + dt * a3[2]
            evalAccel(vx4, vy4, vz4, a4)

            x += (dt / 6.0) * (vx + 2.0 * vx2 + 2.0 * vx3 + vx4)
            y += (dt / 6.0) * (vy + 2.0 * vy2 + 2.0 * vy3 + vy4)
            z += (dt / 6.0) * (vz + 2.0 * vz2 + 2.0 * vz3 + vz4)

            vx += (dt / 6.0) * (a1[0] + 2.0 * a2[0] + 2.0 * a3[0] + a4[0])
            vy += (dt / 6.0) * (a1[1] + 2.0 * a2[1] + 2.0 * a3[1] + a4[1])
            vz += (dt / 6.0) * (a1[2] + 2.0 * a2[2] + 2.0 * a3[2] + a4[2])
            t += dt

            while (nextIdx < targetRangesYards.size) {
                val targetFt = targetRangesYards[nextIdx] * 3.0
                if (x >= targetFt) {
                    val frac = if (x > prevX) (targetFt - prevX) / (x - prevX) else 1.0
                    outList.add(
                        buildSolution(
                            reqRangeYd = targetRangesYards[nextIdx],
                            sx = targetFt,
                            sy = prevY + frac * (y - prevY),
                            sz = prevZ + frac * (z - prevZ),
                            svx = prevVx + frac * (vx - prevVx),
                            svy = prevVy + frac * (vy - prevVy),
                            svz = prevVz + frac * (vz - prevVz),
                            st = prevT + frac * (t - prevT),
                            reached = true
                        )
                    )
                    nextIdx++
                } else {
                    break
                }
            }
        }

        while (nextIdx < targetRangesYards.size) {
            outList.add(
                buildSolution(
                    reqRangeYd = targetRangesYards[nextIdx],
                    sx = x,
                    sy = y,
                    sz = z,
                    svx = vx,
                    svy = vy,
                    svz = vz,
                    st = t,
                    reached = false
                )
            )
            nextIdx++
        }

        return outList
    }

    private class MutableState {
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var vx = 0.0
        var vy = 0.0
        var vz = 0.0
        var t = 0.0
    }

    private fun integrateSinglePoint(
        targetRangeFt: Double,
        launchAngleRad: Double,
        sightHeightFt: Double,
        mv: Double,
        bc: Double,
        dragModel: DragModel,
        air: AirState,
        angleRad: Double,
        windX: Double,
        windY: Double,
        windZ: Double,
        out: MutableState
    ) {
        val gx = -GRAVITY_FPS2 * sin(angleRad)
        val gy = -GRAVITY_FPS2 * cos(angleRad)
        val dragPrefix = ICAO_DRAG_CONST * air.densityRatio / bc

        var x = 0.0
        var y = -sightHeightFt
        var z = 0.0
        var vx = mv * cos(launchAngleRad)
        var vy = mv * sin(launchAngleRad)
        var vz = 0.0
        var t = 0.0
        val dt = 0.001
        val maxTime = 10.0

        val a1 = DoubleArray(3)
        val a2 = DoubleArray(3)
        val a3 = DoubleArray(3)
        val a4 = DoubleArray(3)

        fun evalAccel(cvx: Double, cvy: Double, cvz: Double, dst: DoubleArray) {
            val rvx = cvx - windX
            val rvy = cvy - windY
            val rvz = cvz - windZ
            val vRel = sqrt(rvx * rvx + rvy * rvy + rvz * rvz)
            if (vRel < 1e-6) {
                dst[0] = gx; dst[1] = gy; dst[2] = 0.0; return
            }
            val cd = DragTables.getCd(dragModel, vRel / air.speedOfSoundFps)
            val k = dragPrefix * cd * vRel
            dst[0] = -k * rvx + gx
            dst[1] = -k * rvy + gy
            dst[2] = -k * rvz
        }

        while (x < targetRangeFt && t < maxTime && vx > 15.0) {
            val prevX = x; val prevY = y; val prevZ = z
            val prevVx = vx; val prevVy = vy; val prevVz = vz; val prevT = t

            evalAccel(vx, vy, vz, a1)
            val vx2 = vx + 0.5 * dt * a1[0]; val vy2 = vy + 0.5 * dt * a1[1]; val vz2 = vz + 0.5 * dt * a1[2]
            evalAccel(vx2, vy2, vz2, a2)
            val vx3 = vx + 0.5 * dt * a2[0]; val vy3 = vy + 0.5 * dt * a2[1]; val vz3 = vz + 0.5 * dt * a2[2]
            evalAccel(vx3, vy3, vz3, a3)
            val vx4 = vx + dt * a3[0]; val vy4 = vy + dt * a3[1]; val vz4 = vz + dt * a3[2]
            evalAccel(vx4, vy4, vz4, a4)

            x += (dt / 6.0) * (vx + 2.0 * vx2 + 2.0 * vx3 + vx4)
            y += (dt / 6.0) * (vy + 2.0 * vy2 + 2.0 * vy3 + vy4)
            z += (dt / 6.0) * (vz + 2.0 * vz2 + 2.0 * vz3 + vz4)
            vx += (dt / 6.0) * (a1[0] + 2.0 * a2[0] + 2.0 * a3[0] + a4[0])
            vy += (dt / 6.0) * (a1[1] + 2.0 * a2[1] + 2.0 * a3[1] + a4[1])
            vz += (dt / 6.0) * (a1[2] + 2.0 * a2[2] + 2.0 * a3[2] + a4[2])
            t += dt

            if (x >= targetRangeFt) {
                val frac = if (x > prevX) (targetRangeFt - prevX) / (x - prevX) else 1.0
                out.x = targetRangeFt
                out.y = prevY + frac * (y - prevY)
                out.z = prevZ + frac * (z - prevZ)
                out.vx = prevVx + frac * (vx - prevVx)
                out.vy = prevVy + frac * (vy - prevVy)
                out.vz = prevVz + frac * (vz - prevVz)
                out.t = prevT + frac * (t - prevT)
                return
            }
        }
        out.x = x; out.y = y; out.z = z; out.vx = vx; out.vy = vy; out.vz = vz; out.t = t
    }
}
