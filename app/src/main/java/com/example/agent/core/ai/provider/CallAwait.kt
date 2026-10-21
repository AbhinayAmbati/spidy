package com.example.agent.core.ai.provider

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs the call without blocking a thread, and cancels the HTTP request when the coroutine is
 * cancelled.
 *
 * `Call.execute()` ignores coroutine cancellation, so pressing Stop during a model call used
 * to leave the request running until it answered or timed out.
 */
internal suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }
    })
}
