package ir.pianova.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.pianova.app.core.designsystem.PianovaBorder
import ir.pianova.app.core.designsystem.PianovaGreen
import ir.pianova.app.core.designsystem.PianovaInk
import ir.pianova.app.core.designsystem.PianovaMuted
import ir.pianova.app.core.designsystem.PianovaSage
import ir.pianova.app.core.designsystem.PianovaWhite
import ir.pianova.app.domain.family.FamilyRepository
import ir.pianova.app.domain.family.LearnerAudience
import ir.pianova.app.domain.family.LearnerProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

private const val LOCAL_FAMILY_ID = "local-family"

@Composable
fun FamilyManagementScreen(
    repository: FamilyRepository,
    initialSetup: Boolean,
    onComplete: () -> Unit
) {
    val learners by repository.observeLearners(LOCAL_FAMILY_ID).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editingLearnerId by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf(LearnerAudience.CHILD) }
    var level by remember { mutableStateOf("beginner") }
    var avatar by remember { mutableStateOf("🎹") }
    var goal by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<LearnerProfile?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(editingLearnerId) {
        val selected = learners.firstOrNull { it.learnerId == editingLearnerId }
        if (selected == null) {
            displayName = ""
            audience = LearnerAudience.CHILD
            level = "beginner"
            avatar = "🎹"
            goal = ""
        } else {
            displayName = selected.displayName
            audience = selected.audience
            level = selected.initialLevelId
            avatar = selected.avatarRef ?: "🎹"
            goal = repository.observeGoal(selected.learnerId).first().orEmpty()
        }
        errorMessage = null
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (initialSetup) "راه‌اندازی خانواده" else "مدیریت خانواده و هنرجویان",
                style = MaterialTheme.typography.headlineSmall,
                color = PianovaInk,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (initialSetup) "یک خانواده محلی بسازید و پروفایل هنرجویان را اضافه کنید. همه اطلاعات روی همین دستگاه ذخیره می‌شود."
                else "مدیریت خانواده مستقل از پروفایل هنرجو است. بزرگسال نیز می‌تواند در کنار مدیریت خانواده، پروفایل هنرجویی جداگانه داشته باشد.",
                style = MaterialTheme.typography.bodyMedium,
                color = PianovaMuted
            )

            if (!initialSetup) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PianovaSage),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("مدیریت‌کننده خانواده", color = PianovaInk, fontWeight = FontWeight.SemiBold)
                        Text("این بخش به ساخت پروفایل هنرجو برای سرپرست نیاز ندارد.", color = PianovaMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (learners.isNotEmpty()) {
                Text("پروفایل‌های ثبت‌شده (\${learners.size})", style = MaterialTheme.typography.titleMedium, color = PianovaInk, fontWeight = FontWeight.Bold)
                learners.forEach { learner ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PianovaWhite),
                        border = BorderStroke(1.dp, PianovaBorder)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(learner.avatarRef ?: "🎹", style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.semantics { contentDescription = "تصویر \${learner.displayName}" })
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(learner.displayName, color = PianovaInk, fontWeight = FontWeight.SemiBold)
                                Text(audienceLabel(learner.audience) + " · " + levelLabel(learner.initialLevelId), color = PianovaMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { editingLearnerId = learner.learnerId }) { Text("ویرایش") }
                            TextButton(onClick = { deleteTarget = learner }) { Text("حذف") }
                        }
                    }
                }
            } else if (!initialSetup) {
                Text("هنوز پروفایل هنرجویی ساخته نشده است.", color = PianovaMuted)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = PianovaWhite),
                border = BorderStroke(1.dp, PianovaBorder)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (editingLearnerId == null) "افزودن هنرجو" else "ویرایش پروفایل",
                        style = MaterialTheme.typography.titleLarge,
                        color = PianovaInk,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it; errorMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("نام نمایشی") },
                        singleLine = true,
                        isError = errorMessage != null
                    )
                    Text("گروه سنی", color = PianovaInk, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(LearnerAudience.CHILD, LearnerAudience.TEEN, LearnerAudience.ADULT).forEach { option ->
                            FilterChip(
                                selected = audience == option,
                                onClick = { audience = option },
                                label = { Text(audienceLabel(option)) }
                            )
                        }
                    }
                    Text("سطح شروع", color = PianovaInk, fontWeight = FontWeight.SemiBold)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("beginner", "elementary", "intermediate", "advanced").chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { option ->
                                    FilterChip(
                                        selected = level == option,
                                        onClick = { level = option },
                                        label = { Text(levelLabel(option)) }
                                    )
                                }
                            }
                        }
                    }
                    Text("تصویر پروفایل", color = PianovaInk, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("🎹", "🎼", "🎵", "🌟", "🎧").forEach { option ->
                            FilterChip(
                                selected = avatar == option,
                                onClick = { avatar = option },
                                label = { Text(option, style = MaterialTheme.typography.titleMedium) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = goal,
                        onValueChange = { goal = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("هدف یادگیری (اختیاری)") },
                        placeholder = { Text("مثلاً یادگیری یک قطعه مورد علاقه") },
                        minLines = 2,
                        maxLines = 3
                    )
                    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Button(
                        onClick = {
                            val name = displayName.trim()
                            if (name.isEmpty()) {
                                errorMessage = "نام نمایشی را وارد کنید."
                            } else {
                                scope.launch {
                                    runCatching {
                                        val now = System.currentTimeMillis()
                                        val existing = learners.firstOrNull { it.learnerId == editingLearnerId }
                                        val learner = LearnerProfile(
                                            learnerId = existing?.learnerId ?: UUID.randomUUID().toString(),
                                            familyId = LOCAL_FAMILY_ID,
                                            displayName = name,
                                            audience = audience,
                                            avatarRef = avatar,
                                            initialLevelId = existing?.initialLevelId ?: level,
                                            currentLevelId = existing?.currentLevelId ?: level,
                                            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                                            updatedAtEpochMillis = now
                                        )
                                        repository.saveLearner(learner)
                                        repository.saveGoal(learner.learnerId, goal)
                                    }.onSuccess {
                                        editingLearnerId = null
                                    }.onFailure {
                                        errorMessage = "ذخیره پروفایل انجام نشد. دوباره تلاش کنید."
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (editingLearnerId == null) "افزودن پروفایل" else "ذخیره تغییرات")
                    }
                    if (editingLearnerId != null) {
                        OutlinedButton(
                            onClick = { editingLearnerId = null },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("انصراف از ویرایش") }
                    }
                }
            }

            if (initialSetup) {
                Text(
                    "می‌توانید اکنون یک یا چند هنرجو اضافه کنید و بعداً از بخش مدیریت خانواده، پروفایل‌های بیشتری بسازید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PianovaMuted,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onComplete,
                    enabled = learners.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ورود به Pianova") }
            } else {
                OutlinedButton(onClick = onComplete, modifier = Modifier.fillMaxWidth()) { Text("بازگشت") }
            }
        }
    }

    deleteTarget?.let { learner ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف پروفایل \${learner.displayName}") },
            text = {
                Text("با حذف این پروفایل، سوابق درس‌ها، جلسه‌های تمرین، دستاوردها و پیشرفت آهنگ‌های همین هنرجو نیز از این دستگاه حذف می‌شود. اطلاعات سایر هنرجویان باقی می‌ماند. این کار قابل بازگشت نیست.")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.deleteLearner(learner.learnerId)
                        if (editingLearnerId == learner.learnerId) editingLearnerId = null
                        deleteTarget = null
                    }
                }) { Text("حذف پروفایل") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("انصراف") } }
        )
    }
}

private fun audienceLabel(audience: LearnerAudience): String = when (audience) {
    LearnerAudience.CHILD -> "کودک"
    LearnerAudience.TEEN -> "نوجوان"
    LearnerAudience.ADULT -> "بزرگسال"
    LearnerAudience.UNSPECIFIED -> "نامشخص"
}

private fun levelLabel(level: String): String = when (level) {
    "beginner" -> "مبتدی"
    "elementary" -> "مقدماتی"
    "intermediate" -> "متوسط"
    "advanced" -> "پیشرفته"
    else -> "مبتدی"
}
