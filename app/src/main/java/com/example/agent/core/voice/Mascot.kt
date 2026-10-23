package com.example.agent.core.voice

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class EyeStyle { Open, Wide, Squint, Happy, Cross }

enum class MouthStyle { Flat, Open, Smile, Frown }

/**
 * Everything the drawn character needs for one instant. Pure, so how the character behaves in each
 * state is testable without a canvas. Offsets are fractions of the character's size.
 */
data class MascotPose(
    val eyes: EyeStyle,
    /** 0 open .. 1 shut; only applies to [EyeStyle.Open]. */
    val blink: Float,
    /** Where the pupils look, -1..1. */
    val lookX: Float,
    val lookY: Float,
    val mouth: MouthStyle,
    /** How far the mouth is open, 0..1; only for [MouthStyle.Open]. */
    val mouthOpen: Float,
    /** Body squash and stretch around its feet. */
    val scale: Float,
    val hop: Float,
    val tiltDegrees: Float,
    /** Thinking dots shown above the head, 0..3. */
    val dots: Int,
)

/** The blue character's behaviour for each [VoiceState]. */
object MascotAnimator {

    fun pose(state: VoiceState, level: Float, seconds: Float): MascotPose {
        val t = seconds
        return when (state) {
            VoiceState.Idle -> MascotPose(
                eyes = EyeStyle.Open, blink = blink(t), lookX = 0f, lookY = 0f,
                mouth = MouthStyle.Flat, mouthOpen = 0f,
                scale = 1f + 0.02f * sin(2 * PI * t / 3.0).toFloat(), hop = 0f,
                tiltDegrees = 0f, dots = 0,
            )
            VoiceState.Listening -> {
                val voice = (level * LEVEL_GAIN).coerceIn(0f, 1f)
                MascotPose(
                    eyes = EyeStyle.Wide, blink = 0f, lookX = 0f, lookY = 0f,
                    mouth = MouthStyle.Open, mouthOpen = 0.25f + 0.75f * voice,
                    scale = 1f + 0.12f * voice, hop = 0f,
                    tiltDegrees = 0f, dots = 0,
                )
            }
            VoiceState.Transcribing, VoiceState.Thinking -> MascotPose(
                eyes = EyeStyle.Open, blink = blink(t), lookX = 0.7f * sin(t * 1.8f), lookY = -0.6f,
                mouth = MouthStyle.Flat, mouthOpen = 0f,
                scale = 1f, hop = 0f,
                tiltDegrees = 5f * sin(t * 1.3f), dots = ((t * 2.5f).toInt() % 4),
            )
            VoiceState.Acting -> MascotPose(
                eyes = EyeStyle.Squint, blink = 0f, lookX = sin(t * 5f), lookY = 0f,
                mouth = MouthStyle.Flat, mouthOpen = 0f,
                scale = 1f, hop = 0.08f * abs(sin(t * 6f)),
                tiltDegrees = 3f * sin(t * 6f), dots = 0,
            )
            VoiceState.Done -> MascotPose(
                eyes = EyeStyle.Happy, blink = 0f, lookX = 0f, lookY = 0f,
                mouth = MouthStyle.Smile, mouthOpen = 0f,
                scale = 1f, hop = 0.12f * abs(sin(t * 4f)),
                tiltDegrees = 0f, dots = 0,
            )
            is VoiceState.Error -> MascotPose(
                eyes = EyeStyle.Cross, blink = 0f, lookX = 0f, lookY = 0f,
                mouth = MouthStyle.Frown, mouthOpen = 0f,
                scale = 1f, hop = 0f,
                tiltDegrees = 6f * sin(t * 16f), dots = 0,
            )
        }
    }

    /** A quick blink every few seconds: 0 most of the time, a short dip to 1 and back. */
    private fun blink(t: Float): Float {
        val phase = Math.floorMod((t * 1000).toInt(), BLINK_PERIOD_MS) / BLINK_LENGTH_MS.toFloat()
        return if (phase in 0f..1f) sin(PI * phase).toFloat() else 0f
    }

    private const val LEVEL_GAIN = 6f
    private const val BLINK_PERIOD_MS = 3_800
    private const val BLINK_LENGTH_MS = 180
}
