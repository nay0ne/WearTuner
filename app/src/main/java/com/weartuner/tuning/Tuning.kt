package com.weartuner.tuning

import com.weartuner.dsp.NoteMath
import kotlin.math.roundToInt

/** A tuning preset. An empty [midiNotes] list means chromatic (no fixed targets). */
class Tuning(val id: String, val name: String, val midiNotes: List<Int>) {
    val isChromatic: Boolean get() = midiNotes.isEmpty()

    /**
     * [hz] measured against the string at [index] (0 = lowest), or null for chromatic tunings or
     * an out-of-range index. Octave errors (e.g. a detected harmonic) are folded out, so cents
     * stay within -600..600. Strings are numbered from the highest (1) to the lowest (size).
     */
    fun targetAt(index: Int, hz: Double, a4: Double = NoteMath.DEFAULT_A4): TargetMatch? {
        if (index !in midiNotes.indices || hz <= 0.0) return null
        val targetHz = NoteMath.hzFromMidi(midiNotes[index].toDouble(), a4)
        val cents = NoteMath.centsBetween(hz, targetHz)
        return TargetMatch(
            index = index,
            stringNumber = midiNotes.size - index,
            midi = midiNotes[index],
            targetHz = targetHz,
            cents = cents - 1200.0 * (cents / 1200.0).roundToInt(),
        )
    }

    /** Index of the string after [index], wrapping from the highest back to the lowest. */
    fun nextIndex(index: Int): Int = if (midiNotes.isEmpty()) 0 else Math.floorMod(index + 1, midiNotes.size)

    /** Index of the string before [index], wrapping from the lowest to the highest. */
    fun previousIndex(index: Int): Int = if (midiNotes.isEmpty()) 0 else Math.floorMod(index - 1, midiNotes.size)

    override fun toString(): String = "Tuning($id)"
}

data class TargetMatch(
    val index: Int,
    val stringNumber: Int,
    val midi: Int,
    val targetHz: Double,
    val cents: Double,
) {
    val noteName: String get() = NoteMath.noteName(midi)
    val octave: Int get() = NoteMath.octave(midi)
    val label: String get() = "$stringNumber · ${NoteMath.fullName(midi)}"
}

object Tunings {
    // MIDI: E2=40 A2=45 D3=50 G3=55 B3=59 E4=64
    val CHROMATIC = Tuning("chromatic", "Chromatic", emptyList())
    val STANDARD = Tuning("standard", "Standard", listOf(40, 45, 50, 55, 59, 64))
    val DROP_D = Tuning("drop_d", "Drop D", listOf(38, 45, 50, 55, 59, 64))
    val HALF_STEP_DOWN = Tuning("half_step_down", "Half-step down", listOf(39, 44, 49, 54, 58, 63))
    val DADGAD = Tuning("dadgad", "DADGAD", listOf(38, 45, 50, 55, 57, 62))
    val OPEN_G = Tuning("open_g", "Open G", listOf(38, 43, 50, 55, 59, 62))

    val all: List<Tuning> = listOf(CHROMATIC, STANDARD, DROP_D, HALF_STEP_DOWN, DADGAD, OPEN_G)

    val DEFAULT = CHROMATIC

    fun byId(id: String?): Tuning = all.firstOrNull { it.id == id } ?: DEFAULT
}
