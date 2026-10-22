package com.example.agent.core.intent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContactResolverTest {

    private class FakeContacts(
        private val granted: Boolean = true,
        private val rows: List<Contact>,
    ) : ContactsSource {
        override fun isPermissionGranted() = granted
        override fun phoneRows() = if (granted) rows else emptyList()
    }

    private val rows = listOf(
        Contact("1", "Bhavana", "+91 90000 00001"),
        Contact("2", "Bhavana Reddy", "+91 90000 00002"),
        Contact("3", "Ravi Kumar", "+91 90000 00003"),
        Contact("3", "Ravi Kumar", "+91 90000 00033"),
        Contact("4", "Ravi Teja", "+91 90000 00004"),
        Contact("5", "Mom", "+91 90000 00005"),
        Contact("6", "Priya Sharma", "+91 90000 00006"),
    )

    private val resolver = ContactResolver(FakeContacts(rows = rows))

    private fun foundName(spoken: String) = (resolver.resolve(spoken) as? ContactMatch.Found)?.contact?.name

    @Test
    fun `exact match ignores case`() {
        assertThat(foundName("bhavana")).isEqualTo("Bhavana")
        assertThat(foundName("MOM")).isEqualTo("Mom")
        assertThat(foundName("  priya   sharma ")).isEqualTo("Priya Sharma")
    }

    @Test
    fun `exact match wins over a longer name that starts the same`() {
        assertThat(foundName("bhavana")).isEqualTo("Bhavana")
    }

    @Test
    fun `a unique prefix matches`() {
        assertThat(foundName("priya")).isEqualTo("Priya Sharma")
        assertThat(foundName("sharma")).isEqualTo("Priya Sharma")
        assertThat(foundName("ravi t")).isEqualTo("Ravi Teja")
    }

    @Test
    fun `a contact with two numbers counts once`() {
        assertThat(foundName("ravi kumar")).isEqualTo("Ravi Kumar")
    }

    @Test
    fun `several matches are ambiguous and listed`() {
        val match = resolver.resolve("ravi")
        assertThat(match).isInstanceOf(ContactMatch.Ambiguous::class.java)
        assertThat((match as ContactMatch.Ambiguous).names).containsExactly("Ravi Kumar", "Ravi Teja").inOrder()
    }

    @Test
    fun `two contacts with the same name are ambiguous`() {
        val twin = ContactResolver(
            FakeContacts(rows = listOf(Contact("1", "Sam", "1"), Contact("2", "Sam", "2"))),
        )
        assertThat(twin.resolve("sam")).isInstanceOf(ContactMatch.Ambiguous::class.java)
    }

    @Test
    fun `a hallucinated or empty name is not found`() {
        listOf("zaphod", "", "  ", "bhavanaa", "the alarm").forEach {
            assertThat(resolver.resolve(it)).isInstanceOf(ContactMatch.NotFound::class.java)
        }
    }

    @Test
    fun `missing permission is its own result`() {
        val denied = ContactResolver(FakeContacts(granted = false, rows = rows))
        assertThat(denied.resolve("bhavana")).isEqualTo(ContactMatch.PermissionNeeded)
    }
}
