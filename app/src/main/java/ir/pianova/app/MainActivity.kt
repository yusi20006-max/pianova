package ir.pianova.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import ir.pianova.app.core.audio.OboePianoAudioEngine
import ir.pianova.app.core.designsystem.AboutDialog
import ir.pianova.app.core.designsystem.ElectricBlue
import ir.pianova.app.core.designsystem.PianovaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Native audio smoke-test screen. The SoundFont is parsed off the main thread;
 * the user can start/stop the Oboe stream and play notes on the test keyboard.
 */
class MainActivity : ComponentActivity() {
    private var audioEngine: OboePianoAudioEngine? = null
    private var engineLoading = false
    private var showAbout by mutableStateOf(false)
    private var engineStatus by mutableStateOf("در حال آماده‌سازی بانک صدای پیانو…")
    private var engineReady by mutableStateOf(false)
    private var engineRunning by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PianovaTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AudioEngineTestScreen(
                    status = engineStatus,
                    ready = engineReady,
                    running = engineRunning,
                    onStart = ::startAudioEngine,
                    onStop = ::stopAudioEngine,
                    onNoteOn = { note, velocity ->
                        audioEngine?.noteOn(note, velocity) == true
                    },
                    onNoteOff = { note -> audioEngine?.noteOff(note) },
                    onSustain = { enabled -> audioEngine?.setSustain(enabled) },
                    onVolume = { volume -> audioEngine?.setVolume(volume) },
                        onAbout = { showAbout = true }
                    )
                    if (showAbout) {
                        AboutDialog(onDismiss = { showAbout = false })
                    }
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
                        engineStatus = "موتور آماده است؛ برای شروع صدا بزنید"
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
            engineStatus = if (started) {
                "موتور فعال است؛ کلیدها را لمس کنید"
            } else {
                "شروع خروجی صوتی ناموفق بود"
            }
        }
    }

    private fun stopAudioEngine() {
        val engine = audioEngine ?: return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { engine.stop() }
            engineRunning = false
            engineStatus = "موتور متوقف است"
        }
    }

    override fun onStop() {
        val engine = audioEngine
        if (engine != null && engineRunning) {
            engineRunning = false
            lifecycleScope.launch(Dispatchers.IO) { engine.stop() }
            engineStatus = "با رفتن برنامه به پس‌زمینه، صدا متوقف شد"
        }
        super.onStop()
    }

    override fun onDestroy() {
        val engine = audioEngine
        audioEngine = null
        if (engine != null) engine.close()
        super.onDestroy()
    }
}

@Composable
private fun AudioEngineTestScreen(
    status: String,
    ready: Boolean,
    running: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onNoteOn: (Int, Float) -> Boolean,
    onNoteOff: (Int) -> Unit,
    onSustain: (Boolean) -> Unit,
    onVolume: (Float) -> Unit,
    onAbout: () -> Unit
) {
    var sustainEnabled by androidx.compose.runtime.remember { mutableStateOf(false) }
    var volume by androidx.compose.runtime.remember { mutableStateOf(0.8f) }
    val noteNames = listOf("C4", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B", "C5")
    val blackNotes = setOf(1, 3, 6, 8, 10)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "پیانوا",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "آزمایش موتور صدای پیانو",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = if (running) onStop else onStart,
                enabled = ready,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (running) "توقف موتور صدا" else "شروع موتور صدا")
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = "برای شنیدن صدا، کلیدها را لمس کنید و نگه دارید",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    noteNames.forEachIndexed { index, label ->
                        val note = 60 + index
                        val isBlack = index in blackNotes
                        PianoTestKey(
                            label = label,
                            black = isBlack,
                            enabled = running,
                            onPress = {
                                onNoteOn(note, if (isBlack) 0.72f else 0.82f)
                            },
                            onRelease = { onNoteOff(note) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("پدال نگه‌دارنده", color = MaterialTheme.colorScheme.onSurface)
                TextButton(
                    enabled = running,
                    onClick = {
                        sustainEnabled = !sustainEnabled
                        onSustain(sustainEnabled)
                    }
                ) {
                    Text(if (sustainEnabled) "روشن" else "خاموش")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("بلندی صدا", color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(12.dp))
                Slider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        if (running) onVolume(it)
                    },
                    enabled = running,
                    valueRange = 0.05f..1.0f,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Oboe 1.9.0  •  TinySoundFont  •  Stereo",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        IconButton(
            onClick = onAbout,
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "درباره برنامه",
                tint = ElectricBlue
            )
        }
    }
}

@Composable
private fun PianoTestKey(
    label: String,
    black: Boolean,
    enabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    val background = when {
        !enabled -> if (black) Color(0xFF26343A) else Color(0xFFB8C7CC)
        black -> Color(0xFF10232B)
        else -> Color(0xFFE8F1F3)
    }
    val foreground = if (black) Color.White else Color(0xFF10232B)

    Surface(
        modifier = Modifier
            .width(54.dp)
            .height(142.dp)
            .pointerInput(enabled, label) {
                detectTapGestures(
                    onPress = {
                        if (enabled) {
                            onPress()
                            tryAwaitRelease()
                            onRelease()
                        }
                    }
                )
            },
        shape = RoundedCornerShape(8.dp),
        color = background,
        contentColor = foreground,
        shadowElevation = if (black) 2.dp else 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (black) Color(0xFF31515C) else Color(0xFFD0DDE1)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(bottom = 12.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = foreground
            )
        }
    }
}
