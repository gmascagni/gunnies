package com.gunnies.ballistics

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gunnies.ballistics.ble.BleConnectionState
import com.gunnies.ballistics.model.AppThemeMode
import com.gunnies.ballistics.ui.BallisticsViewModel
import com.gunnies.ballistics.ui.components.LogbookSheetContent
import com.gunnies.ballistics.ui.screens.PreferencesScreen
import com.gunnies.ballistics.ui.screens.ProfilesScreen
import com.gunnies.ballistics.ui.screens.RangeCardScreen
import com.gunnies.ballistics.ui.screens.RangefinderScreen
import com.gunnies.ballistics.ui.screens.SolverScreen
import com.gunnies.ballistics.ui.screens.StageCardScreen
import com.gunnies.ballistics.ui.screens.WeatherScreen
import com.gunnies.ballistics.ui.theme.GunniesTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BallisticsViewModel by viewModels()
    private var keepScreenAwakeEnabled: Boolean = true

    private fun applyKeepScreenAwake(enabled: Boolean, rootView: android.view.View? = null) {
        keepScreenAwakeEnabled = enabled
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        window.decorView.keepScreenOn = enabled
        rootView?.keepScreenOn = enabled
    }

    override fun onResume() {
        super.onResume()
        applyKeepScreenAwake(keepScreenAwakeEnabled)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyKeepScreenAwake(keepScreenAwakeEnabled)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyKeepScreenAwake(true)

        setContent {
            val prefs by viewModel.preferencesFlow.collectAsStateWithLifecycle()
            val view = LocalView.current

            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                val isLight = prefs.themeMode == AppThemeMode.DAYLIGHT
                controller.isAppearanceLightStatusBars = isLight
                controller.isAppearanceLightNavigationBars = isLight
                applyKeepScreenAwake(prefs.keepScreenAwake, view)
            }

            DisposableEffect(view, prefs.keepScreenAwake) {
                applyKeepScreenAwake(prefs.keepScreenAwake, view)
                onDispose {
                    if (!prefs.keepScreenAwake) {
                        applyKeepScreenAwake(false, view)
                    }
                }
            }

            GunniesTheme(
                themeMode = prefs.themeMode,
                textScale = prefs.textScale
            ) {
                GunniesAppRoot(viewModel = viewModel)
            }
        }
    }
}

