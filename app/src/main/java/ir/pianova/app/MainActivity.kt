package ir.pianova.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.lifecycleScope
import ir.pianova.app.core.audio.OboePianoAudioEngine
import ir.pianova.app.core.designsystem.PianovaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var audioEngine: OboePianoAudioEngine? = null
    private var engineLoading = false
    private var engineStatus by mutableStateOf("در حال آماده‌سازی بانک صدای پیانو…")
    private var engineReady by mutableStateOf(false)
    private var engineRunning by mutableStateOf(false)
    private var previousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previousOrientation = requestedOrientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        setContent {
            PianovaTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    PianoScreen(
                        status = engineStatus,
                        ready = engineReady,
                        running = engineRunning,
                        onStart = ::startAudioEngine,
                        onStop = ::stopAudioEngine,
                        onNoteOn = { note, velocity -> audioEngine?.noteOn(note, velocity) == true },
                        onNoteOff = { note -> audioEngine?.noteOff(note) },
                        onSustain = { enabled -> audioEngine?.setSustain(enabled) },
                        onVolume = { volume -> audioEngine?.setVolume(volume) }
                    )
                }
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
        lifecycleScope.launch {
            val started = withContext(Dispatchers.IO) {
                engine.start().also { if (it) engine.setVolume(0.8f) }
            }
            engineRunning = started
            engineStatus = if (started) "موتور فعال است؛ کلاویه‌ها را لمس کنید" else "شروع خروجی صوتی ناموفق بود"
        }
    }

    private fun stopAudioEngine() {
        val engine = audioEngine ?: return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                engine.stopAllNotes()
                engine.stop()
            }
            engineRunning = false
            engineStatus = "موتور متوقف است"
        }
    }

    override fun onStop() {
        val engine = audioEngine
        if (engine != null && engineRunning) {
            engineRunning = false
            lifecycleScope.launch(Dispatchers.IO) {
                engine.stopAllNotes()
                engine.stop()
            }
            engineStatus = "با رفتن برنامه به پس‌زمینه، صدا متوقف شد"
        }
        super.onStop()
    }

    override fun onDestroy() {
        val engine = audioEngine
        audioEngine = null
        if (engine != null) engine.close()
        requestedOrientation = previousOrientation
        super.onDestroy()
    }
}
