package ir.pianova.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.pianova.app.core.voice.AndroidLessonVoiceCoach
import ir.pianova.app.core.voice.LessonNarrationText
import ir.pianova.app.core.voice.VoiceCoachStatus
import ir.pianova.app.domain.model.LessonStep

@Composable
fun LessonVoiceControls(
    step: LessonStep,
    feedback: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coach = remember(context) { AndroidLessonVoiceCoach(context.applicationContext) }
    val status by coach.status.collectAsState()
    var interactive by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var rate by remember { mutableFloatStateOf(AndroidLessonVoiceCoach.DEFAULT_RATE) }

    DisposableEffect(coach) {
        onDispose { coach.shutdown() }
    }

    // A step change always cancels the previous utterance. Nothing is spoken
    // automatically: the learner explicitly starts each narration.
    LaunchedEffect(step.id) {
        coach.stop()
        playing = false
    }

    val canSpeak = status is VoiceCoachStatus.Ready || status is VoiceCoachStatus.Failed
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val parts = buildList {
                        add(LessonNarrationText.forStep(step, interactive))
                        if (!feedback.isNullOrBlank()) add(LessonNarrationText.feedback(feedback))
                    }
                    playing = coach.speak(parts.joinToString(separator = ". ")) { playing = false }
                },
                enabled = canSpeak
            ) { Text(if (playing) "پخش دوباره راهنما" else "پخش راهنمای صوتی") }
            TextButton(
                onClick = {
                    coach.stop()
                    playing = false
                },
                enabled = playing || status is VoiceCoachStatus.Failed
            ) { Text("توقف") }
        }

        TextButton(onClick = { interactive = !interactive }) {
            Text(if (interactive) "حالت تعاملی" else "خواندن پیوسته")
        }

        when (val current = status) {
            VoiceCoachStatus.Initializing -> Text(
                "در حال آماده‌سازی صدای فارسی…",
                style = MaterialTheme.typography.bodySmall
            )
            is VoiceCoachStatus.Unavailable -> {
                Text(
                    current.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                    }.onFailure {
                        try {
                            context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                        } catch (_: ActivityNotFoundException) {
                            // The lesson remains readable and playable without narration.
                        }
                    }
                }) { Text("تنظیم یا نصب صدای فارسی") }
            }
            is VoiceCoachStatus.Failed -> Text(
                current.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            is VoiceCoachStatus.Ready -> {
                Text(
                    if (current.offlineAvailable) "صدای فارسی آماده است · قابل استفاده آفلاین"
                    else "صدای فارسی آماده است · برای پخش به اینترنت نیاز دارد",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("سرعت خواندن: ${"%.2f".format(rate)}×", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = rate,
                    onValueChange = { rate = it; coach.setSpeechRate(it) },
                    valueRange = 0.75f..1.15f,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = {
                    rate = AndroidLessonVoiceCoach.DEFAULT_RATE
                    coach.setSpeechRate(rate)
                }) { Text("بازنشانی سرعت") }
            }
        }
    }
}
