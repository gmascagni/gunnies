package com.bc1800.ballistics.data

import com.bc1800.ballistics.model.ArsenalBackup
import com.bc1800.ballistics.model.BackupImportMode
import com.bc1800.ballistics.model.DefaultProfiles
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class BackupImportSummary(
    val mode: BackupImportMode,
    val firearmsCount: Int,
    val scopesCount: Int,
    val ammoCount: Int,
    val suppressorsCount: Int,
    val stageCardsCount: Int,
    val sessionLogsCount: Int
)

/**
 * Pure Kotlin versioned JSON codec & merge/overwrite engine for full-arsenal backup/restore.
 * Isolated from Android Context so it can be thoroughly verified in JVM unit tests.
 */
object ArsenalBackupCodec {

    const val CURRENT_SCHEMA_VERSION = 1

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(backup: ArsenalBackup): String = prettyJson.encodeToString(backup)

    fun decode(rawJson: String): Result<ArsenalBackup> = runCatching {
        val parsed = prettyJson.decodeFromString<ArsenalBackup>(rawJson)
        require(parsed.schemaVersion >= 1) { "Unsupported backup schemaVersion: ${parsed.schemaVersion}" }
        parsed
    }

    /**
     * Combines [existing] and [incoming] according to [mode]:
     * - [BackupImportMode.MERGE]: non-destructive — retains all existing records and appends incoming records
     *   whose ID is not already present; preserves active selections while validating references.
     * - [BackupImportMode.OVERWRITE]: replaces all lists and preferences with [incoming] (falling back to
     *   starter defaults if critical lists are empty so the app always stays in a valid state).
     */
    fun applyImport(
        existing: ArsenalBackup,
        incoming: ArsenalBackup,
        mode: BackupImportMode
    ): Pair<ArsenalBackup, BackupImportSummary> {
        return when (mode) {
            BackupImportMode.MERGE -> {
                val mergedGuns = mergeById(existing.firearms, incoming.firearms) { it.id }
                    .ifEmpty { DefaultProfiles.starterFirearms }
                val mergedScopes = mergeById(existing.scopes, incoming.scopes) { it.id }
                    .ifEmpty { DefaultProfiles.starterScopes }
                val mergedAmmo = mergeById(existing.ammoLoads, incoming.ammoLoads) { it.id }
                    .ifEmpty { DefaultProfiles.starterAmmo }
                val mergedSupps = mergeById(existing.suppressors, incoming.suppressors) { it.id }
                val mergedStages = mergeById(existing.stageCards, incoming.stageCards) { it.id }
                val mergedLogs = mergeById(existing.sessionLogs, incoming.sessionLogs) { it.id }
                    .sortedByDescending { it.timestampMs }

                val mergedMemory = incoming.preferences.firearmAmmoMemory + existing.preferences.firearmAmmoMemory
                val validPrefs = existing.preferences.copy(
                    activeFirearmId = existing.preferences.activeFirearmId.takeIf { id -> mergedGuns.any { it.id == id } }
                        ?: mergedGuns.first().id,
                    activeScopeId = existing.preferences.activeScopeId.takeIf { id -> mergedScopes.any { it.id == id } }
                        ?: mergedScopes.first().id,
                    activeAmmoId = existing.preferences.activeAmmoId.takeIf { id -> mergedAmmo.any { it.id == id } }
                        ?: mergedAmmo.first().id,
                    activeSuppressorId = existing.preferences.activeSuppressorId?.takeIf { id -> mergedSupps.any { it.id == id } },
                    firearmAmmoMemory = mergedMemory
                )

                val result = ArsenalBackup(
                    schemaVersion = CURRENT_SCHEMA_VERSION,
                    firearms = mergedGuns,
                    scopes = mergedScopes,
                    ammoLoads = mergedAmmo,
                    suppressors = mergedSupps,
                    stageCards = mergedStages,
                    sessionLogs = mergedLogs,
                    preferences = validPrefs
                )
                val addedSummary = BackupImportSummary(
                    mode = mode,
                    firearmsCount = mergedGuns.size - existing.firearms.size,
                    scopesCount = mergedScopes.size - existing.scopes.size,
                    ammoCount = mergedAmmo.size - existing.ammoLoads.size,
                    suppressorsCount = mergedSupps.size - existing.suppressors.size,
                    stageCardsCount = mergedStages.size - existing.stageCards.size,
                    sessionLogsCount = mergedLogs.size - existing.sessionLogs.size
                )
                result to addedSummary
            }

            BackupImportMode.OVERWRITE -> {
                val guns = incoming.firearms.ifEmpty { DefaultProfiles.starterFirearms }
                val scopes = incoming.scopes.ifEmpty { DefaultProfiles.starterScopes }
                val ammo = incoming.ammoLoads.ifEmpty { DefaultProfiles.starterAmmo }
                val supps = incoming.suppressors
                val stages = incoming.stageCards
                val logs = incoming.sessionLogs.sortedByDescending { it.timestampMs }

                val validPrefs = incoming.preferences.copy(
                    activeFirearmId = incoming.preferences.activeFirearmId.takeIf { id -> guns.any { it.id == id } }
                        ?: guns.first().id,
                    activeScopeId = incoming.preferences.activeScopeId.takeIf { id -> scopes.any { it.id == id } }
                        ?: scopes.first().id,
                    activeAmmoId = incoming.preferences.activeAmmoId.takeIf { id -> ammo.any { it.id == id } }
                        ?: ammo.first().id,
                    activeSuppressorId = incoming.preferences.activeSuppressorId?.takeIf { id -> supps.any { it.id == id } }
                )

                val result = ArsenalBackup(
                    schemaVersion = CURRENT_SCHEMA_VERSION,
                    firearms = guns,
                    scopes = scopes,
                    ammoLoads = ammo,
                    suppressors = supps,
                    stageCards = stages,
                    sessionLogs = logs,
                    preferences = validPrefs
                )
                val summary = BackupImportSummary(
                    mode = mode,
                    firearmsCount = guns.size,
                    scopesCount = scopes.size,
                    ammoCount = ammo.size,
                    suppressorsCount = supps.size,
                    stageCardsCount = stages.size,
                    sessionLogsCount = logs.size
                )
                result to summary
            }
        }
    }

    private inline fun <T> mergeById(
        existing: List<T>,
        incoming: List<T>,
        idOf: (T) -> String
    ): List<T> {
        val existingIds = existing.mapTo(HashSet(existing.size)) { idOf(it) }
        val additions = incoming.filter { idOf(it) !in existingIds }
        return existing + additions
    }
}
