package com.weartuner

import com.weartuner.tuning.Tunings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningTest {
    @Test
    fun hundredHzAgainstFifthStringIsFlatA2() {
        val m = Tunings.STANDARD.targetAt(1, 100.0)!!
        assertEquals(45, m.midi)
        assertEquals("A", m.noteName)
        assertEquals(2, m.octave)
        assertEquals("5 · A2", m.label)
        assertTrue(m.cents < 0)
    }

    @Test
    fun exactStringHasZeroCents() {
        val m = Tunings.STANDARD.targetAt(0, 82.4069)!!
        assertEquals("6 · E2", m.label)
        assertEquals(0.0, m.cents, 0.01)
    }

    @Test
    fun measuresAgainstSelectedStringNotNearest() {
        // 110 Hz is A2, but while tuning the low E it reads 500 cents sharp.
        val m = Tunings.STANDARD.targetAt(0, 110.0)!!
        assertEquals(40, m.midi)
        assertEquals(500.0, m.cents, 0.01)
    }

    @Test
    fun octaveErrorsAreFolded() {
        // Low E detected an octave up (second harmonic) still reads as in tune.
        assertEquals(0.0, Tunings.STANDARD.targetAt(0, 164.8138)!!.cents, 0.01)
        assertEquals(0.0, Tunings.STANDARD.targetAt(5, 164.8138)!!.cents, 0.01)
    }

    @Test
    fun chromaticAndOutOfRangeHaveNoTarget() {
        assertNull(Tunings.CHROMATIC.targetAt(0, 440.0))
        assertNull(Tunings.STANDARD.targetAt(6, 440.0))
        assertNull(Tunings.STANDARD.targetAt(-1, 440.0))
    }

    @Test
    fun advancingWrapsAroundStrings() {
        assertEquals(1, Tunings.STANDARD.nextIndex(0))
        assertEquals(0, Tunings.STANDARD.nextIndex(5))
        assertEquals(5, Tunings.STANDARD.previousIndex(0))
        assertEquals(0, Tunings.CHROMATIC.nextIndex(0))
    }

    @Test
    fun presetsHaveExpectedNotes() {
        assertEquals(listOf(38, 45, 50, 55, 59, 64), Tunings.DROP_D.midiNotes)
        assertEquals(6, Tunings.OPEN_G.midiNotes.size)
        assertEquals(Tunings.DADGAD, Tunings.byId("dadgad"))
    }

    @Test
    fun defaultTuningIsChromatic() {
        assertEquals(Tunings.CHROMATIC, Tunings.DEFAULT)
        assertEquals(Tunings.CHROMATIC, Tunings.byId("nope"))
        assertEquals(Tunings.CHROMATIC, Tunings.byId(null))
    }
}
