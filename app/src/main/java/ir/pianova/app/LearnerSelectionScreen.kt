package ir.pianova.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
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
import ir.pianova.app.domain.family.LearnerAudience
import ir.pianova.app.domain.family.LearnerProfile

@Composable
fun LearnerSelectionScreen(
    learners: List<LearnerProfile>,
    lastActiveLearnerId: String?,
    destinationLabel: (String) -> String,
    onSelectLearner: (LearnerProfile) -> Unit,
    onManageFamily: () -> Unit,
    onFamilyReports: () -> Unit = {}
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "چه کسی امروز پیانو می‌نوازد؟",
                style = MaterialTheme.typography.headlineSmall,
                color = PianovaInk,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "پروفایل هنرجو را انتخاب کنید تا مسیر و تنظیمات همان هنرجو بارگذاری شود.",
                style = MaterialTheme.typography.bodyMedium,
                color = PianovaMuted,
                textAlign = TextAlign.Center
            )

            if (learners.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = PianovaSage
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("هنوز هنرجویی ثبت نشده است.", color = PianovaInk, fontWeight = FontWeight.SemiBold)
                        Text(
                            "مدیر خانواده می‌تواند هر زمان یک یا چند پروفایل هنرجویی ایجاد کند.",
                            color = PianovaMuted,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = true).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(learners, key = { it.learnerId }) { learner ->
                        Card(
                            onClick = { onSelectLearner(learner) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = PianovaWhite),
                            border = BorderStroke(1.dp, PianovaBorder)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(learner.avatarRef ?: "🎹", style = MaterialTheme.typography.headlineLarge)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(learner.displayName, color = PianovaInk, fontWeight = FontWeight.Bold)
                                    Text(audienceLabel(learner.audience), color = PianovaMuted, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        if (learner.learnerId == lastActiveLearnerId) "آخرین هنرجوی انتخاب‌شده · ${destinationLabel(learner.learnerId)}"
                                        else "ورود به مسیر هنرجو",
                                        color = PianovaGreen,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Text("انتخاب", color = PianovaGreen, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onFamilyReports, modifier = Modifier.fillMaxWidth()) {
                Text("گزارش پیشرفت خانواده")
            }
            OutlinedButton(onClick = onManageFamily, modifier = Modifier.fillMaxWidth()) {
                Text("مدیریت خانواده و پروفایل‌ها")
            }
            Text(
                "انتخاب هنرجو آزاد است و نیازی به رمز یا PIN ندارد.",
                color = PianovaMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun audienceLabel(audience: LearnerAudience): String = when (audience) {
    LearnerAudience.CHILD -> "کودک"
    LearnerAudience.TEEN -> "نوجوان"
    LearnerAudience.ADULT -> "بزرگسال"
    LearnerAudience.UNSPECIFIED -> "گروه سنی مشخص نشده"
}
