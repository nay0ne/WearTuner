package com.weartuner

import com.weartuner.dsp.PitchSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchSmootherTest {
    @Test
    fun outlierInMedianWindowIsRejected() {
        val s = PitchSmoother()
        var out = 0f
        val inputs = floatArrayOf(110f, 110.1f, 110f, 220f, 110.05f)
        inputs.forEachIndexed { i, hz -> out = s.addSample(hz, i * 64L) }
        assertEquals(110.05f, out, 0.2f)
    }

    @Test
    fun genuineNoteChangeResetsAndFollows() {
        val s = PitchSmoother()
        repeat(5) { s.addSample(110f, it * 64L) }
        s.smoothCents(5f, 45)
        var out = 0f
        repeat(5) { out = s.addSample(146.83f, (5 + it) * 64L) }
        assertEquals(146.83f, out, 0.01f)
        // EMA restarted: first value after a jump is passed through.
        assertEquals(-20f, s.smoothCents(-20f, 45), 1e-4f)
    }

    @Test
    fun emaBlendsAndResetsOnTargetChange() {
        val s = PitchSmoother(alpha = 0.35f)
        assertEquals(10f, s.smoothCents(10f, 1), 1e-5f)
        assertEquals(10f + 0.35f * (0f - 10f), s.smoothCents(0f, 1), 1e-5f)
        assertEquals(-30f, s.smoothCents(-30f, 2), 1e-5f)
    }

    @Test
    fun signalHeldForFourHundredMs() {
        val s = PitchSmoother()
        assertFalse(s.hasSignal(0))
        s.addSample(110f, 1000)
        assertTrue(s.hasSignal(1399))
        assertFalse(s.hasSignal(1401))
    }
}
