package com.zeroindex.intake.wear

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.zeroindex.intake.wearprotocol.ActiveWorkout
import com.zeroindex.intake.wearprotocol.LoggedSet
import com.zeroindex.intake.wearprotocol.WearProtocol
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Play Services tasks as suspend calls, without pulling in another library. */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/**
 * The watch's view of the phone's active workout, from the Data Layer.
 *
 * Logging a set writes a DataItem, which the Data Layer keeps and delivers
 * whenever the phone is next reachable, and marks the set done here at once,
 * so the lifter never waits on Bluetooth between sets.
 */
class WatchWorkout(context: Context) : DataClient.OnDataChangedListener {

    private val data = Wearable.getDataClient(context)
    private val nodes = Wearable.getNodeClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _workout = MutableStateFlow<ActiveWorkout?>(null)
    val workout: StateFlow<ActiveWorkout?> = _workout.asStateFlow()

    private val _phoneConnected = MutableStateFlow(true)
    val phoneConnected: StateFlow<Boolean> = _phoneConnected.asStateFlow()

    /** Sets logged here that the phone has not reflected back yet. */
    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    fun start() {
        data.addListener(this)
        scope.launch {
            runCatching {
                val items = data.getDataItems(Uri.parse("wear://*${WearProtocol.PATH_ACTIVE}")).await()
                items.forEach { item -> if (item.uri.path == WearProtocol.PATH_ACTIVE) read(DataMapItem.fromDataItem(item).dataMap.getString(WearProtocol.KEY_JSON)) }
                items.release()
            }
        }
        scope.launch {
            while (true) {
                _phoneConnected.value = runCatching { nodes.connectedNodes.await().isNotEmpty() }.getOrDefault(false)
                delay(15_000)
            }
        }
    }

    fun stop() {
        data.removeListener(this)
    }

    private fun read(json: String?) {
        _workout.value = json?.let { runCatching { WearProtocol.decodeWorkout(it) }.getOrNull() }
        _pending.value = 0
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.dataItem.uri.path != WearProtocol.PATH_ACTIVE) return@forEach
            when (event.type) {
                DataEvent.TYPE_CHANGED -> read(DataMapItem.fromDataItem(event.dataItem).dataMap.getString(WearProtocol.KEY_JSON))
                DataEvent.TYPE_DELETED -> read(null)
            }
        }
        events.release()
    }

    fun logSet(exerciseIndex: Int, reps: Int, rpe: Double?, restTakenSec: Int?) {
        val w = _workout.value ?: return
        val exercise = w.exercises.getOrNull(exerciseIndex) ?: return
        val set = exercise.nextSet ?: return
        val logged = LoggedSet(
            clientId = UUID.randomUUID().toString(),
            sessionId = w.sessionId,
            exerciseId = exercise.exerciseId,
            setIndex = set.setIndex,
            weightKg = set.weightKg,
            reps = reps,
            rpe = rpe,
            completedAt = System.currentTimeMillis(),
            restTakenSec = restTakenSec,
        )
        // Optimistic: show it done now; the phone's next publish confirms it.
        _workout.value = w.copy(
            exercises = w.exercises.mapIndexed { i, e ->
                if (i != exerciseIndex) e else e.copy(sets = e.sets.map { if (it.setIndex == set.setIndex) it.copy(done = true, reps = reps, rpe = rpe) else it })
            },
        )
        _pending.value += 1
        scope.launch {
            val request = PutDataMapRequest.create(WearProtocol.PATH_LOGGED_PREFIX + logged.clientId).apply {
                dataMap.putString(WearProtocol.KEY_JSON, WearProtocol.encode(logged))
            }.asPutDataRequest().setUrgent()
            runCatching { data.putDataItem(request).await() }
        }
    }
}
