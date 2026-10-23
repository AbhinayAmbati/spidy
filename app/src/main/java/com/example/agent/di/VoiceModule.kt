package com.example.agent.di

import android.content.Context
import com.example.agent.core.agent.AgentRuntime
import com.example.agent.core.ai.local.EngineGate
import com.example.agent.core.ai.local.ModelDownloader
import com.example.agent.core.ai.local.ModelStatus
import com.example.agent.core.ai.local.OnDeviceModelManager
import com.example.agent.core.ai.local.OnDeviceModels
import com.example.agent.core.voice.AgentVoiceHost
import com.example.agent.core.voice.AudioRecordRecorder
import com.example.agent.core.voice.NoopVoiceTrigger
import com.example.agent.core.voice.VoiceController
import com.example.agent.core.voice.VoiceTrigger
import com.example.agent.core.voice.WhistleTranscriber
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/** Voice input lives in its own module so it does not edit [AppModule]. */
@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {

    /** Swap this binding for a wake-word detector later. */
    @Provides
    @Singleton
    fun provideVoiceTrigger(): VoiceTrigger = NoopVoiceTrigger

    @Provides
    @Singleton
    fun provideVoiceController(
        @ApplicationContext context: Context,
        models: OnDeviceModelManager,
        downloader: ModelDownloader,
        gate: EngineGate,
        runtime: AgentRuntime,
        trigger: VoiceTrigger,
        @ApplicationScope scope: CoroutineScope,
    ): VoiceController = VoiceController(
        recorder = AudioRecordRecorder(),
        transcriber = WhistleTranscriber({ downloader.fileFor(OnDeviceModels.WHISTLE) }, gate),
        host = AgentVoiceHost(context, runtime),
        scope = scope,
        whistleReady = { models.whistle.value == ModelStatus.Ready },
    ).also { it.attach(trigger) }
}
