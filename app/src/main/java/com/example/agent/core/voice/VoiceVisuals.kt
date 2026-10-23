package com.example.agent.core.voice

import kotlin.math.PI
import kotlin.math.sin

/** How strongly the blue edge glow shows. */
object GlowModel {

    /** The glow is shown while listening, and faintly while the request is being understood. */
    fun visible(state: VoiceState): Boolean =
        state == VoiceState.Listening || state == VoiceState.Transcribing || state == VoiceState.Thinking

    /**
     * Target opacity 0..1. [level] is the microphone loudness (0..1) and [phase] runs in seconds,
     * giving the slow breathing.
     */
    fun alpha(state: VoiceState, level: Float, phase: Float): Float {
        val breath = ((sin(2.0 * PI * phase / BREATH_SECONDS) + 1.0) / 2.0).toFloat()
        return when (state) {
            VoiceState.Listening -> {
                val voice = (level * LEVEL_GAIN).coerceIn(0f, 1f)
                (0.3f + 0.15f * breath + 0.45f * voice).coerceIn(0f, 1f)
            }
            VoiceState.Transcribing, VoiceState.Thinking -> 0.15f + 0.1f * breath
            else -> 0f
        }
    }

    private const val BREATH_SECONDS = 2.4f
    private const val LEVEL_GAIN = 6f
}
