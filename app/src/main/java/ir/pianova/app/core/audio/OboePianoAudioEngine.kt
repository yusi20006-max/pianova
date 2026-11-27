package ir.pianova.app.core.audio

import android.content.Context
import java.util.concurrent.atomic.AtomicLong

enum class AudioEngineState {
    STOPPED,
    RUNNING,
    RECOVERING,
    RECOVERY_FAILED;

    companion object {
        fun fromNative(value: Int): AudioEngineState = when (value) {
            1 -> RUNNING
            2 -> RECOVERING
            3 -> RECOVERY_FAILED
            else -> RECOVERY_FAILED
        }
    }
}

/**
 * Oboe-backed implementation of [PianoAudioEngine].
 *
 * The SoundFont is loaded from the app's bundled assets before the low-latency
 * Oboe stream starts. Native note commands are placed on a bounded lock-free
 * queue and consumed by Oboe's real-time callback.
 */
class OboePianoAudioEngine(context: Context) : PianoAudioEngine {
    private val handle = AtomicLong(
        NativeAudioBridge.nativeCreate(context.applicationContext.assets)
    )

    init {
        check(handle.get() != 0L) {
            "Unable to initialize Pianova's bundled piano SoundFont"
        }
    }

    @Synchronized
    override fun start(): Boolean {
        val nativeHandle = handle.get()
        return nativeHandle != 0L && NativeAudioBridge.nativeStart(nativeHandle)
    }

    @Synchronized
    fun state(): AudioEngineState {
        val nativeHandle = handle.get()
        return if (nativeHandle == 0L) AudioEngineState.RECOVERY_FAILED
        else AudioEngineState.fromNative(NativeAudioBridge.nativeGetState(nativeHandle))
    }

    @Synchronized
    override fun stop() {
        val nativeHandle = handle.get()
        if (nativeHandle != 0L) NativeAudioBridge.nativeStop(nativeHandle)
    }

    @Synchronized
    override fun noteOn(note: Int, velocity: Float): Boolean {
        val nativeHandle = handle.get()
        return nativeHandle != 0L &&
            NativeAudioBridge.nativeNoteOn(nativeHandle, note, velocity)
    }

    @Synchronized
    override fun noteOff(note: Int): Boolean {
        val nativeHandle = handle.get()
        return nativeHandle != 0L &&
            NativeAudioBridge.nativeNoteOff(nativeHandle, note)
    }

    @Synchronized
    override fun setSustain(enabled: Boolean): Boolean {
        val nativeHandle = handle.get()
        return nativeHandle != 0L &&
            NativeAudioBridge.nativeSetSustain(nativeHandle, enabled)
    }

    @Synchronized
    override fun setVolume(volume: Float): Boolean {
        val nativeHandle = handle.get()
        return nativeHandle != 0L &&
            NativeAudioBridge.nativeSetVolume(nativeHandle, volume)
    }

    @Synchronized
    override fun stopAllNotes(): Boolean {
        val nativeHandle = handle.get()
        return nativeHandle != 0L &&
            NativeAudioBridge.nativeStopAllNotes(nativeHandle)
    }

    @Synchronized
    override fun close() {
        val nativeHandle = handle.getAndSet(0L)
        if (nativeHandle != 0L) NativeAudioBridge.nativeRelease(nativeHandle)
    }
}
