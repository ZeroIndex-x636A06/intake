package com.zeroindex.intake.data.health

import com.zeroindex.intake.data.db.dao.BodyMetricDao
import com.zeroindex.intake.data.db.dao.FoodLogDao
import com.zeroindex.intake.data.db.dao.HydrationDao
import com.zeroindex.intake.data.db.dao.WorkoutDao
import com.zeroindex.intake.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * When Health Connect syncs (SPEC §12): on app resume, and after any local
 * write, debounced. Never on a timer.
 */
@Singleton
class HealthSync @Inject constructor(
    private val manager: HealthConnectManager,
    private val foodLogDao: FoodLogDao,
    private val bodyDao: BodyMetricDao,
    private val hydrationDao: HydrationDao,
    private val workoutDao: WorkoutDao,
    private val clock: Clock,
) {
    private companion object {
        /** A burst of logging becomes one sync, half a minute after it stops. */
        const val WRITE_DEBOUNCE_MS = 30_000L
        /** Resuming the app twice in a minute does not sync twice. */
        const val RESUME_MIN_GAP_MS = 5 * 60_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastResumeSync = 0L

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            combine(
                foodLogDao.observeLastChange(),
                bodyDao.observeLastChange(),
                hydrationDao.observeLastChange(),
                workoutDao.observeLastChange(),
            ) { a, b, c, d -> listOf(a, b, c, d) }
                .distinctUntilChanged()
                .drop(1) // the first value is the state at launch, not a write
                .debounce(WRITE_DEBOUNCE_MS)
                .collect { manager.sync() }
        }
    }

    fun onResume() {
        val now = clock.nowMillis()
        if (now - lastResumeSync < RESUME_MIN_GAP_MS) return
        lastResumeSync = now
        scope.launch { manager.sync() }
    }

    fun syncNow() = scope.launch { manager.sync() }
}
