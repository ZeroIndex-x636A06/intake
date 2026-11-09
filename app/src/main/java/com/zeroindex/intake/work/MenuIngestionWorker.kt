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
import com.zeroindex.intake.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Amendment §1: the scheduled menu import. Weekly, on an unmetered network
 * with the battery not low - menus change seasonally, not hourly, and a
 * once-a-week request is the polite rate for someone else's website.
 *
 * Off by default (SPEC-v3 "AI policy"): it does nothing at all unless the person switched on "Refresh saved
 * menus weekly" in Settings → AI, "Use AI features" is on, and an OpenRouter key exists. When it runs, a
 * page with structured menu data is refreshed with no model call; only sources that need the model use
 * it, under the gateway's guard (trigger: opt-in schedule) and the monthly budget.
 */
@HiltWorker
class MenuIngestionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ingestor: MenuIngestor,
    private val gateway: AiGateway,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!settings.settings.first().menuAutoRefresh || !gateway.isConfigured()) return Result.success()
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

        /** Registers the weekly job when the switch goes on and removes it when it goes off. */
        fun apply(workManager: WorkManager, enabled: Boolean) {
            if (enabled) schedule(workManager) else workManager.cancelUniqueWork(WORK_NAME)
        }

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
