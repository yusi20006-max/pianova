package ir.pianova.app.domain.learning
import ir.pianova.app.domain.family.LearnerAudience

data class LearningLevel(
    val id: String, val title: String, val objective: String,
    val prerequisiteLevelIds: List<String>, val prerequisiteSkillIds: List<String>,
    val prerequisiteDescription: String
)
data class LearningPath(
    val id: String, val title: String, val description: String, val focus: String,
    val recommendedFor: Set<LearnerAudience>
)
object LearningPathCatalog {
    val levels = listOf(
        LearningLevel("beginner", "مبتدی", "شناخت صفحه‌کلید، پیدا کردن دو میانی و اجرای الگوهای کوتاه نت‌ها", emptyList(), emptyList(), "پیش‌نیاز ندارد؛ مناسب شروع از صفر"),
        LearningLevel("elementary", "مقدماتی", "گسترش نت‌خوانی، شناخت کشش‌ها و اجرای قطعات ساده", listOf("beginner"), listOf("identify-white-keys", "identify-black-keys", "find-middle-c"), "آشنایی با الگوی کلاویه‌ها و جای دو میانی"),
        LearningLevel("intermediate", "متوسط", "نت‌خوانی روان‌تر، هماهنگی دو دست، ریتم و مبانی گام و آکورد", listOf("elementary"), listOf("staff-reading-basics", "basic-rhythm"), "خواندن نت‌های پایه و اجرای ریتم‌های ساده"),
        LearningLevel("advanced", "پیشرفته", "اجرای مستقل رپرتوار، کنترل تکنیک و تفسیر موسیقایی", listOf("intermediate"), listOf("independent-note-reading", "repertoire-foundations"), "نت‌خوانی مستقل و تسلط بر مبانی رپرتوار")
    )
    val paths = listOf(
        LearningPath("child-playful-foundations", "مسیر کودک", "گام‌های کوتاه، بازی‌محور و اکتشافی برای آشنایی تدریجی با پیانو", "شنیدن، کشف صفحه‌کلید، بازی‌های کوتاه و تمرین‌های متنوع", setOf(LearnerAudience.CHILD)),
        LearningPath("teen-structured-foundations", "مسیر نوجوان", "مسیر ساختاریافته با تأکید بر نت‌خوانی، مهارت و اجرای قطعه", "نت‌خوانی تدریجی، تمرین هدفمند و قطعات متناسب با سطح", setOf(LearnerAudience.TEEN)),
        LearningPath("adult-start-from-zero", "شروع از صفر", "شروع مرحله‌به‌مرحله برای بزرگسالانی که تجربه قبلی پیانو ندارند", "مبانی صفحه‌کلید، نت‌خوانی، ریتم و اجرای نخستین قطعات", setOf(LearnerAudience.ADULT)),
        LearningPath("adult-classical-foundations", "مبانی کلاسیک", "مسیر منظم برای ساختن پایه‌های کلاسیک نوازندگی پیانو", "تکنیک، نت‌خوانی، ریتم، گام‌ها و رپرتوار کلاسیک", setOf(LearnerAudience.ADULT)),
        LearningPath("adult-favorite-songs", "یادگیری آهنگ‌های محبوب", "یادگیری با قطعات مورد علاقه، از موسیقی ایرانی تا بین‌المللی", "انتخاب قطعه، تقسیم آن به بخش‌های قابل تمرین و توسعه مهارت در بستر آهنگ", setOf(LearnerAudience.ADULT)),
        LearningPath("adult-return-to-piano", "بازگشت به پیانو", "مرور مهارت‌های قبلی و بازسازی تدریجی آمادگی نوازندگی", "مرور نت‌خوانی و تکنیک، تشخیص نیازهای تمرینی و بازگشت به رپرتوار", setOf(LearnerAudience.ADULT))
    )
    fun level(id: String): LearningLevel? = levels.firstOrNull { it.id == id }
    fun path(id: String): LearningPath? = paths.firstOrNull { it.id == id }
    /** Every path remains selectable; audience only changes recommendation ordering. */
    fun pathsFor(audience: LearnerAudience): List<LearningPath> = paths.sortedByDescending { audience in it.recommendedFor }
    fun recommendedPathFor(audience: LearnerAudience): LearningPath? = paths.firstOrNull { audience in it.recommendedFor }
    fun prerequisitesSatisfied(levelId: String, masteredSkillIds: Set<String>): Boolean {
        val selected = level(levelId) ?: return false
        return selected.prerequisiteSkillIds.all { it in masteredSkillIds }
    }
}
data class LessonCatalogDescriptor(
    val lessonId: String, val levelId: String, val skillIds: List<String>,
    val prerequisiteSkillIds: List<String>, val recommendedFor: Set<LearnerAudience>
)
/** Metadata references existing lesson assets; lesson copy remains in one JSON source. */
object LessonCatalogIndex {
    private val allAudiences = setOf(LearnerAudience.CHILD, LearnerAudience.TEEN, LearnerAudience.ADULT)
    val entries = listOf(
        LessonCatalogDescriptor("first-notes", "beginner", listOf("identify-white-keys", "identify-black-keys"), emptyList(), allAudiences),
        LessonCatalogDescriptor("lesson-02-middle-c", "beginner", listOf("find-middle-c"), listOf("identify-white-keys", "identify-black-keys"), allAudiences),
        LessonCatalogDescriptor("lesson-03-do-re-mi", "beginner", listOf("play-do-re-mi"), listOf("find-middle-c"), allAudiences),
        LessonCatalogDescriptor("lesson-04-fa-sol-la-si", "beginner", listOf("play-fa-sol-la-si"), listOf("play-do-re-mi"), allAudiences),
        LessonCatalogDescriptor("lesson-05-first-melody", "beginner", listOf("play-first-seven-note-melody"), listOf("play-fa-sol-la-si"), allAudiences)
    )
    fun discover(audience: LearnerAudience, levelId: String? = null, skillId: String? = null, masteredSkillIds: Set<String> = emptySet()): List<LessonCatalogDescriptor> =
        entries.filter { (levelId == null || it.levelId == levelId) &&
            (skillId == null || it.skillIds.contains(skillId)) && it.prerequisiteSkillIds.all { skill -> masteredSkillIds.contains(skill) } }
            .sortedByDescending { audience in it.recommendedFor }
}
