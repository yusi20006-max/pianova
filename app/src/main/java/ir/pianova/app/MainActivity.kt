package ir.pianova.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import ir.pianova.app.core.audio.OboePianoAudioEngine
import ir.pianova.app.core.designsystem.PianovaTheme
import ir.pianova.app.core.midi.PianoEvent
import ir.pianova.app.core.midi.MergedPianoInput
import ir.pianova.app.core.midi.UsbMidiInput
import ir.pianova.app.core.midi.BluetoothMidiInput
import ir.pianova.app.core.midi.UsbMidiDeviceStatus
import ir.pianova.app.core.midi.MidiTransport
import ir.pianova.app.core.midi.VirtualKeyboardInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.Manifest\nimport android.media.AudioDeviceInfo\nimport android.media.AudioManager\nimport android.content.Intent\nimport android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build

class MainActivity : ComponentActivity() {
    private var audioEngine: OboePianoAudioEngine? = null
    private val virtualKeyboardInput = VirtualKeyboardInput()
    private lateinit var usbMidiInput: UsbMidiInput
    private lateinit var bluetoothMidiInput: BluetoothMidiInput
    private var bluetoothPermissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private lateinit var mergedPianoInput: MergedPianoInput
    private var engineLoading = false
    private var audioOutputRouteStatus by mutableStateOf("مسیر خروجی پیش‌فرض سیستم")\n    private var engineStatus by mutableStateOf("در حال آماده‌سازی بانک صدای پیانو…")
    private var engineReady by mutableStateOf(false)
    private var engineRunning by mutableStateOf(false)
    private var outputVolume = 0.8f
    private var previousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private val audioOperationMutex = Mutex()
    private var isInForeground = false
    private var audioStartPending = false
    private var audioPlaybackRequested = false
    private var referencePlaybackPending = false
    private var referencePlaybackJob: Job? = null
    private var audioStateMonitorJob: Job? = null
    private var resumeAudioAfterPause = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previousOrientation = requestedOrientation
        usbMidiInput = UsbMidiInput(applicationContext)
        val settingsPreferences = getSharedPreferences("pianova_settings", MODE_PRIVATE)
        val preferredMidiIdentity = settingsPreferences.getString("midi_selected_identity", null)
        if (!preferredMidiIdentity.isNullOrBlank()) usbMidiInput.selectDeviceIdentity(preferredMidiIdentity)
        val preferredBluetoothIdentity = settingsPreferences.getString("bluetooth_midi_selected_identity", null)
        if (!preferredBluetoothIdentity.isNullOrBlank()) bluetoothMidiInput.selectDeviceIdentity(preferredBluetoothIdentity)
        mergedPianoInput = MergedPianoInput(listOf(virtualKeyboardInput, usbMidiInput, bluetoothMidiInput), lifecycleScope)

        // The UI emits source-independent events. Only this collector knows
        // about the concrete audio engine, keeping input and playback decoupled.
        lifecycleScope.launch {
            mergedPianoInput.events.collect { event ->
                val engine = audioEngine ?: return@collect
                if (!engineRunning) return@collect
                when (event) {
                    is PianoEvent.NoteOn -> engine.noteOn(event.midiNote, event.velocity)
                    is PianoEvent.NoteOff -> engine.noteOff(event.midiNote)
                    is PianoEvent.Sustain -> engine.setSustain(event.enabled)
                }
            }
        }

        setContent {
            val midiDevices by combine(usbMidiInput.deviceStatuses, bluetoothMidiInput.deviceStatuses) { usb, bluetooth -> usb + bluetooth }.collectAsState(initial = emptyList())
            PianovaTheme {
                PianovaApp(
                    pianoStatus = engineStatus,
                    pianoReady = engineReady,
                    pianoRunning = engineRunning,\n                    audioOutputRouteStatus = audioOutputRouteStatus,\n                    onBluetoothAudioSettings = ::openBluetoothAudioSettings,
                    onPianoStart = ::startAudioEngine,
                    onPianoStop = ::stopAudioEngine,
                    onNoteOn = { note, velocity -> virtualKeyboardInput.noteOn(note, velocity) },
                    onNoteOff = { note -> virtualKeyboardInput.noteOff(note) },
                    onSustain = { enabled -> virtualKeyboardInput.sustain(enabled) },
                    onVolume = { volume -> outputVolume = volume; audioEngine?.setVolume(volume) },
                    midiDevices = midiDevices,
                    onMidiRefresh = usbMidiInput::refreshDevices,
                    onMidiDeviceSelected = { deviceId ->
                        val identity = usbMidiInput.selectedDeviceIdentity(deviceId)
                        settingsPreferences.edit().putString("midi_selected_identity", identity).apply()
                        usbMidiInput.selectDevice(deviceId)
                    },
                    pianoInput = mergedPianoInput,
                    onPlayReferenceNotes = ::playReferenceNotes,
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

    private fun requestBluetoothMidiScan() {
        val required = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) bluetoothMidiInput.refreshDevices()
        else bluetoothPermissionLauncher?.launch(missing.toTypedArray())
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
                audioPlaybackRequested = started
                engineRunning = started
                engineStatus = if (started) {
                    "موتور فعال است؛ کلاویه‌ها را لمس کنید"
                } else {
                    "شروع خروجی صوتی ناموفق بود؛ برای تلاش دوباره بزنید"
                }
                if (started) monitorAudioEngineState(engine)
            }
        }
    }

