package com.zeroindex.intake.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * SPEC §9 and §10: a countdown that runs as a foreground service with the
 * remaining seconds in a notification, so it survives leaving the app, with an
 * optional vibration at zero.
 *
 * Used by both the rest timer and cook mode's inline step timers.
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var countdown: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val seconds = intent.getIntExtra(EXTRA_SECONDS, 0)
                val label = intent.getStringExtra(EXTRA_LABEL) ?: "Rest"
                val vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true)
                val total = intent.getIntExtra(EXTRA_TOTAL, seconds).coerceAtLeast(seconds)
                if (seconds > 0) start(seconds, label, vibrate, total) else stopSelf()
            }
            ACTION_STOP -> stopTimer()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(seconds: Int, label: String, vibrate: Boolean, total: Int = seconds) {
        countdown?.cancel()
        _state.value = TimerState(label = label, remainingSeconds = seconds, running = true, totalSeconds = total)
        startForeground(NOTIFICATION_ID, buildNotification(label, seconds))

        countdown = scope.launch {
            var remaining = seconds
            while (isActive && remaining > 0) {
                delay(1_000)
                remaining -= 1
                _state.value = TimerState(label, remaining, running = remaining > 0, totalSeconds = total)
                if (remaining > 0) {
                    notificationManager().notify(
                        NOTIFICATION_ID,
                        buildNotification(label, remaining),
                    )
                }
            }
            if (isActive) {
                if (vibrate) vibrate()
                notificationManager().notify(NOTIFICATION_ID, buildDoneNotification(label))
                _state.value = TimerState(label, 0, running = false, totalSeconds = total)
                // Leave the "done" notification up but drop the foreground
                // state, so the service is not holding resources needlessly.
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun stopTimer() {
        countdown?.cancel()
        _state.value = TimerState()
        notificationManager().cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        countdown?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(label: String, remaining: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(label)
            .setContentText(format(remaining))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openApp())
            .addAction(0, "Stop", stopIntent())
            .build()

    private fun buildDoneNotification(label: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$label done")
            .setContentText("Time to go again.")
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, TimerService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        notificationManager().createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Timers",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Rest timers and cook-mode step timers."
                setShowBadge(false)
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
        vibrator.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    companion object {
        private const val CHANNEL_ID = "timers"
        private const val NOTIFICATION_ID = 3001
        const val ACTION_START = "com.zeroindex.intake.timer.START"
        const val ACTION_STOP = "com.zeroindex.intake.timer.STOP"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_LABEL = "label"
        const val EXTRA_VIBRATE = "vibrate"
        const val EXTRA_TOTAL = "total"

        private val _state = MutableStateFlow(TimerState())

        /** Observed by the UI so the on-screen countdown matches the notification. */
        val state: StateFlow<TimerState> = _state.asStateFlow()

        fun start(context: Context, seconds: Int, label: String, vibrate: Boolean) {
            context.startService(
                Intent(context, TimerService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_SECONDS, seconds)
                    .putExtra(EXTRA_LABEL, label)
                    .putExtra(EXTRA_VIBRATE, vibrate),
            )
        }

        /** "+15 s" on the rest strip: the countdown carries on with [seconds] more, and the ring's total grows with it. */
        fun extend(context: Context, seconds: Int, vibrate: Boolean) {
            val now = _state.value
            if (!now.running && now.remainingSeconds <= 0) return
            context.startService(
                Intent(context, TimerService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_SECONDS, now.remainingSeconds + seconds)
                    .putExtra(EXTRA_TOTAL, now.totalSeconds + seconds)
                    .putExtra(EXTRA_LABEL, now.label)
                    .putExtra(EXTRA_VIBRATE, vibrate),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TimerService::class.java).setAction(ACTION_STOP),
            )
        }

        fun format(seconds: Int): String {
            val m = seconds / 60
            val s = seconds % 60
            return "%d:%02d".format(m, s)
        }
    }
}

data class TimerState(
    val label: String = "",
    val remainingSeconds: Int = 0,
    val running: Boolean = false,
    /** The length of this rest, for the countdown ring. 0 when unknown. */
    val totalSeconds: Int = 0,
)
