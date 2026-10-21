package com.example.agent.core.ai.local

/** Where one on-device model stands. */
sealed interface ModelStatus {
    data object NotDownloaded : ModelStatus

    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : ModelStatus {
        val percent: Int
            get() = if (totalBytes <= 0) 0 else ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
    }

    data object Ready : ModelStatus

    data class Failed(val message: String) : ModelStatus
}
