package com.bc1800.ballistics.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bc1800.ballistics.data.BallisticsCatalog
import com.bc1800.ballistics.data.UnitFormatters
import com.bc1800.ballistics.model.AmmoCategory
import com.bc1800.ballistics.model.AmmoLoad
import com.bc1800.ballistics.model.ClickUnit
import com.bc1800.ballistics.model.DragModel
import com.bc1800.ballistics.model.FirearmCategory
import com.bc1800.ballistics.model.FirearmSetup
import com.bc1800.ballistics.model.ReticleType
import com.bc1800.ballistics.model.ScopeCategory
import com.bc1800.ballistics.model.ScopeSetup
import com.bc1800.ballistics.model.TwistDirection
import com.bc1800.ballistics.ui.AppUiState
import com.bc1800.ballistics.ui.BallisticsViewModel
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilesScreen(
    uiState: AppUiState,
    viewModel: BallisticsViewModel
) {
    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Guns, 1 = Scopes, 2 = Ammo
    var showCatalogDialog by remember { mutableStateOf(false) }

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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp
        val columns = if (isWide) 2 else 1

        Column(modifier = Modifier.fillMaxSize()) {
            // Active 3-Way Rig Strip
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = "ACTIVE RIG",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${uiState.activeFirearm.name}  •  ${uiState.activeScope.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = uiState.activeAmmo.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Segmented Switcher + Catalog Launcher (row on unfolded, stacked on cover)
            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1.2f)) {
                        SegmentedButton(
                            selected = selectedSubTab == 0,
                            onClick = { selectedSubTab = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                        ) { Text("Guns (${uiState.firearms.size})") }
                        SegmentedButton(
                            selected = selectedSubTab == 1,
                            onClick = { selectedSubTab = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                        ) { Text("Scopes (${uiState.scopes.size})") }
                        SegmentedButton(
                            selected = selectedSubTab == 2,
                            onClick = { selectedSubTab = 2 },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                        ) { Text("Ammo (${uiState.ammoLoads.size})") }
                    }

                    OutlinedButton(
                        onClick = { showCatalogDialog = true },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when (selectedSubTab) {
                                0 -> "Catalog (${BallisticsCatalog.firearms.size})"
                                1 -> "Catalog (${BallisticsCatalog.scopes.size})"
                                else -> "Catalog (${BallisticsCatalog.ammo.size})"
                            }
                        )
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
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) { Text("Guns (${uiState.firearms.size})") }
                    SegmentedButton(
                        selected = selectedSubTab == 1,
                        onClick = { selectedSubTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) { Text("Scopes (${uiState.scopes.size})") }
                    SegmentedButton(
                        selected = selectedSubTab == 2,
                        onClick = { selectedSubTab = 2 },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) { Text("Ammo (${uiState.ammoLoads.size})") }
                }

                OutlinedButton(
                    onClick = { showCatalogDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when (selectedSubTab) {
                            0 -> "Browse Firearms Catalog (${BallisticsCatalog.firearms.size})"
                            1 -> "Browse Optics Catalog (${BallisticsCatalog.scopes.size})"
                            else -> "Browse Ammo Catalog (${BallisticsCatalog.ammo.size})"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            when (selectedSubTab) {
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
                            ArsenalItemCard(
                                title = ammo.name,
                                subtitle = "${ammo.category.label} • ${ammo.caliber} • ${ammo.dragModel.label} ${
                                    String.format(Locale.US, "%.3f", ammo.ballisticCoefficient)
                                } • ${UnitFormatters.formatVelocity(ammo.muzzleVelocityFps, uiState.preferences.velocityUnit)}$tempNote",
                                notes = ammo.notes,
                                isActive = isActive,
                                isFavorite = ammo.isFavorite,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                canDelete = uiState.ammoLoads.size > 1,
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
                    else -> {
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

    // --- Built-in Catalog Browser Dialog ---
    if (showCatalogDialog) {
        val existingNames = remember(selectedSubTab, uiState.firearms, uiState.scopes, uiState.ammoLoads) {
            when (selectedSubTab) {
                0 -> uiState.firearms.map { it.name.trim().lowercase(Locale.US) }.toSet()
                1 -> uiState.scopes.map { it.name.trim().lowercase(Locale.US) }.toSet()
                else -> uiState.ammoLoads.map { it.name.trim().lowercase(Locale.US) }.toSet()
            }
        }
        CatalogBrowserDialog(
            tabIndex = selectedSubTab,
            existingNames = existingNames,
            onDismiss = { showCatalogDialog = false },
            onAddGun = { gun ->
                viewModel.saveFirearm(gun.copy(id = UUID.randomUUID().toString(), isFavorite = true), makeActive = true)
                showCatalogDialog = false
            },
            onAddScope = { scope ->
                viewModel.saveScope(scope.copy(id = UUID.randomUUID().toString(), isFavorite = true), makeActive = true)
                showCatalogDialog = false
            },
            onAddAmmo = { ammo ->
                viewModel.saveAmmo(ammo.copy(id = UUID.randomUUID().toString(), isFavorite = true), makeActive = true)
                showCatalogDialog = false
            }
        )
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
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 12.dp),
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
    var errorText by remember { mutableStateOf<String?>(null) }

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
                when {
                    name.isBlank() -> errorText = "Please enter an ammo name."
                    parsedBc == null || parsedBc <= 0.01 || parsedBc > 2.0 -> errorText = "BC must be between 0.02 and 2.0."
                    parsedMv == null || parsedMv < 100.0 || parsedMv > 5500.0 -> errorText = "MV must be 100–5500 fps."
                    parsedWt == null || parsedWt <= 5.0 -> errorText = "Enter bullet weight in grains."
                    parsedDia == null || parsedDia <= 0.05 -> errorText = "Enter bullet diameter in inches."
                    bulletLen.isNotBlank() && (parsedLen == null || parsedLen !in 0.1..3.5) ->
                        errorText = "Bullet length must be 0.1–3.5 in or blank."
                    else -> onSave(
                        initial.copy(
                            name = name.trim(),
                            caliber = caliber.trim().ifBlank { "Custom" },
                            category = category,
                            dragModel = dragModel,
                            ballisticCoefficient = parsedBc,
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

@Composable
private fun CatalogBrowserDialog(
    tabIndex: Int,
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    onAddGun: (FirearmSetup) -> Unit,
    onAddScope: (ScopeSetup) -> Unit,
    onAddAmmo: (AmmoLoad) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase(Locale.US)

    val gunMatches = remember(tabIndex, q) {
        if (tabIndex != 0) emptyList()
        else BallisticsCatalog.firearms.filter {
            q.isEmpty() || it.name.lowercase(Locale.US).contains(q) ||
                it.caliberHint.lowercase(Locale.US).contains(q) ||
                it.category.label.lowercase(Locale.US).contains(q)
        }
    }

    val scopeMatches = remember(tabIndex, q) {
        if (tabIndex != 1) emptyList()
        else BallisticsCatalog.scopes.filter {
            q.isEmpty() || it.name.lowercase(Locale.US).contains(q) ||
                it.scopeModel.lowercase(Locale.US).contains(q) ||
                it.reticleType.label.lowercase(Locale.US).contains(q)
        }
    }

    val ammoMatches = remember(tabIndex, q) {
        if (tabIndex != 2) emptyList()
        else BallisticsCatalog.ammo.filter {
            q.isEmpty() || it.name.lowercase(Locale.US).contains(q) ||
                it.caliber.lowercase(Locale.US).contains(q) ||
                it.category.label.lowercase(Locale.US).contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (tabIndex) {
                    0 -> "Firearms Catalog"
                    1 -> "Scopes & Optics Catalog"
                    else -> "Factory Ammo Catalog"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search brand, model, or caliber...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (tabIndex) {
                        0 -> {
                            items(gunMatches, key = { it.id }) { gun ->
                                val alreadyAdded = gun.name.trim().lowercase(Locale.US) in existingNames
                                CatalogEntryRow(
                                    title = gun.name,
                                    subtitle = "${gun.category.label} • ${gun.caliberHint} • ${gun.barrelLengthInches}\" • 1:${gun.barrelTwistInches.toInt()}\"",
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    alreadyAdded = alreadyAdded,
                                    onClick = { onAddGun(gun) }
                                )
                            }
                        }
                        1 -> {
                            items(scopeMatches, key = { it.id }) { scope ->
                                val alreadyAdded = scope.name.trim().lowercase(Locale.US) in existingNames
                                CatalogEntryRow(
                                    title = scope.name,
                                    subtitle = "${scope.reticleType.label} • ${scope.clickUnit.label} • SH ${scope.sightHeightInches}\"",
                                    accentColor = MaterialTheme.colorScheme.tertiary,
                                    alreadyAdded = alreadyAdded,
                                    onClick = { onAddScope(scope) }
                                )
                            }
                        }
                        else -> {
                            items(ammoMatches, key = { it.id }) { ammo ->
                                val alreadyAdded = ammo.name.trim().lowercase(Locale.US) in existingNames
                                CatalogEntryRow(
                                    title = ammo.name,
                                    subtitle = "${ammo.caliber} • ${ammo.dragModel.label} ${ammo.ballisticCoefficient} • ${ammo.muzzleVelocityFps.toInt()} fps",
                                    accentColor = MaterialTheme.colorScheme.secondary,
                                    alreadyAdded = alreadyAdded,
                                    onClick = { onAddAmmo(ammo) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun CatalogEntryRow(
    title: String,
    subtitle: String,
    accentColor: Color,
    alreadyAdded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = accentColor)
            }
            if (alreadyAdded) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Added",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
