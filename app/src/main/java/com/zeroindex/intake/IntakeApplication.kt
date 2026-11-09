package com.zeroindex.intake

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.zeroindex.intake.data.health.HealthSync
import com.zeroindex.intake.wear.WearBridge
import com.zeroindex.intake.work.BackupScheduler
import com.zeroindex.intake.work.BaselineWorker
import com.zeroindex.intake.work.MenuIngestionWorker
import com.zeroindex.intake.work.SyncScheduler
import com.zeroindex.intake.work.TdeeWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class IntakeApplication : Application(), Configuration.Provider {

    /** WorkManager is initialised through Hilt so workers can inject (SPEC §8). */
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var healthSync: HealthSync
    @Inject lateinit var backupScheduler: BackupScheduler
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var wearBridge: WearBridge

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // SPEC §8: nightly recalculation. KEEP, so an existing schedule is not
        // reset on every launch.
        TdeeWorker.schedule(WorkManager.getInstance(this))
        MenuIngestionWorker.schedule(WorkManager.getInstance(this))
        // Goal builder: a daily look at the baseline week; it does nothing unless one is running.
        BaselineWorker.schedule(WorkManager.getInstance(this))
        // Coach: a daily look at whether the weekly check-in is due.
        com.zeroindex.intake.work.CoachWorker.schedule(WorkManager.getInstance(this))
        // SPEC §12: Health Connect follows local writes, debounced - never a timer.
        healthSync.start()
        // Amendment §2: nightly backup, scheduled only once a server is set up.
        backupScheduler.start()
        // Amendment §3: device sync, only while paired.
        syncScheduler.start()
        // Amendment §2: mirror the session in progress to a paired watch.
        wearBridge.start()
    }
}
