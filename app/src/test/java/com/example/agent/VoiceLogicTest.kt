package com.example.agent

import com.example.agent.core.voice.AudioLevels
import com.example.agent.core.voice.EndReason
import com.example.agent.core.voice.EndpointDetector
import com.example.agent.core.voice.EyeStyle
import com.example.agent.core.voice.GlowModel
import com.example.agent.core.voice.MascotAnimator
import com.example.agent.core.voice.MouthStyle
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

    private fun pose(state: VoiceState, level: Float = 0f, t: Float = 0f) = MascotAnimator.pose(state, level, t)

    @Test
    fun `each state gives the character its own look`() {
        assertThat(pose(VoiceState.Listening).eyes).isEqualTo(EyeStyle.Wide)
        assertThat(pose(VoiceState.Thinking).eyes).isEqualTo(EyeStyle.Open)
        assertThat(pose(VoiceState.Transcribing).eyes).isEqualTo(EyeStyle.Open)
        assertThat(pose(VoiceState.Acting).eyes).isEqualTo(EyeStyle.Squint)
        assertThat(pose(VoiceState.Done).eyes).isEqualTo(EyeStyle.Happy)
        assertThat(pose(VoiceState.Done).mouth).isEqualTo(MouthStyle.Smile)
        assertThat(pose(VoiceState.Error("x")).eyes).isEqualTo(EyeStyle.Cross)
        assertThat(pose(VoiceState.Error("x")).mouth).isEqualTo(MouthStyle.Frown)
        assertThat(pose(VoiceState.Idle).eyes).isEqualTo(EyeStyle.Open)
    }

    @Test
    fun `the character opens its mouth and grows with the voice`() {
        val quiet = pose(VoiceState.Listening, level = 0f)
        val loud = pose(VoiceState.Listening, level = 0.3f)
        assertThat(loud.mouthOpen).isGreaterThan(quiet.mouthOpen)
        assertThat(loud.scale).isGreaterThan(quiet.scale)
        assertThat(loud.mouthOpen).isAtMost(1f)
    }

    @Test
    fun `thinking shows dots and looks around`() {
        val dots = (0..40).map { pose(VoiceState.Thinking, t = it * 0.1f).dots }.toSet()
        assertThat(dots).containsAtLeast(0, 1, 2, 3)
        val looks = (0..40).map { pose(VoiceState.Thinking, t = it * 0.1f).lookX }.toSet()
        assertThat(looks.size).isGreaterThan(5)
        assertThat(pose(VoiceState.Listening).dots).isEqualTo(0)
    }

    @Test
    fun `only done and acting hop`() {
        assertThat(pose(VoiceState.Acting, t = 0.2f).hop).isGreaterThan(0f)
        assertThat(pose(VoiceState.Done, t = 0.4f).hop).isGreaterThan(0f)
        assertThat(pose(VoiceState.Thinking, t = 0.4f).hop).isEqualTo(0f)
    }

    @Test
    fun `an error shakes`() {
        val tilts = (0..20).map { pose(VoiceState.Error("x"), t = it * 0.03f).tiltDegrees }
        assertThat(tilts.max()).isGreaterThan(0f)
        assertThat(tilts.min()).isLessThan(0f)
    }

    @Test
    fun `an idle character blinks now and then and never leaves its range`() {
        val blinks = (0..4000).map { pose(VoiceState.Idle, t = it * 0.01f).blink }
        assertThat(blinks.max()).isGreaterThan(0.9f)
        assertThat(blinks.min()).isEqualTo(0f)
        assertThat(blinks.all { it in 0f..1f }).isTrue()
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
