package com.example.agent

import com.example.agent.core.ai.local.EngineGate
import com.example.agent.core.voice.NoopVoiceTrigger
import com.example.agent.core.voice.SpeechTranscriber
import com.example.agent.core.voice.VoiceController
import com.example.agent.core.voice.VoiceException
import com.example.agent.core.voice.VoiceHost
import com.example.agent.core.voice.VoiceRecorder
import com.example.agent.core.voice.VoiceState
import com.example.agent.domain.model.TaskProgress
import com.example.agent.domain.model.TaskState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class VoiceControllerTest {

    private val loud = ShortArray(800) { 8000 }
    private val quiet = ShortArray(800)

    /** Feeds scripted chunks; [beforeChunk] runs with the chunk index so a test can act mid-recording. */
    private class FakeRecorder(
        private val chunks: List<ShortArray>,
        private val failWith: String? = null,
        private val beforeChunk: (Int) -> Unit = {},
    ) : VoiceRecorder {
        var recorded = 0

        override suspend fun record(onChunk: (ShortArray, Int) -> Boolean) {
            failWith?.let { throw VoiceException(it) }
            for ((index, chunk) in chunks.withIndex()) {
                beforeChunk(index)
                recorded++
                if (!onChunk(chunk, chunk.size)) return
            }
        }
    }

    private class FakeTranscriber(private val answer: String?) : SpeechTranscriber {
        var calls = 0
        var samples = 0
        override suspend fun transcribe(pcm: FloatArray): String? {
            calls++
            samples = pcm.size
            return answer
        }
    }

    private class FakeHost(var busy0: Boolean = false) : VoiceHost {
        val state = MutableStateFlow(TaskProgress("t", "p", TaskState.IDLE, emptyList(), 0, 0))
        val submitted = mutableListOf<String>()
        var listeningStarts = 0
        override val busy: Boolean get() = busy0
        override val progress: Flow<TaskProgress> get() = state
        override fun onListeningStarted() { listeningStarts++ }
        override fun submit(text: String) {
            submitted += text
            state.value = TaskProgress("t", text, TaskState.PLANNING, emptyList(), 0, 0)
        }
    }

    private fun speech(): List<ShortArray> = List(10) { loud } + List(30) { quiet }

    private fun controller(
        scope: CoroutineScope,
        recorder: VoiceRecorder,
        transcriber: SpeechTranscriber,
        host: VoiceHost,
        ready: Boolean = true,
    ) = VoiceController(recorder, transcriber, host, scope, whistleReady = { ready })

    @Test
    fun `speech is transcribed and submitted like typed text`() = runTest {
        val host = FakeHost()
        val transcriber = FakeTranscriber("  Set a timer for five minutes ")
        val c = controller(this, FakeRecorder(speech()), transcriber, host)

        c.startListening()
        assertThat(c.state.value).isEqualTo(VoiceState.Listening)
        advanceUntilIdle()

        assertThat(host.submitted).containsExactly("Set a timer for five minutes")
        assertThat(host.listeningStarts).isEqualTo(1)
        assertThat(transcriber.calls).isEqualTo(1)
        assertThat(transcriber.samples).isGreaterThan(8_000)
        assertThat(c.state.value).isEqualTo(VoiceState.Idle)
    }

    @Test
    fun `silence is never sent to the speech model`() = runTest {
        val host = FakeHost()
        val transcriber = FakeTranscriber("Thank you.")
        val c = controller(this, FakeRecorder(List(200) { quiet }), transcriber, host)

        c.startListening()
        advanceUntilIdle()

        assertThat(transcriber.calls).isEqualTo(0)
        assertThat(host.submitted).isEmpty()
        assertThat(c.state.value).isInstanceOf(VoiceState.Error::class.java)
    }

    @Test
    fun `a hallucinated transcript is not submitted`() = runTest {
        val host = FakeHost()
        val c = controller(this, FakeRecorder(speech()), FakeTranscriber("Thank you."), host)

        c.startListening()
        advanceUntilIdle()

        assertThat(host.submitted).isEmpty()
        assertThat(c.state.value).isInstanceOf(VoiceState.Error::class.java)
    }

    @Test
    fun `an empty transcript is an error`() = runTest {
        val host = FakeHost()
        val c = controller(this, FakeRecorder(speech()), FakeTranscriber(null), host)

        c.startListening()
        advanceUntilIdle()

        assertThat(host.submitted).isEmpty()
        assertThat((c.state.value as VoiceState.Error).reason).contains("understand")
    }

    @Test
    fun `a missing speech model asks for a download and does not record`() = runTest {
        val host = FakeHost()
        val recorder = FakeRecorder(speech())
        val c = controller(this, recorder, FakeTranscriber("x"), host, ready = false)

        c.startListening()
        advanceUntilIdle()

        assertThat((c.state.value as VoiceState.Error).reason).contains("Settings")
        assertThat(recorder.recorded).isEqualTo(0)
        assertThat(host.listeningStarts).isEqualTo(0)
    }

    @Test
    fun `a running task blocks voice input`() = runTest {
        val host = FakeHost(busy0 = true)
        val recorder = FakeRecorder(speech())
        val c = controller(this, recorder, FakeTranscriber("x"), host)

        c.startListening()
        advanceUntilIdle()

        assertThat(c.state.value).isInstanceOf(VoiceState.Error::class.java)
        assertThat(recorder.recorded).isEqualTo(0)
    }

    @Test
    fun `a microphone failure is reported`() = runTest {
        val host = FakeHost()
        val c = controller(this, FakeRecorder(emptyList(), failWith = "Mic busy"), FakeTranscriber("x"), host)

        c.startListening()
        advanceUntilIdle()

        assertThat(c.state.value).isEqualTo(VoiceState.Error("Mic busy"))
    }

    @Test
    fun `stopping early transcribes what was heard`() = runTest {
        val host = FakeHost()
        val transcriber = FakeTranscriber("call Priya")
        lateinit var c: VoiceController
        // 10 loud chunks (500 ms), then the user taps stop while the speaker pauses.
        val recorder = FakeRecorder(List(10) { loud } + List(5) { quiet }) { index ->
            if (index == 12) c.stopListening()
        }
        c = controller(this, recorder, transcriber, host)

        c.startListening()
        advanceUntilIdle()

        assertThat(recorder.recorded).isEqualTo(13)
        assertThat(host.submitted).containsExactly("call Priya")
    }

    @Test
    fun `a second tap while listening does not start another recording`() = runTest {
        val host = FakeHost()
        val recorder = FakeRecorder(speech())
        val c = controller(this, recorder, FakeTranscriber("open Spotify"), host)

        c.startListening()
        c.startListening()
        advanceUntilIdle()

        assertThat(host.listeningStarts).isEqualTo(1)
        assertThat(host.submitted).hasSize(1)
    }

    @Test
    fun `acknowledging an error returns to idle and allows another try`() = runTest {
        val host = FakeHost()
        val c = controller(this, FakeRecorder(speech()), FakeTranscriber(null), host)

        c.startListening()
        advanceUntilIdle()
        assertThat(c.state.value).isInstanceOf(VoiceState.Error::class.java)

        c.acknowledgeError()
        assertThat(c.state.value).isEqualTo(VoiceState.Idle)

        c.startListening()
        assertThat(c.state.value).isEqualTo(VoiceState.Listening)
        advanceUntilIdle()
    }

    @Test
    fun `thinking is held until the task shows progress or the hand-off times out`() = runTest {
        val host = object : VoiceHost {
            val flow = MutableStateFlow(TaskProgress("t", "p", TaskState.COMPLETED, emptyList(), 0, 0))
            override val busy = false
            override val progress: Flow<TaskProgress> = flow
            override fun onListeningStarted() {}
            override fun submit(text: String) {}
        }
        val c = controller(this, FakeRecorder(speech()), FakeTranscriber("call Priya"), host)

        c.startListening()
        runCurrent()
        assertThat(c.state.value).isEqualTo(VoiceState.Thinking)

        advanceTimeBy(VoiceController.HANDOFF_MILLIS + 1)
        runCurrent()
        assertThat(c.state.value).isEqualTo(VoiceState.Idle)
    }

    @Test
    fun `cancel discards the recording`() = runTest {
        val host = FakeHost()
        val transcriber = FakeTranscriber("x")
        val c = controller(this, FakeRecorder(speech()), transcriber, host)

        c.startListening()
        c.cancel()
        advanceUntilIdle()

        assertThat(c.state.value).isEqualTo(VoiceState.Idle)
        assertThat(transcriber.calls).isEqualTo(0)
    }

    @Test
    fun `a trigger starts listening without a tap`() = runTest {
        val host = FakeHost()
        val c = controller(this, FakeRecorder(speech()), FakeTranscriber("open Spotify"), host)

        c.attach { flowOf(Unit) }
        advanceUntilIdle()

        assertThat(host.submitted).containsExactly("open Spotify")
    }

    @Test
    fun `the default trigger never fires`() = runTest {
        assertThat(NoopVoiceTrigger.events().toList()).isEmpty()
    }

    @Test
    fun `engine gate runs one call at a time`() = runTest {
        val gate = EngineGate()
        val log = mutableListOf<String>()
        val first = launch {
            gate.withLock {
                log += "a-start"
                yield()
                log += "a-end"
            }
        }
        val second = launch {
            gate.withLock {
                log += "b-start"
                log += "b-end"
            }
        }
        first.join()
        second.join()
        assertThat(log).containsExactly("a-start", "a-end", "b-start", "b-end").inOrder()
    }
}
