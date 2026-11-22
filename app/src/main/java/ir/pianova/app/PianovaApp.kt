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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
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
import ir.pianova.app.data.lesson.AndroidLessonAssetSource
import ir.pianova.app.data.lesson.LessonRepository
import ir.pianova.app.domain.model.LessonAudience

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import ir.pianova.app.data.lesson.SkillAttemptStore
import ir.pianova.app.data.persistence.ProgressRepository
import ir.pianova.app.domain.family.LearnerProfile
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

private data class MainTab(val route: String, val label: String, val icon: String)

private val mainTabs = listOf(
    MainTab("home", "خانه", "⌂"),
    MainTab("learning", "یادگیری", "♫"),
    MainTab("practice", "تمرین", "♬"),
    MainTab("progress", "پیشرفت", "◷"),
    MainTab("more", "بیشتر", "•••")
)

@Composable
fun PianovaApp(onPianoVisibilityChanged: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val database = remember(context) { PianovaDatabase.getInstance(context) }
    val familyRepository = remember(database) { RoomFamilyRepository(database) }
    val progressRepository = remember(database) { ProgressRepository(database) }
    val lessonRepository = remember(context) { LessonRepository(AndroidLessonAssetSource(context.assets)) }
    val familyPreferences = remember(context) { context.getSharedPreferences("pianova_preferences", Context.MODE_PRIVATE) }
    val audiencePreferences = familyPreferences

    var familyId by remember { mutableStateOf("local-family") }
    var familyReady by remember { mutableStateOf(false) }
    var familyLearners by remember { mutableStateOf<List<LearnerProfile>?>(null) }
    var onboardingComplete by remember(familyPreferences) {
        mutableStateOf(familyPreferences.getBoolean("family_setup_complete", false))
    }
    var activeLearnerId by remember { mutableStateOf<String?>(null) }
    // Not saveable: each fresh app process deliberately starts at the chooser.
    var showLearnerChooser by remember { mutableStateOf(true) }
    var showFamilyManagerFromChooser by remember { mutableStateOf(false) }
    var initialRoute by remember { mutableStateOf("home") }
    var lessonAudience by remember(activeLearnerId, audiencePreferences) {
        val stored = activeLearnerId?.let {
            audiencePreferences.getString(learnerAudiencePreferenceKey(it), null)
        } ?: audiencePreferences.getString("lesson_audience", "children")
        mutableStateOf(LessonAudience.fromStorageValue(stored))
    }
    var showAboutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(database) {
        val migratedFamily = familyRepository.observeFamily("family-default").first()
        val existingFamily = familyRepository.observeFamily("local-family").first()
        val family = migratedFamily ?: existingFamily
        if (family == null) {
            val now = System.currentTimeMillis()
            familyRepository.saveFamily(Family("local-family", now, now))
            familyId = "local-family"
        } else {
            familyId = family.familyId
        }
        familyReady = true
    }

    LaunchedEffect(familyReady, familyId) {
        if (familyReady) {
            familyRepository.observeLearners(familyId).collect { familyLearners = it }
        }
    }

    LaunchedEffect(familyLearners) {
        val learners = familyLearners ?: return@LaunchedEffect
        SkillAttemptStore.migrateLegacyAttempts(context, learners.map { it.learnerId })
        val savedLearnerId = familyPreferences.getString(ACTIVE_LEARNER_ID_KEY, null)
        if (savedLearnerId != null && learners.none { it.learnerId == savedLearnerId }) {
            familyPreferences.edit().remove(ACTIVE_LEARNER_ID_KEY).apply()
        }
    }

    LaunchedEffect(activeLearnerId, familyLearners) {
        val selectedId = activeLearnerId ?: return@LaunchedEffect
        val learners = familyLearners ?: return@LaunchedEffect
        if (learners.none { it.learnerId == selectedId }) {
            activeLearnerId = null
            showLearnerChooser = true
            familyPreferences.edit().remove(ACTIVE_LEARNER_ID_KEY).apply()
        }
    }

    LaunchedEffect(showLearnerChooser, showFamilyManagerFromChooser, familyReady) {
        if (showLearnerChooser || showFamilyManagerFromChooser || !familyReady) {
            onPianoVisibilityChanged(false)
        }
    }

    fun selectLearner(learner: LearnerProfile) {
        val learnerId = learner.learnerId
        val audienceKey = learnerAudiencePreferenceKey(learnerId)
        if (!audiencePreferences.contains(audienceKey)) {
            val legacyAudience = audiencePreferences.getString("lesson_audience", "children")
            audiencePreferences.edit().putString(audienceKey, legacyAudience).apply()
        }
        initialRoute = learnerRouteToRestore(
            familyPreferences.getString(learnerRoutePreferenceKey(learnerId), null)
        )
        familyPreferences.edit().putString(ACTIVE_LEARNER_ID_KEY, learnerId).apply()
        activeLearnerId = learnerId
        showLearnerChooser = false
        showFamilyManagerFromChooser = false
    }

    val learners = familyLearners.orEmpty()
    val activeLearner = learners.firstOrNull { it.learnerId == activeLearnerId }

    when {
        !familyReady || familyLearners == null -> FamilyLoadingScreen()
        shouldShowFamilySetup(onboardingComplete) -> {
            FamilyManagementScreen(
                repository = familyRepository,
                familyId = familyId,
                initialSetup = true,
                onComplete = {
                    familyPreferences.edit().putBoolean("family_setup_complete", true).apply()
                    onboardingComplete = true
                }
            )
        }
        showFamilyManagerFromChooser -> {
            FamilyManagementScreen(
                repository = familyRepository,
                familyId = familyId,
                initialSetup = false,
                onComplete = { showFamilyManagerFromChooser = false }
            )
        }
        showLearnerChooser || activeLearner == null -> {
            LearnerChooserScreen(
                learners = learners,
                lastActiveLearnerId = familyPreferences.getString(ACTIVE_LEARNER_ID_KEY, null),
                onSelect = ::selectLearner,
                onManageFamily = { showFamilyManagerFromChooser = true }
            )
        }
        else -> {
            val selectedLearnerId = activeLearner.learnerId
            val navController = rememberNavController()
            val entry by navController.currentBackStackEntryAsState()
            val route = entry?.destination?.route ?: initialRoute

            LaunchedEffect(route) {
                onPianoVisibilityChanged(route == "practice")
            }
            LaunchedEffect(route, selectedLearnerId, entry) {
                if (entry != null) {
                    learnerRouteToPersist(route)?.let { safeRoute ->
                        familyPreferences.edit()
                            .putString(learnerRoutePreferenceKey(selectedLearnerId), safeRoute)
                            .apply()
                    }
                }
            }

            CompositionLocalProvider(LocalActiveLearnerId provides selectedLearnerId) {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            mainTabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = if (tab.route == "more") {
                                        route in setOf("more", "progress", "settings", "family")
                                    } else route == tab.route,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            launchSingleTop = true
                                            restoreState = true
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        }
                                    },
                                    icon = { Text(tab.icon) },
                                    label = { Text(tab.label) }
                                )
                            }
                        }
                    }
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = initialRoute,
                        modifier = Modifier.padding(padding)
                    ) {
                        composable("home") { HomeScreen(onStart = { navController.navigate("learning") }) }
                        composable("learning") {
                            LessonListScreen(
                                repository = lessonRepository,
                                audience = lessonAudience,
                                onAudienceChange = { selected ->
                                    lessonAudience = selected
                                    audiencePreferences.edit()
                                        .putString(learnerAudiencePreferenceKey(selectedLearnerId), selected.storageValue)
                                        .apply()
                                },
                                onLessonClick = { lessonId -> navController.navigate("lesson/$lessonId") }
                            )
                        }
                        composable("lesson/{lessonId}") { backStackEntry ->
                            val lessonId = backStackEntry.arguments?.getString("lessonId").orEmpty()
                            LessonPlayerScreen(
                                repository = lessonRepository,
                                lessonId = lessonId,
                                audience = lessonAudience,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("practice") { PianoScreen() }
                        composable("progress") { ProgressScreen(progressRepository, selectedLearnerId) }
                        composable("library") { LibraryScreen() }
                        composable("more") {
                            MoreScreen(
                                navController = navController,
                                onAboutClick = { showAboutDialog = true },
                                onSwitchLearner = {
                                    activeLearnerId = null
                                    showLearnerChooser = true
                                }
                            )
                        }
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
    }
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
    onProgress: () -> Unit
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
private fun LibraryScreen() {
    val activeLearnerId = LocalActiveLearnerId.current ?: return
    key(activeLearnerId) {
        PageColumn {
            PageTitle("کتابخانه آهنگ‌ها", "کتابخانه شخصی هنرجوی فعال")
            EmptyState("کتابخانه در حال آماده‌سازی است", "قطعات این بخش پس از آماده‌شدن، بر اساس پروفایل فعال نمایش داده می‌شوند.")
        }
    }
}

@Composable
private fun ProgressScreen(repository: ProgressRepository, learnerId: String) {
    val lessonProgress by repository.observeLessonProgress(learnerId).collectAsState(initial = emptyList())
    val practiceSessions by repository.observePracticeSessions(learnerId).collectAsState(initial = emptyList())
    val achievements by repository.observeAchievements(learnerId).collectAsState(initial = emptyList())
    PageColumn {
        PageTitle("پیشرفت من", "گزارش‌ها فقط برای هنرجوی فعال نمایش داده می‌شوند")
        if (lessonProgress.isEmpty() && practiceSessions.isEmpty() && achievements.isEmpty()) {
            EmptyState("هنوز گزارشی ثبت نشده است", "پس از انجام درس‌ها و جلسه‌های تمرین، پیشرفت همین هنرجو در این صفحه نمایش داده می‌شود.")
        } else {
            StatusCard("درس‌های دارای پیشرفت", "${lessonProgress.size} درس")
            StatusCard("جلسه‌های تمرین", "${practiceSessions.size} جلسه")
            StatusCard("دستاوردها", "${achievements.size} مورد")
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
private fun MoreScreen(
    navController: NavHostController,
    onAboutClick: () -> Unit,
    onSwitchLearner: () -> Unit
) {
    val groups = listOf(
        "یادگیری و تمرین" to listOf(
            MenuEntry("خانه و داشبورد", "آماده", "home"),
            MenuEntry("مسیر یادگیری", "آماده", "learning"),
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
            MenuEntry("تغییر هنرجو", "آماده", "switch-learner"),
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
                    if (entry.route == "switch-learner") onSwitchLearner()
                    else entry.route?.let { navController.navigate(it) { launchSingleTop = true } }
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
