package com.gunnies.ballistics.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gunnies.ballistics.R
import com.gunnies.ballistics.data.BallisticsCatalog
import com.gunnies.ballistics.data.CustomDragCurveCodec
import com.gunnies.ballistics.data.ImportedProfileBundle
import com.gunnies.ballistics.data.ProfileImportCodec
import com.gunnies.ballistics.data.UnitFormatters
import com.gunnies.ballistics.model.AmmoCategory
import com.gunnies.ballistics.model.AmmoLoad
import com.gunnies.ballistics.model.AppThemeMode
import com.gunnies.ballistics.model.BcBand
import com.gunnies.ballistics.model.ClickUnit
import com.gunnies.ballistics.model.CustomDragCurve
import com.gunnies.ballistics.model.DragModel
import com.gunnies.ballistics.model.FirearmCategory
import com.gunnies.ballistics.model.FirearmSetup
import com.gunnies.ballistics.model.ReticleType
import com.gunnies.ballistics.model.ScopeCategory
import com.gunnies.ballistics.model.ScopeSetup
import com.gunnies.ballistics.model.SuppressorCategory
import com.gunnies.ballistics.model.SuppressorSetup
import com.gunnies.ballistics.model.TwistDirection
import com.gunnies.ballistics.ui.AppUiState
import com.gunnies.ballistics.ui.BallisticsViewModel
import com.gunnies.ballistics.ui.components.GunneryHudPanel
import com.gunnies.ballistics.ui.components.HardwareThumbBadge
import com.gunnies.ballistics.ui.components.RangefinderHelpView
import com.gunnies.ballistics.ui.components.rememberChamferedHudShape
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilesScreen(
    uiState: AppUiState,
    viewModel: BallisticsViewModel
) {
    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Guns, 1 = Scopes, 2 = Ammo, 3 = Cans
    var showRangefinderHelp by rememberSaveable { mutableStateOf(false) }
    var viewDatabaseMode by rememberSaveable { mutableStateOf(false) }
    var dbSearchQuery by rememberSaveable { mutableStateOf("") }
    var dbQuickTag by remember(selectedSubTab) { mutableStateOf("All") }

    var editingGun by remember { mutableStateOf<FirearmSetup?>(null) }
    var isCreatingGun by remember { mutableStateOf(false) }
    var pendingDeleteGun by remember { mutableStateOf<FirearmSetup?>(null) }
    var gunFilter by remember { mutableStateOf("★ Favorites") }

    var editingScope by remember { mutableStateOf<ScopeSetup?>(null) }
    var isCreatingScope by remember { mutableStateOf(false) }
    var pendingDeleteScope by remember { mutableStateOf<ScopeSetup?>(null) }
    var scopeFilter by remember { mutableStateOf("All") }

    var editingAmmo by remember { mutableStateOf<AmmoLoad?>(null) }
    var isCreatingAmmo by remember { mutableStateOf(false) }
    var pendingDeleteAmmo by remember { mutableStateOf<AmmoLoad?>(null) }
    var ammoFilter by remember { mutableStateOf("All") }

    var editingSuppressor by remember { mutableStateOf<SuppressorSetup?>(null) }
    var isCreatingSuppressor by remember { mutableStateOf(false) }
    var pendingDeleteSuppressor by remember { mutableStateOf<SuppressorSetup?>(null) }
    var suppressorFilter by remember { mutableStateOf("All") }

    val filteredGuns = remember(uiState.firearms, gunFilter) {
        when (gunFilter) {
            "★ Favorites" -> uiState.firearms.filter { it.isFavorite }.ifEmpty { uiState.firearms }
            "Rifles" -> uiState.firearms.filter { it.category == FirearmCategory.RIFLE }
            "Handguns" -> uiState.firearms.filter { it.category == FirearmCategory.HANDGUN }
            else -> uiState.firearms
        }
    }

    val filteredScopes = remember(uiState.scopes, scopeFilter) {
        when (scopeFilter) {
            "★ Favorites" -> uiState.scopes.filter { it.isFavorite }.ifEmpty { uiState.scopes }
            "Rifle Scope" -> uiState.scopes.filter { it.category == ScopeCategory.RIFLE_SCOPE }
            "Red Dot / Iron" -> uiState.scopes.filter { it.category == ScopeCategory.RED_DOT_IRON }
            "Handgun" -> uiState.scopes.filter { it.category == ScopeCategory.HANDGUN_OPTIC }
            else -> uiState.scopes
        }
    }

    val filteredAmmo = remember(uiState.ammoLoads, ammoFilter) {
        when (ammoFilter) {
            "★ Favorites" -> uiState.ammoLoads.filter { it.isFavorite }.ifEmpty { uiState.ammoLoads }
            "Rimfire (.22)" -> uiState.ammoLoads.filter { it.category == AmmoCategory.RIMFIRE }
            "Rifle" -> uiState.ammoLoads.filter { it.category == AmmoCategory.RIFLE }
            "Handgun" -> uiState.ammoLoads.filter { it.category == AmmoCategory.HANDGUN }
            else -> uiState.ammoLoads
        }
    }

    val filteredSuppressors = remember(uiState.suppressors, suppressorFilter) {
        when (suppressorFilter) {
            "★ Favorites" -> uiState.suppressors.filter { it.isFavorite }.ifEmpty { uiState.suppressors }
            "Rimfire (.22)" -> uiState.suppressors.filter { it.category == SuppressorCategory.RIMFIRE }
            "Rifle" -> uiState.suppressors.filter { it.category == SuppressorCategory.RIFLE }
            "Pistol / Subgun" -> uiState.suppressors.filter { it.category == SuppressorCategory.PISTOL }
            "Multi-Caliber" -> uiState.suppressors.filter { it.category == SuppressorCategory.MULTI_CAL }
            else -> uiState.suppressors
        }
    }

    var pendingProfileImport by remember { mutableStateOf<ImportedProfileBundle?>(null) }
    var profileImportError by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    val profileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Unable to read selected file.")
                ProfileImportCodec.parse(text, uri.lastPathSegment).getOrThrow()
            }.onSuccess { bundle ->
                profileImportError = null
                pendingProfileImport = bundle
            }.onFailure { err ->
                profileImportError = err.message ?: "Unable to parse profile file."
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp
        val columns = if (isWide) 2 else 1
        val isNightRed = uiState.preferences.themeMode == AppThemeMode.NIGHT_RED

        if (showRangefinderHelp) {
            RangefinderHelpView(
                isNightRed = isNightRed,
                onBack = { showRangefinderHelp = false }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Active Rig Gunnery Strip + LBC1800 Display Help Pill
                val gunThumb = if (uiState.activeFirearm.category == FirearmCategory.HANDGUN) {
                    R.drawable.img_cat_handgun
                } else {
                    R.drawable.img_cat_rifle
                }
                GunneryHudPanel(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    bracketColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f),
                    cutSize = 8.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HardwareThumbBadge(
                            drawableResId = gunThumb,
                            contentDescription = uiState.activeFirearm.name,
                            isNightRed = isNightRed,
                            size = 44.dp,
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ACTIVE RIG",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        onClick = { profileImportLauncher.launch(arrayOf("*/*")) },
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.FileUpload,
                                                contentDescription = "Import Strelok Profile",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "Import",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Surface(
                                        onClick = { showRangefinderHelp = true },
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.HelpOutline,
                                                contentDescription = "Rangefinder Help",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Help",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = "${uiState.activeFirearm.name}  •  ${uiState.activeScope.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val suppLabel = uiState.activeSuppressor?.name ?: "Unsuppressed"
                            Text(
                                text = "${uiState.activeAmmo.name}  •  Can: $suppLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

            // Segmented Switcher + My Arsenal / Full Database Mode Switch
            val dbCount = when (selectedSubTab) {
                0 -> BallisticsCatalog.firearms.size
                1 -> BallisticsCatalog.scopes.size
                2 -> BallisticsCatalog.ammo.size
                else -> BallisticsCatalog.suppressors.size
            }
            val savedCount = when (selectedSubTab) {
                0 -> uiState.firearms.size
                1 -> uiState.scopes.size
                2 -> uiState.ammoLoads.size
                else -> uiState.suppressors.size
            }

            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1.25f)) {
                        SegmentedButton(
                            selected = selectedSubTab == 0,
                            onClick = { selectedSubTab = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4)
                        ) { Text("Guns (${uiState.firearms.size})", maxLines = 1) }
                        SegmentedButton(
                            selected = selectedSubTab == 1,
                            onClick = { selectedSubTab = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4)
                        ) { Text("Scopes (${uiState.scopes.size})", maxLines = 1) }
                        SegmentedButton(
                            selected = selectedSubTab == 2,
                            onClick = { selectedSubTab = 2 },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4)
                        ) { Text("Ammo (${uiState.ammoLoads.size})", maxLines = 1) }
                        SegmentedButton(
                            selected = selectedSubTab == 3,
                            onClick = { selectedSubTab = 3 },
                            shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4)
                        ) { Text("Cans (${uiState.suppressors.size})", maxLines = 1) }
                    }

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(0.75f)) {
                        SegmentedButton(
                            selected = !viewDatabaseMode,
                            onClick = { viewDatabaseMode = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("My Arsenal ($savedCount)", maxLines = 1) }
                        SegmentedButton(
                            selected = viewDatabaseMode,
                            onClick = { viewDatabaseMode = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("Database ($dbCount)", maxLines = 1) }
                    }
                }
            } else {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    SegmentedButton(
                        selected = selectedSubTab == 0,
                        onClick = { selectedSubTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4)
                    ) { Text("Guns", maxLines = 1) }
                    SegmentedButton(
                        selected = selectedSubTab == 1,
                        onClick = { selectedSubTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4)
                    ) { Text("Scopes", maxLines = 1) }
                    SegmentedButton(
                        selected = selectedSubTab == 2,
                        onClick = { selectedSubTab = 2 },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4)
                    ) { Text("Ammo", maxLines = 1) }
                    SegmentedButton(
                        selected = selectedSubTab == 3,
                        onClick = { selectedSubTab = 3 },
                        shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4)
                    ) { Text("Cans", maxLines = 1) }
                }

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    SegmentedButton(
                        selected = !viewDatabaseMode,
                        onClick = { viewDatabaseMode = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("My Arsenal ($savedCount)", maxLines = 1) }
                    SegmentedButton(
                        selected = viewDatabaseMode,
                        onClick = { viewDatabaseMode = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Database ($dbCount)", maxLines = 1) }
                }
            }

            if (viewDatabaseMode) {
                val existingNames = remember(selectedSubTab, uiState.firearms, uiState.scopes, uiState.ammoLoads, uiState.suppressors) {
                    when (selectedSubTab) {
                        0 -> uiState.firearms.map { it.name.trim().lowercase(Locale.US) }.toSet()
                        1 -> uiState.scopes.map { it.name.trim().lowercase(Locale.US) }.toSet()
                        2 -> uiState.ammoLoads.map { it.name.trim().lowercase(Locale.US) }.toSet()
                        else -> uiState.suppressors.map { it.name.trim().lowercase(Locale.US) }.toSet()
                    }
                }
                InlineDatabaseGrid(
                    tabIndex = selectedSubTab,
                    columns = columns,
                    query = dbSearchQuery,
                    onQueryChange = { dbSearchQuery = it },
                    quickTag = dbQuickTag,
                    onQuickTagChange = { dbQuickTag = it },
                    existingNames = existingNames,
                    isNightRed = isNightRed,
                    modifier = Modifier.weight(1f),
                    onAddGun = { gun ->
                        viewModel.saveFirearm(gun.copy(id = UUID.randomUUID().toString(), isFavorite = true), makeActive = true)
                    },
                    onAddScope = { scope ->
                        viewModel.saveScope(scope.copy(id = UUID.randomUUID().toString(), isFavorite = true), makeActive = true)
                    },
                    onAddAmmo = { ammo ->
                        viewModel.saveAmmo(ammo.copy(id = UUID.randomUUID().toString(), isFavorite = true), makeActive = true)
                    },
                    onAddSuppressor = { supp ->
                        viewModel.saveSuppressor(supp.copy(id = UUID.randomUUID().toString(), isFavorite = true), makeActive = true)
                    }
                )
            } else when (selectedSubTab) {
                0 -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 88.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("★ Favorites", "All", "Rifles", "Handguns").forEach { tag ->
                                    FilterChip(
                                        selected = gunFilter == tag,
                                        onClick = { gunFilter = tag },
                                        label = { Text(tag) }
                                    )
                                }
                            }
                        }

                        items(filteredGuns, key = { it.id }) { gun ->
                            val isActive = gun.id == uiState.activeFirearm.id
                            val twistLabel = if (gun.barrelTwistInches > 0.0) {
                                "1:${gun.barrelTwistInches.toInt()}\" ${gun.twistDirection.label}"
                            } else {
                                "No Twist"
                            }
                            ArsenalItemCard(
                                title = gun.name,
                                subtitle = "${gun.category.label} • ${gun.caliberHint} • ${gun.barrelLengthInches}\" • $twistLabel",
                                notes = gun.notes,
                                isActive = isActive,
                                isFavorite = gun.isFavorite,
                                accentColor = MaterialTheme.colorScheme.primary,
                                canDelete = uiState.firearms.size > 1,
                                thumbResId = if (gun.category == FirearmCategory.HANDGUN) R.drawable.img_cat_handgun else R.drawable.img_cat_rifle,
                                isNightRed = isNightRed,
                                onSelect = { viewModel.selectActiveFirearm(gun.id) },
                                onToggleFavorite = { viewModel.toggleFirearmFavorite(gun) },
                                onEdit = {
                                    isCreatingGun = false
                                    editingGun = gun
                                },
                                onDuplicate = { viewModel.duplicateFirearm(gun) },
                                onDelete = { pendingDeleteGun = gun }
                            )
                        }
                    }
                }

                1 -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 88.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("All", "★ Favorites", "Rifle Scope", "Red Dot / Iron", "Handgun").forEach { tag ->
                                    FilterChip(
                                        selected = scopeFilter == tag,
                                        onClick = { scopeFilter = tag },
                                        label = { Text(tag) }
                                    )
                                }
                            }
                        }

                        items(filteredScopes, key = { it.id }) { scope ->
                            val isActive = scope.id == uiState.activeScope.id
                            val ssfBadge = if (kotlin.math.abs(scope.elevationSsf - 1.0) > 0.002) {
                                String.format(Locale.US, " • SSF %.3f", scope.elevationSsf)
                            } else ""
                            ArsenalItemCard(
                                title = scope.name,
                                subtitle = "${scope.reticleType.label} • ${scope.clickUnit.label} • Zero ${
                                    UnitFormatters.formatDistance(scope.zeroRangeYards, uiState.preferences.distanceUnit)
                                } • SH ${String.format(Locale.US, "%.2f\"", scope.sightHeightInches)}$ssfBadge",
                                notes = scope.notes,
                                isActive = isActive,
                                isFavorite = scope.isFavorite,
                                accentColor = MaterialTheme.colorScheme.tertiary,
                                canDelete = uiState.scopes.size > 1,
                                thumbResId = R.drawable.img_cat_scope,
                                isNightRed = isNightRed,
                                onSelect = { viewModel.selectActiveScope(scope.id) },
                                onToggleFavorite = { viewModel.toggleScopeFavorite(scope) },
                                onEdit = {
                                    isCreatingScope = false
                                    editingScope = scope
                                },
                                onDuplicate = { viewModel.duplicateScope(scope) },
                                onDelete = { pendingDeleteScope = scope }
                            )
                        }
                    }
                }

                2 -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 88.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("All", "★ Favorites", "Rimfire (.22)", "Rifle", "Handgun").forEach { tag ->
                                    FilterChip(
                                        selected = ammoFilter == tag,
                                        onClick = { ammoFilter = tag },
                                        label = { Text(tag) }
                                    )
                                }
                            }
                        }

                        items(filteredAmmo, key = { it.id }) { ammo ->
                            val isActive = ammo.id == uiState.activeAmmo.id
                            val tempNote = if (kotlin.math.abs(ammo.mvTempSensitivityFpsPerF) > 0.01) {
                                String.format(Locale.US, " • %.2f fps/°F", ammo.mvTempSensitivityFpsPerF)
                            } else ""
                            val multiBcNote = if (ammo.dragModel != DragModel.CUSTOM && ammo.bcBands.isNotEmpty()) " • ${ammo.bcBands.size}-Band BC" else ""
                            val dsfNote = if (ammo.dsfTable.isNotEmpty()) " • DSF(${ammo.dsfTable.size})" else ""
                            val dragBadge = if (ammo.usesCustomDragCurve()) {
                                "Custom Cd (${ammo.customDragCurve?.points?.size ?: 0}pt)"
                            } else {
                                "${ammo.dragModel.label} ${String.format(Locale.US, "%.3f", ammo.primaryBc())}"
                            }
                            ArsenalItemCard(
                                title = ammo.name,
                                subtitle = "${ammo.category.label} • ${ammo.caliber} • $dragBadge • ${
                                    UnitFormatters.formatVelocity(ammo.muzzleVelocityFps, uiState.preferences.velocityUnit)
                                }$multiBcNote$dsfNote$tempNote",
                                notes = ammo.notes,
                                isActive = isActive,
                                isFavorite = ammo.isFavorite,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                canDelete = uiState.ammoLoads.size > 1,
                                thumbResId = R.drawable.img_cat_ammo,
                                isNightRed = isNightRed,
                                onSelect = { viewModel.selectActiveAmmo(ammo.id) },
                                onToggleFavorite = { viewModel.toggleAmmoFavorite(ammo) },
                                onEdit = {
                                    isCreatingAmmo = false
                                    editingAmmo = ammo
                                },
                                onDuplicate = { viewModel.duplicateAmmo(ammo) },
                                onDelete = { pendingDeleteAmmo = ammo }
                            )
                        }
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 88.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("All", "★ Favorites", "Rimfire (.22)", "Rifle", "Pistol / Subgun", "Multi-Caliber").forEach { tag ->
                                    FilterChip(
                                        selected = suppressorFilter == tag,
                                        onClick = { suppressorFilter = tag },
                                        label = { Text(tag) }
                                    )
                                }
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val isBare = uiState.activeSuppressor == null
                            Surface(
                                onClick = { viewModel.selectActiveSuppressor(null) },
                                color = if (isBare) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                                border = if (isBare) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                },
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Unsuppressed (Bare Muzzle)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "No suppressor attached — 0 MV shift, 0 POI shift",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isBare) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Active",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        items(filteredSuppressors, key = { it.id }) { supp ->
                            val isActive = supp.id == uiState.activeSuppressor?.id
                            val shiftParts = buildList {
                                if (kotlin.math.abs(supp.mvShiftFps) > 0.1) {
                                    add(String.format(Locale.US, "%+.0f fps", supp.mvShiftFps))
                                }
                                if (kotlin.math.abs(supp.poiShiftVerticalInches) > 0.02 ||
                                    kotlin.math.abs(supp.poiShiftHorizontalInches) > 0.02
                                ) {
                                    add(
                                        String.format(
                                            Locale.US,
                                            "POI %+.1f\"V/%+.1f\"H",
                                            supp.poiShiftVerticalInches,
                                            supp.poiShiftHorizontalInches
                                        )
                                    )
                                }
                            }.joinToString(" • ")
                            val shiftSuffix = if (shiftParts.isNotEmpty()) " • $shiftParts" else ""

                            ArsenalItemCard(
                                title = supp.name,
                                subtitle = "${supp.category.label} • ${supp.caliberRating} • ${supp.lengthInches}\" / ${supp.weightOz} oz • ${supp.mountType}$shiftSuffix",
                                notes = supp.notes,
                                isActive = isActive,
                                isFavorite = supp.isFavorite,
                                accentColor = MaterialTheme.colorScheme.tertiary,
                                canDelete = true,
                                thumbResId = R.drawable.img_cat_suppressor,
                                isNightRed = isNightRed,
                                onSelect = { viewModel.selectActiveSuppressor(supp.id) },
                                onToggleFavorite = { viewModel.toggleSuppressorFavorite(supp) },
                                onEdit = {
                                    isCreatingSuppressor = false
                                    editingSuppressor = supp
                                },
                                onDuplicate = { viewModel.duplicateSuppressor(supp) },
                                onDelete = { pendingDeleteSuppressor = supp }
                            )
                        }
                    }
                }
            }
        }

            FloatingActionButton(
                onClick = {
                    when (selectedSubTab) {
                        0 -> {
                            isCreatingGun = true
                            editingGun = FirearmSetup(
                                id = UUID.randomUUID().toString(),
                                name = "",
                                category = FirearmCategory.RIFLE,
                                caliberHint = ".223 Rem",
                                barrelLengthInches = 16.0,
                                barrelTwistInches = 8.0,
                                twistDirection = TwistDirection.RIGHT,
                                isFavorite = true
                            )
                        }
                        1 -> {
                            isCreatingScope = true
                            editingScope = ScopeSetup(
                                id = UUID.randomUUID().toString(),
                                name = "",
                                scopeModel = "",
                                category = ScopeCategory.RIFLE_SCOPE,
                                sightHeightInches = 1.85,
                                zeroRangeYards = 100.0,
                                clickUnit = ClickUnit.QUARTER_MOA,
                                isFavorite = true
                            )
                        }
                        2 -> {
                            isCreatingAmmo = true
                            editingAmmo = AmmoLoad(
                                id = UUID.randomUUID().toString(),
                                name = "",
                                caliber = ".223 Rem",
                                category = AmmoCategory.RIFLE,
                                dragModel = DragModel.G1,
                                ballisticCoefficient = 0.250,
                                muzzleVelocityFps = 3100.0,
                                bulletWeightGrains = 55.0,
                                bulletDiameterInches = 0.224,
                                isFavorite = true
                            )
                        }
                        else -> {
                            isCreatingSuppressor = true
                            editingSuppressor = SuppressorSetup(
                                id = UUID.randomUUID().toString(),
                                name = "",
                                manufacturer = "",
                                category = SuppressorCategory.RIMFIRE,
                                caliberRating = ".22 LR",
                                mountType = "1/2x28 Direct Thread",
                                lengthInches = 5.0,
                                weightOz = 5.0,
                                diameterInches = 1.0,
                                isFavorite = true
                            )
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    }

    // --- Editor & Delete Dialogs ---
    editingGun?.let { gun ->
        FirearmEditorDialog(
            initial = gun,
            isNew = isCreatingGun,
            onDismiss = { editingGun = null },
            onSave = { updated ->
                viewModel.saveFirearm(updated, makeActive = isCreatingGun)
                editingGun = null
            }
        )
    }

    editingScope?.let { scope ->
        ScopeEditorDialog(
            initial = scope,
            isNew = isCreatingScope,
            onDismiss = { editingScope = null },
            onSave = { updated ->
                viewModel.saveScope(updated, makeActive = isCreatingScope)
                editingScope = null
            }
        )
    }

    editingAmmo?.let { ammo ->
        AmmoEditorDialog(
            initial = ammo,
            isNew = isCreatingAmmo,
            onDismiss = { editingAmmo = null },
            onSave = { updated ->
                viewModel.saveAmmo(updated, makeActive = isCreatingAmmo)
                editingAmmo = null
            }
        )
    }

    editingSuppressor?.let { supp ->
        SuppressorEditorDialog(
            initial = supp,
            isNew = isCreatingSuppressor,
            onDismiss = { editingSuppressor = null },
            onSave = { updated ->
                viewModel.saveSuppressor(updated, makeActive = isCreatingSuppressor)
                editingSuppressor = null
            }
        )
    }

    pendingDeleteGun?.let { target ->
        ConfirmDeleteDialog(
            title = "Delete Firearm?",
            itemName = target.name,
            onConfirm = {
                viewModel.deleteFirearm(target.id)
                pendingDeleteGun = null
            },
            onDismiss = { pendingDeleteGun = null }
        )
    }

    pendingDeleteScope?.let { target ->
        ConfirmDeleteDialog(
            title = "Delete Scope?",
            itemName = target.name,
            onConfirm = {
                viewModel.deleteScope(target.id)
                pendingDeleteScope = null
            },
            onDismiss = { pendingDeleteScope = null }
        )
    }

    pendingDeleteAmmo?.let { target ->
        ConfirmDeleteDialog(
            title = "Delete Ammo?",
            itemName = target.name,
            onConfirm = {
                viewModel.deleteAmmo(target.id)
                pendingDeleteAmmo = null
            },
            onDismiss = { pendingDeleteAmmo = null }
        )
    }

    pendingDeleteSuppressor?.let { target ->
        ConfirmDeleteDialog(
            title = "Delete Suppressor?",
            itemName = target.name,
            onConfirm = {
                viewModel.deleteSuppressor(target.id)
                pendingDeleteSuppressor = null
            },
            onDismiss = { pendingDeleteSuppressor = null }
        )
    }

    pendingProfileImport?.let { bundle ->
        AlertDialog(
            onDismissRequest = { pendingProfileImport = null },
            title = { Text("Import Profile (${bundle.sourceFormat})") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Review parsed rig components before adding them to My Arsenal and activating them:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    bundle.firearm?.let { f ->
                        Text(
                            text = "Firearm: ${f.name} (${f.caliberHint}, 1:${String.format(Locale.US, "%.1f", f.barrelTwistInches)}\" ${f.twistDirection.label})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    bundle.scope?.let { s ->
                        Text(
                            text = "Scope: ${s.name} (Zero ${s.zeroRangeYards.toInt()} yd, SH ${String.format(Locale.US, "%.2f", s.sightHeightInches)}\", ${s.clickUnit.label})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    bundle.ammo?.let { a ->
                        val bandSuffix = if (a.bcBands.isNotEmpty()) " (${a.bcBands.size}-Band BC)" else ""
                        Text(
                            text = "Ammo: ${a.name} (${a.dragModel.label} ${String.format(Locale.US, "%.3f", a.primaryBc())}$bandSuffix, ${a.muzzleVelocityFps.toInt()} fps, ${a.bulletWeightGrains.toInt()} gr)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (bundle.warnings.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Import Notes",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                bundle.warnings.forEach { warn ->
                                    Text(
                                        text = "• $warn",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.importExternalProfile(bundle)
                    pendingProfileImport = null
                }) { Text("Import to My Arsenal") }
            },
            dismissButton = {
                TextButton(onClick = { pendingProfileImport = null }) { Text("Cancel") }
            }
        )
    }

    profileImportError?.let { err ->
        AlertDialog(
            onDismissRequest = { profileImportError = null },
            title = { Text("Profile Import Failed") },
            text = { Text(err, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                Button(onClick = { profileImportError = null }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun ArsenalItemCard(
    title: String,
    subtitle: String,
    notes: String,
    isActive: Boolean,
    isFavorite: Boolean,
    accentColor: Color,
    canDelete: Boolean,
    @DrawableRes thumbResId: Int? = null,
    isNightRed: Boolean = false,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        border = if (isActive) {
            BorderStroke(1.5.dp, accentColor)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        shape = rememberChamferedHudShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (thumbResId != null) {
                        HardwareThumbBadge(
                            drawableResId = thumbResId,
                            contentDescription = title,
                            isNightRed = isNightRed,
                            size = 44.dp,
                            accentColor = if (isActive) accentColor else MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    if (isActive) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDuplicate()
                                }
                            )
                            if (canDelete) {
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = accentColor,
                modifier = Modifier.padding(end = 8.dp)
            )
            if (notes.isNotBlank()) {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    itemName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text("Remove \"$itemName\" from saved profiles?") },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirearmEditorDialog(
    initial: FirearmSetup,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (FirearmSetup) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var category by remember { mutableStateOf(initial.category) }
    var caliberHint by remember { mutableStateOf(initial.caliberHint) }
    var barrelLen by remember { mutableStateOf(initial.barrelLengthInches.toString()) }
    var twist by remember { mutableStateOf(initial.barrelTwistInches.toString()) }
    var twistDir by remember { mutableStateOf(initial.twistDirection) }
    var isFavorite by remember { mutableStateOf(initial.isFavorite) }
    var notes by remember { mutableStateOf(initial.notes) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New Firearm" else "Edit Firearm") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Firearm Name *") },
                    placeholder = { Text("e.g., CZ 457 (.22 LR)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FirearmCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = caliberHint,
                    onValueChange = { caliberHint = it },
                    label = { Text("Caliber") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = barrelLen,
                        onValueChange = { barrelLen = it },
                        label = { Text("Barrel (in)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = twist,
                        onValueChange = { twist = it },
                        label = { Text("Twist (1:X\")") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TwistDirection.entries.forEach { dir ->
                        FilterChip(
                            selected = twistDir == dir,
                            onClick = { twistDir = dir },
                            label = { Text("${dir.label} Twist") }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Favorite Profile (★)", modifier = Modifier.weight(1f))
                    Switch(checked = isFavorite, onCheckedChange = { isFavorite = it })
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) {
                    errorText = "Please enter a firearm name."
                } else {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            category = category,
                            caliberHint = caliberHint.trim().ifBlank { "Custom" },
                            barrelLengthInches = (barrelLen.toDoubleOrNull() ?: 16.0).coerceIn(2.0, 36.0),
                            barrelTwistInches = (twist.toDoubleOrNull() ?: 10.0).coerceAtLeast(0.0),
                            twistDirection = twistDir,
                            isFavorite = isFavorite,
                            notes = notes.trim()
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScopeEditorDialog(
    initial: ScopeSetup,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ScopeSetup) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var scopeModel by remember { mutableStateOf(initial.scopeModel) }
    var category by remember { mutableStateOf(initial.category) }
    var reticleType by remember { mutableStateOf(initial.reticleType) }
    var sightHeight by remember { mutableStateOf(initial.sightHeightInches.toString()) }
    var zeroRange by remember { mutableStateOf(initial.zeroRangeYards.toInt().toString()) }
    var zeroOffVert by remember { mutableStateOf(String.format(Locale.US, "%.2f", initial.zeroOffsetVerticalInches)) }
    var zeroOffHoriz by remember { mutableStateOf(String.format(Locale.US, "%.2f", initial.zeroOffsetHorizontalInches)) }
    var elevSsf by remember { mutableStateOf(String.format(Locale.US, "%.3f", initial.elevationSsf)) }
    var clickUnit by remember { mutableStateOf(initial.clickUnit) }
    var isFavorite by remember { mutableStateOf(initial.isFavorite) }
    var notes by remember { mutableStateOf(initial.notes) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New Scope / Optic" else "Edit Scope / Optic") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Scope Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = scopeModel,
                    onValueChange = { scopeModel = it },
                    label = { Text("Model / Magnification") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScopeCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sightHeight,
                        onValueChange = { sightHeight = it },
                        label = { Text("Sight Ht (in)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = zeroRange,
                        onValueChange = { zeroRange = it },
                        label = { Text("Zero (yd)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = zeroOffVert,
                        onValueChange = { zeroOffVert = it },
                        label = { Text("Zero Vert (in)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = zeroOffHoriz,
                        onValueChange = { zeroOffHoriz = it },
                        label = { Text("Zero Horiz (in)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = elevSsf,
                    onValueChange = { elevSsf = it },
                    label = { Text("Elevation Scale Factor (SSF, 1.000 = calibrated)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Reticle Pattern", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ReticleType.entries.forEach { rt ->
                        FilterChip(
                            selected = reticleType == rt,
                            onClick = { reticleType = rt },
                            label = { Text(rt.label) }
                        )
                    }
                }
                Text("Turret Clicks", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ClickUnit.entries.forEach { cu ->
                        FilterChip(
                            selected = clickUnit == cu,
                            onClick = { clickUnit = cu },
                            label = { Text(cu.label) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Favorite Optic (★)", modifier = Modifier.weight(1f))
                    Switch(checked = isFavorite, onCheckedChange = { isFavorite = it })
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Mount / Zero Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedSh = sightHeight.toDoubleOrNull()
                val parsedZero = zeroRange.toDoubleOrNull()
                val parsedZeroV = zeroOffVert.toDoubleOrNull() ?: 0.0
                val parsedZeroH = zeroOffHoriz.toDoubleOrNull() ?: 0.0
                val parsedSsf = elevSsf.toDoubleOrNull()
                when {
                    name.isBlank() -> errorText = "Please enter a scope name."
                    parsedSh == null || parsedSh < 0.0 || parsedSh > 10.0 -> errorText = "Sight height should be 0–10 inches."
                    parsedZero == null || parsedZero < 5.0 || parsedZero > 1000.0 -> errorText = "Zero range should be 5–1000 yards."
                    parsedSsf == null || parsedSsf !in 0.8..1.2 -> errorText = "Elevation SSF must be between 0.800 and 1.200."
                    else -> onSave(
                        initial.copy(
                            name = name.trim(),
                            scopeModel = scopeModel.trim(),
                            category = category,
                            reticleType = reticleType,
                            sightHeightInches = parsedSh,
                            zeroRangeYards = parsedZero,
                            zeroOffsetVerticalInches = parsedZeroV.coerceIn(-12.0, 12.0),
                            zeroOffsetHorizontalInches = parsedZeroH.coerceIn(-12.0, 12.0),
                            elevationSsf = parsedSsf,
                            clickUnit = clickUnit,
                            isFavorite = isFavorite,
                            notes = notes.trim()
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmmoEditorDialog(
    initial: AmmoLoad,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (AmmoLoad) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var caliber by remember { mutableStateOf(initial.caliber) }
    var category by remember { mutableStateOf(initial.category) }
    var dragModel by remember { mutableStateOf(initial.dragModel) }
    var bc by remember { mutableStateOf(initial.ballisticCoefficient.toString()) }
    var mv by remember { mutableStateOf(initial.muzzleVelocityFps.toInt().toString()) }
    var weight by remember { mutableStateOf(initial.bulletWeightGrains.toInt().toString()) }
    var diameter by remember { mutableStateOf(initial.bulletDiameterInches.toString()) }
    var bulletLen by remember { mutableStateOf(initial.bulletLengthInches?.toString() ?: "") }
    var mvTempSens by remember { mutableStateOf(String.format(Locale.US, "%.2f", initial.mvTempSensitivityFpsPerF)) }
    var chronoTemp by remember { mutableStateOf(initial.chronoTempF.toInt().toString()) }
    var isFavorite by remember { mutableStateOf(initial.isFavorite) }
    var notes by remember { mutableStateOf(initial.notes) }
    var multiBcExpanded by remember { mutableStateOf(initial.bcBands.isNotEmpty()) }
    var bandRows by remember {
        mutableStateOf(
            initial.bcBands
                .sortedByDescending { it.minVelocityFps }
                .take(5)
                .map { it.minVelocityFps.toInt().toString() to String.format(Locale.US, "%.3f", it.bc) }
        )
    }
    var errorText by remember { mutableStateOf<String?>(null) }
    var customCurve by remember { mutableStateOf(initial.customDragCurve) }
    var curveStatusMsg by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    val importCurveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not open selected drag curve file.")
                CustomDragCurveCodec.parse(text, uri.lastPathSegment).getOrThrow()
            }.onSuccess { parsed ->
                customCurve = parsed
                dragModel = DragModel.CUSTOM
                curveStatusMsg = "Loaded ${parsed.name} (${parsed.points.size} Mach/Cd knots)"
                errorText = null
            }.onFailure { err ->
                curveStatusMsg = null
                errorText = err.message ?: "Invalid custom drag curve file."
            }
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val c = customCurve
        if (uri != null && c != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(CustomDragCurveCodec.exportJson(c))
                }
            }.onSuccess {
                curveStatusMsg = "Exported ${c.name} as JSON"
            }.onFailure {
                errorText = "Export failed: ${it.message}"
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val c = customCurve
        if (uri != null && c != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(CustomDragCurveCodec.exportCsv(c))
                }
            }.onSuccess {
                curveStatusMsg = "Exported ${c.name} as CSV"
            }.onFailure {
                errorText = "Export failed: ${it.message}"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New Ammo Load" else "Edit Ammo Load") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ammo Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = caliber,
                    onValueChange = { caliber = it },
                    label = { Text("Caliber") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AmmoCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
                Text("Drag Model", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DragModel.entries.forEach { dm ->
                        FilterChip(
                            selected = dragModel == dm,
                            onClick = { dragModel = dm },
                            label = { Text(dm.label) }
                        )
                    }
                }
                if (dragModel == DragModel.CUSTOM) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Custom Cd(Mach) Drag Table",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            val activeCurve = customCurve
                            Text(
                                text = if (activeCurve != null && activeCurve.isValid()) {
                                    val minM = activeCurve.points.minOf { it.mach }
                                    val maxM = activeCurve.points.maxOf { it.mach }
                                    "${activeCurve.name} • ${activeCurve.points.size} knots (M${String.format(Locale.US, "%.2f", minM)}–M${String.format(Locale.US, "%.2f", maxM)})" +
                                        if (activeCurve.source.isNotBlank()) "\nSource: ${activeCurve.source}" else ""
                                } else {
                                    "No curve loaded yet. Import a JSON/CSV Cd-vs-Mach table or load a starter template."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { importCurveLauncher.launch(arrayOf("*/*")) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) { Text("Import JSON/CSV", style = MaterialTheme.typography.labelSmall) }
                                OutlinedButton(
                                    onClick = {
                                        val tpl = CustomDragCurveCodec.buildTemplateCurve(DragModel.G7, "${name.ifBlank { "Custom" }} G7 Template")
                                        customCurve = tpl
                                        curveStatusMsg = "Loaded ${tpl.points.size}-knot G7 template"
                                        errorText = null
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) { Text("Load Template", style = MaterialTheme.typography.labelSmall) }
                            }
                            if (activeCurve != null && activeCurve.isValid()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                    TextButton(
                                        onClick = { exportJsonLauncher.launch("${name.ifBlank { "custom_curve" }.lowercase().replace(Regex("[^a-z0-9]+"), "_")}.json") },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Export JSON", style = MaterialTheme.typography.labelSmall) }
                                    TextButton(
                                        onClick = { exportCsvLauncher.launch("${name.ifBlank { "custom_curve" }.lowercase().replace(Regex("[^a-z0-9]+"), "_")}.csv") },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Export CSV", style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                            curveStatusMsg?.let { msg ->
                                Text(msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = bc,
                        onValueChange = { bc = it },
                        label = { Text("BC (${dragModel.label})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = mv,
                        onValueChange = { mv = it },
                        label = { Text("MV (fps)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Weight (gr)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = diameter,
                        onValueChange = { diameter = it },
                        label = { Text("Dia (in)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = bulletLen,
                        onValueChange = { bulletLen = it },
                        label = { Text("Len (in)") },
                        placeholder = { Text("Auto") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mvTempSens,
                        onValueChange = { mvTempSens = it },
                        label = { Text("Powder (fps/°F)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = chronoTemp,
                        onValueChange = { chronoTemp = it },
                        label = { Text("Chrono (°F)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Expandable Multi-BC (Velocity Bands — up to 5 Sierra/Strelok pairs)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { multiBcExpanded = !multiBcExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Multi-BC (Velocity Bands)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (bandRows.isEmpty()) "Single BC mode (tap to add up to 5 bands)"
                                    else "${bandRows.size} velocity band(s) active • Highest BC synced to LBC1800",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (multiBcExpanded) "Hide" else "Edit",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (multiBcExpanded) {
                            bandRows.forEachIndexed { idx, (velStr, bcStr) ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = velStr,
                                        onValueChange = { newVel ->
                                            bandRows = bandRows.toMutableList().also { list ->
                                                list[idx] = newVel to list[idx].second
                                            }
                                        },
                                        label = { Text("Min fps") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = bcStr,
                                        onValueChange = { newBc ->
                                            bandRows = bandRows.toMutableList().also { list ->
                                                list[idx] = list[idx].first to newBc
                                            }
                                        },
                                        label = { Text("BC") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            bandRows = bandRows.toMutableList().also { it.removeAt(idx) }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove band", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            if (bandRows.size < 5) {
                                OutlinedButton(
                                    onClick = {
                                        val suggestedVel = when (bandRows.size) {
                                            0 -> (mv.toDoubleOrNull()?.times(0.85)?.toInt() ?: 2400).toString()
                                            1 -> (mv.toDoubleOrNull()?.times(0.65)?.toInt() ?: 1900).toString()
                                            2 -> (mv.toDoubleOrNull()?.times(0.48)?.toInt() ?: 1500).toString()
                                            3 -> "1200"
                                            else -> "0"
                                        }
                                        val suggestedBc = bc.toDoubleOrNull()?.let {
                                            String.format(Locale.US, "%.3f", (it - bandRows.size * 0.015).coerceAtLeast(0.05))
                                        } ?: "0.250"
                                        bandRows = bandRows + (suggestedVel to suggestedBc)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Velocity Band (${bandRows.size}/5)")
                                }
                            }
                        }
                    }
                }

                if (initial.dsfTable.isNotEmpty()) {
                    Text(
                        text = "Active DSF Nodes: " + initial.dsfTable.joinToString(", ") {
                            String.format(Locale.US, "M%.2f→%.3f", it.mach, it.dropScaleFactor)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Favorite Ammo (★)", modifier = Modifier.weight(1f))
                    Switch(checked = isFavorite, onCheckedChange = { isFavorite = it })
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedBc = bc.toDoubleOrNull()
                val parsedMv = mv.toDoubleOrNull()
                val parsedWt = weight.toDoubleOrNull()
                val parsedDia = diameter.toDoubleOrNull()
                val parsedLen = bulletLen.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                val parsedSens = mvTempSens.toDoubleOrNull() ?: 0.0
                val parsedChrono = chronoTemp.toDoubleOrNull() ?: 59.0
                val parsedBands = bandRows.mapNotNull { (vStr, bStr) ->
                    val v = vStr.trim().toDoubleOrNull()
                    val b = bStr.trim().toDoubleOrNull()
                    if (v != null && b != null && v >= 0.0 && b in 0.02..2.0) BcBand(v, b) else null
                }.sortedByDescending { it.minVelocityFps }
                val invalidBand = bandRows.isNotEmpty() && parsedBands.size != bandRows.size

                when {
                    name.isBlank() -> errorText = "Please enter an ammo name."
                    parsedBc == null || parsedBc <= 0.01 || parsedBc > 2.0 -> errorText = "BC must be between 0.02 and 2.0."
                    parsedMv == null || parsedMv < 100.0 || parsedMv > 5500.0 -> errorText = "MV must be 100–5500 fps."
                    parsedWt == null || parsedWt <= 5.0 -> errorText = "Enter bullet weight in grains."
                    parsedDia == null || parsedDia <= 0.05 -> errorText = "Enter bullet diameter in inches."
                    bulletLen.isNotBlank() && (parsedLen == null || parsedLen !in 0.1..3.5) ->
                        errorText = "Bullet length must be 0.1–3.5 in or blank."
                    invalidBand -> errorText = "Each Multi-BC row needs valid Min fps (>=0) and BC (0.02–2.0)."
                    dragModel == DragModel.CUSTOM && (customCurve == null || !customCurve!!.isValid()) ->
                        errorText = "Import a valid Custom Cd(Mach) JSON/CSV table (at least 2 points) or load a template."
                    else -> onSave(
                        initial.copy(
                            name = name.trim(),
                            caliber = caliber.trim().ifBlank { "Custom" },
                            category = category,
                            dragModel = dragModel,
                            ballisticCoefficient = parsedBands.firstOrNull()?.bc ?: parsedBc,
                            bcBands = parsedBands,
                            customDragCurve = customCurve,
                            muzzleVelocityFps = parsedMv,
                            bulletWeightGrains = parsedWt,
                            bulletDiameterInches = parsedDia,
                            bulletLengthInches = parsedLen,
                            mvTempSensitivityFpsPerF = parsedSens.coerceIn(-5.0, 5.0),
                            chronoTempF = parsedChrono.coerceIn(-40.0, 140.0),
                            isFavorite = isFavorite,
                            notes = notes.trim()
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuppressorEditorDialog(
    initial: SuppressorSetup,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (SuppressorSetup) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var manufacturer by remember { mutableStateOf(initial.manufacturer) }
    var category by remember { mutableStateOf(initial.category) }
    var caliberRating by remember { mutableStateOf(initial.caliberRating) }
    var mountType by remember { mutableStateOf(initial.mountType) }
    var lengthIn by remember { mutableStateOf(initial.lengthInches.toString()) }
    var weightOz by remember { mutableStateOf(initial.weightOz.toString()) }
    var diameterIn by remember { mutableStateOf(initial.diameterInches.toString()) }
    var mvShift by remember { mutableStateOf(String.format(Locale.US, "%.0f", initial.mvShiftFps)) }
    var poiVert by remember { mutableStateOf(String.format(Locale.US, "%.2f", initial.poiShiftVerticalInches)) }
    var poiHoriz by remember { mutableStateOf(String.format(Locale.US, "%.2f", initial.poiShiftHorizontalInches)) }
    var isFavorite by remember { mutableStateOf(initial.isFavorite) }
    var notes by remember { mutableStateOf(initial.notes) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New Suppressor" else "Edit Suppressor") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Suppressor Model *") },
                    placeholder = { Text("e.g., Gemtech GM-22") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = manufacturer,
                    onValueChange = { manufacturer = it },
                    label = { Text("Manufacturer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuppressorCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = caliberRating,
                        onValueChange = { caliberRating = it },
                        label = { Text("Caliber Rating") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = mountType,
                        onValueChange = { mountType = it },
                        label = { Text("Mount / Thread") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lengthIn,
                        onValueChange = { lengthIn = it },
                        label = { Text("Len (in)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weightOz,
                        onValueChange = { weightOz = it },
                        label = { Text("Wt (oz)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = diameterIn,
                        onValueChange = { diameterIn = it },
                        label = { Text("Dia (in)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mvShift,
                        onValueChange = { mvShift = it },
                        label = { Text("MV Shift (fps)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = poiVert,
                        onValueChange = { poiVert = it },
                        label = { Text("POI Vert (in)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = poiHoriz,
                        onValueChange = { poiHoriz = it },
                        label = { Text("POI Horiz (in)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Favorite Suppressor (★)", modifier = Modifier.weight(1f))
                    Switch(checked = isFavorite, onCheckedChange = { isFavorite = it })
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedLen = lengthIn.toDoubleOrNull() ?: 5.0
                val parsedWt = weightOz.toDoubleOrNull() ?: 5.0
                val parsedDia = diameterIn.toDoubleOrNull() ?: 1.0
                val parsedMvShift = mvShift.toDoubleOrNull() ?: 0.0
                val parsedPoiV = poiVert.toDoubleOrNull() ?: 0.0
                val parsedPoiH = poiHoriz.toDoubleOrNull() ?: 0.0
                if (name.isBlank()) {
                    errorText = "Please enter a suppressor name."
                } else {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            manufacturer = manufacturer.trim(),
                            category = category,
                            caliberRating = caliberRating.trim().ifBlank { "Multi" },
                            mountType = mountType.trim().ifBlank { "Direct Thread" },
                            lengthInches = parsedLen.coerceIn(1.0, 24.0),
                            weightOz = parsedWt.coerceIn(0.5, 64.0),
                            diameterInches = parsedDia.coerceIn(0.5, 4.0),
                            mvShiftFps = parsedMvShift.coerceIn(-250.0, 250.0),
                            poiShiftVerticalInches = parsedPoiV.coerceIn(-12.0, 12.0),
                            poiShiftHorizontalInches = parsedPoiH.coerceIn(-12.0, 12.0),
                            isFavorite = isFavorite,
                            notes = notes.trim()
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineDatabaseGrid(
    tabIndex: Int,
    columns: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    quickTag: String,
    onQuickTagChange: (String) -> Unit,
    existingNames: Set<String>,
    isNightRed: Boolean,
    modifier: Modifier = Modifier,
    onAddGun: (FirearmSetup) -> Unit,
    onAddScope: (ScopeSetup) -> Unit,
    onAddAmmo: (AmmoLoad) -> Unit,
    onAddSuppressor: (SuppressorSetup) -> Unit
) {
    val qTokens = remember(query) {
        query.trim().lowercase(Locale.US).split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    fun matchesTokens(haystack: String): Boolean {
        if (qTokens.isEmpty()) return true
        val lower = haystack.lowercase(Locale.US)
        return qTokens.all { lower.contains(it) }
    }

    val quickTags = remember(tabIndex) {
        when (tabIndex) {
            0 -> listOf("All", "Rimfire", "AR / Gas", "Bolt", "Handgun")
            1 -> listOf("All", "Rifle Scope", "LPVO", "Red Dot", "Handgun")
            2 -> listOf("All", ".22 LR", ".223/5.56", "6.5 CM", ".308", "Magnum", "Handgun", "Multi-BC")
            else -> listOf("All", "Rimfire", "Rifle", "Pistol", "Multi-Cal")
        }
    }

    val gunMatches = remember(tabIndex, qTokens, quickTag) {
        if (tabIndex != 0) emptyList()
        else BallisticsCatalog.firearms.filter { gun ->
            val tagOk = when (quickTag) {
                "Rimfire" -> gun.caliberHint.contains("22 LR", true) || gun.caliberHint.contains("17 HMR", true) || gun.caliberHint.contains("22 WMR", true)
                "AR / Gas" -> gun.name.contains("AR-", true) || gun.name.contains("BCM", true) || gun.name.contains("Daniel", true) || gun.name.contains("Geissele", true) || gun.name.contains("MCX", true) || gun.name.contains("SCAR", true) || gun.name.contains("AK-", true) || gun.name.contains("Aero", true) || gun.name.contains("LMT", true) || gun.name.contains("SR-15", true)
                "Bolt" -> gun.category == FirearmCategory.RIFLE && (gun.notes.contains("bolt", true) || gun.notes.contains("chassis", true) || gun.notes.contains("hunting", true) || gun.name.contains("Tikka", true) || gun.name.contains("Bergara", true) || gun.name.contains("Remington", true) || gun.name.contains("Savage", true))
                "Handgun" -> gun.category == FirearmCategory.HANDGUN
                else -> true
            }
            tagOk && matchesTokens("${gun.name} ${gun.caliberHint} ${gun.category.label} ${gun.notes}")
        }
    }

    val scopeMatches = remember(tabIndex, qTokens, quickTag) {
        if (tabIndex != 1) emptyList()
        else BallisticsCatalog.scopes.filter { sc ->
            val tagOk = when (quickTag) {
                "Rifle Scope" -> sc.category == ScopeCategory.RIFLE_SCOPE && !sc.scopeModel.contains("LPVO", true) && !sc.name.contains("LPVO", true)
                "LPVO" -> sc.scopeModel.contains("LPVO", true) || sc.name.contains("LPVO", true) || sc.name.contains("1-6", true) || sc.name.contains("1-8", true) || sc.name.contains("1-10", true)
                "Red Dot" -> sc.category == ScopeCategory.RED_DOT_IRON
                "Handgun" -> sc.category == ScopeCategory.HANDGUN_OPTIC
                else -> true
            }
            tagOk && matchesTokens("${sc.name} ${sc.scopeModel} ${sc.reticleType.label} ${sc.clickUnit.label} ${sc.notes}")
        }
    }

    val ammoMatches = remember(tabIndex, qTokens, quickTag) {
        if (tabIndex != 2) emptyList()
        else BallisticsCatalog.ammo.filter { am ->
            val tagOk = when (quickTag) {
                ".22 LR" -> am.category == AmmoCategory.RIMFIRE
                ".223/5.56" -> am.caliber.contains("223", true) || am.caliber.contains("5.56", true) || am.caliber.contains("300 Blackout", true) || am.caliber.contains("ARC", true) || am.caliber.contains("Grendel", true)
                "6.5 CM" -> am.caliber.contains("6.5", true) || am.caliber.contains("6mm", true)
                ".308" -> am.caliber.contains("308", true) || am.caliber.contains("30-06", true)
                "Magnum" -> am.caliber.contains("Mag", true) || am.caliber.contains("PRC", true) || am.caliber.contains("Lapua", true)
                "Handgun" -> am.category == AmmoCategory.HANDGUN
                "Multi-BC" -> am.bcBands.isNotEmpty()
                else -> true
            }
            tagOk && matchesTokens("${am.name} ${am.caliber} ${am.category.label} ${am.dragModel.label} ${am.notes}")
        }
    }

    val suppressorMatches = remember(tabIndex, qTokens, quickTag) {
        if (tabIndex != 3) emptyList()
        else BallisticsCatalog.suppressors.filter { supp ->
            val tagOk = when (quickTag) {
                "Rimfire" -> supp.category == SuppressorCategory.RIMFIRE
                "Rifle" -> supp.category == SuppressorCategory.RIFLE
                "Pistol" -> supp.category == SuppressorCategory.PISTOL
                "Multi-Cal" -> supp.category == SuppressorCategory.MULTI_CAL
                else -> true
            }
            tagOk && matchesTokens("${supp.name} ${supp.manufacturer} ${supp.caliberRating} ${supp.mountType} ${supp.category.label} ${supp.notes}")
        }
    }

    val totalCount = when (tabIndex) {
        0 -> BallisticsCatalog.firearms.size
        1 -> BallisticsCatalog.scopes.size
        2 -> BallisticsCatalog.ammo.size
        else -> BallisticsCatalog.suppressors.size
    }
    val shownCount = when (tabIndex) {
        0 -> gunMatches.size
        1 -> scopeMatches.size
        2 -> ammoMatches.size
        else -> suppressorMatches.size
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search $totalCount items by brand, model, caliber...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quickTags.forEach { tag ->
                        FilterChip(
                            selected = quickTag == tag,
                            onClick = { onQuickTagChange(tag) },
                            label = { Text(tag) }
                        )
                    }
                }
                Text(
                    text = "Showing $shownCount of $totalCount • Tap any item to add to My Arsenal & activate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when (tabIndex) {
            0 -> items(gunMatches, key = { it.id }) { gun ->
                val alreadyAdded = gun.name.trim().lowercase(Locale.US) in existingNames
                CatalogEntryRow(
                    title = gun.name,
                    subtitle = "${gun.category.label} • ${gun.caliberHint} • ${gun.barrelLengthInches}\" • 1:${gun.barrelTwistInches.toInt()}\"",
                    notes = gun.notes,
                    accentColor = MaterialTheme.colorScheme.primary,
                    thumbResId = if (gun.category == FirearmCategory.HANDGUN) R.drawable.img_cat_handgun else R.drawable.img_cat_rifle,
                    isNightRed = isNightRed,
                    alreadyAdded = alreadyAdded,
                    onClick = { onAddGun(gun) }
                )
            }
            1 -> items(scopeMatches, key = { it.id }) { scope ->
                val alreadyAdded = scope.name.trim().lowercase(Locale.US) in existingNames
                CatalogEntryRow(
                    title = scope.name,
                    subtitle = "${scope.reticleType.label} • ${scope.clickUnit.label} • SH ${scope.sightHeightInches}\"",
                    notes = scope.notes,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    thumbResId = R.drawable.img_cat_scope,
                    isNightRed = isNightRed,
                    alreadyAdded = alreadyAdded,
                    onClick = { onAddScope(scope) }
                )
            }
            2 -> items(ammoMatches, key = { it.id }) { ammo ->
                val alreadyAdded = ammo.name.trim().lowercase(Locale.US) in existingNames
                val bandTag = if (ammo.bcBands.isNotEmpty()) " • ${ammo.bcBands.size}-Band BC" else ""
                CatalogEntryRow(
                    title = ammo.name,
                    subtitle = "${ammo.caliber} • ${ammo.dragModel.label} ${ammo.primaryBc()} • ${ammo.muzzleVelocityFps.toInt()} fps$bandTag",
                    notes = ammo.notes,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    thumbResId = R.drawable.img_cat_ammo,
                    isNightRed = isNightRed,
                    alreadyAdded = alreadyAdded,
                    onClick = { onAddAmmo(ammo) }
                )
            }
            else -> items(suppressorMatches, key = { it.id }) { supp ->
                val alreadyAdded = supp.name.trim().lowercase(Locale.US) in existingNames
                CatalogEntryRow(
                    title = supp.name,
                    subtitle = "${supp.category.label} • ${supp.caliberRating} • ${supp.lengthInches}\" / ${supp.weightOz} oz • ${supp.mountType}",
                    notes = supp.notes,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    thumbResId = R.drawable.img_cat_suppressor,
                    isNightRed = isNightRed,
                    alreadyAdded = alreadyAdded,
                    onClick = { onAddSuppressor(supp) }
                )
            }
        }
    }
}

@Composable
private fun CatalogEntryRow(
    title: String,
    subtitle: String,
    notes: String,
    accentColor: Color,
    @DrawableRes thumbResId: Int,
    isNightRed: Boolean,
    alreadyAdded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (alreadyAdded) accentColor.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = rememberChamferedHudShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HardwareThumbBadge(
                drawableResId = thumbResId,
                contentDescription = title,
                isNightRed = isNightRed,
                size = 42.dp,
                accentColor = accentColor
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = accentColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (notes.isNotBlank()) {
                    Text(notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Surface(
                color = if (alreadyAdded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = if (alreadyAdded) "Added ✓" else "+ Add",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (alreadyAdded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
