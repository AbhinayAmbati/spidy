package com.example.agent.core.ai.local

import com.example.agent.core.agent.ModelSource
import com.example.agent.core.ai.AIProvider
import com.example.agent.core.ai.AIProviderException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Owns the on-device models: whether each is on the phone, the download that fetches it, and
 * the provider the agent loop talks to once Spidey is ready.
 *
 * Downloads run one at a time on [scope], so they outlive any screen. Spidey is fetched
 * automatically on first launch unless the connection is metered; Whistle waits to be asked.
 */
class OnDeviceModelManager(
    private val downloader: ModelDownloader,
    private val scope: CoroutineScope,
    private val isMetered: () -> Boolean,
    private val engineFor: (File) -> LocalToolEngine = { NativeSpideyEngine(it) },
    private val spideySpec: ModelSpec = OnDeviceModels.SPIDEY,
    private val whistleSpec: ModelSpec = OnDeviceModels.WHISTLE,
) : ModelSource {

    private val spideyStatus = MutableStateFlow<ModelStatus>(ModelStatus.NotDownloaded)
    private val whistleStatus = MutableStateFlow<ModelStatus>(ModelStatus.NotDownloaded)

    val spidey: StateFlow<ModelStatus> = spideyStatus.asStateFlow()
    val whistle: StateFlow<ModelStatus> = whistleStatus.asStateFlow()

    private var downloadJob: Job? = null
    private var provider: AIProvider? = null

    init {
        refresh()
    }

    /** Re-reads what is on disk. A download in progress is left alone. */
    fun refresh() {
        refresh(spideySpec, spideyStatus)
        refresh(whistleSpec, whistleStatus)
    }

    /** First-launch behaviour: fetch Spidey on its own, but never over mobile data unasked. */
    fun startIfAllowed() {
        refresh()
        if (spideyStatus.value is ModelStatus.NotDownloaded && !isMetered()) downloadSpidey()
    }

    fun downloadSpidey() = download(spideySpec, spideyStatus)

    fun downloadWhistle() = download(whistleSpec, whistleStatus)

    override suspend fun provider(): AIProvider {
        when (val status = spideyStatus.value) {
            ModelStatus.Ready -> Unit
            is ModelStatus.Downloading -> throw AIProviderException(
                AIProviderException.Kind.Unknown,
                "The on-device model is still downloading (${status.percent}%). Try again when it finishes.",
            )
            else -> throw AIProviderException(
                AIProviderException.Kind.Unknown,
                "The on-device model has not been downloaded yet. Open Settings to download it.",
            )
        }
        return provider ?: SpideyProvider(engineFor(downloader.fileFor(spideySpec))).also { provider = it }
    }

    private fun refresh(spec: ModelSpec, status: MutableStateFlow<ModelStatus>) {
        if (status.value is ModelStatus.Downloading) return
        status.value = if (downloader.isDownloaded(spec)) ModelStatus.Ready else ModelStatus.NotDownloaded
    }

    private fun download(spec: ModelSpec, status: MutableStateFlow<ModelStatus>) {
        if (downloadJob?.isActive == true) return
        if (downloader.isDownloaded(spec)) {
            status.value = ModelStatus.Ready
            return
        }

        downloadJob = scope.launch {
            status.value = ModelStatus.Downloading(0, spec.sizeBytes)
            try {
                downloader.download(spec) { done, total ->
                    status.update { ModelStatus.Downloading(done, total) }
                }
                status.value = ModelStatus.Ready
            } catch (failure: kotlinx.coroutines.CancellationException) {
                status.value = ModelStatus.NotDownloaded
                throw failure
            } catch (failure: Exception) {
                status.value = ModelStatus.Failed(failure.message ?: "The download failed.")
            }
        }
    }
}
