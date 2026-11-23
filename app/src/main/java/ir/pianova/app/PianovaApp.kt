package ir.pianova.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ir.pianova.app.core.designsystem.AboutDialog
import ir.pianova.app.core.designsystem.PianovaFuture
import ir.pianova.app.core.designsystem.PianovaGreen
import ir.pianova.app.core.designsystem.PianovaInk
import ir.pianova.app.core.designsystem.PianovaMuted
import ir.pianova.app.core.designsystem.PianovaSage
import ir.pianova.app.core.designsystem.PianovaWhite
import ir.pianova.app.core.midi.PianoInput
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.data.persistence.RoomFamilyRepository
import ir.pianova.app.domain.family.Family
import ir.pianova.app.domain.family.LearnerAudience
import ir.pianova.app.domain.family.LearnerProfile
import ir.pianova.app.data.lesson.AndroidLessonAssetSource
import ir.pianova.app.data.lesson.LessonRepository
import ir.pianova.app.data.lesson.SkillAttemptStore
import ir.pianova.app.domain.model.LessonAudience
import ir.pianova.app.data.persistence.LearnerSessionStore
import ir.pianova.app.data.persistence.activeLearnerOrNull
import ir.pianova.app.data.persistence.ProgressRepository
import ir.pianova.app.domain.progress.LearnerProgressSnapshot

private data class MainTab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val mainTabs = listOf(
    MainTab("home", "خانه", Icons.Filled.Home),
    MainTab("learning", "یادگیری", Icons.Filled.School),
    MainTab("practice", "تمرین", Icons.Filled.MusicNote),
    MainTab("library", "کتابخانه", Icons.Filled.LibraryMusic),
    MainTab("more", "بیشتر", Icons.Filled.MoreHoriz)
)

