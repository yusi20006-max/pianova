package ir.pianova.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioEngineStateTest {
    @Test
    fun mapsEveryNativeLifecycleState() {
        assertEquals(AudioEngineState.STOPPED, AudioEngineState.fromNative(0))
        assertEquals(AudioEngineState.RUNNING, AudioEngineState.fromNative(1))
        assertEquals(AudioEngineState.RECOVERING, AudioEngineState.fromNative(2))
        assertEquals(AudioEngineState.RECOVERY_FAILED, AudioEngineState.fromNative(3))
    }

    @Test
    fun unknownNativeStateFailsClosed() {
        assertEquals(AudioEngineState.RECOVERY_FAILED, AudioEngineState.fromNative(-1))
        assertEquals(AudioEngineState.RECOVERY_FAILED, AudioEngineState.fromNative(99))
    }
}
