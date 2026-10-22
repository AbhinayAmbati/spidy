package com.example.agent.core.audit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.RestrictionsManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/** Names of the keys in `res/xml/app_restrictions.xml`. */
object RestrictionKeys {
    const val DISABLE_VOICE = "disable_voice"
    const val ALWAYS_CONFIRM = "always_confirm"
    const val ALLOWED_ACTIONS = "allowed_actions"
    const val AUDIT_RETENTION_DAYS = "audit_retention_days"
    const val DISABLE_MODEL_DOWNLOAD_ON_METERED = "disable_model_download_on_metered"

    /** The five intent names an administrator can allow, as the model names them. */
    val ALL_ACTIONS: Set<String> =
        setOf("open_app", "send_message", "call_contact", "set_alarm", "set_timer")
}

/**
 * What an administrator has set, with safe defaults: no restriction at all when nothing is
 * configured. [managedKeys] lists the keys the administrator actually supplied, so the settings
 * screen can show a "managed" badge only when it is true.
 */
data class ManagedConfigValues(
    val disableVoice: Boolean = false,
    val alwaysConfirm: Boolean = false,
    /** Null means every action is allowed. An empty set means none is. */
    val allowedActions: Set<String>? = null,
    /** Null means the user chooses; 0 means auditing is off. */
    val auditRetentionDays: Int? = null,
    val disableModelDownloadOnMetered: Boolean = false,
    val managedKeys: Set<String> = emptySet(),
) {
    val isManaged: Boolean get() = managedKeys.isNotEmpty()

    fun isActionAllowed(intentType: String): Boolean = allowedActions?.contains(intentType) ?: true
}

/** The few reads parsing needs, so tests do not need an Android [Bundle]. */
interface RestrictionValues {
    fun contains(key: String): Boolean
    fun getBoolean(key: String): Boolean
    fun getInt(key: String): Int?
    fun getStringList(key: String): List<String>?
}

/** Reads restrictions from a [Bundle] the way [RestrictionsManager] hands them out. */
class BundleRestrictionValues(private val bundle: Bundle) : RestrictionValues {
    override fun contains(key: String) = bundle.containsKey(key)

    override fun getBoolean(key: String): Boolean = when (val v = bundle.get(key)) {
        is Boolean -> v
        is String -> v.equals("true", ignoreCase = true)
        else -> false
    }

    override fun getInt(key: String): Int? = when (val v = bundle.get(key)) {
        is Int -> v
        is Long -> v.toInt()
        is String -> v.trim().toIntOrNull()
        else -> null
    }

    override fun getStringList(key: String): List<String>? = when (val v = bundle.get(key)) {
        is Array<*> -> v.filterIsInstance<String>()
        is List<*> -> v.filterIsInstance<String>()
        is String -> listOf(v)
        else -> null
    }
}

/** Where the current restrictions come from. A port, so tests supply a fake. */
fun interface RestrictionsProvider {
    fun current(): RestrictionValues
}

object ManagedConfigParser {

    const val MAX_RETENTION_DAYS = 365

    fun parse(values: RestrictionValues): ManagedConfigValues {
        val managed = linkedSetOf<String>()

        fun flag(key: String): Boolean {
            if (!values.contains(key)) return false
            managed += key
            return values.getBoolean(key)
        }

        val disableVoice = flag(RestrictionKeys.DISABLE_VOICE)
        val alwaysConfirm = flag(RestrictionKeys.ALWAYS_CONFIRM)
        val noMeteredDownload = flag(RestrictionKeys.DISABLE_MODEL_DOWNLOAD_ON_METERED)

        // An administrator who lists actions means exactly those: unknown names are dropped
        // and, if nothing valid is left, nothing is allowed rather than everything.
        val allowed = if (values.contains(RestrictionKeys.ALLOWED_ACTIONS)) {
            val list = values.getStringList(RestrictionKeys.ALLOWED_ACTIONS)
            if (list != null) {
                managed += RestrictionKeys.ALLOWED_ACTIONS
                list.map { it.trim() }.filter { it in RestrictionKeys.ALL_ACTIONS }.toSet()
            } else {
                null
            }
        } else {
            null
        }

        val retention = if (values.contains(RestrictionKeys.AUDIT_RETENTION_DAYS)) {
            values.getInt(RestrictionKeys.AUDIT_RETENTION_DAYS)
                ?.takeIf { it in 0..MAX_RETENTION_DAYS }
                ?.also { managed += RestrictionKeys.AUDIT_RETENTION_DAYS }
        } else {
            null
        }

        return ManagedConfigValues(
            disableVoice = disableVoice,
            alwaysConfirm = alwaysConfirm,
            allowedActions = allowed,
            auditRetentionDays = retention,
            disableModelDownloadOnMetered = noMeteredDownload,
            managedKeys = managed,
        )
    }
}

/**
 * Administrator configuration (Android Enterprise managed configuration). [values] re-emits
 * whenever the system announces that restrictions changed.
 *
 * Whoever enforces a restriction reads it here: the router drops intents outside
 * [ManagedConfigValues.allowedActions], the confirm gate honours
 * [ManagedConfigValues.alwaysConfirm], the composer hides the microphone for
 * [ManagedConfigValues.disableVoice], and the model downloader respects
 * [ManagedConfigValues.disableModelDownloadOnMetered].
 */
@Singleton
class ManagedConfig(
    private val provider: RestrictionsProvider,
    private val changes: Flow<Unit>,
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        provider = RestrictionsProvider { readRestrictions(context) },
        changes = restrictionChanges(context),
    )

    /** The current configuration. Never throws: a failed read means "no restrictions". */
    fun snapshot(): ManagedConfigValues =
        runCatching { ManagedConfigParser.parse(provider.current()) }.getOrDefault(ManagedConfigValues())

    val values: Flow<ManagedConfigValues> = changes
        .onStart { emit(Unit) }
        .map { snapshot() }
        .distinctUntilChanged()

    private companion object {
        fun readRestrictions(context: Context): RestrictionValues {
            val manager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
            return BundleRestrictionValues(manager?.applicationRestrictions ?: Bundle.EMPTY)
        }

        fun restrictionChanges(context: Context): Flow<Unit> = callbackFlow {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    trySend(Unit)
                }
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            awaitClose { runCatching { context.unregisterReceiver(receiver) } }
        }
    }
}
