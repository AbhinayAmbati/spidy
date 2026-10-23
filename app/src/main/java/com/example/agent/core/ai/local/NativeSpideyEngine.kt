package com.example.agent.core.ai.local

import java.io.File

/** [LocalToolEngine] backed by the native engine and the downloaded `spidey.cact`. */
class NativeSpideyEngine(private val modelFile: File) : LocalToolEngine {

    override fun start(systemPrompt: String, toolsJson: String) {
        ensureLoaded()
        val result = SpideyNative.nativeInit(systemPrompt.toUtf8(), toolsJson.toUtf8(), null)
        if (result < 0) throw LocalEngineException("Could not prepare the on-device model: ${lastError()}")
    }

    override fun reset() {
        if (SpideyNative.isAvailable) SpideyNative.nativeReset()
    }

    override fun complete(input: String, maxNewTokens: Int): String {
        ensureLoaded()
        val answer = SpideyNative.nativeComplete(input.toUtf8(), maxNewTokens)
            ?: throw LocalEngineException("The on-device model failed: ${lastError()}")
        return String(answer, Charsets.UTF_8)
    }

    private fun ensureLoaded() = load(modelFile)

    private fun String.toUtf8(): ByteArray = toByteArray(Charsets.UTF_8)

    companion object {
        // The engine maps a model once per process, so a second engine over the same file must
        // not load it again.
        private val loadedPaths = mutableSetOf<String>()

        /** Loads a model file into the engine once per process; Whistle uses this too. */
        internal fun load(file: File) {
            if (!SpideyNative.isAvailable) {
                throw LocalEngineException("This build does not include the on-device engine.")
            }
            synchronized(loadedPaths) {
                if (file.absolutePath in loadedPaths) return
                if (!file.isFile) throw LocalEngineException("The on-device model has not been downloaded yet.")

                val result = SpideyNative.nativeLoad(file.absolutePath.toByteArray(Charsets.UTF_8))
                if (result < 0) throw LocalEngineException("Could not load the on-device model: ${lastError()}")
                loadedPaths += file.absolutePath
            }
        }

        internal fun lastError(): String =
            String(SpideyNative.nativeLastError(), Charsets.UTF_8).ifBlank { "unknown error" }
    }
}
