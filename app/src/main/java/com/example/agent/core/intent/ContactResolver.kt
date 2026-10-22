package com.example.agent.core.intent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** One phone number of one contact. A contact with several numbers yields several rows. */
data class Contact(val id: String, val name: String, val number: String)

/** The device contacts. A port so tests do not need a ContentResolver. */
interface ContactsSource {
    fun isPermissionGranted(): Boolean
    fun phoneRows(): List<Contact>
}

/** [ContactsSource] backed by ContactsContract; needs READ_CONTACTS. */
class ContentResolverContactsSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : ContactsSource {

    override fun isPermissionGranted(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    override fun phoneRows(): List<Contact> {
        if (!isPermissionGranted()) return emptyList()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val rows = mutableListOf<Contact>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1) ?: continue
                val number = cursor.getString(2) ?: continue
                rows += Contact(cursor.getString(0) ?: name, name, number)
            }
        }
        return rows
    }
}

/** The outcome of looking up a spoken contact name. */
sealed interface ContactMatch {
    data class Found(val contact: Contact) : ContactMatch
    data class NotFound(val spoken: String) : ContactMatch
    data class Ambiguous(val spoken: String, val names: List<String>) : ContactMatch

    /** READ_CONTACTS is not granted; the UI should ask for it. */
    data object PermissionNeeded : ContactMatch
}

/** Finds the one contact the user means: exact name, then a unique name or word prefix. */
class ContactResolver @Inject constructor(private val source: ContactsSource) {

    fun resolve(spoken: String): ContactMatch {
        if (!source.isPermissionGranted()) return ContactMatch.PermissionNeeded
        val query = spoken.lowercase().trim().replace(Regex("\\s+"), " ")
        if (query.isEmpty()) return ContactMatch.NotFound(spoken)
        val contacts = source.phoneRows().distinctBy { it.id }

        decide(spoken, contacts.filter { it.name.trim().lowercase() == query })?.let { return it }
        decide(spoken, contacts.filter { startsWithWord(it.name.lowercase(), query) })?.let { return it }
        return ContactMatch.NotFound(spoken)
    }

    private fun startsWithWord(name: String, query: String): Boolean =
        name.startsWith(query) || name.split(" ", "-").any { it.isNotEmpty() && it.startsWith(query) }

    private fun decide(spoken: String, matches: List<Contact>): ContactMatch? = when (matches.size) {
        0 -> null
        1 -> ContactMatch.Found(matches.single())
        else -> ContactMatch.Ambiguous(spoken, matches.map { it.name }.distinct().sorted())
    }
}
