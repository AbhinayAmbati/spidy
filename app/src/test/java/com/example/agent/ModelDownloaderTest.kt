package com.example.agent

import com.example.agent.core.ai.local.ModelDownloadException
import com.example.agent.core.ai.local.ModelDownloader
import com.example.agent.core.ai.local.ModelSpec
import com.example.agent.core.ai.local.RangeSource
import com.example.agent.core.ai.local.RangeStream
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** A model must never be usable half-downloaded, and an interrupted download must resume. */
class ModelDownloaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val content = ByteArray(200_000) { (it * 31).toByte() }

    private fun specFor(bytes: ByteArray, sha256: String = sha256Of(bytes)) = ModelSpec(
        id = "m", displayName = "Model", fileName = "m.cact", url = "https://example.invalid/m.cact",
        sizeBytes = bytes.size.toLong(), sha256 = sha256,
    )

    private fun sha256Of(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class FakeSource(
        private val bytes: ByteArray,
        private val honourRange: Boolean = true,
        private val failAfter: Int? = null,
        private val reportedTotal: Long = bytes.size.toLong(),
    ) : RangeSource {
        val opens = mutableListOf<Long>()

        override fun open(url: String, from: Long): RangeStream {
            opens += from
            val start = if (honourRange) from.toInt() else 0
            val data = bytes.copyOfRange(start, bytes.size)
            val input: InputStream = if (failAfter == null) {
                ByteArrayInputStream(data)
            } else {
                object : InputStream() {
                    private val inner = ByteArrayInputStream(data)
                    private var served = 0
                    override fun read(): Int = inner.read()
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if (served >= failAfter) throw IOException("connection lost")
                        val n = inner.read(b, off, minOf(len, failAfter - served))
                        if (n > 0) served += n
                        return n
                    }
                }
            }
            return RangeStream(startsAt = start.toLong(), totalBytes = reportedTotal, input = input)
        }
    }

    private fun downloader(source: RangeSource) = ModelDownloader(source, folder.root)

    @Test
    fun `a download is verified and published under its final name`() = runTest {
        val progress = mutableListOf<Long>()

        downloader(FakeSource(content)).download(specFor(content)) { done, _ -> progress += done }

        assertThat(File(folder.root, "m.cact").readBytes()).isEqualTo(content)
        assertThat(File(folder.root, "m.cact.part").exists()).isFalse()
        assertThat(progress.last()).isEqualTo(content.size.toLong())
    }

    @Test
    fun `a partial file is resumed from where it stopped`() = runTest {
        File(folder.root, "m.cact.part").writeBytes(content.copyOfRange(0, 50_000))
        val source = FakeSource(content)

        downloader(source).download(specFor(content)) { _, _ -> }

        assertThat(source.opens).containsExactly(50_000L)
        assertThat(File(folder.root, "m.cact").readBytes()).isEqualTo(content)
    }

    @Test
    fun `a server that ignores the range restarts the download`() = runTest {
        File(folder.root, "m.cact.part").writeBytes(content.copyOfRange(0, 50_000))

        downloader(FakeSource(content, honourRange = false)).download(specFor(content)) { _, _ -> }

        assertThat(File(folder.root, "m.cact").readBytes()).isEqualTo(content)
    }

    @Test
    fun `a file that does not match its checksum is discarded`() = runTest {
        val spec = specFor(content, sha256 = "0".repeat(64))

        val failure = runCatching { downloader(FakeSource(content)).download(spec) { _, _ -> } }.exceptionOrNull()

        assertThat(failure).isInstanceOf(ModelDownloadException::class.java)

        assertThat(File(folder.root, "m.cact").exists()).isFalse()
        assertThat(File(folder.root, "m.cact.part").exists()).isFalse()
    }

    @Test
    fun `a server reporting a different size is refused`() = runTest {
        val failure = runCatching {
            downloader(FakeSource(content, reportedTotal = 12L)).download(specFor(content)) { _, _ -> }
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(ModelDownloadException::class.java)
        assertThat(File(folder.root, "m.cact").exists()).isFalse()
    }

    @Test
    fun `an interrupted download keeps its progress and the next attempt resumes`() = runTest {
        val spec = specFor(content)
        val flaky = FakeSource(content, failAfter = 70_000)

        val failure = runCatching { downloader(flaky).download(spec) { _, _ -> } }.exceptionOrNull()
        assertThat(failure).isInstanceOf(ModelDownloadException::class.java)
        val kept = File(folder.root, "m.cact.part").length()
        assertThat(kept).isGreaterThan(0L)
        assertThat(File(folder.root, "m.cact").exists()).isFalse()

        val steady = FakeSource(content)
        downloader(steady).download(spec) { _, _ -> }

        assertThat(steady.opens).containsExactly(kept)
        assertThat(File(folder.root, "m.cact").readBytes()).isEqualTo(content)
    }

    @Test
    fun `a partial file does not count as downloaded`() {
        File(folder.root, "m.cact.part").writeBytes(content.copyOfRange(0, 10))

        assertThat(downloader(FakeSource(content)).isDownloaded(specFor(content))).isFalse()
    }
}
