package com.bc1800.ballistics.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bc1800.ballistics.model.AmmoLoad
import com.bc1800.ballistics.model.AngularUnit
import com.bc1800.ballistics.model.DefaultProfiles
import com.bc1800.ballistics.model.FirearmSetup
import com.bc1800.ballistics.model.ScopeSetup
import com.bc1800.ballistics.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.ballisticsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "bc1800_ballistics_store"
)

/**
 * Atomic, crash-safe persistent storage backed by AndroidX DataStore.
 * Uses distinctUntilChanged() on raw JSON per key so changing one preference never
 * re-decodes firearms, scopes, or ammo lists, and safely merges seed updates without
 * clobbering user customizations.
 */
class BallisticsRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private object Keys {
        val USER_PREFERENCES_JSON = stringPreferencesKey("user_preferences_json")
        val FIREARMS_JSON = stringPreferencesKey("firearms_json")
        val SCOPES_JSON = stringPreferencesKey("scopes_json")
        val AMMO_JSON = stringPreferencesKey("ammo_json")
        val ARSENAL_SEED_VERSION = stringPreferencesKey("arsenal_seed_version")
    }

    companion object {
        private const val CURRENT_ARSENAL_VERSION = "v7_decoupled_scopes"
    }

    private val safeData: Flow<Preferences> = context.ballisticsDataStore.data
        .catch { ex -> if (ex is IOException) emit(emptyPreferences()) else throw ex }

    val preferencesFlow: Flow<UserPreferences> = safeData
        .map { it[Keys.USER_PREFERENCES_JSON] }
        .distinctUntilChanged()
        .map { raw ->
            if (raw.isNullOrBlank()) UserPreferences()
            else runCatching { json.decodeFromString<UserPreferences>(raw) }.getOrElse { UserPreferences() }
        }

    val firearmsFlow: Flow<List<FirearmSetup>> = safeData
        .map { it[Keys.FIREARMS_JSON] }
        .distinctUntilChanged()
        .map { raw -> decodeListOrDefault(raw, DefaultProfiles.starterFirearms) }

    val scopesFlow: Flow<List<ScopeSetup>> = safeData
        .map { it[Keys.SCOPES_JSON] }
        .distinctUntilChanged()
        .map { raw -> decodeListOrDefault(raw, DefaultProfiles.starterScopes) }

    val ammoFlow: Flow<List<AmmoLoad>> = safeData
        .map { it[Keys.AMMO_JSON] }
        .distinctUntilChanged()
        .map { raw -> decodeListOrDefault(raw, DefaultProfiles.starterAmmo) }

    private inline fun <reified T> decodeListOrDefault(raw: String?, fallback: List<T>): List<T> {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { json.decodeFromString<List<T>>(raw).ifEmpty { fallback } }
            .getOrElse { fallback }
    }

    /**
     * Non-destructive upgrade seed: adds any newly introduced starter items by ID while
     * strictly preserving all existing user edits (custom zero, sight height, reticle, trued MV, etc.).
     */
    suspend fun ensureCustomArsenalSeeded() {
        context.ballisticsDataStore.edit { prefs ->
            if (prefs[Keys.ARSENAL_SEED_VERSION] == CURRENT_ARSENAL_VERSION) return@edit

            val existingGuns = prefs[Keys.FIREARMS_JSON]?.let {
                runCatching { json.decodeFromString<List<FirearmSetup>>(it) }.getOrNull()
            }.orEmpty()
            val existingGunIds = existingGuns.map { it.id }.toSet()
            val mergedGuns = existingGuns + DefaultProfiles.starterFirearms.filterNot { it.id in existingGunIds }

            val existingScopes = prefs[Keys.SCOPES_JSON]?.let {
                runCatching { json.decodeFromString<List<ScopeSetup>>(it) }.getOrNull()
            }.orEmpty().map { scope ->
                if (scope.id == "scope-arken-sh4-gen2" && scope.name.contains("(CZ 457)")) {
                    scope.copy(
                        name = scope.name
                            .replace(" (CZ 457)", "")
                            .replace("(CZ 457)", "")
                            .trim()
                    )
                } else {
                    scope
                }
            }
            val existingScopeIds = existingScopes.map { it.id }.toSet()
            val mergedScopes = existingScopes + DefaultProfiles.starterScopes.filterNot { it.id in existingScopeIds }

            val existingAmmo = prefs[Keys.AMMO_JSON]?.let {
                runCatching { json.decodeFromString<List<AmmoLoad>>(it) }.getOrNull()
            }.orEmpty()
            val existingAmmoIds = existingAmmo.map { it.id }.toSet()
            val mergedAmmo = existingAmmo + DefaultProfiles.starterAmmo.filterNot { it.id in existingAmmoIds }

            prefs[Keys.FIREARMS_JSON] = json.encodeToString(mergedGuns)
            prefs[Keys.SCOPES_JSON] = json.encodeToString(mergedScopes)
            prefs[Keys.AMMO_JSON] = json.encodeToString(mergedAmmo)
            prefs[Keys.ARSENAL_SEED_VERSION] = CURRENT_ARSENAL_VERSION

            val currentPrefs = prefs[Keys.USER_PREFERENCES_JSON]?.let {
                runCatching { json.decodeFromString<UserPreferences>(it) }.getOrNull()
            } ?: UserPreferences()

            val validGunId = if (mergedGuns.any { it.id == currentPrefs.activeFirearmId }) currentPrefs.activeFirearmId else mergedGuns.first().id
            val validScopeId = if (mergedScopes.any { it.id == currentPrefs.activeScopeId }) currentPrefs.activeScopeId else mergedScopes.first().id
            val validAmmoId = if (mergedAmmo.any { it.id == currentPrefs.activeAmmoId }) currentPrefs.activeAmmoId else mergedAmmo.first().id

            val activeScopeObj = mergedScopes.first { it.id == validScopeId }
            val matchedAngular = if (currentPrefs.autoMatchScopeUnit) {
                if (activeScopeObj.clickUnit.isMrad) AngularUnit.MRAD else AngularUnit.MOA
            } else {
                currentPrefs.angularUnit
            }

            prefs[Keys.USER_PREFERENCES_JSON] = json.encodeToString(
                currentPrefs.copy(
                    activeFirearmId = validGunId,
                    activeScopeId = validScopeId,
                    activeAmmoId = validAmmoId,
                    angularUnit = matchedAngular
                )
            )
        }
    }

    suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        context.ballisticsDataStore.edit { prefs ->
            val current = prefs[Keys.USER_PREFERENCES_JSON]?.let {
                runCatching { json.decodeFromString<UserPreferences>(it) }.getOrNull()
            } ?: UserPreferences()
            prefs[Keys.USER_PREFERENCES_JSON] = json.encodeToString(transform(current))
        }
    }

    suspend fun saveFirearm(firearm: FirearmSetup) {
        upsertItem(Keys.FIREARMS_JSON, DefaultProfiles.starterFirearms, firearm) { it.id }
    }

    suspend fun deleteFirearm(firearmId: String): Boolean {
        return deleteItem(Keys.FIREARMS_JSON, DefaultProfiles.starterFirearms, firearmId, { it.id }) { prefs, fallbackId ->
            if (prefs.activeFirearmId == firearmId) prefs.copy(activeFirearmId = fallbackId) else prefs
        }
    }

    suspend fun saveScope(scope: ScopeSetup) {
        upsertItem(Keys.SCOPES_JSON, DefaultProfiles.starterScopes, scope) { it.id }
    }

    suspend fun deleteScope(scopeId: String): Boolean {
        return deleteItem(Keys.SCOPES_JSON, DefaultProfiles.starterScopes, scopeId, { it.id }) { prefs, fallbackId ->
            if (prefs.activeScopeId == scopeId) prefs.copy(activeScopeId = fallbackId) else prefs
        }
    }

    suspend fun saveAmmo(ammo: AmmoLoad) {
        upsertItem(Keys.AMMO_JSON, DefaultProfiles.starterAmmo, ammo) { it.id }
    }

    suspend fun deleteAmmo(ammoId: String): Boolean {
        return deleteItem(Keys.AMMO_JSON, DefaultProfiles.starterAmmo, ammoId, { it.id }) { prefs, fallbackId ->
            if (prefs.activeAmmoId == ammoId) prefs.copy(activeAmmoId = fallbackId) else prefs
        }
    }

    private suspend inline fun <reified T> upsertItem(
        key: Preferences.Key<String>,
        fallback: List<T>,
        item: T,
        crossinline idOf: (T) -> String
    ) {
        context.ballisticsDataStore.edit { prefs ->
            val currentList = decodeListOrDefault(prefs[key], fallback)
            val targetId = idOf(item)
            val updated = if (currentList.any { idOf(it) == targetId }) {
                currentList.map { if (idOf(it) == targetId) item else it }
            } else {
                currentList + item
            }
            prefs[key] = json.encodeToString(updated)
        }
    }

    private suspend inline fun <reified T> deleteItem(
        key: Preferences.Key<String>,
        fallback: List<T>,
        targetId: String,
        crossinline idOf: (T) -> String,
        crossinline fixActivePref: (UserPreferences, String) -> UserPreferences
    ): Boolean {
        var deleted = false
        context.ballisticsDataStore.edit { prefs ->
            val currentList = decodeListOrDefault(prefs[key], fallback)
            val updated = currentList.filterNot { idOf(it) == targetId }
            if (updated.isNotEmpty() && updated.size < currentList.size) {
                deleted = true
                prefs[key] = json.encodeToString(updated)
                val currentPrefs = prefs[Keys.USER_PREFERENCES_JSON]?.let {
                    runCatching { json.decodeFromString<UserPreferences>(it) }.getOrNull()
                } ?: UserPreferences()
                prefs[Keys.USER_PREFERENCES_JSON] = json.encodeToString(
                    fixActivePref(currentPrefs, idOf(updated.first()))
                )
            }
        }
        return deleted
    }
}
