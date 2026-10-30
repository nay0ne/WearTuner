package com.weartuner

import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object TestSignals {
    const val SAMPLE_RATE = 16_000
    const val N = 2048

    fun sine(freq: Double, n: Int = N, sampleRate: Int = SAMPLE_RATE, amp: Double = 0.5): FloatArray =
        FloatArray(n) { (amp * sin(2.0 * PI * freq * it / sampleRate)).toFloat() }

    /** Fundamental plus 2nd (0.5) and 3rd (0.3) harmonics with exponential decay. */
    fun pluck(
        freq: Double,
        n: Int = N,
        sampleRate: Int = SAMPLE_RATE,
        decayPerSecond: Double = 4.0,
    ): FloatArray = FloatArray(n) {
        val t = it.toDouble() / sampleRate
        val phase = 2.0 * PI * freq * t
        val s = sin(phase) + 0.5 * sin(2 * phase) + 0.3 * sin(3 * phase)
        (0.3 * exp(-decayPerSecond * t) * s).toFloat()
    }

    fun addNoise(x: FloatArray, amplitude: Float = 0.05f, seed: Long = 42): FloatArray {
        val rnd = Random(seed)
        return FloatArray(x.size) { x[it] + amplitude * (rnd.nextFloat() * 2f - 1f) }
    }

    fun noise(n: Int = N, amplitude: Float = 0.05f, seed: Long = 7): FloatArray {
        val rnd = Random(seed)
        return FloatArray(n) { amplitude * (rnd.nextFloat() * 2f - 1f) }
    }
}
