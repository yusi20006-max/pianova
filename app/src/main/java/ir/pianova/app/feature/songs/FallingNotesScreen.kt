package ir.pianova.app.feature.songs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import ir.pianova.app.PianoPlayableKeys
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.core.database.SongProgressEntity
import ir.pianova.app.core.midi.PianoEvent
import ir.pianova.app.core.midi.PianoInput
import ir.pianova.app.data.persistence.ProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.compose.runtime.withFrameNanos
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs

private val FallingBackground = Color(0xFF0B1422)
private val FallingRightNote = Color(0xFF38BDF8)
private val FallingLeftNote = Color(0xFFFBBF24)
private val FallingBothNote = Color(0xFFA78BFA)

/** Frame-clock-driven song practice, aligned to the same keyboard geometry as free play. */
@Composable
fun FallingNotesScreen(
    learnerId: String,
    songId: String,
    pianoReady: Boolean,
    pianoRunning: Boolean,
    onPianoStart: () -> Unit,
    onPianoStop: () -> Unit,
    onNoteOn: (Int, Float) -> Boolean,
    onNoteOff: (Int) -> Unit,
    pianoInput: PianoInput,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { PianovaDatabase.getInstance(context) }
    val progressRepository = remember(database) { ProgressRepository(database) }
    var song by remember(songId) { mutableStateOf<SongDocument?>(null) }
    var loading by remember(songId) { mutableStateOf(true) }
    var loadError by remember(songId) { mutableStateOf<String?>(null) }

    LaunchedEffect(songId, context) {
        loading = true
        loadError = null
        try {
            song = withContext(Dispatchers.IO) {
                SongCatalogRepository.load(context.applicationContext).firstOrNull { it.id == songId }
            }
            if (song == null) loadError = "این قطعه در کتابخانه پیدا نشد."
        } catch (error: Exception) {
            loadError = error.message ?: "بارگذاری قطعه ناموفق بود."
        } finally {
            loading = false
        }
    }

    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        song == null -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(loadError ?: "قطعه در دسترس نیست.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onExit) { Text("بازگشت به کتابخانه") }
        }
        else -> FallingNotesSession(
            learnerId = learnerId,
            song = song!!,
            progressRepository = progressRepository,
            pianoReady = pianoReady,
            pianoRunning = pianoRunning,
            onPianoStart = onPianoStart,
            onPianoStop = onPianoStop,
            onNoteOn = onNoteOn,
            onNoteOff = onNoteOff,
            pianoInput = pianoInput,
            onExit = onExit
        )
    }
}

