package com.bc1800.ballistics.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class DragModel(val label: String) {
    G1("G1"),
    G7("G7")
}

@Serializable
enum class WeaponMode(val label: String) {
    RIFLE("Rifle"),
    BOW("Bow / Crossbow")
}

@Serializable
enum class FirearmCategory(val label: String) {
    RIFLE("Rifle"),
    HANDGUN("Handgun"),
    BOW_OTHER("Bow / Other")
}

@Serializable
enum class ScopeCategory(val label: String) {
    RIFLE_SCOPE("Rifle Scope"),
    RED_DOT_IRON("Red Dot / Iron"),
    HANDGUN_OPTIC("Handgun Optic")
}

@Serializable
enum class ReticleType(
    val label: String,
    val isMradBased: Boolean,
    val minZoom: Float,
    val maxZoom: Float,
    val isFfp: Boolean
) {
    ARKEN_VPR_MIL("Arken VPR MIL", true, 4f, 16f, true),
    PA_ARC2_MOA("Primary Arms ARC-2 MOA", false, 4f, 14f, true),
    PA_ACSS_RGRID("Primary Arms ACSS / R-Grid", true, 4f, 14f, true),
    RED_DOT_CIRCLE_65("Red Dot (2/65 MOA)", false, 1f, 4f, false),
    HANDGUN_THREE_DOT("Handgun 3-Dot Irons", false, 1f, 2f, false),
    SIMPLE_DUPLEX("Classic Duplex", false, 3f, 9f, false)
}

@Serializable
enum class AmmoCategory(val label: String) {
    RIMFIRE("Rimfire (.22)"),
    RIFLE("Rifle"),
    HANDGUN("Handgun")
}

@Serializable
enum class TwistDirection(val label: String) {
    RIGHT("Right"),
    LEFT("Left"),
    NONE("None")
}

@Serializable
enum class ClickUnit(val label: String, val clicksPerMoa: Double?, val clicksPerMrad: Double?) {
    QUARTER_MOA("1/4 MOA", 4.0, null),
    EIGHTH_MOA("1/8 MOA", 8.0, null),
    HALF_MOA("1/2 MOA", 2.0, null),
    TENTH_MRAD("0.1 MRAD", null, 10.0),
    TWENTIETH_MRAD("0.05 MRAD", null, 20.0);

    val isMrad: Boolean get() = clicksPerMrad != null

    fun toClicks(moa: Double, mrad: Double): Double {
        return when {
            clicksPerMoa != null -> moa * clicksPerMoa
            clicksPerMrad != null -> mrad * clicksPerMrad
            else -> 0.0
        }
    }
}

/**
 * Independent Firearm setup (Rifle or Handgun: barrel length, twist, caliber class, favorite flag).
 */
@Serializable
data class FirearmSetup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: FirearmCategory = FirearmCategory.RIFLE,
    val caliberHint: String = ".22 LR",
    val barrelLengthInches: Double = 18.0,
    val barrelTwistInches: Double = 16.0,
    val twistDirection: TwistDirection = TwistDirection.RIGHT,
    val isFavorite: Boolean = false,
    val notes: String = ""
)

/**
 * Independent Optic / Scope setup (turret clicks, reticle, sight height, zero range,
 * zero POI offset, tall-target sight scale factor, favorite flag).
 */
@Serializable
data class ScopeSetup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val scopeModel: String = "",
    val category: ScopeCategory = ScopeCategory.RIFLE_SCOPE,
    val reticleType: ReticleType = ReticleType.ARKEN_VPR_MIL,
    val weaponMode: WeaponMode = WeaponMode.RIFLE,
    val sightHeightInches: Double = 1.85,
    val zeroRangeYards: Double = 100.0,
    val zeroOffsetVerticalInches: Double = 0.0,
    val zeroOffsetHorizontalInches: Double = 0.0,
    val elevationSsf: Double = 1.0,
    val windageSsf: Double = 1.0,
    val clickUnit: ClickUnit = ClickUnit.QUARTER_MOA,
    val zeroTempF: Double = 59.0,
    val zeroPressureInHg: Double = 29.92,
    val zeroHumidityPercent: Double = 50.0,
    val isFavorite: Boolean = false,
    val notes: String = ""
)

