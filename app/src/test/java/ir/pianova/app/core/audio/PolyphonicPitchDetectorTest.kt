package ir.pianova.app.core.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PolyphonicPitchDetectorTest {
    private val sampleRate = 22_050
    private val detector = PolyphonicPitchDetector()

    @Test
    fun detectsCleanA4WithConfidence() {
        val frame = toneFrame(listOf(69), 0.45f)
        val result = detector.detect(frame, sampleRate)
        assertTrue("A4 should be detected, got ${result.midiNotes}", 69 in result.midiNotes)
        assertTrue("confidence should be above gate", result.confidence >= 0.22f)
    }

    @Test
    fun detectsMajorChordPitches() {
        val frame = toneFrame(listOf(60, 64, 67), 0.22f)
        val result = detector.detect(frame, sampleRate)
        assertTrue("C major chord should include C4, got ${result.midiNotes}", 60 in result.midiNotes)
        assertTrue("C major chord should include E4, got ${result.midiNotes}", 64 in result.midiNotes)
        assertTrue("C major chord should include G4, got ${result.midiNotes}", 67 in result.midiNotes)
    }

    @Test
    fun rejectsSilenceAndLowLevelNoise() {
        val silence = detector.detect(ShortArray(4096), sampleRate)
        assertTrue(silence.midiNotes.isEmpty())
        val noise = ShortArray(4096) { ((it * 31 % 17) - 8).toShort() }
        val noisy = detector.detect(noise, sampleRate)
        assertTrue(noisy.midiNotes.isEmpty())
    }

    private fun toneFrame(notes: List<Int>, amplitude: Float): ShortArray {
        return ShortArray(4096) { index ->
            val t = index.toDouble() / sampleRate
            val sample = notes.sumOf { note -> sin(2.0 * PI * PolyphonicPitchDetector.midiToFrequency(note) * t) } * amplitude.toDouble()
            (sample.coerceIn(-0.95, 0.95) * 32767).toInt().toShort()
        }
    }
}
