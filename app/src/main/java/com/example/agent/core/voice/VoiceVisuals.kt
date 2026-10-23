package com.example.agent.core.voice

import kotlin.math.PI
import kotlin.math.sin

/** The small ASCII character on the overlay. Pure, so the frames are testable. */
object AsciiFace {

    private val idle = listOf("(-_-)")
    private val listening = listOf("(o_o)", "(o_O)", "(O_O)", "(O_o)")
    private val thinking = listOf("(-_-).  ", "(-_-).. ", "(-_-)...")
    private val acting = listOf("(>_>)", "(>_<)", "(<_<)", "(>_<)")
    private val done = listOf("(^_^)")
    private val error = listOf("(x_x)")

    fun frames(state: VoiceState): List<String> = when (state) {
        VoiceState.Idle -> idle
        VoiceState.Listening -> listening
        VoiceState.Transcribing, VoiceState.Thinking -> thinking
        VoiceState.Acting -> acting
        VoiceState.Done -> done
        is VoiceState.Error -> error
    }

    fun frame(state: VoiceState, tick: Int): String {
        val all = frames(state)
        return all[Math.floorMod(tick, all.size)]
    }
}

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
