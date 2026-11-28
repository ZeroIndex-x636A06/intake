package com.zeroindex.intake.wear

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.zeroindex.intake.wearprotocol.RemoteProtocol
import com.zeroindex.intake.wearprotocol.ResponseStatus
import com.zeroindex.intake.wearprotocol.WearProtocol
import kotlinx.coroutines.runBlocking

/**
 * Wakes the watch app when the phone sends something while the app is closed (the Data Layer starts this service for
 * the paths in the manifest). Before this existed the watch only looked for a workout while its screen was open, so a
 * workout started on the phone never showed up on its own.
 *
 * - A workout starts on the phone: an ongoing notification "Workout in progress. Tap to log sets", which opens the app.
 *   It goes away when the workout ends.
 * - The phone answers a quick-remote request that the watch app is not on screen for, and the answer is a refusal
 *   (allergen block, needs the phone): a notification says so, so a warning that arrives late is never missed.
 */
class WatchListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        val items = events.map { it.type to it.dataItem.freeze() }
        events.release()
        items.forEach { (type, item) ->
            val path = item.uri.path.orEmpty()
            when {
                path == WearProtocol.PATH_ACTIVE && type == DataEvent.TYPE_CHANGED -> {
                    val json = DataMapItem.fromDataItem(item).dataMap.getString(WearProtocol.KEY_JSON)
                    val workout = json?.let { runCatching { WearProtocol.decodeWorkout(it) }.getOrNull() }
                    if (workout != null) WatchNotifications.workoutRunning(this, workout.title) else WatchNotifications.cancelWorkout(this)
                }
                path == WearProtocol.PATH_ACTIVE && type == DataEvent.TYPE_DELETED -> WatchNotifications.cancelWorkout(this)
                path.startsWith(RemoteProtocol.PATH_RES_PREFIX) && type == DataEvent.TYPE_CHANGED && !WatchVisibility.visible -> {
                    val json = DataMapItem.fromDataItem(item).dataMap.getString(WearProtocol.KEY_JSON)
                    val response = json?.let { runCatching { RemoteProtocol.decodeResponse(it) }.getOrNull() }
                    if (response != null && response.status != ResponseStatus.DONE) WatchNotifications.refusal(this, response.message)
                    // Seen (or notified about): the app has no more to show for it.
                    if (response != null) runBlocking { runCatching { Wearable.getDataClient(this@WatchListenerService).deleteDataItems(item.uri).await() } }
                }
            }
        }
    }
}

internal object WatchNotifications {
    private const val CHANNEL = "workout"
    private const val CHANNEL_NOTICE = "notice"
    private const val ID_WORKOUT = 1
    private const val ID_NOTICE = 2

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    /** Below Android 13 there is no runtime permission; from 13 on the person must have said yes. */
    private fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun open(context: Context) = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun workoutRunning(context: Context, title: String) {
        if (!allowed(context)) return
        val m = manager(context)
        m.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.channel_workout), NotificationManager.IMPORTANCE_DEFAULT))
        // Posted again on every set; only the first one should buzz.
        if (m.activeNotifications.any { it.id == ID_WORKOUT }) return
        m.notify(
            ID_WORKOUT,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Workout in progress")
                .setContentText("$title. Tap to log sets.")
                .setOngoing(true)
                .setContentIntent(open(context))
                .build(),
        )
    }

    fun cancelWorkout(context: Context) = manager(context).cancel(ID_WORKOUT)

    fun refusal(context: Context, message: String) {
        if (!allowed(context)) return
        val m = manager(context)
        m.createNotificationChannel(NotificationChannel(CHANNEL_NOTICE, "Not logged", NotificationManager.IMPORTANCE_HIGH))
        m.notify(
            ID_NOTICE,
            Notification.Builder(context, CHANNEL_NOTICE)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Intake")
                .setContentText(message)
                .setStyle(Notification.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setContentIntent(open(context))
                .build(),
        )
    }
}