/**
 * Independent Ammunition / Bullet load (category, caliber, drag model, BC, MV, weight,
 * diameter, optional explicit bullet length, powder temperature sensitivity, favorite flag).
 */
@Serializable
data class AmmoLoad(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val caliber: String,
    val category: AmmoCategory = AmmoCategory.RIFLE,
    val dragModel: DragModel = DragModel.G1,
    val ballisticCoefficient: Double = 0.250,
    val muzzleVelocityFps: Double = 2700.0,
    val bulletWeightGrains: Double = 55.0,
    val bulletDiameterInches: Double = 0.224,
    val bulletLengthInches: Double? = null,
    val mvTempSensitivityFpsPerF: Double = 0.0,
    val chronoTempF: Double = 60.0,
    val isFavorite: Boolean = false,
    val notes: String = ""
)

/**
 * Combined active ballistic setup fed into BallisticEngine & Lbc1800Protocol,
 * assembled dynamically from active Firearm + active Scope + active Ammo.
 */
@Serializable
data class RifleProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val caliber: String,
    val firearmName: String = "",
    val scopeName: String = "",
    val weaponMode: WeaponMode = WeaponMode.RIFLE,
    val dragModel: DragModel = DragModel.G1,
    val ballisticCoefficient: Double = 0.462,
    val muzzleVelocityFps: Double = 2650.0,
    val bulletWeightGrains: Double = 168.0,
    val bulletDiameterInches: Double = 0.308,
    val bulletLengthInches: Double? = null,
    val mvTempSensitivityFpsPerF: Double = 0.0,
    val chronoTempF: Double = 60.0,
    val sightHeightInches: Double = 1.75,
    val zeroRangeYards: Double = 100.0,
    val zeroOffsetVerticalInches: Double = 0.0,
    val zeroOffsetHorizontalInches: Double = 0.0,
    val elevationSsf: Double = 1.0,
    val windageSsf: Double = 1.0,
    val clickUnit: ClickUnit = ClickUnit.QUARTER_MOA,
    val barrelTwistInches: Double = 10.0,
    val twistDirection: TwistDirection = TwistDirection.RIGHT,
    val zeroTempF: Double = 59.0,
    val zeroPressureInHg: Double = 29.92,
    val zeroHumidityPercent: Double = 50.0,
    val notes: String = ""
) {
    companion object {
        fun combine(firearm: FirearmSetup, scope: ScopeSetup, ammo: AmmoLoad): RifleProfile = RifleProfile(
            id = "${firearm.id}__${scope.id}__${ammo.id}",
            name = ammo.name,
            caliber = ammo.caliber,
            firearmName = firearm.name,
            scopeName = scope.name,
            weaponMode = if (firearm.category == FirearmCategory.BOW_OTHER) WeaponMode.BOW else scope.weaponMode,
            dragModel = ammo.dragModel,
            ballisticCoefficient = ammo.ballisticCoefficient,
            muzzleVelocityFps = ammo.muzzleVelocityFps,
            bulletWeightGrains = ammo.bulletWeightGrains,
            bulletDiameterInches = ammo.bulletDiameterInches,
            bulletLengthInches = ammo.bulletLengthInches,
            mvTempSensitivityFpsPerF = ammo.mvTempSensitivityFpsPerF,
            chronoTempF = ammo.chronoTempF,
            sightHeightInches = scope.sightHeightInches,
            zeroRangeYards = scope.zeroRangeYards,
            zeroOffsetVerticalInches = scope.zeroOffsetVerticalInches,
            zeroOffsetHorizontalInches = scope.zeroOffsetHorizontalInches,
            elevationSsf = scope.elevationSsf,
            windageSsf = scope.windageSsf,
            clickUnit = scope.clickUnit,
            barrelTwistInches = firearm.barrelTwistInches,
            twistDirection = firearm.twistDirection,
            zeroTempF = scope.zeroTempF,
            zeroPressureInHg = scope.zeroPressureInHg,
            zeroHumidityPercent = scope.zeroHumidityPercent,
            notes = listOf(firearm.name, scope.name, ammo.notes).filter { it.isNotBlank() }.joinToString(" • ")
        )
    }
}

