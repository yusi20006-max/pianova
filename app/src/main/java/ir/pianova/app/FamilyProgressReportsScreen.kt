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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.data.persistence.ProgressRepository
import ir.pianova.app.data.persistence.RoomFamilyRepository
import ir.pianova.app.domain.family.LearnerAudience
import ir.pianova.app.domain.progress.ActivityType
import ir.pianova.app.domain.progress.FamilyLearnerReport
import ir.pianova.app.domain.progress.FamilyProgressOverviewFactory
import ir.pianova.app.domain.progress.FamilyProgressReportFactory
import ir.pianova.app.domain.progress.LearnerActivityEvidence
import ir.pianova.app.domain.progress.PeriodProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun FamilyProgressReportsScreen(
    familyId: String,
    familyRepository: RoomFamilyRepository,
    database: PianovaDatabase,
    onBack: () -> Unit
) {
    val progressRepository = remember(database) { ProgressRepository(database) }
    val reports by remember(familyId, familyRepository, progressRepository) {
        familyRepository.observeLearners(familyId).flatMapLatest { learners ->
            if (learners.isEmpty()) flowOf(emptyList())
            else {
                val reportFlows = learners.map { learner ->
                    combine(
                        progressRepository.observeLearnerProgress(learner.learnerId),
                        progressRepository.observeLearnerGoal(learner.learnerId),
                        progressRepository.observePracticeSessions(learner.learnerId),
                        progressRepository.observeEvaluationEvidence(learner.learnerId),
                        progressRepository.observeLessonProgress(learner.learnerId)
                    ) { progress, goal, sessions, evaluations, lessons ->
                        val activity = buildList {
                            sessions.forEach { session ->
                                add(LearnerActivityEvidence(
                                    session.learnerId,
                                    session.endedAtEpochMillis ?: session.startedAtEpochMillis,
                                    ActivityType.PRACTICE
                                ))
                            }
                            evaluations.forEach { evaluation ->
                                add(LearnerActivityEvidence(
                                    evaluation.learnerId,
                                    evaluation.occurredAtEpochMillis,
                                    ActivityType.EVALUATION
                                ))
                            }
                            lessons.forEach { lesson ->
                                add(LearnerActivityEvidence(
                                    lesson.learnerId,
                                    lesson.lastAccessedAtEpochMillis,
                                    ActivityType.LESSON
                                ))
                            }
                        }
                        FamilyProgressReportFactory.create(learner, progress, goal?.goalText, activity)
                    }
                }
                combine(reportFlows) { values -> values.toList() }
            }
        }
    }.collectAsState(initial = emptyList())

    var selectedLearnerId by remember { mutableStateOf<String?>(null) }
    val selectedReport = reports.firstOrNull { it.learner.learnerId == selectedLearnerId }
    val overview = remember(reports) { FamilyProgressOverviewFactory.aggregate(reports) }

    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (selectedReport == null) "گزارش خانواده" else "گزارش ${selectedReport.learner.displayName}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = PianovaInk,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (selectedReport == null) "نمای کلی عملکرد هنرجویان خانواده"
                        else "سوابق همین هنرجو، بدون تغییر پروفایل فعال",
                        color = PianovaMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = {
                    if (selectedReport != null) selectedLearnerId = null else onBack()
                }) { Text(if (selectedReport != null) "گزارش خانواده" else "بازگشت") }
            }

            if (selectedReport == null) {
                if (reports.isEmpty()) {
                    ReportEmptyState(
                        "هنوز پروفایل هنرجویی وجود ندارد",
                        "پس از افزودن هنرجو، گزارش خانواده در همین صفحه نمایش داده می‌شود."
                    )
                } else {
                    ReportMetricCard("تعداد هنرجویان", overview.reports.size.toString())
                    ReportMetricCard("مجموع زمان تمرین فعال", formatPracticeTime(overview.totalPracticeMillis))
                    ReportMetricCard("مجموع جلسه‌های تمرین", overview.totalSessionCount.toString())
                    ReportMetricCard("مجموع درس‌های تکمیل‌شده", overview.totalCompletedLessonCount.toString())
                    Text("گزارش هر هنرجو", style = MaterialTheme.typography.titleMedium, color = PianovaInk, fontWeight = FontWeight.Bold)
                    reports.forEach { report ->
                        LearnerReportCard(report) { selectedLearnerId = report.learner.learnerId }
                    }
                    Text(
                        "گزارش‌ها جداگانه‌اند و عملکرد هنرجویان با یکدیگر رتبه‌بندی یا مقایسه نمی‌شود.",
                        color = PianovaMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                LearnerReportDetail(selectedReport)
            }
        }
    }
}

