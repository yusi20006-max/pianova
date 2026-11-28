package ir.pianova.app.core.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Consistent Back affordance used across every screen that has a meaningful
 * previous destination.
 *
 * Placement is intentionally uniform: a leading icon + short label, aligned to
 * the start of the top bar. Callers wire [onBack] to `navController.popBackStack()`
 * (or the appropriate overlay/dialog dismissal) so on-screen Back and Android
 * system Back stay in sync. See also the matching `BackHandler`.
 */
@Composable
fun PianovaBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "بازگشت",
    contentDescription: String = "بازگشت به صفحه قبل",
    tint: Color = MaterialTheme.colorScheme.primary
) {
    TextButton(
        onClick = onBack,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(4.dp))
        Text(label, color = tint, style = MaterialTheme.typography.labelLarge)
    }
}
