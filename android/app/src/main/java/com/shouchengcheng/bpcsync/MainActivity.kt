package com.shouchengcheng.bpcsync

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.shouchengcheng.bpcsync.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 200)
        }
    }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        binding.language.setOnClickListener { toggleLanguage() }
        binding.toggle.setOnClickListener {
            if (AppSession.running) TransmitService.stop(this) else TransmitService.start(this)
        }
        binding.resync.setOnClickListener { resync() }
        binding.selfCheck.setOnClickListener { showSelfCheck() }
        binding.volume.setOnClickListener { raiseVolume() }
        binding.frequency.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("13700 Hz", "17125 Hz"),
        )
        binding.waveform.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.waveforms).toList(),
        )
        binding.frequency.setSelection(if (AppSession.frequency == 17125) 1 else 0)
        binding.waveform.setSelection(if (AppSession.waveform == "square") 1 else 0)
        binding.invert.isChecked = AppSession.invert
        binding.trim.progress = AppSession.trimMs / 10 + 30
        binding.frequency.onItemSelectedListener = selected { position ->
            AppSession.frequency = if (position == 1) 17125 else 13700
        }
        binding.waveform.onItemSelectedListener = selected { position ->
            AppSession.waveform = if (position == 1) "square" else "sine"
        }
        binding.invert.setOnCheckedChangeListener { _, checked ->
            AppSession.invert = checked
            render()
        }
        binding.trim.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                AppSession.trimMs = (progress - 30) * 10
                render()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        if (!AppSession.didSync) resync() else showSelfCheck()
    }

    override fun onStart() {
        super.onStart()
        handler.post(tick)
    }

    override fun onStop() {
        handler.removeCallbacks(tick)
        super.onStop()
    }

    private fun toggleLanguage() {
        val next = if (usingChinese()) "en" else "zh"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(next))
    }

    private fun usingChinese(): Boolean {
        val chosen = AppCompatDelegate.getApplicationLocales()
        val language = if (chosen.isEmpty) {
            resources.configuration.locales[0].language
        } else {
            chosen[0]?.language ?: "en"
        }
        return language.startsWith("zh")
    }

    private fun resync() {
        if (AppSession.syncing) return
        AppSession.syncing = true
        render()
        Thread {
            val result = ClockSync.sync()
            AppSession.syncOk = result.ok
            AppSession.offsetMs = result.offsetMs
            AppSession.source = result.source
            AppSession.rttMs = result.rttMs
            AppSession.syncing = false
            AppSession.didSync = true
            handler.post {
                render()
                showSelfCheck()
            }
        }.start()
    }

    private fun raiseVolume() {
        val manager = getSystemService(AudioManager::class.java)
        manager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            AudioManager.FLAG_SHOW_UI,
        )
    }

    private fun showSelfCheck() {
        val now = BpcEncoder.beijingParts(AppSession.beijingNowMs())
        val frameSecond = now.second - now.second % 20
        val input = CivilTime(now.year, now.month, now.day, now.hour, now.minute, frameSecond)
        val decoded = BpcEncoder.decodeFrame(BpcEncoder.encodeFrame(input))
        val matched = decoded.ok &&
            decoded.year == input.year &&
            decoded.month == input.month &&
            decoded.day == input.day &&
            decoded.hour == input.hour &&
            decoded.minute == input.minute &&
            decoded.second == input.second
        if (matched) {
            binding.checkResult.setTextColor(0xFFD6FF4A.toInt())
            binding.checkResult.text = getString(
                R.string.self_check_ok,
                input.year,
                input.month,
                input.day,
                input.hour,
                input.minute,
                input.second,
            )
        } else {
            binding.checkResult.setTextColor(0xFFFFB4A2.toInt())
            binding.checkResult.text = getString(R.string.self_check_bad)
        }
    }

    private fun render() {
        val now = BpcEncoder.beijingParts(AppSession.beijingNowMs())
        binding.beijingTime.text = "%02d:%02d:%02d".format(now.hour, now.minute, now.second)
        val weekday = resources.getStringArray(R.array.weekdays)[BpcEncoder.weekdayMon1(now.year, now.month, now.day) - 1]
        binding.beijingDate.text = "%04d-%02d-%02d %s".format(now.year, now.month, now.day, weekday)
        binding.language.text = if (usingChinese()) "EN" else "中文"
        when {
            AppSession.syncing -> {
                binding.syncStatus.setTextColor(0xFFB7B39F.toInt())
                binding.syncStatus.setText(R.string.syncing)
            }
            AppSession.syncOk -> {
                binding.syncStatus.setTextColor(0xFFF3F0E4.toInt())
                binding.syncStatus.text = getString(
                    R.string.sync_ok,
                    sourceLabel(AppSession.source),
                    formatOffset(AppSession.offsetMs),
                    AppSession.rttMs ?: 0,
                )
            }
            else -> {
                binding.syncStatus.setTextColor(0xFFFFB4A2.toInt())
                binding.syncStatus.setText(R.string.sync_failed)
            }
        }
        binding.headphone.text = headphoneStatus()
        binding.headphone.setTextColor(
            if (wiredDevice() != null) 0xFFB7B39F.toInt() else 0xFFFFB4A2.toInt(),
        )
        binding.toggle.setText(if (AppSession.running) R.string.stop else R.string.start)
        binding.elapsed.text = if (AppSession.running) {
            val total = AppSession.elapsedMs() / 1000
            getString(R.string.elapsed, total / 60, total % 60)
        } else {
            ""
        }
        binding.trimReadout.text = getString(R.string.trim_value, AppSession.trimMs)
        binding.invertNote.text = if (AppSession.invert) getString(R.string.invert_on) else ""
        binding.latency.text = if (AppSession.running) {
            getString(R.string.latency_on, AppSession.latencyMs)
        } else {
            getString(R.string.latency_off)
        }
        if (AppSession.lastError.isNotEmpty() && !AppSession.running) {
            binding.checkResult.setTextColor(0xFFFFB4A2.toInt())
            binding.checkResult.text = AppSession.lastError
            AppSession.lastError = ""
        }
        binding.bars.setState(
            BpcEncoder.encodeMinute(CivilTime(now.year, now.month, now.day, now.hour, now.minute, 0)),
            now.second,
        )
    }

    private fun headphoneStatus(): String {
        val wired = wiredDevice()
        if (wired != null) {
            val kind = when (wired.type) {
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> getString(R.string.type_usb)
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> getString(R.string.type_headset)
                else -> getString(R.string.type_jack)
            }
            val name = wired.productName?.toString().orEmpty()
            return if (name.isBlank()) getString(R.string.wired_ok, kind) else getString(R.string.wired_named, kind, name)
        }
        val devices = getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return if (devices.any { Headphones.isBluetooth(it.type) }) {
            getString(R.string.bluetooth_warn)
        } else {
            getString(R.string.speaker_warn)
        }
    }

    private fun wiredDevice(): AudioDeviceInfo? {
        val devices = getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return Headphones.preferred(devices)
    }

    private fun sourceLabel(id: String): String {
        return when (id) {
            "suning" -> getString(R.string.source_suning)
            "worldtimeapi" -> "WorldTimeAPI"
            "timeapi" -> "TimeAPI"
            else -> getString(R.string.source_local)
        }
    }

    private fun formatOffset(offsetMs: Long): String {
        val rounded = offsetMs
        return if (rounded >= 0) "+$rounded ms" else "$rounded ms"
    }

    private fun selected(onPick: (Int) -> Unit) = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
            onPick(position)
        }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
    }
}
