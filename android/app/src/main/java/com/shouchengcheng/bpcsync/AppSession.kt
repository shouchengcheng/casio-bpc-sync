package com.shouchengcheng.bpcsync

object AppSession {
    @Volatile var didSync: Boolean = false
    @Volatile var syncOk: Boolean = false
    @Volatile var offsetMs: Long = 0
    @Volatile var source: String = "local"
    @Volatile var rttMs: Long? = null
    @Volatile var syncing: Boolean = false

    @Volatile var frequency: Int = 13700
    @Volatile var waveform: String = "sine"
    @Volatile var invert: Boolean = false
    @Volatile var trimMs: Int = 0

    @Volatile var running: Boolean = false
    @Volatile var startedAt: Long = 0
    @Volatile var latencyMs: Int = 0
    @Volatile var lastError: String = ""

    fun beijingNowMs(): Long = System.currentTimeMillis() + offsetMs

    fun elapsedMs(): Long {
        if (!running) return 0
        return android.os.SystemClock.elapsedRealtime() - startedAt
    }
}
