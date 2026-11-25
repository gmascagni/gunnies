package com.gunnies.ballistics.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import com.gunnies.ballistics.data.UnitFormatters
import com.gunnies.ballistics.model.Atmosphere
import com.gunnies.ballistics.model.RifleProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ScannedBleDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val isLikelyBushnell: Boolean,
    val serviceUuids: List<String> = emptyList()
)

data class GattCharacteristicInfo(
    val uuid: String,
    val serviceUuid: String,
    val propertiesLabel: String,
    val canRead: Boolean,
    val canWrite: Boolean,
    val lastValueHex: String = "",
    val lastValueAscii: String = ""
)

data class GattServiceInfo(
    val uuid: String,
    val friendlyName: String,
    val characteristics: List<GattCharacteristicInfo>
)

data class BlePacketLog(
    val timestamp: String,
    val direction: String, // "RX", "TX", "SYS"
    val characteristicUuid: String,
    val hex: String,
    val ascii: String,
    val note: String = ""
)

data class RangefinderReading(
    val distanceYards: Double,
    val angleDegrees: Double?,
    val rawSummary: String,
    val timestamp: String
)

enum class BleConnectionState(val label: String) {
    DISCONNECTED("Disconnected"),
    SCANNING("Scanning..."),
    CONNECTING("Connecting..."),
    DISCOVERING("Discovering GATT..."),
    CONNECTED("Connected")
}

data class BleUiState(
    val connectionState: BleConnectionState = BleConnectionState.DISCONNECTED,
    val scannedDevices: List<ScannedBleDevice> = emptyList(),
    val connectedDeviceName: String? = null,
    val connectedDeviceAddress: String? = null,
    val firmwareVersion: String? = null,
    val lastSyncMessage: String? = null,
    val ackCount: Int = 0,
    val services: List<GattServiceInfo> = emptyList(),
    val packetLogs: List<BlePacketLog> = emptyList(),
    val statusMessage: String = "Press Fire on your Bone Collector 1800 to wake Bluetooth, then tap Scan."
)

/**
 * Thread-safe Native Android BLE manager + GATT Inspector for Bushnell Bone Collector 1800 (LBC1800).
 * All mutable GATT state and serial write/read queues are serialized under [gattLock] and
 * paced cleanly across BLE callbacks so Main and Binder threads never race.
 */
class RangefinderBleManager(private val context: Context) {

    companion object {
        private const val TAG = "Gunnies_BLE"
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val NUS_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"

        private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.US)
        private val DIST_REGEX = Regex("""(?i)(?:rng|dist|d|los|yd|m)\s*[:=]\s*(\d+(?:\.\d+)?)""")
        private val ANG_REGEX = Regex("""(?i)(?:ang|inc|a|pitch)\s*[:=]\s*(-?\d+(?:\.\d+)?)""")

