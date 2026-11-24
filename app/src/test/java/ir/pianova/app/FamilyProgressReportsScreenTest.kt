package ir.pianova.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import ir.pianova.app.core.database.LessonProgressEntity
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.core.database.PracticeSessionEntity
import ir.pianova.app.data.persistence.ProgressRepository
import ir.pianova.app.data.persistence.RoomFamilyRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FamilyProgressReportsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: PianovaDatabase
    private lateinit var familyRepository: RoomFamilyRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PianovaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        familyRepository = RoomFamilyRepository(database)
        runBlocking {
            familyRepository.saveFamily(ir.pianova.app.domain.family.Family("family-a", 1, 1))
            familyRepository.saveLearner(learner("learner-a", "آراد"))
            familyRepository.saveLearner(learner("learner-b", "نورا"))
            familyRepository.saveLearner(learner("learner-c", "سارا"))
            familyRepository.saveGoal("learner-a", "تمرین روزانه آراد")
            familyRepository.saveGoal("learner-b", "تمرین روزانه نورا")
            val progress = ProgressRepository(database)
            progress.savePracticeSession(
                PracticeSessionEntity("session-a", "learner-a", "lesson-1", 1_000, 601_000, 600_000, 12, 10, 2)
            )
            progress.savePracticeSession(
                PracticeSessionEntity("session-b", "learner-b", "lesson-1", 1_000, 181_000, 180_000, 6, 5, 1)
            )
            progress.saveLessonProgress(
                LessonProgressEntity("progress-c", "learner-c", "lesson-open", "[]", false, 300_000, currentStepId = "step-1")
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun learnerReportsShowOnlyThatLearnersPersistedMetricsWithoutSwitchingProfile() {
        val activeLearnerId = "learner-a"
        composeRule.setContent {
            MaterialTheme {
                FamilyProgressReportsScreen(
                    familyId = "family-a",
                    familyRepository = familyRepository,
                    database = database,
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("گزارش خانواده").assertIsDisplayed()
        composeRule.onNodeWithText("آراد").performClick()
        composeRule.onNodeWithText("تمرین روزانه آراد").assertIsDisplayed()
        composeRule.onNodeWithText("10 دقیقه").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("learner-a", activeLearnerId) }

        composeRule.onNodeWithText("گزارش خانواده").performClick()
        composeRule.onNodeWithText("نورا").performClick()
        composeRule.onNodeWithText("تمرین روزانه نورا").assertIsDisplayed()
        composeRule.onNodeWithText("3 دقیقه").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("learner-a", activeLearnerId) }
    }

    @Test
    fun lessonOnlyRecentActivityIsNotHiddenByTheEmptyState() {
        composeRule.setContent {
            MaterialTheme {
                FamilyProgressReportsScreen(
                    familyId = "family-a",
                    familyRepository = familyRepository,
                    database = database,
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("سارا").performClick()
        composeRule.onNodeWithText("آخرین فعالیت در درس").assertIsDisplayed()
    }

    private fun learner(id: String, name: String) = ir.pianova.app.domain.family.LearnerProfile(
        learnerId = id,
        familyId = "family-a",
        displayName = name,
        audience = ir.pianova.app.domain.family.LearnerAudience.CHILD,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1
    )
}