@Composable
private fun LearnerReportCard(report: FamilyLearnerReport, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PianovaWhite),
        border = BorderStroke(1.dp, PianovaBorder)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(report.learner.avatarRef ?: "🎹", style = MaterialTheme.typography.headlineMedium)
                Column(Modifier.weight(1f)) {
                    Text(report.learner.displayName, color = PianovaInk, fontWeight = FontWeight.Bold)
                    Text(audienceLabel(report.learner.audience), color = PianovaMuted, style = MaterialTheme.typography.bodySmall)
                }
                Text("مشاهده گزارش", color = PianovaGreen, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "تمرین فعال: ${formatPracticeTime(report.progress.practiceMillis)} · ${report.progress.sessionCount} جلسه",
                color = PianovaInk,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "دقت ارزیابی: ${report.progress.accuracy?.let { "${(it * 100).toInt()}٪" } ?: "داده‌ای ثبت نشده"}",
                color = PianovaMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun LearnerReportDetail(report: FamilyLearnerReport) {
    val progress = report.progress
    report.goal?.let { ReportMetricCard("هدف یادگیری", it) }
    val hasEvidence = progress.practiceMillis > 0 || progress.evaluationCount > 0 ||
        progress.completedLessonCount > 0 || progress.skills.isNotEmpty()
    if (!hasEvidence) {
        ReportEmptyState(
            "هنوز گزارشی برای این هنرجو ثبت نشده است",
            "پس از انجام تمرین یا ارزیابی، زمان فعال، نتایج و مهارت‌ها از سوابق واقعی نمایش داده می‌شوند."
        )
    } else {
        ReportMetricCard("زمان تمرین فعال", formatPracticeTime(progress.practiceMillis))
        ReportMetricCard("تعداد جلسه‌های تمرین", progress.sessionCount.toString())
        ReportMetricCard("درس‌های تکمیل‌شده", progress.completedLessonCount.toString())
        ReportMetricCard("دقت ارزیابی", progress.accuracy?.let { "${(it * 100).toInt()}٪" } ?: "هنوز ارزیابی کافی ثبت نشده")
        ReportMetricCard("تعداد ارزیابی‌های ثبت‌شده", progress.evaluationCount.toString())

        Text("فعالیت اخیر", style = MaterialTheme.typography.titleMedium, color = PianovaInk, fontWeight = FontWeight.Bold)
        val activity = report.recentActivity
        if (activity == null) {
            Text("فعالیت ثبت‌شده‌ای وجود ندارد.", color = PianovaMuted)
        } else {
            val label = when (activity.activityType) {
                ActivityType.PRACTICE -> "آخرین جلسه تمرین"
                ActivityType.EVALUATION -> "آخرین ارزیابی"
                ActivityType.LESSON -> "آخرین فعالیت در درس"
            }
            ReportMetricCard(label, formatTimestamp(activity.occurredAtEpochMillis))
        }

        Text("نقاط قوت", style = MaterialTheme.typography.titleMedium, color = PianovaInk, fontWeight = FontWeight.Bold)
        if (progress.strengths.isEmpty()) Text("هنوز شواهد کافی برای تأیید تسلط ثبت نشده است.", color = PianovaMuted)
        else progress.strengths.forEach { ReportMetricCard(it.skillId, "تسلط با شواهد مستقل تأیید شده") }

        Text("مهارت‌های نیازمند تمرین", style = MaterialTheme.typography.titleMedium, color = PianovaInk, fontWeight = FontWeight.Bold)
        if (progress.skillsNeedingPractice.isEmpty()) Text("هنوز مهارتی با شواهد کافی در این دسته نیست.", color = PianovaMuted)
        else progress.skillsNeedingPractice.forEach {
            ReportMetricCard(it.skillId, "${it.successfulAttempts} موفق از ${it.attempts} ارزیابی")
        }

        Text("روند هفتگی", style = MaterialTheme.typography.titleMedium, color = PianovaInk, fontWeight = FontWeight.Bold)
        progress.weekly.takeLast(4).forEach { period ->
            ReportMetricCard(
                LocalDate.ofEpochDay(period.periodStartEpochDay).toString(),
                "${formatPracticeTime(period.practiceMillis)} · ${period.sessionCount} جلسه · ${period.accuracy?.let { "${(it * 100).toInt()}٪ دقت" } ?: "بدون ارزیابی"}"
            )
        }
        ReportPeriodComparison(progress.weekly.takeLast(2))

        Text("روند ماهانه", style = MaterialTheme.typography.titleMedium, color = PianovaInk, fontWeight = FontWeight.Bold)
        progress.monthly.takeLast(3).forEach { period ->
            ReportMetricCard(
                LocalDate.ofEpochDay(period.periodStartEpochDay).toString(),
                "${formatPracticeTime(period.practiceMillis)} · ${period.sessionCount} جلسه · ${period.accuracy?.let { "${(it * 100).toInt()}٪ دقت" } ?: "بدون ارزیابی"}"
            )
        }
        ReportPeriodComparison(progress.monthly.takeLast(2))
    }
}

@Composable
private fun ReportPeriodComparison(periods: List<PeriodProgress>) {
    if (periods.size < 2) return
    val previous = periods[0]
    val current = periods[1]
    if (previous.practiceMillis == 0L && current.practiceMillis == 0L &&
        previous.completedEvaluations == 0 && current.completedEvaluations == 0
    ) {
        Text("برای مقایسه با دوره قبل هنوز داده کافی وجود ندارد.", color = PianovaMuted, style = MaterialTheme.typography.bodySmall)
        return
    }
    val delta = current.practiceMillis - previous.practiceMillis
    val label = when {
        delta > 0 -> "افزایش"
        delta < 0 -> "کاهش"
        else -> "بدون تغییر"
    }
    Text(
        "$label زمان تمرین نسبت به دوره قبل: ${formatPracticeTime(kotlin.math.abs(delta))}",
        color = PianovaMuted,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun ReportMetricCard(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PianovaWhite),
        border = BorderStroke(1.dp, PianovaBorder)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = PianovaMuted, style = MaterialTheme.typography.bodySmall)
            Text(value, color = PianovaInk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ReportEmptyState(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PianovaSage)
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = PianovaInk, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(description, color = PianovaMuted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatPracticeTime(millis: Long): String {
    val minutes = millis.coerceAtLeast(0) / 60_000
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours > 0) "$hours ساعت و $remainingMinutes دقیقه" else "$minutes دقیقه"
}

private fun formatTimestamp(epochMillis: Long): String =
    java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))

private fun audienceLabel(audience: LearnerAudience): String = when (audience) {
    LearnerAudience.CHILD -> "کودک"
    LearnerAudience.TEEN -> "نوجوان"
    LearnerAudience.ADULT -> "بزرگسال"
    LearnerAudience.UNSPECIFIED -> "هنرجو"
}
