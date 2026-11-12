# Wear OS companion

The watch app is a companion to the phone app, not a standalone app. The phone stays the source of truth: the watch only
shows what the phone sends and asks the phone to log. There is no AI on the watch.

## Why the watch "did not start working with workouts" (fixed)

Owner report: the earlier watch build did nothing when a workout was active.

**Root cause: the watch and phone apps were not the same app.** The Data Layer only connects a watch app and a phone app
with the same package name and the same signing key. The owner runs the phone as **Intake Preview**
(`com.zeroindex.intake.preview`, `:app:assemblePreview`, `app/build.gradle.kts` `create("preview")`). `wear/build.gradle.kts`
had only `debug` (`com.zeroindex.intake.debug`) and `release` (`com.zeroindex.intake`), so no watch build could ever pair
with the preview phone build. Phone `debug` and watch `debug`, and phone `release` and watch `release`, did match.

Fix: `wear/build.gradle.kts` now has a `preview` build type: `initWith(debug)`, suffix `.preview`, so it is
`com.zeroindex.intake.preview` and is signed with the same debug key as the phone preview build. Its label is "Intake Preview"
(`wear/src/preview/res/values/strings.xml`).

Second problem, found while tracing, that would have hidden a workout even when paired: the watch looked for the workout
only while its own screen was open (`MainActivity.onStart`). It had no service, so nothing woke it when the phone started
a session. Now `WatchListenerService` is registered for the workout path and posts an ongoing "Workout in progress, tap to
log sets" notification (and removes it when the workout ends). The watch asks for the notification permission at first launch.

Checked and fine: the phone's `PhoneWearListener` manifest filter (scheme `wear`, host `*`, path prefix); the Data Layer
paths are the same constants on both sides (`WearProtocol`); the active-workout item is sent with `setUrgent()`;
`WearBridge.start()` runs from `IntakeApplication.onCreate` and is not gated by any setting.

## The quick remote (no workout running)

The watch home shows:

- **Today:** kcal eaten of target, protein, water of target, in the phone's volume unit (stale numbers from another day are not shown).
- **Add water:** the phone's own water quick sizes (Settings, Water and drinks), and a custom amount with a +/- picker
  (50 ml or 1 fl oz a step). Logs through `HydrationRepository.log`.
- **Add food:** a meal-slot chip (defaults by time of day, tap to change), then
  - **Log it** for today's planned meal (the active plan's main dish this week), through `MealPlanRepository.logMeal`;
  - **Recent foods** and **Favourites**, at the default portion, through `FoodLogRepository.logFood`;
  - **Saved meals** (templates), through `MealTemplateRepository.log`;
  - **Quick add calories**, a number picker plus the slot, through `FoodLogRepository.quickAdd`.
- **Start workout:** today's program day (the same rule Train uses) or an empty workout, through
  `TrainingRepository.startSession`. The phone starts it, the existing active-workout item goes to the watch, and the watch
  switches to the workout controls. A workout started this way is a normal session on the phone, so it is already in the
  training log. No `external_session` table was needed: that item in the backlog was about a workout the watch records by
  itself (heart rate, GPS), which this watch app does not do.

### Allergen safety

Every food request is checked by the phone's engine again at the moment it arrives. The watch never decides and never
clears anything.

- CONTAINS: refused. The watch shows the phone's message and the matched terms.
- MAY CONTAIN: **not logged** from the watch. The phone's answer to this is a sheet a person must read, and a watch cannot
  show it, so the watch says to finish on the phone. (A planned meal or saved meal follows the phone's existing path for
  those: a recipe with only a "may contain" verdict logs with a note to check labels; a template is blocked on CONTAINS.)
- A planned meal the phone already marks CONTAINS is shown as blocked and not offered.
- If the answer arrives while the watch app is closed and it is a refusal, a notification shows it.

### Offline and idempotence

Each tap makes a request DataItem (`/intake/remote/req/<id>`) with a fresh UUID. Play services stores it on the watch and
delivers it when the phone is next reachable, so it survives the phone being out of range, the watch restarting and the app
being closed. The home screen lists it as "<what>: pending, will sync" until the phone answers. The phone applies a request
id once (`RequestLedger`, last 200 answers kept in preferences, written before the request is deleted) and sends the answer
as `/intake/remote/res/<id>`. The request is logged at the time it was tapped. A request older than 48 hours is refused
as expired.

### Files

- `wearprotocol/.../RemoteProtocol.kt` (pure, tested): paths, `RemoteState`, `RemoteRequest` (validation, time rules),
  `RemoteResponse`, `RemoteReplies`, `RequestLedger`, volume formatting, slot rule.
- Phone: `app/.../wear/RemoteStateBuilder.kt` (state), `RemoteRequestHandler.kt` (apply, allergen gate, ledger),
  `PhoneWearListener.kt` (receives requests, answers, deletes), `WearBridge.kt` (publishes state).
- Watch: `WatchRemote.kt` (state, queue, answers), `RemoteScreens.kt` (UI: `ScalingLazyColumn`, chips),
  `WatchListenerService.kt` (wake-up and notifications), `MainActivity.kt` (routes between remote and workout).

No Tile or Complication was added (optional, not cheap enough here). No new Maven dependencies, no schema change.

## Installing the pair (owner)

The watch and the phone must be the **same variant**. The phone preview needs the watch preview:

```sh
./gradlew :app:assemblePreview :wear:assemblePreview
# phone: app/build/outputs/apk/preview/app-preview.apk          (Intake Preview, com.zeroindex.intake.preview)
# watch: wear/build/outputs/apk/preview/wear-preview.apk        (Intake Preview, com.zeroindex.intake.preview)
```

Install the watch APK over ADB (`adb -s <watch> install wear-preview.apk`, with ADB debugging over Wi-Fi or Bluetooth
turned on in the watch's developer options) or from Android Studio. The real app pairs with the release watch build
(`:wear:assembleRelease`, same keystore as the phone release). Debug pairs with debug.

Both must be built on the same machine from the same keystore: preview and debug use the machine's default debug key, so a
phone preview built in one place and a watch preview built in another will not pair.

## Not checked on a real watch

Only compiled, linted and unit-tested (the pure protocol); nothing here has run on a watch or a phone-watch pair.

- The two apps pairing and the Data Layer delivering both ways, including after the phone was out of range.
- The ongoing notification and the first-launch notification permission prompt.
- Whether the phone process wakes from the listener service quickly enough for a tap to be answered at once.
- Layout on round screens (chips, the +/- picker, scrolling) and the swipe-to-dismiss gesture leaving the app from a
  sub-screen (each sub-screen also has a Back chip).
- Whether a request made with the phone far away really goes across on reconnection, and shows as pending meanwhile.
- The phone-side handlers on a device (water, food, template, planned meal, quick add, start workout) end to end.
