package com.zeroindex.intake.wear

import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.wearprotocol.RemoteProtocol
import com.zeroindex.intake.wearprotocol.WearProtocol
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * Receives sets logged on the watch. Called by Google Play services on a
 * background thread whenever the watch's queued items arrive - immediately
 * when connected, or on reconnection - so blocking here is fine. Each item is
 * deleted once written, which is the watch's signal that it went through.
 */
@AndroidEntryPoint
class PhoneWearListener : WearableListenerService() {

    @Inject lateinit var training: TrainingRepository
    @Inject lateinit var handler: RemoteRequestHandler

    override fun onDataChanged(events: DataEventBuffer) {
        val changed = events.filter { it.type == DataEvent.TYPE_CHANGED }.map { it.dataItem.freeze() }
        events.release()
        val items = changed.filter { it.uri.path?.startsWith(WearProtocol.PATH_LOGGED_PREFIX) == true }
        val requests = changed.filter { it.uri.path?.startsWith(RemoteProtocol.PATH_REQ_PREFIX) == true }
        if (items.isEmpty() && requests.isEmpty()) return
        val client = Wearable.getDataClient(this)
        runBlocking {
            requests.forEach { item -> answer(client, item) }
            items.forEach { item ->
                val json = DataMapItem.fromDataItem(item).dataMap.getString(WearProtocol.KEY_JSON) ?: return@forEach
                val set = runCatching { WearProtocol.decodeSet(json) }.getOrElse {
                    Log.w(TAG, "Unreadable set from watch", it)
                    return@forEach
                }
                training.logSetFromWatch(
                    id = set.clientId,
                    sessionId = set.sessionId,
                    exerciseId = set.exerciseId,
                    setIndex = set.setIndex,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    rpe = set.rpe,
                    completedAt = set.completedAt,
                    restTakenSec = set.restTakenSec,
                )
                runCatching { client.deleteDataItems(item.uri).await() }
            }
        }
    }

    /**
     * One request from the watch's quick remote: apply it (once, by id), put the answer where the watch will see it, and
     * only then delete the request. If anything here fails before the delete, Play services delivers the request again
     * and the id makes that harmless.
     */
    private suspend fun answer(client: DataClient, item: DataItem) {
        val json = DataMapItem.fromDataItem(item).dataMap.getString(WearProtocol.KEY_JSON)
        val request = json?.let { runCatching { RemoteProtocol.decodeRequest(it) }.getOrNull() }
        if (request == null) {
            Log.w(TAG, "Unreadable request from watch")
            runCatching { client.deleteDataItems(item.uri).await() }
            return
        }
        val response = handler.handle(request)
        runCatching {
            val put = PutDataMapRequest.create(RemoteProtocol.PATH_RES_PREFIX + request.id).apply {
                dataMap.putString(WearProtocol.KEY_JSON, RemoteProtocol.encode(response))
            }.asPutDataRequest().setUrgent()
            client.putDataItem(put).await()
        }.onFailure { Log.i(TAG, "Could not send the answer: ${it.javaClass.simpleName}") }
        runCatching { client.deleteDataItems(item.uri).await() }
    }

    private companion object { const val TAG = "PhoneWearListener" }
}
