package com.example.agent.core.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.agent.automation.AgentForegroundService
import com.example.agent.core.agent.AgentRuntime
import com.example.agent.core.ai.local.EngineGate
import com.example.agent.core.ai.local.LocalEngineException
import com.example.agent.core.ai.local.NativeSpideyEngine
import com.example.agent.core.ai.local.SpideyNative
import com.example.agent.domain.model.TaskProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

/** [VoiceRecorder] over [AudioRecord]. RECORD_AUDIO must already be granted. */
class AudioRecordRecorder : VoiceRecorder {

    @SuppressLint("MissingPermission")
    override suspend fun record(onChunk: (samples: ShortArray, count: Int) -> Boolean) {
        withContext(Dispatchers.IO) {
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) throw VoiceException("This phone cannot record audio at 16 kHz.")

            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer, SAMPLE_RATE * 2),
                )
            } catch (failure: SecurityException) {
                throw VoiceException("Microphone permission is needed.", failure)
            } catch (failure: IllegalArgumentException) {
                throw VoiceException("The microphone could not be opened.", failure)
            }

            try {
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    throw VoiceException("The microphone is in use or not available.")
                }
                recorder.startRecording()
                val chunk = ShortArray(SAMPLE_RATE / 20) // 50 ms
                while (isActive) {
                    val read = recorder.read(chunk, 0, chunk.size)
                    if (read < 0) throw VoiceException("The microphone stopped working.")
                    if (read > 0 && !onChunk(chunk, read)) break
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
        }
    }
}

/** [SpeechTranscriber] over the Whistle model in the native engine. */
class WhistleTranscriber(
    private val modelFile: () -> File,
    private val gate: EngineGate,
) : SpeechTranscriber {

    override suspend fun transcribe(pcm: FloatArray): String? = gate.withLock {
        withContext(Dispatchers.Default) {
            try {
                NativeSpideyEngine.load(modelFile())
            } catch (failure: LocalEngineException) {
                throw VoiceException(failure.message ?: "The speech model could not be loaded.", failure)
            }
            if ((SpideyNative.nativeModels() and SPEECH_BIT) == 0) {
                throw VoiceException("The speech model is not loaded.")
            }
            val answer = SpideyNative.nativeTranscribe(pcm, null, null)
                ?: throw VoiceException("Speech recognition failed: ${NativeSpideyEngine.lastError()}")
            WhistleResult.parse(String(answer, Charsets.UTF_8))
        }
    }

    private companion object {
        /** Bit 2 of `nativeModels()`: the speech model. */
        const val SPEECH_BIT = 2
    }
}

/** Sends recognised text into the same runtime and foreground service typed text uses. */
class AgentVoiceHost(
    private val context: Context,
    private val runtime: AgentRuntime,
) : VoiceHost {

    override val busy: Boolean get() = runtime.isRunning
    override val progress: Flow<TaskProgress> get() = runtime.progress

    override fun onListeningStarted() = AgentForegroundService.start(context, listening = true)

    override fun submit(text: String) = runtime.start(text)
}
