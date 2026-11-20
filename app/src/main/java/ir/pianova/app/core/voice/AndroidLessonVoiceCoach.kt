package ir.pianova.app.core.voice

import android.content.Context
import android.media.AudioAttributes
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
    data class Failed(val message: String) : VoiceCoachStatus
}

class AndroidLessonVoiceCoach(
    context: Context,
    enginePackage: String? = null
) : TextToSpeech.OnInitListener {
    private val statusMutable = MutableStateFlow<VoiceCoachStatus>(VoiceCoachStatus.Initializing)
    val status: StateFlow<VoiceCoachStatus> = statusMutable
    private var engine: TextToSpeech? = TextToSpeech(context.applicationContext, this, enginePackage)
    private var rate = DEFAULT_RATE
    private var initialized = false
    @Volatile private var activeUtteranceId: String? = null
    private val completionCallbacks = ConcurrentHashMap<String, () -> Unit>()

    init {
        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) {
                if (activeUtteranceId == utteranceId) {
                    activeUtteranceId = null
                    completionCallbacks.remove(utteranceId)?.let { callback ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post(callback)
                    }
                } else completionCallbacks.remove(utteranceId)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = reportPlaybackError(utteranceId)
            override fun onError(utteranceId: String, errorCode: Int) = reportPlaybackError(utteranceId)
        })
    }

    override fun onInit(result: Int) {
        val tts = engine ?: return
        if (result != TextToSpeech.SUCCESS) {
            statusMutable.value = VoiceCoachStatus.Unavailable(
                "موتور گفتار انتخاب‌شده آماده نشد. نصب موتور فارسی آفلاین را بررسی کن."
            )
            return
        }
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )

        val voices = tts.voices.orEmpty()
        val persianVoice = voices.firstOrNull {
            isPersianTtsLanguage(it.locale.language) &&
                (it.locale.country.equals("IR", true) || it.locale.country.isBlank())
        } ?: voices.firstOrNull { isPersianTtsLanguage(it.locale.language) }

        if (persianVoice != null) {
            tts.voice = persianVoice
            initialized = true
            tts.setSpeechRate(rate)
            statusMutable.value = VoiceCoachStatus.Ready(!persianVoice.isNetworkConnectionRequired)
            return
        }

        val locale = Locale("fa", "IR")
        val availability = tts.isLanguageAvailable(locale)
        if (availability < TextToSpeech.LANG_AVAILABLE) {
            statusMutable.value = VoiceCoachStatus.Unavailable(
                if (availability == TextToSpeech.LANG_MISSING_DATA) {
                    "دادهٔ صدای فارسی برای این موتور نصب نیست."
                } else "این موتور گفتار از زبان فارسی پشتیبانی نمی‌کند."
            )
            return
        }
        val languageResult = tts.setLanguage(locale)
        if (languageResult < TextToSpeech.LANG_AVAILABLE) {
            statusMutable.value = VoiceCoachStatus.Unavailable("صدای فارسی در موتور انتخاب‌شده در دسترس نیست.")
            return
        }
        initialized = true
        tts.setSpeechRate(rate)
        statusMutable.value = VoiceCoachStatus.Ready(offlineAvailable = false)
    }

    fun speak(text: String, onDone: (() -> Unit)? = null): Boolean {
        val clean = text.trim()
        val tts = engine ?: return false
        if (!initialized || clean.isEmpty()) return false
        stopUtterance()
        tts.setSpeechRate(rate)
        val id = UUID.randomUUID().toString()
        activeUtteranceId = id
        if (onDone != null) completionCallbacks[id] = onDone
        val result = tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result == TextToSpeech.ERROR) {
            activeUtteranceId = null
            completionCallbacks.remove(id)
            statusMutable.value = VoiceCoachStatus.Failed("پخش گفتار شروع نشد. موتور و صدای فارسی را بررسی کن.")
            return false
        }
        return true
    }

    private fun reportPlaybackError(utteranceId: String) {
        if (activeUtteranceId == utteranceId) {
            activeUtteranceId = null
            completionCallbacks.remove(utteranceId)
            statusMutable.value = VoiceCoachStatus.Failed(
                "موتور گفتار نتوانست صدا را پخش کند. بلندی صدای رسانه را بررسی کن."
            )
        } else completionCallbacks.remove(utteranceId)
    }

    fun stop() = stopUtterance()

    private fun stopUtterance() {
        activeUtteranceId = null
        completionCallbacks.clear()
        engine?.stop()
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
