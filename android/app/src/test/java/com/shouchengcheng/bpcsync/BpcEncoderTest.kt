package com.shouchengcheng.bpcsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BpcEncoderTest {
    @Test
    fun noonFrame() {
        val symbols = BpcEncoder.encodeFrame(CivilTime(2026, 10, 7, 12, 0, 0))
        assertEquals(20, symbols.size)
        assertNull(symbols[0])
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 0, 1, 3, 2, 2, 1, 2, 2, 0), symbols.drop(1))
        assertEquals(1, (symbols[10]!! shr 1) and 1)
    }

    @Test
    fun sundayIsSeven() {
        val symbols = BpcEncoder.encodeFrame(CivilTime(2026, 10, 11, 0, 0, 0))
        assertEquals(7, (symbols[8]!! shl 2) or symbols[9]!!)
    }

    @Test
    fun frameAtTwentySeconds() {
        val symbols = BpcEncoder.encodeFrame(CivilTime(2026, 10, 7, 13, 20, 20))
        assertEquals(1, symbols[1])
        assertEquals(0, symbols[3])
        assertEquals(1, symbols[4])
        assertEquals(1, (symbols[10]!! shr 1) and 1)
        assertEquals(1, symbols[5])
        assertEquals(1, symbols[6])
        assertEquals(0, symbols[7])
    }

    @Test
    fun year64Weight() {
        val symbols = BpcEncoder.encodeFrame(CivilTime(2064, 1, 1, 0, 0, 0))
        assertEquals(0, symbols[16])
        assertEquals(0, symbols[17])
        assertEquals(0, symbols[18])
        assertEquals(1, (symbols[19]!! shr 1) and 1)
    }

    @Test
    fun roundTrip() {
        val cases = listOf(
            CivilTime(2026, 10, 7, 12, 0, 0),
            CivilTime(2026, 10, 11, 0, 0, 0),
            CivilTime(2026, 10, 7, 13, 20, 20),
            CivilTime(2026, 12, 31, 23, 59, 40),
            CivilTime(2064, 1, 1, 0, 0, 0),
        )
        for (input in cases) {
            val decoded = BpcEncoder.decodeFrame(BpcEncoder.encodeFrame(input))
            assertTrue(decoded.error, decoded.ok)
            assertEquals(input.year, decoded.year)
            assertEquals(input.month, decoded.month)
            assertEquals(input.day, decoded.day)
            assertEquals(input.hour, decoded.hour)
            assertEquals(input.minute, decoded.minute)
            assertEquals(input.second, decoded.second)
        }
    }

    @Test
    fun minuteHasThreeFrames() {
        val symbols = BpcEncoder.encodeMinute(CivilTime(2026, 10, 7, 8, 5, 0))
        assertEquals(60, symbols.size)
        assertNull(symbols[0])
        assertNull(symbols[20])
        assertNull(symbols[40])
        assertEquals(0, symbols[1])
        assertEquals(1, symbols[21])
        assertEquals(2, symbols[41])
    }

    @Test
    fun envelope() {
        assertEquals(0.0, BpcEncoder.carrierGain(0, 0.05, false), 0.0)
        assertEquals(1.0, BpcEncoder.carrierGain(0, 0.15, false), 0.0)
        assertEquals(1.0, BpcEncoder.carrierGain(null, 0.5, false), 0.0)
        assertEquals(0.0, BpcEncoder.carrierGain(null, 0.5, true), 0.0)
        assertEquals(1.0, BpcEncoder.carrierGain(1, 0.1, true), 0.0)
        assertEquals(0.0, BpcEncoder.carrierGain(1, 0.3, true), 0.0)
    }

    @Test
    fun beijingPartsFollowUtcPlus8() {
        val parts = BpcEncoder.beijingParts(Instant.parse("2026-10-07T04:16:20Z").toEpochMilli())
        assertEquals(BeijingParts(2026, 10, 7, 12, 16, 20, 0), parts)
    }

    @Test
    fun rejectPartialFrame() {
        assertThrows(IllegalArgumentException::class.java) {
            BpcEncoder.encodeFrame(CivilTime(2026, 10, 7, 12, 0, 15))
        }
    }
}
