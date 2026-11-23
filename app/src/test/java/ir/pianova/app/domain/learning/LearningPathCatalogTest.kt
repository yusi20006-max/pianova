package ir.pianova.app.domain.learning
import ir.pianova.app.domain.family.LearnerAudience
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningPathCatalogTest {
    @Test fun adultHasFourDistinctFlexiblePathsAndAgeNeverRemovesOptions() {
        val adult = LearningPathCatalog.pathsFor(LearnerAudience.ADULT)
        assertEquals(4, adult.count { it.id.startsWith("adult-") })
        assertEquals(LearningPathCatalog.paths.size, adult.size)
        assertEquals("adult-start-from-zero", LearningPathCatalog.recommendedPathFor(LearnerAudience.ADULT)?.id)
        assertEquals(LearningPathCatalog.paths.size, LearningPathCatalog.pathsFor(LearnerAudience.CHILD).size)
        assertEquals(LearningPathCatalog.paths.size, LearningPathCatalog.pathsFor(LearnerAudience.TEEN).size)
    }
    @Test fun levelsHaveObjectivesAndOrderedPrerequisites() {
        assertEquals(listOf("beginner", "elementary", "intermediate", "advanced"), LearningPathCatalog.levels.map { it.id })
        assertTrue(LearningPathCatalog.levels.all { it.objective.isNotBlank() && it.prerequisiteDescription.isNotBlank() })
        assertTrue(LearningPathCatalog.level("beginner")!!.prerequisiteLevelIds.isEmpty())
        assertEquals(listOf("beginner"), LearningPathCatalog.level("elementary")!!.prerequisiteLevelIds)
        assertEquals(listOf("elementary"), LearningPathCatalog.level("intermediate")!!.prerequisiteLevelIds)
        assertEquals(listOf("intermediate"), LearningPathCatalog.level("advanced")!!.prerequisiteLevelIds)
    }
    @Test fun prerequisiteReadinessIsAdvisoryAndCanBeEvaluated() {
        val elementary = LearningPathCatalog.level("elementary")!!
        assertFalse(LearningPathCatalog.prerequisitesSatisfied(elementary.id, emptySet()))
        assertTrue(LearningPathCatalog.prerequisitesSatisfied(elementary.id, elementary.prerequisiteSkillIds.toSet()))
        assertTrue(LearningPathCatalog.prerequisitesSatisfied("beginner", emptySet()))
        assertFalse(LearningPathCatalog.prerequisitesSatisfied("unknown", emptySet()))
    }
    @Test fun lessonDiscoveryFiltersAudienceLevelSkillAndPrerequisitesWithoutDuplicatingLessons() {
        assertEquals(5, LessonCatalogIndex.discover(LearnerAudience.CHILD, masteredSkillIds = setOf("identify-white-keys", "identify-black-keys", "find-middle-c", "play-do-re-mi", "play-fa-sol-la-si")).size)
        val mastered = setOf("identify-white-keys", "identify-black-keys", "find-middle-c", "play-do-re-mi", "play-fa-sol-la-si")
        val childIds = LessonCatalogIndex.discover(LearnerAudience.CHILD, masteredSkillIds = mastered).map { it.lessonId }
        assertEquals(childIds, LessonCatalogIndex.discover(LearnerAudience.TEEN, masteredSkillIds = mastered).map { it.lessonId })
        assertEquals(childIds, LessonCatalogIndex.discover(LearnerAudience.ADULT, masteredSkillIds = mastered).map { it.lessonId })
        assertEquals(5, LessonCatalogIndex.entries.map { it.lessonId }.distinct().size)
        assertTrue(LessonCatalogIndex.discover(LearnerAudience.TEEN, skillId = "find-middle-c").isEmpty())
        assertEquals("lesson-02-middle-c", LessonCatalogIndex.discover(
            LearnerAudience.TEEN, "beginner", "find-middle-c",
            setOf("identify-white-keys", "identify-black-keys")
        ).single().lessonId)
        assertTrue(LessonCatalogIndex.discover(LearnerAudience.ADULT, skillId = "play-do-re-mi").isEmpty())
    }
}
