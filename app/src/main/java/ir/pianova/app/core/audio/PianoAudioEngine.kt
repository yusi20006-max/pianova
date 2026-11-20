package ir.pianova.app.core.audio

/**
 * Platform-independent controls exposed to the virtual keyboard and lesson
 * engine. Calls are safe from multiple producer threads.
 */
interface PianoAudioEngine : AutoCloseable {
    fun start(): Boolean
    fun stop()
    fun noteOn(note: Int, velocity: Float = 0.8f): Boolean
    fun noteOff(note: Int): Boolean
    fun setSustain(enabled: Boolean): Boolean
    fun setVolume(volume: Float): Boolean
    fun stopAllNotes(): Boolean
    override fun close()
}
