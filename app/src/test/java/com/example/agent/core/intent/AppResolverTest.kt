package com.example.agent.core.intent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppResolverTest {

    private val installed = listOf(
        InstalledApp("Instagram", "com.instagram.android"),
        InstalledApp("YouTube", "com.google.android.youtube"),
        InstalledApp("YouTube Music", "com.google.android.apps.youtube.music"),
        InstalledApp("Chrome", "com.android.chrome"),
        InstalledApp("WhatsApp", "com.whatsapp"),
        InstalledApp("WhatsApp Business", "com.whatsapp.w4b"),
        InstalledApp("Maps", "com.google.android.apps.maps"),
        InstalledApp("Calculator", "com.android.calculator2"),
        InstalledApp("Clock", "com.android.deskclock"),
        InstalledApp("Notes", "com.example.notes"),
        InstalledApp("Notes Pro", "com.example.notespro"),
        InstalledApp("Photo Editor", "com.a.photoeditor"),
        InstalledApp("Photo Editor", "com.b.photoeditor"),
    )

    private val resolver = AppResolver { installed }

    private fun packageFor(spoken: String): String? =
        (resolver.resolve(spoken) as? AppMatch.Found)?.app?.packageName

    @Test
    fun `resolves names by label ignoring case punctuation and spaces`() {
        val cases = mapOf(
            "instagram" to "com.instagram.android",
            "INSTAGRAM" to "com.instagram.android",
            "you tube" to "com.google.android.youtube",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "whatsapp" to "com.whatsapp",
            "whats app" to "com.whatsapp",
            "calculator" to "com.android.calculator2",
            "  clock " to "com.android.deskclock",
            "notes" to "com.example.notes",
            "youtube music" to "com.google.android.apps.youtube.music",
        )
        cases.forEach { (spoken, pkg) -> assertThat(packageFor(spoken)).isEqualTo(pkg) }
    }

    @Test
    fun `resolves common aliases`() {
        val cases = mapOf(
            "insta" to "com.instagram.android",
            "ig" to "com.instagram.android",
            "yt" to "com.google.android.youtube",
            "wa" to "com.whatsapp",
            "google maps" to "com.google.android.apps.maps",
            "gmaps" to "com.google.android.apps.maps",
        )
        cases.forEach { (spoken, pkg) -> assertThat(packageFor(spoken)).isEqualTo(pkg) }
    }

    @Test
    fun `resolves a close misspelling`() {
        assertThat(packageFor("calculater")).isEqualTo("com.android.calculator2")
        assertThat(packageFor("instgram")).isEqualTo("com.instagram.android")
    }

    @Test
    fun `resolves a unique partial name`() {
        assertThat(packageFor("calc")).isEqualTo("com.android.calculator2")
    }

    @Test
    fun `an unknown or hallucinated app is not found`() {
        listOf("tiktok", "zorblax", "", "  ", "!!!", "xq", "banking", "the").forEach {
            assertThat(resolver.resolve(it)).isInstanceOf(AppMatch.NotFound::class.java)
        }
    }

    @Test
    fun `an ambiguous partial name lists the candidates and never guesses`() {
        val match = resolver.resolve("photo")
        // Two installs share one label but have different packages: that is ambiguous.
        assertThat(match).isInstanceOf(AppMatch.Ambiguous::class.java)
        val ambiguous = match as AppMatch.Ambiguous
        assertThat(ambiguous.candidates.map { it.packageName })
            .containsExactly("com.a.photoeditor", "com.b.photoeditor")
    }

    @Test
    fun `two apps with the same label are ambiguous`() {
        assertThat(resolver.resolve("photo editor")).isInstanceOf(AppMatch.Ambiguous::class.java)
    }

    @Test
    fun `a partial name matching several apps is ambiguous`() {
        assertThat(resolver.resolve("note")).isInstanceOf(AppMatch.Ambiguous::class.java)
    }

    @Test
    fun `an alias whose app is not installed falls through to not found`() {
        val resolverWithout = AppResolver { listOf(InstalledApp("Calculator", "calc")) }
        assertThat(resolverWithout.resolve("yt")).isInstanceOf(AppMatch.NotFound::class.java)
    }
}
