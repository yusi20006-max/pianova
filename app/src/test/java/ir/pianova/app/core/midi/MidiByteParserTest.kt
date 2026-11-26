package ir.pianova.app.core.midi

import org.junit.Assert.assertEquals
import org.junit.Test

class MidiByteParserTest {
    @Test fun noteOnAndNoteOffAreDecoded() {
        val actual = mutableListOf<PianoEvent>()
        val parser = MidiByteParser(clock = { 10L }, emit = actual::add)
        parser.feed(byteArrayOf(0x90.toByte(), 60, 100, 0x80.toByte(), 60, 20))
        assertEquals(PianoEvent.NoteOn(60, 100 / 127f, 10), actual[0])
        assertEquals(PianoEvent.NoteOff(60, 10), actual[1])
    }

    @Test fun velocityZeroNoteOnIsNoteOff() {
        val actual = mutableListOf<PianoEvent>()
        MidiByteParser(clock = { 22L }, emit = actual::add).feed(byteArrayOf(0x90.toByte(), 64, 0))
        assertEquals(listOf(PianoEvent.NoteOff(64, 22)), actual)
    }

    @Test fun sustainUsesCc64Threshold() {
        val actual = mutableListOf<PianoEvent>()
        MidiByteParser(clock = { 30L }, emit = actual::add)
            .feed(byteArrayOf(0xB0.toByte(), 64, 63, 64, 64))
        assertEquals(listOf(PianoEvent.Sustain(false, 30), PianoEvent.Sustain(true, 30)), actual)
    }

    @Test fun runningStatusAndChunkedMessagesAreSupported() {
        val actual = mutableListOf<PianoEvent>()
        val parser = MidiByteParser(clock = { 44L }, emit = actual::add)
        parser.feed(byteArrayOf(0x90.toByte(), 60))
        parser.feed(byteArrayOf(90, 61, 80, 62, 0))
        assertEquals(
            listOf(PianoEvent.NoteOn(60, 90 / 127f, 44), PianoEvent.NoteOn(61, 80 / 127f, 44), PianoEvent.NoteOff(62, 44)),
            actual
        )
    }

    @Test fun realTimeBytesDoNotBreakRunningStatus() {
        val actual = mutableListOf<PianoEvent>()
        MidiByteParser(clock = { 56L }, emit = actual::add).feed(byteArrayOf(0x90.toByte(), 60, 0xF8.toByte(), 90, 61, 80))
        assertEquals(listOf(PianoEvent.NoteOn(60, 90 / 127f, 56), PianoEvent.NoteOn(61, 80 / 127f, 56)), actual)
    }

    @Test fun oneByteChannelMessagesDoNotShiftFollowingNotes() {
        val actual = mutableListOf<PianoEvent>()
        MidiByteParser(clock = { 57L }, emit = actual::add).feed(byteArrayOf(0xC0.toByte(), 5, 0x90.toByte(), 62, 100))
        assertEquals(listOf(PianoEvent.NoteOn(62, 100 / 127f, 57)), actual)
    }

    @Test fun unrelatedControlChangesAreIgnored() {
        val actual = mutableListOf<PianoEvent>()
        MidiByteParser(clock = { 55L }, emit = actual::add).feed(byteArrayOf(0xB0.toByte(), 7, 127))
        assertEquals(emptyList<PianoEvent>(), actual)
    }
}
