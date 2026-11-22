package ir.pianova.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pianova.app.core.designsystem.PianovaGreen
import ir.pianova.app.core.designsystem.PianovaInk
import ir.pianova.app.core.designsystem.PianovaMuted
import ir.pianova.app.core.designsystem.PianovaSage
import ir.pianova.app.core.designsystem.PianovaWhite
import ir.pianova.app.domain.family.FamilyRepository
import ir.pianova.app.domain.family.LearnerAudience
import ir.pianova.app.domain.family.LearnerGoal
import ir.pianova.app.domain.family.LearnerGoalRepository
import ir.pianova.app.domain.family.LearnerProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun FamilyManagementScreen(
    familyId: String,
    familyRepository: FamilyRepository,
    goalRepository: LearnerGoalRepository,
    onboarding: Boolean,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val learners by familyRepository.observeLearners(familyId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editorVisible by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<LearnerProfile?>(null) }
    var editingGoal by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<LearnerProfile?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!onboarding) {
                    TextButton(onClick = onBack) { Text("بازگشت") }
                }
                Text(
                    if (onboarding) "خانوادهٔ Pianova" else "مدیریت خانواده",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PianovaInk,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "پروفایل هر هنرجو مستقل است؛ پیشرفت و هدف آموزشی او با اعضای دیگر ترکیب نمی‌شود.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PianovaMuted
                )
                if (onboarding) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PianovaSage)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("فضای خانوادگی محلی ساخته شد", color = PianovaInk, fontWeight = FontWeight.SemiBold)
                            Text("بدون ساخت حساب یا اتصال اینترنت، برای هر هنرجو یک پروفایل جدا بسازید.", color = PianovaGreen, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (learners.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PianovaWhite)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("هنوز هنرجویی اضافه نشده است", color = PianovaInk, fontWeight = FontWeight.SemiBold)
                        Text("می‌توانید یک یا چند هنرجو بسازید یا این مرحله را فعلاً رد کنید.", color = PianovaMuted, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            editingProfile = null
                            editingGoal = ""
                            saveError = null
                            editorVisible = true
                        }) { Text("افزودن نخستین هنرجو") }
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("هنرجویان خانواده", style = MaterialTheme.typography.titleMedium, color = PianovaInk, fontWeight = FontWeight.Bold)
                    Text("${learners.size} پروفایل", style = MaterialTheme.typography.labelMedium, color = PianovaMuted)
                }
            }
            items(learners, key = { it.learnerId }) { learner ->
                LearnerProfileCard(
                    learner = learner,
                    goalRepository = goalRepository,
                    onEdit = {
                        scope.launch {
                            editingProfile = learner
                            editingGoal = goalRepository.observeGoal(learner.learnerId).first()?.title.orEmpty()
                            saveError = null
                            editorVisible = true
                        }
                    },
                    onDelete = { pendingDelete = learner }
                )
            }
            item {
                Button(
                    onClick = {
                        editingProfile = null
                        editingGoal = ""
                        saveError = null
                        editorVisible = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("افزودن هنرجوی دیگر") }
            }
        }

        item {
            if (onboarding) {
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text("ادامه به Pianova")
                }
                Text(
                    "مدیریت خانواده و افزودن هنرجوها بعداً هم از بخش «بیشتر» در دسترس است.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PianovaMuted
                )
            }
        }
    }

    if (editorVisible) {
        LearnerProfileEditorDialog(
            profile = editingProfile,
            initialGoal = editingGoal,
            saving = saving,
            errorMessage = saveError,
            onDismiss = { if (!saving) editorVisible = false },
            onSave = { displayName, audience, avatarRef, levelId, goalTitle ->
                val now = System.currentTimeMillis()
                val learnerId = editingProfile?.learnerId ?: UUID.randomUUID().toString()
                val profile = LearnerProfile(
                    learnerId = learnerId,
                    familyId = familyId,
                    displayName = displayName,
                    audience = audience,
                    avatarRef = avatarRef,
                    initialLevelId = editingProfile?.initialLevelId ?: levelId,
                    currentLevelId = editingProfile?.currentLevelId ?: levelId,
                    createdAtEpochMillis = editingProfile?.createdAtEpochMillis ?: now,
                    updatedAtEpochMillis = now
                )
                saving = true
                saveError = null
                scope.launch {
                    try {
                        familyRepository.saveLearner(profile)
                        val existingGoal = goalRepository.observeGoal(learnerId).first()
                        if (goalTitle.isBlank()) {
                            goalRepository.deleteGoal(learnerId)
                        } else {
                            goalRepository.saveGoal(
                                LearnerGoal(
                                    learnerId = learnerId,
                                    title = goalTitle.trim(),
                                    createdAtEpochMillis = existingGoal?.createdAtEpochMillis ?: now,
                                    updatedAtEpochMillis = now
                                )
                            )
                        }
                        editorVisible = false
                    } catch (error: Exception) {
                        saveError = error.message ?: "ذخیره پروفایل انجام نشد."
                    } finally {
                        saving = false
                    }
                }
            }
        )
    }

    pendingDelete?.let { learner ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف پروفایل هنرجو؟") },
            text = {
                Text("پروفایل «${learner.displayName}» و داده‌های وابسته به همین هنرجو، از جمله پیشرفت و هدف آموزشی، حذف می‌شود. اطلاعات سایر اعضا باقی می‌ماند.")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        familyRepository.deleteLearner(learner.learnerId)
                        pendingDelete = null
                    }
                }) { Text("حذف هنرجو") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun LearnerProfileCard(
    learner: LearnerProfile,
    goalRepository: LearnerGoalRepository,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val goal by goalRepository.observeGoal(learner.learnerId).collectAsState(initial = null)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PianovaWhite)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(avatarGlyph(learner.avatarRef), style = MaterialTheme.typography.headlineMedium)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(learner.displayName, style = MaterialTheme.typography.titleMedium, color = PianovaInk, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${audienceLabel(learner.audience)} · ${levelLabel(learner.currentLevelId)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PianovaMuted
                    )
                }
            }
            if (goal != null) {
                Text("هدف: ${goal?.title}", style = MaterialTheme.typography.bodySmall, color = PianovaGreen)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("ویرایش") }
                TextButton(onClick = onDelete) { Text("حذف") }
            }
        }
    }
}

