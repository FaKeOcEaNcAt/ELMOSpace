package com.profans.elmospace

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class SignInAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            SignInScheduler.ACTION_SCHEDULED_SIGN_IN -> {
                if (!AppPreferences.isScheduledSignInEnabled(context)) return
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ScheduledSignInService::class.java)
                        .setAction(ScheduledSignInService.ACTION_RUN)
                        .putExtra(
                            ScheduledSignInService.EXTRA_SOURCE,
                            ScheduledSignInService.SOURCE_SCHEDULED
                        )
                )
            }
            SignInScheduler.ACTION_TEST_SCHEDULED_SIGN_IN -> {
                TestSignInCountdownService.cancel(context)
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ScheduledSignInService::class.java)
                        .setAction(ScheduledSignInService.ACTION_RUN)
                        .putExtra(
                            ScheduledSignInService.EXTRA_SOURCE,
                            ScheduledSignInService.SOURCE_TEST
                        )
                )
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (!AppPreferences.isScheduledSignInEnabled(context)) return
                SignInScheduler.scheduleNext(context)
            }
        }
    }
}
