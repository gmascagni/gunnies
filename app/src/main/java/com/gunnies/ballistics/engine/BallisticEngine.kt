package com.gunnies.ballistics.engine

import com.gunnies.ballistics.model.AngularUnit
import com.gunnies.ballistics.model.Atmosphere
import com.gunnies.ballistics.model.BallisticSolution
import com.gunnies.ballistics.model.BcBand
import com.gunnies.ballistics.model.CustomDragCurve
import com.gunnies.ballistics.model.DistanceUnit
import com.gunnies.ballistics.model.DragModel
import com.gunnies.ballistics.model.DsfPoint
import com.gunnies.ballistics.model.HitProbabilityResult
import com.gunnies.ballistics.model.MultiPointTruingResult
import com.gunnies.ballistics.model.RifleProfile
import com.gunnies.ballistics.model.SessionLogEntry
import com.gunnies.ballistics.model.ShotUncertaintyInput
import com.gunnies.ballistics.model.StageTarget
import com.gunnies.ballistics.model.TallTargetResult
import com.gunnies.ballistics.model.TruingObservation
import com.gunnies.ballistics.model.TruingPointResidual
import com.gunnies.ballistics.model.TwistDirection
import com.gunnies.ballistics.model.WeaponMode
import com.gunnies.ballistics.model.WindMode
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
 * - Stepped Multi-BC (velocity-banded ballistic coefficients with smooth boundary transitions)
 * - Transonic & Subsonic Drop Scale Factor (DSF) interpolation + DSF calibration solver
 * - Multi-Zone Wind Banding (Near 0–33%, Mid 33–66%, Far 66–100% downrange sampling)
 * - Humid ICAO air density, station pressure conversion, speed of sound, and density altitude
 * - True 3D inclined-fire gravity AND horizontal wind vector decomposition
 * - Powder temperature sensitivity (fps/°F) + Zero POI vertical/horizontal offset + Scope SSF
 * - Density-corrected Miller gyroscopic stability (Sg), Bryan Litz Spin Drift, Aerodynamic Jump
 * - Full horizontal & vertical (Eötvös) Coriolis acceleration from GPS latitude + shooting bearing
 * - Moving target lead ("Mover") + live Wind Gust Bracket
 * - Two-stage Ballistic Calibration: Supersonic MV Truing + Transonic/Subsonic DSF Truing
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
    private const val LBC1800_ONBOARD_LIMIT_YARDS = 800.0
    private const val METERS_TO_YARDS = 1.0936133
    private const val BC_BLEND_HALF_FPS = 20.0
    const val TRANSONIC_MACH_GATE = 1.20

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
     * Zero-allocation velocity-banded BC lookup using primitive arrays sorted descending by minVelocityFps.
     * Applies a smooth ±20 fps linear transition across step boundaries so RK4 sees no step discontinuity.
     */
    private fun lookupBc(
        vRel: Double,
        fallbackBc: Double,
        bandVels: DoubleArray,
        bandBcs: DoubleArray,
        bandCount: Int
    ): Double {
        if (bandCount == 0) return fallbackBc
        if (bandCount == 1) return bandBcs[0]

        var i = 0
        while (i < bandCount - 1) {
            val thresh = bandVels[i]
            if (vRel >= thresh + BC_BLEND_HALF_FPS) {
                return bandBcs[i]
            }
            if (vRel > thresh - BC_BLEND_HALF_FPS) {
                val frac = (vRel - (thresh - BC_BLEND_HALF_FPS)) / (2.0 * BC_BLEND_HALF_FPS)
                return bandBcs[i + 1] + frac * (bandBcs[i] - bandBcs[i + 1])
            }
            i++
        }
        return bandBcs[bandCount - 1]
    }

    /**
     * Zero-allocation interpolated Drop Scale Factor lookup using primitive arrays sorted ascending by Mach.
     * Above Mach 1.20 (unless an explicit node >= 1.20 is defined), smoothly returns 1.000.
     */
    private fun evalDsf(
        mach: Double,
        dsfMachs: DoubleArray,
        dsfVals: DoubleArray,
        count: Int
    ): Double {
        if (count == 0) return 1.0
        if (mach <= dsfMachs[0]) return dsfVals[0]

        var i = 0
        while (i < count - 1) {
            val m0 = dsfMachs[i]
            val m1 = dsfMachs[i + 1]
            if (mach <= m1) {
                val span = m1 - m0
                if (span < 1e-6) return dsfVals[i + 1]
                val frac = (mach - m0) / span
                return dsfVals[i] + frac * (dsfVals[i + 1] - dsfVals[i])
            }
            i++
        }

        val lastMach = dsfMachs[count - 1]
        val lastVal = dsfVals[count - 1]
        return if (lastMach < TRANSONIC_MACH_GATE) {
            if (mach >= TRANSONIC_MACH_GATE) {
                1.0
            } else {
                val frac = (mach - lastMach) / (TRANSONIC_MACH_GATE - lastMach)
                lastVal + frac * (1.0 - lastVal)
            }
        } else {
            lastVal
        }
    }

    private fun packBcBands(bands: List<BcBand>, outVels: DoubleArray, outBcs: DoubleArray): Int {
        if (bands.isEmpty()) return 0
        val sorted = bands
            .filter { it.bc in 0.01..2.0 && it.minVelocityFps >= 0.0 }
            .sortedByDescending { it.minVelocityFps }
        val count = sorted.size.coerceAtMost(outVels.size)
        for (i in 0 until count) {
            outVels[i] = sorted[i].minVelocityFps
            outBcs[i] = sorted[i].bc.coerceAtLeast(0.01)
        }
        return count
    }

    private fun packDsfTable(table: List<DsfPoint>, outMachs: DoubleArray, outVals: DoubleArray): Int {
        if (table.isEmpty()) return 0
        val sorted = table
            .filter { it.mach in 0.05..5.0 && it.dropScaleFactor in 0.5..1.6 }
            .sortedBy { it.mach }
        val count = sorted.size.coerceAtMost(outMachs.size)
        for (i in 0 until count) {
            outMachs[i] = sorted[i].mach
            outVals[i] = sorted[i].dropScaleFactor
        }
        return count
    }

    /** Packs a [CustomDragCurve] into primitive arrays (sorted ascending by Mach) for zero-alloc RK4 lookup. */
    private fun packCustomDragCurve(
        curve: CustomDragCurve?,
        outMachs: DoubleArray,
        outCds: DoubleArray
    ): Int {
        if (curve == null || curve.points.size < 2) return 0
        val sorted = curve.points
            .filter { it.mach in 0.0..5.0 && it.cd in 0.01..3.0 }
            .sortedBy { it.mach }
        if (sorted.size < 2) return 0
        val count = sorted.size.coerceAtMost(outMachs.size)
        for (i in 0 until count) {
            outMachs[i] = sorted[i].mach
            outCds[i] = sorted[i].cd
        }
        return count
    }

    /** Zero-allocation binary-search linear interpolation on a custom Cd(Mach) table. */
    private fun evalCustomCd(
        mach: Double,
        machs: DoubleArray,
        cds: DoubleArray,
        count: Int
    ): Double {
        if (mach <= machs[0]) return cds[0]
        val last = count - 1
        if (mach >= machs[last]) return cds[last]

        var low = 0
        var high = last
        while (low + 1 < high) {
            val mid = (low + high) ushr 1
            if (machs[mid] <= mach) low = mid else high = mid
        }
        val m0 = machs[low]
        val m1 = machs[high]
        val span = m1 - m0
        if (span < 1e-9) return cds[low]
        val frac = (mach - m0) / span
        return cds[low] + frac * (cds[high] - cds[low])
    }

    /** Sectional density SD = (weight_gr / 7000) / diameter_in^2, used as divisor for physical Cd(Mach) curves. */
    fun computeSectionalDensity(profile: RifleProfile): Double {
        val d = profile.bulletDiameterInches.coerceIn(0.14, 0.75)
        val wtLb = profile.bulletWeightGrains.coerceAtLeast(5.0) / GRAINS_PER_LB
        return (wtLb / (d * d)).coerceAtLeast(0.01)
    }

    /**
     * Merges a newly calibrated DSF node into an existing table (replacing any node within 0.05 Mach)
     * and returns the updated table sorted ascending by Mach.
     */
    fun mergeDsfPoint(existing: List<DsfPoint>, point: DsfPoint): List<DsfPoint> {
        val roundedMach = (point.mach * 100.0).roundToInt() / 100.0
        val roundedDsf = (point.dropScaleFactor * 1000.0).roundToInt() / 1000.0
        val filtered = existing.filterNot { abs(it.mach - roundedMach) < 0.05 }
        return (filtered + DsfPoint(roundedMach, roundedDsf.coerceIn(0.65, 1.45)))
            .sortedBy { it.mach }
            .take(6)
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
        val bc = profile.primaryBc().coerceAtLeast(0.01)

        val bandVels = DoubleArray(8)
        val bandBcs = DoubleArray(8)
        val bandCount = packBcBands(profile.bcBands, bandVels, bandBcs)

        val customMachs = DoubleArray(128)
        val customCds = DoubleArray(128)
        val customCount = if (profile.usesCustomDragCurve()) {
            packCustomDragCurve(profile.customDragCurve, customMachs, customCds)
        } else 0
        val sectionalDensity = computeSectionalDensity(profile)

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
            baseBc = bc,
            bandVels = bandVels,
            bandBcs = bandBcs,
            bandCount = bandCount,
            customMachs = customMachs,
            customCds = customCds,
            customCount = customCount,
            sectionalDensity = sectionalDensity,
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
                baseBc = bc,
                bandVels = bandVels,
                bandBcs = bandBcs,
                bandCount = bandCount,
                customMachs = customMachs,
                customCds = customCds,
                customCount = customCount,
                sectionalDensity = sectionalDensity,
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
     * Computes a full precision firing solution at a single target range, including Multi-BC,
     * DSF, 3-Zone Wind, Wind Gust Bracket, Coriolis, Aerodynamic Jump, Spin Drift, and Mover Lead.
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
        val sustainedMph = atmosphere.effectiveSingleWind().speedMph
        return if (atmosphere.windGustMph > sustainedMph + 1.0) {
            val gustAtmo = if (atmosphere.isThreeZoneWind && atmosphere.windProfile != null) {
                val wp = atmosphere.windProfile
                val ratio = (atmosphere.windGustMph / sustainedMph.coerceAtLeast(1.0)).coerceIn(1.0, 4.0)
                atmosphere.copy(
                    windGustMph = 0.0,
                    windProfile = wp.copy(
                        near = wp.near.copy(speedMph = (wp.near.speedMph * ratio).coerceAtMost(60.0)),
                        mid = wp.mid.copy(speedMph = (wp.mid.speedMph * ratio).coerceAtMost(60.0)),
                        far = wp.far.copy(speedMph = (wp.far.speedMph * ratio).coerceAtMost(60.0))
                    )
                )
            } else {
                atmosphere.copy(windSpeedMph = atmosphere.windGustMph, windGustMph = 0.0)
            }
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
     * Solves a single PRS / Field Stage Card target using optional per-target wind & inclination overrides.
     */
    fun solveStageTarget(
        profile: RifleProfile,
        baseAtmosphere: Atmosphere,
        target: StageTarget,
        cachedZeroAngleRad: Double? = null,
        includeSpinDrift: Boolean = true,
        includeCoriolis: Boolean = true,
        includeAeroJump: Boolean = true
    ): BallisticSolution {
        val targetAtmo = if (target.windSpeedMph != null || target.windDirectionDeg != null) {
            val baseWind = baseAtmosphere.effectiveSingleWind()
            baseAtmosphere.copy(
                windSpeedMph = target.windSpeedMph ?: baseWind.speedMph,
                windDirectionDeg = target.windDirectionDeg ?: baseWind.directionDeg,
                windGustMph = 0.0,
                windProfile = null
            )
        } else {
            baseAtmosphere
        }
        return solve(
            profile = profile,
            atmosphere = targetAtmo,
            rangeYards = target.rangeYards,
            inclinationDeg = target.inclinationDeg,
            cachedZeroAngleRad = cachedZeroAngleRad,
            options = SolverOptions(
                includeSpinDrift = includeSpinDrift,
                includeCoriolis = includeCoriolis,
                includeAeroJump = includeAeroJump,
                moverSpeedMph = target.moverSpeedMph
            )
        )
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
            atmosphere = atmosphere.copy(windSpeedMph = 0.0, windGustMph = 0.0, windProfile = null),
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
     * Stage 1 Calibration — Supersonic Muzzle Velocity (MV) Truing Solver.
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

    /**
     * Stage 2 Calibration — Transonic / Subsonic Drop Scale Factor (DSF) Truing Solver.
     * Locks Muzzle Velocity and solves via bisection for the Drop Scale Factor at the target's
     * Mach regime (Mach <= 1.2) so predicted elevation matches [observedElevationMoa].
     */
    fun trueDropScaleFactor(
        profile: RifleProfile,
        atmosphere: Atmosphere,
        rangeYards: Double,
        observedElevationMoa: Double,
        inclinationDeg: Double = 0.0,
        options: SolverOptions = SolverOptions()
    ): DsfPoint? {
        if (rangeYards <= profile.zeroRangeYards + 10.0 || observedElevationMoa <= 0.1) return null

        val zeroAngle = computeZeroAngleRad(profile)
        val baseSol = solve(
            profile = profile.copy(dsfTable = emptyList()),
            atmosphere = atmosphere,
            rangeYards = rangeYards,
            inclinationDeg = inclinationDeg,
            cachedZeroAngleRad = zeroAngle,
            options = options
        )
        if (!baseSol.reachedTarget || baseSol.mach > TRANSONIC_MACH_GATE + 0.001) return null
        val targetMach = ((baseSol.mach * 100.0).roundToInt() / 100.0).coerceIn(0.10, TRANSONIC_MACH_GATE)

        var lowDsf = 0.65
        var highDsf = 1.45

        fun evalError(candidateDsf: Double): Double {
            val testTable = mergeDsfPoint(profile.dsfTable, DsfPoint(targetMach, candidateDsf))
            val sol = solve(
                profile = profile.copy(dsfTable = testTable),
                atmosphere = atmosphere,
                rangeYards = rangeYards,
                inclinationDeg = inclinationDeg,
                cachedZeroAngleRad = zeroAngle,
                options = options
            )
            return sol.elevationMoa - observedElevationMoa
        }

        val errLow = evalError(lowDsf)
        val errHigh = evalError(highDsf)
        if (errLow > 0.0 || errHigh < 0.0) return null

        for (i in 0 until 22) {
            val mid = 0.5 * (lowDsf + highDsf)
            val errMid = evalError(mid)
            if (abs(errMid) < 0.003) {
                return DsfPoint(mach = targetMach, dropScaleFactor = (mid * 1000.0).roundToInt() / 1000.0)
            }
            if (errMid < 0.0) lowDsf = mid else highDsf = mid
        }
        val solved = 0.5 * (lowDsf + highDsf)
        return DsfPoint(mach = targetMach, dropScaleFactor = (solved * 1000.0).roundToInt() / 1000.0)
    }

    /**
     * Converts a logged range session / cold-bore shot into a [TruingObservation] using the shot's
     * recorded range, actual elevation, inclination, and atmospheric snapshot (temp & station pressure).
     */
    fun logEntryToTruingObservation(
        entry: SessionLogEntry,
        fallbackAtmosphere: Atmosphere
    ): TruingObservation {
        val obsMoa = if (entry.angularUnit == AngularUnit.MRAD) {
            entry.actualElevation * 3.43774677
        } else {
            entry.actualElevation
        }
        val shotAtmo = fallbackAtmosphere.copy(
            temperatureF = entry.temperatureF,
            pressureInHg = entry.stationPressureInHg,
            pressureIsStation = true,
            windSpeedMph = entry.windSpeedMph,
            windDirectionDeg = entry.windDirectionDeg,
            windProfile = null
        )
        val tag = if (entry.isColdBore) "Cold Bore" else "Log"
        return TruingObservation(
            rangeYards = entry.targetRangeYards,
            observedElevationMoa = obsMoa,
            inclinationDeg = entry.inclinationDeg,
            atmosphere = shotAtmo,
            label = "$tag ${entry.targetRangeYards.roundToInt()} yd",
            isColdBore = entry.isColdBore
        )
    }

    /**
     * Multi-Point Ballistic Calibration — damped Levenberg-Marquardt least-squares fitter.
     *
     * Solves for Muzzle Velocity (MV) alone (1-point or when [fitBc] is false / Custom Cd active)
     * or jointly fits **MV and BC** (scaling single BC or all Multi-BC bands proportionally) across
     * multiple observed drops at different distances, returning per-point residuals and RMS error.
     */
    fun fitMvAndBc(
        profile: RifleProfile,
        atmosphere: Atmosphere,
        observations: List<TruingObservation>,
        fitBc: Boolean = true,
        options: SolverOptions = SolverOptions()
    ): MultiPointTruingResult? {
        val validObs = observations
            .filter { it.rangeYards > profile.zeroRangeYards + 10.0 && it.observedElevationMoa > 0.1 }
            .sortedBy { it.rangeYards }
        if (validObs.isEmpty()) return null

        val n = validObs.size
        val minRange = validObs.first().rangeYards
        val maxRange = validObs.last().rangeYards
        val rangeSpread = maxRange - minRange
        val warnings = mutableListOf<String>()

        val canFitBc = fitBc && n >= 2 && rangeSpread >= 25.0 && !profile.usesCustomDragCurve()
        if (fitBc && profile.usesCustomDragCurve()) {
            warnings += "Custom Cd(Mach) curve active — fitted MV only while preserving custom drag table."
        } else if (fitBc && n >= 2 && rangeSpread < 25.0) {
            warnings += "Distances are within 25 yd of each other — fitted MV only. Add a farther range to separate BC."
        } else if (canFitBc && rangeSpread < 150.0) {
            warnings += "Range spread is ${rangeSpread.roundToInt()} yd; wider distance separation improves MV vs. BC accuracy."
        }
        if (validObs.any { it.isColdBore }) {
            warnings += "Includes Cold Bore shot(s) — verify cold-bore POI shift does not bias MV/BC."
        }

        val baseMv = profile.muzzleVelocityFps.coerceIn(350.0, 5000.0)
        val minMv = (baseMv * 0.65).coerceAtLeast(350.0)
        val maxMv = (baseMv * 1.40).coerceAtMost(5000.0)
        val basePrimaryBc = profile.primaryBc().coerceIn(0.02, 2.0)
        val minBcScale = (0.02 / basePrimaryBc).coerceAtLeast(0.65)
        val maxBcScale = (2.0 / basePrimaryBc).coerceAtMost(1.35)

        fun buildCandidate(mv: Double, bcScale: Double): RifleProfile {
            val scaledPrimary = (profile.ballisticCoefficient * bcScale).coerceIn(0.02, 2.0)
            val scaledBands = profile.bcBands.map { band ->
                band.copy(bc = (band.bc * bcScale).coerceIn(0.02, 2.0))
            }
            return profile.copy(
                muzzleVelocityFps = mv,
                ballisticCoefficient = scaledPrimary,
                bcBands = scaledBands
            )
        }

        fun evalSolutions(mv: Double, bcScale: Double): List<BallisticSolution> {
            val cand = buildCandidate(mv, bcScale)
            val zeroAngle = computeZeroAngleRad(cand)
            return validObs.map { obs ->
                solve(
                    profile = cand,
                    atmosphere = obs.atmosphere ?: atmosphere,
                    rangeYards = obs.rangeYards,
                    inclinationDeg = obs.inclinationDeg,
                    cachedZeroAngleRad = zeroAngle,
                    options = options
                )
            }
        }

        fun evalResiduals(mv: Double, bcScale: Double, out: DoubleArray): Double {
            val cand = buildCandidate(mv, bcScale)
            val zeroAngle = computeZeroAngleRad(cand)
            var sumSq = 0.0
            for (i in 0 until n) {
                val obs = validObs[i]
                val sol = solve(
                    profile = cand,
                    atmosphere = obs.atmosphere ?: atmosphere,
                    rangeYards = obs.rangeYards,
                    inclinationDeg = obs.inclinationDeg,
                    cachedZeroAngleRad = zeroAngle,
                    options = options
                )
                val r = sol.elevationMoa - obs.observedElevationMoa
                out[i] = r
                sumSq += r * r
            }
            return 0.5 * sumSq
        }

        val beforeSols = evalSolutions(baseMv, 1.0)
        val beforeRms = sqrt(
            validObs.indices.sumOf { i ->
                val d = beforeSols[i].elevationMoa - validObs[i].observedElevationMoa
                d * d
            } / n
        )

        var curMv = baseMv
        var curScale = 1.0
        val res = DoubleArray(n)
        val resPlus = DoubleArray(n)
        val resMinus = DoubleArray(n)
        val trialRes = DoubleArray(n)
        val jMv = DoubleArray(n)
        val jBc = DoubleArray(n)

        var curCost = evalResiduals(curMv, curScale, res)

        // Stage A: Quick 1D damped Gauss-Newton on MV (stabilizes initial guess before 2D fit)
        var lambda1d = 1e-3
        val mvIters = if (canFitBc) 5 else 16
        for (iter in 0 until mvIters) {
            val rms = sqrt((2.0 * curCost) / n)
            if (rms < 0.002) break
            val dMv = (curMv * 0.002).coerceAtLeast(1.0)
            evalResiduals((curMv + dMv).coerceAtMost(maxMv), curScale, resPlus)
            evalResiduals((curMv - dMv).coerceAtLeast(minMv), curScale, resMinus)
            val span = (curMv + dMv).coerceAtMost(maxMv) - (curMv - dMv).coerceAtLeast(minMv)
            if (span < 1e-6) break

            var jtJ = 0.0
            var jtR = 0.0
            for (i in 0 until n) {
                val g = (resPlus[i] - resMinus[i]) / span
                jtJ += g * g
                jtR += g * res[i]
            }
            if (jtJ < 1e-12) break
            val step = -jtR / (jtJ + lambda1d * jtJ.coerceAtLeast(1e-6))
            val nextMv = (curMv + step.coerceIn(-400.0, 400.0)).coerceIn(minMv, maxMv)
            val nextCost = evalResiduals(nextMv, curScale, trialRes)
            if (nextCost < curCost) {
                curMv = nextMv
                curCost = nextCost
                System.arraycopy(trialRes, 0, res, 0, n)
                lambda1d = (lambda1d * 0.3).coerceAtLeast(1e-7)
                if (abs(step) < 0.05) break
            } else {
                lambda1d = (lambda1d * 5.0).coerceAtMost(1e5)
            }
        }

        // Stage B: 2D damped Levenberg-Marquardt on (MV, BC scale)
        if (canFitBc) {
            var lambda = 1e-3
            for (iter in 0 until 20) {
                val rms = sqrt((2.0 * curCost) / n)
                if (rms < 0.002) break

                val dMv = (curMv * 0.002).coerceAtLeast(1.0)
                val mvHi = (curMv + dMv).coerceAtMost(maxMv)
                val mvLo = (curMv - dMv).coerceAtLeast(minMv)
                val mvSpan = mvHi - mvLo
                if (mvSpan < 1e-6) break

                evalResiduals(mvHi, curScale, resPlus)
                evalResiduals(mvLo, curScale, resMinus)
                for (i in 0 until n) {
                    jMv[i] = (resPlus[i] - resMinus[i]) / mvSpan
                }

                val dSc = 0.002
                val scHi = (curScale + dSc).coerceAtMost(maxBcScale)
                val scLo = (curScale - dSc).coerceAtLeast(minBcScale)
                val scSpan = scHi - scLo
                if (scSpan < 1e-6) break

                evalResiduals(curMv, scHi, resPlus)
                evalResiduals(curMv, scLo, resMinus)
                for (i in 0 until n) {
                    jBc[i] = (resPlus[i] - resMinus[i]) / scSpan
                }

                // Scale columns so MV (~1000) and bcScale (~1) are well-conditioned
                var a00 = 0.0; var a01 = 0.0; var a11 = 0.0
                var g0 = 0.0; var g1 = 0.0
                for (i in 0 until n) {
                    val c0 = jMv[i]
                    val c1 = jBc[i]
                    a00 += c0 * c0
                    a01 += c0 * c1
                    a11 += c1 * c1
                    g0 += c0 * res[i]
                    g1 += c1 * res[i]
                }

                val d00 = a00 + lambda * a00.coerceAtLeast(1e-9)
                val d11 = a11 + lambda * a11.coerceAtLeast(1e-9)
                val det = d00 * d11 - a01 * a01
                if (abs(det) < 1e-18) {
                    lambda = (lambda * 10.0).coerceAtMost(1e6)
                    continue
                }

                val stepMv = (-(d11 * g0 - a01 * g1) / det).coerceIn(-250.0, 250.0)
                val stepSc = (-(-a01 * g0 + d00 * g1) / det).coerceIn(-0.12, 0.12)

                val nextMv = (curMv + stepMv).coerceIn(minMv, maxMv)
                val nextSc = (curScale + stepSc).coerceIn(minBcScale, maxBcScale)
                val nextCost = evalResiduals(nextMv, nextSc, trialRes)

                if (nextCost < curCost) {
                    curMv = nextMv
                    curScale = nextSc
                    curCost = nextCost
                    System.arraycopy(trialRes, 0, res, 0, n)
                    lambda = (lambda * 0.35).coerceAtLeast(1e-7)
                    if (abs(stepMv) < 0.05 && abs(stepSc) < 0.0002) break
                } else {
                    lambda = (lambda * 5.0).coerceAtMost(1e6)
                }
            }
        }

        val roundedMv = (curMv * 10.0).roundToInt() / 10.0
        val roundedScale = if (canFitBc) (curScale * 10000.0).roundToInt() / 10000.0 else 1.0
        val finalProfile = buildCandidate(roundedMv, roundedScale)
        val afterSols = evalSolutions(roundedMv, roundedScale)

        val residualItems = validObs.indices.map { i ->
            val obs = validObs[i]
            val beforePred = beforeSols[i].elevationMoa
            val afterPred = afterSols[i].elevationMoa
            TruingPointResidual(
                rangeYards = obs.rangeYards,
                observedMoa = obs.observedElevationMoa,
                predictedBeforeMoa = beforePred,
                predictedAfterMoa = afterPred,
                residualBeforeMoa = beforePred - obs.observedElevationMoa,
                residualAfterMoa = afterPred - obs.observedElevationMoa,
                mach = afterSols[i].mach,
                label = obs.label,
                isColdBore = obs.isColdBore
            )
        }

        val afterRms = sqrt(residualItems.sumOf { it.residualAfterMoa * it.residualAfterMoa } / n)
        val maxAbsErr = residualItems.maxOf { abs(it.residualAfterMoa) }

        return MultiPointTruingResult(
            fittedMvFps = roundedMv,
            fittedBc = (finalProfile.primaryBc() * 1000.0).roundToInt() / 1000.0,
            bcScaleFactor = roundedScale,
            fittedBcBands = finalProfile.bcBands.map {
                it.copy(bc = (it.bc * 1000.0).roundToInt() / 1000.0)
            },
            fittedBothMvAndBc = canFitBc,
            beforeRmsErrorMoa = beforeRms,
            rmsErrorMoa = afterRms,
            maxAbsErrorMoa = maxAbsErr,
            residuals = residualItems,
            warnings = warnings
        )
    }

    /**
     * Tall-Target Optic Tracking Calibration (Elevation or Windage Sight Scale Factor).
     *
     * Formula:
     * - Expected shift (MOA): `dialed * (distanceYards / 100.0) * 1.04719755` inches
     * - Expected shift (MRAD): `dialed * (distanceYards * 36.0 / 1000.0)` inches
     * - `SSF = expectedShiftInches / measuredShiftInches`
     *   (If a scope under-tracks by 2%, `measured / expected = 0.98`, so `SSF = 1.020` multiplies
     *   raw solver holds upward to compensate.)
     */
    fun computeTallTargetCalibration(
        distanceYards: Double,
        dialedAmount: Double,
        measuredShiftInches: Double,
        angularUnit: AngularUnit
    ): TallTargetResult? {
        if (distanceYards < 10.0 || distanceYards > 1000.0) return null
        if (dialedAmount <= 0.1 || measuredShiftInches <= 0.1) return null

        val expectedInches = when (angularUnit) {
            AngularUnit.MRAD -> dialedAmount * (distanceYards * 36.0 / 1000.0)
            else -> dialedAmount * (distanceYards / 100.0) * 1.0471975512
        }
        val rawSsf = expectedInches / measuredShiftInches
        val roundedSsf = (rawSsf * 1000.0).roundToInt() / 1000.0
        val trackingErrPct = ((measuredShiftInches / expectedInches) - 1.0) * 100.0
        val isValid = roundedSsf in 0.800..1.200

        return TallTargetResult(
            distanceYards = distanceYards,
            dialedAmount = dialedAmount,
            angularUnit = angularUnit,
            expectedShiftInches = expectedInches,
            measuredShiftInches = measuredShiftInches,
            scaleFactor = roundedSsf,
            trackingErrorPercent = trackingErrPct,
            isWithinValidRange = isValid
        )
    }

    /**
     * Estimates first-round hit probability against a circular target plate of diameter
     * [uncertainty.targetDiameterInches] using first-order Gaussian error propagation around
     * the nominal 3-DOF+ trajectory and exact polar integration of the 2D elliptical Gaussian
     * over the circular plate.
     *
     * Assumes centered zero (no mean bias) and statistically independent 1-sigma error sources:
     * MV SD, crosswind call uncertainty, range uncertainty, BC/drag uncertainty, and rifle/shooter 1σ.
     */
    fun computeHitProbability(
        profile: RifleProfile,
        atmosphere: Atmosphere,
        rangeYards: Double,
        uncertainty: ShotUncertaintyInput,
        inclinationDeg: Double = 0.0,
        options: SolverOptions = SolverOptions()
    ): HitProbabilityResult {
        val safeRange = rangeYards.coerceAtLeast(10.0)
        val plateDia = uncertainty.targetDiameterInches.coerceIn(0.5, 120.0)
        val plateRadius = plateDia * 0.5

        val baseZero = computeZeroAngleRad(profile)
        val nominal = solve(
            profile = profile,
            atmosphere = atmosphere,
            rangeYards = safeRange,
            inclinationDeg = inclinationDeg,
            cachedZeroAngleRad = baseZero,
            options = options
        )

        // 1. Muzzle Velocity 1-sigma (central difference)
        val mvSd = uncertainty.mvSdFps.coerceIn(0.0, 250.0)
        var dDropMv = 0.0
        var dDriftMv = 0.0
        if (mvSd > 0.05) {
            val pPlus = profile.copy(muzzleVelocityFps = (profile.muzzleVelocityFps + mvSd).coerceAtMost(5500.0))
            val pMinus = profile.copy(muzzleVelocityFps = (profile.muzzleVelocityFps - mvSd).coerceAtLeast(200.0))
            val sPlus = solve(pPlus, atmosphere, safeRange, inclinationDeg, options = options)
            val sMinus = solve(pMinus, atmosphere, safeRange, inclinationDeg, options = options)
            dDropMv = 0.5 * abs(sPlus.dropInches - sMinus.dropInches)
            dDriftMv = 0.5 * abs(sPlus.windDriftInches - sMinus.windDriftInches)
        }

        // 2. Range Estimation 1-sigma (vertical miss relative to dialed LOS at nominal range)
        val rngErr = uncertainty.rangeErrorYards.coerceIn(0.0, safeRange * 0.25)
        var dDropRange = 0.0
        if (rngErr > 0.05) {
            val rPlus = safeRange + rngErr
            val rMinus = (safeRange - rngErr).coerceAtLeast(5.0)
            val sPlus = solve(profile, atmosphere, rPlus, inclinationDeg, cachedZeroAngleRad = baseZero, options = options)
            val sMinus = solve(profile, atmosphere, rMinus, inclinationDeg, cachedZeroAngleRad = baseZero, options = options)
            val missPlus = sPlus.dropInches - nominal.dropInches * (rPlus / safeRange)
            val missMinus = sMinus.dropInches - nominal.dropInches * (rMinus / safeRange)
            dDropRange = 0.5 * abs(missPlus - missMinus)
        }

        // 3. BC / Drag 1-sigma (scales BC or inverse custom Cd by 1 ± pct)
        val bcPct = (uncertainty.bcErrorPercent.coerceIn(0.0, 25.0)) / 100.0
        var dDropBc = 0.0
        var dDriftBc = 0.0
        if (bcPct > 1e-4) {
            fun scaleDrag(mult: Double): RifleProfile {
                return if (profile.usesCustomDragCurve()) {
                    val c = profile.customDragCurve!!
                    val inv = 1.0 / mult
                    profile.copy(
                        customDragCurve = c.copy(
                            points = c.points.map { it.copy(cd = (it.cd * inv).coerceIn(0.01, 3.0)) }
                        )
                    )
                } else {
                    profile.copy(
                        ballisticCoefficient = (profile.ballisticCoefficient * mult).coerceIn(0.02, 2.0),
                        bcBands = profile.bcBands.map { it.copy(bc = (it.bc * mult).coerceIn(0.02, 2.0)) }
                    )
                }
            }
            val sPlus = solve(scaleDrag(1.0 + bcPct), atmosphere, safeRange, inclinationDeg, options = options)
            val sMinus = solve(scaleDrag(1.0 - bcPct), atmosphere, safeRange, inclinationDeg, options = options)
            dDropBc = 0.5 * abs(sPlus.dropInches - sMinus.dropInches)
            dDriftBc = 0.5 * abs(sPlus.windDriftInches - sMinus.windDriftInches)
        }

        // 4. Crosswind Call 1-sigma (adds ±windErrorMph pure 90° crosswind perturbation)
        val windErr = uncertainty.windErrorMph.coerceIn(0.0, 25.0)
        var dDriftWind = 0.0
        var dDropWind = 0.0
        if (windErr > 0.05) {
            fun addCrosswind(baseAtmo: Atmosphere, deltaCrossMph: Double): Atmosphere {
                fun shiftZone(speed: Double, dirDeg: Double): Pair<Double, Double> {
                    val rad = Math.toRadians(dirDeg)
                    val head = speed * cos(rad)
                    val cross = speed * sin(rad) + deltaCrossMph
                    val newSpd = sqrt(head * head + cross * cross)
                    val newDir = ((Math.toDegrees(atan2(cross, head)) % 360.0) + 360.0) % 360.0
                    return newSpd to newDir
                }
                val wp = baseAtmo.windProfile
                return if (wp != null && wp.mode == WindMode.THREE_ZONE) {
                    val (ns, nd) = shiftZone(wp.near.speedMph, wp.near.directionDeg)
                    val (ms, md) = shiftZone(wp.mid.speedMph, wp.mid.directionDeg)
                    val (fs, fd) = shiftZone(wp.far.speedMph, wp.far.directionDeg)
                    val updatedWp = wp.copy(
                        near = wp.near.copy(speedMph = ns, directionDeg = nd),
                        mid = wp.mid.copy(speedMph = ms, directionDeg = md),
                        far = wp.far.copy(speedMph = fs, directionDeg = fd)
                    )
                    val avg = updatedWp.rangeWeightedAverage()
                    baseAtmo.copy(
                        windSpeedMph = avg.speedMph,
                        windDirectionDeg = avg.directionDeg,
                        windProfile = updatedWp,
                        windGustMph = 0.0
                    )
                } else {
                    val (ns, nd) = shiftZone(baseAtmo.windSpeedMph, baseAtmo.windDirectionDeg)
                    baseAtmo.copy(windSpeedMph = ns, windDirectionDeg = nd, windGustMph = 0.0)
                }
            }
            val sPlus = solve(profile, addCrosswind(atmosphere, windErr), safeRange, inclinationDeg, cachedZeroAngleRad = baseZero, options = options)
            val sMinus = solve(profile, addCrosswind(atmosphere, -windErr), safeRange, inclinationDeg, cachedZeroAngleRad = baseZero, options = options)
            dDriftWind = 0.5 * abs(sPlus.windDriftInches - sMinus.windDriftInches)
            dDropWind = 0.5 * abs(sPlus.dropInches - sMinus.dropInches)
        }

        // 5. Mechanical Rifle + Shooter 1-sigma dispersion (per axis, inches at target range)
        val sysMoa = uncertainty.systemPrecisionMoa.coerceIn(0.0, 10.0)
        val sigmaSysIn = sysMoa * (safeRange / 100.0) * 1.0471975512

        val sigmaV = sqrt(
            dDropMv * dDropMv +
                dDropRange * dDropRange +
                dDropBc * dDropBc +
                dDropWind * dDropWind +
                sigmaSysIn * sigmaSysIn
        )
        val sigmaH = sqrt(
            dDriftWind * dDriftWind +
                dDriftMv * dDriftMv +
                dDriftBc * dDriftBc +
                sigmaSysIn * sigmaSysIn
        )

        // Exact 2D elliptical Gaussian integral over circular plate of radius R via 32-pt midpoint quadrature on [0, pi/2]
        val hitProb = if (sigmaV < 1e-4 && sigmaH < 1e-4) {
            1.0
        } else {
            val sv = sigmaV.coerceAtLeast(1e-4)
            val sh = sigmaH.coerceAtLeast(1e-4)
            val invSv2 = 1.0 / (sv * sv)
            val invSh2 = 1.0 / (sh * sh)
            val r2Half = 0.5 * plateRadius * plateRadius
            val steps = 32
            val dTheta = (PI * 0.5) / steps
            var integral = 0.0
            for (k in 0 until steps) {
                val theta = (k + 0.5) * dTheta
                val c = cos(theta)
                val s = sin(theta)
                val a = c * c * invSh2 + s * s * invSv2
                integral += (1.0 - exp(-r2Half * a)) / a
            }
            ((2.0 / (PI * sh * sv)) * integral * dTheta).coerceIn(0.0, 1.0)
        }

        val components = listOf(
            "Wind" to sqrt(dDriftWind * dDriftWind + dDropWind * dDropWind),
            "Range" to dDropRange,
            "MV SD" to sqrt(dDropMv * dDropMv + dDriftMv * dDriftMv),
            "BC" to sqrt(dDropBc * dDropBc + dDriftBc * dDriftBc),
            "Rifle 1σ" to sigmaSysIn
        )
        val dominant = components.maxByOrNull { it.second }?.first ?: "Rifle 1σ"

        return HitProbabilityResult(
            hitProbabilityPercent = hitProb * 100.0,
            targetDiameterInches = plateDia,
            rangeYards = safeRange,
            sigmaVerticalInches = sigmaV,
            sigmaHorizontalInches = sigmaH,
            sigmaMvInches = sqrt(dDropMv * dDropMv + dDriftMv * dDriftMv),
            sigmaRangeInches = dDropRange,
            sigmaBcInches = sqrt(dDropBc * dDropBc + dDriftBc * dDriftBc),
            sigmaWindInches = sqrt(dDriftWind * dDriftWind + dDropWind * dDropWind),
            sigmaSystemInches = sigmaSysIn,
            dominantErrorSource = dominant
        )
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
        val baseBc = profile.primaryBc().coerceAtLeast(0.01)
        val sightHeightFt = profile.sightHeightInches / 12.0
        val launchedSupersonic = (mv / air.speedOfSoundFps) >= TRANSONIC_MACH_GATE

        // Pre-pack stepped Multi-BC, DSF, and optional Custom Cd(Mach) tables BEFORE the RK4 loop (0 allocations per step)
        val bandVels = DoubleArray(8)
        val bandBcs = DoubleArray(8)
        val bandCount = packBcBands(profile.bcBands, bandVels, bandBcs)

        val dsfMachs = DoubleArray(8)
        val dsfVals = DoubleArray(8)
        val dsfCount = packDsfTable(profile.dsfTable, dsfMachs, dsfVals)

        val customMachs = DoubleArray(128)
        val customCds = DoubleArray(128)
        val customCount = if (profile.usesCustomDragCurve()) {
            packCustomDragCurve(profile.customDragCurve, customMachs, customCds)
        } else 0
        val sectionalDensity = computeSectionalDensity(profile)

        // Precompute 3-Zone OR Single-Zone horizontal wind vectors decomposed into LOS-tilted frame
        val wp = atmosphere.windProfile
        val useThreeZone = wp != null && wp.mode == WindMode.THREE_ZONE

        val nearSpdMph = if (useThreeZone) wp!!.near.speedMph else atmosphere.windSpeedMph
        val nearDirDeg = if (useThreeZone) wp!!.near.directionDeg else atmosphere.windDirectionDeg
        val midSpdMph = if (useThreeZone) wp!!.mid.speedMph else nearSpdMph
        val midDirDeg = if (useThreeZone) wp!!.mid.directionDeg else nearDirDeg
        val farSpdMph = if (useThreeZone) wp!!.far.speedMph else nearSpdMph
        val farDirDeg = if (useThreeZone) wp!!.far.directionDeg else nearDirDeg

        val nearFps = nearSpdMph * 5280.0 / 3600.0
        val nearRad = Math.toRadians(nearDirDeg)
        val nearLong = -nearFps * cos(nearRad)
        val nearWx = nearLong * cosAngle
        val nearWy = -nearLong * sinAngle
        val nearWz = -nearFps * sin(nearRad)

        val midFps = midSpdMph * 5280.0 / 3600.0
        val midRad = Math.toRadians(midDirDeg)
        val midLong = -midFps * cos(midRad)
        val midWx = midLong * cosAngle
        val midWy = -midLong * sinAngle
        val midWz = -midFps * sin(midRad)

        val farFps = farSpdMph * 5280.0 / 3600.0
        val farRad = Math.toRadians(farDirDeg)
        val farLong = -farFps * cos(farRad)
        val farWx = farLong * cosAngle
        val farWy = -farLong * sinAngle
        val farWz = -farFps * sin(farRad)

        val effWindForPct = atmosphere.effectiveSingleWind()
        val windValuePct = (abs(sin(Math.toRadians(effWindForPct.directionDeg))) * 100.0).roundToInt()

        val sg = computeStabilityFactor(profile, air)
        val latRad = Math.toRadians((atmosphere.gpsLatitude ?: 35.0).coerceIn(-90.0, 90.0))
        val azRad = Math.toRadians(atmosphere.shootingBearingDeg)
        val coriolisActive = options.includeCoriolis && profile.weaponMode == WeaponMode.RIFLE && atmosphere.gpsLatitude != null

        // Aero jump occurs at the muzzle, so use Near-zone crosswind
        val crosswindMph = nearSpdMph * sin(nearRad)
        val aeroJumpMoa = if (options.includeAeroJump &&
            profile.weaponMode == WeaponMode.RIFLE &&
            profile.twistDirection != TwistDirection.NONE
        ) {
            val twistSign = if (profile.twistDirection == TwistDirection.RIGHT) 1.0 else -1.0
            0.012 * crosswindMph * twistSign * (1.5 / sg.coerceAtLeast(1.0))
        } else 0.0

        val gx = -GRAVITY_FPS2 * sinAngle
        val gy = -GRAVITY_FPS2 * cosAngle
        val dragAirConst = ICAO_DRAG_CONST * air.densityRatio

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

        val maxTargetFt = (targetRangesYards.lastOrNull() ?: 0.0) * 3.0
        val invTotalRangeFt = 1.0 / maxTargetFt.coerceAtLeast(1.0)
        val speedOfSound = air.speedOfSoundFps
        val dragModel = profile.dragModel

        fun evalAccel(cx: Double, cvx: Double, cvy: Double, cvz: Double, out: DoubleArray) {
            val wx: Double
            val wy: Double
            val wz: Double
            if (useThreeZone) {
                val progress = cx * invTotalRangeFt
                if (progress < 0.3333333333333333) {
                    wx = nearWx; wy = nearWy; wz = nearWz
                } else if (progress < 0.6666666666666666) {
                    wx = midWx; wy = midWy; wz = midWz
                } else {
                    wx = farWx; wy = farWy; wz = farWz
                }
            } else {
                wx = nearWx; wy = nearWy; wz = nearWz
            }

            val rvx = cvx - wx
            val rvy = cvy - wy
            val rvz = cvz - wz
            val vRel = sqrt(rvx * rvx + rvy * rvy + rvz * rvz)
            if (vRel < 1e-6) {
                out[0] = gx
                out[1] = gy
                out[2] = 0.0
                return
            }
            val mach = vRel / speedOfSound
            val cd: Double
            val divisor: Double
            if (customCount >= 2) {
                cd = evalCustomCd(mach, customMachs, customCds, customCount)
                divisor = sectionalDensity
            } else {
                cd = DragTables.getCd(dragModel, mach)
                divisor = if (bandCount == 0) baseBc else lookupBc(vRel, baseBc, bandVels, bandBcs, bandCount)
            }
            val k = (dragAirConst / divisor) * cd * vRel
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
            val speedFps = sqrt(svx * svx + svy * svy + svz * svz)
            val mach = speedFps / speedOfSound
            val appliedDsf = if (dsfCount == 0) 1.0 else evalDsf(mach, dsfMachs, dsfVals, dsfCount)

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

            val scaledSy = sy * appliedDsf
            val dropInches = scaledSy * 12.0
            val elevRad = atan2(-scaledSy, effectiveFt)
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

            val energyFtLbs = (profile.bulletWeightGrains * speedFps * speedFps) / (2.0 * GRAVITY_FPS2 * GRAINS_PER_LB)

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
                appliedDsf = appliedDsf,
                densityAltitudeFt = air.densityAltitudeFt,
                reachedTarget = reached,
                isTransonicWarning = launchedSupersonic && mach < TRANSONIC_MACH_GATE,
                exceedsOnboard800YdLimit = reqRangeYd > LBC1800_ONBOARD_LIMIT_YARDS
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

            evalAccel(x, vx, vy, vz, a1)

            val x2 = x + 0.5 * dt * vx
            val vx2 = vx + 0.5 * dt * a1[0]
            val vy2 = vy + 0.5 * dt * a1[1]
            val vz2 = vz + 0.5 * dt * a1[2]
            evalAccel(x2, vx2, vy2, vz2, a2)

            val x3 = x + 0.5 * dt * vx2
            val vx3 = vx + 0.5 * dt * a2[0]
            val vy3 = vy + 0.5 * dt * a2[1]
            val vz3 = vz + 0.5 * dt * a2[2]
            evalAccel(x3, vx3, vy3, vz3, a3)

            val x4 = x + dt * vx3
            val vx4 = vx + dt * a3[0]
            val vy4 = vy + dt * a3[1]
            val vz4 = vz + dt * a3[2]
            evalAccel(x4, vx4, vy4, vz4, a4)

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
        baseBc: Double,
        bandVels: DoubleArray,
        bandBcs: DoubleArray,
        bandCount: Int,
        customMachs: DoubleArray,
        customCds: DoubleArray,
        customCount: Int,
        sectionalDensity: Double,
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
        val dragAirConst = ICAO_DRAG_CONST * air.densityRatio
        val speedOfSound = air.speedOfSoundFps

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
            val mach = vRel / speedOfSound
            val cd: Double
            val divisor: Double
            if (customCount >= 2) {
                cd = evalCustomCd(mach, customMachs, customCds, customCount)
                divisor = sectionalDensity
            } else {
                cd = DragTables.getCd(dragModel, mach)
                divisor = if (bandCount == 0) baseBc else lookupBc(vRel, baseBc, bandVels, bandBcs, bandCount)
            }
            val k = (dragAirConst / divisor) * cd * vRel
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