@Composable
private fun LearnerProfileEditorDialog(
    profile: LearnerProfile?,
    initialGoal: String,
    saving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (String, LearnerAudience, String, String, String) -> Unit
) {
    var displayName by remember(profile?.learnerId) { mutableStateOf(profile?.displayName.orEmpty()) }
    var audience by remember(profile?.learnerId) { mutableStateOf(profile?.audience ?: LearnerAudience.CHILD) }
    var avatarRef by remember(profile?.learnerId) { mutableStateOf(profile?.avatarRef ?: "piano") }
    var levelId by remember(profile?.learnerId) { mutableStateOf(profile?.initialLevelId ?: "beginner") }
    var goalTitle by remember(profile?.learnerId, initialGoal) { mutableStateOf(initialGoal) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) "افزودن هنرجو" else "ویرایش پروفایل") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("نام نمایشی") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("گروه سنی", style = MaterialTheme.typography.labelLarge, color = PianovaInk)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AudienceChip("کودک", audience == LearnerAudience.CHILD) { audience = LearnerAudience.CHILD }
                    AudienceChip("نوجوان", audience == LearnerAudience.TEEN) { audience = LearnerAudience.TEEN }
                    AudienceChip("بزرگسال", audience == LearnerAudience.ADULT) { audience = LearnerAudience.ADULT }
                }
                if (audience == LearnerAudience.UNSPECIFIED) {
                    Row {
                        AudienceChip("تعیین‌نشده", true) { audience = LearnerAudience.UNSPECIFIED }
                    }
                }
                Text("نماد پروفایل", style = MaterialTheme.typography.labelLarge, color = PianovaInk)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AvatarChip("🎹", avatarRef == "piano") { avatarRef = "piano" }
                    AvatarChip("🎵", avatarRef == "music") { avatarRef = "music" }
                    AvatarChip("⭐", avatarRef == "star") { avatarRef = "star" }
                    AvatarChip("🌿", avatarRef == "leaf") { avatarRef = "leaf" }
                }
                if (profile == null) {
                    Text("سطح آغازین", style = MaterialTheme.typography.labelLarge, color = PianovaInk)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LevelChip("مبتدی", "beginner", levelId) { levelId = it }
                            LevelChip("پایه", "elementary", levelId) { levelId = it }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LevelChip("متوسط", "intermediate", levelId) { levelId = it }
                            LevelChip("پیشرفته", "advanced", levelId) { levelId = it }
                        }
                    }
                } else {
                    Text(
                        "سطح آغازین: " + levelLabel(profile.initialLevelId),
                        style = MaterialTheme.typography.bodySmall,
                        color = PianovaMuted
                    )
                }
                OutlinedTextField(
                    value = goalTitle,
                    onValueChange = { goalTitle = it },
                    label = { Text("هدف یادگیری (اختیاری)") },
                    placeholder = { Text("مثلاً اجرای یک قطعه کوتاه") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                if (errorMessage != null) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(displayName.trim(), audience, avatarRef, levelId, goalTitle.trim()) },
                enabled = displayName.isNotBlank() && !saving
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("انصراف") }
        }
    )
}

@Composable
private fun AudienceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun AvatarChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun LevelChip(label: String, value: String, selectedValue: String, onClick: (String) -> Unit) {
    FilterChip(selected = selectedValue == value, onClick = { onClick(value) }, label = { Text(label) })
}

private fun avatarGlyph(avatarRef: String?): String = when (avatarRef) {
    "music" -> "🎵"
    "star" -> "⭐"
    "leaf" -> "🌿"
    else -> "🎹"
}

private fun audienceLabel(audience: LearnerAudience): String = when (audience) {
    LearnerAudience.CHILD -> "کودک"
    LearnerAudience.TEEN -> "نوجوان"
    LearnerAudience.ADULT -> "بزرگسال"
    LearnerAudience.UNSPECIFIED -> "تعیین‌نشده"
}

private fun levelLabel(levelId: String): String = when (levelId) {
    "elementary" -> "پایه"
    "intermediate" -> "متوسط"
    "advanced" -> "پیشرفته"
    else -> "مبتدی"
}
