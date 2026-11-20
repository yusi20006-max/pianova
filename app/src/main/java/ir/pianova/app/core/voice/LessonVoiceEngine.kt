package ir.pianova.app.core.voice

import java.util.Locale

enum class LessonVoiceEngine(val packageName: String?) {
    OFFLINE_PERSIAN("com.k2fsa.sherpa.onnx.tts.engine"),
    SYSTEM_DEFAULT(null)
}

internal fun isPersianTtsLanguage(languageCode: String): Boolean =
    languageCode.lowercase(Locale.ROOT) in setOf("fa", "fas")
