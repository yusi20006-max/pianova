package ir.pianova.app.core.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.pianova.app.BuildConfig

/**
 * Reusable About / Version dialog showing app name, version, build number and
 * short git SHA so every tested APK is identifiable.
 *
 * Readability: every piece of text uses a dark ink color on the light dialog
 * surface. Secondary text uses [PianovaMuted] (dark, sufficient contrast),
 * never `outline`/`outlineVariant` (border tokens) on a light background, and
 * never light-on-light. Links, version info, and explanation are all readable.
 */
@Composable
fun AboutDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val versionName = BuildConfig.VERSION_NAME
    val buildNumber = BuildConfig.BUILD_NUMBER
    val gitSha = BuildConfig.GIT_SHA

    BackHandler(enabled = true) { onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = PianovaInk,
        textContentColor = PianovaInk,
        title = {
            Text(
                text = "پیانوا (Pianova)",
                style = MaterialTheme.typography.titleLarge,
                color = PianovaInk,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "نسخه: $versionName (بیلد $buildNumber)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PianovaInk,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "کد نسخه: $gitSha",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PianovaMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "پیانوا برای همراهی با نورا و نیکان در مسیر یادگیری پیانو ساخته می‌شود؛ تجربه‌ای آفلاین، تعاملی و متناسب با هر هنرجو.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PianovaInk,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "این برنامه کاملاً آفلاین است و نیازی به اینترنت ندارد. اطلاعات خانواده و پیشرفت روی همین دستگاه ذخیره می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PianovaMuted,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Pianova • مجوزها و قلم‌های باندل‌شده در LICENSES.md ذکر شده‌اند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PianovaMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "بستن",
                    color = PianovaGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}
