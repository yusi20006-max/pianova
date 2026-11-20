package ir.pianova.app.core.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonVoiceEngineTest {
    @Test
    fun offlineEngineUsesOfficialSherpaPackage() {
        assertEquals("com.k2fsa.sherpa.onnx.tts.engine", LessonVoiceEngine.OFFLINE_PERSIAN.packageName)
    }

    @Test
    fun systemEngineUsesPlatformDefault() {
        assertEquals(null, LessonVoiceEngine.SYSTEM_DEFAULT.packageName)
    }

    @Test
    fun recognizesPersianLanguageCodes() {
        assertTrue(isPersianTtsLanguage("fa"))
        assertTrue(isPersianTtsLanguage("fas"))
        assertTrue(isPersianTtsLanguage("FA"))
        assertFalse(isPersianTtsLanguage("en"))
    }
}
