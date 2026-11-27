package ir.pianova.app.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt\nimport kotlin.math.pow

data class PitchDetectionResult(
    val midiNotes: List<Int>,
    val confidence: Float,
    val rms: Float
)

/**
 * Local spectral pitch detector. It scores MIDI fundamentals by harmonic salience,
 * masks harmonics of already-selected notes, and rejects low-energy/low-confidence
 * frames. It intentionally reports confidence rather than forcing a pitch guess.
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
        for (bin in 1 until magnitudes.size) {
            val mag = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
            magnitudes[bin] = mag
            totalMagnitude += mag
        }
        if (totalMagnitude <= 1e-9) return PitchDetectionResult(emptyList(), 0f, rms)

        val candidates = (minMidiNote..maxMidiNote).map { midi ->
            val frequency = midiToFrequency(midi)
            val fundamentalBin = frequency * fftSize / sampleRate
            var score = 0.0
            var harmonics = 0
            for (harmonic in 1..5) {
                val bin = (fundamentalBin * harmonic).toInt()
                if (bin >= magnitudes.size - 2) break
                val localPeak = (bin - 1..bin + 1).maxOf { magnitudes[it] }
                score += localPeak / sqrt(harmonic.toDouble())
                harmonics++
            }
            val confidence = (score / totalMagnitude * 2.5).coerceIn(0.0, 1.0)
            Candidate(midi, frequency, score, confidence, harmonics)
        }.sortedByDescending { it.score }

        val selected = mutableListOf<Candidate>()
        for (candidate in candidates) {
            if (selected.size >= maxPolyphony) break
            if (candidate.confidence < confidenceGate) continue
            if (selected.any { selectedNoteMasksCandidate(it, candidate, sampleRate) }) continue
            selected += candidate
        }
        val confidence = selected.maxOfOrNull { it.confidence }?.toFloat() ?: 0f
        return PitchDetectionResult(selected.map { it.midi }.sorted(), confidence, rms)
    }

    private fun selectedNoteMasksCandidate(selected: Candidate, candidate: Candidate, sampleRate: Int): Boolean {
        val binWidth = sampleRate.toDouble() / fftSize
        for (harmonic in 1..5) {
            val selectedHarmonic = selected.frequency * harmonic
            val candidateHarmonic = candidate.frequency * harmonic
            if (kotlin.math.abs(candidate.frequency - selectedHarmonic) <= binWidth * 1.6) return true
            if (kotlin.math.abs(candidateHarmonic - selected.frequency) <= binWidth * 1.6) return true
        }
        return false
    }

    private data class Candidate(val midi: Int, val frequency: Double, val score: Double, val confidence: Double, val harmonics: Int)

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
