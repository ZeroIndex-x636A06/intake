package com.zeroindex.intake.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zeroindex.intake.data.profile.AutoTargetOutcome
import com.zeroindex.intake.data.profile.AutoTargets
import com.zeroindex.intake.data.repository.TargetShift
import com.zeroindex.intake.data.repository.TdeeRepository
import com.zeroindex.intake.domain.tdee.TdeeCalculator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * SPEC §8: recalculates the TDEE estimate nightly over a 28-day rolling window
 * and re-derives the calorie target from it.
 */
@HiltWorker
class TdeeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val tdeeRepository: TdeeRepository,
    private val autoTargets: AutoTargets,
    private val notifier: TargetShiftNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val result = tdeeRepository.recalculate()
        // One computation for the target, shared with the plan screen and Settings (QA Q4-6).
        val outcome = autoTargets.refresh()
        // SPEC §8: notify when the target moves more than 100 kcal, and say
        // why rather than silently changing the number.
        if (result != null && outcome is AutoTargetOutcome.Updated && outcome.previousKcal != null &&
            TdeeCalculator.shouldNotifyShift(outcome.previousKcal, outcome.target.kcal)
        ) {
            notifier.notifyShift(TargetShift(outcome.previousKcal, outcome.target.kcal, true, result.confidence))
        }
        Result.success()
    } catch (e: Exception) {
        Log.w(TAG, "Nightly TDEE recalculation failed", e)
        // Retry rather than fail: the window is rolling, so a later run is
        // just as good.
        Result.retry()
    }

    companion object {
        private const val TAG = "TdeeWorker"
        const val WORK_NAME = "nightly-tdee-recalculation"

        fun schedule(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TdeeWorker>(1, TimeUnit.DAYS)
                    .setConstraints(
                        Constraints.Builder()
                            // Nothing here needs the network; it just should not
                            // fight the user for the CPU.
                            .setRequiresBatteryNotLow(true)
                            .build(),
                    )
                    .setInitialDelay(initialDelayMinutes(), TimeUnit.MINUTES)
                    .build(),
            )
        }

        /** Aims the first run at roughly 3am local time. */
        private fun initialDelayMinutes(): Long {
            val now = java.time.LocalTime.now()
            val target = java.time.LocalTime.of(3, 0)
            val minutesNow = now.hour * 60 + now.minute
            val minutesTarget = target.hour * 60 + target.minute
            val delta = minutesTarget - minutesNow
            return if (delta > 0) delta.toLong() else (delta + 24 * 60).toLong()
        }
    }
}
