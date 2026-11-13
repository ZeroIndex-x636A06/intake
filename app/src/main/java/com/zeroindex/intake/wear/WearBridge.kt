package com.zeroindex.intake.wear

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.wearprotocol.ActiveWorkout
import com.zeroindex.intake.wearprotocol.RemoteProtocol
import com.zeroindex.intake.wearprotocol.RemoteState
import com.zeroindex.intake.wearprotocol.WearProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/**
 * Amendment §2: keeps the watch's copy of the session in progress current.
 * Whenever a session starts, a set is logged (here or on the watch) or the
 * session ends, the one DataItem at [WearProtocol.PATH_ACTIVE] is replaced or
 * removed. Phones without Google Play services, or with no watch, just log
 * a line - nothing else in the app depends on this.
 */
@Singleton
class WearBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val training: TrainingRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val remote: RemoteStateBuilder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val data by lazy { Wearable.getDataClient(context) }

    fun start() {
        // Never let a failure here reach Application.onCreate: the watch is optional.
        runCatching { startFeeds() }.onFailure { Log.w(TAG, "Watch link not started: ${it.javaClass.simpleName}") }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun startFeeds() {
        training.observeInProgress()
            .flatMapLatest { session ->
                if (session == null) {
                    flowOf(null)
                } else {
                    combine(training.observeSets(session.id), settings.settings.map { it.weightUnit.label }.distinctUntilChanged()) { sets, unit ->
                        val prescriptions = training.prescriptionsFor(session.programDayId, excludeSessionId = session.id)
                        val title = session.programDayId?.let { training.day(it)?.name } ?: "Workout"
                        WatchWorkoutMapper.build(session.id, title, prescriptions, sets, unit, clock.nowMillis())
                    }
                }
            }
            .onEach(::publish)
            .guarded("workout")
            .launchIn(scope)
        // The quick remote's day at a glance. Debounced, and an unchanged state (apart from its timestamp) is not re-sent.
        remote.observe()
            .debounce(750)
            .distinctUntilChanged { a, b -> a.copy(updatedAt = 0) == b.copy(updatedAt = 0) }
            .onEach(::publishState)
            .guarded("state")
            .launchIn(scope)
    }

    /**
     * Nothing about the watch may take the app down. A failure in a feed (a query, the mapper, the Data Layer) ends that
     * feed and is logged, never thrown into the thread's uncaught-exception handler, which would crash the app on launch.
     * Cancellation is left alone.
     */
    private fun <T> Flow<T>.guarded(what: String): Flow<T> = catch { e ->
        if (e is CancellationException) throw e
        Log.w(TAG, "Watch $what feed stopped: ${e.javaClass.simpleName}")
    }

    private suspend fun publishState(state: RemoteState) {
        runCatching {
            val request = PutDataMapRequest.create(RemoteProtocol.PATH_STATE).apply {
                dataMap.putString(WearProtocol.KEY_JSON, RemoteProtocol.encode(state))
            }.asPutDataRequest().setUrgent()
            data.putDataItem(request).await()
        }.onFailure { Log.i(TAG, "No watch link for state: ${it.javaClass.simpleName}") }
    }

    private suspend fun publish(workout: ActiveWorkout?) {
        runCatching {
            if (workout == null) {
                data.deleteDataItems(Uri.parse("wear://*${WearProtocol.PATH_ACTIVE}")).await()
            } else {
                val request = PutDataMapRequest.create(WearProtocol.PATH_ACTIVE).apply {
                    dataMap.putString(WearProtocol.KEY_JSON, WearProtocol.encode(workout))
                }.asPutDataRequest().setUrgent()
                data.putDataItem(request).await()
            }
        }.onFailure { Log.i(TAG, "No watch link: ${it.javaClass.simpleName}") }
    }

    private companion object { const val TAG = "WearBridge" }
}
