package com.example.agent.core.audit

/** What is kept of the sentence the user said. */
enum class RequestTextMode(val key: String) {
    /** A short SHA-256 prefix: lets repeats be noticed, reveals nothing. */
    HASH("hash"),

    /** The first few characters only. */
    TRUNCATED("truncated"),

    /** Nothing. */
    NONE("none"),
    ;

    companion object {
        fun fromKey(key: String?): RequestTextMode = entries.firstOrNull { it.key == key } ?: HASH
    }
}

/**
 * The effective audit rules after the user's settings and any administrator restriction are
 * combined. [retentionDays] of 0 means auditing is off: nothing is recorded.
 */
data class AuditPolicy(
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
    val keepContactNames: Boolean = false,
    val requestTextMode: RequestTextMode = RequestTextMode.HASH,
) {
    val enabled: Boolean get() = retentionDays > 0

    companion object {
        const val DEFAULT_RETENTION_DAYS = 30

        /** The choices the settings screen offers; 0 is "off". */
        val RETENTION_CHOICES: List<Int> = listOf(7, 30, 90, 0)

        /** An administrator value wins over the user's own choice. */
        fun resolve(
            userRetentionDays: Int,
            managedRetentionDays: Int?,
            keepContactNames: Boolean,
            requestTextMode: RequestTextMode,
        ) = AuditPolicy(
            retentionDays = managedRetentionDays ?: userRetentionDays.coerceAtLeast(0),
            keepContactNames = keepContactNames,
            requestTextMode = requestTextMode,
        )

        /** Records older than this instant are due for deletion. */
        fun cutoffMillis(nowMillis: Long, retentionDays: Int): Long =
            nowMillis - retentionDays.toLong() * MILLIS_PER_DAY

        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
