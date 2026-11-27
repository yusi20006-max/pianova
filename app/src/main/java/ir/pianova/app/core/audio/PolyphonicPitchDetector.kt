package ir.pianova.app.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.pow

data class PitchDetectionResult(
    val midiNotes: List<Int>,
    val confidence: Float,
    val rms: Float
)

/**
 * Local FFT pitch detector using interpolated spectral peaks and harmonic rejection.
 * Low-energy and weak/ambiguous peaks are rejected instead of forced into a note.
 */
class PolyphonicPitchDetector(
    private val minMidiNote: Int = 36,
    private val maxMidiNote: Int = 88,
    private val maxPolyphony: Int = 3,
    private val rmsGate: Float = 0.012f,
    private val confidenceGate: Float = 0.22f
) {
    private val fftSize = 4096
    private val real = DoubleArray(fftSize)
    private val imag = DoubleArray(fftSize)
    private val window = DoubleArray(fftSize) { i -> 0.5 - 0.5 * cos(2.0 * PI * i / (fftSize - 1)) }

    fun detect(samples: ShortArray, sampleRate: Int): PitchDetectionResult {
        if (samples.size < fftSize || sampleRate <= 0) return PitchDetectionResult(emptyList(), 0f, 0f)
        var rmsSum = 0.0
        for (i in 0 until fftSize) {
            val normalized = samples[i] / 32768.0
            rmsSum += normalized * normalized
            real[i] = normalized * window[i]
            imag[i] = 0.0
        }
        val rms = sqrt(rmsSum / fftSize).toFloat()
        if (rms < rmsGate) return PitchDetectionResult(emptyList(), 0f, rms)

        fft()
        val magnitudes = DoubleArray(fftSize / 2 + 1)
        var totalMagnitude = 0.0
        var maxMagnitude = 0.0
        for (bin in 1 until magnitudes.size) {
            val mag = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
            magnitudes[bin] = mag
            totalMagnitude += mag
            if (mag > maxMagnitude) maxMagnitude = mag
        }
        if (totalMagnitude <= 1e-9 || maxMagnitude <= 1e-9) return PitchDetectionResult(emptyList(), 0f, rms)

        val binWidth = sampleRate.toDouble() / fftSize
        val minFrequency = midiToFrequency(minMidiNote) - 5.0
        val maxFrequency = midiToFrequency(maxMidiNote) * 5.0
        val peaks = mutableListOf<SpectralPeak>()
        val minBin = maxOf(2, (minFrequency / binWidth).toInt())
        val maxBin = minOf(magnitudes.size - 2, (maxFrequency / binWidth).toInt())
        for (bin in minBin..maxBin) {
            val magnitude = magnitudes[bin]
            if (magnitude < maxMagnitudeThreshold(maxMagnitude)) continue
            if (magnitude < magnitudes[bin - 1] || magnitude < magnitudes[bin + 1]) continue
            val left = magnitudes[bin - 1]
            val center = magnitude
            val right = magnitudes[bin + 1]
            val denominator = left - 2.0 * center + right
            val offset = if (kotlin.math.abs(denominator) < 1e-12) 0.0 else (0.5 * (left - right) / denominator).coerceIn(-0.5, 0.5)
            val frequency = (bin + offset) * binWidth
            if (frequency <= 0.0) continue
            val midi = (69.0 + 12.0 * ln(frequency / 440.0) / ln(2.0)).roundToInt()
            if (midi !in minMidiNote..maxMidiNote) continue
            val targetFrequency = midiToFrequency(midi)
            val cents = 1200.0 * ln(frequency / targetFrequency) / ln(2.0)
            val pitchQuality = (1.0 - kotlin.math.abs(cents) / 50.0).coerceIn(0.0, 1.0)
            val share = magnitude / totalMagnitude
            val confidence = (share * 4.0 * pitchQuality).coerceIn(0.0, 1.0)
            if (confidence >= confidenceGate) peaks += SpectralPeak(midi, frequency, magnitude, confidence)
        }

        val selected = mutableListOf<SpectralPeak>()
        for (peak in peaks.sortedBy { it.frequency }) {
            if (selected.size >= maxPolyphony) break
            if (selected.any { it.midi == peak.midi }) continue
            if (selected.any { isHarmonicOf(peak.frequency, it.frequency, binWidth) }) continue
            selected += peak
        }
        return PitchDetectionResult(
            selected.map { it.midi }.sorted(),
            selected.maxOfOrNull { it.confidence }?.toFloat() ?: 0f,
            rms
        )
    }

    private fun maxMagnitudeThreshold(maxMagnitude: Double) = maxMagnitude * 0.10

    private fun isHarmonicOf(candidate: Double, selectedFundamental: Double, binWidth: Double): Boolean {
        for (harmonic in 2..6) {
            if (kotlin.math.abs(candidate - selectedFundamental * harmonic) <= binWidth * 2.0) return true
        }
        return false
    }

    private data class SpectralPeak(val midi: Int, val frequency: Double, val magnitude: Double, val confidence: Double)

    private fun fft() {
        var j = 0
        for (i in 1 until fftSize) {
            var bit = fftSize shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var len = 2
        while (len <= fftSize) {
            val angle = -2.0 * PI / len
            val wLenR = cos(angle)
            val wLenI = kotlin.math.sin(angle)
            var start = 0
            while (start < fftSize) {
                var wr = 1.0
                var wi = 0.0
                for (k in 0 until len / 2) {
                    val uR = real[start + k]
                    val uI = imag[start + k]
                    val vR = real[start + k + len / 2] * wr - imag[start + k + len / 2] * wi
                    val vI = real[start + k + len / 2] * wi + imag[start + k + len / 2] * wr
                    real[start + k] = uR + vR
                    imag[start + k] = uI + vI
                    real[start + k + len / 2] = uR - vR
                    imag[start + k + len / 2] = uI - vI
                    val nextWr = wr * wLenR - wi * wLenI
                    wi = wr * wLenI + wi * wLenR
                    wr = nextWr
                }
                start += len
            }
            len = len shl 1
        }
    }

    companion object {
        fun midiToFrequency(note: Int): Double = 440.0 * 2.0.pow((note - 69) / 12.0)
        private fun Double.pow(exponent: Double): Double = kotlin.math.pow(this, exponent)
    }
}
