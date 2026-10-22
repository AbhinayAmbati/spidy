package com.example.agent.core.intent

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** One app that has a launcher icon. */
data class InstalledApp(val label: String, val packageName: String)

/** The installed launcher apps. A port so tests do not need a PackageManager. */
fun interface InstalledApps {
    fun launchableApps(): List<InstalledApp>
}

/** [InstalledApps] backed by the PackageManager (visibility comes from `<queries>`). */
class PackageManagerInstalledApps @Inject constructor(
    @ApplicationContext private val context: Context,
) : InstalledApps {
    @Suppress("DEPRECATION")
    override fun launchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0).map {
            InstalledApp(label = it.loadLabel(pm).toString(), packageName = it.activityInfo.packageName)
        }
    }
}

/** The outcome of looking up a spoken app name. */
sealed interface AppMatch {
    data class Found(val app: InstalledApp) : AppMatch
    data class NotFound(val spoken: String) : AppMatch
    data class Ambiguous(val spoken: String, val candidates: List<InstalledApp>) : AppMatch
}

/**
 * Maps a spoken app name to one installed app: exact label, then normalised label, then a known
 * alias, then fuzzy. Several equally good matches are reported as ambiguous, never guessed.
 */
class AppResolver @Inject constructor(private val installed: InstalledApps) {

    fun resolve(spoken: String): AppMatch {
        val query = normalise(spoken)
        if (query.isEmpty()) return AppMatch.NotFound(spoken)
        val apps = installed.launchableApps()

        pick(spoken, apps.filter { it.label.trim().equals(spoken.trim(), ignoreCase = true) })?.let { return it }
        pick(spoken, apps.filter { normalise(it.label) == query })?.let { return it }
        ALIASES[query]?.let { target ->
            pick(spoken, apps.filter { normalise(it.label) == target })?.let { return it }
        }
        if (query.length >= MIN_FUZZY_LENGTH) {
            pick(spoken, apps.filter { contains(normalise(it.label), query) })?.let { return it }
            fuzzy(spoken, query, apps)?.let { return it }
        }
        return AppMatch.NotFound(spoken)
    }

    private fun contains(label: String, query: String): Boolean =
        label.contains(query) || (label.length >= MIN_REVERSE_LENGTH && query.contains(label))

    private fun fuzzy(spoken: String, query: String, apps: List<InstalledApp>): AppMatch? {
        if (query.length < MIN_EDIT_LENGTH) return null
        val limit = if (query.length >= LONG_EDIT_LENGTH) 2 else 1
        val scored = apps.map { it to editDistance(normalise(it.label), query) }.filter { it.second <= limit }
        val best = scored.minOfOrNull { it.second } ?: return null
        return pick(spoken, scored.filter { it.second == best }.map { it.first })
    }

    private fun pick(spoken: String, matches: List<InstalledApp>): AppMatch? {
        val distinct = matches.distinctBy { it.packageName }
        return when (distinct.size) {
            0 -> null
            1 -> AppMatch.Found(distinct.single())
            else -> AppMatch.Ambiguous(spoken, distinct)
        }
    }

    private fun normalise(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            previous = current
        }
        return previous[b.length]
    }

    private companion object {
        const val MIN_FUZZY_LENGTH = 3
        const val MIN_REVERSE_LENGTH = 4
        const val MIN_EDIT_LENGTH = 5
        const val LONG_EDIT_LENGTH = 9

        /** Spoken short forms, normalised, mapped to the normalised label they mean. */
        val ALIASES = mapOf(
            "insta" to "instagram", "ig" to "instagram", "gram" to "instagram",
            "yt" to "youtube", "fb" to "facebook", "wa" to "whatsapp", "whats" to "whatsapp",
            "gmaps" to "maps", "googlemaps" to "maps", "gpay" to "googlepay",
            "googlechrome" to "chrome", "tg" to "telegram", "snap" to "snapchat",
            "twitter" to "x", "sms" to "messages", "texts" to "messages",
            "googleplaystore" to "playstore",
        )
    }
}
