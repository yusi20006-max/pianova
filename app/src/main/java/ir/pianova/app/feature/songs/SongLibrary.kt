package ir.pianova.app.feature.songs

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class SongDocument(
    val schemaVersion: Int,
    val id: String,
    val title: String,
    val composer: String,
    val category: String,
    val difficulty: SongDifficulty,
    val tempo: Int,
    val beatsPerBar: Int = 4,
    val notes: List<SongNote>,
    val practiceSections: List<SongPracticeSection>,
    val license: SongLicense
)

@Serializable
data class SongNote(
    val midiNote: Int,
    val startBeat: Double,
    val duration: Double,
    val hand: SongHand
)

@Serializable
data class SongPracticeSection(
    val id: String,
    val title: String,
    val startBeat: Double,
    val endBeat: Double
)

@Serializable
data class SongLicense(
    val type: String,
    val name: String,
    val attribution: String,
    val sourceUrl: String? = null,
    val licenseUrl: String,
    val notes: String? = null
)

@Serializable
enum class SongDifficulty {
    @SerialName("beginner") BEGINNER,
    @SerialName("easy") EASY,
    @SerialName("intermediate") INTERMEDIATE
}

@Serializable
enum class SongHand {
    @SerialName("right") RIGHT,
    @SerialName("left") LEFT,
    @SerialName("both") BOTH
}

object SongCatalogParser {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
    }

    fun parse(source: String): SongDocument {
        val song = json.decodeFromString<SongDocument>(source)
        require(song.schemaVersion == 1) { "Unsupported song schema version: ${song.schemaVersion}" }
        require(song.id.isNotBlank()) { "Song id must not be blank" }
        require(song.title.isNotBlank() && song.composer.isNotBlank()) { "Song title and composer are required" }
        require(song.category in SONG_CATEGORIES) { "Unsupported song category: ${song.category}" }
        require(song.tempo in 30..240) { "Tempo must be between 30 and 240 BPM" }
        require(song.beatsPerBar in 1..12) { "beatsPerBar must be between 1 and 12" }
        require(song.notes.isNotEmpty()) { "A song must contain at least one note" }
        song.notes.forEachIndexed { index, note ->
            require(note.midiNote in 21..108) { "Note $index MIDI pitch is outside the supported piano range" }
            require(note.startBeat.isFinite() && note.startBeat >= 0.0) { "Note $index has an invalid startBeat" }
            require(note.duration.isFinite() && note.duration > 0.0) { "Note $index has an invalid duration" }
        }
        require(song.notes == song.notes.sortedWith(compareBy<SongNote> { it.startBeat }.thenBy { it.midiNote })) {
            "Notes must be sorted by startBeat and then midiNote"
        }
        require(song.practiceSections.isNotEmpty()) { "At least one practice section is required" }
        song.practiceSections.forEach { section ->
            require(section.id.isNotBlank() && section.title.isNotBlank()) { "Practice section id and title are required" }
            require(section.startBeat.isFinite() && section.startBeat >= 0.0) { "Invalid practice section startBeat" }
            require(section.endBeat.isFinite() && section.endBeat > section.startBeat) { "Invalid practice section endBeat" }
            require(section.endBeat <= song.notes.maxOf { it.startBeat + it.duration } + 0.0001) {
                "Practice section extends beyond the song"
            }
        }
        require(song.license.type.isNotBlank() && song.license.name.isNotBlank()) { "License type and name are required" }
        require(song.license.attribution.isNotBlank() && song.license.licenseUrl.isNotBlank()) {
            "License attribution and licenseUrl are required"
        }
        return song
    }
}

object SongCatalogRepository {
    fun load(context: Context): List<SongDocument> {
        val assetNames = context.assets.list("songs").orEmpty()
            .filter { it.endsWith(".json", ignoreCase = true) }
            .sorted()
        require(assetNames.isNotEmpty()) { "No song JSON files found in assets/songs/" }
        val songs = assetNames.map { name ->
            context.assets.open("songs/$name").bufferedReader(Charsets.UTF_8).use { reader ->
                SongCatalogParser.parse(reader.readText())
            }
        }
        require(songs.map { it.id }.distinct().size == songs.size) { "Song IDs must be unique" }
        return songs
    }
}

val SONG_CATEGORIES = listOf(
    "iranian" to "موسیقی ایرانی",
    "classical" to "موسیقی کلاسیک",
    "international" to "موسیقی بین‌المللی",
    "practice" to "تمرین‌های مبتدی"
)

@Composable
fun SongListScreen(
    progressCounts: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    var songs by remember { mutableStateOf<List<SongDocument>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var expandedSongId by remember { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(context) {
        loading = true
        loadError = null
        try {
            songs = withContext(Dispatchers.IO) { SongCatalogRepository.load(context.applicationContext) }
        } catch (error: Exception) {
            loadError = error.message ?: "بارگذاری قطعات ناموفق بود"
        } finally {
            loading = false
        }
    }

    val visibleSongs = songs.filter { selectedCategory == null || it.category == selectedCategory }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = { Text("همه") }
            )
            SONG_CATEGORIES.forEach { (id, label) ->
                FilterChip(
                    selected = selectedCategory == id,
                    onClick = { selectedCategory = id },
                    label = { Text(label) }
                )
            }
        }

        when {
            loading -> CircularProgressIndicator()
            loadError != null -> Text("خطا در بارگذاری کتابخانه: $loadError", color = MaterialTheme.colorScheme.error)
            visibleSongs.isEmpty() -> Text("در این دسته هنوز قطعه‌ای وجود ندارد.")
            else -> visibleSongs.forEach { song ->
                val expanded = expandedSongId == song.id
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    onClick = { expandedSongId = if (expanded) null else song.id }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(song.composer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AssistChip(
                                onClick = { expandedSongId = if (expanded) null else song.id },
                                label = { Text(song.difficulty.toPersianLabel()) }
                            )
                        }
                        Text(
                            "${SONG_CATEGORIES.first { it.first == song.category }.second}  •  ${song.tempo} BPM  •  ${song.notes.size} نت",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val count = progressCounts[song.id] ?: 0
                        if (count > 0) Text("تمرین‌شده: $count بار", style = MaterialTheme.typography.labelMedium)
                        if (expanded) {
                            Text("بخش‌های تمرین", fontWeight = FontWeight.SemiBold)
                            song.practiceSections.forEach { section ->
                                Text("• ${section.title}  (${section.startBeat} تا ${section.endBeat})", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("انتساب و مجوز: ${song.license.attribution}", style = MaterialTheme.typography.bodySmall)
                            Text("مجوز: ${song.license.name}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private fun SongDifficulty.toPersianLabel(): String = when (this) {
    SongDifficulty.BEGINNER -> "مبتدی"
    SongDifficulty.EASY -> "آسان"
    SongDifficulty.INTERMEDIATE -> "متوسط"
}
