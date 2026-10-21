package com.example.agent.core.ai.local

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** [RangeSource] over HTTP, resuming with a `Range` header. */
class OkHttpRangeSource(private val client: OkHttpClient) : RangeSource {

    override fun open(url: String, from: Long): RangeStream {
        val request = Request.Builder()
            .url(url)
            .apply { if (from > 0) header("Range", "bytes=$from-") }
            .build()

        val response = client.newCall(request).execute()
        val body = response.body
        if (!response.isSuccessful || body == null) {
            val code = response.code
            response.close()
            throw IOException("The download server answered $code")
        }

        // 206 honoured the range; 200 sent the whole file from the start.
        val resumed = response.code == 206
        val total = if (resumed) totalFromContentRange(response.header("Content-Range")) else body.contentLength()
        return RangeStream(
            startsAt = if (resumed) from else 0L,
            totalBytes = total,
            input = body.byteStream(),
            onClose = { response.close() },
        )
    }

    // "bytes 100-999/1000" -> 1000
    private fun totalFromContentRange(header: String?): Long =
        header?.substringAfterLast('/')?.trim()?.toLongOrNull() ?: -1L
}
