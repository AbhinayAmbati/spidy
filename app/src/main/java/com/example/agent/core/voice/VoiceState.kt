package com.example.agent.core.voice

import com.example.agent.domain.model.TaskProgress
import com.example.agent.domain.model.TaskState

/** What the voice feature, and the task it started, look like to the user right now. */
sealed interface VoiceState {
    data object Idle : VoiceState
    data object Listening : VoiceState
    data object Transcribing : VoiceState
    data object Thinking : VoiceState
    data object Acting : VoiceState
    data object Done : VoiceState
    data class Error(val reason: String) : VoiceState

    /** The voice flow itself owns the screen: recording, transcribing or reporting a problem. */
    val isVoicePhase: Boolean
        get() = this is Listening || this is Transcribing || this is Thinking || this is Error
}

/** Merges the voice phase with the task's progress into the single state the overlay shows. */
object VoiceStateMapper {

    fun combine(voice: VoiceState, progress: TaskProgress): VoiceState =
        if (voice.isVoicePhase) voice else fromTask(progress.state, progress.error)

    fun fromTask(state: TaskState, error: String? = null): VoiceState = when (state) {
        TaskState.IDLE, TaskState.CANCELLED, TaskState.PAUSED -> VoiceState.Idle
        TaskState.PLANNING, TaskState.WAITING_FOR_CONFIRMATION -> VoiceState.Thinking
        TaskState.EXECUTING, TaskState.OBSERVING -> VoiceState.Acting
        TaskState.COMPLETED -> VoiceState.Done
        TaskState.FAILED -> VoiceState.Error(error ?: "Something went wrong")
    }

    /** The short words shown next to the character. */
    fun label(state: VoiceState): String = when (state) {
        VoiceState.Idle -> ""
        VoiceState.Listening -> "Listening…"
        VoiceState.Transcribing -> "Transcribing…"
        VoiceState.Thinking -> "Thinking…"
        VoiceState.Acting -> "Working…"
        VoiceState.Done -> "Done"
        is VoiceState.Error -> state.reason
    }
}
