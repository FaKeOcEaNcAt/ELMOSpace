package com.profans.elmospace

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class TestSignInCountdownService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val finishAtMillis = intent?.getLongExtra(EXTRA_FINISH_AT_MILLIS, 0L)
            ?.takeIf { it > System.currentTimeMillis() }
            ?: (System.currentTimeMillis() + COUNTDOWN_MS)
        startForeground(NOTIFICATION_ID, buildNotification(finishAtMillis))
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "定时签到测试",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示测试定时签到的 30 秒倒计时"
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(finishAtMillis: Long) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_home)
            .setContentTitle("定时签到测试倒计时")
            .setContentText("测试任务将在 30 秒后触发")
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, SettingsActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setWhen(finishAtMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        const val CHANNEL_ID = "test_scheduled_sign_countdown"
        const val EXTRA_FINISH_AT_MILLIS = "finish_at_millis"
        const val COUNTDOWN_MS = 30_000L
        private const val NOTIFICATION_ID = 2606194

        fun cancel(context: Context) {
            context.stopService(Intent(context, TestSignInCountdownService::class.java))
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }
    }
}
