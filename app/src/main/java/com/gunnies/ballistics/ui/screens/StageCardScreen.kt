package com.gunnies.ballistics.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gunnies.ballistics.data.UnitFormatters
import com.gunnies.ballistics.model.AngularUnit
import com.gunnies.ballistics.model.DistanceUnit
import com.gunnies.ballistics.model.StageCard
import com.gunnies.ballistics.model.StageTarget
import com.gunnies.ballistics.model.UserPreferences
import com.gunnies.ballistics.ui.AppUiState
import com.gunnies.ballistics.ui.BallisticsViewModel
import com.gunnies.ballistics.ui.StageTargetSolution
import com.gunnies.ballistics.ui.components.GunneryHudPanel
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StageCardScreen(
    uiState: AppUiState,
    viewModel: BallisticsViewModel,
    onSendTargetToSolver: (StageTarget) -> Unit
) {
    val context = LocalContext.current
    val prefs = uiState.preferences
    val activeStage = uiState.activeStageCard
    val solvedTargets = uiState.stageSolutions
    val hasAnyMover = remember(activeStage.targets) { activeStage.targets.any { it.moverSpeedMph > 0.1 } }

    var editingTarget by remember { mutableStateOf<StageTarget?>(null) }
    var isCreatingTarget by remember { mutableStateOf(false) }
    var showNewStageDialog by remember { mutableStateOf(false) }
    var showRenameStageDialog by remember { mutableStateOf(false) }
    var showDeleteStageDialog by remember { mutableStateOf(false) }

    fun shareStageCardText() {
        val sb = StringBuilder()
        sb.appendLine("Gunnies Ballistics PRS Stage Card — ${activeStage.name}")
        sb.appendLine("Rig: ${uiState.activeFirearm.name} • ${uiState.activeScope.name} • ${uiState.activeAmmo.name}")
        sb.appendLine("----------------------------------------------------------")
        for (item in solvedTargets) {
            val t = item.target
            val s = item.solution
            val dist = UnitFormatters.formatDistance(t.rangeYards, prefs.distanceUnit)
            val elev = UnitFormatters.formatCompactHold(
                UnitFormatters.elevationValue(s, prefs.angularUnit),
                prefs.angularUnit,
                "U",
                "D"
            )
            val wind = UnitFormatters.formatCompactHold(
                UnitFormatters.windageValue(s, prefs.angularUnit),
                prefs.angularUnit,
                "R",
                "L"
            )
            val leadPart = if (t.moverSpeedMph > 0.1) {
                val leadVal = if (prefs.angularUnit == AngularUnit.MRAD) s.moverLeadMrad else s.moverLeadMoa
                String.format(Locale.US, " | Lead %.2f", leadVal)
            } else ""
            sb.appendLine(
                "${t.label} ($dist${if (abs(t.inclinationDeg) >= 0.5) String.format(Locale.US, " %+.0f°", t.inclinationDeg) else ""}): " +
                    "Elev $elev (${abs(s.elevationClicks).roundToInt()} clk) | Wind $wind$leadPart | TOF ${String.format(Locale.US, "%.2fs", s.timeOfFlightSec)}"
            )
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Stage Card: ${activeStage.name}")
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        context.startActivity(Intent.createChooser(intent, "Share Stage Card"))
    }

    fun openAddTarget() {
        if (activeStage.targets.size >= 10) return
        isCreatingTarget = true
        val nextNum = activeStage.targets.size + 1
        editingTarget = StageTarget(
            id = UUID.randomUUID().toString(),
            label = "T$nextNum",
            rangeYards = (activeStage.targets.lastOrNull()?.rangeYards?.plus(50.0) ?: 200.0).coerceAtMost(1200.0),
            inclinationDeg = 0.0
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        if (isWide) {
            // Unfolded Z Fold Dual-Pane Competitive Stage Cockpit
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Left Pane: Stage Selector + Target List Editor
                LazyColumn(
                    modifier = Modifier
                        .weight(0.92f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        StageSelectorCard(
                            stageCards = uiState.stageCards,
                            activeStage = activeStage,
                            onSelectStage = { viewModel.selectStageCard(it) },
                            onNewStage = { showNewStageDialog = true },
                            onRenameStage = { showRenameStageDialog = true },
                            onDeleteStage = { showDeleteStageDialog = true },
                            onShareStage = { shareStageCardText() }
                        )
                    }
                    item {
                        StageTargetsHeaderRow(
                            count = activeStage.targets.size,
                            onAddTarget = { openAddTarget() }
                        )
                    }
                    itemsIndexed(activeStage.targets, key = { _, t -> t.id }) { idx, target ->
                        StageTargetManageRow(
                            target = target,
                            prefs = prefs,
                            canMoveUp = idx > 0,
                            canMoveDown = idx < activeStage.targets.lastIndex,
                            onMoveUp = { viewModel.moveStageTarget(activeStage.id, idx, idx - 1) },
                            onMoveDown = { viewModel.moveStageTarget(activeStage.id, idx, idx + 1) },
                            onEdit = {
                                isCreatingTarget = false
                                editingTarget = target
                            },
                            onDelete = { viewModel.deleteStageTarget(activeStage.id, target.id) }
                        )
                    }
                }

                // Right Pane: High-Contrast Live Stage Dope Board
                LazyColumn(
                    modifier = Modifier
                        .weight(1.08f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        StageDopeBoardCard(
                            stageName = activeStage.name,
                            rigSummary = "${uiState.activeFirearm.name} • ${uiState.activeAmmo.name}",
                            solvedTargets = solvedTargets,
                            prefs = prefs,
                            hasAnyMover = hasAnyMover,
                            onSelectTarget = onSendTargetToSolver
                        )
                    }
                }
            }
        } else {
            // Compact Folded Screen: High-Contrast Table on Top + Rapid Target Controls Below
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    StageSelectorCard(
                        stageCards = uiState.stageCards,
                        activeStage = activeStage,
                        onSelectStage = { viewModel.selectStageCard(it) },
                        onNewStage = { showNewStageDialog = true },
                        onRenameStage = { showRenameStageDialog = true },
                        onDeleteStage = { showDeleteStageDialog = true },
                        onShareStage = { shareStageCardText() }
                    )
                }
                item {
                    StageDopeBoardCard(
                        stageName = activeStage.name,
                        rigSummary = "${uiState.activeFirearm.name} • ${uiState.activeAmmo.name}",
                        solvedTargets = solvedTargets,
                        prefs = prefs,
                        hasAnyMover = hasAnyMover,
                        onSelectTarget = onSendTargetToSolver
                    )
                }
                item {
                    StageTargetsHeaderRow(
                        count = activeStage.targets.size,
                        onAddTarget = { openAddTarget() }
                    )
                }
                itemsIndexed(activeStage.targets, key = { _, t -> t.id }) { idx, target ->
                    StageTargetManageRow(
                        target = target,
                        prefs = prefs,
                        canMoveUp = idx > 0,
                        canMoveDown = idx < activeStage.targets.lastIndex,
                        onMoveUp = { viewModel.moveStageTarget(activeStage.id, idx, idx - 1) },
                        onMoveDown = { viewModel.moveStageTarget(activeStage.id, idx, idx + 1) },
                        onEdit = {
                            isCreatingTarget = false
                            editingTarget = target
                        },
                        onDelete = { viewModel.deleteStageTarget(activeStage.id, target.id) }
                    )
                }
            }
        }

        if (activeStage.targets.size < 10) {
            FloatingActionButton(
                onClick = { openAddTarget() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Target")
            }
        }
    }

    editingTarget?.let { target ->
        StageTargetEditorDialog(
            initial = target,
            isNew = isCreatingTarget,
            distanceUnit = prefs.distanceUnit,
            onDismiss = { editingTarget = null },
            onSave = { updated ->
                viewModel.saveStageTarget(activeStage.id, updated)
                editingTarget = null
            }
        )
    }

    if (showNewStageDialog) {
        StageNameDialog(
            title = "New Stage Card",
            initialName = "Stage ${uiState.stageCards.size + 1}",
            confirmLabel = "Create",
            onDismiss = { showNewStageDialog = false },
            onConfirm = { name ->
                viewModel.createStageCard(name)
                showNewStageDialog = false
            }
        )
    }

    if (showRenameStageDialog) {
        StageNameDialog(
            title = "Rename Stage Card",
            initialName = activeStage.name,
            confirmLabel = "Save",
            onDismiss = { showRenameStageDialog = false },
            onConfirm = { name ->
                viewModel.renameStageCard(activeStage.id, name)
                showRenameStageDialog = false
            }
        )
    }

    if (showDeleteStageDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteStageDialog = false },
            title = { Text("Delete Stage Card?") },
            text = { Text("Delete \"${activeStage.name}\" and its ${activeStage.targets.size} targets?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteStageCard(activeStage.id)
                    showDeleteStageDialog = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteStageDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StageSelectorCard(
    stageCards: List<StageCard>,
    activeStage: StageCard,
    onSelectStage: (String) -> Unit,
    onNewStage: () -> Unit,
    onRenameStage: () -> Unit,
    onDeleteStage: () -> Unit,
    onShareStage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Flag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "SAVED STAGE CARDS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onShareStage, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Share Stage", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onRenameStage, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename Stage", modifier = Modifier.size(18.dp))
                    }
                    if (stageCards.size > 1) {
                        IconButton(onClick = onDeleteStage, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Stage", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                stageCards.forEach { stage ->
                    FilterChip(
                        selected = stage.id == activeStage.id,
                        onClick = { onSelectStage(stage.id) },
                        label = { Text("${stage.name} (${stage.targets.size})") }
                    )
                }
                OutlinedButton(
                    onClick = onNewStage,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Stage", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun StageDopeBoardCard(
    stageName: String,
    rigSummary: String,
    solvedTargets: List<StageTargetSolution>,
    prefs: UserPreferences,
    hasAnyMover: Boolean,
    onSelectTarget: (StageTarget) -> Unit
) {
    GunneryHudPanel(
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
        bracketColor = MaterialTheme.colorScheme.primary
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stageName.uppercase(Locale.US),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "$rigSummary • Tap any target to load in Solver",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (solvedTargets.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No targets on this stage yet. Tap + to add up to 10 match targets.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            // Table Header
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TGT", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.75f))
                    Text("RNG", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.95f))
                    Text("ELEV (${prefs.angularUnit.shortLabel})", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.25f), textAlign = TextAlign.End)
                    Text("WIND", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.0f), textAlign = TextAlign.End)
                    if (hasAnyMover) {
                        Text("LEAD", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                    }
                    Text("TOF", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                solvedTargets.forEachIndexed { idx, item ->
                    val t = item.target
                    val s = item.solution
                    val elevRaw = UnitFormatters.elevationValue(s, prefs.angularUnit)
                    val windRaw = UnitFormatters.windageValue(s, prefs.angularUnit)
                    val elevStr = UnitFormatters.formatCompactHold(elevRaw, prefs.angularUnit, "U", "D")
                    val windStr = UnitFormatters.formatCompactHold(windRaw, prefs.angularUnit, "R", "L")
                    val clicks = abs(s.elevationClicks).roundToInt()

                    Surface(
                        color = if (idx % 2 == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTarget(t) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 9.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = t.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(0.75f),
                                    maxLines = 1
                                )
                                Column(modifier = Modifier.weight(0.95f)) {
                                    Text(
                                        text = UnitFormatters.formatDistance(t.rangeYards, prefs.distanceUnit),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    if (abs(t.inclinationDeg) >= 0.5) {
                                        Text(
                                            text = String.format(Locale.US, "%+.0f°", t.inclinationDeg),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1.25f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = elevStr,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "$clicks clk",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1.0f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = windStr,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        maxLines = 1
                                    )
                                    if (t.windSpeedMph != null) {
                                        val dir = t.windDirectionDeg ?: 90.0
                                        Text(
                                            text = "${t.windSpeedMph.roundToInt()}@${UnitFormatters.windClockHour(dir)}:00",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (hasAnyMover) {
                                    val leadText = if (t.moverSpeedMph > 0.1) {
                                        val lv = when (prefs.angularUnit) {
                                            AngularUnit.MRAD -> s.moverLeadMrad
                                            AngularUnit.INCHES -> s.moverLeadInches
                                            AngularUnit.CENTIMETERS -> s.moverLeadInches * 2.54
                                            AngularUnit.MOA -> s.moverLeadMoa
                                        }
                                        String.format(Locale.US, "%.2f", lv)
                                    } else "—"
                                    Text(
                                        text = leadText,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.weight(0.8f),
                                        textAlign = TextAlign.End
                                    )
                                }
                                Text(
                                    text = String.format(Locale.US, "%.2fs", s.timeOfFlightSec),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(0.7f),
                                    textAlign = TextAlign.End
                                )
                            }
                            if (t.notes.isNotBlank()) {
                                Text(
                                    text = t.notes,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StageTargetsHeaderRow(
    count: Int,
    onAddTarget: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "STAGE TARGETS ($count / 10)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        if (count < 10) {
            TextButton(onClick = onAddTarget) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Target")
            }
        }
    }
}

@Composable
private fun StageTargetManageRow(
    target: StageTarget,
    prefs: UserPreferences,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val inclStr = if (abs(target.inclinationDeg) >= 0.5) String.format(Locale.US, " • %+.0f°", target.inclinationDeg) else ""
                val windStr = target.windSpeedMph?.let { spd ->
                    val hr = UnitFormatters.windClockHour(target.windDirectionDeg ?: 90.0)
                    " • Wind ${spd.roundToInt()} mph @ $hr:00"
                } ?: " • Live Wind"
                val moverStr = if (target.moverSpeedMph > 0.1) " • Mover ${target.moverSpeedMph.roundToInt()} mph" else ""
                Text(
                    text = "${target.label} — ${UnitFormatters.formatDistance(target.rangeYards, prefs.distanceUnit)}$inclStr",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$windStr$moverStr".removePrefix(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun StageTargetEditorDialog(
    initial: StageTarget,
    isNew: Boolean,
    distanceUnit: DistanceUnit,
    onDismiss: () -> Unit,
    onSave: (StageTarget) -> Unit
) {
    var label by remember { mutableStateOf(initial.label) }
    var rangeText by remember {
        mutableStateOf(UnitFormatters.distanceValue(initial.rangeYards, distanceUnit).roundToInt().toString())
    }
    var inclText by remember { mutableStateOf(String.format(Locale.US, "%.0f", initial.inclinationDeg)) }
    var customWind by remember { mutableStateOf(initial.windSpeedMph != null) }
    var windSpeedText by remember { mutableStateOf(String.format(Locale.US, "%.0f", initial.windSpeedMph ?: 8.0)) }
    var windClockText by remember { mutableStateOf(String.format(Locale.US, "%.0f", initial.windDirectionDeg ?: 90.0)) }
    var moverText by remember { mutableStateOf(if (initial.moverSpeedMph > 0.1) String.format(Locale.US, "%.1f", initial.moverSpeedMph) else "") }
    var notes by remember { mutableStateOf(initial.notes) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add Stage Target" else "Edit ${initial.label}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Label") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                    OutlinedTextField(
                        value = rangeText,
                        onValueChange = { rangeText = it; error = null },
                        label = { Text("Range (${distanceUnit.shortLabel})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inclText,
                        onValueChange = { inclText = it },
                        label = { Text("Inclination (°)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = moverText,
                        onValueChange = { moverText = it },
                        label = { Text("Mover (mph)") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Custom Target Wind Override", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = customWind, onCheckedChange = { customWind = it })
                }
                if (customWind) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = windSpeedText,
                            onValueChange = { windSpeedText = it },
                            label = { Text("Wind (mph)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = windClockText,
                            onValueChange = { windClockText = it },
                            label = { Text("Clock (°)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Target Notes (e.g. Steel IPSC, Barricade)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedRange = rangeText.toDoubleOrNull()
                if (parsedRange == null || parsedRange < 5.0 || parsedRange > 1800.0) {
                    error = "Enter a valid range (5–1800 ${distanceUnit.shortLabel})."
                    return@Button
                }
                val rangeYards = UnitFormatters.toYards(parsedRange, distanceUnit)
                val incl = (inclText.toDoubleOrNull() ?: 0.0).coerceIn(-60.0, 60.0)
                val mover = (moverText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 30.0)
                val wSpd = if (customWind) (windSpeedText.toDoubleOrNull() ?: 5.0).coerceIn(0.0, 50.0) else null
                val wDir = if (customWind) (((windClockText.toDoubleOrNull() ?: 90.0) % 360.0) + 360.0) % 360.0 else null
                onSave(
                    initial.copy(
                        label = label.trim().ifBlank { "T" },
                        rangeYards = rangeYards,
                        inclinationDeg = incl,
                        windSpeedMph = wSpd,
                        windDirectionDeg = wDir,
                        moverSpeedMph = mover,
                        notes = notes.trim()
                    )
                )
            }) { Text("Save Target") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun StageNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Stage Card Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