private enum class AppRoute(val label: String, val icon: ImageVector) {
    SOLVER("Solver", Icons.Default.GpsFixed),
    STAGE_CARD("Stage", Icons.Default.Flag),
    RANGE_CARD("DOPE", Icons.AutoMirrored.Filled.ListAlt),
    WEATHER("Weather", Icons.Default.Cloud),
    SETUP("Setup", Icons.Default.Tune),
    RANGEFINDER("LBC1800", Icons.Default.Bluetooth),
    SETTINGS("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GunniesAppRoot(viewModel: BallisticsViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedRouteOrdinal by rememberSaveable { mutableIntStateOf(AppRoute.SOLVER.ordinal) }
    var showLogbookSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingConnectTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val bleScanPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            viewModel.bleManager.startScan()
        }
    }

    val bleConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val target = pendingConnectTarget
        pendingConnectTarget = null
        if (results.values.all { it } && target != null) {
            viewModel.connectBleDevice(target.first, target.second)
        }
    }

    val weatherPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            viewModel.fetchLiveWeather(autoSyncRangefinder = true)
        }
    }

    fun requestBleScanWithPermissions() {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            viewModel.bleManager.startScan()
        } else {
            bleScanPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    fun requestBleConnectWithPermissions(address: String, name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val missing = listOf(Manifest.permission.BLUETOOTH_CONNECT).filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                pendingConnectTarget = address to name
                bleConnectPermissionLauncher.launch(missing.toTypedArray())
                return
            }
        }
        viewModel.connectBleDevice(address, name)
    }

    fun requestWeatherWithPermissions() {
        val needed = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val hasAny = needed.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (hasAny) {
            viewModel.fetchLiveWeather(autoSyncRangefinder = true)
        } else {
            weatherPermissionLauncher.launch(needed)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
        viewModel.clearSnackbar()
    }

    val currentRoute = AppRoute.entries[selectedRouteOrdinal]
    val isBleConnected = uiState.bleState.connectionState == BleConnectionState.CONNECTED

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    ),
                    title = {
                        Text(
                            text = when (currentRoute) {
                                AppRoute.SOLVER -> "Gunnies Ballistics"
                                AppRoute.STAGE_CARD -> "PRS Stage Card"
                                AppRoute.RANGE_CARD -> "DOPE Range Card"
                                AppRoute.WEATHER -> "GPS Weather & Wind"
                                AppRoute.SETUP -> "Arsenal Setup"
                                AppRoute.RANGEFINDER -> "Bone Collector 1800"
                                AppRoute.SETTINGS -> "Preferences"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1
                        )
                    },
                    actions = {
                        // Global BLE Status Pill
                        Surface(
                            onClick = { selectedRouteOrdinal = AppRoute.RANGEFINDER.ordinal },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isBleConnected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            color = if (isBleConnected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isBleConnected) MaterialTheme.colorScheme.secondary
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                )
                                Spacer(Modifier.width(5.dp))
                                Icon(
                                    imageVector = if (isBleConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                                    contentDescription = "Rangefinder",
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isBleConnected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = if (isBleConnected) "1800" else "BLE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Cold Bore & Range Session Logbook Drawer
                        IconButton(onClick = { showLogbookSheet = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = "Dope Logbook",
                                tint = if (uiState.sessionLogs.isNotEmpty()) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        // 1-Tap Field Theme / Night Red Cycle
                        IconButton(onClick = { viewModel.cycleThemeMode() }) {
                            Icon(
                                imageVector = Icons.Default.Brightness6,
                                contentDescription = "Cycle Field Theme",
                                tint = if (uiState.preferences.themeMode == AppThemeMode.NIGHT_RED) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        // Dedicated Preferences Route
                        IconButton(onClick = { selectedRouteOrdinal = AppRoute.SETTINGS.ordinal }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Preferences",
                                tint = if (currentRoute == AppRoute.SETTINGS) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!isExpandedScreen) {
                    val coverTabs = listOf(
                        AppRoute.SOLVER,
                        AppRoute.STAGE_CARD,
                        AppRoute.RANGE_CARD,
                        AppRoute.WEATHER,
                        AppRoute.SETUP
                    )
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        coverTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab,
                                onClick = { selectedRouteOrdinal = tab.ordinal },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    val railRoutes = listOf(
                        AppRoute.SOLVER,
                        AppRoute.STAGE_CARD,
                        AppRoute.RANGE_CARD,
                        AppRoute.WEATHER,
                        AppRoute.SETUP,
                        AppRoute.RANGEFINDER
                    )
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Start + WindowInsetsSides.Vertical
                        ),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.weight(0.15f))
                        railRoutes.forEach { route ->
                            NavigationRailItem(
                                selected = currentRoute == route,
                                onClick = { selectedRouteOrdinal = route.ordinal },
                                icon = { Icon(route.icon, contentDescription = route.label) },
                                label = { Text(route.label, maxLines = 1) }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        NavigationRailItem(
                            selected = currentRoute == AppRoute.SETTINGS,
                            onClick = { selectedRouteOrdinal = AppRoute.SETTINGS.ordinal },
                            icon = { Icon(AppRoute.SETTINGS.icon, contentDescription = "Prefs") },
                            label = { Text("Prefs", maxLines = 1) }
                        )
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    when (currentRoute) {
                        AppRoute.SOLVER -> SolverScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onRequestLocationWeather = { requestWeatherWithPermissions() },
                            onOpenWeatherTab = { selectedRouteOrdinal = AppRoute.WEATHER.ordinal },
                            onOpenRangefinderTab = { selectedRouteOrdinal = AppRoute.RANGEFINDER.ordinal },
                            onOpenLogbook = { showLogbookSheet = true }
                        )

                        AppRoute.STAGE_CARD -> StageCardScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onSendTargetToSolver = { target ->
                                viewModel.setRangeYards(target.rangeYards)
                                viewModel.setInclinationDeg(target.inclinationDeg)
                                viewModel.setMoverSpeedMph(target.moverSpeedMph)
                                selectedRouteOrdinal = AppRoute.SOLVER.ordinal
                            }
                        )

                        AppRoute.RANGE_CARD -> RangeCardScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onSelectRangeYards = { rangeYd ->
                                viewModel.setRangeYards(rangeYd)
                                selectedRouteOrdinal = AppRoute.SOLVER.ordinal
                            }
                        )

                        AppRoute.WEATHER -> WeatherScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onRequestLocationWeather = { requestWeatherWithPermissions() }
                        )

                        AppRoute.SETUP -> ProfilesScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )

                        AppRoute.RANGEFINDER -> RangefinderScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onRequestBleScan = { requestBleScanWithPermissions() },
                            onRequestBleConnect = { addr, name -> requestBleConnectWithPermissions(addr, name) }
                        )

                        AppRoute.SETTINGS -> PreferencesScreen(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }

        if (showLogbookSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showLogbookSheet = false },
                sheetState = sheetState
            ) {
                LogbookSheetContent(
                    uiState = uiState,
                    onDeleteLog = { viewModel.deleteSessionLog(it) },
                    onDone = { showLogbookSheet = false }
                )
            }
        }
    }
}
