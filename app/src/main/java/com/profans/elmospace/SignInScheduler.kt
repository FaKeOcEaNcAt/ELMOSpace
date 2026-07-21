package com.profans.elmospace

import android.app.AlarmManager
import android.app.job.JobScheduler
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZonedDateTime

object SignInScheduler {
    fun scheduleNext(context: Context): Boolean {
        cancelLegacyJob(context)
        if (!AppPreferences.isScheduledSignInEnabled(context)) return false
        if (!canScheduleExactAlarms(context)) return false

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        val triggerAtMillis = nextTriggerMillis(context)
        alarmManager.cancel(pendingIntent(context, ACTION_SCHEDULED_SIGN_IN, REQUEST_CODE))
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent(context, ACTION_SCHEDULED_SIGN_IN, REQUEST_CODE)
        )
        return true
    }

    fun scheduleTest(context: Context): Boolean {
        if (!canScheduleExactAlarms(context)) return false

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        alarmManager.cancel(pendingIntent(context, ACTION_TEST_SCHEDULED_SIGN_IN, TEST_REQUEST_CODE))
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + TEST_DELAY_MS,
            pendingIntent(context, ACTION_TEST_SCHEDULED_SIGN_IN, TEST_REQUEST_CODE)
        )
        return true
    }

    fun cancel(context: Context) {
        cancelLegacyJob(context)
        context.getSystemService(AlarmManager::class.java)
            ?.cancel(pendingIntent(context, ACTION_SCHEDULED_SIGN_IN, REQUEST_CODE))
    }

    private fun cancelLegacyJob(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.cancel(LEGACY_JOB_ID)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    private fun nextTriggerMillis(context: Context): Long {
        val now = ZonedDateTime.now()
        var trigger = now.toLocalDate()
            .atTime(
                AppPreferences.getSignHour(context),
                AppPreferences.getSignMinute(context)
            )
            .atZone(now.zone)
        if (!trigger.isAfter(now)) trigger = trigger.plusDays(1)
        return trigger.toInstant().toEpochMilli()
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, SignInAlarmReceiver::class.java)
            .setAction(action)
        return PendingIntent.getBroadcast(
            context.applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    const val ACTION_SCHEDULED_SIGN_IN = "com.profans.elmospace.action.SCHEDULED_SIGN_IN"
    const val ACTION_TEST_SCHEDULED_SIGN_IN =
        "com.profans.elmospace.action.TEST_SCHEDULED_SIGN_IN"
    private const val REQUEST_CODE = 260619
    private const val TEST_REQUEST_CODE = 260620
    private const val LEGACY_JOB_ID = 260619
    private const val TEST_DELAY_MS = 30_000L
}
