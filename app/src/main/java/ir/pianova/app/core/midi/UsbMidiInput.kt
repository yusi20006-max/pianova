package ir.pianova.app.core.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UsbMidiDeviceStatus(val id: Int, val name: String, val connected: Boolean, val selected: Boolean)

/** Android USB MIDI adapter. MIDI remains optional for touchscreen-only devices. */
class UsbMidiInput(context: Context, extraBufferCapacity: Int = 128) : PianoInput, AutoCloseable {
    private val eventsFlow = MutableSharedFlow<PianoEvent>(replay = 0, extraBufferCapacity = extraBufferCapacity)
    override val events = eventsFlow.asSharedFlow()
    private val eventQueue = Channel<PianoEvent>(Channel.UNLIMITED)
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager = context.applicationContext.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val connections = mutableMapOf<Int, Connection>()
    private val pendingDeviceIds = mutableSetOf<Int>()
    private val aggregateHeldNotes = mutableMapOf<Int, Int>()
    private val activeSustainChannels = mutableSetOf<Pair<Int, Int>>()
    private var closed = false
    private var selectedDeviceId: Int? = null
    private val deviceStatusFlow = MutableStateFlow<List<UsbMidiDeviceStatus>>(emptyList())
    val deviceStatuses: StateFlow<List<UsbMidiDeviceStatus>> = deviceStatusFlow
    private data class Connection(
        val device: MidiDevice,
        val ports: MutableList<MidiOutputPort> = mutableListOf(),
        val heldNotesByChannel: MutableMap<Int, MutableSet<Int>> = mutableMapOf(),
        val sustainChannels: MutableSet<Int> = mutableSetOf()
    )
    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) { connectIfSupported(info) }
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            synchronized(lock) { pendingDeviceIds -= info.id }
            disconnect(info.id)
        }
    }
    init {
        require(extraBufferCapacity > 0) { "extraBufferCapacity must be positive" }
        eventScope.launch { for (event in eventQueue) eventsFlow.emit(event) }
        manager?.registerDeviceCallback(callback, mainHandler)
        refreshDevices()
    }
    private fun enqueue(event: PianoEvent) { eventQueue.trySend(event) }

    fun refreshDevices() {
        val infos = manager?.devices?.filter { it.type == MidiDeviceInfo.TYPE_USB }.orEmpty()
        synchronized(lock) {
            deviceStatusFlow.value = infos.map { info ->
                UsbMidiDeviceStatus(info.id, info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "USB MIDI", info.id in connections, selectedDeviceId == info.id)
            }
        }
        val selected = synchronized(lock) { selectedDeviceId }
        infos.filter { selected == null || it.id == selected }.forEach(::connectIfSupported)
    }
    fun selectDevice(deviceId: Int?) {
        synchronized(lock) {
            selectedDeviceId = deviceId
            connections.keys.toList().filter { deviceId != null && it != deviceId }.forEach(::disconnect)
        }
        refreshDevices()
    }

    private fun connectIfSupported(info: MidiDeviceInfo) {
        val midiManager = manager ?: return
        if (synchronized(lock) { selectedDeviceId != null && selectedDeviceId != info.id }) return
        if (info.type != MidiDeviceInfo.TYPE_USB || synchronized(lock) { closed || info.id in connections || !pendingDeviceIds.add(info.id) }) return
        runCatching { midiManager.openDevice(info, { device ->
            if (device == null) { synchronized(lock) { pendingDeviceIds -= info.id }; return@openDevice }
            synchronized(lock) {
                if (!pendingDeviceIds.remove(info.id) || closed || info.id in connections) { device.close(); return@synchronized }
                val connection = Connection(device); connections[info.id] = connection
                info.ports.filter { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }.forEach { portInfo ->
                    val port = runCatching { device.openOutputPort(portInfo.portNumber) }.getOrNull() ?: return@forEach
                    val parser = MidiByteParser(emit = { ch, ev -> handleEvent(info.id, ch, ev) })
                    val receiver = object : MidiReceiver() {
                        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) { parser.feed(msg, offset, count) }
                    }
                    if (runCatching { port.connect(receiver) }.isSuccess) connection.ports += port else runCatching { port.close() }
                }
                if (connection.ports.isEmpty()) { connections.remove(info.id); device.close() }
                refreshDeviceStatusLocked()
            }
        }, mainHandler) }.onFailure { synchronized(lock) { pendingDeviceIds -= info.id } }
    }
    private fun handleEvent(deviceId: Int, channel: Int, event: PianoEvent) {
        synchronized(lock) {
            val connection = connections[deviceId] ?: return
            when (event) {
                is PianoEvent.NoteOn -> {
                    val notes = connection.heldNotesByChannel.getOrPut(channel) { mutableSetOf() }
                    if (notes.add(event.midiNote)) {
                        val n = aggregateHeldNotes[event.midiNote] ?: 0
                        aggregateHeldNotes[event.midiNote] = n + 1
                        if (n == 0) enqueue(event)
                    } else enqueue(event)
                }
                is PianoEvent.NoteOff -> {
                    val notes = connection.heldNotesByChannel[channel] ?: return
                    if (notes.remove(event.midiNote)) {
                        if (notes.isEmpty()) connection.heldNotesByChannel.remove(channel)
                        val n = (aggregateHeldNotes[event.midiNote] ?: 1) - 1
                        if (n <= 0) { aggregateHeldNotes.remove(event.midiNote); enqueue(event) }
                        else aggregateHeldNotes[event.midiNote] = n
                    }
                }
                is PianoEvent.Sustain -> {
                    val key = deviceId to channel
                    val before = activeSustainChannels.isNotEmpty()
                    if (event.enabled) { connection.sustainChannels += channel; activeSustainChannels += key }
                    else { connection.sustainChannels -= channel; activeSustainChannels -= key }
                    val after = activeSustainChannels.isNotEmpty()
                    if (before != after) enqueue(PianoEvent.Sustain(after, event.timestampMs))
                }
            }
        }
    }
    private fun disconnect(deviceId: Int) {
        synchronized(lock) {
            val c = connections.remove(deviceId) ?: return
            c.heldNotesByChannel.values.flatten().forEach { note ->
                val n = (aggregateHeldNotes[note] ?: 1) - 1
                if (n <= 0) { aggregateHeldNotes.remove(note); enqueue(PianoEvent.NoteOff(note, nowMs())) }
                else aggregateHeldNotes[note] = n
            }
            c.heldNotesByChannel.clear()
            val before = activeSustainChannels.isNotEmpty()
            c.sustainChannels.forEach { ch -> activeSustainChannels -= (deviceId to ch) }
            c.sustainChannels.clear()
            if (before && activeSustainChannels.isEmpty()) enqueue(PianoEvent.Sustain(false, nowMs()))
            c.ports.forEach { runCatching { it.close() } }; runCatching { c.device.close() }
            refreshDeviceStatusLocked()
        }
    }
    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true; pendingDeviceIds.clear()
            manager?.unregisterDeviceCallback(callback)
            connections.keys.toList().forEach(::disconnect)
        }
        eventQueue.close()
    }
    private fun refreshDeviceStatusLocked() {
        val infos = manager?.devices?.filter { it.type == MidiDeviceInfo.TYPE_USB }.orEmpty()
        deviceStatusFlow.value = infos.map { info ->
            UsbMidiDeviceStatus(info.id, info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "USB MIDI", info.id in connections)
        }
    }
    private fun nowMs() = System.nanoTime() / 1_000_000L
}
