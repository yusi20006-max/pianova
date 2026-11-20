package ir.pianova.app.core.audio

/**
 * JNI bridge to the native `pianova_audio` shared library built from
 * `app/src/main/cpp` (CMake + Google Oboe via Prefab).
 *
 * Phase 0 only exposes the engine status used by the smoke-test screen.
 * Phase 1 adds the real audio engine API (noteOn/noteOff/setSustain/...).
 */
object NativeAudioBridge {

    init {
        // Loads libpianova_audio.so (packaged from the externalNativeBuild output).
        System.loadLibrary("pianova_audio")
    }

    /**
     * Returns the status reported by the native side,
     * e.g. `"Oboe Native Engine Ready"`.
     */
    external fun getEngineStatus(): String
}
