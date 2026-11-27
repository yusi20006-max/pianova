package ir.pianova.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.pianova.app.domain.family.Family
import ir.pianova.app.domain.family.FamilyRepository
import ir.pianova.app.domain.family.LearnerAudience
import ir.pianova.app.domain.family.LearnerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FamilyManagementScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun learnerDeletionRequiresConfirmationAndCanBeCancelled() {
        val learner = LearnerProfile(
            learnerId = "learner-nora",
            familyId = "family-a",
            displayName = "نورا",
            audience = LearnerAudience.CHILD,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1
        )
        val repository = FakeFamilyRepository(listOf(learner))

        composeRule.setContent {
            MaterialTheme {
                FamilyManagementScreen(
                    repository = repository,
                    familyId = "family-a",
                    initialSetup = false,
                    onComplete = {}
                )
            }
        }

        composeRule.onNodeWithText("نورا").assertIsDisplayed()
        composeRule.onNodeWithText("حذف").performClick()
        composeRule.onNodeWithText("حذف پروفایل نورا").assertIsDisplayed()
        composeRule.onNodeWithText("این کار قابل بازگشت نیست.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("انصراف").performClick()
        composeRule.runOnIdle {
            assertEquals(0, repository.deleteCalls)
            assertEquals(1, repository.learners.value.size)
        }

        composeRule.onNodeWithText("حذف").performClick()
        composeRule.onNodeWithText("حذف پروفایل").performClick()
        composeRule.waitUntil { repository.learners.value.isEmpty() }
        composeRule.runOnIdle {
            assertEquals(1, repository.deleteCalls)
            assertTrue(repository.deletedIds.contains("learner-nora"))
        }
    }

    private class FakeFamilyRepository(initial: List<LearnerProfile>) : FamilyRepository {
        val learners = MutableStateFlow(initial)
        var deleteCalls = 0
        val deletedIds = mutableListOf<String>()

        override fun observeFamily(familyId: String): Flow<Family?> = flowOf(null)
        override fun observeLearners(familyId: String): Flow<List<LearnerProfile>> = learners
        override suspend fun saveFamily(family: Family) = Unit
        override suspend fun saveLearner(learner: LearnerProfile) {
            learners.value = learners.value.filterNot { it.learnerId == learner.learnerId } + learner
        }
        override fun observeGoal(learnerId: String): Flow<String?> = flowOf(null)
        override suspend fun saveGoal(learnerId: String, goalText: String?) = Unit
        override suspend fun deleteLearner(learnerId: String): Boolean {
            deleteCalls++
            deletedIds += learnerId
            val existing = learners.value.any { it.learnerId == learnerId }
            learners.value = learners.value.filterNot { it.learnerId == learnerId }
            return existing
        }
    }
}
