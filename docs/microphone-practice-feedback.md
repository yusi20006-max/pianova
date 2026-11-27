# Microphone-based Practice Feedback (Issue #34)

## Architecture

- `AcousticPianoInput` is a separate `PianoInput` source. It is started only after the learner explicitly taps the microphone practice control and Android grants `RECORD_AUDIO`.
- `PolyphonicPitchDetector` performs local FFT harmonic-salience analysis for fundamentals in the beginner piano range. It returns candidate MIDI pitches, confidence, and RMS level.
- The input adapter requires stable pitch detections across frames before NoteOn and uses short release hysteresis before NoteOff. Low signal/confidence produces guidance instead of a guessed note.
- `PracticeInputRouter` selects either the existing MIDI/touch input or microphone input for evaluation, preventing duplicate note events when both hear/play the same note.
- The existing `EvaluationEngine` remains responsible for correct/wrong/missing/extra-note lesson feedback.

## Privacy

- Microphone permission is explicit and requested only when the learner starts microphone practice.
- A visible listening indicator and Stop control are provided.
- Listening stops when the practice screen exits or the app backgrounds.
- Raw PCM remains in a short in-memory analysis buffer; it is not written to files, database, or network.
- No microphone listening starts automatically.

## Testing and validation

Unit tests cover a clean single tone, a synthetic major chord, silence, and low-level noise. Real-device acoustic validation remains necessary for room noise, piano timbre, sustain pedal behavior, microphone distance, and Android device-specific capture levels.

## Known limits

The detector is a lightweight harmonic-salience prototype, not a studio-grade transcription engine. Confidence gating and retry guidance are preferred over incorrect note assertions. Physical-device calibration and further tuning are expected before declaring full acoustic acceptance.
