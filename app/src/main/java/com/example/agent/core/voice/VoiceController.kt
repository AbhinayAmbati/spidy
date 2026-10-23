package com.example.agent.core.voice

import com.example.agent.domain.model.TaskProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The microphone, or the speech model, could not do its job. The message is safe to show. */
class VoiceException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Records 16 kHz mono PCM16 until [onChunk] returns false or the coroutine is cancelled. */
interface VoiceRecorder {
    suspend fun record(onChunk: (samples: ShortArray, count: Int) -> Boolean)
}

/** Turns 16 kHz mono float audio into text. Returns null when nothing could be made of it. */
fun interface SpeechTranscriber {
    suspend fun transcribe(pcm: FloatArray): String?
}

/** Where recognised text goes; on the phone this is the same agent runtime typed text uses. */
interface VoiceHost {
    val busy: Boolean
    val progress: Flow<TaskProgress>

    /** Recording is about to start: bring up the overlay and keep the process alive. */
    fun onListeningStarted()

    fun submit(text: String)
}

/**
 * The seam for a wake word: anything that can say "start listening" without a tap. Nothing
 * implements it yet beyond [NoopVoiceTrigger].
 */
fun interface VoiceTrigger {
    fun events(): Flow<Unit>
}

object NoopVoiceTrigger : VoiceTrigger {
    override fun events(): Flow<Unit> = emptyFlow()
}

/**
 * Record, decide the speaker has finished, transcribe, and hand the text to the runtime exactly as
 * if it had been typed. One recording at a time.
 */
class VoiceController(
    private val recorder: VoiceRecorder,
    private val transcriber: SpeechTranscriber,
    private val host: VoiceHost,
    private val scope: CoroutineScope,
    private val whistleReady: () -> Boolean,
    private val handoffMillis: Long = HANDOFF_MILLIS,
    private val newDetector: () -> EndpointDetector = { EndpointDetector() },
) {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private var job: Job? = null

    @Volatile
    private var stopRequested = false

    /** Starts listening when something (a tap, later a wake word) asks. */
    fun attach(trigger: VoiceTrigger) {
        scope.launch { trigger.events().collect { startListening() } }
    }

    fun startListening() {
        if (_state.value.let { it.isVoicePhase && it !is VoiceState.Error }) return
        if (host.busy) {
            _state.value = VoiceState.Error("Spidey is busy with another request.")
            return
        }
        if (!whistleReady()) {
            _state.value = VoiceState.Error("Download the speech model in Settings first.")
            return
        }
        stopRequested = false
        _state.value = VoiceState.Listening
        host.onListeningStarted()
        job = scope.launch { run() }
    }

    /** The user is done talking; transcribe what has been heard. */
    fun stopListening() {
        stopRequested = true
    }

    /** Throws the recording away. */
    fun cancel() {
        job?.cancel()
        job = null
        _level.value = 0f
        _state.value = VoiceState.Idle
    }

    /** The overlay has shown the error long enough. */
    fun acknowledgeError() {
        if (_state.value is VoiceState.Error) _state.value = VoiceState.Idle
    }

    private suspend fun run() {
        val detector = newDetector()
        val audio = PcmBuffer()
        try {
            recorder.record { chunk, count ->
                audio.append(chunk, count)
                val level = AudioLevels.rms(chunk, count)
                _level.value = level
                val ms = count * 1000 / SAMPLE_RATE
                detector.feed(level, ms) == null && !stopRequested
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: VoiceException) {
            fail(failure.message ?: "The microphone is not available.")
            return
        }
        _level.value = 0f

        if (!detector.heardSpeech) {
            fail("Didn't hear anything. Try again.")
            return
        }

        _state.value = VoiceState.Transcribing
        val text = try {
            TranscriptCleaner.clean(transcriber.transcribe(audio.toFloats()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: VoiceException) {
            fail(failure.message ?: "Speech recognition failed.")
            return
        }
        if (text == null) {
            fail("Couldn't understand that. Try again.")
            return
        }

        _state.value = VoiceState.Thinking
        host.submit(text)
        // Hold the "thinking" face until the task shows its own progress, so a previous task's
        // finished state is not flashed in between.
        withTimeoutOrNull(handoffMillis) { host.progress.first { it.state.isWorking } }
        _state.value = VoiceState.Idle
    }

    private fun fail(reason: String) {
        _level.value = 0f
        _state.value = VoiceState.Error(reason)
    }

    companion object {
        const val HANDOFF_MILLIS = 3_000L
    }
}

/** Audio collected so far, capped so a stuck microphone cannot grow without bound. */
class PcmBuffer(private val maxSamples: Int = SAMPLE_RATE * 30) {
    private var data = ShortArray(SAMPLE_RATE * 2)
    var size = 0
        private set

    fun append(chunk: ShortArray, count: Int) {
        val n = minOf(count, chunk.size, maxSamples - size)
        if (n <= 0) return
        if (size + n > data.size) data = data.copyOf(maxOf(data.size * 2, size + n))
        System.arraycopy(chunk, 0, data, size, n)
        size += n
    }

    fun toFloats(): FloatArray = AudioLevels.toFloat(data, size)
}
