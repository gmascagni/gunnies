package com.bc1800.ballistics.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bc1800.ballistics.ble.BleUiState
import com.bc1800.ballistics.ble.RangefinderBleManager
import com.bc1800.ballistics.data.BallisticsRepository
import com.bc1800.ballistics.data.WeatherService
import com.bc1800.ballistics.engine.BallisticEngine
import com.bc1800.ballistics.model.AmmoLoad
import com.bc1800.ballistics.model.AngularUnit
import com.bc1800.ballistics.model.AppThemeMode
import com.bc1800.ballistics.model.Atmosphere
import com.bc1800.ballistics.model.BallisticSolution
import com.bc1800.ballistics.model.DefaultProfiles
import com.bc1800.ballistics.model.DistanceUnit
import com.bc1800.ballistics.model.FirearmSetup
import com.bc1800.ballistics.model.PressureUnit
import com.bc1800.ballistics.model.RifleProfile
import com.bc1800.ballistics.model.ScopeSetup
import com.bc1800.ballistics.model.SuppressorSetup
import com.bc1800.ballistics.model.TemperatureUnit
import com.bc1800.ballistics.model.UiTextScale
import com.bc1800.ballistics.model.UserPreferences
import com.bc1800.ballistics.model.VelocityUnit
import com.bc1800.ballistics.model.WindUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

data class SolverInputState(
    val rangeYards: Double = 100.0,
    val inclinationDeg: Double = 0.0,
    val moverSpeedMph: Double = 0.0,
    val atmosphere: Atmosphere = Atmosphere(),
    val weatherStatus: String? = null,
    val isFetchingWeather: Boolean = false
)

data class AppUiState(
    val preferences: UserPreferences = UserPreferences(),
    val firearms: List<FirearmSetup> = DefaultProfiles.starterFirearms,
    val scopes: List<ScopeSetup> = DefaultProfiles.starterScopes,
    val ammoLoads: List<AmmoLoad> = DefaultProfiles.starterAmmo,
    val suppressors: List<SuppressorSetup> = DefaultProfiles.starterSuppressors,
    val activeFirearm: FirearmSetup = DefaultProfiles.starterFirearms.first(),
    val activeScope: ScopeSetup = DefaultProfiles.starterScopes.first(),
    val activeAmmo: AmmoLoad = DefaultProfiles.starterAmmo.first(),
    val activeSuppressor: SuppressorSetup? = DefaultProfiles.starterSuppressors.firstOrNull(),
    val activeProfile: RifleProfile = RifleProfile.combine(
        DefaultProfiles.starterFirearms.first(),
        DefaultProfiles.starterScopes.first(),
        DefaultProfiles.starterAmmo.first(),
        DefaultProfiles.starterSuppressors.firstOrNull()
    ),
    val solverInput: SolverInputState = SolverInputState(),
    val solution: BallisticSolution? = null,
    val mpbrYards: Double = 150.0,
    val rangeCard: List<BallisticSolution> = emptyList(),
    val bleState: BleUiState = BleUiState(),
    val snackbarMessage: String? = null
)

private data class CoreSetupData(
    val prefs: UserPreferences,
    val firearms: List<FirearmSetup>,
    val scopes: List<ScopeSetup>,
    val ammoLoads: List<AmmoLoad>,
    val suppressors: List<SuppressorSetup>
)

private data class BallisticCalcRequest(
    val activeProfile: RifleProfile,
    val atmosphere: Atmosphere,
    val rangeYards: Double,
    val inclinationDeg: Double,
    val moverSpeedMph: Double,
    val distanceUnit: DistanceUnit,
    val stepUnits: Int,
    val maxUnits: Int,
    val includeSpinDrift: Boolean,
    val includeCoriolis: Boolean,
    val includeAeroJump: Boolean
)

private data class BallisticCalcOutput(
    val solution: BallisticSolution,
    val mpbrYards: Double,
    val rangeCard: List<BallisticSolution>
)

class BallisticsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BallisticsRepository(application.applicationContext)
    val bleManager = RangefinderBleManager(application.applicationContext)

    private val _solverInput = MutableStateFlow(SolverInputState())
    private val _snackbar = MutableStateFlow<String?>(null)
    private var userModifiedAtmo = false

    val preferencesFlow: StateFlow<UserPreferences> = repository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserPreferences()
    )

    val bleStateFlow: StateFlow<BleUiState> = bleManager.state
    val snackbarFlow: StateFlow<String?> = _snackbar.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureCustomArsenalSeeded()
            val savedPrefs = repository.preferencesFlow.first()
            if (!userModifiedAtmo) {
                _solverInput.update { it.copy(atmosphere = savedPrefs.defaultAtmosphere) }
            }
        }

        bleManager.setOnReadingListener { reading ->
            _solverInput.update { current ->
                current.copy(
                    rangeYards = reading.distanceYards.coerceIn(5.0, 2000.0),
                    inclinationDeg = reading.angleDegrees?.coerceIn(-75.0, 75.0) ?: current.inclinationDeg
                )
            }
            showMessage("Rangefinder: ${reading.distanceYards.toInt()} yd")
        }
    }

    private val coreDataFlow = combine(
        repository.preferencesFlow,
        repository.firearmsFlow,
        repository.scopesFlow,
        repository.ammoFlow,
        repository.suppressorsFlow
    ) { prefs, firearms, scopes, ammo, suppressors ->
        CoreSetupData(
            prefs = prefs,
            firearms = firearms.sortedByDescending { it.isFavorite },
            scopes = scopes.sortedByDescending { it.isFavorite },
            ammoLoads = ammo.sortedByDescending { it.isFavorite },
            suppressors = suppressors.sortedByDescending { it.isFavorite }
        )
    }

    // Isolated physics pipeline running on Dispatchers.Default — never triggered by BLE packets, themes, or snackbars!
    private val ballisticsCalcFlow = combine(
        coreDataFlow,
        _solverInput
    ) { core, input ->
        val activeFirearm = core.firearms.firstOrNull { it.id == core.prefs.activeFirearmId } ?: core.firearms.first()
        val activeScope = core.scopes.firstOrNull { it.id == core.prefs.activeScopeId } ?: core.scopes.first()
        val activeAmmo = core.ammoLoads.firstOrNull { it.id == core.prefs.activeAmmoId } ?: core.ammoLoads.first()
        val activeSuppressor = core.prefs.activeSuppressorId?.let { id ->
            core.suppressors.firstOrNull { it.id == id }
        }
        val combinedProfile = RifleProfile.combine(activeFirearm, activeScope, activeAmmo, activeSuppressor)

        BallisticCalcRequest(
            activeProfile = combinedProfile,
            atmosphere = input.atmosphere,
            rangeYards = input.rangeYards,
            inclinationDeg = input.inclinationDeg,
            moverSpeedMph = input.moverSpeedMph,
            distanceUnit = core.prefs.distanceUnit,
            stepUnits = core.prefs.rangeCardStepYards,
            maxUnits = core.prefs.rangeCardMaxYards,
            includeSpinDrift = core.prefs.includeSpinDrift,
            includeCoriolis = core.prefs.includeCoriolis,
            includeAeroJump = core.prefs.includeAeroJump
        )
    }
        .distinctUntilChanged()
        .map { req ->
            val options = BallisticEngine.SolverOptions(
                includeSpinDrift = req.includeSpinDrift,
                includeCoriolis = req.includeCoriolis,
                includeAeroJump = req.includeAeroJump,
                moverSpeedMph = req.moverSpeedMph
            )
            val zeroAngle = BallisticEngine.computeZeroAngleRad(req.activeProfile)
            val sol = BallisticEngine.solve(
                profile = req.activeProfile,
                atmosphere = req.atmosphere,
                rangeYards = req.rangeYards,
                inclinationDeg = req.inclinationDeg,
                cachedZeroAngleRad = zeroAngle,
                options = options
            )
            val mpbr = BallisticEngine.computeMpbrYards(
                profile = req.activeProfile,
                atmosphere = req.atmosphere,
                cachedZeroAngleRad = zeroAngle
            )
            val card = BallisticEngine.generateRangeCard(
                profile = req.activeProfile,
                atmosphere = req.atmosphere,
                stepUnits = req.stepUnits,
                maxUnits = req.maxUnits,
                distanceUnit = req.distanceUnit,
                inclinationDeg = req.inclinationDeg,
                cachedZeroAngleRad = zeroAngle,
                options = options
            )
            BallisticCalcOutput(sol, mpbr, card)
        }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<AppUiState> = combine(
        coreDataFlow,
        _solverInput,
        ballisticsCalcFlow,
        bleManager.state,
        _snackbar
    ) { core, input, calc, ble, snackbar ->
        val activeFirearm = core.firearms.firstOrNull { it.id == core.prefs.activeFirearmId } ?: core.firearms.first()
        val activeScope = core.scopes.firstOrNull { it.id == core.prefs.activeScopeId } ?: core.scopes.first()
        val activeAmmo = core.ammoLoads.firstOrNull { it.id == core.prefs.activeAmmoId } ?: core.ammoLoads.first()
        val activeSuppressor = core.prefs.activeSuppressorId?.let { id ->
            core.suppressors.firstOrNull { it.id == id }
        }
        val combinedProfile = RifleProfile.combine(activeFirearm, activeScope, activeAmmo, activeSuppressor)

        AppUiState(
            preferences = core.prefs,
            firearms = core.firearms,
            scopes = core.scopes,
            ammoLoads = core.ammoLoads,
            suppressors = core.suppressors,
            activeFirearm = activeFirearm,
            activeScope = activeScope,
            activeAmmo = activeAmmo,
            activeSuppressor = activeSuppressor,
            activeProfile = combinedProfile,
            solverInput = input,
            solution = calc.solution,
            mpbrYards = calc.mpbrYards,
            rangeCard = calc.rangeCard,
            bleState = ble,
            snackbarMessage = snackbar
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppUiState()
    )

    fun clearSnackbar() {
        _snackbar.value = null
    }

    private fun showMessage(msg: String) {
        _snackbar.value = msg
    }

    // --- Solver Controls ---

    fun setRangeYards(yards: Double) {
        _solverInput.update { it.copy(rangeYards = yards.coerceIn(5.0, 1800.0)) }
    }

    fun nudgeRangeYards(deltaYards: Double) {
        _solverInput.update {
            it.copy(rangeYards = (it.rangeYards + deltaYards).coerceIn(5.0, 1800.0))
        }
    }

    fun setInclinationDeg(degrees: Double) {
        _solverInput.update { it.copy(inclinationDeg = degrees.coerceIn(-60.0, 60.0)) }
    }

    fun setMoverSpeedMph(mph: Double) {
        _solverInput.update { it.copy(moverSpeedMph = mph.coerceIn(0.0, 30.0)) }
    }

    fun updateAtmosphere(transform: (Atmosphere) -> Atmosphere) {
        userModifiedAtmo = true
        _solverInput.update { it.copy(atmosphere = transform(it.atmosphere)) }
    }

    fun setShootingBearingDeg(bearingDeg: Double) {
        val normBearing = ((bearingDeg % 360.0) + 360.0) % 360.0
        userModifiedAtmo = true
        _solverInput.update { current ->
            val atmo = current.atmosphere
            val newRelWind = if (!atmo.manualWindOverride && atmo.meteoWindFromDeg != null) {
                WeatherService.relativeWindClockDeg(atmo.meteoWindFromDeg, normBearing)
            } else {
                atmo.windDirectionDeg
            }
            current.copy(
                atmosphere = atmo.copy(
                    shootingBearingDeg = normBearing,
                    windDirectionDeg = newRelWind
                )
            )
        }
    }

    fun setManualWindOverride(enabled: Boolean) {
        userModifiedAtmo = true
        _solverInput.update { current ->
            val atmo = current.atmosphere
            val nextRelWind = if (!enabled && atmo.meteoWindFromDeg != null) {
                WeatherService.relativeWindClockDeg(atmo.meteoWindFromDeg, atmo.shootingBearingDeg)
            } else {
                atmo.windDirectionDeg
            }
            current.copy(
                atmosphere = atmo.copy(
                    manualWindOverride = enabled,
                    windDirectionDeg = nextRelWind
                )
            )
        }
        showMessage(if (enabled) "Wind: Manual Override" else "Wind: GPS Auto")
    }

    fun setManualWindDirectionDeg(clockDeg: Double) {
        val normClock = ((clockDeg % 360.0) + 360.0) % 360.0
        userModifiedAtmo = true
        _solverInput.update { current ->
            current.copy(
                atmosphere = current.atmosphere.copy(
                    windDirectionDeg = normClock,
                    manualWindOverride = true
                )
            )
        }
    }

    fun setManualWindFromCompassDeg(compassDeg: Double) {
        val normCompass = ((compassDeg % 360.0) + 360.0) % 360.0
        userModifiedAtmo = true
        _solverInput.update { current ->
            val atmo = current.atmosphere
            val relClock = WeatherService.relativeWindClockDeg(normCompass, atmo.shootingBearingDeg)
            current.copy(
                atmosphere = atmo.copy(
                    windDirectionDeg = relClock,
                    manualWindOverride = true
                )
            )
        }
    }

    fun saveCurrentAtmosphereAsDefault() {
        val currentAtmo = _solverInput.value.atmosphere
        viewModelScope.launch {
            repository.updatePreferences { it.copy(defaultAtmosphere = currentAtmo) }
            showMessage("Default atmosphere saved")
        }
    }

    fun fetchLiveWeather() {
        viewModelScope.launch {
            _solverInput.update { it.copy(isFetchingWeather = true, weatherStatus = "Acquiring GPS & local weather...") }
            val result = WeatherService.fetchLocalAtmosphere(
                context = getApplication(),
                shootingBearingDeg = _solverInput.value.atmosphere.shootingBearingDeg
            )
            result.onSuccess { weather ->
                userModifiedAtmo = true
                var finalAtmo = weather.atmosphere
                var finalSummary = weather.summary
                _solverInput.update { current ->
                    val prev = current.atmosphere
                    val merged = if (prev.manualWindOverride) {
                        weather.atmosphere.copy(
                            windSpeedMph = prev.windSpeedMph,
                            windGustMph = prev.windGustMph,
                            windDirectionDeg = prev.windDirectionDeg,
                            shootingBearingDeg = prev.shootingBearingDeg,
                            manualWindOverride = true
                        )
                    } else {
                        weather.atmosphere
                    }
                    finalAtmo = merged
                    finalSummary = if (prev.manualWindOverride) "${weather.summary} • Manual wind kept" else weather.summary
                    current.copy(
                        atmosphere = merged,
                        isFetchingWeather = false,
                        weatherStatus = finalSummary
                    )
                }
                repository.updatePreferences { it.copy(defaultAtmosphere = finalAtmo) }
                showMessage(finalSummary)
            }.onFailure { err ->
                _solverInput.update {
                    it.copy(
                        isFetchingWeather = false,
                        weatherStatus = err.message ?: "Weather fetch failed"
                    )
                }
                showMessage(err.message ?: "Could not fetch local weather")
            }
        }
    }

    /** Applies a trued Muzzle Velocity directly onto the active Ammo load (backing out any active suppressor MV shift) and persists it. */
    fun applyTruedMuzzleVelocity(truedMvFps: Double) {
        val current = uiState.value
        val activeAmmo = current.activeAmmo
        val suppShift = current.activeSuppressor?.mvShiftFps ?: 0.0
        val roundedBaseMv = (truedMvFps - suppShift).roundToInt().toDouble().coerceIn(200.0, 5500.0)
        viewModelScope.launch {
            repository.saveAmmo(activeAmmo.copy(muzzleVelocityFps = roundedBaseMv))
            showMessage("Trued MV saved: ${roundedBaseMv.toInt()} fps for ${activeAmmo.name}")
        }
    }

    // --- Firearm Management (Scopes remain independent across guns) ---

    fun selectActiveFirearm(firearmId: String) {
        viewModelScope.launch {
            val current = uiState.value
            val targetGun = current.firearms.firstOrNull { it.id == firearmId } ?: return@launch
            val remAmmoId = current.preferences.firearmAmmoMemory[firearmId]
                ?.takeIf { id -> current.ammoLoads.any { it.id == id } }

            repository.updatePreferences { prefs ->
                val nextAmmoId = remAmmoId ?: prefs.activeAmmoId
                prefs.copy(
                    activeFirearmId = firearmId,
                    activeAmmoId = nextAmmoId
                )
            }
            showMessage("Firearm: ${targetGun.name}")
        }
    }

    fun toggleFirearmFavorite(firearm: FirearmSetup) {
        saveFirearm(firearm.copy(isFavorite = !firearm.isFavorite), makeActive = false)
    }

    fun saveFirearm(firearm: FirearmSetup, makeActive: Boolean = false) {
        viewModelScope.launch {
            repository.saveFirearm(firearm)
            if (makeActive) {
                repository.updatePreferences { it.copy(activeFirearmId = firearm.id) }
            }
            showMessage("Saved firearm: ${firearm.name}")
        }
    }

    fun duplicateFirearm(firearm: FirearmSetup) {
        saveFirearm(firearm.copy(id = UUID.randomUUID().toString(), name = "${firearm.name} (Copy)"), makeActive = true)
    }

    fun deleteFirearm(firearmId: String) {
        viewModelScope.launch {
            if (repository.deleteFirearm(firearmId)) {
                showMessage("Firearm deleted")
            } else {
                showMessage("At least one firearm must remain")
            }
        }
    }

    // --- Scope / Optic Management (Independent across guns + auto-matches MOA/MRAD unit) ---

    fun selectActiveScope(scopeId: String) {
        viewModelScope.launch {
            val current = uiState.value
            val scope = current.scopes.firstOrNull { it.id == scopeId } ?: return@launch
            repository.updatePreferences { prefs ->
                val matchedUnit = if (prefs.autoMatchScopeUnit) {
                    if (scope.clickUnit.isMrad) AngularUnit.MRAD else AngularUnit.MOA
                } else prefs.angularUnit

                prefs.copy(
                    activeScopeId = scopeId,
                    angularUnit = matchedUnit
                )
            }
            showMessage("Scope: ${scope.name} (${scope.clickUnit.label})")
        }
    }

    fun toggleScopeFavorite(scope: ScopeSetup) {
        saveScope(scope.copy(isFavorite = !scope.isFavorite), makeActive = false)
    }

    fun saveScope(scope: ScopeSetup, makeActive: Boolean = false) {
        viewModelScope.launch {
            repository.saveScope(scope)
            if (makeActive) {
                repository.updatePreferences { prefs ->
                    val matchedUnit = if (prefs.autoMatchScopeUnit) {
                        if (scope.clickUnit.isMrad) AngularUnit.MRAD else AngularUnit.MOA
                    } else prefs.angularUnit
                    prefs.copy(
                        activeScopeId = scope.id,
                        angularUnit = matchedUnit
                    )
                }
            }
            showMessage("Saved scope: ${scope.name}")
        }
    }

    fun duplicateScope(scope: ScopeSetup) {
        saveScope(scope.copy(id = UUID.randomUUID().toString(), name = "${scope.name} (Copy)"), makeActive = true)
    }

    fun deleteScope(scopeId: String) {
        viewModelScope.launch {
            if (repository.deleteScope(scopeId)) {
                showMessage("Scope deleted")
            } else {
                showMessage("At least one scope must remain")
            }
        }
    }

    // --- Ammo / Bullet Management ---

    fun selectActiveAmmo(ammoId: String) {
        viewModelScope.launch {
            val ammo = uiState.value.ammoLoads.firstOrNull { it.id == ammoId } ?: return@launch
            repository.updatePreferences { prefs ->
                prefs.copy(
                    activeAmmoId = ammoId,
                    firearmAmmoMemory = prefs.firearmAmmoMemory + (prefs.activeFirearmId to ammoId)
                )
            }
            showMessage("Ammo: ${ammo.name}")
        }
    }

    fun toggleAmmoFavorite(ammo: AmmoLoad) {
        saveAmmo(ammo.copy(isFavorite = !ammo.isFavorite), makeActive = false)
    }

    fun saveAmmo(ammo: AmmoLoad, makeActive: Boolean = false) {
        viewModelScope.launch {
            repository.saveAmmo(ammo)
            if (makeActive) {
                repository.updatePreferences { prefs ->
                    prefs.copy(
                        activeAmmoId = ammo.id,
                        firearmAmmoMemory = prefs.firearmAmmoMemory + (prefs.activeFirearmId to ammo.id)
                    )
                }
            }
            showMessage("Saved ammo: ${ammo.name}")
        }
    }

    fun duplicateAmmo(ammo: AmmoLoad) {
        saveAmmo(ammo.copy(id = UUID.randomUUID().toString(), name = "${ammo.name} (Copy)"), makeActive = true)
    }

    fun deleteAmmo(ammoId: String) {
        viewModelScope.launch {
            if (repository.deleteAmmo(ammoId)) {
                showMessage("Ammo deleted")
            } else {
                showMessage("At least one ammo load must remain")
            }
        }
    }

    // --- Suppressor Management ---

    fun selectActiveSuppressor(suppressorId: String?) {
        viewModelScope.launch {
            val target = suppressorId?.let { id ->
                uiState.value.suppressors.firstOrNull { it.id == id } ?: return@launch
            }
            repository.updatePreferences { it.copy(activeSuppressorId = target?.id) }
            showMessage(target?.let { "Suppressor: ${it.name}" } ?: "Unsuppressed (Bare Muzzle)")
        }
    }

    fun toggleSuppressorFavorite(suppressor: SuppressorSetup) {
        saveSuppressor(suppressor.copy(isFavorite = !suppressor.isFavorite), makeActive = false)
    }

    fun saveSuppressor(suppressor: SuppressorSetup, makeActive: Boolean = false) {
        viewModelScope.launch {
            repository.saveSuppressor(suppressor)
            if (makeActive) {
                repository.updatePreferences { it.copy(activeSuppressorId = suppressor.id) }
            }
            showMessage("Saved suppressor: ${suppressor.name}")
        }
    }

    fun duplicateSuppressor(suppressor: SuppressorSetup) {
        saveSuppressor(
            suppressor.copy(id = UUID.randomUUID().toString(), name = "${suppressor.name} (Copy)"),
            makeActive = true
        )
    }

    fun deleteSuppressor(suppressorId: String) {
        viewModelScope.launch {
            if (repository.deleteSuppressor(suppressorId)) {
                showMessage("Suppressor deleted")
            }
        }
    }

    // --- Preferences (Auto-Persisted Immediately) ---

    fun setDistanceUnit(unit: DistanceUnit) = updatePref("Distance: ${unit.fullLabel}") {
        it.copy(distanceUnit = unit)
    }

    fun setAngularUnit(unit: AngularUnit) = updatePref("Hold unit: ${unit.fullLabel}") {
        it.copy(angularUnit = unit)
    }

    fun setAutoMatchScopeUnit(enabled: Boolean) = updatePref("Auto-match scope unit: ${if (enabled) "ON" else "OFF"}") {
        it.copy(autoMatchScopeUnit = enabled)
    }

    fun setVelocityUnit(unit: VelocityUnit) = updatePref("Velocity: ${unit.shortLabel}") {
        it.copy(velocityUnit = unit)
    }

    fun setTemperatureUnit(unit: TemperatureUnit) = updatePref("Temp: ${unit.shortLabel}") {
        it.copy(temperatureUnit = unit)
    }

    fun setPressureUnit(unit: PressureUnit) = updatePref("Pressure: ${unit.shortLabel}") {
        it.copy(pressureUnit = unit)
    }

    fun setWindUnit(unit: WindUnit) = updatePref("Wind: ${unit.shortLabel}") {
        it.copy(windUnit = unit)
    }

    fun setThemeMode(mode: AppThemeMode) = updatePref("Theme: ${mode.label}") {
        it.copy(themeMode = mode)
    }

    fun cycleThemeMode() {
        val modes = AppThemeMode.entries
        val current = preferencesFlow.value.themeMode
        val next = modes[(modes.indexOf(current) + 1) % modes.size]
        setThemeMode(next)
    }

    fun setTextScale(scale: UiTextScale) = updatePref("Text size: ${scale.label}") {
        it.copy(textScale = scale)
    }

    fun setKeepScreenAwake(enabled: Boolean) = updatePref("Keep screen awake: ${if (enabled) "ON" else "OFF"}") {
        it.copy(keepScreenAwake = enabled)
    }

    fun setIncludeSpinDrift(enabled: Boolean) = updatePref("Spin drift: ${if (enabled) "ON" else "OFF"}") {
        it.copy(includeSpinDrift = enabled)
    }

    fun setIncludeCoriolis(enabled: Boolean) = updatePref("Coriolis: ${if (enabled) "ON" else "OFF"}") {
        it.copy(includeCoriolis = enabled)
    }

    fun setIncludeAeroJump(enabled: Boolean) = updatePref("Aero jump: ${if (enabled) "ON" else "OFF"}") {
        it.copy(includeAeroJump = enabled)
    }

    fun setRangeCardStep(stepUnits: Int) = updatePref("Card step: $stepUnits") {
        it.copy(rangeCardStepYards = stepUnits)
    }

    fun setRangeCardMax(maxUnits: Int) = updatePref("Card max: $maxUnits") {
        it.copy(rangeCardMaxYards = maxUnits)
    }

    fun connectBleDevice(address: String, name: String) {
        bleManager.connect(address, name)
        viewModelScope.launch {
            repository.updatePreferences {
                it.copy(lastConnectedBleAddress = address, lastConnectedBleName = name)
            }
        }
    }

    fun syncActiveProfileToRangefinder() {
        val current = uiState.value
        bleManager.syncProfileToLbc1800(
            profile = current.activeProfile,
            atmosphere = current.solverInput.atmosphere,
            moverSpeedMph = current.solverInput.moverSpeedMph
        )
    }

    fun queryRangefinderInfo() {
        bleManager.queryLbc1800DeviceInfo()
    }

    private fun updatePref(toast: String, transform: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch {
            repository.updatePreferences(transform)
            showMessage(toast)
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.shutdown()
    }
}
