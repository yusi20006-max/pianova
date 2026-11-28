package ir.pianova.app.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.pianova.app.core.audio.ReferencePlaybackController
import ir.pianova.app.core.audio.ReferencePlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Compact reference-playback controls: Play/Pause, Stop, Replay, progress.
 * Renders nothing actionable when there is no valid playback data.
 */
@Composable
fun ReferencePlaybackPanel(
    controller: ReferencePlaybackController,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit = {},
    onStop: () -> Unit = {},
    title: String = "شنیدن نمونه",
    infoMessage: String? = null
) {
    val progress by controller.progress.collectAsState()
    val hasContent = controller.hasContent

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = PianovaInk, modifier = Modifier.weight(1f, fill = false))
            if (infoMessage != null) InfoButton(title = title, message = infoMessage)
        }

        if (!hasContent) {
            Text("برای این مورد داده‌ی پخش معتبر نیست.", style = MaterialTheme.typography.bodySmall, color = PianovaMuted)
            return@Column
        }

        LinearProgressIndicator(
            progress = progress.fraction,
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = PianovaGreen,
            trackColor = PianovaSage
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (progress.state == ReferencePlaybackState.PLAYING) {
                OutlinedButton(onClick = { scope.launch { controller.pause() } }, modifier = Modifier.weight(1f)) { Text("توقف موقت") }
            } else {
                OutlinedButton(onClick = {
                    onPlay()
                    scope.launch { controller.play() }
                }, modifier = Modifier.weight(1f)) { Text("پخش") }
            }
            OutlinedButton(onClick = { scope.launch { controller.stop(); onStop() } }, modifier = Modifier.weight(1f)) { Text("توقف") }
            OutlinedButton(onClick = { onPlay(); scope.launch { controller.replay() } }, modifier = Modifier.weight(1f)) { Text("از نو") }
        }
    }
}
