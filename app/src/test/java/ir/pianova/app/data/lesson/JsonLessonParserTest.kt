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
    fun parsesListenStepWithSynthesizedReferenceNotes() {
        val input = VALID_LESSON.replace(
            "\"audioAsset\":\"audio/c4.ogg\"",
            "\"midiNotes\":[60],\"noteDurationMs\":900"
        )
        val lesson = parser.parse(input)
        val listen = lesson.chapters.single().steps[1] as LessonStep.Listen
        assertEquals(listOf(60), listen.midiNotes)
        assertEquals(900L, listen.noteDurationMs)
        assertEquals(null, listen.audioAsset)
    }

    @Test
    fun rejectsInvalidListenReferenceNote() {
        val input = VALID_LESSON.replace(
            "\"audioAsset\":\"audio/c4.ogg\"",
            "\"midiNotes\":[200]"
        )
        assertThrows(LessonFormatException::class.java) { parser.parse(input) }
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        val input = VALID_LESSON.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")
        assertThrows(LessonFormatException::class.java) { parser.parse(input) }
    }

    @Test
    fun parsesVersionTwoWithSeparateDisplayAndNarrationAndMasteryCriteria() {
        val lesson = parser.parse(VALID_V2_LESSON)
        assertEquals(2, lesson.schemaVersion)
        assertEquals("fa-IR", lesson.language)
        assertEquals(3, lesson.masteryCriteria?.successfulIndependentAttempts)
        val step = lesson.chapters.single().steps.single() as LessonStep.PlayNote
        assertEquals("کلید را پیدا کن", step.displayText)
        assertEquals("یک کلید را بنواز.", step.narrationText)
        assertEquals("identify-white-keys", step.skillId)
        assertEquals(true, step.scaffolding?.level1?.revealsAnswer)
    }

    @Test
    fun rejectsVersionTwoWithoutExplicitNarrationText() {
        val input = VALID_V2_LESSON.replace("یک کلید را بنواز.", "")
        assertThrows(LessonFormatException::class.java) { parser.parse(input) }
    }

    @Test
    fun rejectsUnknownVersionTwoFields() {
        val input = VALID_V2_LESSON.replace("آزمون", "آزمون\", \"unexpected\": true")
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
        const val VALID_V2_LESSON = """
            {
              "schemaVersion": 2,
              "id": "v2-test",
              "title": "آزمون",
              "language": "fa-IR",
              "pedagogy": {
                "teacherPersonality": "adaptive",
                "learningPace": "adaptive",
                "notationProgression": "gradual",
                "waitModeByDefault": true,
                "visualGuidance": {
                  "automaticSuggestionsEnabled": true,
                  "manualSelectionEnabled": true,
                  "defaultLevel": 2,
                  "suggestMoreHelpAfterConsecutiveErrors": 2,
                  "suggestLessHelpAfterConsecutiveSuccesses": 2,
                  "inactivitySuggestionSeconds": 8
                }
              },
              "masteryCriteria": {
                "successfulIndependentAttempts": 3,
                "minimumDistinctSessions": 2
              },
              "chapters": [{
                "id": "chapter-1",
                "title": "شروع",
                "steps": [{
                  "type": "play_note",
                  "id": "white-key",
                  "title": "کلید سفید",
                  "displayText": "کلید را پیدا کن",
                  "narrationText": "یک کلید را بنواز.",
                  "skillId": "identify-white-keys",
                  "scaffolding": {
                    "level1": { "hintText": "کلید دو را بزن.", "revealsAnswer": true },
                    "level2": { "hintText": "به گروه دوتایی نگاه کن.", "revealsAnswer": false },
                    "level3": { "hintText": "یک کلید سفید پیدا کن.", "revealsAnswer": false }
                  },
                  "midiNote": 60
                }]
              }]
            }
        """

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
