package com.example.agent.core.actions

import com.example.agent.core.intent.ActionResult
import com.example.agent.core.intent.Contact
import com.example.agent.core.intent.ContactResolver
import com.example.agent.core.intent.ContactsSource
import com.example.agent.core.intent.SpideyIntent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AndroidActionExecutorTest {

    private class FakeLauncher(private val error: String? = null) : ActionLauncher {
        val requests = mutableListOf<LaunchRequest>()
        override fun launch(request: LaunchRequest): String? {
            requests += request
            return error
        }
    }

    private class ThrowingLauncher : ActionLauncher {
        override fun launch(request: LaunchRequest): String? = throw SecurityException("nope")
    }

    private class FakeContacts(private val granted: Boolean = true) : ContactsSource {
        override fun isPermissionGranted() = granted
        override fun phoneRows() = listOf(
            Contact("1", "Bhavana", "+919000000001"),
            Contact("2", "Ravi Kumar", "+919000000002"),
            Contact("3", "Ravi Teja", "+919000000003"),
        )
    }

    private fun executor(launcher: ActionLauncher, granted: Boolean = true) =
        AndroidActionExecutor(launcher, ContactResolver(FakeContacts(granted)))

    @Test
    fun `open app launches the package`() = runTest {
        val launcher = FakeLauncher()
        val result = executor(launcher).execute(SpideyIntent.OpenApp("com.instagram.android"))
        assertThat(result).isInstanceOf(ActionResult.Done::class.java)
        assertThat(launcher.requests).containsExactly(LaunchRequest.OpenPackage("com.instagram.android"))
    }

    @Test
    fun `message opens the composer prefilled and never sends`() = runTest {
        val launcher = FakeLauncher()
        val result = executor(launcher).execute(SpideyIntent.SendMessage("Bhavana", "the alarm is not working"))
        assertThat(result).isInstanceOf(ActionResult.Done::class.java)
        assertThat(launcher.requests)
            .containsExactly(LaunchRequest.ComposeSms("+919000000001", "the alarm is not working"))
    }

    @Test
    fun `call opens the dialer`() = runTest {
        val launcher = FakeLauncher()
        executor(launcher).execute(SpideyIntent.CallContact("Bhavana"))
        assertThat(launcher.requests).containsExactly(LaunchRequest.Dial("+919000000001"))
    }

    @Test
    fun `alarm passes hour and minute`() = runTest {
        val launcher = FakeLauncher()
        executor(launcher).execute(SpideyIntent.SetAlarm("18:30"))
        executor(launcher).execute(SpideyIntent.SetAlarm("6 am"))
        assertThat(launcher.requests).containsExactly(LaunchRequest.Alarm(18, 30), LaunchRequest.Alarm(6, 0)).inOrder()
    }

    @Test
    fun `timer passes seconds`() = runTest {
        val launcher = FakeLauncher()
        executor(launcher).execute(SpideyIntent.SetTimer(5))
        assertThat(launcher.requests).containsExactly(LaunchRequest.Timer(300))
    }

    @Test
    fun `bad values fail without launching`() = runTest {
        val launcher = FakeLauncher()
        val exec = executor(launcher)
        assertThat(exec.execute(SpideyIntent.SetAlarm("banana"))).isInstanceOf(ActionResult.Failed::class.java)
        assertThat(exec.execute(SpideyIntent.SetTimer(0))).isInstanceOf(ActionResult.Failed::class.java)
        assertThat(exec.execute(SpideyIntent.SetTimer(1441))).isInstanceOf(ActionResult.Failed::class.java)
        assertThat(exec.execute(SpideyIntent.CallContact("zaphod"))).isInstanceOf(ActionResult.Failed::class.java)
        assertThat(exec.execute(SpideyIntent.CallContact("Ravi"))).isInstanceOf(ActionResult.Failed::class.java)
        assertThat(launcher.requests).isEmpty()
    }

    @Test
    fun `missing contacts permission fails without launching`() = runTest {
        val launcher = FakeLauncher()
        val result = executor(launcher, granted = false).execute(SpideyIntent.CallContact("Bhavana"))
        assertThat(result).isInstanceOf(ActionResult.Failed::class.java)
        assertThat(launcher.requests).isEmpty()
    }

    @Test
    fun `a launcher error becomes a failed result`() = runTest {
        val result = executor(FakeLauncher("No app on this phone can do that."))
            .execute(SpideyIntent.SetTimer(5))
        assertThat(result).isEqualTo(ActionResult.Failed("No app on this phone can do that."))
    }

    @Test
    fun `a security exception becomes a failed result`() = runTest {
        val result = executor(ThrowingLauncher()).execute(SpideyIntent.OpenApp("com.x"))
        assertThat(result).isInstanceOf(ActionResult.Failed::class.java)
    }
}
