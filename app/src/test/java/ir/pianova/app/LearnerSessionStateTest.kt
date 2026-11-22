package ir.pianova.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LearnerSessionStateTest {
    private val preferences by lazy {
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("learner-session-state-test", Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    @Test
    fun legacyAudienceIsAssignedOnlyToTheFirstSelectedLearner() {
        preferences.edit().putString("lesson_audience", "adults").commit()

        assertEquals("adults", ensureLearnerAudiencePreference(preferences, "learner-a"))
        assertEquals("children", ensureLearnerAudiencePreference(preferences, "learner-b"))
        assertEquals("adults", preferences.getString(learnerAudiencePreferenceKey("learner-a"), null))
        assertEquals("children", preferences.getString(learnerAudiencePreferenceKey("learner-b"), null))
    }

    @Test
    fun preferencesAreNamespacedByStableLearnerId() {
        assertNotEquals(
            learnerAudiencePreferenceKey("learner-a"),
            learnerAudiencePreferenceKey("learner-b")
        )
        assertNotEquals(
            learnerRoutePreferenceKey("learner-a"),
            learnerRoutePreferenceKey("learner-b")
        )
        assertNotEquals(
            learnerPianoVolumePreferenceKey("learner-a"),
            learnerPianoVolumePreferenceKey("learner-b")
        )
        assertNotEquals(
            learnerPianoRangePreferenceKey("learner-a"),
            learnerPianoRangePreferenceKey("learner-b")
        )
        assertNotEquals(
            learnerPianoLabelModePreferenceKey("learner-a"),
            learnerPianoLabelModePreferenceKey("learner-b")
        )
    }

    @Test
    fun onlySafeTopLevelRoutesArePersisted() {
        assertEquals("library", learnerRouteToPersist("library"))
        assertEquals("learning", learnerRouteToPersist("learning"))
        assertNull(learnerRouteToPersist("practice"))
        assertNull(learnerRouteToPersist("lesson/first-notes/chapter-1"))
    }

    @Test
    fun unsafeOrMissingRoutesRestoreToHome() {
        assertEquals("home", learnerRouteToRestore(null))
        assertEquals("home", learnerRouteToRestore("practice"))
        assertEquals("progress", learnerRouteToRestore("progress"))
    }
}
