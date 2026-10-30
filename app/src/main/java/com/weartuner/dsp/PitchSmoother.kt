package com.weartuner.dsp

import kotlin.math.abs
import kotlin.math.ln

/**
 * Median-of-N filter on Hz followed by an exponential moving average on cents.
 *
 * If the median moves by more than [jumpCents] the window and the EMA are reset, so a genuine
 * note change is followed quickly while single outliers are rejected by the median.
 * Allocation-free after construction. Not thread-safe.
 */
class PitchSmoother(
    private val windowSize: Int = 5,
    private val alpha: Float = 0.35f,
    private val holdMs: Long = 400L,
    private val jumpCents: Double = 50.0,
) {
    private val ring = FloatArray(windowSize)
    private val scratch = FloatArray(windowSize)
    private var count = 0
    private var head = 0
    private var lastMedian = Float.NaN
    private var lastSampleMs = Long.MIN_VALUE

    private var ema = 0f
    private var emaValid = false
    private var emaKey = Int.MIN_VALUE

    /** Adds a raw detection and returns the median frequency in Hz. */
    fun addSample(hz: Float, nowMs: Long): Float {
        lastSampleMs = nowMs
        push(hz)
        var median = median()
        if (!lastMedian.isNaN()) {
            val moved = abs(1200.0 * ln((median / lastMedian).toDouble()) / LN2)
            if (moved > jumpCents) {
                count = 0
                head = 0
                push(hz)
                median = hz
                emaValid = false
            }
        }
        lastMedian = median
        return median
    }

    /**
     * EMA over cents. [targetKey] identifies the note being measured against (e.g. its MIDI
     * number); a change restarts the average.
     */
    fun smoothCents(cents: Float, targetKey: Int): Float {
        if (!emaValid || targetKey != emaKey) {
            ema = cents
            emaKey = targetKey
            emaValid = true
        } else {
            ema += alpha * (cents - ema)
        }
        return ema
    }

    /** True while the last valid detection is no older than the hold time. */
    fun hasSignal(nowMs: Long): Boolean =
        lastSampleMs != Long.MIN_VALUE && nowMs - lastSampleMs <= holdMs

    fun reset() {
        count = 0
        head = 0
        lastMedian = Float.NaN
        lastSampleMs = Long.MIN_VALUE
        emaValid = false
    }

    private fun push(hz: Float) {
        ring[head] = hz
        head = (head + 1) % windowSize
        if (count < windowSize) count++
    }

    private fun median(): Float {
        for (i in 0 until count) scratch[i] = ring[i]
        // insertion sort over the first `count` entries
        for (i in 1 until count) {
            val v = scratch[i]
            var j = i - 1
            while (j >= 0 && scratch[j] > v) {
                scratch[j + 1] = scratch[j]
                j--
            }
            scratch[j + 1] = v
        }
        return if (count % 2 == 1) scratch[count / 2]
        else 0.5f * (scratch[count / 2 - 1] + scratch[count / 2])
    }

    private companion object {
        val LN2 = ln(2.0)
    }
}
