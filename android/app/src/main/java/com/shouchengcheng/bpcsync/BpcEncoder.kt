package com.shouchengcheng.bpcsync

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class BeijingParts(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val ms: Int,
)

data class CivilTime(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
)

data class DecodedFrame(
    val ok: Boolean,
    val year: Int = 0,
    val month: Int = 0,
    val day: Int = 0,
    val hour: Int = 0,
    val minute: Int = 0,
    val second: Int = 0,
    val weekday: Int = 0,
    val pm: Int = 0,
    val error: String = "",
)

object BpcEncoder {
    private const val SHANGHAI_OFFSET_MS = 8 * 60 * 60 * 1000L
    private val PULSE_WIDTH = doubleArrayOf(0.1, 0.2, 0.3, 0.4)

    fun beijingParts(epochMs: Long): BeijingParts {
        val shifted = Instant.ofEpochMilli(epochMs + SHANGHAI_OFFSET_MS).atZone(ZoneOffset.UTC)
        return BeijingParts(
            year = shifted.year,
            month = shifted.monthValue,
            day = shifted.dayOfMonth,
            hour = shifted.hour,
            minute = shifted.minute,
            second = shifted.second,
            ms = shifted.nano / 1_000_000,
        )
    }

    fun weekdayMon1(year: Int, month: Int, day: Int): Int {
        return LocalDate.of(year, month, day).dayOfWeek.value
    }

    fun encodeFrame(time: CivilTime): List<Int?> {
        if (time.second != 0 && time.second != 20 && time.second != 40) {
            throw IllegalArgumentException("帧起始秒必须是 0、20 或 40，收到 ${time.second}")
        }
        val hour12 = time.hour % 12
        val pm = if (time.hour >= 12) 1 else 0
        val year2 = time.year % 100
        val weekday = weekdayMon1(time.year, time.month, time.day)
        val symbols = arrayOfNulls<Int>(20)
        symbols[0] = null
        symbols[1] = time.second / 20
        symbols[2] = 0
        symbols[3] = hour12 shr 2
        symbols[4] = hour12 and 3
        symbols[5] = time.minute shr 4
        symbols[6] = (time.minute shr 2) and 3
        symbols[7] = time.minute and 3
        symbols[8] = weekday shr 2
        symbols[9] = weekday and 3
        symbols[10] = (pm shl 1) or xorSymbols(symbols.slice(1 until 10))
        symbols[11] = (time.day shr 4) and 1
        symbols[12] = (time.day shr 2) and 3
        symbols[13] = time.day and 3
        symbols[14] = time.month shr 2
        symbols[15] = time.month and 3
        symbols[16] = (year2 shr 4) and 3
        symbols[17] = (year2 shr 2) and 3
        symbols[18] = year2 and 3
        symbols[19] = ((year2 shr 6) shl 1) or xorSymbols(symbols.slice(11 until 19))
        return symbols.toList()
    }

    fun encodeMinute(time: CivilTime): List<Int?> {
        return listOf(0, 20, 40).flatMap { second -> encodeFrame(time.copy(second = second)) }
    }

    fun decodeFrame(symbols: List<Int?>, century: Int = 2000): DecodedFrame {
        if (symbols.size != 20) return DecodedFrame(ok = false, error = "帧长度必须是 20")
        for (index in 1 until 20) {
            val symbol = symbols[index]
            if (symbol == null || symbol !in 0..3) {
                return DecodedFrame(ok = false, error = "秒 $index 不是四进制符号")
            }
        }
        val values = symbols.map { it ?: 0 }
        if ((values[10] and 1) != xorSymbols(symbols.slice(1 until 10))) {
            return DecodedFrame(ok = false, error = "前半校验失败")
        }
        if ((values[19] and 1) != xorSymbols(symbols.slice(11 until 19))) {
            return DecodedFrame(ok = false, error = "后半校验失败")
        }
        if (values[1] > 2) return DecodedFrame(ok = false, error = "秒字段无效")

        val hour12 = (values[3] shl 2) or values[4]
        val minute = (values[5] shl 4) or (values[6] shl 2) or values[7]
        val weekday = (values[8] shl 2) or values[9]
        val day = ((values[11] and 1) shl 4) or (values[12] shl 2) or values[13]
        val month = (values[14] shl 2) or values[15]
        if (hour12 > 11 || minute > 59 || weekday !in 1..7) {
            return DecodedFrame(ok = false, error = "时间字段超出范围")
        }
        if (month !in 1..12 || day !in 1..31) {
            return DecodedFrame(ok = false, error = "日期超出范围")
        }

        val year2 = (values[16] shl 4) or
            (values[17] shl 2) or
            values[18] or
            (((values[19] shr 1) and 1) shl 6)
        val year = century + year2
        if (weekdayMon1(year, month, day) != weekday) {
            return DecodedFrame(ok = false, error = "星期与日期不符")
        }
        val pm = (values[10] shr 1) and 1
        return DecodedFrame(
            ok = true,
            year = year,
            month = month,
            day = day,
            hour = hour12 + if (pm == 1) 12 else 0,
            minute = minute,
            second = values[1] * 20,
            weekday = weekday,
            pm = pm,
        )
    }

    fun carrierGain(symbol: Int?, secondsInto: Double, invert: Boolean): Double {
        if (symbol == null) return if (invert) 0.0 else 1.0
        val width = PULSE_WIDTH[symbol]
        return if (invert) {
            if (secondsInto < width) 1.0 else 0.0
        } else {
            if (secondsInto < width) 0.0 else 1.0
        }
    }

    private fun xorSymbols(symbols: List<Int?>): Int {
        var parity = 0
        for (symbol in symbols) {
            val value = symbol ?: 0
            parity = parity xor ((value shr 1) and 1) xor (value and 1)
        }
        return parity
    }
}
