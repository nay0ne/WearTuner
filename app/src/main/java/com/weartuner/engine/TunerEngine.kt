package com.weartuner.engine

import android.os.SystemClock
import com.weartuner.audio.AudioCapture
import com.weartuner.dsp.NoteMath
import com.weartuner.dsp.PitchAlgorithm
import com.weartuner.dsp.PitchDetector
import com.weartuner.dsp.PitchSmoother
import com.weartuner.dsp.Rms
import com.weartuner.tuning.Tuning
import com.weartuner.tuning.Tunings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/** User-adjustable parameters read by the engine on every analysis window. */
data class TunerConfig(
    val tuning: Tuning = Tunings.DEFAULT,
    /** String being tuned in preset tunings (0 = lowest); ignored for chromatic. */
    val stringIndex: Int = 0,
    val algorithm: PitchAlgorithm = PitchAlgorithm.MPM,
    val a4Hz: Float = 440f,
    val hapticsEnabled: Boolean = true,
)

/**
 * Owns the capture loop, detector, smoother and silence timer. Capture and DSP run on the
 * AudioCapture thread; UI observes [state].
 */
class TunerEngine(
    private val capture: AudioCapture,
    private val haptics: Haptics,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val _state = MutableStateFlow<TunerState>(TunerState.Stopped)
    val state: StateFlow<TunerState> = _state.asStateFlow()

    @Volatile
    var config: TunerConfig = TunerConfig()

    @Volatile
    private var stopRequested = false

    private val lifecycleLock = Any()

    // Touched only from the capture thread while it runs.
    private var detector: PitchDetector? = null
    private var detectorAlgorithm: PitchAlgorithm? = null
    private var sampleRate = 0
    private val smoother = PitchSmoother()
    private val hapticTrigger = HapticTrigger()
    private var lastValidMs = 0L

    val isRunning: Boolean get() = capture.isRunning

    fun markNeedsPermission() {
        synchronized(lifecycleLock) {
            if (!capture.isRunning) _state.value = TunerState.NeedsPermission
        }
    }

    /** Starts listening. Does nothing if already running. */
    fun start() {
        synchronized(lifecycleLock) {
            if (capture.isRunning) return
            stopRequested = false
            smoother.reset()
            hapticTrigger.signalLost()
            detector = null
            detectorAlgorithm = null
            lastValidMs = clock()
            _state.value = TunerState.Listening.EMPTY
            capture.start(listener)
        }
    }

    /** Releases the microphone. An AutoStopped or NeedsPermission state is preserved. */
    fun stop() {
        synchronized(lifecycleLock) {
            if (!capture.isRunning) return
            stopRequested = true
            capture.stop()
            _state.value = TunerState.Stopped
        }
    }

    private val listener = object : AudioCapture.Listener {
        override fun onStarted(sampleRate: Int) {
            this@TunerEngine.sampleRate = sampleRate
            lastValidMs = clock()
        }

        override fun onWindow(window: FloatArray): Boolean = process(window)

        override fun onError(message: String) {
            if (!stopRequested) _state.value = TunerState.Stopped
        }
    }

    private fun process(window: FloatArray): Boolean {
        if (stopRequested) return false
        val now = clock()
        val cfg = config

        var det = detector
        if (det == null || detectorAlgorithm != cfg.algorithm) {
            det = PitchDetector.create(cfg.algorithm, sampleRate)
            detector = det
            detectorAlgorithm = cfg.algorithm
        }

        var found = false
        if (Rms.compute(window) >= Rms.SILENCE_RMS) {
            val result = det.detect(window)
            if (result != null) {
                found = true
                lastValidMs = now
                publishReading(smoother.addSample(result.frequencyHz, now), cfg, now)
            }
        }

        if (!found && !smoother.hasSignal(now)) {
            hapticTrigger.signalLost()
            val current = _state.value
            if (current is TunerState.Listening && (current.signal || current.inTune)) {
                _state.value = current.copy(signal = false, inTune = false)
            }
        }

        if (now - lastValidMs >= AUTO_STOP_MS) {
            if (!stopRequested) _state.value = TunerState.AutoStopped
            return false
        }
        return true
    }

    private fun publishReading(hz: Float, cfg: TunerConfig, now: Long) {
        val a4 = cfg.a4Hz.toDouble()
        val target = cfg.tuning.targetAt(cfg.stringIndex, hz.toDouble(), a4)
        val midi: Int
        val rawCents: Float
        if (target != null) {
            midi = target.midi
            rawCents = target.cents.toFloat()
        } else {
            val nearest = NoteMath.nearest(hz.toDouble(), a4)
            midi = nearest.midi
            rawCents = nearest.cents.toFloat()
        }
        val cents = smoother.smoothCents(rawCents, midi).coerceIn(-50f, 50f)
        val inTune = abs(cents) <= IN_TUNE_CENTS

        if (hapticTrigger.update(cents, midi, now) && cfg.hapticsEnabled) haptics.click()

        _state.value = TunerState.Listening(
            noteName = NoteMath.noteName(midi),
            octave = NoteMath.octave(midi),
            cents = cents,
            frequencyHz = hz,
            inTune = inTune,
            signal = true,
        )
    }

    companion object {
        const val AUTO_STOP_MS = 30_000L
        const val IN_TUNE_CENTS = 3f
    }
}
