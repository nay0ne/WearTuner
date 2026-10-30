package com.weartuner.dsp

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * McLeod Pitch Method (McLeod & Wyvill, 2005) using a direct time-domain normalised square
 * difference function restricted to the lag range of interest.
 */
class MpmDetector(
    override val sampleRate: Int,
    override val windowSize: Int = Dsp.WINDOW_SIZE,
    private val cutoff: Float = 0.93f,
    private val minConfidence: Float = 0.8f,
) : PitchDetector {

    private val minLag = max(2, floor(sampleRate / Dsp.MAX_HZ.toDouble()).toInt())
    private val maxLag = min(ceil(sampleRate / Dsp.MIN_HZ.toDouble()).toInt(), windowSize / 2)
    private val nsdf = FloatArray(maxLag + 2)
    private val keyMaxima = IntArray(maxLag / 2 + 2)

    override fun detect(samples: FloatArray): PitchResult? {
        val n = windowSize
        require(samples.size >= n) { "Need at least $n samples" }

        // m(0) = 2 * sum(x^2); m(tau) = m(tau-1) - x[tau-1]^2 - x[n-tau]^2
        var m = 0.0
        for (j in 0 until n) {
            val s = samples[j].toDouble()
            m += s * s
        }
        m *= 2.0
        if (m < 1e-9) return null

        for (tau in 0..maxLag) {
            if (tau > 0) {
                val a = samples[tau - 1].toDouble()
                val b = samples[n - tau].toDouble()
                m -= a * a + b * b
            }
            var r = 0.0
            val end = n - tau
            for (j in 0 until end) {
                r += samples[j].toDouble() * samples[j + tau]
            }
            nsdf[tau] = if (m > 1e-9) (2.0 * r / m).toFloat() else 0f
        }

        // Skip the initial positive region around tau = 0.
        var pos = 0
        while (pos < maxLag && nsdf[pos] > 0f) pos++

        var count = 0
        var highest = 0f
        while (pos < maxLag) {
            while (pos < maxLag && nsdf[pos] <= 0f) pos++
            if (pos >= maxLag) break
            var best = pos
            while (pos < maxLag && nsdf[pos] > 0f) {
                if (nsdf[pos] > nsdf[best]) best = pos
                pos++
            }
            if (best >= minLag && best < maxLag && nsdf[best + 1] <= nsdf[best] &&
                nsdf[best - 1] <= nsdf[best]
            ) {
                keyMaxima[count++] = best
                if (nsdf[best] > highest) highest = nsdf[best]
            }
        }
        if (count == 0) return null

        val threshold = cutoff * highest
        var chosen = -1
        for (i in 0 until count) {
            if (nsdf[keyMaxima[i]] >= threshold) {
                chosen = keyMaxima[i]
                break
            }
        }
        if (chosen < 0) return null

        val a = nsdf[chosen - 1]
        val b = nsdf[chosen]
        val c = nsdf[chosen + 1]
        val denom = a - 2f * b + c
        var shift = 0f
        var peak = b
        if (denom < 0f) {
            shift = (0.5f * (a - c) / denom).coerceIn(-1f, 1f)
            peak = b - 0.25f * (a - c) * shift
        }
        val confidence = min(peak, 1f)
        if (confidence < minConfidence) return null
        val period = chosen + shift
        if (period <= 0f) return null
        return PitchResult(sampleRate / period, confidence)
    }
}
