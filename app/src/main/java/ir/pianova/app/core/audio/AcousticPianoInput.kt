package ir.pianova.app.core.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import ir.pianova.app.core.midi.PianoEvent
import ir.pianova.app.core.midi.PianoInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class AcousticInputStatus(
    val listening: Boolean = false,
    val confidence: Float = 0f,
    val messageFa: String = "شنود صوتی خاموش است."
)

/**
 * Explicitly-started, local microphone input. PCM stays in the in-memory frame
 * buffer and is never persisted or uploaded. Note events share the MIDI contract.
 */
class AcousticPianoInput(context: Context) : PianoInput, AutoCloseable {
    private val appContext = context.applicationContext
    private val eventFlow = MutableSharedFlow<PianoEvent>(replay = 0, extraBufferCapacity = 0)
    override val events = eventFlow.asSharedFlow()
    private val eventQueue = Channel<PianoEvent>(Channel.UNLIMITED)
    private val emitterScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val detector = PolyphonicPitchDetector()
    private val statusFlow = MutableStateFlow(AcousticInputStatus())
    val status: StateFlow<AcousticInputStatus> = statusFlow
    private val running = AtomicBoolean(false)
    private var captureJob: Job? = null
    @Volatile private var audioRecord: AudioRecord? = null
    private val heldNotes = mutableSetOf<Int>()
    private val stableFrames = mutableMapOf<Int, Int>()
    private val missingFrames = mutableMapOf<Int, Int>()
    private val sampleRate = 22_050
    private val frameSize = 4096
    private val hopSize = frameSize / 2

    init {
        emitterScope.launch { for (event in eventQueue) eventFlow.emit(event) }
    }

    fun start(): Boolean {
        if (running.get()) return true
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            statusFlow.value = AcousticInputStatus(false, 0f, "برای شنیدن پیانو، اجازهٔ میکروفون لازم است.")
            return false
        }
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) {
            statusFlow.value = AcousticInputStatus(false, 0f, "میکروفون این دستگاه آمادهٔ استفاده نیست.")
            return false
        }
        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, frameSize * 2)
            )
        }.getOrNull()
        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { recorder?.release() }
            statusFlow.value = AcousticInputStatus(false, 0f, "راه‌اندازی میکروفون ناموفق بود.")
            return false
        }
        return runCatching {
            recorder.startRecording()
            audioRecord = recorder
            running.set(true)
            statusFlow.value = AcousticInputStatus(true, 0f, "در حال شنیدن پیانو…")
            captureJob = captureScope.launch { captureLoop(recorder) }
            true
        }.getOrElse {
            running.set(false)
            runCatching { recorder.release() }
            statusFlow.value = AcousticInputStatus(false, 0f, "شروع شنود صوتی ناموفق بود.")
            false
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) {
            statusFlow.value = AcousticInputStatus(false, 0f, "شنود صوتی خاموش است.")
            return
        }
        runCatching { audioRecord?.stop() }
        captureJob?.cancel()
        captureJob = null
        audioRecord?.let { runCatching { it.release() } }
        audioRecord = null
        releaseHeldNotes()
        statusFlow.value = AcousticInputStatus(false, 0f, "شنود متوقف شد. صدایی ذخیره نشده است.")
    }

    private suspend fun captureLoop(recorder: AudioRecord) {
        val frame = ShortArray(frameSize)
        val hop = ShortArray(hopSize)
        var filled = 0
        try {
            while (running.get()) {
                val count = recorder.read(hop, 0, hop.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) continue
                val copyCount = minOf(count, frameSize - filled)
                hop.copyInto(frame, filled, 0, copyCount)
                filled += copyCount
                if (filled >= frameSize) {
                    processFrame(frame)
                    frame.copyInto(frame, 0, hopSize, frameSize)
                    filled = hopSize
                }
            }
        } finally {
            if (running.getAndSet(false)) runCatching { recorder.stop() }
            runCatching { recorder.release() }
            if (audioRecord === recorder) audioRecord = null
            releaseHeldNotes()
            statusFlow.value = AcousticInputStatus(false, 0f, "شنود صوتی خاموش است.")
        }
    }

    private fun processFrame(frame: ShortArray) {
        val result = detector.detect(frame, sampleRate)
        val confidence = result.confidence
        if (confidence < 0.22f || result.midiNotes.isEmpty()) {
            heldNotes.toList().forEach { note ->
                val missing = (missingFrames[note] ?: 0) + 1
                missingFrames[note] = missing
                if (missing >= 2) releaseNote(note)
            }
            statusFlow.value = AcousticInputStatus(
                true,
                confidence,
                if (result.rms < 0.012f) "صدای پیانو کم است؛ کمی نزدیک‌تر بنواز." else "صدا واضح نیست؛ یک نت را آرام و واضح دوباره بنواز."
            )
            return
        }

        val detected = result.midiNotes.toSet()
        detected.forEach { note ->
            missingFrames[note] = 0
            val stable = (stableFrames[note] ?: 0) + 1
            stableFrames[note] = stable
            if (stable >= 2 && note !in heldNotes) {
                heldNotes += note
                val velocity = (result.rms * 5.0f).coerceIn(0.15f, 1f)
                eventQueue.trySend(PianoEvent.NoteOn(note, velocity, nowMs()))
            }
        }
        heldNotes.toList().filter { it !in detected }.forEach { note ->
            val missing = (missingFrames[note] ?: 0) + 1
            missingFrames[note] = missing
            if (missing >= 2) releaseNote(note)
        }
        statusFlow.value = AcousticInputStatus(true, confidence, "نت دریافت شد؛ ادامه بده.")
    }

    private fun releaseNote(note: Int) {
        if (heldNotes.remove(note)) eventQueue.trySend(PianoEvent.NoteOff(note, nowMs()))
        stableFrames.remove(note)
        missingFrames.remove(note)
    }

    private fun releaseHeldNotes() {
        heldNotes.toList().forEach { releaseNote(it) }
        stableFrames.clear()
        missingFrames.clear()
    }

    private fun nowMs() = System.nanoTime() / 1_000_000L

    override fun close() {
        stop()
        eventQueue.close()
    }
}
