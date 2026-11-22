package ir.pianova.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import ir.pianova.app.domain.family.LearnerAudience
import ir.pianova.app.domain.family.LearnerProfile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LearnerSelectionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chooserShowsLearnerNameAvatarAudienceAndSeparateFamilyManagementEntry() {
        var selectedLearnerId: String? = null
        var manageFamilyClicked = false
        val learner = LearnerProfile(
            learnerId = "learner-nora",
            familyId = "family-a",
            displayName = "نورا",
            audience = LearnerAudience.CHILD,
            avatarRef = "🌟",
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1
        )

        composeRule.setContent {
            MaterialTheme {
                LearnerSelectionScreen(
                    learners = listOf(learner),
                    lastActiveLearnerId = learner.learnerId,
                    destinationLabel = { "مسیر یادگیری" },
                    onSelectLearner = { selectedLearnerId = it.learnerId },
                    onManageFamily = { manageFamilyClicked = true }
                )
            }
        }

        composeRule.onNodeWithText("نورا").assertIsDisplayed()
        composeRule.onNodeWithText("🌟").assertIsDisplayed()
        composeRule.onNodeWithText("کودک").assertIsDisplayed()
        composeRule.onNodeWithText("مدیریت خانواده و پروفایل‌ها").assertIsDisplayed()
        composeRule.onNodeWithText("نورا").performClick()
        composeRule.runOnIdle { assertEquals("learner-nora", selectedLearnerId) }
        composeRule.onNodeWithText("مدیریت خانواده و پروفایل‌ها").performClick()
        composeRule.runOnIdle { assert(manageFamilyClicked) }
    }

    @Test
    fun emptyFamilyStillOffersFamilyManagementWithoutInventingALearner() {
        composeRule.setContent {
            MaterialTheme {
                LearnerSelectionScreen(
                    learners = emptyList(),
                    lastActiveLearnerId = null,
                    destinationLabel = { "مسیر یادگیری" },
                    onSelectLearner = {},
                    onManageFamily = {}
                )
            }
        }

        composeRule.onNodeWithText("هنوز هنرجویی ثبت نشده است.").assertIsDisplayed()
        composeRule.onNodeWithText("مدیریت خانواده و پروفایل‌ها").assertIsDisplayed()
    }
}
