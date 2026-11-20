package ir.pianova.app.data.lesson

import android.content.res.AssetManager
import ir.pianova.app.domain.model.Lesson
import java.io.IOException

interface LessonAssetSource {
    fun list(path: String): List<String>
    fun read(path: String): String
}

class AndroidLessonAssetSource(
    private val assets: AssetManager
) : LessonAssetSource {
    override fun list(path: String): List<String> =
        assets.list(path)?.toList().orEmpty()

    override fun read(path: String): String =
        assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}

class LessonRepository(
    private val source: LessonAssetSource,
    private val parser: JsonLessonParser = JsonLessonParser(),
    private val assetsDirectory: String = DEFAULT_ASSETS_DIRECTORY
) {
    fun listLessonIds(): List<String> =
        source.list(assetsDirectory)
            .asSequence()
            .filter { it.endsWith(".json", ignoreCase = true) }
            .map { it.substringBeforeLast('.') }
            .filter(::isSafeLessonId)
            .sorted()
            .toList()

    fun getLesson(id: String): Lesson {
        require(isSafeLessonId(id)) { "Invalid lesson id" }
        val path = "$assetsDirectory/$id.json"
        val content = try {
            source.read(path)
        } catch (error: IOException) {
            throw LessonFormatException("Lesson asset not found: $id", error)
        }
        val lesson = parser.parse(content)
        require(lesson.id == id) {
            "Lesson id in JSON does not match asset filename"
        }
        return lesson
    }

    companion object {
        const val DEFAULT_ASSETS_DIRECTORY = "lessons"

        private fun isSafeLessonId(id: String): Boolean =
            id.matches(Regex("[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}"))
    }
}
