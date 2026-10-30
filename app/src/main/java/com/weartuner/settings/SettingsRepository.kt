package com.weartuner.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.weartuner.dsp.PitchAlgorithm
import com.weartuner.tuning.Tunings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    val tuningId: String = Tunings.DEFAULT.id,
    val algorithm: PitchAlgorithm = PitchAlgorithm.MPM,
    /** Reference pitch for A4, 430..450 Hz. */
    val a4Hz: Int = DEFAULT_A4_HZ,
    val hapticsEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_A4_HZ = 440
        const val MIN_A4_HZ = 430
        const val MAX_A4_HZ = 450
    }
}

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    val settings: Flow<Settings> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { p ->
            Settings(
                tuningId = Tunings.byId(p[TUNING]).id,
                algorithm = PitchAlgorithm.fromId(p[ALGORITHM]),
                a4Hz = (p[A4] ?: Settings.DEFAULT_A4_HZ.toFloat()).toInt()
                    .coerceIn(Settings.MIN_A4_HZ, Settings.MAX_A4_HZ),
                hapticsEnabled = p[HAPTICS] ?: true,
            )
        }

    suspend fun setTuning(id: String) = store.edit { it[TUNING] = id }
    suspend fun setAlgorithm(algorithm: PitchAlgorithm) = store.edit { it[ALGORITHM] = algorithm.id }
    suspend fun setA4(hz: Int) = store.edit {
        it[A4] = hz.coerceIn(Settings.MIN_A4_HZ, Settings.MAX_A4_HZ).toFloat()
    }
    suspend fun setHaptics(enabled: Boolean) = store.edit { it[HAPTICS] = enabled }

    private companion object {
        val TUNING = stringPreferencesKey("tuning_id")
        val ALGORITHM = stringPreferencesKey("algorithm")
        val A4 = floatPreferencesKey("a4_hz")
        val HAPTICS = booleanPreferencesKey("haptics")
    }
}
