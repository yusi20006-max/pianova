package ir.pianova.app.core.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One scheduled reference note. Timing is in beats; pitch is MIDI. */
data class ReferenceNote(
    val midiNote: Int,
    val startBeat: Double,
    val durationBeats: Double,
    val velocity: Float = 0.82f
) {
    init {
        require(midiNote in 0..127) { "MIDI note out of range" }
        require(startBeat.isFinite() && startBeat >= 0.0) { "startBeat must be finite and >= 0" }
        require(durationBeats.isFinite() && durationBeats > 0.0) { "durationBeats must be finite and > 0" }
        require(velocity.isFinite() && velocity in 0f..1f) { "velocity must be in 0..1" }
    }
}

enum class ReferencePlaybackState { IDLE, PLAYING, PAUSED, STOPPED, COMPLETED }

data class ReferencePlaybackProgress(
    val state: ReferencePlaybackState = ReferencePlaybackState.IDLE,
    val currentBeat: Double = 0.0,
    val totalBeats: Double = 0.0,
    val elapsedMs: Long = 0L
) {
    val fraction: Float
        get() = if (totalBeats <= 0.0) 0f else (currentBeat / totalBeats).coerceIn(0.0, 1.0).toFloat()
    val isPlaying: Boolean get() = state == ReferencePlaybackState.PLAYING
}

/**
 * Sink for reference (example) playback. Reference playback NEVER feeds the
 * practice input pipeline; it only produces audio. Implementations must
 * guarantee a [noteOn] is balanced by [noteOff] for the same note.
 */
interface ReferenceAudioSink {
    fun noteOn(midiNote: Int, velocity: Float)
    fun noteOff(midiNote: Int)
}

/** Pure scheduling helper, independent of time, for testing and reuse. */
class ReferenceScheduler(val notes: List<ReferenceNote>) {
    val totalBeats: Double = notes.maxOfOrNull { it.startBeat + it.durationBeats } ?: 0.0

    fun startedBy(beat: Double): Set<Int> =
        notes.withIndex().filter { it.value.startBeat <= beat + EPS }.map { it.index }.toSet()

    fun endedBy(beat: Double): Set<Int> =
        notes.withIndex().filter { it.value.startBeat + it.value.durationBeats <= beat + EPS }.map { it.index }.toSet()

    fun stateAt(beat: Double): Pair<Set<Int>, Set<Int>> = startedBy(beat) to endedBy(beat)

    private companion object { const val EPS = 1e-6 }
}

/**
 * Plays [ReferenceNote]s at [tempoBpm], driving a [ReferenceAudioSink].
 *
 * Guarantees:
 * - single active session: [play]/[replay] cancel any in-flight job;
 * - exactly one [noteOn] per note and a matching [noteOff]; no stuck notes;
 * - [pause] releases active notes and remembers position; resume re-triggers
 *   notes still sounding;
 * - [stop] releases active notes and resets to 0;
 * - [release] stops and prevents further callbacks (use on dispose/navigation);
 * - no restart on recomposition (callers key once; controller never replays
 *   implicitly).
 *
 * Thread-safe. The caller-provided [scope] is used for the internal job and
 * cancelled independently; [release] cancels only this controller's job.
 */
