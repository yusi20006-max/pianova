package ir.pianova.app
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import ir.pianova.app.core.designsystem.PianovaBackButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.core.designsystem.PianovaGreen
import ir.pianova.app.core.designsystem.PianovaInk
import ir.pianova.app.core.designsystem.PianovaMuted
import ir.pianova.app.data.persistence.LearningPathRepository
import ir.pianova.app.domain.family.LearnerProfile
import ir.pianova.app.domain.learning.LearningPathCatalog
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun LearningPathScreen(learner: LearnerProfile, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val database = remember(context) { PianovaDatabase.getInstance(context) }
    val repository = remember(database) { LearningPathRepository(database) }
    val active by repository.observeActive(learner.learnerId).collectAsState(initial = null)
    val history by repository.observeHistory(learner.learnerId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var message by remember(learner.learnerId) { mutableStateOf<String?>(null) }
    LaunchedEffect(learner.learnerId, learner.audience, learner.currentLevelId) {
        repository.ensureInitialAssignment(learner.learnerId, learner.audience, learner.currentLevelId)
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                PianovaBackButton(onBack = onBack, label = "بازگشت")
                Text("مسیر آموزشی هنرجو", style = MaterialTheme.typography.headlineSmall, color = PianovaInk, fontWeight = FontWeight.Bold)
                Text("مسیر و سطح را هر زمان تغییر دهید؛ سابقه انتخاب‌ها و پیشرفت درس‌ها حفظ می‌شود.", color = PianovaMuted)
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("انتخاب فعلی", color = PianovaMuted)
                    Text(active?.let { LearningPathCatalog.path(it.pathId)?.title ?: it.pathId } ?: "هنوز مسیری انتخاب نشده",
                        color = PianovaInk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(active?.let { LearningPathCatalog.level(it.levelId)?.title ?: it.levelId } ?: "یک مسیر را انتخاب کنید", color = PianovaGreen)
                }
            }
        }
        item { Text("مسیرهای قابل انتخاب", color = PianovaInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(LearningPathCatalog.pathsFor(learner.audience), key = { it.id }) { path ->
            val recommended = learner.audience in path.recommendedFor
            val selected = active?.pathId == path.id
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(path.title, color = PianovaInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (recommended) Text("پیشنهادی", color = PianovaGreen, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(path.description, color = PianovaMuted)
                    Text("تمرکز: " + path.focus, color = PianovaInk, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { scope.launch {
                        runCatching { repository.switchPath(learner.learnerId, path.id) }
                            .onSuccess { message = "مسیر به‌روزرسانی شد؛ سوابق قبلی حفظ شده‌اند." }
                            .onFailure { message = it.message ?: "تغییر مسیر انجام نشد." }
                    } }, enabled = !selected) { Text(if (selected) "مسیر فعلی" else "انتخاب این مسیر") }
                }
            }
        }
        item { Text("سطح آموزشی", color = PianovaInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(LearningPathCatalog.levels, key = { it.id }) { level ->
            val selected = active?.levelId == level.id
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(level.title, color = PianovaInk, fontWeight = FontWeight.Bold)
                        if (selected) Text("سطح فعلی", color = PianovaGreen, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(level.objective, color = PianovaMuted)
                    Text("پیش‌نیاز: " + level.prerequisiteDescription, color = PianovaInk, style = MaterialTheme.typography.bodySmall)
                    if (level.prerequisiteLevelIds.isNotEmpty()) {
                        Text("سطح‌های پیش‌نیاز: " + level.prerequisiteLevelIds.mapNotNull { LearningPathCatalog.level(it)?.title }.joinToString("، "),
                            color = PianovaMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { scope.launch {
                        runCatching { repository.changeLevel(learner.learnerId, level.id) }
                            .onSuccess { message = "سطح تغییر کرد؛ تاریخچه و پیشرفت حذف نشد." }
                            .onFailure { message = it.message ?: "تغییر سطح انجام نشد." }
                    } }, enabled = active != null && !selected) { Text(if (selected) "سطح فعلی" else "انتخاب سطح") }
                }
            }
        }
        message?.let { item { Text(it, color = PianovaGreen, style = MaterialTheme.typography.bodySmall) } }
        item { Text("تاریخچه مسیرها و سطح‌ها", color = PianovaInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (history.isEmpty()) item { Text("پس از انتخاب مسیر، تاریخچه در این بخش ثبت می‌شود.", color = PianovaMuted) }
        else items(history, key = { it.assignmentId }) { record ->
            val pathTitle = LearningPathCatalog.path(record.pathId)?.title ?: record.pathId
            val levelTitle = LearningPathCatalog.level(record.levelId)?.title ?: record.levelId
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("$pathTitle · $levelTitle", color = PianovaInk, fontWeight = FontWeight.SemiBold)
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.startedAtEpochMillis)) +
                        if (record.isActive) " · فعال" else " · پایان‌یافته", color = PianovaMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("پیش‌نیازها راهنمای آموزشی‌اند و مانع انتخاب مسیر یا سطح نمی‌شوند. سن فقط ترتیب پیشنهادها را تغییر می‌دهد؛ هیچ مسیر یا قابلیتی بر اساس سن قفل نیست.",
            color = PianovaMuted, style = MaterialTheme.typography.bodySmall) }
    }
}
