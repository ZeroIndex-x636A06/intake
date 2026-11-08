package com.zeroindex.intake.timer

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Placeholder for the rest-timer and cook-mode timer foreground service
 * (SPEC §9, §10). Declared in the manifest so the component exists; the
 * countdown, notification and vibration land with the training phase.
 */
class TimerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
}
