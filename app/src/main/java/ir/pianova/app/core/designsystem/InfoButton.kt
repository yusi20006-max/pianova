package ir.pianova.app.core.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Subtle, consistently-styled circular Information button ("ⓘ").
 *
 * Tapped, it shows a short, non-empty dialog explaining what the feature does,
 * how to use it, what to expect, and any important limitations. [message]
 * should always contain real explanation — never an empty or generic dialog.
 */
@Composable
fun InfoButton(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    contentDescription: String = "اطلاعات بیشتر",
    dismissLabel: String = "فهمیدم"
) {
    var open by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .clickable(onClick = { open = true })
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ⓘ",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
    }

    if (open) {
        // System Back dismisses the dialog (and never leaks through to navigation).
        BackHandler(enabled = true) { open = false }
        AlertDialog(
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PianovaInk,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PianovaInk,
                    modifier = Modifier.widthIn(max = 320.dp).padding(top = 2.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(dismissLabel, color = PianovaGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

/** Convenience row: a label followed by its Information button. */
@Composable
fun LabelWithInfo(
    label: String,
    infoTitle: String,
    infoMessage: String,
    modifier: Modifier = Modifier,
    labelColor: Color = PianovaInk
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
    ) {
        Text(label, color = labelColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
        InfoButton(title = infoTitle, message = infoMessage)
    }
}
