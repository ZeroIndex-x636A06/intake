package com.zeroindex.intake.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.ai.menu.MenuIngestor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Amendment §1: the scheduled menu import. Weekly, on an unmetered network
 * with the battery not low - menus change seasonally, not hourly, and a
 * once-a-week request is the polite rate for someone else's website.
 *
 * Does nothing at all without an OpenRouter key.
 */
@HiltWorker
class MenuIngestionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ingestor: MenuIngestor,
    private val gateway: AiGateway,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!gateway.isConfigured()) return Result.success()
        return try {
            ingestor.runScheduled()
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Scheduled menu import failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MenuIngestionWorker"
        const val WORK_NAME = "weekly-menu-ingestion"

        fun schedule(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MenuIngestionWorker>(7, TimeUnit.DAYS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.UNMETERED)
                            .setRequiresBatteryNotLow(true)
                            .build(),
                    )
                    .build(),
            )
        }
    }
}
