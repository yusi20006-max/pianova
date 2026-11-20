package ir.pianova.app.core.midi

import kotlinx.coroutines.flow.SharedFlow

/**
 * A source of normalized piano events. Lesson and evaluation code should
 * depend on this contract rather than on a particular input device.
 */
interface PianoInput {
    val events: SharedFlow<PianoEvent>
}
