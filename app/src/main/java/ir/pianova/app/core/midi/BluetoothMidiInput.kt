package ir.pianova.app.core.midi

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** BLE-MIDI adapter. Uses the same normalized PianoInput/PianoEvent contract as USB and touch input. */
class BluetoothMidiInput(
    context: Context,
    extraBufferCapacity: Int = 128
) : PianoInput, AutoCloseable {
    companion object {
        val MIDI_SERVICE_UUID: UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
    }

    private val appContext = context.applicationContext
    private val eventFlow = MutableSharedFlow<PianoEvent>(replay = 0, extraBufferCapacity = extraBufferCapacity)
    override val events = eventFlow.asSharedFlow()
    private val queue = Channel<PianoEvent>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val midiManager = appContext.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private val scanner get() = bluetoothManager?.adapter?.bluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val discovered = linkedMapOf<String, BluetoothDevice>()
    private var selectedAddress: String? = null
    private var pendingAddress: String? = null
    private var scanActive = false
    private var closed = false
    private var openToken = 0L

    private data class Connection(
        val device: MidiDevice,
        val deviceId: Int,
        val address: String,
        val ports: MutableList<MidiOutputPort> = mutableListOf(),
        val eventTracker: MidiConnectionEventTracker = MidiConnectionEventTracker()
    )

    private val activeConnection = MutableStateFlow<Connection?>(null)
    private val statusesFlow = MutableStateFlow<List<UsbMidiDeviceStatus>>(emptyList())
    val deviceStatuses: StateFlow<List<UsbMidiDeviceStatus>> = statusesFlow


    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            if (info.type != MidiDeviceInfo.TYPE_BLUETOOTH) return
            val current = activeConnection.value
            if (current?.deviceId == info.id) {
                disconnect()
                handler.postDelayed(reconnectScanRunnable, 1_000L)
            }
        }
    }

    private val reconnectScanRunnable = Runnable {
        if (!closed && selectedAddress != null && activeConnection.value == null) refreshDevices()
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = onDeviceFound(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::onDeviceFound)
        override fun onScanFailed(errorCode: Int) {
            synchronized(lock) { scanActive = false }
            publishStatuses()
        }
    }

    init {
        require(extraBufferCapacity > 0)
        scope.launch { for (event in queue) eventFlow.emit(event) }
        midiManager?.registerDeviceCallback(deviceCallback, handler)
    }

    fun selectDeviceIdentity(address: String?) {
        synchronized(lock) {
            if (selectedAddress != address) {
                pendingAddress = null
                ++openToken
                disconnectLocked()
            }
            selectedAddress = address
        }
        if (address != null) discovered[address]?.let(::connect)
        publishStatuses()
    }

    fun refreshDevices() {
        val bleScanner = scanner ?: return
        synchronized(lock) { discovered.clear(); scanActive = true }
        publishStatuses()
        runCatching {
            val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(MIDI_SERVICE_UUID)).build()
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            bleScanner.startScan(listOf(filter), settings, scanCallback)
            handler.postDelayed({ stopScan() }, 12_000L)
        }.onFailure {
            synchronized(lock) { scanActive = false }
            publishStatuses()
        }
    }

    private fun stopScan() {
        runCatching { scanner?.stopScan(scanCallback) }
        synchronized(lock) { scanActive = false }
        publishStatuses()
        if (!closed && selectedAddress != null && activeConnection.value == null) {
            handler.removeCallbacks(reconnectScanRunnable)
            handler.postDelayed(reconnectScanRunnable, 3_000L)
        }
    }

    private fun onDeviceFound(result: ScanResult) {
        val device = result.device
        val address = device.address ?: return
        synchronized(lock) { discovered[address] = device }
        val selected = synchronized(lock) { selectedAddress }
        if (selected == address && activeConnection.value == null) connect(device)
        publishStatuses()
    }

    private fun connect(device: BluetoothDevice) {
        val manager = midiManager ?: return
        val address = device.address ?: return
        val token = synchronized(lock) {
            if (closed || activeConnection.value != null || pendingAddress != null || address != selectedAddress) return
            pendingAddress = address
            ++openToken
        }
        runCatching {
            manager.openBluetoothDevice(device, { opened ->
                if (opened == null) {
                    synchronized(lock) { if (token == openToken) pendingAddress = null }
                    publishStatuses()
                    return@openBluetoothDevice
                }
                synchronized(lock) {
                    if (token == openToken) pendingAddress = null
                    if (closed || token != openToken || address != selectedAddress || activeConnection.value != null) {
                        opened.close()
                        return@synchronized
                    }
                    val info = opened.info
                    val newConnection = Connection(opened, info.id, address)
                    info.ports.filter { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }.forEach { portInfo ->
                        val port = runCatching { opened.openOutputPort(portInfo.portNumber) }.getOrNull() ?: return@forEach
                        val parser = MidiByteParser { channel, event -> handleEvent(info.id, channel, event) }
                        val receiver = object : MidiReceiver() {
                            override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                                parser.feed(msg, offset, count)
                            }
                        }
                        if (runCatching { port.connect(receiver) }.isSuccess) newConnection.ports += port
                        else runCatching { port.close() }
                    }
                    if (newConnection.ports.isEmpty()) {
                        opened.close()
                    } else {
                        activeConnection.value = newConnection
                    }
                }
                publishStatuses()
                if (activeConnection.value != null) stopScan()
            }, handler)
        }.onFailure {
            synchronized(lock) { if (token == openToken) pendingAddress = null }
            publishStatuses()
        }
    }

    private fun handleEvent(deviceId: Int, channel: Int, event: PianoEvent) {
        synchronized(lock) {
            val c = activeConnection.value ?: return
            if (c.deviceId != deviceId) return
            c.eventTracker.accept(channel, event).forEach { queue.trySend(it) }
        }
    }

    fun disconnect() {
        synchronized(lock) { disconnectLocked() }
        publishStatuses()
    }

    private fun disconnectLocked() {
        val c = activeConnection.value ?: return
        activeConnection.value = null
        pendingAddress = null
        ++openToken
        c.eventTracker.releaseAll(nowMs()).forEach { queue.trySend(it) }
        c.ports.forEach { runCatching { it.close() } }
        runCatching { c.device.close() }
    }

    private fun publishStatuses() {
        val rows = synchronized(lock) {
            val published = discovered.map { (address, device) ->
                val isConnected = activeConnection.value?.address == address
                UsbMidiDeviceStatus(
                    id = address.hashCode(),
                    name = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Bluetooth MIDI",
                    connected = isConnected,
                    selected = selectedAddress == address,
                    transport = MidiTransport.BLUETOOTH,
                    identity = address
                )
            }.toMutableList()
            activeConnection.value?.let { connection ->
                if (published.none { it.identity == connection.address }) {
                    val name = connection.device.info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
                    published += UsbMidiDeviceStatus(
                        id = connection.address.hashCode(),
                        name = name?.takeIf { it.isNotBlank() } ?: "Bluetooth MIDI",
                        connected = true,
                        selected = selectedAddress == connection.address,
                        transport = MidiTransport.BLUETOOTH,
                        identity = connection.address
                    )
                }
            }
            published
        }
        statusesFlow.value = rows
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            disconnectLocked()
            ++openToken
        }
        handler.removeCallbacks(reconnectScanRunnable)
        stopScan()
        midiManager?.unregisterDeviceCallback(deviceCallback)
        queue.close()
    }

    private fun nowMs() = System.nanoTime() / 1_000_000L
}
