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
 * the provider the agent loop talks to once Needle is ready.
 *
 * Downloads run one at a time on [scope], so they outlive any screen. Needle is fetched
 * automatically on first launch unless the connection is metered; Whistle waits to be asked.
 */
class OnDeviceModelManager(
    private val downloader: ModelDownloader,
    private val scope: CoroutineScope,
    private val isMetered: () -> Boolean,
    private val engineFor: (File) -> LocalToolEngine = { NativeNeedleEngine(it) },
    private val needleSpec: ModelSpec = OnDeviceModels.NEEDLE,
    private val whistleSpec: ModelSpec = OnDeviceModels.WHISTLE,
) : ModelSource {

    private val needleStatus = MutableStateFlow<ModelStatus>(ModelStatus.NotDownloaded)
    private val whistleStatus = MutableStateFlow<ModelStatus>(ModelStatus.NotDownloaded)

    val needle: StateFlow<ModelStatus> = needleStatus.asStateFlow()
    val whistle: StateFlow<ModelStatus> = whistleStatus.asStateFlow()

    private var downloadJob: Job? = null
    private var provider: AIProvider? = null

    init {
        refresh()
    }

    /** Re-reads what is on disk. A download in progress is left alone. */
    fun refresh() {
        refresh(needleSpec, needleStatus)
        refresh(whistleSpec, whistleStatus)
    }

    /** First-launch behaviour: fetch Needle on its own, but never over mobile data unasked. */
    fun startIfAllowed() {
        refresh()
        if (needleStatus.value is ModelStatus.NotDownloaded && !isMetered()) downloadNeedle()
    }

    fun downloadNeedle() = download(needleSpec, needleStatus)

    fun downloadWhistle() = download(whistleSpec, whistleStatus)

    override suspend fun provider(): AIProvider {
        when (val status = needleStatus.value) {
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
        return provider ?: NeedleProvider(engineFor(downloader.fileFor(needleSpec))).also { provider = it }
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
