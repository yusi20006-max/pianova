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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
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
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.core.designsystem.AboutDialog
import ir.pianova.app.core.designsystem.PianovaFuture
import ir.pianova.app.core.designsystem.PianovaGreen
import ir.pianova.app.core.designsystem.PianovaInk
import ir.pianova.app.core.designsystem.PianovaMuted
import ir.pianova.app.core.designsystem.PianovaSage
import ir.pianova.app.core.designsystem.PianovaWhite
import ir.pianova.app.core.midi.PianoInput
import ir.pianova.app.data.lesson.AndroidLessonAssetSource
import ir.pianova.app.data.lesson.LessonRepository
import ir.pianova.app.data.persistence.RoomFamilyRepository
import ir.pianova.app.data.persistence.RoomLearnerGoalRepository
import ir.pianova.app.domain.family.Family
import ir.pianova.app.domain.model.LessonAudience
import kotlinx.coroutines.flow.first
import java.util.UUID

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
    val navController = rememberNavController()
    val context = LocalContext.current
    val lessonRepository = remember(context) { LessonRepository(AndroidLessonAssetSource(context.assets)) }
    val database = remember(context) { PianovaDatabase.getInstance(context) }
    val familyRepository = remember(database) { RoomFamilyRepository(database) }
    val goalRepository = remember(database) { RoomLearnerGoalRepository(database) }
    val audiencePreferences = remember(context) { context.getSharedPreferences("pianova_preferences", 0) }
    val localFamily by familyRepository.observeLocalFamily().collectAsState(initial = null)
    var familyReady by remember(familyRepository) { mutableStateOf(false) }
    var onboardingComplete by remember(audiencePreferences) {
        mutableStateOf(audiencePreferences.getBoolean("family_setup_complete", false))
    }
    LaunchedEffect(familyRepository) {
        if (familyRepository.observeLocalFamily().first() == null) {
            val now = System.currentTimeMillis()
            familyRepository.saveFamily(Family(UUID.randomUUID().toString(), now, now))
        }
        familyReady = true
    }
    var lessonAudience by remember(audiencePreferences) {
        mutableStateOf(LessonAudience.fromStorageValue(audiencePreferences.getString("lesson_audience", "children")))
    }
    var showAboutDialog by remember { mutableStateOf(false) }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"

    LaunchedEffect(route) { onPianoVisibilityChanged(route == "practice") }
    val family = localFamily

    if (!familyReady || family == null) {
        FamilyLoadingScreen()
        return
    }
    if (!onboardingComplete) {
        FamilyManagementScreen(
            familyId = family.familyId,
            familyRepository = familyRepository,
            goalRepository = goalRepository,
            onboarding = true,
            onContinue = {
                audiencePreferences.edit().putBoolean("family_setup_complete", true).apply()
                onboardingComplete = true
            },
            onBack = {}
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (route != "practice" && !route.startsWith("lesson/")) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    mainTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = if (tab.route == "more") route in setOf("more", "progress", "settings", "family") else route == tab.route,
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
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(if (route == "practice") PaddingValues(0.dp) else padding)
        ) {
            composable("home") {
                HomeScreen(
                    onContinue = { navController.navigate("learning") },
                    onPractice = { navController.navigate("practice") },
                    onLibrary = { navController.navigate("library") },
                    onProgress = { navController.navigate("progress") }
                )
            }
            composable("learning") {
                LessonListScreen(
                    repository = lessonRepository,
                    audience = lessonAudience,
                    onAudienceChange = { selected ->
                        lessonAudience = selected
                        audiencePreferences.edit().putString("lesson_audience", selected.storageValue).apply()
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
            composable("library") { LibraryScreen() }
            composable("more") { MoreScreen(navController, onAboutClick = { showAboutDialog = true }) }
            composable("progress") { ProgressScreen() }
            composable("settings") { SettingsScreen(onAboutClick = { showAboutDialog = true }) }
            composable("family") {
                FamilyManagementScreen(
                    familyId = family.familyId,
                    familyRepository = familyRepository,
                    goalRepository = goalRepository,
                    onboarding = false,
                    onContinue = {},
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
    if (showAboutDialog) AboutDialog(onDismiss = { showAboutDialog = false })
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
    PageColumn {
        PageTitle("کتابخانه آهنگ‌ها", "قطعات آموزشی و آهنگ‌های شخصی")
        EmptyState("کتابخانه در حال آماده‌سازی است", "قطعات مبتدی ایرانی و بین‌المللی در مراحل بعدی اضافه می‌شوند.")
    }
}

@Composable
private fun ProgressScreen() {
    PageColumn {
        PageTitle("پیشرفت من", "مسیر تمرین و مهارت‌هایت را اینجا دنبال می‌کنی")
        EmptyState("هنوز گزارشی ثبت نشده است", "پس از انجام درس‌ها و جلسه‌های تمرین، زمان تمرین و پیشرفت واقعی تو در این صفحه نمایش داده می‌شود.")
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
            MenuEntry("پروفایل", "در حال توسعه", future = true),
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