@Composable
fun PianovaApp(
    pianoStatus: String,
    pianoReady: Boolean,
    pianoRunning: Boolean,
    onPianoStart: () -> Unit,
    onPianoStop: () -> Unit,
    onNoteOn: (Int, Float) -> Boolean,
    onNoteOff: (Int) -> Unit,
    onSustain: (Boolean) -> Unit,
    onVolume: (Float) -> Unit,
    onPianoVisibilityChanged: (Boolean) -> Unit,
    pianoInput: PianoInput,
    onPlayReferenceNotes: (List<Int>, Long) -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { PianovaDatabase.getInstance(context) }
    val familyRepository = remember(database) { RoomFamilyRepository(database) }
    val sessionStore = remember(context) { LearnerSessionStore(context) }
    val familyPreferences = remember(context) { context.getSharedPreferences("pianova_family", 0) }
    var familyReady by remember { mutableStateOf(false) }
    var familyId by remember { mutableStateOf("local-family") }
    var onboardingComplete by remember { mutableStateOf(familyPreferences.getBoolean("onboarding_complete", false)) }
    var activeLearnerId by remember { mutableStateOf<String?>(null) }
    var managerOpen by remember { mutableStateOf(false) }
    val learners by familyRepository.observeLearners(familyId).collectAsState(initial = emptyList())
    val validActiveLearnerId = activeLearnerOrNull(activeLearnerId, learners.map { it.learnerId }.toSet())
    val activeLearner = learners.firstOrNull { it.learnerId == validActiveLearnerId }

    LaunchedEffect(database) {
        val migratedFamily = database.familyDao().findById("family-default")
        val existingFamily = database.familyDao().findById("local-family")
        val selectedFamily = migratedFamily ?: existingFamily
        if (selectedFamily == null) {
            val now = System.currentTimeMillis()
            familyRepository.saveFamily(Family("local-family", now, now))
            familyId = "local-family"
        } else {
            familyId = selectedFamily.familyId
        }
        onboardingComplete = familyPreferences.getBoolean("onboarding_complete", false)
        familyReady = true
    }

    LaunchedEffect(familyReady, managerOpen, activeLearner?.learnerId) {
        if (!familyReady || managerOpen || activeLearner == null) {
            onPianoVisibilityChanged(false)
            onPianoStop()
        }
    }

    // If a profile is removed while another screen is open, discard its active
    // context immediately and return to the chooser. The manager is not a learner.
    LaunchedEffect(learners, activeLearnerId) {
        if (activeLearnerId != null && activeLearner == null) {
            sessionStore.forgetActiveLearner(activeLearnerId!!)
            activeLearnerId = null
        }
    }

    when {
        !familyReady -> {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("در حال آماده‌سازی خانواده…", color = PianovaMuted)
            }
        }
        shouldShowFamilySetup(onboardingComplete) -> {
            FamilyManagementScreen(
                repository = familyRepository,
                familyId = familyId,
                initialSetup = true,
                onComplete = {
                    familyPreferences.edit().putBoolean("onboarding_complete", true).commit()
                    onboardingComplete = true
                }
            )
        }
        managerOpen -> {
            FamilyManagementScreen(
                repository = familyRepository,
                familyId = familyId,
                initialSetup = false,
                onComplete = { managerOpen = false }
            )
        }
        activeLearner == null -> {
            LearnerSelectionScreen(
                learners = learners,
                lastActiveLearnerId = sessionStore.lastActiveLearnerId()?.takeIf { id -> learners.any { it.learnerId == id } },
                destinationLabel = { id -> destinationLabel(sessionStore.destinationFor(id)) },
                onSelectLearner = { learner ->
                    SkillAttemptStore.migrateLegacyAttempts(context, learners.map { it.learnerId })
                    sessionStore.ensureAudienceFor(learner.learnerId, defaultLessonAudience(learner.audience))
                    sessionStore.rememberActiveLearner(learner.learnerId)
                    activeLearnerId = learner.learnerId
                },
                onManageFamily = { managerOpen = true }
            )
        }
        else -> {
            key(activeLearner.learnerId) {
                LearnerWorkspace(
                    learner = activeLearner,
                    familyId = familyId,
                    familyRepository = familyRepository,
                    sessionStore = sessionStore,
                    pianoStatus = pianoStatus,
                    pianoReady = pianoReady,
                    pianoRunning = pianoRunning,
                    onPianoStart = onPianoStart,
                    onPianoStop = onPianoStop,
                    onNoteOn = onNoteOn,
                    onNoteOff = onNoteOff,
                    onSustain = onSustain,
                    onVolume = onVolume,
                    onPianoVisibilityChanged = onPianoVisibilityChanged,
                    pianoInput = pianoInput,
                    onPlayReferenceNotes = onPlayReferenceNotes,
                    onChangeLearner = {
                        onPianoVisibilityChanged(false)
                        onPianoStop()
                        activeLearnerId = null
                    }
                )
            }
        }
    }
}

