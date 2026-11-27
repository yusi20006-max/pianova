package ir.pianova.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope\nimport androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.os.SystemClock
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
import ir.pianova.app.core.midi.PianoInput
import ir.pianova.app.core.midi.PracticeInputRouter
import ir.pianova.app.core.midi.PracticeInputMode
import ir.pianova.app.core.audio.AcousticPianoInput
import ir.pianova.app.core.midi.PianoEvent
import ir.pianova.app.data.lesson.LessonRepository
import ir.pianova.app.core.database.EvaluationEvidenceEntity
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.core.database.PracticeSessionEntity
import ir.pianova.app.data.persistence.ProgressRepository
import ir.pianova.app.data.lesson.SkillAttemptStore
import ir.pianova.app.domain.evaluation.EvaluationEngine
import ir.pianova.app.domain.evaluation.EvaluationSession
import ir.pianova.app.domain.evaluation.EvaluationState
import ir.pianova.app.domain.evaluation.EvaluationStatus
import ir.pianova.app.domain.lesson.flattenedSteps
import ir.pianova.app.domain.lesson.SkillAttempt
import ir.pianova.app.domain.lesson.previewNotesAt
import ir.pianova.app.domain.lesson.targetMidiNotes
import ir.pianova.app.domain.model.Lesson
import ir.pianova.app.domain.model.LessonAudience
import ir.pianova.app.domain.model.titleFor
import ir.pianova.app.domain.model.descriptionFor
import ir.pianova.app.domain.model.displayTextFor
import ir.pianova.app.domain.model.bodyFor
import ir.pianova.app.domain.model.promptFor
import ir.pianova.app.domain.model.LessonStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import java.util.UUID

internal object ProgressPersistenceScope {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("PianovaProgress", "Progress persistence failed", throwable)
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1) + exceptionHandler)
}

