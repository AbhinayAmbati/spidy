package com.example.agent.domain.model

enum class AppearanceMode {
    System, Light, Dark;

    companion object {
        fun fromKey(key: String?): AppearanceMode =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: System
    }
}
