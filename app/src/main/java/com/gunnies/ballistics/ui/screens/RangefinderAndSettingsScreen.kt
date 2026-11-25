package com.gunnies.ballistics.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gunnies.ballistics.R
import com.gunnies.ballistics.ble.BleConnectionState
import com.gunnies.ballistics.ble.GattCharacteristicInfo
import com.gunnies.ballistics.model.AngularUnit
import com.gunnies.ballistics.model.AppThemeMode
import com.gunnies.ballistics.model.ArsenalBackup
import com.gunnies.ballistics.model.BackupImportMode
import com.gunnies.ballistics.model.DistanceUnit
import com.gunnies.ballistics.model.PressureUnit
import com.gunnies.ballistics.model.TemperatureUnit
import com.gunnies.ballistics.model.UiTextScale
import com.gunnies.ballistics.model.VelocityUnit
import com.gunnies.ballistics.model.WindUnit
import com.gunnies.ballistics.ui.AppUiState
import com.gunnies.ballistics.ui.BallisticsViewModel
import com.gunnies.ballistics.ui.components.GunneryHudPanel
import com.gunnies.ballistics.ui.components.HardwareThumbBadge

@Composable
fun RangefinderScreen(
    uiState: AppUiState,
    viewModel: BallisticsViewModel,
    onRequestBleScan: () -> Unit,
    onRequestBleConnect: (address: String, name: String) -> Unit
) {
    val context = LocalContext.current
    val ble = uiState.bleState
    val prefs = uiState.preferences
    var showAllDevices by remember { mutableStateOf(true) }
    var diagnosticsExpanded by rememberSaveable { mutableStateOf(false) }
    var writeTarget by remember { mutableStateOf<GattCharacteristicInfo?>(null) }

    val visibleDevices = remember(ble.scannedDevices, showAllDevices) {
        if (showAllDevices) ble.scannedDevices
        else ble.scannedDevices.filter { it.isLikelyBushnell }
    }
    val reversedLogs = remember(ble.packetLogs) { ble.packetLogs.reversed() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        BleConnectionCard(
                            uiState = uiState,
                            viewModel = viewModel,
                            onRequestBleScan = onRequestBleScan,
                            onRequestBleConnect = onRequestBleConnect
                        )
                    }

                    if (ble.scannedDevices.isNotEmpty()) {
                        item {
                            NearbyDevicesHeader(
                                count = visibleDevices.size,
                                showAllDevices = showAllDevices,
                                onToggleShowAll = { showAllDevices = !showAllDevices }
                            )
                        }
                        items(visibleDevices, key = { it.address }) { dev ->
                            ScannedDeviceRow(
                                name = dev.name,
                                address = dev.address,
                                rssi = dev.rssi,
                                isLikelyBushnell = dev.isLikelyBushnell,
                                onConnect = { onRequestBleConnect(dev.address, dev.name) }
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        AdvancedDiagnosticsHeaderCard(
                            expanded = diagnosticsExpanded,
                            serviceCount = ble.services.size,
                            logCount = ble.packetLogs.size,
                            onToggle = { diagnosticsExpanded = !diagnosticsExpanded },
                            onSimulatePing = { viewModel.bleManager.simulateRangePing(425.0, -14.0) },
                            onExportDump = {
                                val dump = viewModel.bleManager.buildDiagnosticDump()
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "LBC1800 BLE GATT Dump")
                                    putExtra(Intent.EXTRA_TEXT, dump)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share BLE Diagnostic Dump"))
                            },
                            onClearLogs = { viewModel.bleManager.clearLogs() }
                        )
                    }

                    if (diagnosticsExpanded) {
                        if (ble.services.isNotEmpty()) {
                            items(ble.services, key = { it.uuid }) { svc ->
                                GattServiceCard(
                                    service = svc,
                                    onRead = { svcUuid, chUuid -> viewModel.bleManager.readCharacteristic(svcUuid, chUuid) },
                                    onWrite = { ch -> writeTarget = ch }
                                )
                            }
                        }

                        if (reversedLogs.isEmpty()) {
                            item { EmptyPacketLogNotice() }
                        } else {
                            itemsIndexed(
                                items = reversedLogs,
                                key = { idx, log -> "${reversedLogs.size - 1 - idx}_${log.timestamp}_${log.direction}" }
                            ) { _, log ->
                                PacketLogRow(log)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    BleConnectionCard(
                        uiState = uiState,
                        viewModel = viewModel,
                        onRequestBleScan = onRequestBleScan,
                        onRequestBleConnect = onRequestBleConnect
                    )
                }

                if (ble.scannedDevices.isNotEmpty()) {
                    item {
                        NearbyDevicesHeader(
                            count = visibleDevices.size,
                            showAllDevices = showAllDevices,
                            onToggleShowAll = { showAllDevices = !showAllDevices }
                        )
                    }
                    items(visibleDevices, key = { it.address }) { dev ->
                        ScannedDeviceRow(
                            name = dev.name,
                            address = dev.address,
                            rssi = dev.rssi,
                            isLikelyBushnell = dev.isLikelyBushnell,
                            onConnect = { onRequestBleConnect(dev.address, dev.name) }
                        )
                    }
                }

                item {
                    AdvancedDiagnosticsHeaderCard(
                        expanded = diagnosticsExpanded,
                        serviceCount = ble.services.size,
                        logCount = ble.packetLogs.size,
                        onToggle = { diagnosticsExpanded = !diagnosticsExpanded },
                        onSimulatePing = { viewModel.bleManager.simulateRangePing(425.0, -14.0) },
                        onExportDump = {
                            val dump = viewModel.bleManager.buildDiagnosticDump()
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "LBC1800 BLE GATT Dump")
                                putExtra(Intent.EXTRA_TEXT, dump)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share BLE Diagnostic Dump"))
                        },
                        onClearLogs = { viewModel.bleManager.clearLogs() }
                    )
                }

                if (diagnosticsExpanded) {
                    if (ble.services.isNotEmpty()) {
                        items(ble.services, key = { it.uuid }) { svc ->
                            GattServiceCard(
                                service = svc,
                                onRead = { svcUuid, chUuid -> viewModel.bleManager.readCharacteristic(svcUuid, chUuid) },
                                onWrite = { ch -> writeTarget = ch }
                            )
                        }
                    }

                    if (reversedLogs.isEmpty()) {
                        item { EmptyPacketLogNotice() }
                    } else {
                        itemsIndexed(
                            items = reversedLogs,
                            key = { idx, log -> "${reversedLogs.size - 1 - idx}_${log.timestamp}_${log.direction}" }
                        ) { _, log ->
                            PacketLogRow(log)
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    writeTarget?.let { target ->
        var payloadText by remember { mutableStateOf("") }
        var asHex by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { writeTarget = null },
            title = { Text("Write Characteristic") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = target.uuid,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hex bytes (e.g. AA 01 FF)", modifier = Modifier.weight(1f))
                        Switch(checked = asHex, onCheckedChange = { asHex = it })
                    }
                    OutlinedTextField(
                        value = payloadText,
                        onValueChange = { payloadText = it },
                        label = { Text(if (asHex) "Hex payload" else "ASCII text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val bytes = if (asHex) {
                        payloadText.trim().split(Regex("\\s+"))
                            .mapNotNull { it.toIntOrNull(16)?.toByte() }
                            .toByteArray()
                    } else {
                        payloadText.toByteArray(Charsets.UTF_8)
                    }
                    if (bytes.isNotEmpty()) {
                        viewModel.bleManager.writeCharacteristic(target.serviceUuid, target.uuid, bytes)
                    }
                    writeTarget = null
                }) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { writeTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BleConnectionCard(
    uiState: AppUiState,
    viewModel: BallisticsViewModel,
    onRequestBleScan: () -> Unit,
    onRequestBleConnect: (address: String, name: String) -> Unit
) {
    val ble = uiState.bleState
    val prefs = uiState.preferences
    val isNightRed = prefs.themeMode == AppThemeMode.NIGHT_RED
    val accentBorder = if (ble.connectionState == BleConnectionState.CONNECTED) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.outline
    }

    GunneryHudPanel(
        borderColor = accentBorder,
        bracketColor = if (ble.connectionState == BleConnectionState.CONNECTED) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.primary
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HardwareThumbBadge(
                drawableResId = R.drawable.img_rangefinder_bc1800,
                contentDescription = "Bushnell Bone Collector 1800",
                isNightRed = isNightRed,
                size = 64.dp,
                accentColor = if (ble.connectionState == BleConnectionState.CONNECTED) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (ble.connectionState) {
                            BleConnectionState.CONNECTED -> Icons.Default.BluetoothConnected
                            BleConnectionState.SCANNING -> Icons.AutoMirrored.Filled.BluetoothSearching
                            else -> Icons.Default.Bluetooth
                        },
                        contentDescription = null,
                        tint = if (ble.connectionState == BleConnectionState.CONNECTED) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "BONE COLLECTOR 1800",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = ble.connectionState.label +
                        (ble.connectedDeviceName?.let { " — $it" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

            Text(
                text = ble.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (ble.firmwareVersion != null || ble.lastSyncMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        ble.firmwareVersion?.let { fw ->
                            Text(
                                text = "LBC1800 Firmware: $fw",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        ble.lastSyncMessage?.let { syncMsg ->
                            Text(
                                text = syncMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            if (ble.connectionState == BleConnectionState.CONNECTED) {
                Button(
                    onClick = { viewModel.syncActiveProfileToRangefinder() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sync ${uiState.activeFirearm.name} + ${uiState.activeAmmo.name}")
                }
                OutlinedButton(
                    onClick = { viewModel.queryRangefinderInfo() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Refresh Device Info (:DU + :DD)")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (ble.connectionState == BleConnectionState.SCANNING) {
                    OutlinedButton(
                        onClick = { viewModel.bleManager.stopScan() },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop Scan")
                    }
                } else {
                    Button(
                        onClick = onRequestBleScan,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Scan BLE")
                    }
                }

                if (ble.connectionState == BleConnectionState.CONNECTED ||
                    ble.connectionState == BleConnectionState.CONNECTING ||
                    ble.connectionState == BleConnectionState.DISCOVERING
                ) {
                    OutlinedButton(
                        onClick = { viewModel.bleManager.disconnect() },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Disconnect")
                    }
                } else if (!prefs.lastConnectedBleAddress.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            onRequestBleConnect(
                                prefs.lastConnectedBleAddress,
                                prefs.lastConnectedBleName ?: "LBC1800"
                            )
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Reconnect")
                    }
                }
            }
    }
}

@Composable
private fun NearbyDevicesHeader(
    count: Int,
    showAllDevices: Boolean,
    onToggleShowAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "NEARBY BLE DEVICES ($count)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        TextButton(onClick = onToggleShowAll) {
            Text(if (showAllDevices) "Bushnell Only" else "Show All")
        }
    }
}

@Composable
private fun ScannedDeviceRow(
    name: String,
    address: String,
    rssi: Int,
    isLikelyBushnell: Boolean,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLikelyBushnell) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isLikelyBushnell) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "$address  •  $rssi dBm" + if (isLikelyBushnell) "  •  Rangefinder" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onConnect, shape = MaterialTheme.shapes.small) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun AdvancedDiagnosticsHeaderCard(
    expanded: Boolean,
    serviceCount: Int,
    logCount: Int,
    onToggle: () -> Unit,
    onSimulatePing: () -> Unit,
    onExportDump: () -> Unit,
    onClearLogs: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ADVANCED BLE DIAGNOSTICS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "$serviceCount GATT services • $logCount packet logs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSimulatePing,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Test Ping")
                        }
                        OutlinedButton(
                            onClick = onExportDump,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export Dump")
                        }
                    }
                    if (logCount > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onClearLogs) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Clear Packet Logs")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GattServiceCard(
    service: com.gunnies.ballistics.ble.GattServiceInfo,
    onRead: (serviceUuid: String, charUuid: String) -> Unit,
    onWrite: (GattCharacteristicInfo) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = service.friendlyName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = service.uuid,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            service.characteristics.forEach { ch ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = ch.uuid,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ch.propertiesLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (ch.canRead) {
                                OutlinedButton(
                                    onClick = { onRead(ch.serviceUuid, ch.uuid) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Read", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (ch.canWrite) {
                                OutlinedButton(
                                    onClick = { onWrite(ch) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Write", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    if (ch.lastValueHex.isNotEmpty()) {
                        Text(
                            text = "HEX: ${ch.lastValueHex}\nASCII: ${ch.lastValueAscii}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPacketLogNotice() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "No BLE packets logged yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun PacketLogRow(log: com.gunnies.ballistics.ble.BlePacketLog) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "[${log.timestamp}] ${log.direction} • ${log.characteristicUuid} • ${log.note}",
                style = MaterialTheme.typography.labelSmall,
                color = when (log.direction) {
                    "RX" -> MaterialTheme.colorScheme.secondary
                    "TX" -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (log.hex.isNotEmpty()) {
                Text(
                    text = "HEX: ${log.hex}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (log.ascii.isNotEmpty()) {
                Text(
                    text = "TXT: ${log.ascii}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferencesScreen(
    uiState: AppUiState,
    viewModel: BallisticsViewModel
) {
    val prefs = uiState.preferences
    var pendingImport by remember { mutableStateOf<Pair<String, ArsenalBackup>?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var showModelGuide by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportArsenalToUri(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.readBackupPreviewFromUri(uri) { result ->
                result.onSuccess { pair ->
                    importError = null
                    pendingImport = pair
                }.onFailure { err ->
                    importError = err.message ?: "Selected file is not a valid Gunnies Ballistics backup (.json)"
                }
            }
        }
    }

    val launchExport = {
        val dateTag = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        exportLauncher.launch("gunnies_ballistics_backup_$dateTag.json")
    }
    val launchImport = {
        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DisplayAndScalingCard(prefs = prefs, viewModel = viewModel)
                    SolverPhysicsCard(prefs = prefs, viewModel = viewModel)
                    ArsenalBackupCard(
                        uiState = uiState,
                        importError = importError,
                        onExport = launchExport,
                        onImport = launchImport
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BallisticUnitsCard(prefs = prefs, viewModel = viewModel)
                    BallisticModelAboutCard(onOpenFullGuide = { showModelGuide = true })
                    Spacer(Modifier.height(12.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { DisplayAndScalingCard(prefs = prefs, viewModel = viewModel) }
                item { SolverPhysicsCard(prefs = prefs, viewModel = viewModel) }
                item {
                    ArsenalBackupCard(
                        uiState = uiState,
                        importError = importError,
                        onExport = launchExport,
                        onImport = launchImport
                    )
                }
                item { BallisticUnitsCard(prefs = prefs, viewModel = viewModel) }
                item { BallisticModelAboutCard(onOpenFullGuide = { showModelGuide = true }) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showModelGuide) {
        BallisticModelGuideDialog(onDismiss = { showModelGuide = false })
    }

    pendingImport?.let { (rawJson, preview) ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import Arsenal Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Backup v${preview.schemaVersion} ready to import:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${preview.firearms.size} Guns • ${preview.scopes.size} Scopes • " +
                                "${preview.ammoLoads.size} Ammo • ${preview.suppressors.size} Suppressors • " +
                                "${preview.stageCards.size} Stage Cards • ${preview.sessionLogs.size} Session Logs",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Text(
                        text = "• Merge keeps all your existing profiles and adds any new items from the backup.\n" +
                            "• Overwrite replaces your entire database and preferences with the backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        viewModel.importArsenalJson(rawJson, BackupImportMode.OVERWRITE)
                        pendingImport = null
                    }) { Text("Overwrite") }
                    Button(onClick = {
                        viewModel.importArsenalJson(rawJson, BackupImportMode.MERGE)
                        pendingImport = null
                    }) { Text("Merge") }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ArsenalBackupCard(
    uiState: AppUiState,
    importError: String?,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    SettingsCard(title = "ARSENAL BACKUP & RESTORE (JSON)") {
        Text(
            text = "${uiState.firearms.size} Guns • ${uiState.scopes.size} Scopes • ${uiState.ammoLoads.size} Ammo • " +
                "${uiState.suppressors.size} Cans • ${uiState.stageCards.size} Stages • ${uiState.sessionLogs.size} Logs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Export your complete trued arsenal, Multi-BC/DSF data, Stage Cards, Cold Bore logs, and Preferences to a versioned .json file.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onExport,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export (.json)")
            }
            OutlinedButton(
                onClick = onImport,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import (.json)")
            }
        }
        importError?.let { err ->
            Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplayAndScalingCard(
    prefs: com.gunnies.ballistics.model.UserPreferences,
    viewModel: BallisticsViewModel
) {
    SettingsCard(title = "DISPLAY & SCALING") {
        Text("Field Theme", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = prefs.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    label = { Text(mode.label) }
                )
            }
        }
        Text(
            text = prefs.themeMode.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))
        Text("App Text Size", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            UiTextScale.entries.forEach { scale ->
                FilterChip(
                    selected = prefs.textScale == scale,
                    onClick = { viewModel.setTextScale(scale) },
                    label = { Text(scale.label) }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SettingsToggleRow(
            title = "Keep Screen Awake",
            subtitle = "Prevent timeout while viewing Solver HUD",
            checked = prefs.keepScreenAwake,
            onCheckedChange = { viewModel.setKeepScreenAwake(it) }
        )
    }
}

@Composable
private fun SolverPhysicsCard(
    prefs: com.gunnies.ballistics.model.UserPreferences,
    viewModel: BallisticsViewModel
) {
    SettingsCard(title = "PRECISION PHYSICS") {
        SettingsToggleRow(
            title = "Auto-Match Scope Unit",
            subtitle = "Switch MOA/MRAD automatically to match selected optic clicks",
            checked = prefs.autoMatchScopeUnit,
            onCheckedChange = { viewModel.setAutoMatchScopeUnit(it) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsToggleRow(
            title = "Spin Drift",
            subtitle = "Gyroscopic lateral drift from barrel twist & bullet length",
            checked = prefs.includeSpinDrift,
            onCheckedChange = { viewModel.setIncludeSpinDrift(it) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsToggleRow(
            title = "Aerodynamic Jump",
            subtitle = "Vertical deflection induced by crosswind & bullet spin",
            checked = prefs.includeAeroJump,
            onCheckedChange = { viewModel.setIncludeAeroJump(it) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsToggleRow(
            title = "Coriolis Effect",
            subtitle = "Earth-rotation deflection using GPS latitude & target bearing",
            checked = prefs.includeCoriolis,
            onCheckedChange = { viewModel.setIncludeCoriolis(it) }
        )
    }
}

@Composable
private fun BallisticUnitsCard(
    prefs: com.gunnies.ballistics.model.UserPreferences,
    viewModel: BallisticsViewModel
) {
    SettingsCard(title = "BALLISTIC UNITS") {
        UnitChipGroup(
            label = "Distance",
            options = DistanceUnit.entries,
            selected = prefs.distanceUnit,
            optionLabel = { "${it.fullLabel} (${it.shortLabel})" },
            onSelect = { viewModel.setDistanceUnit(it) }
        )
        UnitChipGroup(
            label = "Elevation & Windage Hold",
            options = AngularUnit.entries,
            selected = prefs.angularUnit,
            optionLabel = { it.fullLabel },
            onSelect = { viewModel.setAngularUnit(it) }
        )
        UnitChipGroup(
            label = "Velocity & Energy",
            options = VelocityUnit.entries,
            selected = prefs.velocityUnit,
            optionLabel = { if (it == VelocityUnit.FPS) "fps / ft-lb" else "m/s / Joules" },
            onSelect = { viewModel.setVelocityUnit(it) }
        )
        UnitChipGroup(
            label = "Temperature",
            options = TemperatureUnit.entries,
            selected = prefs.temperatureUnit,
            optionLabel = { it.shortLabel },
            onSelect = { viewModel.setTemperatureUnit(it) }
        )
        UnitChipGroup(
            label = "Barometric Pressure",
            options = PressureUnit.entries,
            selected = prefs.pressureUnit,
            optionLabel = { it.shortLabel },
            onSelect = { viewModel.setPressureUnit(it) }
        )
        UnitChipGroup(
            label = "Wind Speed",
            options = WindUnit.entries,
            selected = prefs.windUnit,
            optionLabel = { it.shortLabel },
            onSelect = { viewModel.setWindUnit(it) }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> UnitChipGroup(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { opt ->
                FilterChip(
                    selected = opt == selected,
                    onClick = { onSelect(opt) },
                    label = { Text(optionLabel(opt)) }
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BallisticModelAboutCard(
    onOpenFullGuide: () -> Unit
) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"
    }

    SettingsCard(title = "ABOUT & BALLISTIC MODEL (3-DOF+)") {
        Text(
            text = "Gunnies Ballistics v$versionName",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Gunnies uses a 3-DOF+ point-mass trajectory solver: it integrates 3D bullet motion (downrange, drop, and lateral drift) at 1 ms RK4 steps using standard BRL/JBM G1 or G7 drag tables, plus optional empirical corrections for Spin Drift, Aerodynamic Jump, Coriolis, velocity-banded BCs, and transonic/subsonic Drop Scale Factor (DSF).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Key notes: Coriolis activates only after GPS latitude is synced via Weather → Sync GPS. Spin Drift and Aero Jump apply in Rifle mode when barrel twist direction is set. DSF scales vertical drop (primarily at Mach ≤ 1.20) without altering wind drift or velocity.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = onOpenFullGuide,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Read Full Ballistic Model & Limitations")
        }
    }
}

@Composable
private fun BallisticModelGuideDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text("How the 3-DOF+ Solver Works")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ModelSection(
                    heading = "What \"3-DOF+\" Means",
                    body = "A 3-DOF (three degrees of freedom) solver tracks the bullet's 3D position and velocity in 0.001-second RK4 steps under gravity, air drag, and 3D wind. The \"+\" means Gunnies layers rotational and Earth-frame effects—Spin Drift, Aerodynamic Jump, Coriolis, stepped Multi-BC, and Drop Scale Factor—on top of the point-mass trajectory rather than simulating full 6-DOF bullet pitch and yaw moments."
                )
                ModelSection(
                    heading = "Drag Models & Velocity-Banded BC",
                    body = "Uses standard BRL/JBM G1 (79 Mach/Cd points) and G7 (80 Mach/Cd points) curves from Mach 0.00 to 5.00 with linear interpolation. Loads can use a single BC or up to 5 velocity-banded BCs that blend linearly over ±20 fps across each velocity boundary."
                )
                ModelSection(
                    heading = "Atmosphere, Inclination & Wind",
                    body = "Computes humid ICAO air density, density altitude, and humidity-adjusted speed of sound from temperature, humidity, and station pressure (or sea-level pressure + altitude). Adjusts muzzle velocity for powder temperature sensitivity (fps/°F) and suppressor shift. Inclined shots decompose both gravity and wind into the tilted line-of-sight frame (±75° in solver; ±60° via phone inclinometer). Wind supports Single Wind or 3-Zone Wind (Near 0–33%, Mid 33–66%, Far 66–100%) plus Wind Gust Bracket."
                )
                ModelSection(
                    heading = "When Optional Corrections Apply",
                    body = "• Spin Drift: Rifle mode with Left or Right barrel twist and barrel twist > 0 (Miller stability factor Sg + Litz empirical drift).\n" +
                        "• Aerodynamic Jump: Rifle mode with Left or Right twist, computed from Near-zone crosswind and Sg.\n" +
                        "• Coriolis (horizontal + Eötvös vertical): Rifle mode only after GPS latitude is populated via Weather → Sync GPS, combined with your target bearing.\n" +
                        "• Two-Stage Truing: Stage 1 trues Muzzle Velocity from observed supersonic drop; Stage 2 locks MV and solves Drop Scale Factor (DSF) at Mach ≤ 1.20 (up to 6 Mach nodes)."
                )
                ModelSection(
                    heading = "Known Limitations",
                    body = "• Not a 6-DOF rigid-body or custom Doppler radar (CDM) solver; accuracy depends on accurate inputs and field truing.\n" +
                        "• Zero angle is solved at calm wind (0 mph) under your scope's zero atmosphere without applying DSF.\n" +
                        "• DSF scales vertical drop after integration and does not alter wind drift, remaining velocity, or time of flight.\n" +
                        "• On DOPE Range Cards, 3-Zone Wind splits Near/Mid/Far thirds across the card's maximum distance.\n" +
                        "• If bullet length is left blank, gyroscopic stability (Sg) uses a caliber/weight estimate.\n" +
                        "• Sync GPS Weather requires GPS + internet (saved defaults and manual weather work offline). Optional Bushnell LBC1800 BLE sync sends only the primary BC and range-weighted average wind, and the rangefinder's onboard solver caps at 800 yards."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun ModelSection(
    heading: String,
    body: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = heading,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
