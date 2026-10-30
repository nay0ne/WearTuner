package com.weartuner.engine

sealed interface TunerState {
    data object NeedsPermission : TunerState

    /** Initial state, or capture stopped because the app left the foreground. */
    data object Stopped : TunerState

    /** Capture stopped after 30 s without a valid pitch. */
    data object AutoStopped : TunerState

    data class Listening(
        val noteName: String?,
        val octave: Int?,
        /** -50..+50 */
        val cents: Float,
        val frequencyHz: Float?,
        /** |cents| <= 3 */
        val inTune: Boolean,
        val signal: Boolean,
    ) : TunerState {
        companion object {
            val EMPTY = Listening(
                noteName = null,
                octave = null,
                cents = 0f,
                frequencyHz = null,
                inTune = false,
                signal = false,
            )
        }
    }
}
