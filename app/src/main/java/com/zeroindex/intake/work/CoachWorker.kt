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
import com.zeroindex.intake.data.coach.CoachReworder
import com.zeroindex.intake.data.coach.CoachRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The one weekly notification: "Your weekly check-in is ready". Skipped quietly when the
 * notification permission is missing; the Today card still shows the pending check-in.
 */
@Singleton
class CoachNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyReady(suggestions: Int): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Weekly check-in", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Once a week: your coach has looked at your week and has suggestions. Nothing changes until you tap Apply."
            },
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra(EXTRA_OPEN, OPEN_COACH)
        }?.let {
            PendingIntent.getActivity(context, 1, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val text = if (suggestions > 0) {
            "$suggestions suggestion${if (suggestions == 1) "" else "s"} from your week. Nothing changes until you tap Apply."
        } else {
            "All on track this week. Open it for your week at a glance."
        }
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Your weekly check-in is ready")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
        return true
    }

    companion object {
        const val CHANNEL_ID = "weekly-check-in"
        const val NOTIFICATION_ID = 2003
        const val EXTRA_OPEN = "intake_open"
        const val OPEN_COACH = "coach"
    }
}

/**
 * A daily look at whether the weekly check-in is due (its weekday has come and no check-in has been
 * made since). If so it makes the check-in, optionally rewords it with AI when the person turned
 * that on, and sends the one notification. Safe to schedule for everyone: with no data yet it does nothing.
 */
@HiltWorker
class CoachWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val coach: CoachRepository,
    private val reworder: CoachReworder,
    private val settings: SettingsRepository,
    private val notifier: CoachNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val review = coach.generateIfDue()
        if (review != null) {
            val day = settings.settings.first().coachState.lastCheckInDay
            // Wording only; any failure leaves the rule's own text.
            if (day != null) runCatching { reworder.rewordCheckIn(day) }
            if (settings.settings.first().coachNotify) notifier.notifyReady(review.suggestions.size)
        }
        Result.success()
    } catch (e: Exception) {
        Log.w(TAG, "Coach check-in failed", e)
        Result.retry()
    }

    companion object {
        private const val TAG = "CoachWorker"
        const val WORK_NAME = "coach-weekly-check-in"

        /** Daily, early morning, so the check-in is there when the day starts. KEEP leaves an existing schedule alone. */
        fun schedule(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CoachWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(initialDelayMinutes(), TimeUnit.MINUTES)
                    .build(),
            )
        }

        private fun initialDelayMinutes(): Long {
            val now = java.time.LocalTime.now()
            val target = java.time.LocalTime.of(8, 20)
            val delta = (target.hour * 60 + target.minute) - (now.hour * 60 + now.minute)
            return if (delta > 0) delta.toLong() else (delta + 24 * 60).toLong()
        }
    }
}
