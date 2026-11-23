package ir.pianova.app.data.persistence
import androidx.room.withTransaction
import ir.pianova.app.core.database.LearningPathAssignmentEntity
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.domain.family.LearnerAudience
import ir.pianova.app.domain.learning.LearningPathCatalog
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** Local, learner-scoped path selection and immutable activation history. */
class LearningPathRepository(private val database: PianovaDatabase) {
    private val assignments = database.learningPathAssignmentDao()
    fun observeActive(learnerId: String): Flow<LearningPathAssignmentEntity?> = assignments.observeActive(learnerId)
    fun observeHistory(learnerId: String): Flow<List<LearningPathAssignmentEntity>> = assignments.observeHistory(learnerId)
    suspend fun ensureInitialAssignment(learnerId: String, audience: LearnerAudience, currentLevelId: String): LearningPathAssignmentEntity? =
        database.withTransaction {
            require(learnerId.isNotBlank())
            assignments.findActive(learnerId)?.let { return@withTransaction it }
            val recommended = LearningPathCatalog.recommendedPathFor(audience) ?: return@withTransaction null
            val levelId = LearningPathCatalog.level(currentLevelId)?.id ?: "beginner"
            createAssignment(learnerId, recommended.id, levelId, System.currentTimeMillis())
        }
    suspend fun switchPath(learnerId: String, pathId: String, levelId: String? = null): LearningPathAssignmentEntity =
        database.withTransaction {
            require(learnerId.isNotBlank())
            require(LearningPathCatalog.path(pathId) != null) { "Unknown learning path" }
            require(database.learnerProfileDao().findById(learnerId) != null) { "Learner does not exist" }
            val current = assignments.findActive(learnerId)
            val selectedLevel = levelId ?: current?.levelId ?: database.learnerProfileDao().findById(learnerId)?.currentLevelId ?: "beginner"
            require(LearningPathCatalog.level(selectedLevel) != null) { "Unknown learning level" }
            if (current != null && current.pathId == pathId && current.levelId == selectedLevel) return@withTransaction current
            createAssignment(learnerId, pathId, selectedLevel, System.currentTimeMillis())
        }
    suspend fun changeLevel(learnerId: String, levelId: String): LearningPathAssignmentEntity =
        database.withTransaction {
            require(LearningPathCatalog.level(levelId) != null) { "Unknown learning level" }
            val current = assignments.findActive(learnerId) ?: throw IllegalStateException("Select a learning path before changing level")
            if (current.levelId == levelId) return@withTransaction current
            createAssignment(learnerId, current.pathId, levelId, System.currentTimeMillis())
        }
    private suspend fun createAssignment(learnerId: String, pathId: String, levelId: String, now: Long): LearningPathAssignmentEntity {
        require(database.learnerProfileDao().findById(learnerId) != null) { "Learner does not exist" }
        assignments.closeActiveForLearner(learnerId, now)
        val assignment = LearningPathAssignmentEntity(UUID.randomUUID().toString(), learnerId, pathId, levelId, now, null, true)
        assignments.insert(assignment)
        database.learnerProfileDao().updateCurrentLevel(learnerId, levelId, now)
        return assignment
    }
}
