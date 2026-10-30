package com.weartuner.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import com.weartuner.dsp.Dsp
import kotlin.math.max

/**
 * Wraps [AudioRecord] on a dedicated background thread (THREAD_PRIORITY_AUDIO).
 *
 * Emits sliding analysis windows of [windowSize] samples every [hopSize] samples (50% overlap)
 * through [Listener]. All listener callbacks run on the capture thread. The microphone is
 * released whenever the thread ends: on [stop], on a listener request, or on a read error.
 */
class AudioCapture(
    context: Context,
    private val windowSize: Int = Dsp.WINDOW_SIZE,
    private val hopSize: Int = Dsp.HOP_SIZE,
) {
    interface Listener {
        /** Called once on the capture thread after the microphone opened successfully. */
        fun onStarted(sampleRate: Int)

        /** Called with a full window (first [windowSize] samples valid). Return false to stop. */
        fun onWindow(window: FloatArray): Boolean

        /** The microphone could not be opened or failed while reading. Capture has ended. */
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val lock = Any()
    private var thread: Thread? = null

    @Volatile
    private var running = false

    val isRunning: Boolean get() = running

    fun start(listener: Listener) {
        synchronized(lock) {
            if (running) return
            val previous = thread
            if (previous?.isAlive == true) {
                // A previous session is still releasing the microphone; wait for it.
                try {
                    previous.join(JOIN_TIMEOUT_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    listener.onError("Interrupted while waiting for microphone")
                    return
                }
                if (previous.isAlive) {
                    listener.onError("Microphone still busy")
                    return
                }
            }
            running = true
            thread = Thread({ runCapture(listener) }, "tuner-audio").also { it.start() }
        }
    }

    /** Stops capture and waits briefly for the microphone to be released. */
    fun stop() {
        val t: Thread?
        synchronized(lock) {
            running = false
            t = thread
            thread = null
        }
        if (t != null && t !== Thread.currentThread()) {
            try {
                t.join(JOIN_TIMEOUT_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun runCapture(listener: Listener) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        var record: AudioRecord? = null
        try {
            val opened = openRecord()
            if (opened == null) {
                running = false
                listener.onError("Unable to open microphone")
                return
            }
            record = opened.first
            val sampleRate = opened.second
            try {
                record.startRecording()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "startRecording failed", e)
                running = false
                listener.onError("Unable to start recording")
                return
            }
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                running = false
                listener.onError("Microphone is busy")
                return
            }
            listener.onStarted(sampleRate)

            val window = FloatArray(windowSize)
            val hop = FloatArray(hopSize)
            var filled = 0
            while (running) {
                if (!readFully(record, hop)) {
                    if (running) {
                        running = false
                        listener.onError("Microphone read failed")
                    }
                    return
                }
                if (!running) break
                System.arraycopy(window, hopSize, window, 0, windowSize - hopSize)
                System.arraycopy(hop, 0, window, windowSize - hopSize, hopSize)
                if (filled < windowSize) filled += hopSize
                if (filled >= windowSize && !listener.onWindow(window)) {
                    running = false
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Capture failed", t)
            if (running) {
                running = false
                listener.onError(t.message ?: "Capture failed")
            }
        } finally {
            running = false
            if (record != null) {
                try {
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
                } catch (_: IllegalStateException) {
                }
                record.release()
            }
        }
    }

    /** Fills [out] with blocking reads. Returns false on an unrecoverable error. */
    private fun readFully(record: AudioRecord, out: FloatArray): Boolean {
        var offset = 0
        var transientErrors = 0
        while (offset < out.size && running) {
            val n = record.read(out, offset, out.size - offset, AudioRecord.READ_BLOCKING)
            if (n > 0) {
                offset += n
                transientErrors = 0
            } else {
                // 0 or a negative AudioRecord.ERROR_* code.
                if (n == AudioRecord.ERROR_DEAD_OBJECT) return false
                if (++transientErrors > MAX_TRANSIENT_ERRORS) return false
            }
        }
        return offset >= out.size
    }

    @SuppressLint("MissingPermission")
    private fun openRecord(): Pair<AudioRecord, Int>? {
        val source = chooseSource()
        for (rate in SAMPLE_RATES) {
            val minBytes = AudioRecord.getMinBufferSize(
                rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT,
            )
            if (minBytes <= 0) continue
            val bufferBytes = max(minBytes * 2, 4096 * 4)
            val record = try {
                AudioRecord(
                    source, rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT, bufferBytes,
                )
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord init failed at $rate Hz", e)
                continue
            }
            if (record.state == AudioRecord.STATE_INITIALIZED) return record to rate
            record.release()
        }
        return null
    }

    private fun chooseSource(): Int {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val unprocessed =
            am?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED
        else MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    private companion object {
        const val TAG = "AudioCapture"
        const val JOIN_TIMEOUT_MS = 400L
        const val MAX_TRANSIENT_ERRORS = 3
        val SAMPLE_RATES = intArrayOf(16_000, 22_050, 44_100)
    }
}
