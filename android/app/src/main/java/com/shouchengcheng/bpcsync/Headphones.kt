package com.shouchengcheng.bpcsync

import android.media.AudioDeviceInfo

object Headphones {
    private val wiredTypes = intArrayOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
    )

    fun preferred(devices: Array<AudioDeviceInfo>): AudioDeviceInfo? {
        for (type in wiredTypes) {
            devices.firstOrNull { it.type == type }?.let { return it }
        }
        return null
    }

    fun isBluetooth(type: Int): Boolean {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            type == AudioDeviceInfo.TYPE_BLE_SPEAKER
    }
}
