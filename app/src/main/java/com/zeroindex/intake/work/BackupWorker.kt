package com.zeroindex.intake.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zeroindex.intake.data.backup.remote.BackupOutcome
import com.zeroindex.intake.data.backup.remote.BackupRunner
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.domain.backup.BackupSchedule
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Amendment §2: the nightly headless backup. Asks for any network and a
 * battery that is not low, aims for about 2 AM local time, and retries a few
 * times if the server was unreachable. Failures are recorded as backup runs
 * and shown on the Backup & sync screen rather than as notifications.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: BackupRunner,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (val outcome = runner.run()) {
        is BackupOutcome.Done, is BackupOutcome.Skipped -> Result.success()
        is BackupOutcome.Failed -> if (outcome.transient && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    companion object {
        const val WORK_NAME = "nightly-backup"
        private const val MAX_RETRIES = 3
        private val TARGET = LocalTime.of(2, 0)

        /** Minutes from [now] to the next [TARGET], so the first run lands at night. */
        internal fun delayUntilTarget(now: ZonedDateTime): Duration {
            var next = now.with(TARGET)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next)
        }

        /** Called on launch and whenever backup settings change; idempotent. */
        fun sync(workManager: WorkManager, settings: AppSettings) {
            if (settings.backupDestination == null || settings.backupSchedule == BackupSchedule.MANUAL) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                // KEEP so reopening the app does not push tonight's run back a day.
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS, 2, TimeUnit.HOURS)
                    .setInitialDelay(delayUntilTarget(ZonedDateTime.now()).toMinutes(), TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .setRequiresBatteryNotLow(true)
                            .build(),
                    )
                    .build(),
            )
        }
    }
}
