package com.bc1800.ballistics.data

import com.bc1800.ballistics.model.AngularUnit
import com.bc1800.ballistics.model.Atmosphere
import com.bc1800.ballistics.model.BallisticSolution
import com.bc1800.ballistics.model.DistanceUnit
import com.bc1800.ballistics.model.PressureUnit
import com.bc1800.ballistics.model.TemperatureUnit
import com.bc1800.ballistics.model.UserPreferences
import com.bc1800.ballistics.model.VelocityUnit
import com.bc1800.ballistics.model.WindUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object UnitFormatters {

    const val YARDS_TO_METERS = 0.9144
    const val METERS_TO_YARDS = 1.0936133
    const val FPS_TO_MPS = 0.3048
    const val INHG_TO_HPA = 33.8638867
    const val MPH_TO_MPS = 0.44704
    const val MPH_TO_KPH = 1.609344
    const val FTLBS_TO_JOULES = 1.355818

    fun formatDistance(yards: Double, unit: DistanceUnit, decimals: Int = 0): String {
        val value = distanceValue(yards, unit)
        return String.format(Locale.US, "%.${decimals}f %s", value, unit.shortLabel)
    }

    fun distanceValue(yards: Double, unit: DistanceUnit): Double {
        return if (unit == DistanceUnit.METERS) yards * YARDS_TO_METERS else yards
    }

    fun toYards(value: Double, unit: DistanceUnit): Double {
        return if (unit == DistanceUnit.METERS) value * METERS_TO_YARDS else value
    }

    fun elevationValue(solution: BallisticSolution, unit: AngularUnit): Double = when (unit) {
        AngularUnit.MOA -> solution.elevationMoa
        AngularUnit.MRAD -> solution.elevationMrad
        AngularUnit.INCHES -> -solution.dropInches
        AngularUnit.CENTIMETERS -> -solution.dropInches * 2.54
    }

    fun windageValue(solution: BallisticSolution, unit: AngularUnit): Double = when (unit) {
        AngularUnit.MOA -> solution.windageMoa
        AngularUnit.MRAD -> solution.windageMrad
        AngularUnit.INCHES -> -solution.windDriftInches
        AngularUnit.CENTIMETERS -> -solution.windDriftInches * 2.54
    }

    fun formatElevationHold(solution: BallisticSolution, unit: AngularUnit): Pair<String, String> {
        val raw = elevationValue(solution, unit)
        val decimals = if (unit == AngularUnit.MOA || unit == AngularUnit.MRAD) 2 else 1
        val zeroThreshold = if (decimals == 2) 0.005 else 0.05
        if (abs(raw) < zeroThreshold) {
            return String.format(Locale.US, "%.${decimals}f", 0.0) to "ZERO"
        }
        val dir = if (raw > 0) "UP" else "DOWN"
        return String.format(Locale.US, "%.${decimals}f", abs(raw)) to dir
    }

    fun formatWindageHold(solution: BallisticSolution, unit: AngularUnit): Pair<String, String> {
        val raw = windageValue(solution, unit)
        val decimals = if (unit == AngularUnit.MOA || unit == AngularUnit.MRAD) 2 else 1
        val zeroThreshold = if (decimals == 2) 0.005 else 0.05
        if (abs(raw) < zeroThreshold) {
            return String.format(Locale.US, "%.${decimals}f", 0.0) to "ZERO"
        }
        val dir = if (raw > 0) "RIGHT" else "LEFT"
        return String.format(Locale.US, "%.${decimals}f", abs(raw)) to dir
    }

    fun formatCompactHold(raw: Double, unit: AngularUnit, posTag: String, negTag: String): String {
        val decimals = when (unit) {
            AngularUnit.MOA, AngularUnit.MRAD -> 2
            AngularUnit.INCHES, AngularUnit.CENTIMETERS -> 1
        }
        val threshold = if (decimals == 2) 0.005 else 0.05
        if (abs(raw) < threshold) return String.format(Locale.US, "%.${decimals}f", 0.0)
        val tag = if (raw > 0) posTag else negTag
        return String.format(Locale.US, "%s%.${decimals}f", tag, abs(raw))
    }

    fun formatVelocity(fps: Double, unit: VelocityUnit): String {
        val value = if (unit == VelocityUnit.MPS) fps * FPS_TO_MPS else fps
        return String.format(Locale.US, "%.0f %s", value, unit.shortLabel)
    }

    fun formatEnergy(ftLbs: Double, velocityUnit: VelocityUnit): String {
        return if (velocityUnit == VelocityUnit.MPS) {
            String.format(Locale.US, "%.0f J", ftLbs * FTLBS_TO_JOULES)
        } else {
            String.format(Locale.US, "%.0f ft-lb", ftLbs)
        }
    }

    fun formatDrop(dropInches: Double, distanceUnit: DistanceUnit): String {
        return if (distanceUnit == DistanceUnit.METERS) {
            String.format(Locale.US, "%.1f cm", dropInches * 2.54)
        } else {
            String.format(Locale.US, "%.1f in", dropInches)
        }
    }

    fun formatTemp(tempF: Double, unit: TemperatureUnit): String {
        val value = if (unit == TemperatureUnit.CELSIUS) (tempF - 32.0) * 5.0 / 9.0 else tempF
        return String.format(Locale.US, "%.0f%s", value, unit.shortLabel)
    }

    fun formatPressure(inHg: Double, unit: PressureUnit): String {
        return if (unit == PressureUnit.HPA) {
            String.format(Locale.US, "%.0f %s", inHg * INHG_TO_HPA, unit.shortLabel)
        } else {
            String.format(Locale.US, "%.2f %s", inHg, unit.shortLabel)
        }
    }

    fun windValue(mph: Double, unit: WindUnit): Double = when (unit) {
        WindUnit.MPH -> mph
        WindUnit.MPS -> mph * MPH_TO_MPS
        WindUnit.KPH -> mph * MPH_TO_KPH
    }

    fun windToMph(value: Double, unit: WindUnit): Double = when (unit) {
        WindUnit.MPH -> value
        WindUnit.MPS -> value / MPH_TO_MPS
        WindUnit.KPH -> value / MPH_TO_KPH
    }

    fun formatWind(mph: Double, unit: WindUnit): String {
        return String.format(Locale.US, "%.1f %s", windValue(mph, unit), unit.shortLabel)
    }

    fun windClockHour(deg: Double): Int {
        val normalized = ((deg % 360.0) + 360.0) % 360.0
        val hour = (normalized / 30.0).roundToInt() % 12
        return if (hour == 0) 12 else hour
    }

    fun formatRangeCardText(
        rigTitle: String,
        ammoTitle: String,
        atmosphere: Atmosphere,
        solutions: List<BallisticSolution>,
        prefs: UserPreferences
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Gunnies Ballistics DOPE Card")
        sb.appendLine("Rig: $rigTitle")
        sb.appendLine("Ammo: $ammoTitle")
        sb.appendLine(
            "Cond: ${formatTemp(atmosphere.temperatureF, prefs.temperatureUnit)} | " +
                "${formatPressure(atmosphere.pressureInHg, prefs.pressureUnit)} | " +
                "${atmosphere.humidityPercent.roundToInt()}% RH | " +
                "Wind ${formatWind(atmosphere.windSpeedMph, prefs.windUnit)} @ ${windClockHour(atmosphere.windDirectionDeg)}:00"
        )
        sb.appendLine("--------------------------------------------------------------------")
        sb.appendLine(
            String.format(
                Locale.US,
                "%-7s %-8s %-6s %-8s %-9s %-8s %-6s",
                "Range",
                "Elev",
                "Clks",
                "Wind",
                "Drop",
                "Vel",
                "TOF"
            )
        )
        for (s in solutions) {
            val dist = String.format(Locale.US, "%.0f", distanceValue(s.rangeYards, prefs.distanceUnit))
            val elevVal = elevationValue(s, prefs.angularUnit)
            val windVal = windageValue(s, prefs.angularUnit)
            val dropStr = if (prefs.distanceUnit == DistanceUnit.METERS) {
                String.format(Locale.US, "%.1fcm", s.dropInches * 2.54)
            } else {
                String.format(Locale.US, "%.1fin", s.dropInches)
            }
            val vel = String.format(
                Locale.US,
                "%.0f",
                if (prefs.velocityUnit == VelocityUnit.MPS) s.velocityFps * FPS_TO_MPS else s.velocityFps
            )
            sb.appendLine(
                String.format(
                    Locale.US,
                    "%-7s %-8s %-6d %-8s %-9s %-8s %.2fs",
                    "$dist${prefs.distanceUnit.shortLabel}",
                    formatCompactHold(elevVal, prefs.angularUnit, "U", "D"),
                    abs(s.elevationClicks).roundToInt(),
                    formatCompactHold(windVal, prefs.angularUnit, "R", "L"),
                    dropStr,
                    vel,
                    s.timeOfFlightSec
                )
            )
        }
        return sb.toString()
    }
}
