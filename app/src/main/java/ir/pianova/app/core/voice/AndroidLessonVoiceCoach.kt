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

class AndroidLessonVoiceCoach(context: Context) : TextToSpeech.OnInitListener {
    private val statusMutable = MutableStateFlow<VoiceCoachStatus>(VoiceCoachStatus.Initializing)
    val status: StateFlow<VoiceCoachStatus> = statusMutable
    private var engine: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var rate = DEFAULT_RATE
    private var lastText: String? = null
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
                } else {
                    completionCallbacks.remove(utteranceId)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                reportPlaybackError(utteranceId)
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                reportPlaybackError(utteranceId)
            }
        })
    }

    override fun onInit(result: Int) {
        val tts = engine ?: return
        if (result != TextToSpeech.SUCCESS) {
            statusMutable.value = VoiceCoachStatus.Unavailable("موتور گفتار گوشی آماده نشد.")
            return
        }

        // Route speech through the normal media channel so media volume and
        // Bluetooth speaker routing behave consistently on Android devices.
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )

        val persianVoices = tts.voices.orEmpty().filter { it.locale.language == "fa" }
        val selected = persianVoices.firstOrNull { it.locale.country.equals("IR", true) }
            ?: persianVoices.firstOrNull()
        if (selected != null) {
            tts.voice = selected
            initialized = true
            tts.setSpeechRate(rate)
            statusMutable.value = VoiceCoachStatus.Ready(!selected.isNetworkConnectionRequired)
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

    fun speak(text: String, onDone: (() -> Unit)? = null): Boolean {
        val clean = text.trim()
        val tts = engine ?: return false
        if (!initialized || clean.isEmpty()) return false

        stopUtterance(clearLastText = false)
        lastText = clean
        tts.setSpeechRate(rate)
        val id = UUID.randomUUID().toString()
        activeUtteranceId = id
        if (onDone != null) completionCallbacks[id] = onDone
        val result = tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result == TextToSpeech.ERROR) {
            activeUtteranceId = null
            statusMutable.value = VoiceCoachStatus.Failed("پخش گفتار شروع نشد. موتور گفتار فارسی گوشی را بررسی و دوباره تلاش کن.")
            return false
        }
        return true
    }

    private fun reportPlaybackError(utteranceId: String) {
        if (activeUtteranceId == utteranceId) {
            activeUtteranceId = null
            completionCallbacks.remove(utteranceId)
            statusMutable.value = VoiceCoachStatus.Failed(
                "موتور گفتار نتوانست صدا را پخش کند. صدای فارسی و بلندی صدای رسانه را بررسی کن."
            )
        } else {
            completionCallbacks.remove(utteranceId)
        }
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
