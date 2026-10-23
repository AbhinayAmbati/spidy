package com.example.agent.core.ai.local

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one lock around the native engine. It is process-global and not thread-safe, so routing a
 * sentence and transcribing speech must never overlap: both take this gate for each native call.
 */
@Singleton
class EngineGate @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
