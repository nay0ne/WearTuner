package com.weartuner.dsp

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * YIN (de Cheveigne & Kawahara, 2002): difference function, cumulative mean normalisation,
 * absolute threshold and parabolic interpolation. Stops computing lags as soon as the first
 * dip is resolved to save CPU.
 */
class YinDetector(
    override val sampleRate: Int,
    override val windowSize: Int = Dsp.WINDOW_SIZE,
    private val threshold: Float = 0.15f,
) : PitchDetector {

    private val minLag = max(2, floor(sampleRate / Dsp.MAX_HZ.toDouble()).toInt())
    private val maxLag = min(ceil(sampleRate / Dsp.MIN_HZ.toDouble()).toInt(), windowSize / 2)
    private val integrationWindow = windowSize - maxLag
    private val dn = FloatArray(maxLag + 2)

    override fun detect(samples: FloatArray): PitchResult? {
        require(samples.size >= windowSize) { "Need at least $windowSize samples" }

        dn[0] = 1f
        var running = 0.0
        var estimate = -1
        var tau = 1
        while (tau <= maxLag) {
            var d = 0.0
            for (j in 0 until integrationWindow) {
                val diff = samples[j].toDouble() - samples[j + tau]
                d += diff * diff
            }
            running += d
            dn[tau] = if (running > 1e-12) (d * tau / running).toFloat() else 1f

            if (estimate < 0) {
                if (tau >= minLag && dn[tau] < threshold) estimate = tau
            } else if (dn[tau] < dn[estimate]) {
                estimate = tau
            } else {
                break
            }
            tau++
        }
        if (estimate < 0) return null

        var period = estimate.toFloat()
        // Right neighbour exists only if it was computed (estimate < tau).
        if (estimate < tau && estimate >= 1) {
            val a = dn[estimate - 1]
            val b = dn[estimate]
            val c = dn[estimate + 1]
            val denom = a - 2f * b + c
            if (denom > 0f) {
                period += (0.5f * (a - c) / denom).coerceIn(-1f, 1f)
            }
        }
        if (period <= 0f) return null
        val confidence = (1f - dn[estimate]).coerceIn(0f, 1f)
        return PitchResult(sampleRate / period, confidence)
    }
}