@Composable
private fun LearnerWorkspace(
    learner: LearnerProfile,
    familyId: String,
    familyRepository: RoomFamilyRepository,
    sessionStore: LearnerSessionStore,
    pianoStatus: String,
    pianoReady: Boolean,
    pianoRunning: Boolean,
    onPianoStart: () -> Unit,
    onPianoStop: () -> Unit,
    onNoteOn: (Int, Float) -> Boolean,
    onNoteOff: (Int) -> Unit,
    onSustain: (Boolean) -> Unit,
    onVolume: (Float) -> Unit,
    onPianoVisibilityChanged: (Boolean) -> Unit,
    pianoInput: PianoInput,
    onPlayReferenceNotes: (List<Int>, Long) -> Unit,
    onChangeLearner: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val lessonRepository = remember(context) { LessonRepository(AndroidLessonAssetSource(context.assets)) }
    val defaultAudience = defaultLessonAudience(learner.audience)
    var lessonAudience by remember(learner.learnerId) {
        mutableStateOf(sessionStore.audienceFor(learner.learnerId, defaultAudience))
    }
    var showAboutDialog by remember { mutableStateOf(false) }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val startDestination = remember(learner.learnerId) { sessionStore.destinationFor(learner.learnerId) }

    LaunchedEffect(route, learner.learnerId) {
        sessionStore.saveDestination(learner.learnerId, route)
        onPianoVisibilityChanged(route == "practice")
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (route != "practice" && !route.startsWith("lesson/")) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    mainTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = if (tab.route == "more") route in setOf("more", "progress", "settings", "family", "paths") else route == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(learner.avatarRef ?: "🎹", style = MaterialTheme.typography.titleLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(learner.displayName, color = PianovaInk, fontWeight = FontWeight.SemiBold)
                        Text(audienceLabel(learner.audience), color = PianovaMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(onClick = onChangeLearner) { Text("تغییر هنرجو") }
            }
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.weight(1f)
            ) {
                composable("home") {
                    HomeScreen(
                        onContinue = { navController.navigate("learning") },
                        onPractice = { navController.navigate("practice") },
                        onLibrary = { navController.navigate("library") },
                        onProgress = { navController.navigate("progress") },
                        onLearningPaths = { navController.navigate("paths") }
                    )
                }
                composable("learning") {
                    LessonListScreen(
                        repository = lessonRepository,
                        audience = lessonAudience,
                        onAudienceChange = { selected ->
                            lessonAudience = selected
                            sessionStore.saveAudience(learner.learnerId, selected)
                        },
                        onStartChapter = { lessonId, chapterId ->
                            navController.navigate("lesson/$lessonId/$chapterId")
                        }
                    )
                }
                composable(
                    route = "lesson/{lessonId}/{chapterId}",
                    arguments = listOf(
                        navArgument("lessonId") { type = NavType.StringType },
                        navArgument("chapterId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val lessonId = backStackEntry.arguments?.getString("lessonId").orEmpty()
                    val chapterId = backStackEntry.arguments?.getString("chapterId").orEmpty()
                    val lesson = remember(lessonId) { runCatching { lessonRepository.getLesson(lessonId) }.getOrNull() }
                    if (lesson == null) {
                        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("بارگذاری درس ناموفق بود.", color = PianovaInk)
                            TextButton(onClick = { navController.popBackStack() }) { Text("بازگشت") }
                        }
                    } else {
                        LessonPlayerScreen(
                            learnerId = learner.learnerId,
                            lesson = lesson,
                            audience = lessonAudience,
                            startChapterId = chapterId,
                            pianoInput = pianoInput,
                            pianoReady = pianoReady,
                            pianoRunning = pianoRunning,
                            onPianoStart = onPianoStart,
                            onNoteOn = onNoteOn,
                            onNoteOff = onNoteOff,
                            onPlayReferenceNotes = onPlayReferenceNotes,
                            onExit = { navController.popBackStack() }
                        )
                    }
                }
                composable("practice") {
                    PianoScreen(
                        learnerId = learner.learnerId,
                        initialVolume = sessionStore.volumeFor(learner.learnerId),
                        initialLabelMode = sessionStore.labelModeFor(learner.learnerId),
                        initialFirstMidi = sessionStore.firstMidiFor(learner.learnerId),
                        onLearnerPreferenceChanged = { volume, labelMode, firstMidi ->
                            sessionStore.saveVolume(learner.learnerId, volume)
                            sessionStore.saveLabelMode(learner.learnerId, labelMode)
                            sessionStore.saveFirstMidi(learner.learnerId, firstMidi)
                        },
                        status = pianoStatus,
                        ready = pianoReady,
                        running = pianoRunning,
                        onStart = onPianoStart,
                        onStop = onPianoStop,
                        onNoteOn = onNoteOn,
                        onNoteOff = onNoteOff,
                        onSustain = onSustain,
                        onVolume = onVolume
                    )
                }
                composable("paths") { LearningPathScreen(learner) }
                composable("library") { LibraryScreen(learner.learnerId) }
                composable("more") { MoreScreen(navController, onAboutClick = { showAboutDialog = true }) }
                composable("progress") { ProgressScreen(learner.learnerId) }
                composable("settings") { SettingsScreen(onAboutClick = { showAboutDialog = true }) }
                composable("family") {
                    FamilyManagementScreen(
                        repository = familyRepository,
                        familyId = familyId,
                        initialSetup = false,
                        onComplete = { navController.popBackStack() }
                    )
                }
            }
        }
    }
    if (showAboutDialog) AboutDialog(onDismiss = { showAboutDialog = false })
}

