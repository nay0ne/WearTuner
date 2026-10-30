package com.weartuner.dsp

import kotlin.math.sqrt

object Rms {
    /** RMS below this (about -50 dBFS) is treated as silence; pitch detection is skipped. */
    const val SILENCE_RMS = 0.003f

    /** Root-mean-square of the first [count] samples. Does not allocate. */
    fun compute(samples: FloatArray, count: Int = samples.size): Float {
        if (count <= 0) return 0f
        var sum = 0.0
        for (i in 0 until count) {
            val s = samples[i].toDouble()
            sum += s * s
        }
        return sqrt(sum / count).toFloat()
    }
}
