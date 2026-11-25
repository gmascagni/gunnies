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
    val activeFirearm: FirearmSetup = DefaultProfiles.starterFirearms.first(),
    val activeScope: ScopeSetup = DefaultProfiles.starterScopes.first(),
    val activeAmmo: AmmoLoad = DefaultProfiles.starterAmmo.first(),
    val activeProfile: RifleProfile = RifleProfile.combine(
        DefaultProfiles.starterFirearms.first(),
        DefaultProfiles.starterScopes.first(),
        DefaultProfiles.starterAmmo.first()
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
    val ammoLoads: List<AmmoLoad>
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
        repository.ammoFlow
    ) { prefs, firearms, scopes, ammo ->
        CoreSetupData(
            prefs = prefs,
            firearms = firearms.sortedByDescending { it.isFavorite },
            scopes = scopes.sortedByDescending { it.isFavorite },
            ammoLoads = ammo.sortedByDescending { it.isFavorite }
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
        val combinedProfile = RifleProfile.combine(activeFirearm, activeScope, activeAmmo)

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
        val combinedProfile = RifleProfile.combine(activeFirearm, activeScope, activeAmmo)

        AppUiState(
            preferences = core.prefs,
            firearms = core.firearms,
            scopes = core.scopes,
            ammoLoads = core.ammoLoads,
            activeFirearm = activeFirearm,
            activeScope = activeScope,
            activeAmmo = activeAmmo,
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
        userModifiedAtmo = true
        _solverInput.update { current ->
            val atmo = current.atmosphere
            val newRelWind = atmo.meteoWindFromDeg?.let { meteo ->
                WeatherService.relativeWindClockDeg(meteo, bearingDeg)
            } ?: atmo.windDirectionDeg
            current.copy(
                atmosphere = atmo.copy(
                    shootingBearingDeg = bearingDeg,
                    windDirectionDeg = newRelWind
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
                _solverInput.update {
                    it.copy(
                        atmosphere = weather.atmosphere,
                        isFetchingWeather = false,
                        weatherStatus = weather.summary
                    )
                }
                repository.updatePreferences { it.copy(defaultAtmosphere = weather.atmosphere) }
                showMessage(weather.summary)
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

    /** Applies a trued Muzzle Velocity directly onto the active Ammo load and persists it. */
    fun applyTruedMuzzleVelocity(truedMvFps: Double) {
        val activeAmmo = uiState.value.activeAmmo
        val roundedMv = truedMvFps.roundToInt().toDouble().coerceIn(200.0, 5500.0)
        viewModelScope.launch {
            repository.saveAmmo(activeAmmo.copy(muzzleVelocityFps = roundedMv))
            showMessage("Trued MV saved: ${roundedMv.toInt()} fps for ${activeAmmo.name}")
        }
    }

    // --- Firearm Management (Remembers Scope + Ammo pairing per gun!) ---

    fun selectActiveFirearm(firearmId: String) {
        viewModelScope.launch {
            val current = uiState.value
            val targetGun = current.firearms.firstOrNull { it.id == firearmId } ?: return@launch
            val remScopeId = current.preferences.firearmScopeMemory[firearmId]
                ?.takeIf { id -> current.scopes.any { it.id == id } }
            val remAmmoId = current.preferences.firearmAmmoMemory[firearmId]
                ?.takeIf { id -> current.ammoLoads.any { it.id == id } }

            repository.updatePreferences { prefs ->
                val nextScopeId = remScopeId ?: prefs.activeScopeId
                val nextAmmoId = remAmmoId ?: prefs.activeAmmoId
                val nextScope = current.scopes.firstOrNull { it.id == nextScopeId }
                val matchedUnit = if (prefs.autoMatchScopeUnit && nextScope != null) {
                    if (nextScope.clickUnit.isMrad) AngularUnit.MRAD else AngularUnit.MOA
                } else prefs.angularUnit

                prefs.copy(
                    activeFirearmId = firearmId,
                    activeScopeId = nextScopeId,
                    activeAmmoId = nextAmmoId,
                    angularUnit = matchedUnit
                )
            }
            showMessage("Active rig: ${targetGun.name}")
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

    // --- Scope / Optic Management (Remembers pairing + auto-matches MOA/MRAD unit) ---

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
                    angularUnit = matchedUnit,
                    firearmScopeMemory = prefs.firearmScopeMemory + (prefs.activeFirearmId to scopeId)
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
                        angularUnit = matchedUnit,
                        firearmScopeMemory = prefs.firearmScopeMemory + (prefs.activeFirearmId to scope.id)
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
