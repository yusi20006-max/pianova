package ir.pianova.app.core.audio

import android.content.res.AssetManager

/**
 * Thin JNI boundary for the native Oboe + TinySoundFont engine.
 * The returned handle owns one native AudioEngine instance.
 */
internal object NativeAudioBridge {
    init {
        System.loadLibrary("pianova_audio")
    }

    external fun nativeCreate(assets: AssetManager): Long
    external fun nativeStart(handle: Long): Boolean
    external fun nativeGetState(handle: Long): Int
    external fun nativeStop(handle: Long)
    external fun nativeNoteOn(handle: Long, note: Int, velocity: Float): Boolean
    external fun nativeNoteOff(handle: Long, note: Int): Boolean
    external fun nativeSetSustain(handle: Long, enabled: Boolean): Boolean
    external fun nativeSetVolume(handle: Long, volume: Float): Boolean
    external fun nativeStopAllNotes(handle: Long): Boolean
    external fun nativeRelease(handle: Long)
}
