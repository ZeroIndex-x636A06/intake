package com.zeroindex.intake.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.zeroindex.intake.MainActivity
import com.zeroindex.intake.R
import com.zeroindex.intake.domain.training.RestRules
import com.zeroindex.intake.domain.training.RestSound
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
                val sound = RestSound.fromName(intent.getStringExtra(EXTRA_SOUND))
                if (seconds > 0) start(seconds, label, vibrate, total, sound) else stopSelf()
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_STOP -> stopTimer()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(seconds: Int, label: String, vibrate: Boolean, total: Int = seconds, sound: RestSound = RestSound.OFF) {
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
                if (sound != RestSound.OFF) playTone(sound)
                notificationManager().notify(NOTIFICATION_ID, buildDoneNotification(label))
                _state.value = TimerState(label, 0, running = false, totalSeconds = total)
                // Leave the "done" notification up but drop the foreground
                // state, so the service is not holding resources needlessly.
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    /** Freezes the countdown where it is (the workout was paused). The notification stays and says so. */
    private fun pauseTimer() {
        val now = _state.value
        if (!now.running) return
        countdown?.cancel()
        _state.value = now.copy(running = false, paused = true)
        notificationManager().notify(NOTIFICATION_ID, buildNotification(now.label + " (paused)", now.remainingSeconds))
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

    /** One of the short built-in tones, on the notification volume. Nothing is downloaded or bundled. */
    private fun playTone(sound: RestSound) {
        scope.launch {
            val generator = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull() ?: return@launch
            try {
                when (sound) {
                    RestSound.BEEP -> {
                        generator.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
                        delay(400)
                    }
                    RestSound.DOUBLE -> {
                        generator.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
                        delay(350)
                        generator.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
                        delay(400)
                    }
                    RestSound.CHIME -> {
                        generator.startTone(ToneGenerator.TONE_PROP_ACK, 400)
                        delay(600)
                    }
                    RestSound.OFF -> Unit
                }
            } finally {
                generator.release()
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "timers"
        private const val NOTIFICATION_ID = 3001
        const val ACTION_START = "com.zeroindex.intake.timer.START"
        const val ACTION_STOP = "com.zeroindex.intake.timer.STOP"
        const val ACTION_PAUSE = "com.zeroindex.intake.timer.PAUSE"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_LABEL = "label"
        const val EXTRA_VIBRATE = "vibrate"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_SOUND = "sound"

        /** The alert chosen when the rest last started, so a later +15 s, -15 s or resume keeps it. */
        private var lastSound: RestSound = RestSound.OFF
        private var lastVibrate: Boolean = true

        private val _state = MutableStateFlow(TimerState())

        /** Observed by the UI so the on-screen countdown matches the notification. */
        val state: StateFlow<TimerState> = _state.asStateFlow()

        fun start(context: Context, seconds: Int, label: String, vibrate: Boolean, sound: RestSound = RestSound.OFF) {
            lastSound = sound
            lastVibrate = vibrate
            context.startService(
                Intent(context, TimerService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_SECONDS, seconds)
                    .putExtra(EXTRA_LABEL, label)
                    .putExtra(EXTRA_VIBRATE, vibrate)
                    .putExtra(EXTRA_SOUND, sound.name),
            )
        }

        /**
         * "+15 s" or "-15 s" on the rest strip: the countdown carries on with [seconds] more or less. Adding grows the ring
         * with it; taking away down to zero ends the rest (see [RestRules.adjust]).
         */
        fun extend(context: Context, seconds: Int, vibrate: Boolean) {
            val now = _state.value
            if (!now.running && now.remainingSeconds <= 0) return
            val adjusted = RestRules.adjust(now.remainingSeconds, now.totalSeconds, seconds)
            if (adjusted == null) {
                stop(context)
                return
            }
            lastVibrate = vibrate
            context.startService(
                Intent(context, TimerService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_SECONDS, adjusted.remainingSeconds)
                    .putExtra(EXTRA_TOTAL, adjusted.totalSeconds)
                    .putExtra(EXTRA_LABEL, now.label)
                    .putExtra(EXTRA_VIBRATE, vibrate)
                    .putExtra(EXTRA_SOUND, lastSound.name),
            )
        }

        /** The workout was paused: freeze a running rest where it is. */
        fun pause(context: Context) {
            if (!_state.value.running) return
            context.startService(Intent(context, TimerService::class.java).setAction(ACTION_PAUSE))
        }

        /** The workout carries on: a frozen rest counts down again from where it stopped. */
        fun resume(context: Context) {
            val now = _state.value
            if (!now.paused || now.remainingSeconds <= 0) return
            context.startService(
                Intent(context, TimerService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_SECONDS, now.remainingSeconds)
                    .putExtra(EXTRA_TOTAL, now.totalSeconds)
                    .putExtra(EXTRA_LABEL, now.label)
                    .putExtra(EXTRA_VIBRATE, lastVibrate)
                    .putExtra(EXTRA_SOUND, lastSound.name),
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
    /** True while the workout is paused and the rest is frozen. */
    val paused: Boolean = false,
)
