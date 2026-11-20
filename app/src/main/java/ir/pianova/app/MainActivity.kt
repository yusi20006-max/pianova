package ir.pianova.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import ir.pianova.app.core.audio.OboePianoAudioEngine
import ir.pianova.app.core.designsystem.PianovaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var audioEngine: OboePianoAudioEngine? = null
    private var engineLoading = false
    private var engineStatus by mutableStateOf("در حال آماده‌سازی بانک صدای پیانو…")
    private var engineReady by mutableStateOf(false)
    private var engineRunning by mutableStateOf(false)
    private var outputVolume = 0.8f
    private var previousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private val audioOperationMutex = Mutex()
    private var isInForeground = false
    private var audioStartPending = false
    private var resumeAudioAfterPause = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previousOrientation = requestedOrientation

        setContent {
            PianovaTheme {
                PianovaApp(
                    pianoStatus = engineStatus,
                    pianoReady = engineReady,
                    pianoRunning = engineRunning,
                    onPianoStart = ::startAudioEngine,
                    onPianoStop = ::stopAudioEngine,
                    onNoteOn = { note, velocity -> audioEngine?.noteOn(note, velocity) == true },
                    onNoteOff = { note -> audioEngine?.noteOff(note) },
                    onSustain = { enabled -> audioEngine?.setSustain(enabled) },
                    onVolume = { volume -> outputVolume = volume; audioEngine?.setVolume(volume) },
                    onPianoVisibilityChanged = { visible ->
                        requestedOrientation = if (visible) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            previousOrientation
                        }
                    }
                )
            }
        }
        loadAudioEngine()
    }

    private fun loadAudioEngine() {
        if (engineLoading || audioEngine != null) return
        engineLoading = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { OboePianoAudioEngine(applicationContext) }
            }
            engineLoading = false
            result.fold(
                onSuccess = { created ->
                    if (isDestroyed) {
                        withContext(Dispatchers.IO) { created.close() }
                    } else {
                        audioEngine = created
                        engineReady = true
                        engineStatus = "موتور آماده است؛ شروع صدا را بزنید"
                    }
                },
                onFailure = { error ->
                    engineReady = false
                    engineStatus = "خطا در بارگذاری صدا: ${error.message ?: "نامشخص"}"
                }
            )
        }
    }

    private fun startAudioEngine() {
        val engine = audioEngine ?: return
        audioStartPending = true
        lifecycleScope.launch {
            val started = audioOperationMutex.withLock {
                if (!isInForeground || audioEngine !== engine) {
                    false
                } else {
                    withContext(Dispatchers.IO) {
                        engine.start().also { if (it) engine.setVolume(outputVolume) }
                    }.also { opened ->
                        if (opened && !isInForeground) {
                            withContext(Dispatchers.IO) {
                                engine.stopAllNotes()
                                engine.stop()
                            }
                        }
                    } && isInForeground
                }
            }
            audioStartPending = false
            if (audioEngine === engine && isInForeground) {
                engineRunning = started
                engineStatus = if (started) {
                    "موتور فعال است؛ کلاویه‌ها را لمس کنید"
                } else {
                    "شروع خروجی صوتی ناموفق بود"
                }
            }
        }
    }

    private fun stopAudioEngine() {
        stopAudioEngine("موتور متوقف است")
    }

    private fun stopAudioEngine(statusMessage: String) {
        val engine = audioEngine ?: run {
            engineRunning = false
            return
        }
        engineRunning = false
        lifecycleScope.launch {
            audioOperationMutex.withLock {
                withContext(Dispatchers.IO) {
                    engine.stopAllNotes()
                    engine.stop()
                }
            }
            if (audioEngine === engine) engineStatus = statusMessage
        }
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true
        if (resumeAudioAfterPause) {
            resumeAudioAfterPause = false
            startAudioEngine()
        }
    }

    override fun onPause() {
        isInForeground = false
        if (engineRunning || audioStartPending) {
            resumeAudioAfterPause = true
            stopAudioEngine("با خروج برنامه از پیش‌زمینه، صدا متوقف شد")
        }
        super.onPause()
    }

    override fun onStop() {
        if (engineRunning || audioStartPending) {
            stopAudioEngine("با رفتن برنامه به پس‌زمینه، صدا متوقف شد")
        }
        super.onStop()
    }

    override fun onDestroy() {
        isInForeground = false
        resumeAudioAfterPause = false
        val engine = audioEngine
        audioEngine = null
        if (engine != null) engine.close()
        requestedOrientation = previousOrientation
        super.onDestroy()
    }
}
