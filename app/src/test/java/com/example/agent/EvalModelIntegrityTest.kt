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

/**
 * A model that is already installed and good must survive every way a re-download can go wrong:
 * corrupt bytes, a stream that ends early, a dropped connection, a lying server, a poisoned
 * partial file. Only a download that passes the size and SHA-256 pins may replace it.
 */
class EvalModelIntegrityTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val good = ByteArray(150_000) { (it * 7).toByte() }
    private val newer = ByteArray(150_000) { (it * 13 + 5).toByte() }

    private fun sha256Of(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun specFor(bytes: ByteArray) = ModelSpec(
        id = "m", displayName = "Model", fileName = "m.cact", url = "https://example.invalid/m.cact",
        sizeBytes = bytes.size.toLong(), sha256 = sha256Of(bytes),
    )

    private fun installGood(): File =
        File(folder.root, "m.cact").also { it.writeBytes(good) }

    private fun part() = File(folder.root, "m.cact.part")

    private fun downloader(source: RangeSource) = ModelDownloader(source, folder.root)

    private fun assertGoodFileUntouched() {
        assertThat(File(folder.root, "m.cact").readBytes()).isEqualTo(good)
    }

    /** Serves [bytes] from the requested offset; the stream ends cleanly after [stopAfter] bytes. */
    private class ScriptedSource(
        private val bytes: ByteArray,
        private val stopAfter: Int = Int.MAX_VALUE,
        private val dropAfter: Int? = null,
        private val reportedTotal: Long = bytes.size.toLong(),
        private val unreachable: Boolean = false,
    ) : RangeSource {
        override fun open(url: String, from: Long): RangeStream {
            if (unreachable) throw IOException("no route to host")
            val data = bytes.copyOfRange(from.toInt(), bytes.size)
            val input: InputStream = object : InputStream() {
                private val inner = ByteArrayInputStream(data)
                private var served = 0
                override fun read(): Int = inner.read()
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (dropAfter != null && served >= dropAfter) throw IOException("connection lost")
                    if (served >= stopAfter) return -1
                    val limit = minOf(len, (dropAfter ?: stopAfter) - served)
                    val n = inner.read(b, off, limit)
                    if (n > 0) served += n
                    return n
                }
            }
            return RangeStream(startsAt = from, totalBytes = reportedTotal, input = input)
        }
    }

    private suspend fun failureOf(downloader: ModelDownloader, spec: ModelSpec): Throwable? =
        runCatching { downloader.download(spec) { _, _ -> } }.exceptionOrNull()

    @Test
    fun `corrupt bytes of the right size never replace a good model`() = runTest {
        installGood()
        val corrupt = good.copyOf().also { it[70_000] = (it[70_000] + 1).toByte() }

        val failure = failureOf(downloader(ScriptedSource(corrupt)), specFor(good))

        assertThat(failure).isInstanceOf(ModelDownloadException::class.java)
        assertGoodFileUntouched()
        assertThat(part().exists()).isFalse()
    }

    @Test
    fun `a stream that ends early never replaces a good model`() = runTest {
        installGood()

        val failure = failureOf(downloader(ScriptedSource(good, stopAfter = 90_000)), specFor(good))

        assertThat(failure).isInstanceOf(ModelDownloadException::class.java)
        assertGoodFileUntouched()
        assertThat(part().length()).isEqualTo(90_000L)
    }

    @Test
    fun `a dropped connection never replaces a good model`() = runTest {
        installGood()

        val failure = failureOf(downloader(ScriptedSource(good, dropAfter = 40_000)), specFor(good))

        assertThat(failure).isInstanceOf(ModelDownloadException::class.java)
        assertGoodFileUntouched()
    }

    @Test
    fun `an unreachable server never replaces a good model`() = runTest {
        installGood()

        val failure = failureOf(downloader(ScriptedSource(good, unreachable = true)), specFor(good))

        assertThat(failure).isInstanceOf(ModelDownloadException::class.java)
        assertGoodFileUntouched()
    }

    @Test
    fun `a server reporting another size never replaces a good model`() = runTest {
        installGood()

        val failure = failureOf(downloader(ScriptedSource(newer, reportedTotal = 9L)), specFor(newer))

        assertThat(failure).isInstanceOf(ModelDownloadException::class.java)
        assertGoodFileUntouched()
    }

    @Test
    fun `a poisoned partial file is caught by the checksum and the good model survives`() = runTest {
        installGood()
        part().writeBytes(ByteArray(50_000) { 0x5A })

        val failure = failureOf(downloader(ScriptedSource(good)), specFor(good))

        assertThat(failure).isInstanceOf(ModelDownloadException::class.java)
        assertGoodFileUntouched()
        assertThat(part().exists()).isFalse()
    }

    @Test
    fun `an oversized leftover partial is discarded and the download starts over`() = runTest {
        installGood()
        part().writeBytes(ByteArray(good.size + 10))

        downloader(ScriptedSource(good)).download(specFor(good)) { _, _ -> }

        assertGoodFileUntouched()
        assertThat(part().exists()).isFalse()
    }

    @Test
    fun `after failed attempts a correct download still installs`() = runTest {
        installGood()
        val spec = specFor(newer)

        failureOf(downloader(ScriptedSource(newer, dropAfter = 30_000)), spec)
        assertGoodFileUntouched()
        failureOf(downloader(ScriptedSource(newer.copyOf().also { it[100_000] = (it[100_000] + 1).toByte() })), spec)
        assertGoodFileUntouched()

        downloader(ScriptedSource(newer)).download(spec) { _, _ -> }

        assertThat(File(folder.root, "m.cact").readBytes()).isEqualTo(newer)
        assertThat(part().exists()).isFalse()
    }

    @Test
    fun `a good model still counts as downloaded after every failed attempt`() = runTest {
        installGood()
        val spec = specFor(good)

        failureOf(downloader(ScriptedSource(good, stopAfter = 10)), spec)
        failureOf(downloader(ScriptedSource(good, unreachable = true)), spec)

        assertThat(downloader(ScriptedSource(good)).isDownloaded(spec)).isTrue()
    }
}
