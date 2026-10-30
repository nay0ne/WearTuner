package com.weartuner

import com.weartuner.dsp.MpmDetector
import com.weartuner.dsp.NoteMath
import com.weartuner.dsp.PitchDetector
import com.weartuner.dsp.Rms
import com.weartuner.dsp.YinDetector
import com.weartuner.tuning.Tunings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PitchDetectorTest {

    private val openStringHz: List<Double> = Tunings.all
        .flatMap { it.midiNotes }
        .distinct()
        .sorted()
        .map { NoteMath.hzFromMidi(it.toDouble()) }

    private fun detectors(): List<Pair<String, PitchDetector>> = listOf(
        "MPM" to MpmDetector(TestSignals.SAMPLE_RATE),
        "YIN" to YinDetector(TestSignals.SAMPLE_RATE),
    )

    private fun assertWithinCents(name: String, signal: FloatArray, expectedHz: Double, tol: Double, d: PitchDetector) {
        val r = d.detect(signal)
        assertNotNull("$name returned null at $expectedHz Hz", r)
        val cents = NoteMath.centsBetween(r!!.frequencyHz.toDouble(), expectedHz)
        assertTrue("$name at $expectedHz Hz: got ${r.frequencyHz} Hz (${"%.2f".format(cents)} cents)", abs(cents) <= tol)
    }

    @Test
    fun pureSineWithinTwoCents() {
        for ((name, d) in detectors()) for (hz in openStringHz) {
            assertWithinCents(name, TestSignals.sine(hz), hz, 2.0, d)
        }
    }

    @Test
    fun harmonicDecayingPluckWithinTwoCents() {
        for ((name, d) in detectors()) for (hz in openStringHz) {
            assertWithinCents(name, TestSignals.pluck(hz), hz, 2.0, d)
        }
    }

    @Test
    fun noisySignalWithinFiveCentsAndNoOctaveErrors() {
        for ((name, d) in detectors()) for (hz in openStringHz) {
            val noisy = TestSignals.addNoise(TestSignals.pluck(hz))
            val r = d.detect(noisy)
            assertNotNull("$name null with noise at $hz", r)
            val cents = NoteMath.centsBetween(r!!.frequencyHz.toDouble(), hz)
            assertTrue("$name octave/accuracy error at $hz Hz: got ${r.frequencyHz}", abs(cents) <= 5.0)
        }
    }

    @Test
    fun noiseOnlyAndSilenceGiveNoPitch() {
        for ((name, d) in detectors()) {
            assertNull("$name noise", d.detect(TestSignals.noise()))
            assertNull("$name silence", d.detect(FloatArray(TestSignals.N)))
        }
    }

    @Test
    fun silenceIsRejectedByRmsGate() {
        assertTrue(Rms.compute(FloatArray(2048)) < Rms.SILENCE_RMS)
        assertTrue(Rms.compute(TestSignals.noise(amplitude = 0.002f)) < Rms.SILENCE_RMS)
        assertTrue(Rms.compute(TestSignals.sine(110.0)) > Rms.SILENCE_RMS)
    }

    @Test
    fun detectorsAreReusable() {
        for ((_, d) in detectors()) {
            val a = d.detect(TestSignals.sine(110.0))
            d.detect(TestSignals.sine(329.63))
            val b = d.detect(TestSignals.sine(110.0))
            assertEquals(a!!.frequencyHz, b!!.frequencyHz, 1e-4f)
        }
    }
}
