package com.zeroindex.intake.work

import android.content.Context
import androidx.work.WorkManager
import com.zeroindex.intake.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Keeps the nightly backup scheduled exactly when a server is set up and the schedule is Nightly. */
@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        settings.settings
            .distinctUntilChanged { a, b -> (a.backupDestination != null) == (b.backupDestination != null) && a.backupSchedule == b.backupSchedule }
            .onEach { BackupWorker.sync(WorkManager.getInstance(context), it) }
            .launchIn(scope)
    }
}
