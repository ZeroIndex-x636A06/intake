package com.zeroindex.intake.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zeroindex.intake.R
import com.zeroindex.intake.data.repository.TargetShift
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * SPEC §8: when the derived target shifts more than 100 kcal, say so and
 * explain why. Silently moving someone's calorie target is how they lose trust
 * in the number.
 */
@Singleton
class TargetShiftNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyShift(shift: TargetShift) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Permission denied is a normal state, not an error (SPEC §12).
            return
        }

        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Calorie target changes",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Told when the measured maintenance estimate moves your target."
            },
        )

        val direction = if (shift.previousKcal != null && shift.newKcal > shift.previousKcal) {
            "up"
        } else {
            "down"
        }
        val delta = shift.previousKcal?.let { abs(shift.newKcal - it) }
        val body = buildString {
            append("Your target is now ${shift.newKcal} kcal")
            if (delta != null) append(", $direction $delta")
            append(". ")
            append(
                "That came from your own intake and weight trend over the last four weeks, " +
                    "at ${shift.confidence.name.lowercase()} confidence.",
            )
        }

        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Calorie target updated")
                .setContentText("Now ${shift.newKcal} kcal")
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .build(),
        )
    }

    private companion object {
        const val CHANNEL_ID = "target-shift"
        const val NOTIFICATION_ID = 2001
    }
}
