package ir.pianova.app.data.persistence
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ir.pianova.app.core.database.FamilyEntity
import ir.pianova.app.core.database.LearnerProfileEntity
import ir.pianova.app.core.database.LessonProgressEntity
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.domain.family.LearnerAudience
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LearningPathRepositoryTest {
    private lateinit var database: PianovaDatabase
    private lateinit var repository: LearningPathRepository
    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PianovaDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = LearningPathRepository(database)
        database.familyDao().upsert(FamilyEntity("family", 1, 1))
        database.learnerProfileDao().upsert(LearnerProfileEntity("learner-a", "family", "هنرجو الف",
            audienceCategory = "adult", createdAtEpochMillis = 1, updatedAtEpochMillis = 1))
        database.learnerProfileDao().upsert(LearnerProfileEntity("learner-b", "family", "هنرجو ب",
            audienceCategory = "child", createdAtEpochMillis = 2, updatedAtEpochMillis = 2))
    }
    @After fun tearDown() { database.close() }
    @Test fun initialPathIsRecommendedAndLearnersAreIsolated() = runBlocking {
        val adult = repository.ensureInitialAssignment("learner-a", LearnerAudience.ADULT, "beginner")!!
        val child = repository.ensureInitialAssignment("learner-b", LearnerAudience.CHILD, "beginner")!!
        assertEquals("adult-start-from-zero", adult.pathId)
        assertEquals("child-playful-foundations", child.pathId)
        assertTrue(repository.observeHistory("learner-a").first().all { it.learnerId == "learner-a" })
        assertTrue(repository.observeHistory("learner-b").first().all { it.learnerId == "learner-b" })
    }
    @Test fun switchingAndReturningPreservesEveryHistoricalAssignment() = runBlocking {
        repository.ensureInitialAssignment("learner-a", LearnerAudience.ADULT, "beginner")
        val original = repository.observeActive("learner-a").first()!!
        repository.switchPath("learner-a", "adult-favorite-songs")
        repository.switchPath("learner-a", original.pathId)
        val history = repository.observeHistory("learner-a").first()
        assertEquals(3, history.size)
        assertEquals(original.pathId, repository.observeActive("learner-a").first()!!.pathId)
        assertEquals(1, history.count { it.isActive })
        assertTrue(history.any { it.pathId == "adult-favorite-songs" && !it.isActive })
    }
    @Test fun levelChangePreservesProgressAndUpdatesOnlySelectedLearner() = runBlocking {
        repository.ensureInitialAssignment("learner-a", LearnerAudience.ADULT, "beginner")
        val progress = ProgressRepository(database)
        progress.saveLessonProgress(LessonProgressEntity("progress-a", "learner-a", "first-notes",
            isCompleted = true, lastAccessedAtEpochMillis = 100))
        val before = progress.observeLessonProgress("learner-a").first()
        val changed = repository.changeLevel("learner-a", "advanced")
        assertEquals("advanced", changed.levelId)
        assertEquals(2, repository.observeHistory("learner-a").first().size)
        assertEquals(before, progress.observeLessonProgress("learner-a").first())
        assertEquals("advanced", database.learnerProfileDao().findById("learner-a")!!.currentLevelId)
        assertEquals("beginner", database.learnerProfileDao().findById("learner-b")!!.currentLevelId)
    }
    @Test fun invalidPathLevelAndUnknownLearnerAreRejected() = runBlocking {
        repository.ensureInitialAssignment("learner-a", LearnerAudience.ADULT, "beginner")
        assertFalse(runCatching { repository.switchPath("learner-a", "missing-path") }.isSuccess)
        assertFalse(runCatching { repository.changeLevel("learner-a", "missing-level") }.isSuccess)
        assertFalse(runCatching { repository.switchPath("missing-learner", "adult-start-from-zero") }.isSuccess)
    }
}
