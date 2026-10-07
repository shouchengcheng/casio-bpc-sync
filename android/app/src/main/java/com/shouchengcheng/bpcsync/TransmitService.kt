package com.shouchengcheng.bpcsync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class TransmitService : Service() {
    private var transmitter: Transmitter? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        startAsForeground()
        if (transmitter == null) {
            val created = Transmitter(this)
            try {
                created.start()
                transmitter = created
            } catch (error: Exception) {
                AppSession.lastError = error.message ?: getString(R.string.audio_failed)
                AppSession.running = false
                shutdown()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        transmitter?.stop()
        transmitter = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
        val stopIntent = Intent(this, TransmitService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wave)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .addAction(0, getString(R.string.stop), stopPending)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun shutdown() {
        transmitter?.stop()
        transmitter = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: IllegalStateException) {
            // 服务还没进入前台时，停止请求不需要拆通知。
        }
        stopSelf()
    }

    companion object {
        const val ACTION_STOP = "com.shouchengcheng.bpcsync.STOP"
        private const val CHANNEL_ID = "bpc_transmit"
        private const val NOTIFICATION_ID = 7

        fun start(context: Context) {
            val intent = Intent(context, TransmitService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TransmitService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