@Serializable
data class Atmosphere(
    val temperatureF: Double = 59.0,
    val pressureInHg: Double = 29.92,
    val pressureIsStation: Boolean = true,
    val altitudeFeet: Double = 0.0,
    val humidityPercent: Double = 50.0,
    val windSpeedMph: Double = 5.0,
    val windGustMph: Double = 0.0,
    /** Wind clock direction relative to shooter in degrees: 0 = 12 o'clock (headwind), 90 = 3 o'clock (from right), 180 = 6 o'clock (tailwind), 270 = 9 o'clock (from left). */
    val windDirectionDeg: Double = 90.0,
    /** True meteorological compass wind direction (where wind blows FROM, 0=N, 90=E, 180=S, 270=W), populated by GPS weather. */
    val meteoWindFromDeg: Double? = null,
    /** Shooter's target bearing in compass degrees (0=N, 90=E, 180=S, 270=W). */
    val shootingBearingDeg: Double = 0.0,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val weatherUpdatedAt: String? = null
)

@Serializable
enum class DistanceUnit(val shortLabel: String, val fullLabel: String) {
    YARDS("yd", "Yards"),
    METERS("m", "Meters")
}

@Serializable
enum class AngularUnit(val shortLabel: String, val fullLabel: String) {
    MOA("MOA", "MOA"),
    MRAD("MRAD", "MRAD"),
    INCHES("in", "Inches"),
    CENTIMETERS("cm", "Centimeters")
}

@Serializable
enum class VelocityUnit(val shortLabel: String) {
    FPS("fps"),
    MPS("m/s")
}

@Serializable
enum class TemperatureUnit(val shortLabel: String) {
    FAHRENHEIT("°F"),
    CELSIUS("°C")
}

@Serializable
enum class PressureUnit(val shortLabel: String) {
    INHG("inHg"),
    HPA("hPa")
}

@Serializable
enum class WindUnit(val shortLabel: String) {
    MPH("mph"),
    MPS("m/s"),
    KPH("km/h")
}

@Serializable
enum class AppThemeMode(val label: String, val subtitle: String) {
    FIELD_DARK("Field Dark", "High-contrast anodized matte graphite"),
    NIGHT_RED("Night Red", "True OLED red-spectrum preserves night vision"),
    DAYLIGHT("Daylight", "Crisp high-contrast titanium white for sun")
}

@Serializable
enum class UiTextScale(val label: String, val factor: Float) {
    COMPACT("Compact", 0.92f),
    STANDARD("Standard", 1.0f),
    LARGE("Large", 1.12f),
    EXTRA_LARGE("XL Field", 1.24f)
}

@Serializable
data class UserPreferences(
    val distanceUnit: DistanceUnit = DistanceUnit.YARDS,
    val angularUnit: AngularUnit = AngularUnit.MRAD,
    val autoMatchScopeUnit: Boolean = true,
    val velocityUnit: VelocityUnit = VelocityUnit.FPS,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    val pressureUnit: PressureUnit = PressureUnit.INHG,
    val windUnit: WindUnit = WindUnit.MPH,
    val themeMode: AppThemeMode = AppThemeMode.FIELD_DARK,
    val textScale: UiTextScale = UiTextScale.STANDARD,
    val keepScreenAwake: Boolean = true,
    val includeSpinDrift: Boolean = true,
    val includeCoriolis: Boolean = true,
    val includeAeroJump: Boolean = true,
    val rangeCardStepYards: Int = 25,
    val rangeCardMaxYards: Int = 300,
    val activeFirearmId: String = "gun-cz-457",
    val activeScopeId: String = "scope-arken-sh4-gen2",
    val activeAmmoId: String = "ammo-cci-quiet22-40",
    /** Remembers preferred (ScopeId, AmmoId) per FirearmId so switching guns never pairs a 9mm Glock with a 16x rifle scope. */
    val firearmScopeMemory: Map<String, String> = mapOf(
        "gun-cz-457" to "scope-arken-sh4-gen2",
        "gun-ruger-1022" to "scope-pa-4-14x44",
        "gun-anderson-ar15" to "scope-ar15-optic",
        "gun-glock-17-g4" to "scope-handgun-sights"
    ),
    val firearmAmmoMemory: Map<String, String> = mapOf(
        "gun-cz-457" to "ammo-cci-quiet22-40",
        "gun-ruger-1022" to "ammo-cci-blazer-22-40",
        "gun-anderson-ar15" to "ammo-wolf-gold-223-55",
        "gun-glock-17-g4" to "ammo-fed-syntech-9mm-124"
    ),
    val lastConnectedBleAddress: String? = null,
    val lastConnectedBleName: String? = null,
    val defaultAtmosphere: Atmosphere = Atmosphere()
)

