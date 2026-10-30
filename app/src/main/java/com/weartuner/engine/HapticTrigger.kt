package com.weartuner.engine

import kotlin.math.abs

/**
 * Decides when to fire the in-tune haptic click. Pure logic, no Android dependencies.
 *
 * Fires once when |cents| stays within [enterCents] for at least [dwellMs]. Re-arms only after
 * |cents| exceeds [rearmCents] or the target note changes, so hovering around the target does
 * not buzz repeatedly.
 */
class HapticTrigger(
    private val enterCents: Float = 3f,
    private val rearmCents: Float = 8f,
    private val dwellMs: Long = 250L,
) {
    private var armed = true
    private var inTuneSince = -1L
    private var targetKey = Int.MIN_VALUE

    /** Returns true exactly when a click should be fired. */
    fun update(cents: Float, key: Int, nowMs: Long): Boolean {
        if (key != targetKey) {
            targetKey = key
            armed = true
            inTuneSince = -1L
        }
        val a = abs(cents)
        if (a > rearmCents) armed = true
        if (a > enterCents) {
            inTuneSince = -1L
            return false
        }
        if (inTuneSince < 0) inTuneSince = nowMs
        if (armed && nowMs - inTuneSince >= dwellMs) {
            armed = false
            return true
        }
        return false
    }

    /** Call when the signal is lost. Keeps the armed state so a pause does not re-fire. */
    fun signalLost() {
        inTuneSince = -1L
    }
}
