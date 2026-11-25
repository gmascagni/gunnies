package com.gunnies.ballistics.data

import android.content.Context
import com.gunnies.ballistics.model.AngularUnit
import com.gunnies.ballistics.model.ReticleType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class ReticleFocalPlane(val label: String) {
    FFP("FFP"),
    SFP("SFP"),
    NON_MAGNIFYING("1x")
}

@Serializable
enum class ReticleStadiaStyle {
    CHRISTMAS_TREE,
    DUPLEX,
    RED_DOT,
    HOLOGRAPHIC_RING,
    PISTOL_IRONS
}

/**
 * Data-driven reticle definition (JSON schema v1) so new reticles can be added or shared
 * without modifying Kotlin rendering code.
 */
@Serializable
data class ReticleDefinition(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val focalPlane: ReticleFocalPlane = ReticleFocalPlane.FFP,
    val unit: AngularUnit = AngularUnit.MRAD,
    val stadiaStyle: ReticleStadiaStyle = ReticleStadiaStyle.CHRISTMAS_TREE,
    val defaultZoom: Float = 10f,
    val minZoom: Float = 4f,
    val maxZoom: Float = 24f,
    val maxSubtension: Float = 10f,
    val majorTickStep: Float = 1f,
    val minorTickStep: Float = 0.2f,
    val treeStartHold: Float = 1f,
    val treeMaxHold: Float = 10f,
    val centerDotSize: Float = 0.05f,
    val outerRingDiameter: Float = 0f,
    val illuminatedByDefault: Boolean = false,
    val author: String = "Gunnies Ballistics",
    val notes: String = ""
) {
    val isMradBased: Boolean get() = unit == AngularUnit.MRAD
}

object ReticleDefinitionCodec {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val builtInDefinitions: Map<ReticleType, ReticleDefinition> = mapOf(
        ReticleType.ARKEN_VPR_MIL to ReticleDefinition(
            id = "arken-vpr-mil",
            name = "Arken VPR MIL (FFP)",
            focalPlane = ReticleFocalPlane.FFP,
            unit = AngularUnit.MRAD,
            stadiaStyle = ReticleStadiaStyle.CHRISTMAS_TREE,
            defaultZoom = 10f, minZoom = 4f, maxZoom = 25f,
            maxSubtension = 10f, majorTickStep = 1f, minorTickStep = 0.2f,
            treeStartHold = 1f, treeMaxHold = 10f, centerDotSize = 0.05f,
            illuminatedByDefault = true,
            notes = "0.2 MRAD subtensions with floating center dot and Christmas-tree wind holds."
        ),
        ReticleType.PA_ARC2_MOA to ReticleDefinition(
            id = "pa-arc2-moa",
            name = "Primary Arms ARC-2 MOA (FFP)",
            focalPlane = ReticleFocalPlane.FFP,
            unit = AngularUnit.MOA,
            stadiaStyle = ReticleStadiaStyle.CHRISTMAS_TREE,
            defaultZoom = 10f, minZoom = 4f, maxZoom = 24f,
            maxSubtension = 30f, majorTickStep = 4f, minorTickStep = 1f,
            treeStartHold = 4f, treeMaxHold = 28f, centerDotSize = 0.25f,
            notes = "1 MOA subtensions with windage grid."
        ),
        ReticleType.PA_ACSS_RGRID to ReticleDefinition(
            id = "pa-acss-rgrid",
            name = "Primary Arms ACSS / R-Grid (FFP)",
            focalPlane = ReticleFocalPlane.FFP,
            unit = AngularUnit.MRAD,
            stadiaStyle = ReticleStadiaStyle.CHRISTMAS_TREE,
            defaultZoom = 10f, minZoom = 4f, maxZoom = 14f,
            maxSubtension = 10f, majorTickStep = 1f, minorTickStep = 0.5f,
            treeStartHold = 1f, treeMaxHold = 10f, centerDotSize = 0.1f,
            notes = "R-Grid MIL holdover tree."
        ),
        ReticleType.SIMPLE_DUPLEX to ReticleDefinition(
            id = "classic-duplex",
            name = "Classic Duplex (SFP)",
            focalPlane = ReticleFocalPlane.SFP,
            unit = AngularUnit.MOA,
            stadiaStyle = ReticleStadiaStyle.DUPLEX,
            defaultZoom = 6f, minZoom = 3f, maxZoom = 12f,
            maxSubtension = 20f, majorTickStep = 5f, minorTickStep = 5f,
            treeStartHold = 0f, treeMaxHold = 0f, centerDotSize = 0.2f,
            notes = "Traditional hunting duplex crosshair."
        ),
        ReticleType.RED_DOT_CIRCLE_65 to ReticleDefinition(
            id = "red-dot-2-65",
            name = "Red Dot (2 / 65 MOA)",
            focalPlane = ReticleFocalPlane.NON_MAGNIFYING,
            unit = AngularUnit.MOA,
            stadiaStyle = ReticleStadiaStyle.RED_DOT,
            defaultZoom = 1f, minZoom = 1f, maxZoom = 3f,
            maxSubtension = 65f, majorTickStep = 32.5f, minorTickStep = 32.5f,
            centerDotSize = 2.0f, outerRingDiameter = 65.0f,
            illuminatedByDefault = true,
            notes = "2 MOA center dot inside 65 MOA segmented circle."
        ),
        ReticleType.EOTECH_HWS_68 to ReticleDefinition(
            id = "eotech-68-1",
            name = "EOTech 68 MOA / 1 MOA",
            focalPlane = ReticleFocalPlane.NON_MAGNIFYING,
            unit = AngularUnit.MOA,
            stadiaStyle = ReticleStadiaStyle.HOLOGRAPHIC_RING,
            defaultZoom = 1f, minZoom = 1f, maxZoom = 3f,
            maxSubtension = 68f, majorTickStep = 34f, minorTickStep = 34f,
            centerDotSize = 1.0f, outerRingDiameter = 68.0f,
            illuminatedByDefault = true,
            notes = "1 MOA dot inside 68 MOA holographic ring with cardinal ticks."
        ),
        ReticleType.HANDGUN_THREE_DOT to ReticleDefinition(
            id = "handgun-3-dot",
            name = "Handgun 3-Dot Irons",
            focalPlane = ReticleFocalPlane.NON_MAGNIFYING,
            unit = AngularUnit.MOA,
            stadiaStyle = ReticleStadiaStyle.PISTOL_IRONS,
            defaultZoom = 1f, minZoom = 1f, maxZoom = 1f,
            maxSubtension = 40f, majorTickStep = 10f, minorTickStep = 10f,
            centerDotSize = 6.0f,
            notes = "Standard 3-dot pistol iron sight picture."
        )
    )