data class BallisticSolution(
    val rangeYards: Double,
    val trueHorizontalYards: Double,
    val angleDegrees: Double,
    /** Positive = hold/dial UP (bullet hits low); Negative = hold/dial DOWN (bullet hits high). */
    val elevationMoa: Double,
    val elevationMrad: Double,
    val dropInches: Double,
    val elevationClicks: Double,
    /** Positive = hold/dial RIGHT (bullet drifted left); Negative = hold/dial LEFT (bullet drifted right). */
    val windageMoa: Double,
    val windageMrad: Double,
    val windDriftInches: Double,
    val windageClicks: Double,
    /** Wind hold if gusts hit (null if no gust above sustained wind). */
    val gustWindageMoa: Double? = null,
    val gustWindageMrad: Double? = null,
    val windValuePercent: Int = 100,
    val spinDriftInches: Double = 0.0,
    val aeroJumpMoa: Double = 0.0,
    val coriolisElevMoa: Double = 0.0,
    val coriolisWindMoa: Double = 0.0,
    val moverLeadMoa: Double = 0.0,
    val moverLeadMrad: Double = 0.0,
    val moverLeadInches: Double = 0.0,
    val velocityFps: Double,
    val energyFtLbs: Double,
    val timeOfFlightSec: Double,
    val mach: Double,
    val stabilityFactorSg: Double = 1.5,
    val densityAltitudeFt: Double,
    val reachedTarget: Boolean = true,
    /** True only when a supersonic-launched projectile drops below Mach 1.2 (transonic instability). */
    val isTransonicWarning: Boolean = false,
    val exceedsOnboard800YdLimit: Boolean = false
)

object DefaultProfiles {

    val starterFirearms: List<FirearmSetup> = listOf(
        FirearmSetup(
            id = "gun-cz-457",
            name = "CZ 457 (.22 LR)",
            category = FirearmCategory.RIFLE,
            caliberHint = ".22 LR",
            barrelLengthInches = 20.5,
            barrelTwistInches = 16.0,
            twistDirection = TwistDirection.RIGHT,
            isFavorite = true,
            notes = "Bolt-action rimfire rifle • 1:16\" RH twist"
        ),
        FirearmSetup(
            id = "gun-ruger-1022",
            name = "Ruger 10/22 Carbine (.22 LR)",
            category = FirearmCategory.RIFLE,
            caliberHint = ".22 LR",
            barrelLengthInches = 18.5,
            barrelTwistInches = 16.0,
            twistDirection = TwistDirection.RIGHT,
            isFavorite = true,
            notes = "Semi-auto rimfire carbine • Stainless barrel • 1:16\" RH twist"
        ),
        FirearmSetup(
            id = "gun-anderson-ar15",
            name = "Anderson AR-15 (.223 / 5.56)",
            category = FirearmCategory.RIFLE,
            caliberHint = ".223 Rem / 5.56 NATO",
            barrelLengthInches = 16.0,
            barrelTwistInches = 8.0,
            twistDirection = TwistDirection.RIGHT,
            isFavorite = true,
            notes = "Anderson Manufacturing AR-15 • 1:8\" RH twist (edit if 1:7\" or 1:9\")"
        ),
        FirearmSetup(
            id = "gun-glock-17-g4",
            name = "Glock 17 Gen 4 (9mm)",
            category = FirearmCategory.HANDGUN,
            caliberHint = "9mm Luger",
            barrelLengthInches = 4.49,
            barrelTwistInches = 9.84,
            twistDirection = TwistDirection.RIGHT,
            isFavorite = true,
            notes = "Glock G4 17 • 4.49\" polygonal barrel • 1:9.84\" RH twist"
        ),
        FirearmSetup(
            id = "gun-generic-rifle",
            name = "Generic Centerfire Rifle",
            category = FirearmCategory.RIFLE,
            caliberHint = "Any Rifle",
            barrelLengthInches = 20.0,
            barrelTwistInches = 10.0,
            twistDirection = TwistDirection.RIGHT,
            isFavorite = false,
            notes = "Generic rifle template — duplicate or edit for any new rifle"
        ),
        FirearmSetup(
            id = "gun-generic-handgun",
            name = "Generic Handgun / Pistol",
            category = FirearmCategory.HANDGUN,
            caliberHint = "Any Handgun",
            barrelLengthInches = 4.5,
            barrelTwistInches = 10.0,
            twistDirection = TwistDirection.RIGHT,
            isFavorite = false,
            notes = "Generic handgun template — duplicate or edit for any pistol"
        )
    )

