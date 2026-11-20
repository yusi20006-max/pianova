package ir.pianova.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.provider.Settings
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
    pianoRunning: Boolean,
    onPlayReferenceNotes: (List<Int>, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coach = remember(context) { AndroidLessonVoiceCoach(context.applicationContext) }
    val status by coach.status.collectAsState()
    var enabled by remember { mutableStateOf(false) }
    var interactive by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    var rate by remember { mutableFloatStateOf(AndroidLessonVoiceCoach.DEFAULT_RATE) }
    var referencePlayedFor by remember { mutableStateOf<String?>(null) }

    DisposableEffect(coach) {
        onDispose { coach.shutdown() }
    }
    LaunchedEffect(step.id) { referencePlayedFor = null }

    LaunchedEffect(step.id, enabled, interactive, status, pianoRunning) {
        if (enabled && status is VoiceCoachStatus.Ready) {
            paused = false
            val listen = step as? LessonStep.Listen
            coach.speak(LessonNarrationText.forStep(step, interactive)) {
                if (listen != null && listen.midiNotes.isNotEmpty() && pianoRunning && referencePlayedFor != step.id) {
                    referencePlayedFor = step.id
                    onPlayReferenceNotes(listen.midiNotes, listen.noteDurationMs)
                }
            }
        }
    }

    LaunchedEffect(step.id, pianoRunning, enabled, status) {
        val listen = step as? LessonStep.Listen
        if (listen != null && listen.midiNotes.isNotEmpty() && pianoRunning && (!enabled || status is VoiceCoachStatus.Unavailable) &&
            referencePlayedFor != step.id
        ) {
            referencePlayedFor = step.id
            onPlayReferenceNotes(listen.midiNotes, listen.noteDurationMs)
        }
    }

    LaunchedEffect(step.id, feedback) {
        if (enabled && !feedback.isNullOrBlank() &&
            feedback != "آماده‌ای؟ شروع کن." &&
            feedback != "آماده‌ای؟ شروع کن"
        ) {
            coach.speak(LessonNarrationText.feedback(feedback))
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = {
                enabled = !enabled
                paused = false
                if (!enabled) coach.stop()
            }) { Text(if (enabled) "خاموش‌کردن مربی" else "مربی صوتی") }
            TextButton(onClick = { interactive = !interactive; paused = false }) {
                Text(if (interactive) "حالت تعاملی" else "خواندن پیوسته")
            }
        }
        if (enabled) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = {
                    if (paused) coach.resume() else coach.pause()
                    paused = !paused
                }) { Text(if (paused) "ادامه از ابتدای جمله" else "مکث") }
                TextButton(onClick = { paused = false; coach.replay() }) { Text("تکرار") }
                TextButton(onClick = {
                    rate = AndroidLessonVoiceCoach.DEFAULT_RATE
                    coach.setSpeechRate(rate)
                }) { Text("سرعت پیش‌فرض") }
            }
            Text("سرعت خواندن: ${"%.2f".format(rate)}×", style = MaterialTheme.typography.labelSmall)
            Slider(
                value = rate,
                onValueChange = { rate = it; coach.setSpeechRate(it) },
                valueRange = 0.75f..1.15f,
                modifier = Modifier.fillMaxWidth()
            )
            when (val current = status) {
                VoiceCoachStatus.Initializing -> Text("در حال آماده‌سازی صدای فارسی…", style = MaterialTheme.typography.bodySmall)
                is VoiceCoachStatus.Unavailable -> {
                    Text(current.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                        }.onFailure {
                            try {
                                context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                            } catch (_: ActivityNotFoundException) {
                                // The lesson remains usable without narration.
                            }
                        }
                    }) { Text("تنظیم یا نصب صدای فارسی") }
                }
                is VoiceCoachStatus.Ready -> Text(
                    if (current.offlineAvailable) "صدای فارسی آماده است · قابل استفاده آفلاین" else "صدای فارسی آماده است · اتصال اینترنت ممکن است لازم باشد",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
