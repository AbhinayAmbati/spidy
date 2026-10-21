package com.example.agent.core.ai.local

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** A model could not be downloaded or did not match its pinned checksum. */
class ModelDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Downloads a model into [directory], resuming a partial file, and only ever leaves a complete,
 * verified file under its final name.
 *
 * Bytes go to `<name>.part` and are renamed once the size and SHA-256 match the spec, so an
 * interrupted download (the app killed, the network lost) resumes instead of restarting, and a
 * truncated file can never be mistaken for a model.
 */
class ModelDownloader(
    private val source: RangeSource,
    private val directory: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    fun fileFor(spec: ModelSpec): File = File(directory, spec.fileName)

    /** Cheap enough to call on every start: the checksum was verified when it was downloaded. */
    fun isDownloaded(spec: ModelSpec): Boolean =
        fileFor(spec).let { it.isFile && it.length() == spec.sizeBytes }

    suspend fun download(spec: ModelSpec, onProgress: (downloaded: Long, total: Long) -> Unit) {
        withContext(io) {
            directory.mkdirs()
            val part = File(directory, spec.fileName + ".part")
            if (part.length() > spec.sizeBytes) part.delete()

            if (part.length() < spec.sizeBytes) fetchInto(spec, part, onProgress)
            verifyAndPublish(spec, part)
        }
    }

    private suspend fun fetchInto(spec: ModelSpec, part: File, onProgress: (Long, Long) -> Unit) {
        val stream = try {
            source.open(spec.url, part.length())
        } catch (failure: IOException) {
            throw ModelDownloadException("Could not reach the download server: ${failure.message}", failure)
        }

        stream.use { open ->
            // A server that ignored the range sent the whole file, so the partial one is useless.
            if (open.startsAt != part.length()) part.delete()
            if (open.totalBytes > 0 && open.totalBytes != spec.sizeBytes) {
                throw ModelDownloadException(
                    "${spec.displayName} is ${open.totalBytes} bytes on the server but ${spec.sizeBytes} were expected.",
                )
            }

            RandomAccessFile(part, "rw").use { out ->
                out.seek(part.length())
                val buffer = ByteArray(BUFFER_BYTES)
                var written = out.length()
                try {
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = open.input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        written += read
                        onProgress(written, spec.sizeBytes)
                    }
                } catch (failure: IOException) {
                    throw ModelDownloadException("The download was interrupted: ${failure.message}", failure)
                }
            }
        }
    }

    private fun verifyAndPublish(spec: ModelSpec, part: File) {
        if (part.length() != spec.sizeBytes) {
            throw ModelDownloadException(
                "The download stopped early (${part.length()} of ${spec.sizeBytes} bytes). Try again.",
            )
        }
        if (sha256Of(part) != spec.sha256) {
            part.delete()
            throw ModelDownloadException("${spec.displayName} did not match its checksum and was discarded. Try again.")
        }

        val target = fileFor(spec)
        target.delete()
        if (!part.renameTo(target)) {
            throw ModelDownloadException("Could not save ${spec.displayName}.")
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
    }
}
