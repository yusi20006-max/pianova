package ir.pianova.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.floor
import kotlin.math.roundToInt
import ir.pianova.app.core.database.PianovaDatabase
import ir.pianova.app.core.database.PracticeSessionEntity
import ir.pianova.app.data.persistence.ProgressRepository
import java.util.UUID
import kotlinx.coroutines.launch

private val PianoWhite = Color(0xFFF1F5F6)
private val PianoBlack = Color(0xFF10232B)
private val PianoPressed = Color(0xFF38BDF8)
private val PianoOutline = Color(0xFF78909C)
private val PianoMuted = Color(0xFF263640)

private val whitePitchClasses = setOf(0, 2, 4, 5, 7, 9, 11)
private val blackPitchClasses = setOf(1, 3, 6, 8, 10)
private val pitchNames = listOf("دو", "دو♯", "ر", "ر♯", "می", "فا", "فا♯", "سل", "سل♯", "لا", "لا♯", "سی")
private val latinPitchNames = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

private fun midiLabel(note: Int, mode: Int): String {
    if (mode == 2) return ""
    val pitch = note % 12
    val octave = note / 12 - 1
    return "${if (mode == 0) pitchNames[pitch] else latinPitchNames[pitch]}${octave}"
}

@Composable
fun PianoScreen(
    learnerId: String,
    initialVolume: Float,
    initialLabelMode: Int,
    initialFirstMidi: Int,
    onLearnerPreferenceChanged: (Float, Int, Int) -> Unit,
    status: String,
    ready: Boolean,
    running: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onNoteOn: (Int, Float) -> Boolean,
    onNoteOff: (Int) -> Unit,
    onSustain: (Boolean) -> Unit,
    onVolume: (Float) -> Unit
) {
    var firstMidi by remember(learnerId) { mutableStateOf(initialFirstMidi) }
    var sustain by remember(learnerId) { mutableStateOf(false) }
    var volume by remember(learnerId) { mutableStateOf(initialVolume) }
    var labelMode by remember(learnerId) { mutableStateOf(initialLabelMode) }
    val context = LocalContext.current
    val hapticEnabled = remember(context) { context.getSharedPreferences("pianova_settings", 0).getBoolean("haptic_enabled", true) }
    val database = remember(context) { PianovaDatabase.getInstance(context) }
    val progressRepository = remember(database) { ProgressRepository(database) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val practiceClock = remember(learnerId) { PracticeSessionClock() }
    var practiceSessionId by remember(learnerId) { mutableStateOf<String?>(null) }

    fun persistPracticeSession() {
        val sessionId = practiceSessionId ?: return
        val snapshot = practiceClock.snapshot()
        if (snapshot.notesPlayed == 0) return
        ProgressPersistenceScope.scope.launch {
            progressRepository.savePracticeSession(
                PracticeSessionEntity(
                    sessionId = sessionId,
                    learnerId = learnerId,
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

    DisposableEffect(lifecycleOwner, learnerId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> practiceClock.resume()
                Lifecycle.Event.ON_STOP -> {
                    practiceClock.pause()
                    persistPracticeSession()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            practiceClock.resume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            practiceClock.pause()
            persistPracticeSession()
        }
    }

    val trackedNoteOn: (Int, Float) -> Boolean = { note, velocity ->
        val accepted = onNoteOn(note, velocity)
        if (accepted) {
            practiceClock.recordNote()
            val sessionId = practiceSessionId ?: UUID.randomUUID().toString().also {
                practiceSessionId = it
            }
            val snapshot = practiceClock.snapshot()
            ProgressPersistenceScope.scope.launch {
                progressRepository.savePracticeSession(
                    PracticeSessionEntity(
                        sessionId = sessionId,
                        learnerId = learnerId,
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
        accepted
    }

    LaunchedEffect(running) {
        if (!running && sustain) {
            sustain = false
            onSustain(false)
        }
        if (running) onVolume(volume)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0B1422)).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("پیانوا", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(status, color = Color(0xFF9FB3C1), fontSize = 10.sp, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    TextButton(onClick = {
                        labelMode = (labelMode + 1) % 3
                        onLearnerPreferenceChanged(volume, labelMode, firstMidi)
                    }) {
                        Text(when (labelMode) { 0 -> "نام فارسی"; 1 -> "Latin"; else -> "بدون نام" }, color = Color(0xFF7DD3FC), fontSize = 11.sp)
                    }
                    TextButton(onClick = { sustain = !sustain; onSustain(sustain) }, enabled = running) {
                        Text(if (sustain) "پدال روشن" else "پدال خاموش", color = if (sustain) PianoPressed else Color.White, fontSize = 11.sp)
                    }
                    Button(
                        onClick = if (running) onStop else onStart,
                        enabled = ready,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF164E63))
                    ) { Text(if (running) "توقف صدا" else "شروع صدا", fontSize = 11.sp) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("بلندی", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                Slider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        onLearnerPreferenceChanged(volume, labelMode, firstMidi)
                        if (running) onVolume(it)
                    },
                    enabled = running,
                    valueRange = 0.05f..1f,
                    modifier = Modifier.weight(1f).height(28.dp)
                )
                Text("محدوده: ${midiLabel(firstMidi, 1)} تا ${midiLabel((firstMidi + 23).coerceAtMost(108), 1)}", color = Color.White, fontSize = 11.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("اکتاو", color = Color(0xFF9FB3C1), fontSize = 10.sp)
                    Button(
                        onClick = {
                            firstMidi = (firstMidi + 12).coerceAtMost(84)
                            onLearnerPreferenceChanged(volume, labelMode, firstMidi)
                        },
                        enabled = firstMidi < 84,
                        modifier = Modifier.size(width = 42.dp, height = 38.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) { Text("+", fontSize = 18.sp) }
                    Button(
                        onClick = {
                            firstMidi = (firstMidi - 12).coerceAtLeast(21)
                            onLearnerPreferenceChanged(volume, labelMode, firstMidi)
                        },
                        enabled = firstMidi > 21,
                        modifier = Modifier.size(width = 42.dp, height = 38.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) { Text("−", fontSize = 18.sp) }
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    PianoPlayableKeys(
                        firstMidi = firstMidi,
                        labelMode = labelMode,
                        enabled = running,
                        onNoteOn = trackedNoteOn,
                        onNoteOff = onNoteOff,
                        hapticEnabled = hapticEnabled,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("نمای کامل ۸۸ کلاویه  •  A0 تا C8", color = Color(0xFFCBD5E1), fontSize = 10.sp)
                    Text("برای انتخاب محدوده لمس کنید", color = Color(0xFF7DD3FC), fontSize = 10.sp)
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    PianoRangeOverview(
                        firstMidi = firstMidi,
                        onSelect = {
                            firstMidi = it
                            onLearnerPreferenceChanged(volume, labelMode, firstMidi)
                        },
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PianoPlayableKeys(
    firstMidi: Int,
    labelMode: Int,
    enabled: Boolean,
    onNoteOn: (Int, Float) -> Boolean,
    onNoteOff: (Int) -> Unit,
    highlightedNotes: Set<Int> = emptySet(),
    noteCount: Int = 24,
    hapticEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val currentOn by rememberUpdatedState(onNoteOn)
    val currentOff by rememberUpdatedState(onNoteOff)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentStart by rememberUpdatedState(firstMidi)
    val currentLabelMode by rememberUpdatedState(labelMode)
    val currentCount by rememberUpdatedState(noteCount.coerceIn(1, 48))
    val hapticFeedback = LocalHapticFeedback.current
    val pressedNotes = remember { mutableStateMapOf<Int, Int>() }

    Canvas(
        modifier = modifier
            .background(Color(0xFF172635), RoundedCornerShape(10.dp))
            .padding(3.dp)
            .pointerInput(enabled, firstMidi, noteCount, hapticEnabled) {
                val active = mutableMapOf<PointerId, Int>()
                fun release(note: Int) {
                    val count = pressedNotes[note] ?: return
                    if (count <= 1) {
                        pressedNotes.remove(note)
                        currentOff(note)
                    } else {
                        pressedNotes[note] = count - 1
                    }
                }
                fun press(note: Int, velocity: Float) {
                    val count = pressedNotes[note] ?: 0
                    pressedNotes[note] = count + 1
                    if (count == 0) {
                        currentOn(note, velocity)
                        if (hapticEnabled) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
                fun noteAt(x: Float, y: Float, width: Float, height: Float): Int? {
                    val start = currentStart
                    val end = (start + currentCount - 1).coerceAtMost(108)
                    val whites = (start..end).filter { it % 12 in whitePitchClasses }
                    if (whites.isEmpty() || x < 0f || x >= width || y < 0f || y >= height) return null
                    val whiteWidth = width / whites.size
                    val blackWidth = whiteWidth * 0.58f
                    val blackHeight = height * 0.62f
                    if (y <= blackHeight) {
                        for (note in start..end) {
                            if (note % 12 !in blackPitchClasses) continue
                            val previousWhite = (note - 1 downTo start).firstOrNull { it % 12 in whitePitchClasses }
                                ?: continue
                            val whiteIndex = whites.indexOf(previousWhite)
                            if (whiteIndex < 0) continue
                            val left = (whiteIndex + 1) * whiteWidth - blackWidth / 2f
                            if (x >= left && x <= left + blackWidth) return note
                        }
                    }
                    val index = floor(x / whiteWidth).toInt().coerceIn(0, whites.lastIndex)
                    return whites[index]
                }
                try {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            event.changes.forEach { change ->
                                val id = change.id
                                if (!currentEnabled) {
                                    active.remove(id)?.let(::release)
                                    return@forEach
                                }
                                if (change.pressed) {
                                    val next = noteAt(change.position.x, change.position.y, w, h)
                                    val previous = active[id]
                                    if (next != previous) {
                                        if (previous != null) release(previous)
                                        if (next != null) {
                                            press(next, if (next % 12 in blackPitchClasses) 0.72f else 0.82f)
                                            active[id] = next
                                        } else active.remove(id)
                                    }
                                } else {
                                    active.remove(id)?.let(::release)
                                }
                                change.consume()
                            }
                        }
                    }
                } finally {
                    active.values.forEach(::release)
                    active.clear()
                }
            }
    ) {
        val start = firstMidi
        val end = (start + noteCount.coerceIn(1, 48) - 1).coerceAtMost(108)
        val whites = (start..end).filter { it % 12 in whitePitchClasses }
        if (whites.isEmpty()) return@Canvas
        val whiteWidth = size.width / whites.size
        val blackWidth = whiteWidth * 0.58f
        val blackHeight = size.height * 0.62f
        val whitePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(241, 245, 246)
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = (size.height * 0.105f).coerceAtLeast(10f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        whites.forEachIndexed { index, note ->
            val left = index * whiteWidth
            val pressed = pressedNotes.containsKey(note)
            drawRoundRect(
                color = when { pressed -> PianoPressed; note in highlightedNotes -> Color(0xFFDCE9D9); else -> PianoWhite },
                topLeft = Offset(left + 1f, 1f),
                size = Size(whiteWidth - 2f, size.height - 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f)
            )
            drawRoundRect(
                color = PianoOutline.copy(alpha = 0.7f),
                topLeft = Offset(left + 1f, 1f),
                size = Size(whiteWidth - 2f, size.height - 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f),
                style = Stroke(1f)
            )
            val label = midiLabel(note, labelMode)
            if (label.isNotEmpty()) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(label, left + whiteWidth / 2f, size.height - 9f, whitePaint)
                }
            }
        }
        for (note in start..end) {
            if (note % 12 !in blackPitchClasses) continue
            val previousWhite = (note - 1 downTo start).firstOrNull { it % 12 in whitePitchClasses } ?: continue
            val whiteIndex = whites.indexOf(previousWhite)
            if (whiteIndex < 0) continue
            val left = (whiteIndex + 1) * whiteWidth - blackWidth / 2f
            drawRoundRect(
                color = when { note in pressedNotes -> PianoPressed; note in highlightedNotes -> Color(0xFF557A62); else -> PianoBlack },
                topLeft = Offset(left, 0f),
                size = Size(blackWidth, blackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
            )
            drawRoundRect(
                color = PianoOutline,
                topLeft = Offset(left, 0f),
                size = Size(blackWidth, blackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                style = Stroke(1f)
            )
        }
    }
}

@Composable
private fun PianoRangeOverview(
    firstMidi: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .background(Color(0xFF172635), RoundedCornerShape(7.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                val selected = (21 + fraction * (108 - 21)).roundToInt()
                                onSelect(selected.coerceIn(21, 84))
                                change.consume()
                            }
                        }
                    }
                }
            }
    ) {
        val total = 88f
        val unit = size.width / total
        for (i in 0 until 88) {
            val note = 21 + i
            val black = note % 12 in blackPitchClasses
            drawRect(
                color = if (black) Color(0xFF334155) else Color(0xFFCBD5E1),
                topLeft = Offset(i * unit, if (black) 0f else size.height * 0.18f),
                size = Size((unit - 0.5f).coerceAtLeast(0.5f), if (black) size.height * 0.62f else size.height * 0.82f)
            )
        }
        val left = ((firstMidi - 21) / 88f) * size.width
        val width = (24f / 88f) * size.width
        drawRoundRect(
            color = PianoPressed.copy(alpha = 0.25f),
            topLeft = Offset(left, 0f),
            size = Size(width.coerceAtMost(size.width - left), size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
        )
        drawRoundRect(
            color = PianoPressed,
            topLeft = Offset(left, 0f),
            size = Size(width.coerceAtMost(size.width - left), size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
            style = Stroke(2f)
        )
    }
}