@Composable
private fun FallingNotesSession(
    learnerId: String,
    song: SongDocument,
    progressRepository: ProgressRepository,
    pianoReady: Boolean,
    pianoRunning: Boolean,
    onPianoStart: () -> Unit,
    onPianoStop: () -> Unit,
    onNoteOn: (Int, Float) -> Boolean,
    onNoteOff: (Int) -> Unit,
    pianoInput: PianoInput,
    onExit: () -> Unit
) {
    var selectedHand by remember(song.id) { mutableStateOf(FallingNotesHand.BOTH) }
    var tempoPercent by remember(song.id) { mutableFloatStateOf(100f) }
    var waitForNote by remember(song.id) { mutableStateOf(false) }
    var waitingForNote by remember(song.id) { mutableStateOf(false) }
    var isPlaying by remember(song.id) { mutableStateOf(false) }
    var wantsPlayback by remember(song.id) { mutableStateOf(false) }
    var isFinished by remember(song.id) { mutableStateOf(false) }
    var isSaving by remember(song.id) { mutableStateOf(false) }
    var isSaved by remember(song.id) { mutableStateOf(false) }
    var correctCount by remember(song.id) { mutableIntStateOf(0) }
    var wrongCount by remember(song.id) { mutableIntStateOf(0) }
    var feedback by remember(song.id) { mutableStateOf("برای شروع، دکمه پخش را بزن.") }
    var highlightedNotes by remember(song.id) { mutableStateOf(emptySet<Int>()) }
    val matchedIndices = remember(song.id) { mutableStateListOf<Int>() }
    val playheadBeat = remember(song.id) { mutableFloatStateOf(0f) }

    val selectedNotes = remember(song, selectedHand) {
        FallingNotesEngine.notesForHand(song, selectedHand)
    }
    val keyRange = remember(selectedNotes) {
        if (selectedNotes.isNotEmpty()) FallingNotesEngine.midiRange(selectedNotes) else null
    }
    val totalBeats = remember(selectedNotes) { FallingNotesEngine.totalBeats(selectedNotes) }
    val effectiveTempo = song.tempo * (tempoPercent / 100f)

    suspend fun saveCompletedRun() {
        if (isSaved || isSaving) return
        isSaving = true
        try {
            val accuracy = FallingNotesEngine.accuracy(correctCount, selectedNotes.size)
            val completedSections = FallingNotesEngine.completedSectionIds(
                song, selectedNotes, matchedIndices.toSet()
            )
            val encodedSections = Json.encodeToString(ListSerializer(String.serializer()), completedSections)
            val previous = progressRepository.observeSongProgress(learnerId).first()
                .firstOrNull { it.songId == song.id }
            progressRepository.saveSongProgress(
                SongProgressEntity(
                    learnerId = learnerId,
                    songId = song.id,
                    completedSectionsJson = encodedSections,
                    bestAccuracy = maxOf(previous?.bestAccuracy ?: 0f, accuracy),
                    lastPlayedAtEpochMillis = System.currentTimeMillis(),
                    playCount = (previous?.playCount ?: 0) + 1
                )
            )
            isSaved = true
            feedback = "پایان قطعه؛ دقت اجرا ${(accuracy * 100).toInt()}٪ بود."
        } catch (error: Exception) {
            feedback = "ذخیره نتیجه ناموفق بود: ${error.message ?: "خطای نامشخص"}"
        } finally {
            isSaving = false
        }
    }

    fun handlePlayedNote(midiNote: Int) {
        if (isFinished || !isPlaying) return
        val pending = selectedNotes.indices.filterNot { it in matchedIndices }
        val candidateIndices = if (waitForNote && waitingForNote) {
            val nextBeat = pending.minOfOrNull { selectedNotes[it].startBeat }
            if (nextBeat == null) emptyList() else pending.filter {
                abs(selectedNotes[it].startBeat - nextBeat) < 0.0001
            }
        } else {
            pending.filter { abs(selectedNotes[it].startBeat - playheadBeat.floatValue) <= 0.55f }
        }
        val matchedIndex = candidateIndices.firstOrNull { selectedNotes[it].midiNote == midiNote }
        if (matchedIndex == null) {
            wrongCount++
            feedback = "این نت با نت برجسته‌شده هماهنگ نیست؛ دوباره امتحان کن."
            return
        }

        matchedIndices.add(matchedIndex)
        correctCount++
        feedback = "درست بود؛ ادامه بده."
        if (waitForNote && waitingForNote) {
            val groupBeat = selectedNotes[matchedIndex].startBeat
            val group = selectedNotes.indices.filter {
                abs(selectedNotes[it].startBeat - groupBeat) < 0.0001
            }
            if (group.all { it in matchedIndices }) {
                waitingForNote = false
                highlightedNotes = emptySet()
                feedback = "آفرین؛ ادامه قطعه پخش می‌شود."
            }
        }
    }

    LaunchedEffect(pianoRunning, wantsPlayback) {
        if (wantsPlayback && pianoRunning && !isFinished) isPlaying = true
    }

    LaunchedEffect(pianoRunning) {
        if (!pianoRunning && isPlaying) {
            isPlaying = false
            wantsPlayback = false
            feedback = "صدای پیانو متوقف شد؛ برای ادامه دوباره پخش را بزن."
        }
    }

    LaunchedEffect(pianoInput, isPlaying) {
        if (isPlaying) {
            pianoInput.events.collect { event ->
                if (event is PianoEvent.NoteOn) handlePlayedNote(event.midiNote)
            }
        }
    }

    LaunchedEffect(isPlaying, waitForNote, selectedNotes, song.id, effectiveTempo) {
        var previousFrame = 0L
        while (isPlaying && !isFinished) {
            val frameTime = withFrameNanos { it }
            if (previousFrame != 0L && !waitingForNote) {
                val deltaSeconds = (frameTime - previousFrame).coerceAtLeast(0L) / 1_000_000_000f
                val nextBeat = playheadBeat.floatValue + deltaSeconds * effectiveTempo / 60f
                val nextPending = selectedNotes.indices.firstOrNull { it !in matchedIndices }
                val nextNoteBeat = nextPending?.let { selectedNotes[it].startBeat.toFloat() }
                if (waitForNote && nextNoteBeat != null && nextBeat >= nextNoteBeat) {
                    playheadBeat.floatValue = maxOf(playheadBeat.floatValue, nextNoteBeat)
                    val group = selectedNotes.indices.filter {
                        it !in matchedIndices && abs(selectedNotes[it].startBeat.toFloat() - nextNoteBeat) < 0.0001f
                    }
                    highlightedNotes = group.map { selectedNotes[it].midiNote }.toSet()
                    waitingForNote = true
                    feedback = "نت برجسته‌شده را روی کلاویه بنواز."
                } else {
                    playheadBeat.floatValue = nextBeat
                }

                if (playheadBeat.floatValue >= totalBeats + 0.25f) {
                    playheadBeat.floatValue = totalBeats + 0.25f
                    isFinished = true
                    wantsPlayback = false
                    saveCompletedRun()
                    isPlaying = false
                    onPianoStop()
                }
            }
            previousFrame = frameTime
        }
    }

    val range = keyRange
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier.fillMaxSize().background(FallingBackground).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(song.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${song.composer} · ${song.tempo} BPM", color = Color(0xFF9FB3C1), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onExit) { Text("بازگشت", color = Color(0xFF7DD3FC)) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    FallingNotesHand.RIGHT to "دست راست",
                    FallingNotesHand.LEFT to "دست چپ",
                    FallingNotesHand.BOTH to "هر دو دست"
                ).forEach { (hand, label) ->
                    FilterChip(
                        selected = selectedHand == hand,
                        onClick = {
                            if (!isPlaying && matchedIndices.isEmpty() && playheadBeat.floatValue == 0f) selectedHand = hand
                        },
                        enabled = !isPlaying && matchedIndices.isEmpty() && playheadBeat.floatValue == 0f,
                        label = { Text(label) }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("سرعت ${tempoPercent.toInt()}٪", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = tempoPercent,
                    onValueChange = { tempoPercent = it },
                    valueRange = 50f..100f,
                    modifier = Modifier.weight(1f),
                    enabled = !isFinished
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("انتظار برای نواختن نت", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = waitForNote,
                    onCheckedChange = {
                        waitForNote = it
                        waitingForNote = false
                        highlightedNotes = emptySet()
                    },
                    enabled = !isFinished
                )
            }

            if (range != null) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Canvas(
                        modifier = Modifier.fillMaxWidth().weight(1f)
                            .background(Color(0xFF172635), RoundedCornerShape(10.dp))
                            .padding(3.dp)
                    ) {
                        val beat = playheadBeat.floatValue
                        val pixelsPerBeat = size.height * 0.19f
                        val hitLineY = size.height * 0.88f
                        selectedNotes.forEach { note ->
                            val centerFraction = FallingNotesEngine.keyCenterFraction(note.midiNote, range) ?: return@forEach
                            val widthFraction = FallingNotesEngine.keyWidthFraction(note.midiNote, range)
                            val noteHeight = (note.duration.toFloat() * pixelsPerBeat).coerceAtLeast(10f)
                            val noteHitY = hitLineY - (note.startBeat.toFloat() - beat) * pixelsPerBeat
                            val top = noteHitY - noteHeight
                            if (top <= size.height && noteHitY >= 0f) {
                                val color = when (note.hand) {
                                    SongHand.RIGHT -> FallingRightNote
                                    SongHand.LEFT -> FallingLeftNote
                                    SongHand.BOTH -> FallingBothNote
                                }
                                drawRoundRect(
                                    color = color,
                                    topLeft = Offset(centerFraction * size.width - widthFraction * size.width / 2f, top),
                                    size = Size(widthFraction * size.width, noteHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                                )
                            }
                        }
                        drawLine(
                            color = Color(0xFFE2E8F0),
                            start = Offset(0f, hitLineY),
                            end = Offset(size.width, hitLineY),
                            strokeWidth = 2f
                        )
                    }

                    PianoPlayableKeys(
                        firstMidi = range.firstMidi,
                        labelMode = 0,
                        enabled = pianoRunning && !isFinished,
                        onNoteOn = onNoteOn,
                        onNoteOff = onNoteOff,
                        highlightedNotes = highlightedNotes,
                        noteCount = range.noteCount,
                        hapticEnabled = false,
                        modifier = Modifier.fillMaxWidth().height(112.dp)
                    )
                }
            } else {
                Text("برای این انتخاب دستی نتی وجود ندارد.", color = Color.White)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("درست: $correctCount از ${selectedNotes.size}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Text("اشتباه: $wrongCount", color = Color(0xFFFCA5A5), style = MaterialTheme.typography.labelSmall)
                    if (isFinished) Text("جاافتاده: ${(selectedNotes.size - correctCount).coerceAtLeast(0)}", color = Color(0xFFFDE68A), style = MaterialTheme.typography.labelSmall)
                }
                Text(if (isFinished) "پایان تمرین" else if (waitingForNote) "در انتظار نت" else if (isPlaying) "در حال پخش" else "متوقف", color = Color(0xFF7DD3FC), style = MaterialTheme.typography.labelMedium)
            }
            Text(feedback, color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (isPlaying) {
                            isPlaying = false
                            wantsPlayback = false
                        } else if (!isFinished && pianoReady) {
                            wantsPlayback = true
                            if (pianoRunning) isPlaying = true else onPianoStart()
                        }
                    },
                    enabled = pianoReady && !isFinished,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isPlaying) "توقف" else if (pianoRunning) "ادامه" else "شروع تمرین")
                }
                TextButton(onClick = onExit) { Text("خروج از تمرین") }
            }
            if (isSaving) Text("در حال ذخیره نتیجه…", color = Color(0xFF9FB3C1), style = MaterialTheme.typography.labelSmall)
            if (isSaved) Text("پیشرفت این قطعه ذخیره شد.", color = Color(0xFF86EFAC), style = MaterialTheme.typography.labelSmall)
        }
    }
}