private fun defaultLessonAudience(audience: LearnerAudience): LessonAudience = when (audience) {
    LearnerAudience.CHILD -> LessonAudience.CHILDREN
    LearnerAudience.TEEN -> LessonAudience.TEENS
    LearnerAudience.ADULT -> LessonAudience.ADULTS
    LearnerAudience.UNSPECIFIED -> LessonAudience.CHILDREN
}

private fun audienceLabel(audience: LearnerAudience): String = when (audience) {
    LearnerAudience.CHILD -> "کودک"
    LearnerAudience.TEEN -> "نوجوان"
    LearnerAudience.ADULT -> "بزرگسال"
    LearnerAudience.UNSPECIFIED -> "هنرجو"
}

private fun destinationLabel(route: String): String = when (route) {
    "home" -> "خانه"
    "learning" -> "مسیر یادگیری"
    "paths" -> "مسیرهای آموزشی"
    "practice" -> "تمرین"
    "library" -> "کتابخانه"
    "progress" -> "پیشرفت"
    "more" -> "بیشتر"
    "settings" -> "تنظیمات"
    else -> "مسیر یادگیری"
}

@Composable
private fun PageColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = PianovaInk, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = PianovaMuted)
    }
}

@Composable
private fun JourneyCard(title: String, detail: String, action: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PianovaSage)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = PianovaInk, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = PianovaGreen)
            Text(action, style = MaterialTheme.typography.labelLarge, color = PianovaGreen)
        }
    }
}

