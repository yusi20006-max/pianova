package ir.pianova.app.feature.songs

enum class FallingNotesHand {
    RIGHT,
    LEFT,
    BOTH
}

data class PianoMidiRange(
    val firstMidi: Int,
    val noteCount: Int
) {
    val lastMidi: Int get() = (firstMidi + noteCount - 1).coerceAtMost(108)
}

object FallingNotesEngine {
    fun notesForHand(song: SongDocument, hand: FallingNotesHand): List<SongNote> =
        song.notes.filter { note ->
            when (hand) {
                FallingNotesHand.BOTH -> true
                FallingNotesHand.RIGHT -> note.hand == SongHand.RIGHT || note.hand == SongHand.BOTH
                FallingNotesHand.LEFT -> note.hand == SongHand.LEFT || note.hand == SongHand.BOTH
            }
        }

    fun midiRange(notes: List<SongNote>): PianoMidiRange {
        require(notes.isNotEmpty()) { "At least one note is required to determine the keyboard range" }
        val minimum = notes.minOf { it.midiNote }
        val maximum = notes.maxOf { it.midiNote }
        var first = (minimum - 3).coerceAtLeast(21)
        var last = (maximum + 3).coerceAtMost(108)
        if (last - first + 1 > 48) first = (last - 47).coerceAtLeast(21)
        if (last - first + 1 < 12) last = (first + 11).coerceAtMost(108)
        if (last - first + 1 < 12) first = (last - 11).coerceAtLeast(21)
        return PianoMidiRange(first, (last - first + 1).coerceIn(1, 48))
    }

    /** Returns a compact 24-key window, clamped to the piano MIDI range, for a wide song range. */
    fun visibleKeyboardRange(songRange: PianoMidiRange, requestedFirstMidi: Int, visibleNoteCount: Int = 24): PianoMidiRange {
        val count = visibleNoteCount.coerceIn(12, 48)
        val first = requestedFirstMidi.coerceIn(21, (108 - count + 1).coerceAtLeast(21))
        return PianoMidiRange(first, count)
    }

    /** Matches PianoPlayableKeys white-key spacing and black-key boundary centers. */
    fun keyCenterFraction(midiNote: Int, range: PianoMidiRange): Float? {
        if (midiNote !in range.firstMidi..range.lastMidi) return null
        val whiteNotes = (range.firstMidi..range.lastMidi).filter { it % 12 in WHITE_PITCH_CLASSES }
        if (whiteNotes.isEmpty()) return null
        val whiteIndex = whiteNotes.indexOf(midiNote)
        if (whiteIndex >= 0) return (whiteIndex + 0.5f) / whiteNotes.size
        if (midiNote % 12 !in BLACK_PITCH_CLASSES) return null
        val previousWhite = (midiNote - 1 downTo range.firstMidi)
            .firstOrNull { it % 12 in WHITE_PITCH_CLASSES } ?: return null
        val previousIndex = whiteNotes.indexOf(previousWhite)
        if (previousIndex < 0) return null
        return (previousIndex + 1f) / whiteNotes.size
    }

    fun keyWidthFraction(midiNote: Int, range: PianoMidiRange): Float {
        val whiteCount = (range.firstMidi..range.lastMidi).count { it % 12 in WHITE_PITCH_CLASSES }
        if (whiteCount == 0) return 0f
        return if (midiNote % 12 in BLACK_PITCH_CLASSES) 0.58f / whiteCount else 0.72f / whiteCount
    }

    fun completedSectionIds(
        song: SongDocument,
        selectedNotes: List<SongNote>,
        matchedIndices: Set<Int>
    ): List<String> = song.practiceSections.filter { section ->
        val indices = selectedNotes.indices.filter { index ->
            val note = selectedNotes[index]
            note.startBeat >= section.startBeat &&
                note.startBeat + note.duration <= section.endBeat
        }
        indices.isNotEmpty() && indices.all { it in matchedIndices }
    }.map { it.id }

    fun accuracy(correctNotes: Int, totalNotes: Int): Float =
        if (totalNotes <= 0) 0f else (correctNotes.toFloat() / totalNotes).coerceIn(0f, 1f)

    fun totalBeats(notes: List<SongNote>): Float =
        notes.maxOfOrNull { (it.startBeat + it.duration).toFloat() } ?: 0f

    private val WHITE_PITCH_CLASSES = setOf(0, 2, 4, 5, 7, 9, 11)
    private val BLACK_PITCH_CLASSES = setOf(1, 3, 6, 8, 10)
}
