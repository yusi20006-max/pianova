# Pianova performance audit (Issue #18)

## Scope
Code-level review of `PianoScreen`, `PianoPlayableKeys`, and `FallingNotesScreen` at the Issue #18 starting commit. This is source inspection, not a measured device benchmark.

## Findings

### Virtual piano
- The keyboard Canvas owns pointer input; active pointer-to-note mappings and per-note press counts prevent duplicate note-on/note-off events during multi-touch.
- Press-count state is observable Compose state. It updates on key transitions, not on an animation/frame clock. Multi-touch may trigger recomposition/redraw work on key changes; source inspection does not show a continuous 60fps recomposition loop.
- Haptic feedback occurs on the first transition from unpressed to pressed for a note.

### Falling Notes
- Animation is driven by `withFrameNanos` in a coroutine and updates playhead state.
- The Canvas reads the playhead in its draw lambda, allowing frame changes to invalidate drawing without requiring the full composable tree to recompose every frame.
- Session state (matching, playback and feedback) changes on events rather than being continuously updated per frame.

## Recommended physical-device profiling
Record device model, Android version, build SHA, refresh rate and:
- 10-minute session with repeated single notes, chords, and rapid multi-touch.
- Falling Notes with both hands, dense passages, and 50%, 75%, and 100% tempo.
- Frame timing/jank with Android Studio Profiler or Perfetto; CPU and memory; audio underruns; input-to-sound latency.
- Confirm no dropped/stuck notes after app background/foreground or engine stop.

## Evidence and limits
- Release Validation CI builds the R8/minified APK and AAB and runs the complete unit-test suite.
- Source inspection found no per-frame composable state update loop in either screen.
- Cloud CI cannot certify frame pacing, touch latency, audio/MIDI behavior or physical-device launch. Those remain hardware QA tasks.
