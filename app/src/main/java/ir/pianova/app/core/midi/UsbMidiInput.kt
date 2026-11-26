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
import kotlinx.coroutines.launch

/**
 * Android USB MIDI adapter. Automatically discovers USB MIDI devices and listens
 * to their output ports (the ports that transmit keyboard performance data).
 * MIDI is optional: devices without the MIDI service keep the virtual keyboard usable.
 */
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
    private val sustainDevices = mutableSetOf<Int>()
    private var closed = false

    private data class Connection(
        val device: MidiDevice,
        val ports: MutableList<MidiOutputPort> = mutableListOf(),
        val heldNotes: MutableSet<Int> = mutableSetOf(),
        var sustain: Boolean = false
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
        eventScope.launch {
            for (event in eventQueue) eventsFlow.emit(event)
        }
        manager?.registerDeviceCallback(callback, mainHandler)
        manager?.devices?.forEach(::connectIfSupported)
    }

    private fun enqueue(event: PianoEvent) {
        eventQueue.trySend(event)
    }

    private fun connectIfSupported(info: MidiDeviceInfo) {
        val midiManager = manager ?: return
        if (info.type != MidiDeviceInfo.TYPE_USB || synchronized(lock) { closed || info.id in connections || !pendingDeviceIds.add(info.id) }) return
        runCatching { midiManager.openDevice(info, { device ->
            if (device == null) {
                synchronized(lock) { pendingDeviceIds -= info.id }
                return@openDevice
            }
            synchronized(lock) {
                // Consume the pending marker and publish the connection atomically.
                // A concurrent onDeviceRemoved callback either wins first (so this
                // device is closed) or runs after publication and disconnects it.
                if (!pendingDeviceIds.remove(info.id) || closed || info.id in connections) {
                    device.close()
                    return@synchronized
                }
                val connection = Connection(device)
                connections[info.id] = connection
                info.ports.filter { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }.forEach { portInfo ->
                    val port = runCatching { device.openOutputPort(portInfo.portNumber) }.getOrNull() ?: return@forEach
                    val parser = MidiByteParser(emit = { event -> handleEvent(info.id, event) })
                    val receiver = object : MidiReceiver() {
                        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                            parser.feed(msg, offset, count)
                        }
                    }
                    if (runCatching { port.connect(receiver) }.isSuccess) connection.ports += port
                    else runCatching { port.close() }
                }
                if (connection.ports.isEmpty()) {
                    connections.remove(info.id)
                    device.close()
                }
            }
        }, mainHandler) }.onFailure { synchronized(lock) { pendingDeviceIds -= info.id } }
    }

    private fun handleEvent(deviceId: Int, event: PianoEvent) {
        synchronized(lock) {
            val connection = connections[deviceId] ?: return
            when (event) {
                is PianoEvent.NoteOn -> {
                    if (connection.heldNotes.add(event.midiNote)) {
                        val count = aggregateHeldNotes[event.midiNote] ?: 0
                        aggregateHeldNotes[event.midiNote] = count + 1
                        if (count == 0) enqueue(event)
                    } else {
                        // Repeated NoteOn on the same source refreshes velocity.
                        enqueue(event)
                    }
                }
                is PianoEvent.NoteOff -> {
                    if (connection.heldNotes.remove(event.midiNote)) {
                        val count = (aggregateHeldNotes[event.midiNote] ?: 1) - 1
                        if (count <= 0) {
                            aggregateHeldNotes.remove(event.midiNote)
                            enqueue(event)
                        } else aggregateHeldNotes[event.midiNote] = count
                    }
                }
                is PianoEvent.Sustain -> {
                    connection.sustain = event.enabled
                    if (event.enabled) sustainDevices += deviceId else sustainDevices -= deviceId
                    enqueue(PianoEvent.Sustain(sustainDevices.isNotEmpty(), event.timestampMs))
                }
            }
        }
    }

    private fun disconnect(deviceId: Int) {
        synchronized(lock) {
            val connection = connections.remove(deviceId) ?: return
            connection.heldNotes.toList().forEach { note ->
                val count = (aggregateHeldNotes[note] ?: 1) - 1
                if (count <= 0) {
                    aggregateHeldNotes.remove(note)
                    enqueue(PianoEvent.NoteOff(note, nowMs()))
                } else aggregateHeldNotes[note] = count
            }
            connection.heldNotes.clear()
            sustainDevices -= deviceId
            if (connection.sustain && sustainDevices.isEmpty()) enqueue(PianoEvent.Sustain(false, nowMs()))
            connection.ports.forEach { runCatching { it.close() } }
            runCatching { connection.device.close() }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            pendingDeviceIds.clear()
            manager?.unregisterDeviceCallback(callback)
            connections.keys.toList().forEach(::disconnect)
        }
        eventQueue.close()
    }

    private fun nowMs() = System.nanoTime() / 1_000_000L
}