class ReferencePlaybackController(
    private val scope: CoroutineScope,
    private val sink: ReferenceAudioSink,
    private val tickMs: Long = 16L,
    private val nowNanos: () -> Long = { System.nanoTime() },
    private val sleep: suspend (Long) -> Unit = { delay(it) }
) {
    private val lock = Mutex()
    private var job: Job? = null

    private var notes: List<ReferenceNote> = emptyList()
    private var scheduler: ReferenceScheduler = ReferenceScheduler(emptyList())
    private var tempoBpm: Double = 120.0

    private var anchorBeat: Double = 0.0
    private var anchorNanos: Long = 0L
    private var currentBeat: Double = 0.0

    private val emittedOn = mutableSetOf<Int>()
    private val emittedOff = mutableSetOf<Int>()
    private val active = mutableSetOf<Int>()

    private val _progress = MutableStateFlow(ReferencePlaybackProgress())
    val progress: StateFlow<ReferencePlaybackProgress> = _progress.asStateFlow()

    val hasContent: Boolean get() = notes.isNotEmpty() && scheduler.totalBeats > 0.0
    fun totalBeats(): Double = scheduler.totalBeats

    /** Replaces content. Stops any current playback first. */
    suspend fun load(notes: List<ReferenceNote>, tempoBpm: Double = 120.0) {
        stopInternal()
        this.notes = notes.toList()
        this.scheduler = ReferenceScheduler(this.notes)
        this.tempoBpm = safeTempo(tempoBpm, 120.0)
        publish(ReferencePlaybackState.IDLE, currentBeat = 0.0, elapsedMs = 0L, totalBeats = scheduler.totalBeats)
    }

    fun setTempo(tempoBpm: Double) {
        val newTempo = safeTempo(tempoBpm, this.tempoBpm)
        if (_progress.value.state == ReferencePlaybackState.PLAYING) {
            anchorBeat = currentBeat
            anchorNanos = nowNanos()
        }
        this.tempoBpm = newTempo
    }

    /** Starts (or resumes) playback. No-op if no valid content. Single session. */
    suspend fun play() {
        if (!hasContent) return
        if (_progress.value.state == ReferencePlaybackState.PLAYING) return
        val resuming = _progress.value.state == ReferencePlaybackState.PAUSED
        startSession(fromBeat = if (resuming) currentBeat else 0.0, resetEmissions = !resuming)
    }

    /** Replays from the start. */
    suspend fun replay() {
        if (!hasContent) return
        stopInternal()
        startSession(fromBeat = 0.0, resetEmissions = true)
    }

    suspend fun pause() = lock.withLock {
        if (_progress.value.state != ReferencePlaybackState.PLAYING) return
        job?.cancel(); job = null
        releaseActive()
        anchorBeat = currentBeat
        publish(ReferencePlaybackState.PAUSED)
    }

    suspend fun stop() = lock.withLock { stopInternal() }

    /** Stops and clears; call on dispose/navigation. */
    suspend fun release() = lock.withLock {
        job?.cancel(); job = null
        releaseActive()
        currentBeat = 0.0; anchorBeat = 0.0
        emittedOn.clear(); emittedOff.clear(); active.clear()
        publish(ReferencePlaybackState.IDLE, currentBeat = 0.0, elapsedMs = 0L)
    }

    /** Suspends until the current session job finishes; for tests. */
    suspend fun join() { job?.join() }

    // ---- internals (called under [lock] where mutation occurs) ----

    private suspend fun startSession(fromBeat: Double, resetEmissions: Boolean) {
        lock.withLock {
            job?.cancel(); job = null
            releaseActive()
            if (resetEmissions) {
                emittedOn.clear(); emittedOff.clear(); active.clear()
            } else {
                val (on, off) = scheduler.stateAt(fromBeat)
                emittedOn.clear(); emittedOn.addAll(on)
                emittedOff.clear(); emittedOff.addAll(off)
                active.clear(); active.addAll(on); active.removeAll(off)
                active.forEach { idx -> notes.getOrNull(idx)?.let { sink.noteOn(it.midiNote, it.velocity) } }
            }
            currentBeat = fromBeat
            anchorBeat = fromBeat
            anchorNanos = nowNanos()
            publish(ReferencePlaybackState.PLAYING, currentBeat = fromBeat, elapsedMs = beatToMs(fromBeat))
        }

        val total = scheduler.totalBeats
        val jobRef = scope.launch {
            var guard = 0L
            val maxTicks = ((total / beatPerMs().coerceAtLeast(1e-9)) / tickMs.coerceAtLeast(1L)).toLong() + 10_000L
            while (true) {
                sleep(tickMs)
                val finished = lock.withLock {
                    if (_progress.value.state != ReferencePlaybackState.PLAYING) return@launch
                    val elapsedNanos = (nowNanos() - anchorNanos).coerceAtLeast(0L)
                    val elapsedMs = elapsedNanos / 1_000_000L
                    val beat = anchorBeat + (elapsedMs.toDouble() / 1000.0) * (tempoBpm / 60.0)
                    currentBeat = beat
                    advance(beat)
                    publish(ReferencePlaybackState.PLAYING, currentBeat = beat, elapsedMs = elapsedMs + beatToMs(anchorBeat))
                    beat >= total
                }
                if (finished) {
                    lock.withLock {
                        releaseActive()
                        currentBeat = total
                        publish(ReferencePlaybackState.COMPLETED, currentBeat = total, elapsedMs = beatToMs(total))
                        job = null
                    }
                    return@launch
                }
                if (++guard > maxTicks) {
                    lock.withLock {
                        releaseActive()
                        currentBeat = total
                        publish(ReferencePlaybackState.COMPLETED, currentBeat = total, elapsedMs = beatToMs(total))
                        job = null
                    }
                    return@launch
                }
            }
        }
        lock.withLock { job = jobRef }
    }

    private suspend fun stopInternal() {
        job?.cancel(); job = null
        releaseActive()
        emittedOn.clear(); emittedOff.clear(); active.clear()
        currentBeat = 0.0; anchorBeat = 0.0
        publish(ReferencePlaybackState.STOPPED, currentBeat = 0.0, elapsedMs = 0L)
    }

    /** Fire noteOn/noteOff for notes starting/ending at or before [beat]. */
    private fun advance(beat: Double) {
        for (idx in notes.indices) {
            val n = notes[idx]
            val start = n.startBeat
            val end = n.startBeat + n.durationBeats
            if (start <= beat + EPS && idx !in emittedOn) {
                emittedOn += idx; active += idx
                sink.noteOn(n.midiNote, n.velocity)
            }
            if (end <= beat + EPS && idx in emittedOn && idx !in emittedOff) {
                emittedOff += idx
                if (active.remove(idx)) sink.noteOff(n.midiNote)
            }
        }
    }

    private fun releaseActive() {
        active.toList().forEach { idx -> notes.getOrNull(idx)?.let { sink.noteOff(it.midiNote) } }
        active.clear()
    }

    private fun beatPerMs(): Double = (tempoBpm / 60.0) / 1000.0
    private fun beatToMs(beat: Double): Long = (beat * 60000.0 / tempoBpm).toLong()

    private fun publish(
        state: ReferencePlaybackState,
        currentBeat: Double = this.currentBeat,
        elapsedMs: Long = _progress.value.elapsedMs,
        totalBeats: Double = scheduler.totalBeats
    ) {
        _progress.value = ReferencePlaybackProgress(
            state = state,
            currentBeat = currentBeat.coerceIn(0.0, totalBeats),
            totalBeats = totalBeats,
            elapsedMs = elapsedMs
        )
    }

    private fun safeTempo(value: Double, fallback: Double): Double =
        if (value.isFinite() && value > 0.0) value else fallback

    private companion object { const val EPS = 1e-6 }
}
