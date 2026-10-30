package com.weartuner

import com.weartuner.engine.HapticTrigger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticTriggerTest {
    @Test
    fun firesOnlyAfterDwell() {
        val h = HapticTrigger()
        assertFalse(h.update(1f, 40, 0))
        assertFalse(h.update(1f, 40, 200))
        assertTrue(h.update(1f, 40, 250))
        assertFalse(h.update(1f, 40, 600))
    }

    @Test
    fun hysteresisPreventsRepeatUntilLeavingEightCents() {
        val h = HapticTrigger()
        h.update(0f, 40, 0)
        assertTrue(h.update(0f, 40, 300))
        // wander out to 5 cents and back: still disarmed
        h.update(5f, 40, 400)
        h.update(1f, 40, 500)
        assertFalse(h.update(1f, 40, 900))
        // leave beyond 8 cents, then return
        h.update(12f, 40, 1000)
        h.update(1f, 40, 1100)
        assertTrue(h.update(1f, 40, 1400))
    }

    @Test
    fun targetChangeRearms() {
        val h = HapticTrigger()
        h.update(0f, 40, 0)
        assertTrue(h.update(0f, 40, 300))
        h.update(0f, 45, 400)
        assertTrue(h.update(0f, 45, 700))
    }
}