    private fun playReferenceNotes(notes: List<Int>, durationMs: Long) {
        if (notes.isEmpty()) return
        referencePlaybackJob?.cancel()
        referencePlaybackPending = true
        referencePlaybackJob = lifecycleScope.launch {
            val engine = audioEngine ?: return@launch
            val started = audioOperationMutex.withLock {
                if (!isInForeground || audioEngine !== engine) {
                    false
                } else if (engineRunning) {
                    true
                } else {
                    withContext(Dispatchers.IO) {
                        engine.start().also { opened ->
                            if (opened) {
                                engine.setVolume(outputVolume)
                                if (!isInForeground) {
                                    engine.stopAllNotes()
                                    engine.stop()
                                }
                            }
                        }
                    } && isInForeground
                }
            }
            if (!started || !isInForeground || audioEngine !== engine) return@launch
            audioPlaybackRequested = true
            engineRunning = true
            engineStatus = "نمونه نت‌ها در حال پخش است"
            monitorAudioEngineState(engine)
            var activeNote: Int? = null
            try {
                notes.forEach { note ->
                    if (!isInForeground || audioEngine !== engine) return@launch
                    withContext(Dispatchers.IO) { engine.noteOn(note, 0.82f) }
                    activeNote = note
                    delay(durationMs.coerceIn(150L, 3000L))
                    withContext(Dispatchers.IO) { engine.noteOff(note) }
                    activeNote = null
                    delay(100L)
                }
            } finally {
                activeNote?.let { note ->
                    withContext(Dispatchers.IO) { engine.noteOff(note) }
                }
                if (audioEngine === engine && engineRunning) {
                    engineStatus = "موتور فعال است؛ کلاویه‌ها را لمس کنید"
                }
            }
        }
        referencePlaybackJob?.invokeOnCompletion { referencePlaybackPending = false }
    }

    private fun monitorAudioEngineState(engine: OboePianoAudioEngine) {
        audioStateMonitorJob?.cancel()
        audioStateMonitorJob = lifecycleScope.launch {
            while (isActive && audioEngine === engine && isInForeground) {
                val state = withContext(Dispatchers.IO) { engine.state() }\n                withContext(Dispatchers.IO) { refreshAudioOutputRoute() }
                if (audioEngine !== engine || !isInForeground) break
                when (state) {
                    ir.pianova.app.core.audio.AudioEngineState.RUNNING -> {
                        engineRunning = true
                        if (referencePlaybackJob?.isActive != true) {
                            engineStatus = "موتور فعال است؛ کلاویه‌ها را لمس کنید"
                        }
                    }
                    ir.pianova.app.core.audio.AudioEngineState.RECOVERING -> {
                        engineRunning = false
                        engineStatus = "خروجی صوتی قطع شد؛ در حال بازیابی…"
                    }
                    ir.pianova.app.core.audio.AudioEngineState.RECOVERY_FAILED -> {
                        audioPlaybackRequested = false
                        referencePlaybackJob?.cancel()
                        referencePlaybackJob = null
                        engineRunning = false
                        engineStatus = "بازیابی صدا ناموفق بود؛ برای تلاش دوباره بزنید"
                        break
                    }
                    ir.pianova.app.core.audio.AudioEngineState.STOPPED -> {
                        audioPlaybackRequested = false
                        if (engineRunning) {
                            engineRunning = false
                            engineStatus = "موتور متوقف است"
                        }
                    }
                }
                delay(100)
            }
        }
    }

    private fun refreshAudioOutputRoute() {\n        val engine = audioEngine\n        val deviceId = engine?.outputDeviceId() ?: -1\n        val audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager\n        val device = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.firstOrNull { it.id == deviceId }\n        audioOutputRouteStatus = when (device?.type) {\n            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Audio · ${device.productName}"\n            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE Audio · ${device.productName}"\n            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "بلندگوی گوشی"\n            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "هدفون سیمی"\n            AudioDeviceInfo.TYPE_USB_HEADSET -> "خروجی USB"\n            else -> if (deviceId >= 0) "خروجی صوتی · ${device?.productName ?: deviceId}" else "مسیر خروجی پیش‌فرض سیستم"\n        }\n    }\n\n    private fun openBluetoothAudioSettings() {\n        val panelIntent = if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_BLUETOOTH) else Intent(Settings.ACTION_BLUETOOTH_SETTINGS)\n        runCatching { startActivity(panelIntent) }.onFailure { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }\n    }\n\n    private fun stopAudioEngine() {
        stopAudioEngine("موتور متوقف است")
    }

    private fun stopAudioEngine(statusMessage: String) {
        referencePlaybackJob?.cancel()
        referencePlaybackJob = null
        referencePlaybackPending = false
        audioStateMonitorJob?.cancel()
        audioStateMonitorJob = null
        audioPlaybackRequested = false
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
        val wasReferencePlaybackPending = referencePlaybackPending
        referencePlaybackJob?.cancel()
        if (audioPlaybackRequested || engineRunning || audioStartPending || wasReferencePlaybackPending) {
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
        referencePlaybackJob?.cancel()
        audioStateMonitorJob?.cancel()
        resumeAudioAfterPause = false
        val engine = audioEngine
        audioEngine = null
        if (engine != null) engine.close()
        if (::usbMidiInput.isInitialized) usbMidiInput.close()
        if (::bluetoothMidiInput.isInitialized) bluetoothMidiInput.close()
        requestedOrientation = previousOrientation
        super.onDestroy()
    }
}