@Composable
fun LessonListScreen(
    repository: LessonRepository,
    audience: LessonAudience,
    onAudienceChange: (LessonAudience) -> Unit,
    onStartChapter: (lessonId: String, chapterId: String) -> Unit
) {
    val lessons = remember(repository) {
        repository.listLessonIds().mapNotNull { id ->
            runCatching { repository.getLesson(id) }.getOrNull()
        }
    }
    val chapterGroups = remember(lessons) {
        lessons.flatMap { lesson ->
            lesson.chapters.map { chapter -> lesson to chapter }
        }.groupBy { (_, chapter) -> chapter.title }
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
                        Text("محتوای آموزشی برای:", style = MaterialTheme.typography.labelLarge, color = PianovaMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (audience == LessonAudience.CHILDREN) Button(onClick = { onAudienceChange(LessonAudience.CHILDREN) }) { Text("کودکان") }
                            else TextButton(onClick = { onAudienceChange(LessonAudience.CHILDREN) }) { Text("کودکان") }
                            if (audience == LessonAudience.TEENS) Button(onClick = { onAudienceChange(LessonAudience.TEENS) }) { Text("نوجوانان") }
                            else TextButton(onClick = { onAudienceChange(LessonAudience.TEENS) }) { Text("نوجوانان") }
                            if (audience == LessonAudience.ADULTS) Button(onClick = { onAudienceChange(LessonAudience.ADULTS) }) { Text("بزرگسالان") }
                            else TextButton(onClick = { onAudienceChange(LessonAudience.ADULTS) }) { Text("بزرگسالان") }
                        }
                    }
                }
                chapterGroups.forEach { (chapterTitle, entries) ->
                    item(key = "chapter-$chapterTitle") {
                        Text(
                            chapterTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = PianovaGreen,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    items(entries, key = { (lesson, chapter) -> "${lesson.id}-${chapter.id}" }) { (lesson, chapter) ->
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
                                Text(lesson.titleFor(audience), style = MaterialTheme.typography.titleMedium, color = PianovaInk, fontWeight = FontWeight.SemiBold)
                                lesson.descriptionFor(audience)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = PianovaMuted)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${chapter.steps.size} مرحله آموزشی", style = MaterialTheme.typography.labelMedium, color = PianovaMuted)
                                    Text("شروع درس  ←", style = MaterialTheme.typography.labelLarge, color = PianovaGreen)
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
    learnerId: String,
    lesson: Lesson,
    audience: LessonAudience,
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
    val context = LocalContext.current
    val practiceScope = rememberCoroutineScope()
    val acousticInput = remember(context) { AcousticPianoInput(context) }
    val practiceRouter = remember(pianoInput, acousticInput) { PracticeInputRouter(pianoInput, acousticInput, practiceScope) }
    val attemptStore = remember(context, learnerId) { SkillAttemptStore(context.applicationContext, learnerId) }
    val database = remember(context) { PianovaDatabase.getInstance(context) }
    val progressRepository = remember(database) { ProgressRepository(database) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val masterySessionId = remember(lesson.id, startChapterId) { UUID.randomUUID().toString() }
    val practiceClock = remember(masterySessionId) { PracticeSessionClock() }
    LaunchedEffect(masterySessionId, pianoInput) {
        pianoInput.events.collect { event ->
            if (event is PianoEvent.NoteOn) practiceClock.recordNote()
        }
    }

    DisposableEffect(lifecycleOwner, masterySessionId) {
        fun persistPracticeSession() {
            val snapshot = practiceClock.snapshot()
            ProgressPersistenceScope.scope.launch {
                progressRepository.savePracticeSession(
                    PracticeSessionEntity(
                        sessionId = masterySessionId,
                        learnerId = learnerId,
                        lessonId = lesson.id,
                        startedAtEpochMillis = snapshot.startedAtEpochMillis,
                        endedAtEpochMillis = snapshot.endedAtEpochMillis,
                        durationMillis = snapshot.activeDurationMillis,
                        notesPlayed = snapshot.notesPlayed,
                        correctNotes = snapshot.correctNotes,
                        wrongNotes = snapshot.wrongNotes,
                        wasInterrupted = snapshot.wasInterrupted
                    )
                )
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> practiceClock.resume()
                Lifecycle.Event.ON_STOP -> {
                    practiceClock.pause(interrupted = true)
                    persistPracticeSession()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) practiceClock.resume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            practiceClock.pause()
            persistPracticeSession()
        }
    }
    val entries = remember(lesson) { lesson.flattenedSteps() }
    val startIndex = remember(lesson.id, startChapterId, entries) {
        entries.indexOfFirst { it.chapterId == startChapterId }.takeIf { it >= 0 } ?: 0
    }
    var stepIndex by remember(lesson.id, startChapterId) { mutableIntStateOf(startIndex) }
    var retryAttempt by remember(lesson.id, startChapterId) { mutableIntStateOf(0) }
    var completedStepIds by remember(lesson.id, startChapterId) { mutableStateOf(emptySet<String>()) }
    var showCompleteDialog by remember(lesson.id, startChapterId) { mutableStateOf(false) }
    var masteryRefresh by remember(lesson.id) { mutableIntStateOf(0) }

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
    val evaluationEngine = remember(practiceRouter) { EvaluationEngine(practiceRouter) }
    val coroutineScope = practiceScope
    var microphoneMode by remember(lesson.id) { mutableStateOf(false) }
    var microphonePermissionMessage by remember(lesson.id) { mutableStateOf<String?>(null) }\n    val microphoneModeState by rememberUpdatedState(microphoneMode)
    val microphoneStatus by acousticInput.status.collectAsState()
    val microphonePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && acousticInput.start()) {
            microphonePermissionMessage = null
            practiceRouter.setMode(PracticeInputMode.MICROPHONE)
            microphoneMode = true
        } else if (!granted) {
            microphoneMode = false
            microphonePermissionMessage = "اجازهٔ میکروفون داده نشد؛ برای شنیدن پیانو، مجوز را فعال کن."
        }
    }
    var session by remember(lesson.id, step.id) { mutableStateOf<EvaluationSession?>(null) }

    DisposableEffect(lifecycleOwner, acousticInput, practiceRouter) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && microphoneModeState) {
                acousticInput.stop()
                practiceRouter.setMode(PracticeInputMode.MIDI)
                microphoneMode = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            acousticInput.stop()
            practiceRouter.setMode(PracticeInputMode.MIDI)
            acousticInput.close()
            practiceRouter.close()
        }
    }

    LaunchedEffect(microphoneStatus.listening) {
        if (!microphoneStatus.listening && microphoneMode) {
            practiceRouter.setMode(PracticeInputMode.MIDI)
            microphoneMode = false
        }
    }

    DisposableEffect(step.id, retryAttempt)
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
    var scaffoldingLevel by remember(lesson.id, step.id) {
        mutableIntStateOf(lesson.pedagogy?.visualGuidance?.defaultLevel ?: 2)
    }
    var scaffoldingSuggestion by remember(lesson.id, step.id) { mutableStateOf<Int?>(null) }
    var consecutiveErrors by remember(lesson.id) { mutableIntStateOf(0) }
    var consecutiveSuccesses by remember(lesson.id) { mutableIntStateOf(0) }
    var activityVersion by remember(lesson.id, step.id) { mutableIntStateOf(0) }
    var answerRevealingHintUsed by remember(lesson.id, step.id, retryAttempt) {
        val initialLevel = lesson.pedagogy?.visualGuidance?.defaultLevel ?: 2
        val initialHint = step.scaffolding?.let { scaffold ->
            when (initialLevel) {
                1 -> scaffold.level1
                3 -> scaffold.level3
                else -> scaffold.level2
            }
        }
        mutableStateOf(initialHint?.revealsAnswer == true)
    }
    val answerHintEvidence = remember(lesson.id, step.id, retryAttempt) { MutableStateFlow(answerRevealingHintUsed) }

    LaunchedEffect(lesson.id, step.id) {
        ProgressPersistenceScope.scope.launch {
            val existing = progressRepository.observeLesson(learnerId, lesson.id).first()
            if (existing?.isCompleted != true) {
                val savedIds = existing?.completedStepIdsJson?.let { raw ->
                    runCatching { Json.decodeFromString(ListSerializer(String.serializer()), raw) }
                        .getOrDefault(emptyList())
                }.orEmpty()
                val now = System.currentTimeMillis()
                progressRepository.saveLessonProgress(
                    ir.pianova.app.core.database.LessonProgressEntity(
                        progressId = existing?.progressId ?: UUID.randomUUID().toString(),
                        learnerId = learnerId,
                        lessonId = lesson.id,
                        completedStepIdsJson = Json.encodeToString(ListSerializer(String.serializer()), savedIds.distinct().sorted()),
                        isCompleted = false,
                        lastAccessedAtEpochMillis = now,
                        completedAtEpochMillis = null,
                        currentStepId = step.id
                    )
                )
            }
        }
    }

    LaunchedEffect(step.id, session) {
        val activeSession = session ?: return@LaunchedEffect
        ProgressPersistenceScope.scope.launch {
            activeSession.transitions.collect { transition ->
            if (transition.status in setOf(
                    EvaluationStatus.CORRECT,
                    EvaluationStatus.WRONG_NOTE,
                    EvaluationStatus.MISSING_NOTE,
                    EvaluationStatus.EXTRA_NOTE
                ) && (transition.receivedNotes.isNotEmpty() || transition.status == EvaluationStatus.MISSING_NOTE)
            ) {
                practiceClock.recordEvaluation(transition.status)
                val evidenceId = "${masterySessionId}:${activeSession.attemptId}:${transition.receivedNotes.size}:${transition.status.name}"
                progressRepository.saveEvaluationEvidence(
                    EvaluationEvidenceEntity(
                        evaluationId = evidenceId,
                        learnerId = learnerId,
                        sessionId = masterySessionId,
                        lessonId = lesson.id,
                        stepId = step.id,
                        skillId = step.skillId,
                        occurredAtEpochMillis = System.currentTimeMillis(),
                        status = transition.status.name,
                        expectedMidiNotesCsv = transition.expectedNotes.ifEmpty { step.targetMidiNotes() }.joinToString(","),
                        receivedMidiNotesCsv = transition.receivedNotes.joinToString(","),
                        answerRevealingHintUsed = answerHintEvidence.value
                    )
                )
            }
            }
        }
    }

    LaunchedEffect(step.id, evaluation.isComplete) {
        val skillId = step.skillId
        if (evaluation.isComplete && !skillId.isNullOrBlank()) {
            attemptStore.record(
                SkillAttempt(
                    attemptId = UUID.randomUUID().toString(),
                    sessionId = masterySessionId,
                    lessonId = lesson.id,
                    skillId = skillId,
                    successful = true,
                    answerRevealingHintUsed = answerRevealingHintUsed,
                    occurredAtEpochMillis = System.currentTimeMillis()
                )
            )
            masteryRefresh++
        }
    }

    LaunchedEffect(step.id, evaluation.status, evaluation.receivedNotes.size) {
        val settings = lesson.pedagogy?.visualGuidance
        when (evaluation.status) {
            EvaluationStatus.CORRECT -> {
                consecutiveSuccesses++
                consecutiveErrors = 0
                if (settings?.automaticSuggestionsEnabled == true &&
                    consecutiveSuccesses >= settings.suggestLessHelpAfterConsecutiveSuccesses
                ) scaffoldingSuggestion = (scaffoldingLevel + 1).coerceAtMost(3)
            }
            EvaluationStatus.WRONG_NOTE, EvaluationStatus.MISSING_NOTE, EvaluationStatus.EXTRA_NOTE -> {
                consecutiveErrors++
                consecutiveSuccesses = 0
                if (settings?.automaticSuggestionsEnabled == true &&
                    consecutiveErrors >= settings.suggestMoreHelpAfterConsecutiveErrors
                ) scaffoldingSuggestion = (scaffoldingLevel - 1).coerceAtLeast(1)
            }
            else -> Unit
        }
    }

    LaunchedEffect(step.id, activityVersion, evaluation.isComplete) {
        val seconds = lesson.pedagogy?.visualGuidance?.inactivitySuggestionSeconds ?: 8
        delay(seconds * 1000L)
        if (!evaluation.isComplete &&
            lesson.pedagogy?.visualGuidance?.automaticSuggestionsEnabled == true &&
            scaffoldingSuggestion == null
        ) scaffoldingSuggestion = (scaffoldingLevel - 1).coerceAtLeast(1)
    }

    LaunchedEffect(lesson.id, pianoReady) {
        if (pianoReady && !pianoRunning) onPianoStart()
    }
    val targetNotes = if (step.skillId != null) emptyList() else when (step) {
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
    val requiredSkillIds = entries.mapNotNull { it.step.skillId }.toSet()
    val masteredSkillIds = remember(lesson.id, masteryRefresh, requiredSkillIds) {
        lesson.masteryCriteria?.let { criteria ->
            attemptStore.masteredSkillIds(lesson.id, requiredSkillIds, criteria)
        }.orEmpty()
    }

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
                    Text(lesson.titleFor(audience), style = MaterialTheme.typography.titleLarge, color = PianovaInk, fontWeight = FontWeight.Bold)
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
                    LessonVoiceControls(
                        step = step,
                        audience = audience,
                        feedback = if (playable) evaluation.feedbackFa else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        when (step) {
                            is LessonStep.Explanation -> "یاد بگیر"
                            is LessonStep.Listen -> "گوش کن"
                            else -> "حالا تو بنواز"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = PianovaGreen
                    )
                    Text(step.displayTextFor(audience), style = MaterialTheme.typography.headlineSmall, color = PianovaInk, fontWeight = FontWeight.Bold)
                    step.scaffolding?.let { scaffolding ->
                        val selectedHint = when (scaffoldingLevel) {
                            1 -> scaffolding.level1
                            3 -> scaffolding.level3
                            else -> scaffolding.level2
                        }
                        if (lesson.pedagogy?.visualGuidance?.manualSelectionEnabled != false) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                (1..3).forEach { level ->
                                    TextButton(onClick = {
                                        scaffoldingLevel = level
                                        scaffoldingSuggestion = null
                                        val hint = when (level) {
                                            1 -> scaffolding.level1
                                            3 -> scaffolding.level3
                                            else -> scaffolding.level2
                                        }
                                        if (hint.revealsAnswer) {
                                            answerRevealingHintUsed = true
                                            answerHintEvidence.value = true
                                        }
                                    }) { Text("راهنمایی $level") }
                                }
                            }
                        }
                        Text(selectedHint.hintText, style = MaterialTheme.typography.bodyMedium, color = PianovaMuted)
                        scaffoldingSuggestion?.let { suggested ->
                            TextButton(onClick = {
                                scaffoldingLevel = suggested
                                scaffoldingSuggestion = null
                                val hint = when (suggested) {
                                    1 -> scaffolding.level1
                                    3 -> scaffolding.level3
                                    else -> scaffolding.level2
                                }
                                if (hint.revealsAnswer) {
                                    answerRevealingHintUsed = true
                                    answerHintEvidence.value = true
                                }
                            }) { Text("پیشنهاد تغییر به سطح $suggested") }
                        }
                    }
                    when (step) {
                        is LessonStep.Explanation -> {
                            Text(step.bodyFor(audience).orEmpty(), style = MaterialTheme.typography.bodyLarge, color = PianovaInk)
                            if (targetNotes.isNotEmpty()) {
                                Text("این کلید را روی پیانو پیدا کن.", style = MaterialTheme.typography.bodyMedium, color = PianovaMuted)
                                Text(targetNotes.joinToString("، ") { midiNoteLabel(it) }, style = MaterialTheme.typography.titleMedium, color = PianovaGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                        is LessonStep.Listen -> {
                            Text(step.promptFor(audience) ?: "به صدای نت‌ها با دقت گوش کن.", style = MaterialTheme.typography.bodyLarge, color = PianovaInk)
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
                            if (step.skillId == null) {
                                Text("نت هدف را بنواز: ${midiNoteLabel(step.midiNote)}", style = MaterialTheme.typography.bodyLarge, color = PianovaInk)
                            }
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
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            if (microphoneMode || microphoneStatus.listening) {
                                acousticInput.stop()
                                practiceRouter.setMode(PracticeInputMode.MIDI)
                                microphoneMode = false
                            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                if (acousticInput.start()) {
                                    practiceRouter.setMode(PracticeInputMode.MICROPHONE)
                                    microphoneMode = true
                                }
                            } else {
                                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (microphoneMode || microphoneStatus.listening) "توقف شنود میکروفون" else "تمرین با شنیدن صدای پیانو")
                    }
                    if (microphoneStatus.listening) {
                        Text("● در حال شنیدن پیانو", color = PianovaGreen, fontWeight = FontWeight.Bold)
                        Text(microphoneStatus.messageFa, color = PianovaMuted)
                        Text("اطمینان تشخیص: ${(microphoneStatus.confidence * 100).toInt()}٪", color = PianovaMuted, style = MaterialTheme.typography.labelSmall)
                    } else if (microphonePermissionMessage != null) {
                        Text(microphonePermissionMessage!!, color = PianovaMuted)
                    } else if (microphoneStatus.messageFa != "شنود صوتی خاموش است.") {
                        Text(microphoneStatus.messageFa, color = PianovaMuted)
                    }
                    Text("صدا فقط به‌صورت زنده و در حافظهٔ موقت پردازش می‌شود؛ ضبط یا ارسال نمی‌شود.", color = PianovaMuted, style = MaterialTheme.typography.labelSmall)
                }
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
                    onNoteOn = { note, velocity ->
                        activityVersion++
                        onNoteOn(note, velocity)
                    },
                    onNoteOff = { note ->
                        activityVersion++
                        onNoteOff(note)
                    },
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
                TextButton(onClick = onExit, modifier = Modifier.weight(1f)) { Text("خروج از درس") }
                Button(
                    onClick = {
                        val updatedInSession = completedStepIds + step.id
                        completedStepIds = updatedInSession
                        ProgressPersistenceScope.scope.launch {
                            val existing = progressRepository.observeLesson(learnerId, lesson.id).first()
                            val previousIds = existing?.completedStepIdsJson?.let { raw ->
                                runCatching {
                                    Json.decodeFromString(ListSerializer(String.serializer()), raw)
                                }.getOrDefault(emptyList())
                            }.orEmpty()
                            val allCompletedIds = (previousIds + updatedInSession).toSet()
                            val lessonCompleted = existing?.isCompleted == true || entries.all { it.step.id in allCompletedIds }
                            val now = System.currentTimeMillis()
                            progressRepository.saveLessonProgress(
                                ir.pianova.app.core.database.LessonProgressEntity(
                                    progressId = existing?.progressId ?: UUID.randomUUID().toString(),
                                    learnerId = learnerId,
                                    lessonId = lesson.id,
                                    completedStepIdsJson = Json.encodeToString(ListSerializer(String.serializer()), allCompletedIds.sorted()),
                                    isCompleted = lessonCompleted,
                                    lastAccessedAtEpochMillis = now,
                                    completedAtEpochMillis = if (lessonCompleted) existing?.completedAtEpochMillis ?: now else null,
                                    currentStepId = if (lessonCompleted) null else entries.getOrNull(stepIndex + 1)?.step?.id
                                )
                            )
                        }
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
                Text("در این جلسه ${completedStepIds.size} مرحله را تکمیل کردی. تسلط ثبت‌شده: ${masteredSkillIds.size} از ${requiredSkillIds.size} مهارت.")
            },
            confirmButton = {
                Button(onClick = onExit) { Text("بازگشت به درس‌ها") }
            },
            dismissButton = {
                TextButton(onClick = {
                    stepIndex = 0
                    retryAttempt++
                    completedStepIds = emptySet()
                    showCompleteDialog = false
                }) { Text("مرور دوباره") }
            }
        )
    }
}


internal data class PracticeClockSnapshot(
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val activeDurationMillis: Long,
    val wasInterrupted: Boolean,
    val notesPlayed: Int,
    val correctNotes: Int,
    val wrongNotes: Int
)

/** Measures foreground time only; elapsedRealtime avoids wall-clock adjustments. */
internal class PracticeSessionClock(private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() }) {
    private val createdAtEpochMillis = System.currentTimeMillis()
    private var startedAtEpochMillis: Long? = null
    private var activeSinceElapsedMillis: Long? = null
    private var accumulatedActiveMillis = 0L
    private var wasInterrupted = false
    private var notesPlayed = 0
    private var correctNotes = 0
    private var wrongNotes = 0

    private var isForeground = false

    @Synchronized fun resume() {
        isForeground = true
    }

    @Synchronized fun recordNote() {
        notesPlayed++
        if (startedAtEpochMillis == null) startedAtEpochMillis = System.currentTimeMillis()
        if (!isForeground) return
        val now = elapsedRealtime()
        activeSinceElapsedMillis?.let { previous ->
            accumulatedActiveMillis += (now - previous).coerceIn(0L, IDLE_TIMEOUT_MILLIS)
        }
        activeSinceElapsedMillis = now
    }

    @Synchronized fun recordEvaluation(status: EvaluationStatus) {
        when (status) {
            EvaluationStatus.CORRECT -> correctNotes++
            EvaluationStatus.WRONG_NOTE, EvaluationStatus.MISSING_NOTE, EvaluationStatus.EXTRA_NOTE -> wrongNotes++
            else -> Unit
        }
    }

    @Synchronized fun pause(interrupted: Boolean = false) {
        if (!isForeground) return
        val now = elapsedRealtime()
        activeSinceElapsedMillis?.let { previous ->
            accumulatedActiveMillis += (now - previous).coerceIn(0L, IDLE_TIMEOUT_MILLIS)
        }
        activeSinceElapsedMillis = null
        isForeground = false
        wasInterrupted = wasInterrupted || interrupted
    }

    @Synchronized fun snapshot(): PracticeClockSnapshot {
        val currentActive = activeSinceElapsedMillis?.let {
            (elapsedRealtime() - it).coerceIn(0L, IDLE_TIMEOUT_MILLIS)
        } ?: 0L
        return PracticeClockSnapshot(
            startedAtEpochMillis = startedAtEpochMillis ?: createdAtEpochMillis,
            endedAtEpochMillis = if (isForeground) null else System.currentTimeMillis(),
            activeDurationMillis = accumulatedActiveMillis + currentActive,
            wasInterrupted = wasInterrupted,
            notesPlayed = notesPlayed,
            correctNotes = correctNotes,
            wrongNotes = wrongNotes
        )
    }

    private companion object {
        const val IDLE_TIMEOUT_MILLIS = 60_000L
    }
}

private fun midiNoteLabel(note: Int): String {
    val persian = listOf("دو", "دو دیز", "ر", "ر دیز", "می", "فا", "فا دیز", "سل", "سل دیز", "لا", "لا دیز", "سی")
    val latin = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    val pitch = note.mod(12)
    val octave = note / 12 - 1
    return "${persian[pitch]} (${latin[pitch]}$octave)"
}
