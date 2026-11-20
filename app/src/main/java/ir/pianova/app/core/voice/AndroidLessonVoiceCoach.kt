package ir.pianova.app.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.UUID

sealed interface VoiceCoachStatus {
    data object Initializing : VoiceCoachStatus
    data class Ready(val offlineAvailable: Boolean) : VoiceCoachStatus
    data class Unavailable(val message: String) : VoiceCoachStatus
}

class AndroidLessonVoiceCoach(context: Context) : TextToSpeech.OnInitListener {
    private val statusMutable = MutableStateFlow<VoiceCoachStatus>(VoiceCoachStatus.Initializing)
    val status: StateFlow<VoiceCoachStatus> = statusMutable
    private var engine: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var rate = DEFAULT_RATE
    private var lastText: String? = null
    private var initialized = false

    override fun onInit(result: Int) {
        val tts = engine ?: return
        if (result != TextToSpeech.SUCCESS) {
            statusMutable.value = VoiceCoachStatus.Unavailable("موتور گفتار گوشی آماده نشد.")
            return
        }
        val locale = Locale("fa", "IR")
        val availability = tts.isLanguageAvailable(locale)
        if (availability < TextToSpeech.LANG_AVAILABLE) {
            statusMutable.value = VoiceCoachStatus.Unavailable(
                if (availability == TextToSpeech.LANG_MISSING_DATA) {
                    "داده صدای فارسی نصب نیست. از تنظیمات تبدیل متن به گفتار اندروید، صدای فارسی را نصب کنید."
                } else {
                    "موتور گفتار انتخاب‌شده از زبان فارسی پشتیبانی نمی‌کند."
                }
            )
            return
        }
        val resultCode = tts.setLanguage(locale)
        if (resultCode < TextToSpeech.LANG_AVAILABLE) {
            statusMutable.value = VoiceCoachStatus.Unavailable("صدای فارسی در موتور گفتار گوشی در دسترس نیست.")
            return
        }
        val persianVoices = tts.voices.orEmpty().filter { it.locale.language == "fa" }
        val selected = persianVoices.firstOrNull { it.locale.country.equals("IR", true) }
            ?: persianVoices.firstOrNull()
        if (selected != null) tts.voice = selected
        val offline = selected?.networkConnectionRequired == false
        tts.setSpeechRate(rate)
        initialized = true
        statusMutable.value = VoiceCoachStatus.Ready(offlineAvailable = offline)
    }

    fun speak(text: String) {
        val clean = text.trim()
        val tts = engine ?: return
        if (!initialized || clean.isEmpty()) return
        lastText = clean
        tts.stop()
        tts.setSpeechRate(rate)
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    // Android TextToSpeech has no portable pause API. Resume restarts the current
    // sentence instead of pretending to preserve a word-level playback position.
    fun pause() {
        engine?.stop()
    }

    fun resume() {
        lastText?.let(::speak)
    }

    fun replay() {
        lastText?.let(::speak)
    }

    fun stop() {
        engine?.stop()
        lastText = null
    }

    fun setSpeechRate(value: Float) {
        rate = value.coerceIn(0.75f, 1.15f)
        if (initialized) engine?.setSpeechRate(rate)
    }

    fun shutdown() {
        stop()
        engine?.shutdown()
        engine = null
        initialized = false
    }

    companion object {
        const val DEFAULT_RATE = 0.9f
    }
}
