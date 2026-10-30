package com.weartuner.dsp

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Frequency <-> MIDI <-> note-name helpers. Sharps only. */
object NoteMath {
    const val DEFAULT_A4 = 440.0

    private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val LN2 = ln(2.0)

    /** Fractional MIDI note number for [hz]. */
    fun midiFromHz(hz: Double, a4: Double = DEFAULT_A4): Double = 69.0 + 12.0 * ln(hz / a4) / LN2

    fun hzFromMidi(midi: Double, a4: Double = DEFAULT_A4): Double = a4 * 2.0.pow((midi - 69.0) / 12.0)

    /** Signed distance in cents from [referenceHz] to [hz]. */
    fun centsBetween(hz: Double, referenceHz: Double): Double = 1200.0 * ln(hz / referenceHz) / LN2

    /** Pitch class name, e.g. "C#". */
    fun noteName(midi: Int): String = NAMES[Math.floorMod(midi, 12)]

    /** Scientific octave number (MIDI 60 = C4). */
    fun octave(midi: Int): Int = Math.floorDiv(midi, 12) - 1

    /** Full name such as "E2". */
    fun fullName(midi: Int): String = noteName(midi) + octave(midi)

    /** Nearest equal-tempered note to [hz] and the offset from it in cents (-50..50). */
    fun nearest(hz: Double, a4: Double = DEFAULT_A4): NearestNote {
        val midi = midiFromHz(hz, a4)
        val rounded = midi.roundToInt()
        return NearestNote(rounded, 100.0 * (midi - rounded))
    }

    data class NearestNote(val midi: Int, val cents: Double) {
        val name: String get() = noteName(midi)
        val octave: Int get() = octave(midi)
        val isInTune: Boolean get() = abs(cents) <= 3.0
    }
}
