package com.weartuner.dsp

/** A detected fundamental frequency and how much the detector trusts it (0..1). */
data class PitchResult(val frequencyHz: Float, val confidence: Float)

/** Pitch detection algorithms selectable in settings. */
enum class PitchAlgorithm(val id: String, val label: String) {
    MPM("mpm", "MPM"),
    YIN("yin", "YIN");

    companion object {
        fun fromId(id: String?): PitchAlgorithm = entries.firstOrNull { it.id == id } ?: MPM
    }
}

/** Shared analysis constants. */
object Dsp {
    const val WINDOW_SIZE = 2048
    const val HOP_SIZE = 1024
    const val MIN_HZ = 60f
    const val MAX_HZ = 1400f
}

/**
 * Monophonic pitch detector working on a fixed-size window of mono samples in -1..1.
 *
 * Implementations pre-allocate all work buffers at construction and must not allocate inside
 * their analysis loops. Instances are not thread-safe.
 */
interface PitchDetector {
    val sampleRate: Int
    val windowSize: Int

    /** Analyses the first [windowSize] samples. Returns null when no reliable pitch is found. */
    fun detect(samples: FloatArray): PitchResult?

    companion object {
        fun create(
            algorithm: PitchAlgorithm,
            sampleRate: Int,
            windowSize: Int = Dsp.WINDOW_SIZE,
        ): PitchDetector = when (algorithm) {
            PitchAlgorithm.MPM -> MpmDetector(sampleRate, windowSize)
            PitchAlgorithm.YIN -> YinDetector(sampleRate, windowSize)
        }
    }
}
