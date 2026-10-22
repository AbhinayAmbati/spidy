package com.example.agent.core.intent

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DeviceIntentGuardTest {

    private class FakeContacts(
        private val granted: Boolean = true,
        private val rows: List<Contact> = emptyList(),
    ) : ContactsSource {
        override fun isPermissionGranted() = granted
        override fun phoneRows() = rows
    }

    private val apps = InstalledApps {
        listOf(
            InstalledApp("Instagram", "com.instagram.android"),
            InstalledApp("Notes", "com.example.notes"),
            InstalledApp("Notes Pro", "com.example.notespro"),
        )
    }

    private val contactRows = listOf(
        Contact("1", "Bhavana", "+919000000001"),
        Contact("2", "Ravi Kumar", "+919000000002"),
        Contact("3", "Ravi Teja", "+919000000003"),
    )

    private fun guard(granted: Boolean = true) =
        DeviceIntentGuard(AppResolver(apps), ContactResolver(FakeContacts(granted, contactRows)))

    private fun allow(verdict: GuardVerdict) = verdict as GuardVerdict.Allow
    private fun reject(verdict: GuardVerdict) = (verdict as GuardVerdict.Reject).reason

    @Test
    fun `sending a message always needs confirmation and is summarised`() = runTest {
        val verdict = allow(guard().check(SpideyIntent.SendMessage("bhavana", "the alarm is not working")))
        assertThat(verdict.needsConfirmation).isTrue()
        assertThat(verdict.summary).isEqualTo("Message Bhavana: \"the alarm is not working\"")
        assertThat(verdict.intent).isEqualTo(SpideyIntent.SendMessage("Bhavana", "the alarm is not working"))
    }

    @Test
    fun `calling always needs confirmation`() = runTest {
        val verdict = allow(guard().check(SpideyIntent.CallContact("bhavana")))
        assertThat(verdict.needsConfirmation).isTrue()
        assertThat(verdict.summary).isEqualTo("Call Bhavana")
        assertThat(verdict.intent).isEqualTo(SpideyIntent.CallContact("Bhavana"))
    }

    @Test
    fun `opening an app resolves the package without confirmation`() = runTest {
        val verdict = allow(guard().check(SpideyIntent.OpenApp("insta")))
        assertThat(verdict.needsConfirmation).isFalse()
        assertThat(verdict.summary).isEqualTo("Open Instagram")
        assertThat(verdict.intent).isEqualTo(SpideyIntent.OpenApp("com.instagram.android"))
    }

    @Test
    fun `alarm and timer need no confirmation`() = runTest {
        val alarm = allow(guard().check(SpideyIntent.SetAlarm("half past six pm")))
        assertThat(alarm.needsConfirmation).isFalse()
        assertThat(alarm.intent).isEqualTo(SpideyIntent.SetAlarm("18:30"))
        assertThat(alarm.summary).isEqualTo("Set an alarm for 6:30 PM")

        val timer = allow(guard().check(SpideyIntent.SetTimer(10)))
        assertThat(timer.needsConfirmation).isFalse()
        assertThat(timer.summary).isEqualTo("Set a timer for 10 minutes")
        assertThat(allow(guard().check(SpideyIntent.SetTimer(1))).summary).isEqualTo("Set a timer for 1 minute")
    }

    @Test
    fun `unknown and ambiguous apps are rejected`() = runTest {
        assertThat(reject(guard().check(SpideyIntent.OpenApp("tiktok")))).contains("tiktok")
        val ambiguous = reject(guard().check(SpideyIntent.OpenApp("note")))
        assertThat(ambiguous).contains("Notes")
        assertThat(ambiguous).contains("Notes Pro")
    }

    @Test
    fun `a hallucinated contact is rejected for message and call`() = runTest {
        assertThat(reject(guard().check(SpideyIntent.SendMessage("zaphod", "hi")))).contains("zaphod")
        assertThat(reject(guard().check(SpideyIntent.CallContact("zaphod")))).contains("zaphod")
    }

    @Test
    fun `an ambiguous contact lists the candidates`() = runTest {
        val reason = reject(guard().check(SpideyIntent.CallContact("ravi")))
        assertThat(reason).contains("Ravi Kumar")
        assertThat(reason).contains("Ravi Teja")
    }

    @Test
    fun `an empty message is rejected`() = runTest {
        assertThat(reject(guard().check(SpideyIntent.SendMessage("bhavana", "  ")))).contains("empty")
    }

    @Test
    fun `missing contacts permission gives a distinct rejection`() = runTest {
        val denied = guard(granted = false)
        val send = denied.check(SpideyIntent.SendMessage("bhavana", "hi"))
        val call = denied.check(SpideyIntent.CallContact("bhavana"))
        assertThat(DeviceIntentGuard.isPermissionRejection(send)).isTrue()
        assertThat(DeviceIntentGuard.isPermissionRejection(call)).isTrue()
        assertThat(DeviceIntentGuard.isPermissionRejection(guard().check(SpideyIntent.CallContact("zaphod"))))
            .isFalse()
    }

    @Test
    fun `nonsense times and out of range timers are rejected`() = runTest {
        listOf("banana", "25:00", "").forEach {
            assertThat(guard().check(SpideyIntent.SetAlarm(it))).isInstanceOf(GuardVerdict.Reject::class.java)
        }
        listOf(0, -1, 1441, Int.MAX_VALUE).forEach {
            assertThat(guard().check(SpideyIntent.SetTimer(it))).isInstanceOf(GuardVerdict.Reject::class.java)
        }
        assertThat(guard().check(SpideyIntent.SetTimer(1440))).isInstanceOf(GuardVerdict.Allow::class.java)
    }
}
