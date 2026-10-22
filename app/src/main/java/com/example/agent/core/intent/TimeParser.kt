package com.example.agent.core.intent

/** A time of day in 24-hour form. */
data class ClockTime(val hour: Int, val minute: Int) {
    /** "18:05", the canonical form the guard hands to the executor. */
    fun canonical(): String = "%02d:%02d".format(hour, minute)

    /** "6:05 PM", for confirmation text. */
    fun display(): String {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        return "%d:%02d %s".format(h12, minute, if (hour < 12) "AM" else "PM")
    }
}

/** Turns the times people say ("6 am", "7:30 pm", "half past six") into a [ClockTime]. */
object TimeParser {

    const val MIN_TIMER_MINUTES = 1
    const val MAX_TIMER_MINUTES = 1440

    fun isValidTimerMinutes(minutes: Int): Boolean = minutes in MIN_TIMER_MINUTES..MAX_TIMER_MINUTES

    private val HOUR_WORDS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
    )

    private val MINUTE_WORDS = mapOf(
        "five" to 5, "ten" to 10, "fifteen" to 15, "twenty" to 20, "twenty five" to 25,
        "thirty" to 30, "forty" to 40, "forty five" to 45, "fifty" to 50,
    )

    private val DIGITS = Regex("""^(\d{1,2})(?:[:.](\d{2}))?$""")
    private val RELATIVE = Regex("""^(half past|quarter past|quarter to) (\w+)$""")
    private val WORDS = Regex("""^(\w+)(?: (\w+(?: \w+)?))?$""")
    private val TRAILING_MERIDIEM = Regex("""^(.*?)\s*(am|pm)$""")

    /** Returns the time, or null when [input] is not a valid time of day. */
    fun parse(input: String): ClockTime? {
        var body = clean(input)
        if (body.isEmpty()) return null
        var meridiem: String? = null
        for ((suffix, value) in MERIDIEM_SUFFIXES) {
            if (body.endsWith(suffix)) {
                meridiem = value
                body = body.removeSuffix(suffix).trim()
            }
        }
        TRAILING_MERIDIEM.matchEntire(body)?.let { m ->
            if (meridiem == null) meridiem = m.groupValues[2]
            body = m.groupValues[1].trim()
        }
        if (body == "noon" && meridiem == null) return ClockTime(12, 0)
        if (body == "midnight" && meridiem == null) return ClockTime(0, 0)
        val mer = meridiem

        DIGITS.matchEntire(body)?.let { m ->
            val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
            return build(m.groupValues[1].toInt(), minute, mer)
        }
        RELATIVE.matchEntire(body)?.let { m ->
            val hour = hourValue(m.groupValues[2]) ?: return null
            return when (m.groupValues[1]) {
                "half past" -> build(hour, 30, mer)
                "quarter past" -> build(hour, 15, mer)
                else -> {
                    val previous = if (mer != null) (if (hour == 1) 12 else hour - 1) else (hour + 23) % 24
                    build(previous, 45, mer)
                }
            }
        }
        WORDS.matchEntire(body)?.let { m ->
            val hour = HOUR_WORDS[m.groupValues[1]] ?: return null
            val minuteText = m.groupValues[2]
            val minute = if (minuteText.isEmpty()) 0 else MINUTE_WORDS[minuteText] ?: return null
            return build(hour, minute, mer)
        }
        return null
    }

    private fun hourValue(word: String): Int? = word.toIntOrNull() ?: HOUR_WORDS[word]

    private fun build(hour: Int, minute: Int, meridiem: String?): ClockTime? {
        if (minute !in 0..59) return null
        val h24 = when (meridiem) {
            "am" -> if (hour in 1..12) hour % 12 else return null
            "pm" -> if (hour in 1..12) hour % 12 + 12 else return null
            else -> if (hour in 0..23) hour else return null
        }
        return ClockTime(h24, minute)
    }

    private val MERIDIEM_SUFFIXES = listOf(
        "in the morning" to "am",
        "in the afternoon" to "pm",
        "in the evening" to "pm",
        "at night" to "pm",
    )

    private fun clean(input: String): String {
        var s = input.lowercase().trim()
        s = s.replace("a.m.", "am").replace("p.m.", "pm").replace(Regex("(?<=[a-z])-(?=[a-z])"), " ")
        s = s.replace("o'clock", "").replace("oclock", "").replace("sharp", "")
        s = s.replace(Regex("""^(at|around)\s+"""), "")
        s = s.replace(Regex("""[,!?]"""), " ")
        return s.replace(Regex("""\s+"""), " ").trim()
    }
}
