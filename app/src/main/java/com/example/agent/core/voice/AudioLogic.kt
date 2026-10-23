package com.example.agent.core.voice

import kotlin.math.abs
import kotlin.math.sqrt

/** Whistle reads 16 kHz mono audio. */
const val SAMPLE_RATE = 16_000

/** Measurements over signed 16-bit PCM. Levels are normalised to 0..1. */
object AudioLevels {

    fun rms(samples: ShortArray, count: Int = samples.size): Float {
        val n = count.coerceIn(0, samples.size)
        if (n == 0) return 0f
        var sum = 0.0
        for (i in 0 until n) {
            val v = samples[i] / 32768.0
            sum += v * v
        }
        return sqrt(sum / n).toFloat()
    }

    fun peak(samples: ShortArray, count: Int = samples.size): Float {
        val n = count.coerceIn(0, samples.size)
        var max = 0
        for (i in 0 until n) max = maxOf(max, abs(samples[i].toInt()))
        return max / 32768f
    }

    fun toFloat(samples: ShortArray, count: Int = samples.size): FloatArray {
        val n = count.coerceIn(0, samples.size)
        return FloatArray(n) { samples[it] / 32768f }
    }
}

/** Why recording ended. */
enum class EndReason { SpeechEnded, NoSpeech, MaxLength }

/**
 * Decides when to stop recording from the loudness of each chunk. Whistle invents text on silence,
 * so [heardSpeech] is also what decides whether the audio is sent to it at all.
 */
class EndpointDetector(
    private val speechThreshold: Float = 0.015f,
    private val silenceMillis: Int = 900,
    private val minSpeechMillis: Int = 300,
    private val noSpeechTimeoutMillis: Int = 6_000,
    private val maxMillis: Int = 15_000,
) {
    private var elapsed = 0
    private var speech = 0
    private var silenceRun = 0

    val heardSpeech: Boolean get() = speech >= minSpeechMillis

    /** Feeds one chunk; returns null to keep recording, or why to stop. */
    fun feed(level: Float, chunkMillis: Int): EndReason? {
        elapsed += chunkMillis
        if (level >= speechThreshold) {
            speech += chunkMillis
            silenceRun = 0
        } else if (heardSpeech) {
            silenceRun += chunkMillis
        }
        return when {
            heardSpeech && silenceRun >= silenceMillis -> EndReason.SpeechEnded
            elapsed >= maxMillis -> EndReason.MaxLength
            !heardSpeech && elapsed >= noSpeechTimeoutMillis -> EndReason.NoSpeech
            else -> null
        }
    }
}
