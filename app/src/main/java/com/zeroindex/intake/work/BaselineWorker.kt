package com.zeroindex.intake.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zeroindex.intake.R
import com.zeroindex.intake.data.profile.ProfileRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single "your plan is ready" notification (SPEC-v3 point 11).
 *
 * Posting is skipped quietly when the notification permission is missing: that is a normal state,
 * and the Today card still says "Ready" either way.
 */
@Singleton
class BaselineNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Returns true when a notification was actually posted. */
    fun notifyReady(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Baseline week", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you once when your baseline week has enough data to build your plan."
            },
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Your plan is ready to build")
                .setContentText("Your baseline week has enough data for calories, macros, water and training.")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Your baseline week has enough data for calories, macros, water and training. " +
                            "Open Intake to see how it adds up and make any changes you like.",
                    ),
                )
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
        return true
    }

    private companion object {
        const val CHANNEL_ID = "baseline-week"
        const val NOTIFICATION_ID = 2002
    }
}

/**
 * A once-a-day look at the baseline week. When the readiness rule is met and the person has not
 * been told yet, it sends the notification and records that, so it can never repeat. With no
 * baseline running it does nothing, so it is safe to schedule for everyone.
 */
@HiltWorker
class BaselineWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val profile: ProfileRepository,
    private val notifier: BaselineNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val snapshot = profile.baselineSnapshot()
        if (snapshot != null && snapshot.progress.ready && snapshot.state.notifiedAtMillis == null) {
            // Mark it either way: without the permission there is nothing to retry, and the card says "Ready".
            notifier.notifyReady()
            profile.markBaselineNotified()
        }
        Result.success()
    } catch (e: Exception) {
        Log.w(TAG, "Baseline check failed", e)
        Result.retry()
    }

    companion object {
        private const val TAG = "BaselineWorker"
        const val WORK_NAME = "baseline-week-check"

        /** Daily, aimed at early evening when the day's food is mostly logged. KEEP leaves an existing schedule alone. */
        fun schedule(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BaselineWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(initialDelayMinutes(), TimeUnit.MINUTES)
                    .build(),
            )
        }

        private fun initialDelayMinutes(): Long {
            val now = java.time.LocalTime.now()
            val target = java.time.LocalTime.of(19, 0)
            val delta = (target.hour * 60 + target.minute) - (now.hour * 60 + now.minute)
            return if (delta > 0) delta.toLong() else (delta + 24 * 60).toLong()
        }
    }
}