        private val BUSHNELL_KEYWORDS = listOf(
            "bc-", "bushnell", "lbc1800", "bc1800", "bone", "collector",
            "nitro", "fusion", "conx", "elite", "applied", "ab-"
        )
    }

    private data class QueuedWrite(
        val serviceUuid: UUID,
        val charUuid: UUID,
        val payload: ByteArray,
        val delayMsAfter: Long = 0L
    )

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val _state = MutableStateFlow(BleUiState())
    val state: StateFlow<BleUiState> = _state.asStateFlow()

    private val gattLock = Any()
    private val pacingExecutor = Executors.newSingleThreadScheduledExecutor()

    private var activeGatt: BluetoothGatt? = null
    private var activeScanCallback: ScanCallback? = null
    private val pendingNotificationQueue = ArrayDeque<BluetoothGattCharacteristic>()
    private val pendingReadQueue = ArrayDeque<BluetoothGattCharacteristic>()
    private val pendingWrites = ArrayDeque<QueuedWrite>()
    private var writeInFlight = false
    private val hdlcDeframer = Lbc1800Protocol.StreamDeframer()
    private val rawAsciiLineBuffer = StringBuilder()

    @Volatile
    private var onReadingCallback: ((RangefinderReading) -> Unit)? = null

    fun setOnReadingListener(listener: (RangefinderReading) -> Unit) {
        onReadingCallback = listener
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _state.update {
                it.copy(statusMessage = "Bluetooth is off. Enable Bluetooth in Android settings.")
            }
            return
        }

        stopScan()

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _state.update { it.copy(statusMessage = "BLE scanner unavailable on this device.") }
            return
        }

        val discoveredMap = LinkedHashMap<String, ScannedBleDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val address = device.address ?: return
                val rawName = runCatching {
                    result.scanRecord?.deviceName ?: device.name
                }.getOrNull() ?: "Unnamed BLE Device"
                val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString() }.orEmpty()
                val lower = rawName.lowercase(Locale.US)
                val isBushnell = BUSHNELL_KEYWORDS.any { lower.contains(it) } ||
                    serviceUuids.any {
                        it.equals(Lbc1800Protocol.SERVICE_UUID, ignoreCase = true) ||
                            it.equals(NUS_SERVICE_UUID, ignoreCase = true)
                    }

                synchronized(discoveredMap) {
                    discoveredMap[address] = ScannedBleDevice(
                        address = address,
                        name = rawName,
                        rssi = result.rssi,
                        isLikelyBushnell = isBushnell,
                        serviceUuids = serviceUuids
                    )
                    val sorted = discoveredMap.values.sortedWith(
                        compareByDescending<ScannedBleDevice> { it.isLikelyBushnell }
                            .thenByDescending { it.rssi }
                    )
                    _state.update {
                        it.copy(
                            scannedDevices = sorted,
                            statusMessage = if (sorted.any { d -> d.isLikelyBushnell }) {
                                "Found Bone Collector rangefinder candidate — tap Connect."
                            } else {
                                "Scanning (${sorted.size} BLE nearby)... Wake LBC1800 by pressing Fire."
                            }
                        )
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                _state.update {
                    it.copy(
                        connectionState = BleConnectionState.DISCONNECTED,
                        statusMessage = "BLE scan failed (code $errorCode). Ensure Bluetooth & permissions are enabled."
                    )
                }
            }
        }

        activeScanCallback = callback
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        runCatching {
            scanner.startScan(null, settings, callback)
            _state.update {
                it.copy(
                    connectionState = BleConnectionState.SCANNING,
                    scannedDevices = emptyList(),
                    statusMessage = "Scanning for Bushnell Bone Collector 1800..."
                )
            }
            appendLog("SYS", "-", "", "", "BLE scan started")
        }.onFailure { err ->
            _state.update {
                it.copy(
                    connectionState = BleConnectionState.DISCONNECTED,
                    statusMessage = "Scan error: ${err.message}"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val cb = activeScanCallback ?: return
        runCatching { bluetoothAdapter?.bluetoothLeScanner?.stopScan(cb) }
        activeScanCallback = null
        if (_state.value.connectionState == BleConnectionState.SCANNING) {
            _state.update {
                it.copy(
                    connectionState = BleConnectionState.DISCONNECTED,
                    statusMessage = "Scan stopped."
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, displayName: String? = null) {
        stopScan()
        disconnect()

        val adapter = bluetoothAdapter ?: return
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            _state.update { it.copy(statusMessage = "Invalid BLE address: $address") }
            return
        }

        val resolvedName = displayName ?: runCatching { device.name }.getOrNull() ?: address
        _state.update {
            it.copy(
                connectionState = BleConnectionState.CONNECTING,
                connectedDeviceAddress = address,
                connectedDeviceName = resolvedName,
                firmwareVersion = null,
                lastSyncMessage = null,
                services = emptyList(),
                statusMessage = "Connecting to $resolvedName..."
            )
        }
        appendLog("SYS", "-", "", "", "Connecting to $address")

        val gatt = runCatching {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }.getOrElse { err ->
            _state.update {
                it.copy(
                    connectionState = BleConnectionState.DISCONNECTED,
                    statusMessage = "Bluetooth permission or connection error: ${err.message}"
                )
            }
            return
        }
        synchronized(gattLock) {
            activeGatt = gatt
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val gattToClose: BluetoothGatt?
        synchronized(gattLock) {
            pendingNotificationQueue.clear()
            pendingReadQueue.clear()
            pendingWrites.clear()
            writeInFlight = false
            hdlcDeframer.reset()
            rawAsciiLineBuffer.clear()
            gattToClose = activeGatt
            activeGatt = null
        }
        gattToClose?.let { gatt ->
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
        _state.update {
            it.copy(
                connectionState = BleConnectionState.DISCONNECTED,
                services = emptyList(),
                statusMessage = "Disconnected."
            )
        }
    }

    /** Sends the verified Bone Collector 1800 unlock + device query sequence (:DU + :DD). */
    fun queryLbc1800DeviceInfo() {
        enqueueLbcCommand(Lbc1800Protocol.buildDeviceUnlockCommand(), "Unlock (:DU)", delayMsAfter = 60L)
        enqueueLbcCommand(Lbc1800Protocol.buildQueryDeviceDataCommand(), "Query Info (:DD)", delayMsAfter = 60L)
    }

    /**
     * Pushes the active Firearm + Scope + Ammo profile and live GPS weather directly to the
     * Bone Collector 1800 using the verified Applied Ballistics Ultralite :DU / :BP / :AV commands.
     */
    fun syncProfileToLbc1800(
        profile: RifleProfile,
        atmosphere: Atmosphere,
        moverSpeedMph: Double = 0.0
    ) {
        if (_state.value.connectionState != BleConnectionState.CONNECTED) {
            _state.update { it.copy(lastSyncMessage = "Connect to your rangefinder first before syncing.") }
            return
        }
        val lat = atmosphere.gpsLatitude ?: 35.0
        val duCmd = Lbc1800Protocol.buildDeviceUnlockCommand()
        val bpCmd = Lbc1800Protocol.buildBallisticProfileCommand(
            profile = profile,
            atmosphere = atmosphere,
            latitudeDeg = lat,
            moverSpeedMph = moverSpeedMph
        )
        val avCmd = Lbc1800Protocol.buildAuxVelocityCommand(profile)

        _state.update {
            it.copy(
                lastSyncMessage = "Syncing ${profile.firearmName.ifBlank { profile.scopeName }} + ${profile.name}...",
                statusMessage = "Uploading ballistic profile & weather to LBC1800..."
            )
        }
        enqueueLbcCommand(duCmd, "Device Unlock (:DU)", delayMsAfter = 65L)
        enqueueLbcCommand(bpCmd, "Ballistic Profile (:BP) — ${profile.name}", delayMsAfter = 65L)
        enqueueLbcCommand(avCmd, "Aux Velocity (:AV)", delayMsAfter = 40L)
    }

    private fun enqueueLbcCommand(asciiCommand: String, label: String, delayMsAfter: Long = 0L) {
        val bytes = asciiCommand.toByteArray(Charsets.US_ASCII)
        appendLog(
            direction = "TX",
            uuid = Lbc1800Protocol.WRITE_CHAR_UUID,
            hex = bytes.toHex(),
            ascii = asciiCommand.trimEnd('\r'),
            note = "$label (${bytes.size}B)"
        )
        val svcUuid = UUID.fromString(Lbc1800Protocol.SERVICE_UUID)
        val charUuid = UUID.fromString(Lbc1800Protocol.WRITE_CHAR_UUID)
        synchronized(gattLock) {
            var offset = 0
            while (offset < bytes.size) {
                val end = (offset + 20).coerceAtMost(bytes.size)
                val isLast = end == bytes.size
                pendingWrites.addLast(
                    QueuedWrite(
                        serviceUuid = svcUuid,
                        charUuid = charUuid,
                        payload = bytes.copyOfRange(offset, end),
                        delayMsAfter = if (isLast) delayMsAfter else 0L
                    )
                )
                offset = end
            }
        }
        drainWriteQueue()
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(serviceUuid: String, characteristicUuid: String) {
        val gatt = synchronized(gattLock) { activeGatt } ?: return
        val service = gatt.getService(UUID.fromString(serviceUuid)) ?: return
        val ch = service.getCharacteristic(UUID.fromString(characteristicUuid)) ?: return
        synchronized(gattLock) {
            pendingReadQueue.addLast(ch)
        }
        appendLog("TX", characteristicUuid, "", "", "Read requested")
        readNextQueuedCharacteristic(gatt)
    }

    fun writeCharacteristic(serviceUuid: String, characteristicUuid: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val svcUuid = UUID.fromString(serviceUuid)
        val chUuid = UUID.fromString(characteristicUuid)
        appendLog("TX", characteristicUuid, bytes.toHex(), bytes.toPrintableAscii(), "Manual write (${bytes.size} bytes)")
        synchronized(gattLock) {
            var offset = 0
            while (offset < bytes.size) {
                val end = (offset + 20).coerceAtMost(bytes.size)
                pendingWrites.addLast(QueuedWrite(svcUuid, chUuid, bytes.copyOfRange(offset, end)))
                offset = end
            }
        }
        drainWriteQueue()
    }

    @SuppressLint("MissingPermission")
    private fun drainWriteQueue() {
        var gatt: BluetoothGatt? = null
        var item: QueuedWrite? = null
        synchronized(gattLock) {
            if (writeInFlight) return
            val currentGatt = activeGatt ?: return
            val nextItem = pendingWrites.removeFirstOrNull() ?: return
            writeInFlight = true
            gatt = currentGatt
            item = nextItem
        }
        val safeGatt = gatt ?: return
        val safeItem = item ?: return

        val service = safeGatt.getService(safeItem.serviceUuid)
        val ch = service?.getCharacteristic(safeItem.charUuid)
        if (ch == null) {
            synchronized(gattLock) { writeInFlight = false }
            _state.update { it.copy(lastSyncMessage = "Required BLE characteristic unavailable.") }
            return
        }

        val started = runCatching {
            val writeType = if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                safeGatt.writeCharacteristic(ch, safeItem.payload, writeType) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                ch.writeType = writeType
                @Suppress("DEPRECATION")
                ch.value = safeItem.payload
                @Suppress("DEPRECATION")
                safeGatt.writeCharacteristic(ch)
            }
        }.getOrDefault(false)

        if (!started) {
            synchronized(gattLock) { writeInFlight = false }
        } else if (safeItem.delayMsAfter > 0L) {
            // Delay only affects next write after onCharacteristicWrite clears writeInFlight
            pacingExecutor.schedule({ drainWriteQueue() }, safeItem.delayMsAfter, TimeUnit.MILLISECONDS)
        }
    }

    fun simulateRangePing(distanceYards: Double, angleDegrees: Double) {
        val reading = RangefinderReading(
            distanceYards = distanceYards,
            angleDegrees = angleDegrees,
            rawSummary = "Simulated LBC1800 ping: ${distanceYards.toInt()} yd @ ${angleDegrees.toInt()}°",
            timestamp = nowStamp()
        )
        _state.update {
            it.copy(statusMessage = "Simulated range received: ${distanceYards.toInt()} yd (${angleDegrees.toInt()}°)")
        }
        appendLog("RX", "SIMULATOR", "", reading.rawSummary, "Simulated telemetry")
        onReadingCallback?.invoke(reading)
    }

    fun clearLogs() {
        _state.update { it.copy(packetLogs = emptyList()) }
    }

    fun buildDiagnosticDump(): String {
        val s = _state.value
        val sb = StringBuilder()
        sb.appendLine("=== Gunnies Ballistics BLE Diagnostic Dump ===")
        sb.appendLine("Device: ${s.connectedDeviceName ?: "None"} (${s.connectedDeviceAddress ?: "N/A"})")
        sb.appendLine("State: ${s.connectionState.label}")
        sb.appendLine("Firmware: ${s.firmwareVersion ?: "Unknown"}")
        sb.appendLine()
        sb.appendLine("--- Discovered GATT Services (${s.services.size}) ---")
        for (svc in s.services) {
            sb.appendLine("Service: ${svc.uuid} (${svc.friendlyName})")
            for (ch in svc.characteristics) {
                sb.appendLine("  Char: ${ch.uuid} [${ch.propertiesLabel}]")
                if (ch.lastValueHex.isNotEmpty()) {
                    sb.appendLine("    Last HEX: ${ch.lastValueHex} | ASCII: ${ch.lastValueAscii}")
                }
            }
        }
        sb.appendLine()
        sb.appendLine("--- Recent Packet Log (${s.packetLogs.size}) ---")
        for (log in s.packetLogs.takeLast(80)) {
            sb.appendLine("[${log.timestamp}] ${log.direction} ${log.characteristicUuid} | HEX=${log.hex} | ASCII=${log.ascii} | ${log.note}")
        }
        return sb.toString()
    }

    fun shutdown() {
        stopScan()
        disconnect()
        pacingExecutor.shutdownNow()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                appendLog("SYS", "-", "", "", "GATT error status=$status")
                disconnect()
                _state.update {
                    it.copy(statusMessage = "Connection dropped (status $status). Wake rangefinder and retry.")
                }
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    appendLog("SYS", "-", "", "", "Connected; discovering services...")
                    _state.update {
                        it.copy(
                            connectionState = BleConnectionState.DISCOVERING,
                            statusMessage = "Connected — inspecting GATT services..."
                        )
                    }
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    appendLog("SYS", "-", "", "", "Peripheral disconnected")
                    disconnect()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.update { it.copy(statusMessage = "Service discovery failed (status $status)") }
                return
            }

            val notifyFound = ArrayList<BluetoothGattCharacteristic>()
            val readFound = ArrayList<BluetoothGattCharacteristic>()

            val mappedServices = gatt.services.map { service ->
                val svcUuid = service.uuid.toString()
                val chars = service.characteristics.map { ch ->
                    val props = ch.properties
                    val canRead = (props and BluetoothGattCharacteristic.PROPERTY_READ) != 0
                    val canWrite = (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                        (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                    val canNotify = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                        (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

                    if (canNotify) notifyFound.add(ch)
                    if (canRead) readFound.add(ch)

                    val propsStr = buildList {
                        if (canRead) add("READ")
                        if (canWrite) add("WRITE")
                        if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) add("NOTIFY")
                        if ((props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) add("INDICATE")
                    }.joinToString(" | ")

                    GattCharacteristicInfo(
                        uuid = ch.uuid.toString(),
                        serviceUuid = svcUuid,
                        propertiesLabel = propsStr,
                        canRead = canRead,
                        canWrite = canWrite
                    )
                }
                GattServiceInfo(
                    uuid = svcUuid,
                    friendlyName = friendlyServiceName(svcUuid),
                    characteristics = chars
                )
            }

            synchronized(gattLock) {
                pendingNotificationQueue.clear()
                pendingNotificationQueue.addAll(notifyFound)
                pendingReadQueue.clear()
                pendingReadQueue.addAll(readFound)
            }

            _state.update {
                it.copy(
                    connectionState = BleConnectionState.CONNECTED,
                    services = mappedServices,
                    statusMessage = "Connected (${mappedServices.size} services). Handshaking with LBC1800..."
                )
            }
            appendLog("SYS", "-", "", "", "Discovered ${mappedServices.size} services; subscribing to ${notifyFound.size} notify chars")
            subscribeNextNotification(gatt)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            synchronized(gattLock) {
                writeInFlight = false
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    pendingWrites.clear()
                }
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                appendLog("SYS", characteristic.uuid.toString(), "", "", "Write failed (status=$status)")
                return
            }
            drainWriteQueue()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            appendLog("SYS", descriptor.characteristic.uuid.toString(), "", "", "CCCD subscribed (status=$status)")
            subscribeNextNotification(gatt)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                @Suppress("DEPRECATION")
                val bytes = characteristic.value ?: ByteArray(0)
                handleIncomingPayload(characteristic.uuid.toString(), bytes, "READ")
            } else {
                appendLog("SYS", characteristic.uuid.toString(), "", "", "Read failed (status=$status)")
            }
            readNextQueuedCharacteristic(gatt)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleIncomingPayload(characteristic.uuid.toString(), value, "READ")
            } else {
                appendLog("SYS", characteristic.uuid.toString(), "", "", "Read failed (status=$status)")
            }
            readNextQueuedCharacteristic(gatt)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val bytes = characteristic.value ?: ByteArray(0)
            handleIncomingPayload(characteristic.uuid.toString(), bytes, "NOTIFY")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingPayload(characteristic.uuid.toString(), value, "NOTIFY")
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribeNextNotification(gatt: BluetoothGatt) {
        val next = synchronized(gattLock) { pendingNotificationQueue.removeFirstOrNull() }
        if (next == null) {
            readNextQueuedCharacteristic(gatt)
            return
        }
        gatt.setCharacteristicNotification(next, true)
        val cccd = next.getDescriptor(CCCD_UUID)
        if (cccd == null) {
            subscribeNextNotification(gatt)
            return
        }
        val payload = if ((next.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }

        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, payload) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.value = payload
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
        }.getOrDefault(false)

        if (!started) {
            appendLog("SYS", next.uuid.toString(), "", "", "CCCD write declined; skipping")
            subscribeNextNotification(gatt)
        }
    }

    @SuppressLint("MissingPermission")
    private fun readNextQueuedCharacteristic(gatt: BluetoothGatt) {
        val next = synchronized(gattLock) { pendingReadQueue.removeFirstOrNull() }
        if (next == null) {
            if (gatt.getService(UUID.fromString(Lbc1800Protocol.SERVICE_UUID)) != null) {
                queryLbc1800DeviceInfo()
            }
            return
        }
        val started = runCatching { gatt.readCharacteristic(next) }.getOrDefault(false)
        if (!started) {
            readNextQueuedCharacteristic(gatt)
        }
    }

    private fun handleIncomingPayload(charUuid: String, bytes: ByteArray, source: String) {
        val hex = bytes.toHex()
        val ascii = bytes.toPrintableAscii()

        _state.update { current ->
            val updatedServices = current.services.map { svc ->
                svc.copy(
                    characteristics = svc.characteristics.map { ch ->
                        if (ch.uuid.equals(charUuid, ignoreCase = true)) {
                            ch.copy(lastValueHex = hex, lastValueAscii = ascii)
                        } else ch
                    }
                )
            }
            current.copy(services = updatedServices)
        }

        if (charUuid.equals(Lbc1800Protocol.NOTIFY_CHAR_UUID, ignoreCase = true)) {
            val decodedFrames = synchronized(gattLock) {
                if (hdlcDeframer.isInFrame || (bytes.isNotEmpty() && bytes[0] == Lbc1800Protocol.HDLC_FLAG)) {
                    hdlcDeframer.feed(bytes)
                } else {
                    val lines = mutableListOf<Lbc1800Protocol.DecodedFrame>()
                    rawAsciiLineBuffer.append(String(bytes, Charsets.US_ASCII))
                    while (true) {
                        val crIdx = rawAsciiLineBuffer.indexOf("\r")
                        val lfIdx = rawAsciiLineBuffer.indexOf("\n")
                        val cut = listOf(crIdx, lfIdx).filter { it >= 0 }.minOrNull() ?: break
                        val line = rawAsciiLineBuffer.substring(0, cut).trim()
                        rawAsciiLineBuffer.delete(0, cut + 1)
                        if (line.startsWith(":")) {
                            lines.add(
                                Lbc1800Protocol.DecodedFrame(
                                    channel = 0,
                                    asciiMessage = line,
                                    crcValid = Lbc1800Protocol.verifyAsciiChecksum(line)
                                )
                            )
                        }
                    }
                    if (rawAsciiLineBuffer.length > 512) rawAsciiLineBuffer.clear()
                    lines
                }
            }
            for (frame in decodedFrames) {
                handleLbcDecodedMessage(frame)
            }
        }

        val parsed = tryParseTelemetry(ascii)
        appendLog(
            direction = "RX",
            uuid = charUuid,
            hex = hex,
            ascii = ascii,
            note = parsed?.rawSummary ?: "$source (${bytes.size} bytes)"
        )

        if (parsed != null) {
            _state.update {
                it.copy(statusMessage = "Rangefinder reading: ${parsed.distanceYards.toInt()} yd")
            }
            onReadingCallback?.invoke(parsed)
        }
    }

    private fun handleLbcDecodedMessage(frame: Lbc1800Protocol.DecodedFrame) {
        val msg = frame.asciiMessage
        Log.i(TAG, "LBC1800 Decoded (ch=0x${frame.channel.toString(16)}, valid=${frame.crcValid}): $msg")
        if (!frame.crcValid) return

        when {
            msg.startsWith(":AK") -> {
                _state.update { cur ->
                    val nextAck = cur.ackCount + 1
                    cur.copy(
                        ackCount = nextAck,
                        lastSyncMessage = "Synced cleanly with Bone Collector 1800 (:AK #$nextAck)",
                        statusMessage = "Bone Collector 1800 synced (:AK)"
                    )
                }
            }
            msg.startsWith(":VR,") -> {
                val parts = msg.split(",")
                val fw = if (parts.size >= 3) "v${parts[1]}.${parts[2]}" else msg
                _state.update {
                    it.copy(
                        firmwareVersion = fw,
                        statusMessage = "Bone Collector 1800 ready (FW $fw)"
                    )
                }
            }
            msg.startsWith(":A+") -> {
                _state.update { cur ->
                    if (cur.statusMessage.contains("Discovering") || cur.statusMessage.contains("Handshaking")) {
                        cur.copy(statusMessage = "LBC1800 connected (:A+). Ready to sync.")
                    } else cur
                }
            }
            msg.startsWith(":BS,") -> {
                appendLog("SYS", "LBC1800", "", msg, "Device settings received")
            }
        }
    }

    private fun tryParseTelemetry(ascii: String): RangefinderReading? {
        val trimmed = ascii.trim()
        if (trimmed.length < 2) return null

        val distMatch = DIST_REGEX.find(trimmed)
        if (distMatch != null) {
            val rawD = distMatch.groupValues[1].toDoubleOrNull() ?: return null
            if (rawD in 5.0..2500.0) {
                val isMeters = trimmed.contains("m", ignoreCase = true) && !trimmed.contains("yd", ignoreCase = true)
                val distYd = if (isMeters) rawD * UnitFormatters.METERS_TO_YARDS else rawD
                val ang = ANG_REGEX.find(trimmed)?.groupValues?.get(1)?.toDoubleOrNull()
                return RangefinderReading(
                    distanceYards = distYd,
                    angleDegrees = ang,
                    rawSummary = "Parsed telemetry: ${distYd.toInt()} yd" + (ang?.let { " @ ${it.toInt()}°" } ?: ""),
                    timestamp = nowStamp()
                )
            }
        }

        if (trimmed.startsWith("$") && trimmed.contains(",")) {
            val parts = trimmed.split(",")
            val rawD = parts.getOrNull(1)?.toDoubleOrNull()
            if (rawD != null && rawD in 5.0..2500.0) {
                val unitToken = parts.getOrNull(2)?.trim()?.uppercase(Locale.US)
                val distYd = if (unitToken == "M") rawD * UnitFormatters.METERS_TO_YARDS else rawD
                val ang = parts.getOrNull(3)?.toDoubleOrNull()
                return RangefinderReading(
                    distanceYards = distYd,
                    angleDegrees = ang,
                    rawSummary = "Parsed NMEA frame: ${distYd.toInt()} yd",
                    timestamp = nowStamp()
                )
            }
        }

        return null
    }

    private fun appendLog(direction: String, uuid: String, hex: String, ascii: String, note: String) {
        val entry = BlePacketLog(
            timestamp = nowStamp(),
            direction = direction,
            characteristicUuid = uuid.take(8),
            hex = hex,
            ascii = ascii,
            note = note
        )
        Log.i(TAG, "[$direction] $uuid | HEX=$hex | ASCII=$ascii | $note")
        _state.update { current ->
            current.copy(packetLogs = (current.packetLogs + entry).takeLast(120))
        }
    }

    private fun friendlyServiceName(uuid: String): String {
        val lower = uuid.lowercase(Locale.US)
        return when {
            lower == Lbc1800Protocol.SERVICE_UUID -> "Bushnell LBC1800 UART Bridge (ISSC)"
            lower == NUS_SERVICE_UUID -> "Nordic UART Service (Serial Bridge)"
            lower.startsWith("00001800") -> "Generic Access"
            lower.startsWith("00001801") -> "Generic Attribute"
            lower.startsWith("0000180a") -> "Device Information"
            lower.startsWith("0000180f") -> "Battery Service"
            else -> "Vendor / Rangefinder Service"
        }
    }

    private fun nowStamp(): String = LocalTime.now().format(TIME_FMT)

    private fun ByteArray.toHex(): String =
        joinToString(" ") { String.format(Locale.US, "%02X", it) }

    private fun ByteArray.toPrintableAscii(): String =
        map { b ->
            val c = b.toInt() and 0xFF
            if (c in 32..126) c.toChar() else '.'
        }.joinToString("")
}
