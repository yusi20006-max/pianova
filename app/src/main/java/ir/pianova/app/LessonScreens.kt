package ir.pianova.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.pianova.app.core.designsystem.PianovaBorder
import ir.pianova.app.core.designsystem.PianovaGreen
import ir.pianova.app.core.designsystem.PianovaInk
import ir.pianova.app.core.designsystem.PianovaMuted
import ir.pianova.app.core.designsystem.PianovaSage
import ir.pianova.app.core.designsystem.PianovaWhite
import ir.pianova.app.core.midi.PianoInput
import ir.pianova.app.data.lesson.LessonRepository
import ir.pianova.app.domain.evaluation.EvaluationEngine
import ir.pianova.app.domain.evaluation.EvaluationSession
import ir.pianova.app.domain.evaluation.EvaluationState
import ir.pianova.app.domain.evaluation.EvaluationStatus
import ir.pianova.app.domain.lesson.flattenedSteps
import ir.pianova.app.domain.lesson.previewNotesAt
import ir.pianova.app.domain.lesson.targetMidiNotes
import ir.pianova.app.domain.model.Lesson
import ir.pianova.app.domain.model.LessonStep
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun LessonListScreen(
    repository: LessonRepository,
    onStartChapter: (lessonId: String, chapterId: String) -> Unit
) {
    val lessons = remember(repository) {
        repository.listLessonIds().mapNotNull { id ->
            runCatching { repository.getLesson(id) }.getOrNull()
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (lessons.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("مسیر یادگیری", style = MaterialTheme.typography.headlineSmall, color = PianovaInk, fontWeight = FontWeight.Bold)
                Text("هنوز درسی برای نمایش آماده نیست.", color = PianovaMuted, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("مسیر یادگیری من", style = MaterialTheme.typography.headlineSmall, color = PianovaInk, fontWeight = FontWeight.Bold)
                        Text("درس‌ها را فصل‌به‌فصل پیش ببر و هر نت را با پیانو تجربه کن.", color = PianovaMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                lessons.forEach { lesson ->
                    item(key = "lesson-${lesson.id}") {
                        Text(
                            lesson.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = PianovaGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        lesson.description?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = PianovaMuted)
                        }
                    }
                    items(lesson.chapters, key = { "${lesson.id}-${it.id}" }) { chapter ->
                        Card(
                            onClick = { onStartChapter(lesson.id, chapter.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = PianovaWhite),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PianovaBorder)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(chapter.title, style = MaterialTheme.typography.titleMedium, color = PianovaInk, fontWeight = FontWeight.SemiBold)
                                chapter.description?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = PianovaMuted)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${chapter.steps.size} مرحله آموزشی", style = MaterialTheme.typography.labelMedium, color = PianovaMuted)
                                    Text("شروع فصل  ←", style = MaterialTheme.typography.labelLarge, color = PianovaGreen)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LessonPlayerScreen(
    lesson: Lesson,
    startChapterId: String,
    pianoInput: PianoInput,
    pianoReady: Boolean,
    pianoRunning: Boolean,
    onPianoStart: () -> Unit,
    onNoteOn: (Int, Float) -> Boolean,
    onNoteOff: (Int) -> Unit,
    onPlayReferenceNotes: (List<Int>, Long) -> Unit,
    onExit: () -> Unit
) {
    val entries = remember(lesson) { lesson.flattenedSteps() }
    val startIndex = remember(lesson.id, startChapterId, entries) {
        entries.indexOfFirst { it.chapterId == startChapterId }.takeIf { it >= 0 } ?: 0
    }
    var stepIndex by remember(lesson.id, startChapterId) { mutableIntStateOf(startIndex) }
    var completedStepIds by remember(lesson.id, startChapterId) { mutableStateOf(emptySet<String>()) }
    var showCompleteDialog by remember(lesson.id, startChapterId) { mutableStateOf(false) }
    var playedReferenceStep by remember(lesson.id) { mutableStateOf<String?>(null) }

    val entry = entries.getOrNull(stepIndex)
    if (entry == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("این درس هنوز مرحله‌ای ندارد.", color = PianovaInk)
            TextButton(onClick = onExit) { Text("بازگشت") }
        }
        return
    }

    val step = entry.step
    val playable = step is LessonStep.PlayNote || step is LessonStep.PlaySequence || step is LessonStep.PlayChord
    val evaluationEngine = remember(pianoInput) { EvaluationEngine(pianoInput) }
    val coroutineScope = rememberCoroutineScope()
    var session by remember(lesson.id, step.id) { mutableStateOf<EvaluationSession?>(null) }

    DisposableEffect(step.id) {
        val created = if (playable) evaluationEngine.start(step, coroutineScope) else null
        session = created
        onDispose { created?.cancel() }
    }

    val fallbackState = remember(step.id) {
        MutableStateFlow(
            EvaluationState(
                status = EvaluationStatus.READY,
                expectedNotes = step.targetMidiNotes(),
                receivedNotes = emptyList(),
                completedUnits = 0,
                totalUnits = step.targetMidiNotes().size,
                feedbackFa = "آماده‌ای؟ شروع کن."
            )
        )
    }
    val evaluationFlow = session?.state ?: fallbackState
    val evaluation by evaluationFlow.collectAsState()

    LaunchedEffect(lesson.id, pianoReady) {
        if (pianoReady && !pianoRunning) onPianoStart()
    }
    LaunchedEffect(step.id, pianoRunning) {
        val listen = step as? LessonStep.Listen
        if (listen != null && pianoRunning && listen.midiNotes.isNotEmpty() && playedReferenceStep != step.id) {
            playedReferenceStep = step.id
            onPlayReferenceNotes(listen.midiNotes, listen.noteDurationMs)
        }
    }

    val targetNotes = when (step) {
        is LessonStep.Explanation -> entries.previewNotesAt(stepIndex)
        is LessonStep.Listen -> step.midiNotes
        is LessonStep.PlayNote, is LessonStep.PlaySequence, is LessonStep.PlayChord ->
            evaluation.expectedNotes.ifEmpty { step.targetMidiNotes() }
    }
    val keyboardStart = ((targetNotes.minOrNull() ?: 60) / 12 * 12).coerceIn(21, 96)
    val keyboardCount = ((targetNotes.maxOrNull() ?: keyboardStart) - keyboardStart + 1)
        .coerceAtLeast(12).coerceAtMost(24)
    val progress = (stepIndex + 1).toFloat() / entries.size.coerceAtLeast(1)
    val canAdvance = !playable || evaluation.isComplete

    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(lesson.title, style = MaterialTheme.typography.titleLarge, color = PianovaInk, fontWeight = FontWeight.Bold)
                    Text(entry.chapterTitle, style = MaterialTheme.typography.bodySmall, color = PianovaMuted)
                }
                TextButton(onClick = onExit) { Text("خروج از درس") }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("مرحله ${stepIndex + 1} از ${entries.size}", style = MaterialTheme.typography.labelMedium, color = PianovaMuted)
                    Text("${(progress * 100).toInt()}٪", style = MaterialTheme.typography.labelMedium, color = PianovaGreen, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(7.dp),
                    color = PianovaGreen,
                    trackColor = PianovaSage
                )
            }

            Card(
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PianovaWhite),
                border = androidx.compose.foundation.BorderStroke(1.dp, PianovaBorder)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        when (step) {
                            is LessonStep.Explanation -> "یاد بگیر"
                            is LessonStep.Listen -> "گوش کن"
                            else -> "حالا تو بنواز"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = PianovaGreen
                    )
                    Text(step.title, style = MaterialTheme.typography.headlineSmall, color = PianovaInk, fontWeight = FontWeight.Bold)
                    when (step) {
                        is LessonStep.Explanation -> {
                            Text(step.body, style = MaterialTheme.typography.bodyLarge, color = PianovaInk)
                            if (targetNotes.isNotEmpty()) {
                                Text("این کلید را روی پیانو پیدا کن.", style = MaterialTheme.typography.bodyMedium, color = PianovaMuted)
                                Text(targetNotes.joinToString("، ") { midiNoteLabel(it) }, style = MaterialTheme.typography.titleMedium, color = PianovaGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                        is LessonStep.Listen -> {
                            Text(step.prompt ?: "به صدای نت‌ها با دقت گوش کن.", style = MaterialTheme.typography.bodyLarge, color = PianovaInk)
                            if (step.midiNotes.isNotEmpty()) {
                                Text("نمونه صوتی با صدای پیانوی داخلی پخش می‌شود.", style = MaterialTheme.typography.bodySmall, color = PianovaMuted)
                                TextButton(
                                    onClick = { onPlayReferenceNotes(step.midiNotes, step.noteDurationMs) },
                                    enabled = pianoRunning
                                ) { Text("پخش دوباره نمونه") }
                            } else {
                                Text("برای این مرحله هنوز نمونه صوتی قابل پخش تعریف نشده است.", style = MaterialTheme.typography.bodySmall, color = PianovaMuted)
                            }
                        }
                        is LessonStep.PlayNote -> {
                            Text("نت هدف را بنواز: ${midiNoteLabel(step.midiNote)}", style = MaterialTheme.typography.bodyLarge, color = PianovaInk)
                            if (step.repetitionCount > 1) Text("تعداد تکرار: ${step.repetitionCount}", color = PianovaMuted)
                        }
                        is LessonStep.PlaySequence -> {
                            Text("نت‌ها را به ترتیب بنواز.", style = MaterialTheme.typography.bodyLarge, color = PianovaInk)
                            Text("سرعت پیشنهادی: ${step.tempoBpm} ضرب در دقیقه", style = MaterialTheme.typography.bodySmall, color = PianovaMuted)
                        }
                        is LessonStep.PlayChord -> {
                            Text("همه نت‌ها را هم‌زمان نگه دار.", style = MaterialTheme.typography.bodyLarge, color = PianovaInk)
                            Text(step.midiNotes.joinToString("  +  ") { midiNoteLabel(it) }, style = MaterialTheme.typography.titleMedium, color = PianovaGreen)
                        }
                    }
                }
            }

            if (playable) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = when (evaluation.status) {
                        EvaluationStatus.CORRECT -> PianovaSage
                        EvaluationStatus.WRONG_NOTE, EvaluationStatus.MISSING_NOTE, EvaluationStatus.EXTRA_NOTE -> Color(0xFFF8E4E1)
                        else -> Color(0xFFEDEFEA)
                    }
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(evaluation.feedbackFa, style = MaterialTheme.typography.bodyMedium, color = PianovaInk, fontWeight = FontWeight.SemiBold)
                        if (evaluation.totalUnits > 1) {
                            Text("${evaluation.completedUnits} از ${evaluation.totalUnits} نت", style = MaterialTheme.typography.labelSmall, color = PianovaMuted)
                        }
                    }
                }
            }

            if (!pianoRunning) {
                Button(
                    onClick = onPianoStart,
                    enabled = pianoReady,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (pianoReady) "فعال‌کردن صدای پیانو" else "در حال آماده‌سازی پیانو…") }
            }

            androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                PianoPlayableKeys(
                    firstMidi = keyboardStart,
                    labelMode = 0,
                    enabled = pianoRunning && playable,
                    onNoteOn = onNoteOn,
                    onNoteOff = onNoteOff,
                    highlightedNotes = targetNotes.toSet(),
                    noteCount = keyboardCount,
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onExit, modifier = Modifier.weight(1f)) { Text("بعداً ادامه می‌دهم") }
                Button(
                    onClick = {
                        completedStepIds = completedStepIds + step.id
                        if (stepIndex < entries.lastIndex) {
                            stepIndex++
                        } else {
                            showCompleteDialog = true
                        }
                    },
                    enabled = canAdvance,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (stepIndex == entries.lastIndex) "اتمام درس" else "مرحله بعد")
                }
            }
        }
    }

    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = { Text("درس را به پایان رساندی!") },
            text = {
                Text("همه ${completedStepIds.size} مرحله این درس در این جلسه تکمیل شد. پیشرفت بلندمدت پس از آماده‌شدن ذخیره‌سازی محلی ثبت خواهد شد.")
            },
            confirmButton = {
                Button(onClick = onExit) { Text("بازگشت به درس‌ها") }
            },
            dismissButton = {
                TextButton(onClick = {
                    stepIndex = 0
                    completedStepIds = emptySet()
                    showCompleteDialog = false
                }) { Text("مرور دوباره") }
            }
        )
    }
}

private fun midiNoteLabel(note: Int): String {
    val persian = listOf("دو", "دو دیز", "ر", "ر دیز", "می", "فا", "فا دیز", "سل", "سل دیز", "لا", "لا دیز", "سی")
    val latin = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    val pitch = note.mod(12)
    val octave = note / 12 - 1
    return "${persian[pitch]} (${latin[pitch]}$octave)"
}
