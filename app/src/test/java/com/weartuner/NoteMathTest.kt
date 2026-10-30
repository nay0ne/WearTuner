package com.weartuner

import com.weartuner.dsp.NoteMath
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteMathTest {
    @Test
    fun a4Is440() {
        val n = NoteMath.nearest(440.0, 440.0)
        assertEquals("A4", NoteMath.fullName(n.midi))
        assertEquals(0.0, n.cents, 1e-6)
    }

    @Test
    fun lowE() {
        val n = NoteMath.nearest(82.41)
        assertEquals("E2", NoteMath.fullName(n.midi))
        assertEquals(0.0, n.cents, 0.5)
    }

    @Test
    fun a4ReferenceShiftIsRespected() {
        val n = NoteMath.nearest(432.0, 432.0)
        assertEquals("A4", NoteMath.fullName(n.midi))
        assertEquals(0.0, n.cents, 1e-6)
        // 432 Hz measured against A4 = 440 is about 31.8 cents flat.
        val against440 = NoteMath.nearest(432.0, 440.0)
        assertEquals("A4", NoteMath.fullName(against440.midi))
        assertEquals(-31.77, against440.cents, 0.05)
    }

    @Test
    fun namesAndOctaves() {
        assertEquals("C4", NoteMath.fullName(60))
        assertEquals("C#3", NoteMath.fullName(49))
        assertEquals("B-1", NoteMath.fullName(11))
    }

    @Test
    fun roundTrip() {
        val hz = NoteMath.hzFromMidi(57.0, 435.0)
        assertEquals(57.0, NoteMath.midiFromHz(hz, 435.0), 1e-9)
    }
}
