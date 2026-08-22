package com.xianhaoran.dsh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder

class EngineService : Service() {
    private var watchdogThread: Thread? = null
    @Volatile private var stopped = false
    @Volatile private var restarting = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (watchdogThread == null) {
            watchdogThread = Thread {
                while (!stopped) {
                    if (!EngineProbe.isRunning() && !restarting) {
                        restarting = true
                        EngineManager(this).startEngineAsync(
                            onResult = { _, _ -> restarting = false }
                        )
                    }
                    try {
                        Thread.sleep(5000)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }.also {
                it.isDaemon = true
                it.start()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopped = true
        watchdogThread?.interrupt()
        watchdogThread = null
        super.onDestroy()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "dsh 引擎", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification {
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("dsh 引擎运行中")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "dsh-engine"
        private const val NOTIFICATION_ID = 1001
    }
}
