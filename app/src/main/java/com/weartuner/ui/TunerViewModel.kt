package com.weartuner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weartuner.audio.AudioCapture
import com.weartuner.dsp.PitchAlgorithm
import com.weartuner.engine.Haptics
import com.weartuner.engine.TunerConfig
import com.weartuner.engine.TunerEngine
import com.weartuner.engine.TunerState
import com.weartuner.settings.Settings
import com.weartuner.settings.SettingsRepository
import com.weartuner.tuning.Tunings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TunerViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = TunerEngine(AudioCapture(application), Haptics(application))
    private val repository = SettingsRepository(application)

    val state: StateFlow<TunerState> = engine.state

    val settings: StateFlow<Settings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _stringIndex = MutableStateFlow(0)

    /** String being tuned in preset tunings (0 = lowest). Not persisted; resets on tuning change. */
    val stringIndex: StateFlow<Int> = _stringIndex.asStateFlow()

    /** True unless capture ended because of an error; drives resume on ON_START. */
    private var wantListening = true

    init {
        viewModelScope.launch {
            combine(settings, _stringIndex, ::Pair).collect { (s, index) ->
                engine.config = TunerConfig(
                    tuning = Tunings.byId(s.tuningId),
                    stringIndex = index,
                    algorithm = s.algorithm,
                    a4Hz = s.a4Hz.toFloat(),
                    hapticsEnabled = s.hapticsEnabled,
                )
            }
        }
    }

    fun nextString() = _stringIndex.update { Tunings.byId(settings.value.tuningId).nextIndex(it) }
    fun previousString() = _stringIndex.update { Tunings.byId(settings.value.tuningId).previousIndex(it) }

    /** Activity ON_START. Resumes listening unless auto-stopped or permission is missing. */
    fun onHostStart(hasPermission: Boolean) {
        if (!hasPermission) {
            engine.markNeedsPermission()
            return
        }
        if (wantListening && engine.state.value != TunerState.AutoStopped) engine.start()
    }

    /** Activity ON_STOP. Always releases the microphone. */
    fun onHostStop() {
        engine.stop()
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            wantListening = true
            engine.start()
        } else {
            engine.markNeedsPermission()
        }
    }

    /** User tapped the screen while stopped (permission already held). */
    fun onTapToTune() {
        wantListening = true
        engine.start()
    }

    fun setTuning(id: String) {
        if (id != settings.value.tuningId) _stringIndex.value = 0
        viewModelScope.launch { repository.setTuning(id) }
    }
    fun setAlgorithm(a: PitchAlgorithm) = viewModelScope.launch { repository.setAlgorithm(a) }
    fun setA4(hz: Int) = viewModelScope.launch { repository.setA4(hz) }
    fun setHaptics(enabled: Boolean) = viewModelScope.launch { repository.setHaptics(enabled) }

    override fun onCleared() {
        engine.stop()
    }
}
