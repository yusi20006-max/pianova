package ir.pianova.app.feature.songs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FallingNotesEngineTest {
    private fun song(): SongDocument = SongDocument(
        schemaVersion = 1,
        id = "practice-test",
        title = "Practice test",
        composer = "Pianova",
        category = "practice",
        difficulty = SongDifficulty.BEGINNER,
        tempo = 80,
        notes = listOf(
            SongNote(60, 0.0, 1.0, SongHand.RIGHT),
            SongNote(61, 1.0, 1.0, SongHand.LEFT),
            SongNote(64, 2.0, 1.0, SongHand.BOTH)
        ),
        practiceSections = listOf(
            SongPracticeSection("first", "First", 0.0, 1.0),
            SongPracticeSection("second", "Second", 1.0, 3.0)
        ),
        license = SongLicense("original", "Pianova", "Test fixture")
    )

    @Test
    fun handSelectionIncludesOnlyRequestedHandAndSharedNotes() {
        val source = song()
        assertEquals(listOf(60, 64), FallingNotesEngine.notesForHand(source, FallingNotesHand.RIGHT).map { it.midiNote })
        assertEquals(listOf(61, 64), FallingNotesEngine.notesForHand(source, FallingNotesHand.LEFT).map { it.midiNote })
        assertEquals(3, FallingNotesEngine.notesForHand(source, FallingNotesHand.BOTH).size)
    }

    @Test
    fun keyboardRangeContainsSongNotesAndStaysWithinFortyEightKeys() {
        val range = FallingNotesEngine.midiRange(song().notes)
        assertTrue(range.firstMidi <= 60)
        assertTrue(range.lastMidi >= 64)
        assertTrue(range.noteCount in 12..48)
        assertTrue(range.firstMidi >= 21)
        assertTrue(range.lastMidi <= 108)
    }

    @Test
    fun whiteAndBlackKeyCentersFollowPianoKeyboardGeometry() {
        val range = PianoMidiRange(firstMidi = 60, noteCount = 12)
        assertEquals(0.0625f, FallingNotesEngine.keyCenterFraction(60, range)!!, 0.0001f)
        assertEquals(0.125f, FallingNotesEngine.keyCenterFraction(61, range)!!, 0.0001f)
        assertEquals(0.1875f, FallingNotesEngine.keyCenterFraction(62, range)!!, 0.0001f)
        assertNull(FallingNotesEngine.keyCenterFraction(72, range))
    }

    @Test
    fun completedSectionsRequireEverySelectedNoteInThatSection() {
        val source = song()
        assertEquals(
            listOf("first"),
            FallingNotesEngine.completedSectionIds(source, source.notes, setOf(0))
        )
        assertEquals(
            listOf("first", "second"),
            FallingNotesEngine.completedSectionIds(source, source.notes, setOf(0, 1, 2))
        )
    }

    @Test
    fun accuracyIsBoundedAndHandlesEmptySongs() {
        assertEquals(0.75f, FallingNotesEngine.accuracy(3, 4), 0.0001f)
        assertEquals(0f, FallingNotesEngine.accuracy(1, 0), 0f)
        assertEquals(1f, FallingNotesEngine.accuracy(8, 4), 0f)
    }

    @Test
    fun totalBeatLengthIncludesFinalNoteDuration() {
        assertEquals(3f, FallingNotesEngine.totalBeats(song().notes), 0f)
    }
}
