package com.profans.elmospace

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ScheduledSignInService : Service() {
    @Volatile
    private var running = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val source = intent?.getStringExtra(EXTRA_SOURCE) ?: SOURCE_SCHEDULED
        if (source == SOURCE_SCHEDULED && !AppPreferences.isScheduledSignInEnabled(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY

        running = true
        startForeground(
            NOTIFICATION_RUNNING_ID,
            buildNotification(
                getString(R.string.scheduled_sign_running),
                autoCancel = false
            )
        )

        Thread {
            val result = NativeSignInClient.signIn(applicationContext)
            finish(result, source, startId)
        }.start()

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun finish(result: NativeSignInClient.Result, source: String, startId: Int) {
        val message = when (result) {
            NativeSignInClient.Result.AlreadySigned ->
                getString(R.string.scheduled_sign_already_done)
            is NativeSignInClient.Result.AlreadySignedWithScore ->
                getString(R.string.scheduled_sign_already_done_with_score, result.score)
            NativeSignInClient.Result.LoginInvalid ->
                getString(R.string.scheduled_sign_login_invalid)
            NativeSignInClient.Result.InterfaceUnavailable ->
                getString(R.string.scheduled_sign_interface_unavailable)
            is NativeSignInClient.Result.SignedWithScore ->
                getString(R.string.scheduled_sign_success_with_score, result.score)
            NativeSignInClient.Result.SignedWithoutScore ->
                getString(R.string.scheduled_sign_success_without_score)
        }

        runCatching {
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_RESULT_ID,
                buildNotification(message, autoCancel = true)
            )
        }
        if (source == SOURCE_SCHEDULED && result.canRunAutoExchange()) {
            val exchangeMessage = AutoExchangeExecutor.execute(applicationContext)
            if (exchangeMessage.isNotBlank()) {
                runCatching {
                    getSystemService(NotificationManager::class.java).notify(
                        NOTIFICATION_EXCHANGE_RESULT_ID,
                        buildNotification(exchangeMessage, autoCancel = true)
                    )
                }
            }
        }
        if (source == SOURCE_SCHEDULED) {
            SignInScheduler.scheduleNext(this)
        }
        running = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "定时自动签到",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "显示定时自动签到执行状态和结果" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(message: String, autoCancel: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_home)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(!autoCancel)
            .setAutoCancel(autoCancel)
            .build()

    private fun NativeSignInClient.Result.canRunAutoExchange(): Boolean =
        when (this) {
            NativeSignInClient.Result.AlreadySigned,
            is NativeSignInClient.Result.AlreadySignedWithScore,
            is NativeSignInClient.Result.SignedWithScore,
            NativeSignInClient.Result.SignedWithoutScore -> true
            NativeSignInClient.Result.LoginInvalid,
            NativeSignInClient.Result.InterfaceUnavailable -> false
        }

    companion object {
        const val ACTION_RUN = "com.profans.elmospace.action.RUN_SCHEDULED_SIGN_IN"
        const val EXTRA_SOURCE = "source"
        const val SOURCE_SCHEDULED = "scheduled"
        const val SOURCE_TEST = "test"
        const val CHANNEL_ID = "scheduled_sign_in"
        private const val NOTIFICATION_RUNNING_ID = 2606191
        private const val NOTIFICATION_RESULT_ID = 2606192
        private const val NOTIFICATION_EXCHANGE_RESULT_ID = 2606193
    }
}
