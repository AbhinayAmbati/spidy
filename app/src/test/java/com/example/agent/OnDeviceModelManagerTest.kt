package com.example.agent

import com.example.agent.core.ai.AIProviderException
import com.example.agent.core.ai.local.LocalToolEngine
import com.example.agent.core.ai.local.ModelDownloader
import com.example.agent.core.ai.local.ModelSpec
import com.example.agent.core.ai.local.ModelStatus
import com.example.agent.core.ai.local.SpideyProvider
import com.example.agent.core.ai.local.OnDeviceModelManager
import com.example.agent.core.ai.local.RangeSource
import com.example.agent.core.ai.local.RangeStream
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class OnDeviceModelManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val content = ByteArray(10_000) { (it * 7).toByte() }

    private val spidey = ModelSpec(
        id = "n", displayName = "Spidey", fileName = "n.cact", url = "https://example.invalid/n",
        sizeBytes = content.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) },
    )
    private val whistle = spidey.copy(id = "w", displayName = "Whistle", fileName = "w.cact")

    private class Engine : LocalToolEngine {
        override fun start(systemPrompt: String, toolsJson: String) = Unit
        override fun reset() = Unit
        override fun complete(input: String, maxNewTokens: Int) = "{}"
    }

    private val serving = RangeSource { _, from ->
        RangeStream(from, content.size.toLong(), ByteArrayInputStream(content.copyOfRange(from.toInt(), content.size)))
    }

    private val offline = RangeSource { _, _ -> throw IOException("no network") }

    private fun TestScope.newManager(source: RangeSource, metered: Boolean = false): Pair<OnDeviceModelManager, CoroutineScope> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val manager = OnDeviceModelManager(
            downloader = ModelDownloader(source, folder.root, dispatcher),
            scope = scope,
            isMetered = { metered },
            engineFor = { Engine() },
            spideySpec = spidey,
            whistleSpec = whistle,
        )
        return manager to scope
    }

    @Test
    fun `a model already on disk is ready at start`() = runTest {
        File(folder.root, "n.cact").writeBytes(content)

        val (manager, scope) = newManager(offline)

        assertThat(manager.spidey.value).isEqualTo(ModelStatus.Ready)
        assertThat(manager.whistle.value).isEqualTo(ModelStatus.NotDownloaded)
        scope.cancel()
    }

    @Test
    fun `starting on an unmetered connection downloads Spidey and makes it usable`() = runTest {
        val (manager, scope) = newManager(serving)

        manager.startIfAllowed()
        advanceUntilIdle()

        assertThat(manager.spidey.value).isEqualTo(ModelStatus.Ready)
        assertThat(manager.provider()).isInstanceOf(SpideyProvider::class.java)
        assertThat(manager.whistle.value).isEqualTo(ModelStatus.NotDownloaded)
        scope.cancel()
    }

    @Test
    fun `a metered connection does not start a download on its own`() = runTest {
        val (manager, scope) = newManager(serving, metered = true)

        manager.startIfAllowed()
        advanceUntilIdle()

        assertThat(manager.spidey.value).isEqualTo(ModelStatus.NotDownloaded)
        scope.cancel()
    }

    @Test
    fun `asking for the download works even on a metered connection`() = runTest {
        val (manager, scope) = newManager(serving, metered = true)

        manager.downloadSpidey()
        advanceUntilIdle()

        assertThat(manager.spidey.value).isEqualTo(ModelStatus.Ready)
        scope.cancel()
    }

    @Test
    fun `a failed download reports why and the model is not usable`() = runTest {
        val (manager, scope) = newManager(offline)

        manager.downloadSpidey()
        advanceUntilIdle()

        val status = manager.spidey.value
        assertThat(status).isInstanceOf(ModelStatus.Failed::class.java)
        assertThat((status as ModelStatus.Failed).message).contains("no network")
        val failure = runCatching { manager.provider() }.exceptionOrNull()
        assertThat(failure).isInstanceOf(AIProviderException::class.java)
        assertThat(failure?.message).contains("not been downloaded")
        scope.cancel()
    }

    @Test
    fun `the provider explains that the model is not there yet before it is downloaded`() = runTest {
        val (manager, scope) = newManager(serving)

        val failure = runCatching { manager.provider() }.exceptionOrNull()

        assertThat(failure).isInstanceOf(AIProviderException::class.java)
        assertThat(failure?.message).contains("Settings")
        scope.cancel()
    }
}
