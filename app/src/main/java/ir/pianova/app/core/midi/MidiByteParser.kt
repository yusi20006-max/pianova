package ir.pianova.app.core.midi

/** Channel-aware stateful parser for MIDI bytes from Android MidiReceiver. */
class MidiByteParser(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
    private val emit: (channel: Int, event: PianoEvent) -> Unit
) {
    private var runningStatus = 0
    private var currentStatus = 0
    private var dataCount = 0
    private var expectedDataCount = 2
    private var firstData = 0
    fun feed(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size - offset) {
        require(offset >= 0 && count >= 0 && offset + count <= bytes.size)
        for (index in offset until offset + count) {
            val value = bytes[index].toInt() and 0xFF
            when {
                value >= 0xF8 -> Unit
                value >= 0x80 -> {
                    if (value in 0x80..0xEF) {
                        runningStatus = value; currentStatus = value
                        expectedDataCount = if ((value and 0xF0) in 0xC0..0xD0) 1 else 2
                    } else { runningStatus = 0; currentStatus = 0; expectedDataCount = 2 }
                    dataCount = 0
                }
                else -> {
                    if (currentStatus == 0) currentStatus = runningStatus
                    if (currentStatus == 0) continue
                    if (dataCount == 0) {
                        firstData = value
                        dataCount = 1
                        if (expectedDataCount == 1) {
                            handle(currentStatus, firstData, 0)
                            dataCount = 0
                            currentStatus = runningStatus
                        }
                    } else {
                        handle(currentStatus, firstData, value)
                        dataCount = 0
                        currentStatus = runningStatus
                    }
                }
            }
        }
    }
    private fun handle(status: Int, data1: Int, data2: Int) {
        val channel = status and 0x0F
        when (status and 0xF0) {
            0x80 -> if (data1 in 0..127) emit(channel, PianoEvent.NoteOff(data1, clock()))
            0x90 -> if (data1 in 0..127 && data2 in 0..127) {
                if (data2 == 0) emit(channel, PianoEvent.NoteOff(data1, clock()))
                else emit(channel, PianoEvent.NoteOn(data1, data2 / 127f, clock()))
            }
            0xB0 -> if (data1 == 64 && data2 in 0..127) emit(channel, PianoEvent.Sustain(data2 >= 64, clock()))
        }
    }
    fun reset() { runningStatus = 0; currentStatus = 0; dataCount = 0; expectedDataCount = 2 }
}
