package com.zeroindex.intake.wear

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.zeroindex.intake.wearprotocol.RemoteProtocol
import com.zeroindex.intake.wearprotocol.RemoteRequest
import com.zeroindex.intake.wearprotocol.RemoteResponse
import com.zeroindex.intake.wearprotocol.RemoteState
import com.zeroindex.intake.wearprotocol.ResponseStatus
import com.zeroindex.intake.wearprotocol.WearProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** True while the activity is on screen, so the background service knows whether to post a notification itself. */
internal object WatchVisibility {
    @Volatile var visible: Boolean = false
}

/**
 * The watch's side of the quick remote: the phone's day ([state]), the requests waiting for the phone ([pending]) and the
 * phone's latest answer ([notice]).
 *
 * A request is a DataItem. Play services stores it on the watch and delivers it when the phone is next reachable, so it
 * survives the phone being out of range, the watch restarting and this app being closed. The request stays in the Data Layer
 * until the phone has acted on it and deleted it, which is what "pending, will sync" means. The request id is generated here,
 * and the phone applies an id once, so a re-delivery cannot log twice.
 */
class WatchRemote(context: Context) : DataClient.OnDataChangedListener {

    private val data = Wearable.getDataClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<RemoteState?>(null)
    val state: StateFlow<RemoteState?> = _state.asStateFlow()

    private val _pending = MutableStateFlow<Map<String, RemoteRequest>>(emptyMap())
    /** Requests the phone has not answered yet, oldest first. */
    val pending: StateFlow<Map<String, RemoteRequest>> = _pending.asStateFlow()

    private val _notice = MutableStateFlow<RemoteResponse?>(null)
    /** The phone's latest answer, until the person dismisses it (or, for a plain success, a few seconds). */
    val notice: StateFlow<RemoteResponse?> = _notice.asStateFlow()

    private var noticeJob: Job? = null

    fun start() {
        data.addListener(this)
        scope.launch {
            runCatching {
                val items = data.getDataItems(Uri.parse("wear://*/intake/remote/"), DataClient.FILTER_PREFIX).await()
                val now = System.currentTimeMillis()
                items.forEach { item ->
                    val path = item.uri.path.orEmpty()
                    val json = DataMapItem.fromDataItem(item).dataMap.getString(WearProtocol.KEY_JSON)
                    when {
                        path == RemoteProtocol.PATH_STATE -> readState(json)
                        path.startsWith(RemoteProtocol.PATH_REQ_PREFIX) -> readRequest(json, now)
                        path.startsWith(RemoteProtocol.PATH_RES_PREFIX) -> readResponse(item.uri, json)
                    }
                }
                items.release()
            }
        }
    }

    fun stop() {
        data.removeListener(this)
    }

    /** Queues [request] for the phone and shows it as pending straight away. */
    fun send(request: RemoteRequest) {
        _pending.update { it + (request.id to request) }
        scope.launch {
            val put = PutDataMapRequest.create(RemoteProtocol.PATH_REQ_PREFIX + request.id).apply {
                dataMap.putString(WearProtocol.KEY_JSON, RemoteProtocol.encode(request))
            }.asPutDataRequest().setUrgent()
            runCatching { data.putDataItem(put).await() }
        }
    }

    fun dismissNotice() {
        noticeJob?.cancel()
        _notice.value = null
    }

    override fun onDataChanged(events: DataEventBuffer) {
        val frozen = events.map { it.type to it.dataItem.freeze() }
        events.release()
        val now = System.currentTimeMillis()
        frozen.forEach { (type, item) ->
            val path = item.uri.path.orEmpty()
            when {
                path == RemoteProtocol.PATH_STATE && type == DataEvent.TYPE_CHANGED ->
                    readState(DataMapItem.fromDataItem(item).dataMap.getString(WearProtocol.KEY_JSON))
                path.startsWith(RemoteProtocol.PATH_REQ_PREFIX) && type == DataEvent.TYPE_DELETED ->
                    // The phone deleted it: it has acted on it.
                    _pending.update { it - path.removePrefix(RemoteProtocol.PATH_REQ_PREFIX) }
                path.startsWith(RemoteProtocol.PATH_REQ_PREFIX) && type == DataEvent.TYPE_CHANGED ->
                    readRequest(DataMapItem.fromDataItem(item).dataMap.getString(WearProtocol.KEY_JSON), now)
                path.startsWith(RemoteProtocol.PATH_RES_PREFIX) && type == DataEvent.TYPE_CHANGED ->
                    readResponse(item.uri, DataMapItem.fromDataItem(item).dataMap.getString(WearProtocol.KEY_JSON))
            }
        }
    }

    private fun readState(json: String?) {
        _state.value = json?.let { runCatching { RemoteProtocol.decodeState(it) }.getOrNull() } ?: return
    }

    private fun readRequest(json: String?, now: Long) {
        val request = json?.let { runCatching { RemoteProtocol.decodeRequest(it) }.getOrNull() } ?: return
        if (now - request.at > RemoteProtocol.MAX_REQUEST_AGE_MS) {
            // The phone would refuse it as too old; do not show it as waiting for ever.
            scope.launch { runCatching { data.deleteDataItems(Uri.parse("wear://*${RemoteProtocol.PATH_REQ_PREFIX}${request.id}")).await() } }
            return
        }
        _pending.update { it + (request.id to request) }
    }

    private fun readResponse(uri: Uri, json: String?) {
        val response = json?.let { runCatching { RemoteProtocol.decodeResponse(it) }.getOrNull() }
        scope.launch { runCatching { data.deleteDataItems(uri).await() } }
        if (response == null) return
        _pending.update { it - response.requestId }
        show(response)
    }

    private fun show(response: RemoteResponse) {
        noticeJob?.cancel()
        _notice.value = response
        if (response.status == ResponseStatus.DONE) {
            noticeJob = scope.launch {
                delay(4_000)
                _notice.value = null
            }
        }
    }
}
