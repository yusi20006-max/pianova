package ir.pianova.app.data.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ir.pianova.app.domain.model.LessonAudience
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LearnerSessionStoreTest {
    private lateinit var context: Context
    private lateinit var store: LearnerSessionStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("pianova_learner_sessions", Context.MODE_PRIVATE).edit().clear().commit()
        store = LearnerSessionStore(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("pianova_learner_sessions", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun unsafeLessonAndManagerRoutesReturnToLearning() {
        assertEquals("learning", safeLearnerDestination("lesson/lesson-01/chapter-01"))
        assertEquals("learning", safeLearnerDestination("family"))
        assertEquals("learning", safeLearnerDestination(null))
    }

    @Test
    fun safeTopLevelDestinationsAreRestored() {
        listOf("home", "learning", "practice", "library", "progress", "more", "settings").forEach {
            assertEquals(it, safeLearnerDestination(it))
        }
    }

    @Test
    fun deletedInactiveLearnerCannotRemainActive() {
        val currentProfiles = setOf("learner-a", "learner-c")
        assertNull(activeLearnerOrNull("learner-b", currentProfiles))
        assertEquals("learner-a", activeLearnerOrNull("learner-a", currentProfiles))
        assertNull(activeLearnerOrNull(null, currentProfiles))
    }

    @Test
    fun learnerPreferencesAndDestinationsNeverCrossProfiles() {
        store.rememberActiveLearner("learner-a")
        store.saveAudience("learner-a", LessonAudience.ADULTS)
        store.saveDestination("learner-a", "practice")
        store.saveVolume("learner-a", 0.45f)
        store.saveLabelMode("learner-a", 2)
        store.saveFirstMidi("learner-a", 72)

        assertEquals("learner-a", store.lastActiveLearnerId())
        assertEquals(LessonAudience.ADULTS, store.audienceFor("learner-a", LessonAudience.CHILDREN))
        assertEquals(LessonAudience.CHILDREN, store.audienceFor("learner-b", LessonAudience.CHILDREN))
        assertEquals("practice", store.destinationFor("learner-a"))
        assertEquals("learning", store.destinationFor("learner-b"))
        assertEquals(0.45f, store.volumeFor("learner-a"), 0.001f)
        assertEquals(0.8f, store.volumeFor("learner-b"), 0.001f)
        assertEquals(2, store.labelModeFor("learner-a"))
        assertEquals(1, store.labelModeFor("learner-b"))
        assertEquals(72, store.firstMidiFor("learner-a"))
        assertEquals(60, store.firstMidiFor("learner-b"))
    }

    @Test
    fun clearingDeletedProfileRemovesOnlyItsPreferencesAndLastSelection() {
        store.rememberActiveLearner("learner-a")
        store.saveAudience("learner-a", LessonAudience.ADULTS)
        store.saveDestination("learner-a", "practice")
        store.saveVolume("learner-a", 0.4f)
        store.saveAudience("learner-b", LessonAudience.CHILDREN)
        store.clearForLearner("learner-a")

        assertNull(store.lastActiveLearnerId())
        assertEquals(LessonAudience.CHILDREN, store.audienceFor("learner-a", LessonAudience.CHILDREN))
        assertEquals(LessonAudience.CHILDREN, store.audienceFor("learner-b", LessonAudience.ADULTS))
        assertEquals(0.8f, store.volumeFor("learner-a"), 0.001f)
    }
}
