# Daily reminders

Settings > Reminders (round 3, item D). Notifications only, on this phone. Nothing is sent anywhere, and every
reminder is off until the person switches it on.

## What the person sees

| Setting (id) | Choice | Fires when |
| --- | --- | --- |
| Workout reminder (`workoutRemind`, `workoutTime`) | on/off, time of day | a training day, at or after the time, until today's workout is finished |
| Meal reminders (`mealRemind`, `breakfastTime`, `lunchTime`, `dinnerTime`) | on/off, three times | at the meal's time, only if nothing is logged for that meal today |
| Water reminders (`waterRemind`, `waterHours`) | on/off, from hour and until hour | every two hours in the water hours, only when behind for the time of day |
| Progress photo reminder (`photoReminder`, existing) | off, 2, 3 or 4 weeks | a photo is due (see below) |

Turning on any of them asks for notification permission on Android 13 and later. Denied is fine: the reminder is
skipped and the rest of the app works the same.

## The rules

All decisions are pure functions in `domain/reminders/ReminderRules.kt`, tested in
`ReminderRulesTest`. They take plain values: the time, whether a meal is logged, the workout plan, the water so far.

- **Workout** uses `WorkoutSchedule.planFor`. A `Train` plan means train today. A rest day, or a workout already
  finished today (`trainedToday`), gives no reminder.
- **Meal** is due only if that slot (breakfast, lunch or dinner) has no food logged today. Snacks have no reminder.
- **Water** uses the same behind rule as the Today card: `DailyFocusPicker.expectedWaterShare(hour)` minus
  `DailyFocusPicker.WATER_GAP`. Slots run every two hours from the start hour up to and including the end hour.
- **Progress photo** uses `PhotoReminderRule.isDue` (2, 3 or 4 weeks since the newest photo, or none yet), from 10:00.
  It sends one notification per photo cycle: taking a new photo starts a new cycle. A person who never takes a photo
  gets one notification, not a daily one.
- A time-of-day reminder is sent only during the six hours after its time. If the phone was off all evening, the
  reminder is skipped, not sent at night.

## How it runs

- `work/ReminderWorker.kt` is a `PeriodicWorkRequest` every 15 minutes (the shortest WorkManager allows), scheduled
  from `IntakeApplication.onCreate` with the KEEP policy. It does nothing when every reminder is off. It needs no
  network.
- `work/ReminderNotifier.kt` posts the notifications. Four channels: "Workout reminders", "Meal reminders", "Water
  reminders", "Progress photo reminder". Tapping a notification opens the app.
- `work/ReminderLedger.kt` remembers what has already gone out, so each slot fires once per day. It is kept in
  SharedPreferences on this device, not in the settings store, so it is never exported, backed up or synced.

## Settings keys

Switches and times are stored in the settings DataStore and travel with an export (`PortableSettings`):
`workout_reminder_on`, `workout_reminder_minute`, `meal_reminder_on`, `breakfast_reminder_minute`,
`lunch_reminder_minute`, `dinner_reminder_minute`, `water_reminder_on`, `water_reminder_start_hour`,
`water_reminder_end_hour`. Times are minutes after midnight, local time. Each new control is listed in
`docs/SETTINGS-INVENTORY.md` (ids 122 to 129).

## Not verified here

The notifications, the permission prompt and the 15-minute timing were not seen on a device. Check them on the phone:
switch each reminder on with a time a few minutes ahead, confirm the notification shows once, and confirm that logging
the meal or finishing the workout stops the next one.