    val starterScopes: List<ScopeSetup> = listOf(
        ScopeSetup(
            id = "scope-arken-sh4-gen2",
            name = "Arken SH-4 Gen II 4-16x50",
            scopeModel = "Arken SH-4 Gen II 4-16x50 FFP (0.1 MRAD)",
            category = ScopeCategory.RIFLE_SCOPE,
            reticleType = ReticleType.ARKEN_VPR_MIL,
            weaponMode = WeaponMode.RIFLE,
            sightHeightInches = 1.85,
            zeroRangeYards = 50.0,
            clickUnit = ClickUnit.TENTH_MRAD,
            isFavorite = true,
            notes = "WestHunter rings w/ level • 0.1 MRAD Zero Stop • VPR MIL Christmas Tree"
        ),
        ScopeSetup(
            id = "scope-pa-4-14x44",
            name = "Primary Arms 4-14x44 FFP",
            scopeModel = "Primary Arms 4-14x44mm FFP (1/4 MOA)",
            category = ScopeCategory.RIFLE_SCOPE,
            reticleType = ReticleType.PA_ARC2_MOA,
            weaponMode = WeaponMode.RIFLE,
            sightHeightInches = 2.50,
            zeroRangeYards = 50.0,
            clickUnit = ClickUnit.QUARTER_MOA,
            isFavorite = true,
            notes = "Cantilever mount • 1/4 MOA turrets • ARC-2 MOA (switchable to ACSS R-Grid)"
        ),
        ScopeSetup(
            id = "scope-ar15-optic",
            name = "AR-15 Optic / Red Dot (2.6\" SH)",
            scopeModel = "Standard AR-Height Scope or Red Dot",
            category = ScopeCategory.RED_DOT_IRON,
            reticleType = ReticleType.RED_DOT_CIRCLE_65,
            weaponMode = WeaponMode.RIFLE,
            sightHeightInches = 2.60,
            zeroRangeYards = 100.0,
            clickUnit = ClickUnit.HALF_MOA,
            isFavorite = true,
            notes = "Standard 2.60\" AR-15 bore-to-optic height • 2 MOA Dot / 65 MOA Ring"
        ),
        ScopeSetup(
            id = "scope-handgun-sights",
            name = "Handgun Iron Sights / Red Dot",
            scopeModel = "Glock Iron Sights / Slide Optic",
            category = ScopeCategory.HANDGUN_OPTIC,
            reticleType = ReticleType.HANDGUN_THREE_DOT,
            weaponMode = WeaponMode.RIFLE,
            sightHeightInches = 0.75,
            zeroRangeYards = 25.0,
            clickUnit = ClickUnit.QUARTER_MOA,
            isFavorite = true,
            notes = "Glock 17 3-Dot U-Notch iron sights (~0.65\") or slide red dot (~1.0\") • 25 yd zero"
        )
    )

