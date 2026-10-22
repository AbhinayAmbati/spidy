package com.example.agent.core.audit

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Map-backed stand-in for the system Bundle, so parsing is tested on the JVM. */
private class FakeValues(private val map: Map<String, Any?>) : RestrictionValues {
    override fun contains(key: String) = map.containsKey(key)
    override fun getBoolean(key: String) = map[key] as? Boolean ?: false
    override fun getInt(key: String) = when (val v = map[key]) {
        is Int -> v
        is String -> v.toIntOrNull()
        else -> null
    }
    override fun getStringList(key: String) = (map[key] as? List<*>)?.filterIsInstance<String>()
}

class ManagedConfigTest {

    private fun parse(vararg pairs: Pair<String, Any?>) =
        ManagedConfigParser.parse(FakeValues(mapOf(*pairs)))

    @Test
    fun noRestrictionsMeansNoLimits() {
        val v = parse()
        assertThat(v.disableVoice).isFalse()
        assertThat(v.alwaysConfirm).isFalse()
        assertThat(v.allowedActions).isNull()
        assertThat(v.auditRetentionDays).isNull()
        assertThat(v.disableModelDownloadOnMetered).isFalse()
        assertThat(v.isManaged).isFalse()
        assertThat(v.isActionAllowed("call_contact")).isTrue()
    }

    @Test
    fun booleansAreReadAndMarkedManaged() {
        val v = parse(
            RestrictionKeys.DISABLE_VOICE to true,
            RestrictionKeys.ALWAYS_CONFIRM to true,
            RestrictionKeys.DISABLE_MODEL_DOWNLOAD_ON_METERED to true,
        )
        assertThat(v.disableVoice).isTrue()
        assertThat(v.alwaysConfirm).isTrue()
        assertThat(v.disableModelDownloadOnMetered).isTrue()
        assertThat(v.managedKeys).containsExactly(
            RestrictionKeys.DISABLE_VOICE,
            RestrictionKeys.ALWAYS_CONFIRM,
            RestrictionKeys.DISABLE_MODEL_DOWNLOAD_ON_METERED,
        )
    }

    @Test
    fun allowedActionsKeepsOnlyKnownNames() {
        val v = parse(RestrictionKeys.ALLOWED_ACTIONS to listOf("open_app", " set_timer ", "format_disk"))
        assertThat(v.allowedActions).containsExactly("open_app", "set_timer")
        assertThat(v.isActionAllowed("open_app")).isTrue()
        assertThat(v.isActionAllowed("call_contact")).isFalse()
    }

    @Test
    fun emptyOrAllUnknownAllowedActionsAllowsNothing() {
        assertThat(parse(RestrictionKeys.ALLOWED_ACTIONS to emptyList<String>()).allowedActions).isEmpty()
        assertThat(parse(RestrictionKeys.ALLOWED_ACTIONS to listOf("nope")).allowedActions).isEmpty()
    }

    @Test
    fun retentionAcceptsZeroToAYearAndIgnoresTheRest() {
        assertThat(parse(RestrictionKeys.AUDIT_RETENTION_DAYS to 0).auditRetentionDays).isEqualTo(0)
        assertThat(parse(RestrictionKeys.AUDIT_RETENTION_DAYS to 90).auditRetentionDays).isEqualTo(90)
        assertThat(parse(RestrictionKeys.AUDIT_RETENTION_DAYS to "14").auditRetentionDays).isEqualTo(14)
        assertThat(parse(RestrictionKeys.AUDIT_RETENTION_DAYS to -1).auditRetentionDays).isNull()
        assertThat(parse(RestrictionKeys.AUDIT_RETENTION_DAYS to 9999).auditRetentionDays).isNull()
        assertThat(parse(RestrictionKeys.AUDIT_RETENTION_DAYS to "abc").auditRetentionDays).isNull()
        assertThat(parse(RestrictionKeys.AUDIT_RETENTION_DAYS to -1).isManaged).isFalse()
    }

    @Test
    fun aFailingProviderFallsBackToDefaults() = runTest {
        val config = ManagedConfig(provider = { error("boom") }, changes = emptyFlow())
        assertThat(config.snapshot()).isEqualTo(ManagedConfigValues())
        assertThat(config.values.first()).isEqualTo(ManagedConfigValues())
    }

    @Test
    fun valuesReEmitWhenRestrictionsChange() = runTest(UnconfinedTestDispatcher()) {
        var current: Map<String, Any?> = emptyMap()
        val changes = MutableSharedFlow<Unit>()
        val config = ManagedConfig(provider = { FakeValues(current) }, changes = changes)

        val seen = mutableListOf<ManagedConfigValues>()
        val job = launch { config.values.toList(seen) }

        current = mapOf(RestrictionKeys.ALWAYS_CONFIRM to true)
        changes.emit(Unit)
        // Same content again must not re-emit.
        changes.emit(Unit)
        job.cancel()

        assertThat(seen.map { it.alwaysConfirm }).containsExactly(false, true).inOrder()
    }
}
