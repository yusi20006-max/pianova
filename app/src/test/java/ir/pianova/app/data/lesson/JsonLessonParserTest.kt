package ir.pianova.app.data.lesson

import ir.pianova.app.domain.model.LessonStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonLessonParserTest {
    private val parser = JsonLessonParser()

    @Test
    fun parsesVersionedLessonWithPolymorphicSteps() {
        val lesson = parser.parse(VALID_LESSON)

        assertEquals("first-notes", lesson.id)
        assertEquals(1, lesson.schemaVersion)
        assertEquals(3, lesson.chapters.single().steps.size)
        assert(lesson.chapters.single().steps[0] is LessonStep.Explanation)
        assert(lesson.chapters.single().steps[1] is LessonStep.Listen)
        assert(lesson.chapters.single().steps[2] is LessonStep.PlayNote)
        assertEquals(60, (lesson.chapters.single().steps[2] as LessonStep.PlayNote).midiNote)
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        val input = VALID_LESSON.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")
        assertThrows(LessonFormatException::class.java) { parser.parse(input) }
    }

    @Test
    fun rejectsMalformedJson() {
        assertThrows(LessonFormatException::class.java) { parser.parse("{ not-json") }
    }

    @Test
    fun rejectsUnknownStepType() {
        val input = VALID_LESSON.replace("\"type\":\"explanation\"", "\"type\":\"surprise\"")
        assertThrows(LessonFormatException::class.java) { parser.parse(input) }
    }

    @Test
    fun rejectsInvalidMidiNote() {
        val input = VALID_LESSON.replace("\"midiNote\":60", "\"midiNote\":200")
        assertThrows(LessonFormatException::class.java) { parser.parse(input) }
    }

    @Test
    fun rejectsUnknownFieldsToCatchContentTypos() {
        val input = VALID_LESSON.replace("\"title\": \"اولین نت‌ها\"", "\"titel\": \"اولین نت‌ها\"")
        assertThrows(LessonFormatException::class.java) { parser.parse(input) }
    }

    private companion object {
        const val VALID_LESSON = """
            {
              "schemaVersion": 1,
              "id": "first-notes",
              "title": "اولین نت‌ها",
              "chapters": [{
                "id": "chapter-1",
                "title": "شروع",
                "steps": [
                  {"type":"explanation","id":"intro","title":"معرفی","body":"متن"},
                  {"type":"listen","id":"listen","title":"گوش کن","audioAsset":"audio/c4.ogg"},
                  {"type":"play_note","id":"play","title":"بنواز","midiNote":60}
                ]
              }]
            }
        """
    }
}
