package ir.pianova.app.core.midi

import org.junit.Assert.assertEquals
import org.junit.Test

class MidiByteParserTest {
    @Test fun noteOnAndOffPreserveChannel() {
        val out = mutableListOf<Pair<Int, PianoEvent>>()
        MidiByteParser(clock = { 10 }, emit = { ch, ev -> out += ch to ev })
            .feed(byteArrayOf(0x92.toByte(), 60, 100, 0x82.toByte(), 60, 20))
        assertEquals(listOf(2 to PianoEvent.NoteOn(60, 100 / 127f, 10), 2 to PianoEvent.NoteOff(60, 10)), out)
    }
    @Test fun zeroVelocityIsNoteOffAndChannelIsRetained() {
        val out = mutableListOf<Pair<Int, PianoEvent>>()
        MidiByteParser(clock = { 22 }, emit = { ch, ev -> out += ch to ev }).feed(byteArrayOf(0x93.toByte(), 64, 0))
        assertEquals(listOf(3 to PianoEvent.NoteOff(64, 22)), out)
    }
    @Test fun sustainThresholdAndChannelAreDecoded() {
        val out = mutableListOf<Pair<Int, PianoEvent>>()
        MidiByteParser(clock = { 30 }, emit = { ch, ev -> out += ch to ev }).feed(byteArrayOf(0xB4.toByte(), 64, 63, 64, 64))
        assertEquals(listOf(4 to PianoEvent.Sustain(false, 30), 4 to PianoEvent.Sustain(true, 30)), out)
    }
    @Test fun runningStatusAndChunkingRetainChannel() {
        val out = mutableListOf<Pair<Int, PianoEvent>>()
        val p = MidiByteParser(clock = { 44 }, emit = { ch, ev -> out += ch to ev })
        p.feed(byteArrayOf(0x91.toByte(), 60)); p.feed(byteArrayOf(90, 61, 80, 62, 0))
        assertEquals(listOf(1 to PianoEvent.NoteOn(60, 90 / 127f, 44), 1 to PianoEvent.NoteOn(61, 80 / 127f, 44), 1 to PianoEvent.NoteOff(62, 44)), out)
    }
    @Test fun realtimeBytesDoNotBreakRunningStatus() {
        val out = mutableListOf<Pair<Int, PianoEvent>>()
        MidiByteParser(clock = { 56 }, emit = { ch, ev -> out += ch to ev }).feed(byteArrayOf(0x90.toByte(), 60, 0xF8.toByte(), 90, 61, 80))
        assertEquals(listOf(0 to PianoEvent.NoteOn(60, 90 / 127f, 56), 0 to PianoEvent.NoteOn(61, 80 / 127f, 56)), out)
    }
    @Test fun oneByteChannelMessagesDoNotShiftAndChannelChanges() {
        val out = mutableListOf<Pair<Int, PianoEvent>>()
        MidiByteParser(clock = { 57 }, emit = { ch, ev -> out += ch to ev }).feed(byteArrayOf(0xC5.toByte(), 5, 0x95.toByte(), 62, 100))
        assertEquals(listOf(5 to PianoEvent.NoteOn(62, 100 / 127f, 57)), out)
    }
    @Test fun unrelatedControlChangesAreIgnored() {
        val out = mutableListOf<Pair<Int, PianoEvent>>()
        MidiByteParser(clock = { 55 }, emit = { ch, ev -> out += ch to ev }).feed(byteArrayOf(0xB0.toByte(), 7, 127))
        assertEquals(emptyList<Pair<Int, PianoEvent>>(), out)
    }
    @Test fun samePitchOnDifferentChannelsProducesDistinctEvents() {
        val out = mutableListOf<Pair<Int, PianoEvent>>()
        MidiByteParser(clock = { 0 }, emit = { ch, ev -> out += ch to ev }).feed(
            byteArrayOf(0x90.toByte(), 60, 90, 0x91.toByte(), 60, 80, 0x80.toByte(), 60, 0, 0x81.toByte(), 60, 0)
        )
        assertEquals(listOf(0 to PianoEvent.NoteOn(60, 90 / 127f, 0), 1 to PianoEvent.NoteOn(60, 80 / 127f, 0), 0 to PianoEvent.NoteOff(60, 0), 1 to PianoEvent.NoteOff(60, 0)), out)
    }
    @Test fun sustainEventsFromDifferentChannelsRemainDistinct() {
        val out = mutableListOf<Pair<Int, PianoEvent>>()
        MidiByteParser(clock = { 0 }, emit = { ch, ev -> out += ch to ev }).feed(byteArrayOf(0xB0.toByte(), 64, 127, 0xB1.toByte(), 64, 127, 0xB0.toByte(), 64, 0, 0xB1.toByte(), 64, 0))
        assertEquals(listOf(0 to PianoEvent.Sustain(true, 0), 1 to PianoEvent.Sustain(true, 0), 0 to PianoEvent.Sustain(false, 0), 1 to PianoEvent.Sustain(false, 0)), out)
    }
}
