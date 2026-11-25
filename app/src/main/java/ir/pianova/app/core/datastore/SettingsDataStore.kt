package ir.pianova.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.map

private val Context.pianovaSettingsDataStore by preferencesDataStore(name = "pianova_settings")

@Serializable
data class PianoSettings(
    val volume: Float = 0.8f,
    val instrument: String = "acoustic_grand_piano",
    val noteLabelMode: String = "latin",
    val defaultOctave: Int = 4,
    val hapticEnabled: Boolean = false
)

/** Persistent, offline-only user preferences. */
class SettingsDataStore(context: Context) {
    private val store = context.applicationContext.pianovaSettingsDataStore

    val settings: Flow<PianoSettings> = store.data.map { preferences ->
        PianoSettings(
            volume = preferences[Keys.VOLUME] ?: 0.8f,
            instrument = preferences[Keys.INSTRUMENT] ?: "acoustic_grand_piano",
            noteLabelMode = preferences[Keys.NOTE_LABEL_MODE] ?: "latin",
            defaultOctave = preferences[Keys.DEFAULT_OCTAVE] ?: 4,
            hapticEnabled = preferences[Keys.HAPTIC_ENABLED] ?: false
        )
    }

    suspend fun currentSettings(): PianoSettings = settings.first()

    /** Replaces the complete settings snapshot during an explicitly confirmed restore. */
    suspend fun replace(settings: PianoSettings) {
        require(settings.volume in 0f..1f)
        require(settings.noteLabelMode in setOf("latin", "persian", "both"))
        require(settings.defaultOctave in 0..8)
        require(settings.instrument.isNotBlank())
        store.edit { preferences ->
            preferences.clear()
            preferences[Keys.VOLUME] = settings.volume
            preferences[Keys.INSTRUMENT] = settings.instrument
            preferences[Keys.NOTE_LABEL_MODE] = settings.noteLabelMode
            preferences[Keys.DEFAULT_OCTAVE] = settings.defaultOctave
            preferences[Keys.HAPTIC_ENABLED] = settings.hapticEnabled
        }
    }

    suspend fun updateVolume(value: Float) {
        require(value in 0f..1f)
        store.edit { it[Keys.VOLUME] = value }
    }

    suspend fun updateInstrument(value: String) {
        require(value.isNotBlank())
        store.edit { it[Keys.INSTRUMENT] = value }
    }

    suspend fun updateNoteLabelMode(value: String) {
        require(value in setOf("latin", "persian", "both"))
        store.edit { it[Keys.NOTE_LABEL_MODE] = value }
    }

    suspend fun updateDefaultOctave(value: Int) {
        require(value in 0..8)
        store.edit { it[Keys.DEFAULT_OCTAVE] = value }
    }

    suspend fun updateHapticEnabled(value: Boolean) {
        store.edit { it[Keys.HAPTIC_ENABLED] = value }
    }

    private object Keys {
        val VOLUME = floatPreferencesKey("volume")
        val INSTRUMENT = stringPreferencesKey("instrument")
        val NOTE_LABEL_MODE = stringPreferencesKey("note_label_mode")
        val DEFAULT_OCTAVE = intPreferencesKey("default_octave")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
    }
}
