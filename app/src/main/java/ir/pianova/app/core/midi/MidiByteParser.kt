package ir.pianova.app.core.midi

/**
 * Stateful parser for channel voice MIDI bytes delivered by Android MidiReceiver.
 * Supports running status for NoteOn, NoteOff and Sustain (CC64), while keeping
 * byte alignment for unrelated channel messages and real-time status bytes.
 */
class MidiByteParser(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
    private val emit: (PianoEvent) -> Unit
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
                value >= 0xF8 -> Unit // MIDI real-time bytes can appear between data bytes.
                value >= 0x80 -> {
                    if (value in 0x80..0xEF) {
                        runningStatus = value
                        currentStatus = value
                        expectedDataCount = if ((value and 0xF0) in 0xC0..0xD0) 1 else 2
                    } else {
                        runningStatus = 0
                        currentStatus = 0
                        expectedDataCount = 2
                    }
                    dataCount = 0
                }
                else -> {
                    if (currentStatus == 0) currentStatus = runningStatus
                    if (currentStatus == 0) continue
                    if (dataCount == 0) {
                        firstData = value
                        dataCount = 1
                    } else if (expectedDataCount == 1) {
                        handle(currentStatus, firstData, 0)
                        dataCount = 0
                        currentStatus = runningStatus
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
        when (status and 0xF0) {
            0x80 -> if (data1 in 0..127) emit(PianoEvent.NoteOff(data1, clock()))
            0x90 -> if (data1 in 0..127) {
                if (data2 == 0) emit(PianoEvent.NoteOff(data1, clock()))
                else emit(PianoEvent.NoteOn(data1, data2 / 127f, clock()))
            }
            0xB0 -> if (data1 == 64) emit(PianoEvent.Sustain(data2 >= 64, clock()))
        }
    }

    fun reset() {
        runningStatus = 0
        currentStatus = 0
        dataCount = 0
        expectedDataCount = 2
    }
}