    val starterAmmo: List<AmmoLoad> = listOf(
        AmmoLoad("ammo-cci-quiet22-40", "CCI Quiet-22 — 40gr LRN", ".22 LR", AmmoCategory.RIMFIRE, DragModel.G1, 0.120, 710.0, 40.0, 0.223, bulletLengthInches = 0.480, isFavorite = true, notes = "Subsonic 710 fps Lead Round Nose"),
        AmmoLoad("ammo-win-supersupp-45", "Winchester Super Suppressed — 45gr", ".22 LR", AmmoCategory.RIMFIRE, DragModel.G1, 0.130, 1090.0, 45.0, 0.223, bulletLengthInches = 0.515, isFavorite = true, notes = "45gr Black Copper-Plated RN Subsonic (1090 fps)"),
        AmmoLoad("ammo-std-vel-40", "Standard Velocity (Tiger-Cat) — 40gr LRN", ".22 LR", AmmoCategory.RIMFIRE, DragModel.G1, 0.120, 1070.0, 40.0, 0.223, bulletLengthInches = 0.480, isFavorite = true, notes = "Standard Velocity 40gr Lead Round Nose (1070 fps)"),
        AmmoLoad("ammo-cci-blazer-22-40", "CCI Blazer .22 LR — 40gr LRN", ".22 LR", AmmoCategory.RIMFIRE, DragModel.G1, 0.133, 1235.0, 40.0, 0.223, bulletLengthInches = 0.480, isFavorite = true, notes = "High Velocity 40gr LRN (1235 fps)"),
        AmmoLoad("ammo-cci-minimag-36", "CCI Mini-Mag HP — 36gr CPHP", ".22 LR", AmmoCategory.RIMFIRE, DragModel.G1, 0.125, 1260.0, 36.0, 0.223, bulletLengthInches = 0.455, isFavorite = true, notes = "36gr Copper-Plated Hollow Point (1260 fps)"),
        AmmoLoad("ammo-wolf-gold-223-55", "Wolf Gold .223 Rem — 55gr FMJ", ".223 Rem", AmmoCategory.RIFLE, DragModel.G1, 0.255, 3150.0, 55.0, 0.224, bulletLengthInches = 0.740, isFavorite = true, notes = "Copper-jacketed 55gr FMJ (3250 fps / 24\", ~3150 in 16\" AR)"),
        AmmoLoad("ammo-ae-msr-556-55", "Federal American Eagle MSR 5.56 — 55gr FMJ", "5.56x45mm NATO", AmmoCategory.RIFLE, DragModel.G1, 0.246, 3165.0, 55.0, 0.224, bulletLengthInches = 0.740, isFavorite = true, notes = "Military-grade 5.56x45mm 55gr FMJ (3165 fps)"),
        AmmoLoad("ammo-fed-le-223-62", "Federal Premium LE Tactical .223 — 62gr SP", ".223 Rem", AmmoCategory.RIFLE, DragModel.G1, 0.224, 3050.0, 62.0, 0.224, bulletLengthInches = 0.785, isFavorite = true, notes = "Law Enforcement Tactical Bonded 62gr SP (LE223T3)"),
        AmmoLoad("ammo-scorpio-223-55", "STV Scorpio .223 Rem — 55gr FMJ", ".223 Rem", AmmoCategory.RIFLE, DragModel.G1, 0.250, 3150.0, 55.0, 0.224, bulletLengthInches = 0.740, isFavorite = true, notes = "Scorpio .223 Remington 55gr FMJ"),
        AmmoLoad("ammo-fed-syntech-9mm-124", "Federal Syntech Range 9mm — 124gr TSJ", "9mm Luger", AmmoCategory.HANDGUN, DragModel.G1, 0.150, 1110.0, 124.0, 0.355, bulletLengthInches = 0.590, isFavorite = true, notes = "124gr Total Synthetic Jacket (1110 fps)"),
        AmmoLoad("ammo-blazer-brass-9mm-115", "CCI Blazer Brass 9mm — 115gr FMJ", "9mm Luger", AmmoCategory.HANDGUN, DragModel.G1, 0.125, 1145.0, 115.0, 0.355, bulletLengthInches = 0.560, isFavorite = true, notes = "Blazer Brass 115gr FMJ (edit to 124gr / 1090 fps if 124gr box)"),
        AmmoLoad("ammo-blazer-alum-9mm-115", "CCI Blazer Aluminum 9mm — 115gr FMJ", "9mm Luger", AmmoCategory.HANDGUN, DragModel.G1, 0.125, 1145.0, 115.0, 0.355, bulletLengthInches = 0.560, isFavorite = true, notes = "Blazer Aluminum Case 115gr FMJ")
    )

    val starterProfiles: List<RifleProfile>
        get() = listOf(
            RifleProfile.combine(
                starterFirearms.first(),
                starterScopes.first(),
                starterAmmo.first()
            )
        )
}
