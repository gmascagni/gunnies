package com.gunnies.ballistics.data

import com.gunnies.ballistics.engine.DragTables
import com.gunnies.ballistics.model.CustomDragCurve
import com.gunnies.ballistics.model.DragCurvePoint
import com.gunnies.ballistics.model.DragModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import kotlin.math.abs

/**
 * Import/export and validation codec for user-supplied Custom Cd-vs-Mach drag curves
 * in both JSON (`schemaVersion = 1`) and CSV formats.
 */
object CustomDragCurveCodec {

    const val SCHEMA_VERSION = 1
    const val MIN_POINTS = 2
    const val MAX_POINTS = 128
    const val MIN_MACH = 0.0
    const val MAX_MACH = 5.0
    const val MIN_CD = 0.01
    const val MAX_CD = 3.0

    @Serializable
    data class CustomDragCurveDocument(
        val schemaVersion: Int = SCHEMA_VERSION,
        val name: String = "",
        val source: String = "",
        val points: List<DragCurvePoint> = emptyList()
    )

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Auto-detects JSON vs CSV and parses + validates into a sorted [CustomDragCurve].
     * Returns readable, line-numbered validation messages on failure.
     */
    fun parse(rawText: String, fileNameHint: String? = null): Result<CustomDragCurve> {
        val cleaned = rawText.removePrefix("\uFEFF").trim()
        if (cleaned.isEmpty()) {
            return Result.failure(IllegalArgumentException("File is empty. Expected JSON or CSV Mach,Cd pairs."))
        }
        val looksJson = cleaned.startsWith("{") ||
            fileNameHint?.endsWith(".json", ignoreCase = true) == true
        return if (looksJson) parseJson(cleaned) else parseCsv(cleaned, fileNameHint)
    }

    fun parseJson(rawJson: String): Result<CustomDragCurve> {
        val doc = try {
            json.decodeFromString<CustomDragCurveDocument>(rawJson.removePrefix("\uFEFF").trim())
        } catch (e: Exception) {
            return Result.failure(
                IllegalArgumentException("Invalid custom drag curve JSON: ${e.message ?: "malformed syntax"}")
            )
        }
        if (doc.schemaVersion != SCHEMA_VERSION) {
            return Result.failure(
                IllegalArgumentException(
                    "Unsupported custom drag curve schemaVersion ${doc.schemaVersion} (expected $SCHEMA_VERSION)."
                )
            )
        }
        return validateAndNormalize(
            name = doc.name.ifBlank { "Custom Drag Curve" },
            source = doc.source,
            points = doc.points
        )
    }

    fun parseCsv(rawCsv: String, fileNameHint: String? = null): Result<CustomDragCurve> {
        val lines = rawCsv.removePrefix("\uFEFF").replace("\r\n", "\n").replace("\r", "\n").split("\n")
        var name = fileNameHint?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Custom Drag Curve"
        var source = ""
        val points = mutableListOf<DragCurvePoint>()

        lines.forEachIndexed { idx, rawLine ->
            val lineNum = idx + 1
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachIndexed
            if (line.startsWith("#")) {
                val meta = line.removePrefix("#").trim()
                when {
                    meta.startsWith("name:", ignoreCase = true) ->
                        name = meta.substringAfter(":").trim().ifBlank { name }
                    meta.startsWith("source:", ignoreCase = true) ->
                        source = meta.substringAfter(":").trim()
                }
                return@forEachIndexed
            }

            val parts = line.split(',', ';', '\t').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size < 2) {
                return Result.failure(
                    IllegalArgumentException("Line $lineNum: expected at least 2 columns (mach,cd), got '$line'.")
                )
            }

            // Allow a single header row such as "mach,cd"
            if (points.isEmpty() && parts[0].equals("mach", ignoreCase = true)) {
                return@forEachIndexed
            }

            val mach = parts[0].toDoubleOrNull()
                ?: return Result.failure(
                    IllegalArgumentException("Line $lineNum: Mach value '${parts[0]}' is not a valid number.")
                )
            val cd = parts[1].toDoubleOrNull()
                ?: return Result.failure(
                    IllegalArgumentException("Line $lineNum: Cd value '${parts[1]}' is not a valid number.")
                )
            points.add(DragCurvePoint(mach = mach, cd = cd))
        }

