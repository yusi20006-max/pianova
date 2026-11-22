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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.pianova.app.core.designsystem.PianovaGreen
import ir.pianova.app.core.designsystem.PianovaInk
import ir.pianova.app.core.designsystem.PianovaMuted
import ir.pianova.app.core.designsystem.PianovaSage
import ir.pianova.app.core.designsystem.PianovaWhite
import ir.pianova.app.domain.family.LearnerAudience
import ir.pianova.app.domain.family.LearnerProfile

@Composable
fun LearnerChooserScreen(
    learners: List<LearnerProfile>,
    lastActiveLearnerId: String?,
    onSelect: (LearnerProfile) -> Unit,
    onManageFamily: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    "چه کسی امروز پیانو می‌نوازد؟",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PianovaInk,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "پروفایل هنرجو را انتخاب کنید. جابه‌جایی آزاد است و نیازی به رمز یا PIN ندارد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PianovaMuted
                )
            }
        }
        if (learners.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = PianovaWhite)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Text("هنوز پروفایل هنرجویی وجود ندارد", color = PianovaInk, fontWeight = FontWeight.SemiBold)
                        Text(
                            "ابتدا از مدیریت خانواده یک هنرجو بسازید؛ می‌توانید بعداً چند هنرجو اضافه کنید.",
                            color = PianovaMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(onClick = onManageFamily) { Text("مدیریت خانواده") }
                    }
                }
            }
        } else {
            item {
                Text("انتخاب هنرجو", style = MaterialTheme.typography.titleMedium, color = PianovaInk, fontWeight = FontWeight.Bold)
            }
            items(learners, key = { it.learnerId }) { learner ->
                Card(
                    onClick = { onSelect(learner) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = if (learner.learnerId == lastActiveLearnerId) PianovaSage else PianovaWhite)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(13.dp)
                    ) {
                        Text(avatarGlyph(learner.avatarRef), style = MaterialTheme.typography.headlineMedium)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(learner.displayName, style = MaterialTheme.typography.titleMedium, color = PianovaInk, fontWeight = FontWeight.SemiBold)
                            Text(audienceLabel(learner.audience), style = MaterialTheme.typography.bodySmall, color = PianovaMuted)
                            if (learner.learnerId == lastActiveLearnerId) {
                                Text("آخرین هنرجوی انتخاب‌شده", style = MaterialTheme.typography.labelSmall, color = PianovaGreen)
                            }
                        }
                        Text("ورود", style = MaterialTheme.typography.labelLarge, color = PianovaGreen)
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PianovaSage)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("مدیریت خانواده", color = PianovaInk, fontWeight = FontWeight.SemiBold)
                        Text("افزودن، ویرایش یا حذف پروفایل‌ها", color = PianovaGreen, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onManageFamily) { Text("باز کردن") }
                }
            }
        }
    }
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
