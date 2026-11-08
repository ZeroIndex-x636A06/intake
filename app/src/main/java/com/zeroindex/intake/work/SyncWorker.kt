package com.zeroindex.intake.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zeroindex.intake.data.sync.SyncEngine
import com.zeroindex.intake.data.sync.SyncException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Amendment §3: sync runs only with a network, so logging offline is never
 * blocked and the backlog goes up on reconnection. A run that fails to reach
 * the server is retried with backoff; one the server refused (device removed)
 * is not.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        engine.sync()
        Result.success()
    } catch (e: SyncException) {
        Log.i(TAG, "Sync did not complete: ${e.message}")
        if (e.unauthorized || runAttemptCount >= 5) Result.success() else Result.retry()
    }

    companion object {
        private const val TAG = "SyncWorker"
        const val PERIODIC = "sync-periodic"
        const val NOW = "sync-now"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                    .setConstraints(network)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .build(),
            )
        }

        /** Soon, once there is a network. Repeated calls before it runs collapse into one. */
        fun requestSoon(workManager: WorkManager, delaySeconds: Long = 0) {
            workManager.enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(network)
                    .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build(),
            )
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(PERIODIC)
            workManager.cancelUniqueWork(NOW)
        }
    }
}
