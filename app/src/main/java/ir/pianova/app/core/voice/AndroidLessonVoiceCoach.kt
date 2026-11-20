package ir.pianova.app.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    private var activeUtteranceId: String? = null
    private val completionCallbacks = ConcurrentHashMap<String, () -> Unit>()

    init {
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) {
                if (activeUtteranceId == utteranceId) {
                    activeUtteranceId = null
                    completionCallbacks.remove(utteranceId)?.invoke()
                } else {
                    completionCallbacks.remove(utteranceId)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                completionCallbacks.remove(utteranceId)
            }
            override fun onError(utteranceId: String, errorCode: Int) {
                completionCallbacks.remove(utteranceId)
            }
        })
    }

    override fun onInit(result: Int) {
        val tts = engine ?: return
        if (result != TextToSpeech.SUCCESS) {
            statusMutable.value = VoiceCoachStatus.Unavailable("موتور گفتار گوشی آماده نشد.")
            return
        }
        val persianVoices = tts.voices.orEmpty().filter { it.locale.language == "fa" }
        val selected = persianVoices.firstOrNull { it.locale.country.equals("IR", true) }
            ?: persianVoices.firstOrNull()
        if (selected != null) {
            tts.voice = selected
            initialized = true
            tts.setSpeechRate(rate)
            statusMutable.value = VoiceCoachStatus.Ready(selected.networkConnectionRequired.not())
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
        val languageResult = tts.setLanguage(locale)
        if (languageResult < TextToSpeech.LANG_AVAILABLE) {
            statusMutable.value = VoiceCoachStatus.Unavailable("صدای فارسی در موتور گفتار گوشی در دسترس نیست.")
            return
        }
        initialized = true
        tts.setSpeechRate(rate)
        statusMutable.value = VoiceCoachStatus.Ready(offlineAvailable = false)
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val clean = text.trim()
        val tts = engine ?: return
        if (!initialized || clean.isEmpty()) return
        stopUtterance(clearLastText = false)
        lastText = clean
        tts.setSpeechRate(rate)
        val id = UUID.randomUUID().toString()
        activeUtteranceId = id
        if (onDone != null) completionCallbacks[id] = onDone
        val result = tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result == TextToSpeech.ERROR) {
            completionCallbacks.remove(id)?.invoke()
            activeUtteranceId = null
        }
    }

    // Android TextToSpeech has no portable pause API. Resume restarts the
    // current sentence instead of pretending to preserve a word-level position.
    fun pause() {
        stopUtterance(clearLastText = false)
    }

    fun resume() {
        lastText?.let { speak(it) }
    }

    fun replay() {
        lastText?.let { speak(it) }
    }

    fun stop() = stopUtterance(clearLastText = true)

    private fun stopUtterance(clearLastText: Boolean) {
        engine?.stop()
        activeUtteranceId?.let(completionCallbacks::remove)
        activeUtteranceId = null
        completionCallbacks.clear()
        if (clearLastText) lastText = null
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
