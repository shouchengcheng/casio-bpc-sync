package com.shouchengcheng.bpcsync

import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime

data class SyncResult(
    val ok: Boolean,
    val offsetMs: Long,
    val source: String,
    val rttMs: Long?,
)

object ClockSync {
    private val sources = listOf(
        Source("suning", "https://f.m.suning.com/api/ct.do", ::parseSuning),
        Source("worldtimeapi", "https://worldtimeapi.org/api/timezone/Asia/Shanghai", ::parseWorldTime),
        Source("timeapi", "https://timeapi.io/api/Time/current/zone?timeZone=Asia/Shanghai", ::parseTimeApi),
    )

    fun offsetFromExchange(sentAt: Long, receivedAt: Long, serverMs: Long): Pair<Long, Long> {
        val rtt = receivedAt - sentAt
        return rtt to (serverMs - (sentAt + rtt / 2))
    }

    fun parseSuning(body: String): Long {
        val match = Regex(""""currentTime"\s*:\s*(-?\d+)""").find(body)
            ?: throw IllegalArgumentException("苏宁时间无效")
        return match.groupValues[1].toLong()
    }

    fun parseWorldTime(body: String): Long {
        val unix = Regex(""""unixtime"\s*:\s*(-?\d+)""").find(body)
        if (unix != null) return unix.groupValues[1].toLong() * 1000
        val utc = Regex(""""utc_datetime"\s*:\s*"([^"]+)"""").find(body)
            ?: throw IllegalArgumentException("WorldTimeAPI 时间无效")
        return OffsetDateTime.parse(utc.groupValues[1]).toInstant().toEpochMilli()
    }

    fun parseTimeApi(body: String): Long {
        val match = Regex(""""dateTime"\s*:\s*"([^"]+)"""").find(body)
            ?: throw IllegalArgumentException("TimeAPI 时间无效")
        return parseShanghaiDateTime(match.groupValues[1])
    }

    fun parseShanghaiDateTime(dateTime: String): Long {
        val match = Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d+))?""").find(dateTime)
            ?: throw IllegalArgumentException("TimeAPI 时间无效")
        val fraction = match.groupValues[2].ifEmpty { "0" }.take(3).padEnd(3, '0')
        return OffsetDateTime.parse("${match.groupValues[1]}.$fraction+08:00").toInstant().toEpochMilli()
    }

    fun sync(fetch: (String) -> String = ::httpGet): SyncResult {
        val samples = sources.map { source ->
            val sentAt = System.currentTimeMillis()
            try {
                val body = fetch(source.url)
                val receivedAt = System.currentTimeMillis()
                val serverMs = source.parse(body)
                val (rtt, offset) = offsetFromExchange(sentAt, receivedAt, serverMs)
                Sample(ok = true, source = source.id, rttMs = rtt, offsetMs = offset)
            } catch (_: Exception) {
                Sample(ok = false, source = source.id)
            }
        }
        val best = samples.filter { it.ok }.minByOrNull { it.rttMs }
        if (best == null) {
            return SyncResult(ok = false, offsetMs = 0, source = "local", rttMs = null)
        }
        return SyncResult(ok = true, offsetMs = best.offsetMs, source = best.source, rttMs = best.rttMs)
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 4000
            setRequestProperty("Cache-Control", "no-store")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            return stream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private data class Source(val id: String, val url: String, val parse: (String) -> Long)

    private data class Sample(
        val ok: Boolean,
        val source: String,
        val rttMs: Long = Long.MAX_VALUE,
        val offsetMs: Long = 0,
    )
}
