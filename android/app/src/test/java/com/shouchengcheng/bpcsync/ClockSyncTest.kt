package com.shouchengcheng.bpcsync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

class ClockSyncTest {
    @Test
    fun halfRoundTrip() {
        val (rtt, offset) = ClockSync.offsetFromExchange(1_000, 1_200, 5_000)
        assertEquals(200, rtt)
        assertEquals(5_000 - 1_100, offset)
    }

    @Test
    fun parsers() {
        assertEquals(1_759_000_000_000, ClockSync.parseSuning("""{"currentTime":1759000000000}"""))
        assertEquals(1_759_000_000_000, ClockSync.parseWorldTime("""{"unixtime":1759000000}"""))
        val shanghai = ClockSync.parseTimeApi("""{"dateTime":"2026-10-07T13:24:25.5078903"}""")
        val expected = OffsetDateTime.parse("2026-10-07T13:24:25.507+08:00").toInstant().toEpochMilli()
        assertEquals(expected, shanghai)
    }

    @Test
    fun picksFastestSuccessfulSource() {
        val result = ClockSync.sync { url ->
            when {
                url.contains("suning") -> {
                    Thread.sleep(30)
                    """{"currentTime":5000}"""
                }
                url.contains("worldtimeapi") -> throw IllegalStateException("404")
                else -> """{"dateTime":"2026-10-07T13:00:00.000"}"""
            }
        }
        assertEquals(true, result.ok)
        assertEquals("timeapi", result.source)
    }

    @Test
    fun allFailedUsesLocalClock() {
        val result = ClockSync.sync { throw IllegalStateException("offline") }
        assertEquals(false, result.ok)
        assertEquals("local", result.source)
        assertEquals(0, result.offsetMs)
    }
}
