package com.zeroindex.intake.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zeroindex.intake.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The four daily reminders. Each has its own notification channel, so the person can mute one in Android settings. */
enum class ReminderKind(val channelId: String, val channelName: String, val channelDescription: String) {
    WORKOUT(
        "reminder-workout", "Workout reminders",
        "On training days, at your chosen time, until today's workout is done.",
    ),
    MEAL(
        "reminder-meal", "Meal reminders",
        "At each meal's time, only when nothing is logged for that meal yet.",
    ),
    WATER(
        "reminder-water", "Water reminders",
        "Every two hours, only when you are behind for the time of day.",
    ),
    PHOTO(
        "reminder-photo", "Progress photo reminder",
        "When a progress photo is due.",
    ),
}

/**
 * Posts the daily reminder notifications. Skipped quietly when the notification permission is missing (Android 13+
 * asks for it when a reminder is switched on in Settings > Reminders). Returns whether anything was shown.
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun post(kind: ReminderKind, notificationId: Int, title: String, text: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(kind.channelId, kind.channelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = kind.channelDescription
            },
        )
        // Tapping opens the app, the same way the weekly check-in notification does.
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, notificationId, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        manager.notify(
            notificationId,
            NotificationCompat.Builder(context, kind.channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
        return true
    }

    companion object {
        const val ID_WORKOUT = 2101
        const val ID_WATER = 2103
        const val ID_PHOTO = 2104

        /** One notification per meal: breakfast, lunch, dinner use 2110, 2111, 2112. */
        fun idForMeal(ordinal: Int): Int = 2110 + ordinal
    }
}
