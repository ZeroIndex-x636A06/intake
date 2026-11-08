package com.zeroindex.intake.work

import android.content.Context
import androidx.work.WorkManager
import com.zeroindex.intake.data.backup.backupTables
import com.zeroindex.intake.data.db.IntakeDatabase
import com.zeroindex.intake.data.db.dao.ExportDao
import com.zeroindex.intake.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * While this device is paired: a periodic sync, one on launch, and one shortly
 * after local changes settle. Edits made in a burst (logging a whole meal)
 * become one sync, not one per row.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val db: IntakeDatabase,
    private val exportDao: ExportDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var changes: Job? = null

    /** Every synced table; the sync's own writes trigger at most one extra, empty round. */
    private val watched: Array<String> by lazy {
        backupTables(exportDao).map { it.name }.filter { it != "backup_run" }.toTypedArray()
    }

    @OptIn(FlowPreview::class)
    fun start() {
        val work = WorkManager.getInstance(context)
        settings.settings.map { it.sync.paired }.distinctUntilChanged().onEach { paired ->
            changes?.cancel()
            if (!paired) {
                SyncWorker.cancel(work)
                return@onEach
            }
            SyncWorker.schedulePeriodic(work)
            SyncWorker.requestSoon(work)
            changes = db.invalidationTracker.createFlow(*watched)
                .debounce(30_000)
                .onEach { SyncWorker.requestSoon(work) }
                .launchIn(scope)
        }.launchIn(scope)
    }
}
