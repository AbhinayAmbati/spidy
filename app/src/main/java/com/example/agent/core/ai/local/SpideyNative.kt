package com.example.agent.core.ai.local

/**
 * The JNI surface of the Cactus Spidey engine (app/src/main/cpp/needle_jni.cpp).
 *
 * Strings cross as UTF-8 byte arrays so characters outside the BMP survive. The engine holds
 * one process-global, non-thread-safe model per kind, so nothing here may be called
 * concurrently: [NativeSpideyEngine] serialises access.
 *
 * Every function returns a negative number (or null) on failure; [nativeLastError] then says
 * why. The native library is only built after tools/fetch_needle_engine.py has fetched the
 * engine, so [isAvailable] is false in a build without it.
 */
internal object SpideyNative {

    val isAvailable: Boolean by lazy {
        try {
            System.loadLibrary("spidey_engine")
            true
        } catch (missing: UnsatisfiedLinkError) {
            false
        }
    }

    @JvmStatic external fun nativeLoad(path: ByteArray): Int

    /** Bit set of the models loaded: 1 for text (Spidey), 2 for speech (Whistle). */
    @JvmStatic external fun nativeModels(): Int

    @JvmStatic external fun nativeLastError(): ByteArray

    @JvmStatic external fun nativeInit(systemPrompt: ByteArray, toolsJson: ByteArray, toolIndexPath: ByteArray?): Int

    @JvmStatic external fun nativeComplete(input: ByteArray, maxNewTokens: Int): ByteArray?

    @JvmStatic external fun nativeReset()

    @JvmStatic external fun nativeTranscribe(pcm: FloatArray, language: ByteArray?, keywords: ByteArray?): ByteArray?
}
