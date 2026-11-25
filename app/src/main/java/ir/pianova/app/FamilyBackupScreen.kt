package ir.pianova.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.core.designsystem.PianovaInk
import ir.pianova.app.core.designsystem.PianovaMuted
import ir.pianova.app.core.designsystem.PianovaSage
import ir.pianova.app.core.designsystem.PianovaWhite
import ir.pianova.app.data.backup.FamilyBackupManager
import ir.pianova.app.data.backup.FamilyBackupPreview
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun FamilyBackupScreen(
    database: PianovaDatabase,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<FamilyBackupPreview?>(null) }
    var busy by remember { mutableStateOf(false) }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                statusMessage = null
                runCatching { FamilyBackupManager.exportToUri(context, database, uri) }
                    .onSuccess { statusMessage = "نسخه پشتیبان با موفقیت ذخیره شد." }
                    .onFailure { statusMessage = "ساخت نسخه پشتیبان انجام نشد: ${it.message ?: "خطای نامشخص"}" }
                busy = false
            }
        }
    }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                statusMessage = null
                runCatching { FamilyBackupManager.previewFromUri(context, uri) }
                    .onSuccess {
                        pendingUri = uri
                        preview = it
                    }
                    .onFailure {
                        pendingUri = null
                        statusMessage = "فایل پشتیبان معتبر نیست: ${it.message ?: "خطای نامشخص"}"
                    }
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BackupPageTitle(
            "پشتیبان‌گیری و بازیابی خانواده",
            "یک نسخه آفلاین از اطلاعات خانواده بسازید یا اطلاعات را از یک فایل معتبر بازیابی کنید."
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = PianovaSage)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("چه اطلاعاتی ذخیره می‌شود؟", color = PianovaInk, style = MaterialTheme.typography.titleMedium)
                Text(
                    "پروفایل خانواده و هنرجویان، هدف‌ها، تنظیمات، مسیرهای آموزشی، سوابق درس و تمرین، ارزیابی‌ها و دستاوردها.",
                    color = PianovaInk,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "فایل فقط روی مقصدی که خودتان انتخاب می‌کنید ذخیره می‌شود. فایل صوتی خام، رمزها و اطلاعات ورود در نسخه پشتیبان قرار نمی‌گیرند.",
                    color = PianovaMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Button(
            onClick = { createBackup.launch("pianova-family-backup-${System.currentTimeMillis()}.json") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ساخت نسخه پشتیبان")
        }
        OutlinedButton(
            onClick = { openBackup.launch(arrayOf("application/json", "text/*", "application/*")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("بررسی و بازیابی از فایل")
        }
        statusMessage?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = PianovaWhite)
            ) {
                Text(it, modifier = Modifier.padding(16.dp), color = PianovaInk)
            }
        }
        TextButton(onClick = onBack, enabled = !busy) { Text("بازگشت") }
    }

    val selectedPreview = preview
    if (selectedPreview != null) {
        AlertDialog(
            onDismissRequest = {
                preview = null
                pendingUri = null
            },
            title = { Text("تأیید جایگزینی اطلاعات") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("فایل پشتیبان معتبر است.")
                    Text("تاریخ ساخت: ${DateFormat.getDateTimeInstance().format(Date(selectedPreview.createdAtEpochMillis))}")
                    Text("خانواده‌ها: ${selectedPreview.familyCount} · هنرجویان: ${selectedPreview.learnerCount}")
                    Text("سوابق درس: ${selectedPreview.lessonRecordCount} · جلسه‌های تمرین: ${selectedPreview.practiceSessionCount}")
                    Text("ارزیابی‌ها: ${selectedPreview.evaluationCount}")
                    Text(
                        "با ادامه، تمام اطلاعات فعلی این برنامه با اطلاعات داخل فایل جایگزین می‌شود. این کار بدون تأیید شما انجام نمی‌شود.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val uri = pendingUri ?: return@TextButton
                        preview = null
                        pendingUri = null
                        scope.launch {
                            busy = true
                            runCatching { FamilyBackupManager.restoreFromUri(context, database, uri) }
                                .onSuccess { statusMessage = "بازیابی کامل شد. اطلاعات خانواده از فایل انتخاب‌شده جایگزین شد." }
                                .onFailure { statusMessage = "بازیابی انجام نشد: ${it.message ?: "خطای نامشخص"}" }
                            busy = false
                        }
                    }
                ) { Text("جایگزینی و بازیابی") }
            },
            dismissButton = {
                TextButton(onClick = {
                    preview = null
                    pendingUri = null
                }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun BackupPageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = PianovaInk, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = PianovaMuted)
    }
}
