package com.example.agent

import com.example.agent.core.voice.AsciiFace
import com.example.agent.core.voice.AudioLevels
import com.example.agent.core.voice.EndReason
import com.example.agent.core.voice.EndpointDetector
import com.example.agent.core.voice.GlowModel
import com.example.agent.core.voice.TranscriptCleaner
import com.example.agent.core.voice.VoiceState
import com.example.agent.core.voice.VoiceStateMapper
import com.example.agent.core.voice.WhistleResult
import com.example.agent.domain.model.TaskProgress
import com.example.agent.domain.model.TaskState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceLogicTest {

    // ---- audio levels ----

    @Test
    fun `rms of silence is zero and of a loud tone is large`() {
        assertThat(AudioLevels.rms(ShortArray(100))).isEqualTo(0f)
        assertThat(AudioLevels.rms(ShortArray(100) { 16384 })).isWithin(0.001f).of(0.5f)
        assertThat(AudioLevels.rms(ShortArray(0))).isEqualTo(0f)
    }

    @Test
    fun `rms only counts the filled part of a chunk`() {
        val chunk = ShortArray(100) { if (it < 10) 16384 else 0 }
        assertThat(AudioLevels.rms(chunk, 10)).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun `peak and float conversion are normalised`() {
        val samples = shortArrayOf(0, 16384, -32768)
        assertThat(AudioLevels.peak(samples)).isEqualTo(1f)
        assertThat(AudioLevels.toFloat(samples).toList()).containsExactly(0f, 0.5f, -1f).inOrder()
        assertThat(AudioLevels.toFloat(samples, 2)).hasLength(2)
    }

    // ---- endpointing ----

    private fun detector() = EndpointDetector(
        speechThreshold = 0.05f,
        silenceMillis = 500,
        minSpeechMillis = 200,
        noSpeechTimeoutMillis = 1_000,
        maxMillis = 3_000,
    )

    @Test
    fun `stops after speech followed by enough silence`() {
        val d = detector()
        repeat(6) { assertThat(d.feed(0.2f, 50)).isNull() }
        assertThat(d.heardSpeech).isTrue()
        repeat(9) { assertThat(d.feed(0.0f, 50)).isNull() }
        assertThat(d.feed(0.0f, 50)).isEqualTo(EndReason.SpeechEnded)
    }

    @Test
    fun `gives up when nothing is said`() {
        val d = detector()
        var reason: EndReason? = null
        var chunks = 0
        while (reason == null && chunks < 1000) {
            reason = d.feed(0.0f, 50)
            chunks++
        }
        assertThat(reason).isEqualTo(EndReason.NoSpeech)
        assertThat(d.heardSpeech).isFalse()
    }

    @Test
    fun `a short blip is not speech`() {
        val d = detector()
        d.feed(0.5f, 50)
        assertThat(d.heardSpeech).isFalse()
    }

    @Test
    fun `a pause shorter than the silence window does not end the speech`() {
        val d = detector()
        repeat(6) { d.feed(0.2f, 50) }
        repeat(4) { assertThat(d.feed(0.0f, 50)).isNull() }
        d.feed(0.2f, 50)
        repeat(4) { assertThat(d.feed(0.0f, 50)).isNull() }
    }

    @Test
    fun `never records past the maximum`() {
        val d = detector()
        var reason: EndReason? = null
        var chunks = 0
        while (reason == null && chunks < 1000) {
            reason = d.feed(0.2f, 50)
            chunks++
        }
        assertThat(reason).isEqualTo(EndReason.MaxLength)
        assertThat(chunks).isEqualTo(60)
    }

    // ---- transcripts ----

    @Test
    fun `parse reads common text keys`() {
        assertThat(WhistleResult.parse("""{"text":"call Priya"}""")).isEqualTo("call Priya")
        assertThat(WhistleResult.parse("""{"response":"open Spotify","confidence":0.9}""")).isEqualTo("open Spotify")
        assertThat(WhistleResult.parse("""{"transcript":"set an alarm"}""")).isEqualTo("set an alarm")
    }

    @Test
    fun `parse joins segments and accepts plain text`() {
        assertThat(WhistleResult.parse("""{"segments":[{"text":"call"},{"text":"Priya"}]}""")).isEqualTo("call Priya")
        assertThat(WhistleResult.parse("call Priya")).isEqualTo("call Priya")
    }

    @Test
    fun `parse returns null for nothing useful`() {
        assertThat(WhistleResult.parse("")).isNull()
        assertThat(WhistleResult.parse("""{"text":"  "}""")).isNull()
        assertThat(WhistleResult.parse("""{"duration":3}""")).isNull()
        assertThat(WhistleResult.parse("{broken")).isNull()
    }

    @Test
    fun `cleaner tidies whitespace`() {
        assertThat(TranscriptCleaner.clean("  set a   timer\nfor 5 minutes ")).isEqualTo("set a timer for 5 minutes")
    }

    @Test
    fun `cleaner drops what Whistle says over silence`() {
        assertThat(TranscriptCleaner.clean(null)).isNull()
        assertThat(TranscriptCleaner.clean("")).isNull()
        assertThat(TranscriptCleaner.clean("Thank you.")).isNull()
        assertThat(TranscriptCleaner.clean("[BLANK_AUDIO]")).isNull()
        assertThat(TranscriptCleaner.clean("(music)")).isNull()
        assertThat(TranscriptCleaner.clean("you")).isNull()
    }

    @Test
    fun `cleaner keeps a real command that contains a noise word`() {
        assertThat(TranscriptCleaner.clean("Thank you note app, open it")).isEqualTo("Thank you note app, open it")
    }

    // ---- state mapping ----

    private fun progress(state: TaskState, error: String? = null) =
        TaskProgress("t", "p", state, emptyList(), 0, 0, error = error)

    @Test
    fun `voice phases win over a stale task`() {
        val stale = progress(TaskState.COMPLETED)
        assertThat(VoiceStateMapper.combine(VoiceState.Listening, stale)).isEqualTo(VoiceState.Listening)
        assertThat(VoiceStateMapper.combine(VoiceState.Transcribing, stale)).isEqualTo(VoiceState.Transcribing)
        assertThat(VoiceStateMapper.combine(VoiceState.Thinking, stale)).isEqualTo(VoiceState.Thinking)
        assertThat(VoiceStateMapper.combine(VoiceState.Error("x"), stale)).isEqualTo(VoiceState.Error("x"))
    }

    @Test
    fun `an idle voice follows the task`() {
        val idle = VoiceState.Idle
        assertThat(VoiceStateMapper.combine(idle, progress(TaskState.PLANNING))).isEqualTo(VoiceState.Thinking)
        assertThat(VoiceStateMapper.combine(idle, progress(TaskState.EXECUTING))).isEqualTo(VoiceState.Acting)
        assertThat(VoiceStateMapper.combine(idle, progress(TaskState.OBSERVING))).isEqualTo(VoiceState.Acting)
        assertThat(VoiceStateMapper.combine(idle, progress(TaskState.WAITING_FOR_CONFIRMATION)))
            .isEqualTo(VoiceState.Thinking)
        assertThat(VoiceStateMapper.combine(idle, progress(TaskState.COMPLETED))).isEqualTo(VoiceState.Done)
        assertThat(VoiceStateMapper.combine(idle, progress(TaskState.FAILED, "boom")))
            .isEqualTo(VoiceState.Error("boom"))
        assertThat(VoiceStateMapper.combine(idle, progress(TaskState.FAILED)))
            .isEqualTo(VoiceState.Error("Something went wrong"))
        assertThat(VoiceStateMapper.combine(idle, progress(TaskState.CANCELLED))).isEqualTo(VoiceState.Idle)
        assertThat(VoiceStateMapper.combine(idle, progress(TaskState.IDLE))).isEqualTo(VoiceState.Idle)
    }

    @Test
    fun `labels are short words`() {
        assertThat(VoiceStateMapper.label(VoiceState.Listening)).isEqualTo("Listening…")
        assertThat(VoiceStateMapper.label(VoiceState.Error("No mic"))).isEqualTo("No mic")
        assertThat(VoiceStateMapper.label(VoiceState.Idle)).isEmpty()
    }

    // ---- visuals ----

    @Test
    fun `every state has a face and frames cycle`() {
        val states = listOf(
            VoiceState.Idle, VoiceState.Listening, VoiceState.Transcribing, VoiceState.Thinking,
            VoiceState.Acting, VoiceState.Done, VoiceState.Error("x"),
        )
        for (state in states) {
            assertThat(AsciiFace.frames(state)).isNotEmpty()
            val frames = AsciiFace.frames(state)
            assertThat(AsciiFace.frame(state, 0)).isEqualTo(frames[0])
            assertThat(AsciiFace.frame(state, frames.size)).isEqualTo(frames[0])
            assertThat(AsciiFace.frame(state, -1)).isEqualTo(frames.last())
        }
    }

    @Test
    fun `listening animates and done and error are distinct`() {
        assertThat(AsciiFace.frames(VoiceState.Listening).toSet().size).isGreaterThan(1)
        assertThat(AsciiFace.frame(VoiceState.Done, 0)).isNotEqualTo(AsciiFace.frame(VoiceState.Error("x"), 0))
    }

    @Test
    fun `glow shows while listening and fades out otherwise`() {
        assertThat(GlowModel.visible(VoiceState.Listening)).isTrue()
        assertThat(GlowModel.visible(VoiceState.Thinking)).isTrue()
        assertThat(GlowModel.visible(VoiceState.Done)).isFalse()
        assertThat(GlowModel.alpha(VoiceState.Done, 1f, 0f)).isEqualTo(0f)
        assertThat(GlowModel.alpha(VoiceState.Idle, 1f, 0f)).isEqualTo(0f)
        assertThat(GlowModel.alpha(VoiceState.Error("x"), 1f, 0f)).isEqualTo(0f)
    }

    @Test
    fun `glow is brighter when the voice is louder and stays in range`() {
        val quiet = GlowModel.alpha(VoiceState.Listening, 0f, 0f)
        val loud = GlowModel.alpha(VoiceState.Listening, 0.3f, 0f)
        assertThat(loud).isGreaterThan(quiet)
        for (phase in listOf(0f, 0.6f, 1.2f, 1.8f)) {
            for (level in listOf(0f, 0.5f, 5f)) {
                val a = GlowModel.alpha(VoiceState.Listening, level, phase)
                assertThat(a).isAtLeast(0f)
                assertThat(a).isAtMost(1f)
            }
        }
        assertThat(GlowModel.alpha(VoiceState.Thinking, 0f, 0f))
            .isLessThan(GlowModel.alpha(VoiceState.Listening, 0f, 0f))
    }
}