        return validateAndNormalize(name = name, source = source, points = points)
    }

    fun validateAndNormalize(
        name: String,
        source: String,
        points: List<DragCurvePoint>
    ): Result<CustomDragCurve> {
        if (points.size < MIN_POINTS) {
            return Result.failure(
                IllegalArgumentException("Custom drag curve requires at least $MIN_POINTS Mach/Cd points (found ${points.size}).")
            )
        }
        if (points.size > MAX_POINTS) {
            return Result.failure(
                IllegalArgumentException("Custom drag curve has ${points.size} points; maximum supported is $MAX_POINTS.")
            )
        }

        points.forEachIndexed { i, pt ->
            val row = i + 1
            if (pt.mach.isNaN() || pt.mach.isInfinite() || pt.mach < MIN_MACH || pt.mach > MAX_MACH) {
                return Result.failure(
                    IllegalArgumentException(
                        "Point #$row: Mach ${pt.mach} is outside the supported range ($MIN_MACH–$MAX_MACH)."
                    )
                )
            }
            if (pt.cd.isNaN() || pt.cd.isInfinite() || pt.cd < MIN_CD || pt.cd > MAX_CD) {
                return Result.failure(
                    IllegalArgumentException(
                        "Point #$row (Mach ${String.format(Locale.US, "%.3f", pt.mach)}): Cd ${pt.cd} must be between $MIN_CD and $MAX_CD."
                    )
                )
            }
        }

        val sorted = points.sortedBy { it.mach }
        for (i in 1 until sorted.size) {
            if (abs(sorted[i].mach - sorted[i - 1].mach) < 1e-5) {
                return Result.failure(
                    IllegalArgumentException(
                        "Duplicate Mach knot ${String.format(Locale.US, "%.3f", sorted[i].mach)} — each Mach value must be unique."
                    )
                )
            }
        }

        return Result.success(
            CustomDragCurve(
                name = name.trim().ifBlank { "Custom Drag Curve" },
                source = source.trim(),
                points = sorted
            )
        )
    }

    fun exportJson(curve: CustomDragCurve): String {
        val doc = CustomDragCurveDocument(
            schemaVersion = SCHEMA_VERSION,
            name = curve.name.ifBlank { "Custom Drag Curve" },
            source = curve.source,
            points = curve.points.sortedBy { it.mach }
        )
        return json.encodeToString(doc)
    }

    fun exportCsv(curve: CustomDragCurve): String {
        val sb = StringBuilder()
        sb.appendLine("# name: ${curve.name.ifBlank { "Custom Drag Curve" }}")
        if (curve.source.isNotBlank()) {
            sb.appendLine("# source: ${curve.source}")
        }
        sb.appendLine("mach,cd")
        curve.points.sortedBy { it.mach }.forEach { pt ->
            sb.appendLine(String.format(Locale.US, "%.4f,%.5f", pt.mach, pt.cd))
        }
        return sb.toString()
    }

    /** Builds a clean starter custom Cd(Mach) curve sampled from standard BRL/JBM G7 or G1. */
    fun buildTemplateCurve(baseModel: DragModel = DragModel.G7, name: String? = null): CustomDragCurve {
        val effectiveBase = if (baseModel == DragModel.G1) DragModel.G1 else DragModel.G7
        val sampleMachs = listOf(
            0.00, 0.25, 0.50, 0.70, 0.80, 0.85, 0.90, 0.95, 1.00,
            1.05, 1.10, 1.15, 1.20, 1.30, 1.50, 1.80, 2.00, 2.50, 3.00, 3.50
        )
        val pts = sampleMachs.map { m ->
            DragCurvePoint(mach = m, cd = DragTables.getCd(effectiveBase, m))
        }
        return CustomDragCurve(
            name = name ?: "${effectiveBase.label} Reference Template",
            source = "BRL/JBM ${effectiveBase.label} standard drag table (template)",
            points = pts
        )
    }
}
