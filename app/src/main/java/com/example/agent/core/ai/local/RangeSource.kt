package com.example.agent.core.ai.local

import java.io.Closeable
import java.io.InputStream

/**
 * An open download.
 *
 * @property startsAt the byte the stream actually begins at. It equals the requested offset
 * when the server honoured the range, and is 0 when it ignored it and sent the whole file.
 * @property totalBytes the size of the complete file.
 */
class RangeStream(
    val startsAt: Long,
    val totalBytes: Long,
    val input: InputStream,
    private val onClose: () -> Unit = {},
) : Closeable {
    override fun close() {
        input.close()
        onClose()
    }
}

/** Where model bytes come from, so the downloader can be tested without a network. */
fun interface RangeSource {
    /** Opens [url] from byte [from]; throws [java.io.IOException] when it cannot. */
    fun open(url: String, from: Long): RangeStream
}
