package ir.pianova.app.core.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Android USB MIDI adapter. Automatically discovers USB MIDI devices and listens
 * to their output ports (the ports that transmit keyboard performance data).
 */
class UsbMidiInput(context: Context, extraBufferCapacity: Int = 128) : PianoInput, AutoCloseable {
    private val eventsFlow = MutableSharedFlow<PianoEvent>(replay = 0, extraBufferCapacity = extraBufferCapacity)
    override val events = eventsFlow.asSharedFlow()
    private val manager = context.applicationContext.getSystemService(Context.MIDI_SERVICE) as MidiManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val connections = mutableMapOf<Int, Connection>()
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
        override fun onDeviceRemoved(info: MidiDeviceInfo) { disconnect(info.id) }
    }

    init {
        require(extraBufferCapacity > 0) { "extraBufferCapacity must be positive" }
        manager.registerDeviceCallback(callback, mainHandler)
        manager.devices.forEach(::connectIfSupported)
    }

    private fun connectIfSupported(info: MidiDeviceInfo) {
        if (info.type != MidiDeviceInfo.TYPE_USB || synchronized(lock) { closed || info.id in connections }) return
        manager.openDevice(info, { device ->
            if (device == null) return@openDevice
            synchronized(lock) {
                if (closed || info.id in connections) {
                    device.close()
                    return@synchronized
                }
                val connection = Connection(device)
                connections[info.id] = connection
                info.ports.filter { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }.forEach { portInfo ->
                    val port = device.openOutputPort(portInfo.portNumber) ?: return@forEach
                    val parser = MidiByteParser(emit = { event -> handleEvent(info.id, event) })
                    port.connect(object : MidiReceiver() {
                        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                            parser.feed(msg, offset, count)
                        }
                    })
                    connection.ports += port
                }
                if (connection.ports.isEmpty()) {
                    connections.remove(info.id)
                    device.close()
                }
            }
        }, mainHandler)
    }

    private fun handleEvent(deviceId: Int, event: PianoEvent) {
        synchronized(lock) {
            val connection = connections[deviceId] ?: return
            when (event) {
                is PianoEvent.NoteOn -> {
                    if (connection.heldNotes.add(event.midiNote)) {
                        val count = aggregateHeldNotes[event.midiNote] ?: 0
                        aggregateHeldNotes[event.midiNote] = count + 1
                        if (count == 0) eventsFlow.tryEmit(event)
                    } else {
                        // Repeated NoteOn on the same source refreshes velocity.
                        eventsFlow.tryEmit(event)
                    }
                }
                is PianoEvent.NoteOff -> {
                    if (connection.heldNotes.remove(event.midiNote)) {
                        val count = (aggregateHeldNotes[event.midiNote] ?: 1) - 1
                        if (count <= 0) {
                            aggregateHeldNotes.remove(event.midiNote)
                            eventsFlow.tryEmit(event)
                        } else aggregateHeldNotes[event.midiNote] = count
                    }
                }
                is PianoEvent.Sustain -> {
                    connection.sustain = event.enabled
                    if (event.enabled) sustainDevices += deviceId else sustainDevices -= deviceId
                    val enabled = sustainDevices.isNotEmpty()
                    eventsFlow.tryEmit(PianoEvent.Sustain(enabled, event.timestampMs))
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
                    eventsFlow.tryEmit(PianoEvent.NoteOff(note, nowMs()))
                } else aggregateHeldNotes[note] = count
            }
            connection.heldNotes.clear()
            sustainDevices -= deviceId
            if (connection.sustain && sustainDevices.isEmpty()) eventsFlow.tryEmit(PianoEvent.Sustain(false, nowMs()))
            connection.ports.forEach { runCatching { it.close() } }
            runCatching { connection.device.close() }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            manager.unregisterDeviceCallback(callback)
            connections.keys.toList().forEach(::disconnect)
        }
    }

    private fun nowMs() = System.nanoTime() / 1_000_000L
}
