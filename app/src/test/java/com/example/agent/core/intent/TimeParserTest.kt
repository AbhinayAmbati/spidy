package com.example.agent.core.intent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimeParserTest {

    private val valid = listOf(
        "6 am" to ClockTime(6, 0),
        "6am" to ClockTime(6, 0),
        "6 AM" to ClockTime(6, 0),
        "6 a.m." to ClockTime(6, 0),
        "7:30 pm" to ClockTime(19, 30),
        "7:30pm" to ClockTime(19, 30),
        "12 am" to ClockTime(0, 0),
        "12 pm" to ClockTime(12, 0),
        "12:15 am" to ClockTime(0, 15),
        "18:00" to ClockTime(18, 0),
        "0:05" to ClockTime(0, 5),
        "23:59" to ClockTime(23, 59),
        "6.45 pm" to ClockTime(18, 45),
        "at 5 pm" to ClockTime(17, 0),
        "half past six" to ClockTime(6, 30),
        "half past six pm" to ClockTime(18, 30),
        "quarter past seven" to ClockTime(7, 15),
        "quarter to seven" to ClockTime(6, 45),
        "quarter to one pm" to ClockTime(12, 45),
        "six thirty" to ClockTime(6, 30),
        "six thirty pm" to ClockTime(18, 30),
        "seven pm" to ClockTime(19, 0),
        "six o'clock" to ClockTime(6, 0),
        "six forty five am" to ClockTime(6, 45),
        "6 in the morning" to ClockTime(6, 0),
        "6 in the evening" to ClockTime(18, 0),
        "noon" to ClockTime(12, 0),
        "midnight" to ClockTime(0, 0),
    )

    private val invalid = listOf(
        "", "   ", "banana", "25:00", "24:00", "13 pm", "0 am", "6:60", "6:99 am", "-3",
        "half past", "half past banana", "thirteen", "six sixty", "ten to nowhere",
    )

    @Test
    fun `parses spoken and written times`() {
        valid.forEach { (text, expected) ->
            assertThat(TimeParser.parse(text)).isEqualTo(expected)
        }
    }

    @Test
    fun `rejects nonsense`() {
        invalid.forEach { text ->
            assertThat(TimeParser.parse(text)).isNull()
        }
    }

    @Test
    fun `canonical and display forms`() {
        assertThat(ClockTime(18, 5).canonical()).isEqualTo("18:05")
        assertThat(ClockTime(18, 5).display()).isEqualTo("6:05 PM")
        assertThat(ClockTime(0, 0).display()).isEqualTo("12:00 AM")
        assertThat(ClockTime(12, 30).display()).isEqualTo("12:30 PM")
        assertThat(TimeParser.parse(ClockTime(7, 9).canonical())).isEqualTo(ClockTime(7, 9))
    }

    @Test
    fun `timer minutes must be between 1 and 1440`() {
        listOf(1, 2, 90, 1439, 1440).forEach { assertThat(TimeParser.isValidTimerMinutes(it)).isTrue() }
        listOf(Int.MIN_VALUE, -5, 0, 1441, 100000).forEach {
            assertThat(TimeParser.isValidTimerMinutes(it)).isFalse()
        }
    }
}