    fun forReticleType(type: ReticleType): ReticleDefinition =
        builtInDefinitions[type] ?: builtInDefinitions.getValue(ReticleType.ARKEN_VPR_MIL)

    fun encodeJson(definition: ReticleDefinition): String = json.encodeToString(definition)

    fun decodeJson(rawJson: String): Result<ReticleDefinition> = runCatching {
        val parsed = json.decodeFromString<ReticleDefinition>(rawJson)
        validate(parsed).getOrThrow()
    }

    fun validate(def: ReticleDefinition): Result<ReticleDefinition> = runCatching {
        require(def.schemaVersion == 1) { "Unsupported reticle schemaVersion=${def.schemaVersion} (expected 1)." }
        require(def.id.isNotBlank()) { "Reticle 'id' must not be blank." }
        require(def.name.isNotBlank()) { "Reticle 'name' must not be blank." }
        require(def.unit == AngularUnit.MRAD || def.unit == AngularUnit.MOA) {
            "Reticle 'unit' must be MRAD or MOA (got ${def.unit})."
        }
        require(def.minZoom >= 1f && def.maxZoom >= def.minZoom && def.maxZoom <= 60f) {
            "Zoom bounds invalid: min=${def.minZoom}, max=${def.maxZoom} (must be 1..60)."
        }
        require(def.defaultZoom in def.minZoom..def.maxZoom) {
            "defaultZoom (${def.defaultZoom}) must be within [${def.minZoom}, ${def.maxZoom}]."
        }
        require(def.maxSubtension in 1f..150f) {
            "maxSubtension (${def.maxSubtension}) must be between 1 and 150."
        }
        require(def.majorTickStep > 0f && def.minorTickStep > 0f && def.minorTickStep <= def.majorTickStep) {
            "Tick steps must be positive with minorTickStep <= majorTickStep."
        }
        def
    }

    /**
     * Loads any bundled JSON reticle definitions from `assets/reticles/`.
     * Adding a new JSON file to `app/src/main/assets/reticles/` requires zero Kotlin changes.
     */
    fun loadBundledAssetDefinitions(context: Context): List<ReticleDefinition> {
        val files = runCatching { context.assets.list("reticles").orEmpty() }.getOrDefault(emptyArray())
        return files.filter { it.endsWith(".json", ignoreCase = true) }
            .sorted()
            .mapNotNull { fileName ->
                runCatching {
                    val raw = context.assets.open("reticles/$fileName").bufferedReader().use { it.readText() }
                    decodeJson(raw).getOrNull()
                }.getOrNull()
            }
    }
}
