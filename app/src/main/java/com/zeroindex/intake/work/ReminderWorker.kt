package com.zeroindex.intake.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.HydrationRepository
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.photos.ProgressPhotoRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.reminders.ReminderRules
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.time.localDateOf
import com.zeroindex.intake.domain.training.ScheduleDay
import com.zeroindex.intake.domain.training.TodayPlan
import com.zeroindex.intake.domain.training.WorkoutSchedule
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * The daily reminders (Settings > Reminders). Runs every 15 minutes, reads the settings and today's data, asks the
 * pure rules in [ReminderRules] whether each reminder is due now, and posts at most one notification per slot per day
 * (the [ReminderLedger] remembers what has gone out). Off reminders cost nothing: with every switch off it stops early.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsRepository,
    private val training: TrainingRepository,
    private val foodLog: FoodLogRepository,
    private val hydration: HydrationRepository,
    private val photos: ProgressPhotoRepository,
    private val clock: Clock,
    private val ledger: ReminderLedger,
    private val notifier: ReminderNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val s = settings.settings.first()
        if (s.workoutReminderOn || s.mealReminderOn || s.waterReminderOn || s.photoReminderWeeks > 0) {
            val now = clock.nowMillis()
            val zone = clock.zone()
            val time = Instant.ofEpochMilli(now).atZone(zone).toLocalTime()
            val nowMinute = time.hour * 60 + time.minute
            val today = clock.today()
            if (s.workoutReminderOn) workout(s, today, nowMinute)
            if (s.mealReminderOn) meals(s, today, nowMinute)
            if (s.waterReminderOn) water(s, today, time.hour)
            if (s.photoReminderWeeks > 0) photo(s, now, time.hour)
        }
        Result.success()
    } catch (e: Exception) {
        Log.w(TAG, "Reminder check failed", e)
        Result.retry()
    }

    /** Training day, workout not finished yet, at or after the chosen time. */
    private suspend fun workout(s: AppSettings, today: LocalDate, nowMinute: Int) {
        if (ledger.sent(today, SLOT_WORKOUT)) return
        val program = training.observeActiveProgram().first() ?: return
        val days = training.observeDays(program.id).first()
        if (days.isEmpty()) return
        val finished = training.observeSessionStats(1).first().firstOrNull()?.finishedAt
        val plan = WorkoutSchedule.planFor(
            days.map { ScheduleDay(it.dayIndex, it.name, it.weekday) },
            program.currentDayIndex,
            today.dayOfWeek.value,
            trainedToday = finished != null && localDateOf(finished, clock.zone()) == today,
        )
        if (!ReminderRules.workoutDue(nowMinute, s.workoutReminderMinute, plan)) return
        val workoutName = (plan as? TodayPlan.Train)?.day?.name
        notifier.post(
            ReminderKind.WORKOUT,
            ReminderNotifier.ID_WORKOUT,
            "Workout today",
            workoutName?.let { "$it is on your plan today." } ?: "A session is on your plan today.",
        )
        ledger.markSent(today, SLOT_WORKOUT)
    }

    /** Each meal slot, at its time, if nothing is logged for that slot today. */
    private suspend fun meals(s: AppSettings, today: LocalDate, nowMinute: Int) {
        val logged = foodLog.observeDay(today).first().map { it.mealSlot }.toSet()
        listOf(
            MealSlot.BREAKFAST to s.breakfastReminderMinute,
            MealSlot.LUNCH to s.lunchReminderMinute,
            MealSlot.DINNER to s.dinnerReminderMinute,
        ).forEach { (slot, minute) ->
            val key = "$SLOT_MEAL_PREFIX${slot.name}"
            if (ledger.sent(today, key)) return@forEach
            if (!ReminderRules.mealDue(nowMinute, minute, slotLoggedToday = slot in logged)) return@forEach
            val name = slot.name.lowercase().replaceFirstChar { it.uppercase() }
            notifier.post(
                ReminderKind.MEAL,
                ReminderNotifier.idForMeal(slot.ordinal),
                "Log $name",
                "Nothing logged for $name yet.",
            )
            ledger.markSent(today, key)
        }
    }

    /** Every two hours in the water hours, when behind for the time of day. One check per slot hour. */
    private suspend fun water(s: AppSettings, today: LocalDate, hour: Int) {
        if (!ReminderRules.isWaterSlot(hour, s.waterReminderStartHour, s.waterReminderEndHour)) return
        val key = "$SLOT_WATER_PREFIX$hour"
        if (ledger.sent(today, key)) return
        val day = hydration.observeDay(today).first()
        val behind = ReminderRules.waterBehind(hour, day.consumedMl, day.target.totalMl)
        if (!ReminderRules.waterDue(hour, s.waterReminderStartHour, s.waterReminderEndHour, behind)) return
        notifier.post(
            ReminderKind.WATER,
            ReminderNotifier.ID_WATER,
            "Drink some water",
            "${s.volumeUnit.format(day.consumedMl)} of ${s.volumeUnit.format(day.target.totalMl)} so far, " +
                "a little behind for this time of day.",
        )
        ledger.markSent(today, key)
    }

    /** Once per photo cycle: due by the photo rule, from 10 in the morning. */
    private suspend fun photo(s: AppSettings, now: Long, hour: Int) {
        val newest = photos.observe().first().maxOfOrNull { it.takenAt }
        val cycle = newest ?: 0L
        if (ledger.photoCycleSent(cycle)) return
        if (!ReminderRules.photoDue(hour, s.photoReminderWeeks, newest, now)) return
        notifier.post(
            ReminderKind.PHOTO,
            ReminderNotifier.ID_PHOTO,
            "Progress photo due",
            "It has been ${s.photoReminderWeeks} weeks since your last photo.",
        )
        ledger.markPhotoCycleSent(cycle)
    }

    companion object {
        private const val TAG = "ReminderWorker"
        const val WORK_NAME = "daily-reminders"
        private const val SLOT_WORKOUT = "workout"
        private const val SLOT_MEAL_PREFIX = "meal-"
        private const val SLOT_WATER_PREFIX = "water-"

        /**
         * Every 15 minutes, the shortest period WorkManager allows. KEEP leaves an existing schedule alone, so a launch
         * does not reset it. Works offline: no network constraint.
         */
        fun schedule(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
    }
}
