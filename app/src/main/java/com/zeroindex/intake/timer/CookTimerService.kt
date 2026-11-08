package com.zeroindex.intake.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zeroindex.intake.MainActivity
import com.zeroindex.intake.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One running cook-mode timer. [endsAtElapsed] is a [SystemClock.elapsedRealtime]
 * deadline, so the remaining time is always computed from it rather than counted
 * down, and a pause of the process or a clock change cannot drift it.
 */
data class CookTimer(
    val id: String,
    val label: String,
    val totalSeconds: Int,
    val endsAtElapsed: Long,
) {
    fun remainingSeconds(nowElapsed: Long = SystemClock.elapsedRealtime()): Int =
        (((endsAtElapsed - nowElapsed) + 999) / 1000).toInt().coerceAtLeast(0)
}

/**
 * SPEC §9: cook-mode step timers as a foreground service, so they survive
 * leaving the app. Several can run at once, one notification lists them with a
 * countdown chronometer for the one ending soonest. A finished timer posts its
 * own notification that chimes and vibrates. Separate from [TimerService] (the
 * rest timer), whose short-service type is capped at about three minutes.
 */
class CookTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_ID) ?: return stopIfIdle()
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 0)
                val label = intent.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { "Timer" }
                if (seconds <= 0) return stopIfIdle()
                _timers.update { list ->
                    list.filterNot { it.id == id } +
                        CookTimer(id, label, seconds, SystemClock.elapsedRealtime() + seconds * 1000L)
                }
                enterForeground()
                ensureTicker()
            }
            ACTION_CANCEL -> {
                val id = intent.getStringExtra(EXTRA_ID)
                _timers.update { list -> if (id == null) emptyList() else list.filterNot { it.id == id } }
                if (_timers.value.isEmpty()) shutDown() else refreshOngoing()
            }
            else -> return stopIfIdle()
        }
        return START_NOT_STICKY
    }

    /**
     * Called when a start command arrives without anything to run. A service
     * started with startForegroundService must still enter the foreground, so
     * show the (empty-safe) notification before stopping.
     */
    private fun stopIfIdle(): Int {
        if (_timers.value.isEmpty()) {
            startForegroundCompat(buildOngoing())
            shutDown()
        }
        return START_NOT_STICKY
    }

    private fun enterForeground() = startForegroundCompat(buildOngoing())

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ONGOING_ID, notification)
        }
    }

    private fun ensureTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(500)
                val now = SystemClock.elapsedRealtime()
                val finished = _timers.value.filter { it.endsAtElapsed <= now }
                if (finished.isNotEmpty()) {
                    _timers.update { list -> list.filterNot { t -> finished.any { it.id == t.id } } }
                    finished.forEach { done(it) }
                    if (_timers.value.isEmpty()) {
                        shutDown(keepDoneNotifications = true)
                        return@launch
                    }
                    refreshOngoing()
                }
            }
        }
    }

    private fun done(timer: CookTimer) {
        vibrate()
        val notification = NotificationCompat.Builder(this, DONE_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${timer.label} is done")
            .setContentText("Timer finished")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        runCatching { notificationManager().notify(DONE_BASE_ID + (timer.id.hashCode() and 0xffff), notification) }
    }

    private fun refreshOngoing() {
        runCatching { notificationManager().notify(ONGOING_ID, buildOngoing()) }
    }

    private fun shutDown(keepDoneNotifications: Boolean = true) {
        ticker?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (!keepDoneNotifications) notificationManager().cancelAll()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildOngoing(): android.app.Notification {
        val now = SystemClock.elapsedRealtime()
        val timers = _timers.value.sortedBy { it.endsAtElapsed }
        val soonest = timers.firstOrNull()
        val builder = NotificationCompat.Builder(this, ONGOING_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
        if (soonest == null) {
            builder.setContentTitle("Cook timers")
        } else {
            val remainingMs = (soonest.endsAtElapsed - now).coerceAtLeast(0)
            builder
                .setContentTitle(if (timers.size == 1) soonest.label else "${timers.size} cook timers running")
                .setContentText(
                    if (timers.size == 1) "Counting down" else timers.joinToString(" · ") { it.label },
                )
                // A countdown the system draws itself, so it stays correct with no updates from here.
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + remainingMs)
                .setShowWhen(true)
                .addAction(0, if (timers.size == 1) "Cancel" else "Cancel all", cancelIntent(null))
        }
        return builder.build()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun cancelIntent(id: String?): PendingIntent = PendingIntent.getService(
        this, 11,
        Intent(this, CookTimerService::class.java).setAction(ACTION_CANCEL).apply { id?.let { putExtra(EXTRA_ID, it) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannels() {
        val nm = notificationManager()
        nm.createNotificationChannel(
            NotificationChannel(ONGOING_CHANNEL, "Cook timers running", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The countdown while a cook-mode step timer runs."
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(DONE_CHANNEL, "Cook timer finished", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Chimes when a cook-mode step timer finishes."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
        )
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1))
    }

    companion object {
        private const val ONGOING_CHANNEL = "cook_timers"
        private const val DONE_CHANNEL = "cook_timers_done"
        private const val ONGOING_ID = 3101
        private const val DONE_BASE_ID = 3200
        const val ACTION_START = "com.zeroindex.intake.timer.COOK_START"
        const val ACTION_CANCEL = "com.zeroindex.intake.timer.COOK_CANCEL"
        const val EXTRA_ID = "id"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_LABEL = "label"

        private val _timers = MutableStateFlow<List<CookTimer>>(emptyList())

        /** Running timers; the UI derives remaining time from each deadline. */
        val timers: StateFlow<List<CookTimer>> = _timers.asStateFlow()

        fun start(context: Context, id: String, label: String, seconds: Int) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CookTimerService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_ID, id)
                    .putExtra(EXTRA_LABEL, label)
                    .putExtra(EXTRA_SECONDS, seconds),
            )
        }

        fun cancel(context: Context, id: String) {
            if (_timers.value.none { it.id == id }) return
            context.startService(
                Intent(context, CookTimerService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_ID, id),
            )
        }
    }
}
