package ir.pianova.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PracticeSessionClockTest {
    @Test
    fun foregroundWithoutNoteActivityDoesNotCountAsPractice() {
        var elapsed = 10_000L
        val clock = PracticeSessionClock { elapsed }
        clock.resume()

        elapsed += 10 * 60_000L

        assertEquals(0L, clock.snapshot().activeDurationMillis)
    }

    @Test
    fun noteActivityCountsOnlyUntilIdleTimeoutAndExcludesBackground() {
        var elapsed = 1_000L
        val clock = PracticeSessionClock { elapsed }
        clock.resume()

        clock.recordNote()
        elapsed += 30_000L
        clock.recordNote()
        elapsed += 2 * 60_000L

        assertEquals(90_000L, clock.snapshot().activeDurationMillis)

        clock.pause()
        val interrupted = clock.snapshot()
        assertEquals(90_000L, interrupted.activeDurationMillis)
        assertTrue(interrupted.wasInterrupted)
        assertEquals(2, interrupted.notesPlayed)

        elapsed += 60 * 60_000L
        clock.resume()
        elapsed += 10 * 60_000L
        assertEquals(90_000L, clock.snapshot().activeDurationMillis)

        clock.recordNote()
        elapsed += 20_000L
        assertEquals(110_000L, clock.snapshot().activeDurationMillis)
        assertEquals(3, clock.snapshot().notesPlayed)
    }
}