@Composable
fun HomeScreen(
    onContinue: () -> Unit,
    onPractice: () -> Unit,
    onLibrary: () -> Unit,
    onProgress: () -> Unit,
    onLearningPaths: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PageTitle("از همین‌جا شروع کن", "هر مهارت، یک قدم تا اجرای اولین قطعه")
        }
        item {
            JourneyCard(
                "مسیر یادگیری من",
                "قدم اول را بردار و آموزش پیانو را از مبانی آغاز کن.",
                "مشاهده مسیر یادگیری  ←",
                onContinue
            )
        }
        item {
            Text("یادگیری و اجرا", style = MaterialTheme.typography.titleMedium, color = PianovaInk, fontWeight = FontWeight.Bold)
        }
        item { ActionCard("انتخاب مسیر و سطح", "مسیر مناسب را انتخاب کنید و هر زمان تغییر دهید؛ سابقه حفظ می‌شود.", "مدیریت مسیر آموزشی", onLearningPaths) }
        item { ActionCard("پیانو آزاد", "نواختن با صدای پیانوی Classic Grand", "شروع نواختن", onPractice) }
        item { ActionCard("کتابخانه آهنگ‌ها", "قطعات آموزشی و آهنگ‌های مورد علاقه", "مشاهده کتابخانه", onLibrary) }
        item { ActionCard("پیشرفت من", "گزارش تمرین‌ها و مهارت‌های آموخته‌شده", "مشاهده پیشرفت", onProgress) }
        item {
            Text(
                "قابلیت‌های جدید به‌تدریج به این مسیر اضافه می‌شوند.",
                style = MaterialTheme.typography.bodySmall,
                color = PianovaMuted,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ActionCard(title: String, detail: String, action: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PianovaWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = PianovaInk, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = PianovaMuted)
                Text(action, style = MaterialTheme.typography.labelLarge, color = PianovaGreen)
            }
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = PianovaGreen, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun LearningScreen(onPractice: () -> Unit) {
    PageColumn {
        PageTitle("مسیر یادگیری من", "از شناخت کلاویه‌ها تا اجرای اولین قطعه")
        JourneyCard("مرحله ۱ · آشنایی با پیانو", "با الگوی کلاویه‌های سفید و سیاه و جای نت دو آشنا شو.", "شروع تمرین پیانو  ←", onPractice)
        listOf("مرحله ۲ · ریتم و زمان‌بندی", "مرحله ۳ · نت‌خوانی پایه", "مرحله ۴ · ملودی‌های ساده", "مرحله ۵ · آکوردهای ابتدایی").forEach { title ->
            StatusCard(title, "در نقشه راه", planned = true)
        }
    }
}

@Composable
private fun LibraryScreen(learnerId: String) {
    val context = LocalContext.current
    val database = remember(context) { PianovaDatabase.getInstance(context) }
    val progressRepository = remember(database) { ProgressRepository(database) }
    val songProgress by progressRepository.observeSongProgress(learnerId).collectAsState(initial = emptyList())

    PageColumn {
        PageTitle("کتابخانه آهنگ‌ها", "قطعات آموزشی و آهنگ‌های شخصی")
        if (songProgress.isEmpty()) {
            EmptyState("کتابخانه در حال آماده‌سازی است", "هنوز پیشرفت آهنگی برای این هنرجو ثبت نشده است. قطعات مبتدی ایرانی و بین‌المللی در مراحل بعدی اضافه می‌شوند.")
        } else {
            StatusCard("آهنگ‌های دارای سابقه تمرین", songProgress.size.toString())
            songProgress.forEach { song ->
                StatusCard(song.songId, "${song.playCount} بار تمرین")
            }
        }
    }
}

@Composable
private fun ProgressScreen(learnerId: String) {
    val context = LocalContext.current
    val database = remember(context) { PianovaDatabase.getInstance(context) }
    val progressRepository = remember(database) { ProgressRepository(database) }
    val lessonProgress by progressRepository.observeLessonProgress(learnerId).collectAsState(initial = emptyList())
    val practiceSessions by progressRepository.observePracticeSessions(learnerId).collectAsState(initial = emptyList())
    val achievements by progressRepository.observeAchievements(learnerId).collectAsState(initial = emptyList())
    val learnerProgress by progressRepository.observeLearnerProgress(learnerId).collectAsState(
        initial = LearnerProgressSnapshot(learnerId, 0L, 0, 0, 0, null, emptyList(), emptyList(), emptyList())
    )

    PageColumn {
        PageTitle("پیشرفت من", "مسیر تمرین و مهارت‌هایت را اینجا دنبال می‌کنی")
        if (lessonProgress.isEmpty() && practiceSessions.isEmpty() && achievements.isEmpty() &&
            learnerProgress.evaluationCount == 0
        ) {
            EmptyState("هنوز گزارشی ثبت نشده است", "با شروع درس‌ها و تمرین‌ها، اطلاعات واقعی همین هنرجو در این صفحه نمایش داده می‌شود.")
        } else {
            run { val progress = learnerProgress
                StatusCard("درس‌های تکمیل‌شده", progress.completedLessonCount.toString())
                StatusCard("زمان تمرین فعال", "${progress.practiceMillis / 60_000} دقیقه")
                StatusCard("جلسه‌های تمرین", progress.sessionCount.toString())
                StatusCard(
                    "دقت ارزیابی",
                    progress.accuracy?.let { "${(it * 100).toInt()}٪" } ?: "هنوز ارزیابی کافی ثبت نشده"
                )
                StatusCard("تعداد ارزیابی‌ها", progress.evaluationCount.toString())
                Text("مهارت‌ها", style = MaterialTheme.typography.titleMedium, color = PianovaInk)
                if (progress.skills.isEmpty()) {
                    Text("برای مهارت‌ها هنوز شواهدی ثبت نشده است.", color = PianovaMuted)
                } else {
                    progress.skills.forEach { skill ->
                        StatusCard(
                            skill.skillId,
                            if (skill.isMastered) "تسلط تأییدشده" else
                                "${skill.successfulAttempts} موفق از ${skill.attempts} ارزیابی"
                        )
                    }
                }
                Text("روند هفتگی", style = MaterialTheme.typography.titleMedium, color = PianovaInk)
                progress.weekly.takeLast(4).forEach { period ->
                    StatusCard(
                        java.time.LocalDate.ofEpochDay(period.periodStartEpochDay).toString(),
                        "${period.practiceMillis / 60_000} دقیقه تمرین · ${period.sessionCount} جلسه"
                    )
                }
            }
            StatusCard("دستاوردها", achievements.size.toString())
        }
    }
}

@Composable
private fun SettingsScreen(onAboutClick: () -> Unit) {
    PageColumn {
        PageTitle("تنظیمات", "تنظیم تجربه یادگیری و صدای پیانو")
        StatusCard("صدای پیانو و خروجی صوتی", "آماده")
        StatusCard("زبان و شیوه نمایش نت‌ها", "آماده")
        StatusCard("تم و نمایش", "آماده")
        TextButton(onClick = onAboutClick) { Text("درباره Pianova و نسخه نصب‌شده") }
        StatusCard("پشتیبان‌گیری از پیشرفت", "برنامه‌ریزی‌شده", planned = true)
    }
}

private data class MenuEntry(val title: String, val status: String, val route: String? = null, val future: Boolean = false)

@Composable
private fun MoreScreen(navController: NavHostController, onAboutClick: () -> Unit) {
    val groups = listOf(
        "یادگیری و تمرین" to listOf(
            MenuEntry("خانه و داشبورد", "آماده", "home"),
            MenuEntry("مسیر یادگیری", "آماده", "learning"),
            MenuEntry("انتخاب مسیر و سطح", "آماده", "paths"),
            MenuEntry("درس‌ها و دوره‌ها", "در حال توسعه", "learning"),
            MenuEntry("تمرین روزانه", "در حال توسعه", "learning"),
            MenuEntry("پیانو آزاد", "آماده", "practice")
        ),
        "کتابخانه و پیشرفت" to listOf(
            MenuEntry("کتابخانه آهنگ‌ها", "در حال توسعه", "library"),
            MenuEntry("پیشرفت و دستاوردها", "در حال توسعه", "progress")
        ),
        "ابزارهای آموزشی" to listOf(
            MenuEntry("مترونوم و ریتم", "برنامه‌ریزی‌شده", future = true),
            MenuEntry("نت‌خوانی و تئوری موسیقی", "برنامه‌ریزی‌شده", future = true),
            MenuEntry("گوش‌پروری", "برنامه‌ریزی‌شده", future = true)
        ),
        "امکانات توسعه" to listOf(
            MenuEntry("ضبط و بازپخش اجرا", "آینده", future = true),
            MenuEntry("اتصال MIDI", "آینده", future = true),
            MenuEntry("مربی هوشمند", "آینده", future = true)
        ),
        "حساب و برنامه" to listOf(
            MenuEntry("مدیریت خانواده و هنرجویان", "آماده", "family"),
            MenuEntry("تنظیمات", "آماده", "settings")
        )
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { PageTitle("همه امکانات Pianova", "قابلیت‌های فعلی و مسیر توسعه در یک نگاه") }
        groups.forEach { (group, entries) ->
            item {
                Text(group, style = MaterialTheme.typography.titleMedium, color = PianovaInk, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }
            items(entries) { entry ->
                StatusCard(entry.title, entry.status, planned = entry.future, onClick = {
                    entry.route?.let { navController.navigate(it) { launchSingleTop = true } }
                })
            }
        }
        item { TextButton(onClick = onAboutClick) { Text("درباره Pianova و اطلاعات نسخه") } }
        item {
            Text(
                "وضعیت‌ها: آماده، در حال توسعه، برنامه‌ریزی‌شده و آینده",
                style = MaterialTheme.typography.bodySmall,
                color = PianovaMuted,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    status: String,
    planned: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val colors = CardDefaults.cardColors(containerColor = if (planned) PianovaFuture else PianovaWhite)
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = colors
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (planned) PianovaMuted else PianovaInk)
            Text(status, style = MaterialTheme.typography.labelSmall, color = PianovaGreen)
        }
    }
}

@Composable
private fun EmptyState(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PianovaWhite)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = PianovaInk, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = PianovaMuted, textAlign = TextAlign.Center)
        }
    }
}
