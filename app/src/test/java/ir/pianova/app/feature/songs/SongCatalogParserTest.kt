package ir.pianova.app.feature.songs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SongCatalogParserTest {
    private val validSong = """
        {
          "schemaVersion": 1,
          "id": "test-song",
          "title": "Test",
          "composer": "Pianova",
          "category": "practice",
          "difficulty": "beginner",
          "tempo": 80,
          "beatsPerBar": 4,
          "notes": [
            {"midiNote": 60, "startBeat": 0, "duration": 1, "hand": "right"},
            {"midiNote": 62, "startBeat": 1, "duration": 1, "hand": "right"}
          ],
          "practiceSections": [
            {"id": "part-1", "title": "Part 1", "startBeat": 0, "endBeat": 2}
          ],
          "license": {
            "type": "original",
            "name": "Pianova original content",
            "attribution": "Original test fixture",
            "licenseUrl": "https://github.com/yusi20006-max/pianova"
          }
        }
    """.trimIndent()

    @Test
    fun parsesValidSongAndPreservesPracticeMetadata() {
        val song = SongCatalogParser.parse(validSong)
        assertEquals("test-song", song.id)
        assertEquals(2, song.notes.size)
        assertEquals("Part 1", song.practiceSections.single().title)
        assertEquals("Pianova original content", song.license.name)
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        val source = validSong.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")
        assertThrows(IllegalArgumentException::class.java) { SongCatalogParser.parse(source) }
    }

    @Test
    fun rejectsOutOfRangeMidiNotes() {
        val source = validSong.replace("\"midiNote\": 60", "\"midiNote\": 10")
        assertThrows(IllegalArgumentException::class.java) { SongCatalogParser.parse(source) }
    }

    @Test
    fun rejectsMissingAttribution() {
        val source = validSong.replace("\"attribution\": \"Original test fixture\"", "\"attribution\": \"\"")
        assertThrows(IllegalArgumentException::class.java) { SongCatalogParser.parse(source) }
    }

    @Test
    fun acceptsAllSupportedCategoryLabels() {
        assertEquals(4, SONG_CATEGORIES.size)
        assertTrue(SONG_CATEGORIES.map { it.first }.containsAll(listOf("iranian", "classical", "international", "practice")))
    }

    @Test
    fun loadsEveryBundledSongAssetAndChecksUniqueIds() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val songs = SongCatalogRepository.load(context)

        assertEquals(10, songs.size)
        assertEquals(10, songs.map { it.id }.distinct().size)
        assertTrue(songs.any { it.category == "iranian" })
        assertTrue(songs.any { it.category == "classical" })
        assertTrue(songs.any { it.category == "international" })
        assertTrue(songs.any { it.category == "practice" })
        assertTrue(songs.all { it.notes.isNotEmpty() && it.practiceSections.isNotEmpty() })
    }
}