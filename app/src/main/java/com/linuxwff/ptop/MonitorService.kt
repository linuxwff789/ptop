package com.linuxwff.ptop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Keeps the sampler alive when the user picks "后台运行" on the back gesture.
 *
 * PARTIAL_WAKE_LOCK keeps the CPU awake; the foreground notification keeps the
 * process out of the freezer. Both are released when the user hits 停止 in the
 * notification (or swipes the app away).
 */
class MonitorService : Service() {
    companion object {
        const val CHANNEL = "ptop-bg"
        private const val NOTIF_ID = 41
        const val ACTION_STOP = "com.linuxwff.ptop.STOP"

        @Volatile var running = false
        @Volatile var requested = false
    }

    private var wake: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification("ptop 后台监控中", "唤醒锁已持有 · 持续采样"))
        val pm = getSystemService(PowerManager::class.java)
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ptop:bg").apply {
            setReferenceCounted(false)
            acquire()
        }
        (application as PtopApplication).monitor.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        requested = false
        runCatching { wake?.release() }
        wake = null
        // If the user came back to the app and then stopped the notification,
        // keep sampling while the UI is visible.
        val app = application as PtopApplication
        if (app.visible == 0) app.monitor.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "后台监控", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 2,
            Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return b
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "停止", stop).build())
            .build()
    }
}
