package com.shouchengcheng.bpcsync

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

class Transmitter(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    private var focusRequest: AudioFocusRequest? = null
    private var deviceCallback: AudioDeviceCallback? = null
    @Volatile private var stopRequested = false
    private var framesWritten = 0L
    private var headBase = 0L
    private var lastHead = 0L

    fun start() {
        if (AppSession.running) return
        stopRequested = false
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBytes <= 0) throw IllegalStateException("这个设备没有可用的音频输出")
        val bufferBytes = max(minBytes, SAMPLE_RATE / 5 * 2)
        val created = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .build()
        if (created.state != AudioTrack.STATE_INITIALIZED) {
            created.release()
            throw IllegalStateException("声卡没有准备好")
        }
        routeToWired(created)
        watchDevices(created)
        requestFocus(attributes)
        created.play()
        track = created
        framesWritten = 0
        headBase = 0
        lastHead = 0
        AppSession.startedAt = android.os.SystemClock.elapsedRealtime()
        AppSession.running = true
        AppSession.lastError = ""
        thread = Thread({ writeLoop(created) }, "bpc-transmit").also { it.start() }
    }

    fun stop() {
        stopRequested = true
        AppSession.running = false
        thread?.interrupt()
        thread?.join(500)
        thread = null
        deviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) }
        deviceCallback = null
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        val current = track
        track = null
        if (current != null) {
            try {
                current.pause()
                current.flush()
                current.stop()
            } catch (_: IllegalStateException) {
                // 声卡已经停掉时再 stop 会抛错。
            }
            current.release()
        }
    }

    private fun writeLoop(audio: AudioTrack) {
        val pcm = ShortArray(SAMPLE_RATE / 50)
        var phase = 0.0
        var cacheSecond = Long.MIN_VALUE
        var cacheSymbols: List<Int?> = emptyList()
        var cacheSecondOfMinute = 0
        try {
            while (!stopRequested && !Thread.currentThread().isInterrupted) {
                val queued = framesWritten - playbackHead(audio)
                val space = TARGET_QUEUE_FRAMES - queued
                if (space < SAMPLE_RATE / 100) {
                    Thread.sleep(5)
                    continue
                }
                val count = minOf(pcm.size, space.toInt())
                val latency = outputLatencyMs(audio)
                AppSession.latencyMs = latency
                val origin = AppSession.beijingNowMs() + latency + AppSession.trimMs
                val frequency = AppSession.frequency
                val square = AppSession.waveform == "square"
                val invert = AppSession.invert
                val step = 2.0 * PI * frequency / SAMPLE_RATE
                for (index in 0 until count) {
                    val encodeMs = origin + index * 1000.0 / SAMPLE_RATE
                    val wholeSecond = floor(encodeMs / 1000.0).toLong()
                    if (wholeSecond != cacheSecond) {
                        val parts = BpcEncoder.beijingParts(wholeSecond * 1000)
                        val frameSecond = parts.second - parts.second % 20
                        cacheSymbols = BpcEncoder.encodeFrame(
                            CivilTime(parts.year, parts.month, parts.day, parts.hour, parts.minute, frameSecond),
                        )
                        cacheSecondOfMinute = parts.second
                        cacheSecond = wholeSecond
                    }
                    val into = encodeMs / 1000.0 - wholeSecond
                    val symbol = cacheSymbols[cacheSecondOfMinute % 20]
                    val gain = BpcEncoder.carrierGain(symbol, into, invert)
                    val wave = if (square) {
                        if (phase < PI) 1.0 else -1.0
                    } else {
                        sin(phase)
                    }
                    pcm[index] = (wave * gain * 0.9 * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt() + 1, Short.MAX_VALUE.toInt())
                        .toShort()
                    phase += step
                    if (phase >= 2.0 * PI) phase -= 2.0 * PI
                }
                val wrote = audio.write(pcm, 0, count)
                if (wrote < 0) break
                framesWritten += wrote
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            AppSession.running = false
            if (!stopRequested) {
                mainHandler.post { TransmitService.stop(appContext) }
            }
        }
    }

    private fun outputLatencyMs(audio: AudioTrack): Int {
        return try {
            val method = AudioTrack::class.java.getMethod("getLatency")
            (method.invoke(audio) as Int).coerceAtLeast(0)
        } catch (_: Exception) {
            (audio.bufferSizeInFrames * 1000 / SAMPLE_RATE).coerceAtLeast(40)
        }
    }

    private fun playbackHead(audio: AudioTrack): Long {
        val unsigned = audio.playbackHeadPosition.toLong() and 0xffffffffL
        if (unsigned < lastHead) headBase += 1L shl 32
        lastHead = unsigned
        return headBase + unsigned
    }

    private fun routeToWired(audio: AudioTrack) {
        val wired = Headphones.preferred(audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
        if (wired != null) audio.preferredDevice = wired
    }

    private fun watchDevices(audio: AudioTrack) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = routeToWired(audio)
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = routeToWired(audio)
        }
        deviceCallback = callback
        audioManager.registerAudioDeviceCallback(callback, mainHandler)
    }

    private fun requestFocus(attributes: AudioAttributes) {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS) TransmitService.stop(appContext)
            }, mainHandler)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    companion object {
        const val SAMPLE_RATE = 48000
        private const val TARGET_QUEUE_FRAMES = SAMPLE_RATE / 5
    }
}
