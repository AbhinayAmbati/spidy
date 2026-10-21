package com.example.agent.core.ai.local

import java.io.File

/** [LocalToolEngine] backed by the native Needle engine and a downloaded `needle3.cact`. */
class NativeNeedleEngine(private val modelFile: File) : LocalToolEngine {

    override fun start(systemPrompt: String, toolsJson: String) {
        ensureLoaded()
        val result = NeedleNative.nativeInit(systemPrompt.toUtf8(), toolsJson.toUtf8(), null)
        if (result < 0) throw LocalEngineException("Could not prepare the on-device model: ${lastError()}")
    }

    override fun reset() {
        if (NeedleNative.isAvailable) NeedleNative.nativeReset()
    }

    override fun complete(input: String, maxNewTokens: Int): String {
        ensureLoaded()
        val answer = NeedleNative.nativeComplete(input.toUtf8(), maxNewTokens)
            ?: throw LocalEngineException("The on-device model failed: ${lastError()}")
        return String(answer, Charsets.UTF_8)
    }

    private fun ensureLoaded() {
        if (!NeedleNative.isAvailable) {
            throw LocalEngineException("This build does not include the on-device engine.")
        }
        synchronized(loadedPaths) {
            if (modelFile.absolutePath in loadedPaths) return
            if (!modelFile.isFile) throw LocalEngineException("The on-device model has not been downloaded yet.")

            val result = NeedleNative.nativeLoad(modelFile.absolutePath.toUtf8())
            if (result < 0) throw LocalEngineException("Could not load the on-device model: ${lastError()}")
            loadedPaths += modelFile.absolutePath
        }
    }

    private fun lastError(): String =
        String(NeedleNative.nativeLastError(), Charsets.UTF_8).ifBlank { "unknown error" }

    private fun String.toUtf8(): ByteArray = toByteArray(Charsets.UTF_8)

    private companion object {
        // The engine maps a model once per process, so a second engine over the same file must
        // not load it again.
        val loadedPaths = mutableSetOf<String>()
    }
}
