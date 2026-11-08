package com.zeroindex.intake

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.zeroindex.intake.data.health.HealthSync
import com.zeroindex.intake.work.MenuIngestionWorker
import com.zeroindex.intake.work.TdeeWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class IntakeApplication : Application(), Configuration.Provider {

    /** WorkManager is initialised through Hilt so workers can inject (SPEC §8). */
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var healthSync: HealthSync

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
        // SPEC §12: Health Connect follows local writes, debounced - never a timer.
        healthSync.start()
    }
}
