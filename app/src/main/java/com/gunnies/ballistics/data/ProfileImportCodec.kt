package com.gunnies.ballistics.data

import com.gunnies.ballistics.model.AmmoCategory
import com.gunnies.ballistics.model.AmmoLoad
import com.gunnies.ballistics.model.BcBand
import com.gunnies.ballistics.model.ClickUnit
import com.gunnies.ballistics.model.DragModel
import com.gunnies.ballistics.model.FirearmCategory
import com.gunnies.ballistics.model.FirearmSetup
import com.gunnies.ballistics.model.ScopeCategory
import com.gunnies.ballistics.model.ScopeSetup
import com.gunnies.ballistics.model.TwistDirection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * Result of importing an external ballistic profile (e.g., Strelok / Strelok Pro XML, JSON,
 * key=value `.srl`, or CSV export) into Gunnies Ballistics' modular Firearm + Scope + Ammo model.
 */
data class ImportedProfileBundle(
    val sourceFormat: String,
    val firearm: FirearmSetup,
    val scope: ScopeSetup,
    val ammo: AmmoLoad,
    val warnings: List<String> = emptyList()
)

/**
 * Parses common Strelok-style profile exports (XML, JSON, key=value `.srl`, or 2-row CSV)
 * into non-destructive Gunnies [FirearmSetup], [ScopeSetup], and [AmmoLoad] records.
 */
object ProfileImportCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawText: String, fileNameHint: String? = null): Result<ImportedProfileBundle> {
        val cleaned = rawText.removePrefix("\uFEFF").trim()
        if (cleaned.isEmpty()) {
            return Result.failure(IllegalArgumentException("Profile file is empty."))
        }
        return when {
            cleaned.startsWith("{") -> parseJsonMap(cleaned)
            cleaned.startsWith("<") -> parseXml(cleaned)
            looksLikeCsvTable(cleaned, fileNameHint) -> parseCsvTable(cleaned)
            else -> parseKeyValue(cleaned)
        }
    }

    private fun looksLikeCsvTable(text: String, fileNameHint: String?): Boolean {
        val lines = text.replace("\r\n", "\n").split("\n").map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (lines.size < 2) return false
        val first = lines[0]
        return (first.contains(",") && !first.contains("=")) ||
            (fileNameHint?.endsWith(".csv", ignoreCase = true) == true && first.contains(","))
    }

    private fun parseJsonMap(rawJson: String): Result<ImportedProfileBundle> {
        val root = try {
            json.parseToJsonElement(rawJson).jsonObject
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Invalid JSON profile: ${e.message ?: "malformed syntax"}"))
        }

        val map = mutableMapOf<String, String>()
        fun flatten(obj: JsonObject) {
            for ((k, v) in obj) {
                val key = normalizeKey(k)
                when (v) {
                    is JsonPrimitive -> v.contentOrNull?.let { map[key] = it }
                    is JsonObject -> flatten(v)
                    else -> Unit
                }
            }
        }
        flatten(root)

        // Optional multi-BC array in JSON
        val bands = mutableListOf<BcBand>()
        root["bc_bands"]?.let { el ->
            runCatching {
                el.jsonArray.forEach { item ->
                    val o = item.jsonObject
                    val v = (o["min_velocity_fps"] ?: o["minVelocityFps"] ?: o["v"])?.jsonPrimitive?.doubleOrNull
                    val b = (o["bc"])?.jsonPrimitive?.doubleOrNull
                    if (v != null && b != null && v >= 0.0 && b in 0.02..2.0) bands += BcBand(v, b)
                }
            }
        }

        return buildBundle("Strelok / JSON Profile", map, bands)
    }

    private fun parseXml(rawXml: String): Result<ImportedProfileBundle> {
        val map = mutableMapOf<String, String>()
        // Extract <tag>value</tag> and attribute="value" pairs cleanly without Android-only XmlPullParser deps
        val tagRegex = Regex("<([A-Za-z0-9_:\\-]+)[^>]*>([^<]+)</\\1>")
        tagRegex.findAll(rawXml).forEach { m ->
            val key = normalizeKey(m.groupValues[1])
            val value = m.groupValues[2].trim()
            if (value.isNotEmpty() && !map.containsKey(key)) {
                map[key] = value
            }
        }
        val attrRegex = Regex("""([A-Za-z0-9_:\-]+)\s*=\s*"([^"]*)"""")
        attrRegex.findAll(rawXml).forEach { m ->
            val key = normalizeKey(m.groupValues[1])
            val value = m.groupValues[2].trim()
            if (value.isNotEmpty() && !map.containsKey(key)) {
                map[key] = value
            }
        }
        if (map.isEmpty()) {
            return Result.failure(IllegalArgumentException("No readable XML profile fields found."))
        }
        return buildBundle("Strelok XML Profile", map, emptyList())
    }

    private fun parseCsvTable(rawCsv: String): Result<ImportedProfileBundle> {
        val lines = rawCsv.replace("\r\n", "\n").replace("\r", "\n")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        if (lines.size < 2) {
            return Result.failure(IllegalArgumentException("CSV profile requires a header row and at least one data row."))
        }
        val headers = splitCsvLine(lines[0]).map(::normalizeKey)
        val values = splitCsvLine(lines[1])
        val map = mutableMapOf<String, String>()
        headers.forEachIndexed { i, h ->
            val v = values.getOrNull(i)?.trim().orEmpty()
            if (h.isNotEmpty() && v.isNotEmpty()) map[h] = v
        }
        return buildBundle("CSV Profile", map, emptyList())
    }

    private fun parseKeyValue(rawText: String): Result<ImportedProfileBundle> {
        val map = mutableMapOf<String, String>()
        rawText.replace("\r\n", "\n").replace("\r", "\n").split("\n").forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";") || line.startsWith("[")) return@forEach
            val sep = when {
                line.contains('=') -> '='
                line.contains(':') -> ':'
                else -> return@forEach
            }
            val key = normalizeKey(line.substringBefore(sep))
            val value = line.substringAfter(sep).trim().trim('"')
            if (key.isNotEmpty() && value.isNotEmpty()) {
                map[key] = value
            }
        }
        if (map.isEmpty()) {
            return Result.failure(IllegalArgumentException("No recognizable profile fields found (expected Strelok XML, JSON, CSV, or key=value format)."))
        }
        return buildBundle("Strelok Key-Value Profile", map, emptyList())
    }

    private fun buildBundle(
        formatLabel: String,
        map: Map<String, String>,
        extraBands: List<BcBand>
    ): Result<ImportedProfileBundle> {
        val warnings = mutableListOf<String>()

        fun str(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { k -> map[normalizeKey(k)]?.takeIf { it.isNotBlank() } }

        fun num(vararg keys: String): Double? =
            str(*keys)?.replace(",", ".")?.replace(Regex("[^0-9.+\\-]"), "")?.toDoubleOrNull()

        val rifleName = str("riflename", "rifle", "gunname", "gun", "weaponname", "name")
            ?: "Imported Rifle"
        val scopeName = str("scopename", "scope", "optic", "opticname")
            ?: "$rifleName Scope"
        val cartridgeName = str("cartridgename", "cartridge", "ammoname", "ammo", "bulletname", "loadname")
            ?: str("name")
            ?: "Imported Load"
        val caliber = str("caliber", "calibre") ?: "Custom"

        // Muzzle velocity (fps or m/s)
        val mvFps = num("muzzlevelocity", "mv", "mvfps", "speed", "velocity", "v0")
            ?: num("mvms", "velocityms")?.times(3.280839895)
            ?: return Result.failure(
                IllegalArgumentException("Missing muzzle velocity (expected 'mv', 'muzzle_velocity', or 'speed').")
            )
        if (mvFps !in 100.0..5500.0) {
            return Result.failure(IllegalArgumentException("Muzzle velocity $mvFps fps is outside valid range (100–5500 fps)."))
        }

        // Drag model
        val dragRaw = str("dragfunction", "dragmodel", "df", "model")?.uppercase() ?: "G1"
        val dragModel = when {
            dragRaw.contains("G7") || dragRaw == "7" -> DragModel.G7
            else -> DragModel.G1
        }

        // Multi-BC bands (Strelok bc1..bc5 + v1..v5)
        val bands = mutableListOf<BcBand>().apply { addAll(extraBands) }
        for (i in 1..5) {
            val b = num("bc$i")
            val v = num("v$i", "vel$i", "speed$i")
            if (b != null && b in 0.02..2.0 && v != null && v >= 0.0) {
                bands += BcBand(minVelocityFps = v, bc = b)
            }
        }
        val sortedBands = bands.distinctBy { it.minVelocityFps.toInt() }
            .sortedByDescending { it.minVelocityFps }
            .take(5)

        val singleBc = num("bc", "ballisticcoefficient", "bc1")
            ?: sortedBands.firstOrNull()?.bc
            ?: return Result.failure(
                IllegalArgumentException("Missing ballistic coefficient (expected 'bc' or 'bc1').")
            )
        if (singleBc !in 0.02..2.0) {
            return Result.failure(IllegalArgumentException("Ballistic coefficient $singleBc is outside valid range (0.02–2.0)."))
        }

        val weightGr = num("bulletweight", "weight", "weightgr", "mass") ?: run {
            warnings += "Bullet weight missing; defaulted to 140 gr."
            140.0
        }
        val diameterIn = num("bulletdiameter", "diameter", "caliberin", "dia") ?: run {
            warnings += "Bullet diameter missing; defaulted to 0.308 in."
            0.308
        }
        val lengthIn = num("bulletlength", "length", "len")?.takeIf { it in 0.2..3.5 }

        val sightHeightIn = num("sightheight", "scopeheight", "sh") ?: run {
            warnings += "Sight height missing; defaulted to 1.75 in."
            1.75
        }
        val zeroRangeYd = num("zerodistance", "zerorange", "zero") ?: run {
            warnings += "Zero distance missing; defaulted to 100 yd."
            100.0
        }
        val twistIn = num("twist", "barreltwist", "twistrate") ?: 10.0
        val twistDirRaw = str("twistdirection", "twistdir")?.uppercase() ?: "RIGHT"
        val twistDir = when {
            twistDirRaw.startsWith("L") -> TwistDirection.LEFT
            twistDirRaw.startsWith("N") || twistIn <= 0.0 -> TwistDirection.NONE
            else -> TwistDirection.RIGHT
        }

        val clickUnit = parseClickUnit(str("clickunit", "clicks", "turretunit", "vertclick", "click"))
        val tempSens = num("tempsensitivity", "mvtempsens", "powdertemp") ?: 0.0
        val chronoTemp = num("chronotemp", "zerotemp", "temperature") ?: 59.0

        val isRimfire = caliber.contains("22", ignoreCase = true) && mvFps < 1700.0
        val ammoCat = if (isRimfire) AmmoCategory.RIMFIRE else AmmoCategory.RIFLE

        val firearm = FirearmSetup(
            id = "imp-gun-${UUID.randomUUID().toString().take(8)}",
            name = rifleName,
            category = FirearmCategory.RIFLE,
            caliberHint = caliber,
            barrelLengthInches = num("barrellength", "barrel") ?: 20.0,
            barrelTwistInches = twistIn.coerceIn(0.0, 60.0),
            twistDirection = twistDir,
            notes = "Imported via $formatLabel"
        )
        val scope = ScopeSetup(
            id = "imp-scope-${UUID.randomUUID().toString().take(8)}",
            name = scopeName,
            scopeModel = scopeName,
            category = ScopeCategory.RIFLE_SCOPE,
            sightHeightInches = sightHeightIn.coerceIn(0.2, 6.0),
            zeroRangeYards = zeroRangeYd.coerceIn(10.0, 1000.0),
            clickUnit = clickUnit,
            zeroTempF = chronoTemp.coerceIn(-40.0, 140.0),
            notes = "Imported via $formatLabel"
        )
        val ammo = AmmoLoad(
            id = "imp-ammo-${UUID.randomUUID().toString().take(8)}",
            name = cartridgeName,
            caliber = caliber,
            category = ammoCat,
            dragModel = dragModel,
            ballisticCoefficient = sortedBands.firstOrNull()?.bc ?: singleBc,
            muzzleVelocityFps = mvFps,
            bulletWeightGrains = weightGr.coerceIn(10.0, 1000.0),
            bulletDiameterInches = diameterIn.coerceIn(0.14, 0.75),
            bulletLengthInches = lengthIn,
            mvTempSensitivityFpsPerF = tempSens.coerceIn(-5.0, 5.0),
            chronoTempF = chronoTemp.coerceIn(-40.0, 140.0),
            bcBands = sortedBands,
            notes = "Imported via $formatLabel"
        )

        return Result.success(
            ImportedProfileBundle(
                sourceFormat = formatLabel,
                firearm = firearm,
                scope = scope,
                ammo = ammo,
                warnings = warnings
            )
        )
    }

    private fun parseClickUnit(raw: String?): ClickUnit {
        if (raw.isNullOrBlank()) return ClickUnit.QUARTER_MOA
        val u = raw.uppercase()
        return when {
            u.contains("0.05") && (u.contains("MRAD") || u.contains("MIL")) -> ClickUnit.TWENTIETH_MRAD
            u.contains("MRAD") || u.contains("MIL") || u == "0.1" -> ClickUnit.TENTH_MRAD
            u.contains("1/8") || u.contains("0.125") -> ClickUnit.EIGHTH_MOA
            u.contains("1/2") || u.contains("0.5") -> ClickUnit.HALF_MOA
            else -> ClickUnit.QUARTER_MOA
        }
    }

    private fun normalizeKey(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    out += cur.toString()
                    cur.clear()
                }
                else -> cur.append(ch)
            }
        }
        out += cur.toString()
        return out
    }
}
