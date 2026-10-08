package com.example.agent.core.common

import android.content.Context
import android.provider.Settings

/**
 * Respects the system "Remove animations" accessibility setting. When the user has
 * turned animations off, Spidey drops its fades, pulses and progress tweens rather
 * than playing them anyway.
 */
object Motion {
    fun animationsEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }.getOrDefault(true)
}
