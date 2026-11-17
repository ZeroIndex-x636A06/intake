# Saved workouts, weekday scheduling and starting weights

## My workouts (custom workouts)

A saved workout is a single-session routine such as "Arm day" or "Hotel gym": named exercises with sets, reps,
rest and a note each. It can be started any time, with or without a program. Train shows **My workouts** with a
Start button per workout.

### How it is modelled, and why

A workout is a **program with `isStandalone = true` and exactly one day** (new column `program.isStandalone`,
`migrateExercises_2_3`). The alternative was a new `workout_template` table.

Why a flag, not a table:

- Starting a session, the active-workout screen, progression rules and starting weights, history, the
  program builder, the program script exporter, sync, encrypted backup and export all already work on
  `program` / `program_day` / `program_exercise`. A second table would mean a second copy of every one of those.
- "Copy a program day" and "save a freestyle session" already produce a day with exercise rows; a workout is
  that day under a standalone program, so all three creation routes share one builder (`WorkoutBuilder`).
- Soft delete, `updatedAt` merging in sync and the v2 backup format come for free (old rows decode with
  `isStandalone = false`).

What the flag changes: the program list leaves standalone programs out (`ProgramDao.observeAll`), they are never
active, and finishing a session on one does not advance a rotation. The builder opens on a workout with no
"Make active", no "Add day" and no weekday controls.

### Ways to make one

1. **From scratch:** Train, My workouts, New workout, then add exercises in the builder.
2. **After a session:** finishing a freestyle session asks "Keep this workout?" with **Save as workout instead**.
3. **From a program day:** the day menu in the builder has **Copy to My workouts**. The original is untouched.

Edit (builder), rename, duplicate and delete are in the workout's menu. Delete is a soft delete: the program,
day and exercise rows are stamped `deletedAt`, never removed, and sessions logged on it stay in history.
A `# Workout:` script kind was not added.

## Weekday scheduling

Chosen training weekdays now schedule program days. `program_day.weekday` holds an ISO weekday (1 Monday to 7
Sunday) or null.

- **Profile** (Training you can do): "Which days?" chips. Choosing weekdays sets the number of days
  (`UserProfile.effectiveTrainingDays`), so the generator plans exactly those days.
- **A new program** (generated, from a template, or from Your plan) gets weekdays in
  `TrainingRepository.finish` through `WorkoutSchedule.weekdaysFor`: your chosen weekdays if their count matches
  the days in the program, an even spread if you chose none, otherwise none (rotation).
- **Today's card and the coach** use `WorkoutSchedule.planFor`: the day whose weekday is today is "today's
  workout"; on any other day the card says "Rest day, next: Lower A, Wednesday" with **Train anyway**.
  The coach's "workout today" is true on a scheduled day.
- **A program with no weekdays** (older programs, imports, hand-built) **keeps the rotation**: the next day in
  order, whichever weekday it is. A day's weekday can be set in the builder (day menu, Set weekday).

## Starting weights

`domain/training/StartingWeightEstimator.kt`, pure and tested. Used when a program is created
(`TrainingRepository.finish`), written to `program_exercise.startWeightKg` with a note, and shown on the first
session as "Start light; adjusts after your first session". It moves on from the first logged session.

1. **Same lift has history:** best estimated max from the last session (Epley, `OneRepMax`), converted to the
   new rep range (midpoint of the range), times **87.5%** (the middle of 85 to 90%).
2. **A related lift has history** (a dumbbell press when you have benched): the same sum, scaled by a fixed
   ratio between the two lifts (per hand for dumbbells). The ratios are rough rules of thumb.
3. **No history, a key lift** (bench, overhead press, squat, deadlift, row, pulldown, hip thrust and their
   family): a fraction of body weight by experience, for about 10 reps, then moved to the rep range:

   | Lift | New | Some | Experienced |
   | --- | --- | --- | --- |
   | Bench press | 0.40 | 0.65 | 0.85 |
   | Overhead press | 0.25 | 0.40 | 0.55 |
   | Back squat | 0.50 | 0.80 | 1.10 |
   | Deadlift | 0.60 | 1.00 | 1.35 |
   | Barbell row | 0.35 | 0.55 | 0.75 |
   | Lat pulldown | 0.40 | 0.60 | 0.80 |
   | Hip thrust | 0.50 | 0.85 | 1.20 |

   Scaled by 0.62 (upper body) or 0.80 (lower body) when sex is female. Needs a weigh-in.
4. **Anything else** (isolation, bodyweight, power, no weigh-in) gets no number: "pick a weight".

Rounding is always down: barbell lifts to a load your plates can make (never below the empty bar), everything
else in 2.5 kg steps. All weights are kg.

## Reasons for every exercise

Each generated exercise has a one-line `why` built from the answers that picked it, for example
"Lat pulldown: back is a focus, 2 of your 4 days train back, cable machine available." The numbers line
(role, reps, effort, progression) is separate (`prescription`). The reason is saved as the exercise's note.
Your plan and the generator preview show **How this plan was built** (`TrainingExplainer`): which answers
were used (goal, experience, days and weekdays, session length, equipment, focus muscles, areas to go easy
on, hidden exercises, starting weights) and which were not ("Your build and age do not change exercise choice;
they affect calories.").

## Train home, the active workout and the summary (Option A part 3)

- **Train > Workout** shows today's session (it follows weekday scheduling; a rest day says "Rest day, next: ..." and offers Train anyway), Other ways to start (Empty workout, From a program, My workouts, From a video, Repeat last), this week's muscle volume on a 6-region body map, and recent sessions. Programs (list, builder, make-one, templates, import, export as a script, archive and restore, My workouts) is one tap away and is the only place a program is edited.
- **One session at a time.** `TrainingRepository.startSession` returns the running session, so every Start resumes it. The back arrow minimises the workout; a session bar above the pill on every tab root brings it back. Finish asks Finish / Keep going / Discard (a soft delete of the session and its sets), then opens the summary.
- **Set rows** show Set, Previous (the same set last time; tap to copy), weight, reps and a tick. The tick logs the set, plays the check, buzzes and starts the rest timer; the rest strip has a ring, +15 s and Skip. Barbell lifts get a "Per side" line from the bar and plates in Settings. A set that beats the best estimated max, or the reps at that weight, wears a trophy (`PrDetector`); a first-ever set is never a record.
- **Swap exercise** ranks alternatives by muscle similarity, then owned equipment (`SwapRanking`); the weight is the new lift's own last weight or a cautious estimate from the one replaced (`SwapWeight`). It changes this session only.
- **Weekly volume** counts calendar weeks (Monday start) against Settings > Training > Advanced. **Weighted muscles: the fallback was built** (main muscle 1.0, each helper region 0.5, `MuscleWeights`); there is no `exercise_muscle` table and no schema change. The 1.0 / 0.5 / 0.25 table stays on the backlog.
- **Summary**: records, minutes, volume, sets, PRs, muscles worked against this week's targets, the exercises, next-time changes (latest session of a program day only) and, for an empty workout, Keep this workout. It is also the read-only view of an older session.
- A workout made from a video keeps the video id on its program row (`program.videoId`); My workouts offers "Watch the video". See `docs/VIDEOS.md`.
- The watch Data Layer feed (`WearBridge`) is unchanged. A swap or added exercise is phone-only until a set is logged.

## Round 5: body diagram, specific equipment, Training log and exercise history

### The body diagram (item 15)

An original front and back anatomical-style figure replaces the 6-region map. Twenty muscle regions are separate paths:
upper and mid/lower chest, front, side and rear delts, biceps, triceps, forearms, abs, obliques, upper traps, mid back, lats,
lower back, glutes, quads, hamstrings, calves, inner thighs (adductors) and hip abductors, plus thin anatomy lines
(collarbone, spine, ab lines, kneecap). It is drawn by `tools/musclemap/build_musclemap.py`, which writes
`domain/training/muscles/MuscleGeometry.kt` (generated; do not hand-edit) and an SVG preview. `MuscleMap` smooths the corner
points with Catmull-Rom, the same maths as the preview. `design/mockup-r5/muscle-map.png` is that preview, coloured to show the
three statuses. Licence: original work for this app, no third-party art (see `docs/EXERCISE-IMAGES.md`).

- **Weighted muscles.** `MuscleWeights.muscleCredit` credits a set per region: the main muscle's regions (narrowed by the
  exercise name, so a lateral raise is the side delt) 1.0, helper regions 0.5, never both for one region. A region's target is
  its body region's weekly target times `MuscleWeights.targetShare` (1.0 for the big movers, 0.5 for traps, lower back, forearms,
  obliques, calves, inner thighs and abductors). `WeeklyVolume.computeMuscles` returns all 20 with contributors and the same
  under / in range / over bands as before. The six-region rows, hint and targets are unchanged.
- **Where it shows.** Train > Workout week card: tap any muscle to list the exercises that counted for it. Exercise detail and
  the post-workout summary use the same figure (`MuscleMapView`) with the exercise's or session's muscles lit.
- Tests: `MuscleVolumeTest`, `MuscleMapTest`.

### Specific equipment (item 16)

`domain/training/equipment/EquipmentItems.kt`: 40 items in four groups (free weights, racks and benches, machines, other).
Stored as a JSON set of item ids in the existing profile value (`UserProfile.equipmentItems`, plus `dumbbellMaxKg`), so there is
no schema change. `UserProfile.equipment` (the five old kinds) is kept in step with `EquipmentItems.kindsOf`, because the video
suggestions and older code read it.

- **Quick picks** fill the list: Full gym is every item; Home dumbbells is adjustable dumbbells, an adjustable bench and a
  pull-up bar; Bodyweight only is a pull-up bar. Ticking items by hand moves the chip to "My own list" (or back to a quick pick
  when the list matches one).
- **Requirements.** `EquipmentRequirements.forExercise(name, oldKind)` returns groups; each group needs any one of its items
  (a bench press needs a bar, plates and a flat or adjustable bench; a back squat needs a rack, squat stand or Smith machine;
  a pulldown needs a lat pulldown or a cable stack; a leg press needs a leg press). Names win over kinds, so custom exercises
  and "Kettlebell ..." or "Resistance band ..." work. The generator (`GeneratorInputs.equipmentItems`) and swap ranking
  (`SwapRanking.rank(ownedItems)`) and the library's "My equipment" chip use it. With no item list they use the old kinds.
- **Migration.** A profile saved before the checklist has `equipmentItems = null` and reads through
  `EquipmentItems.fromLegacy`: a saved preset gives its items; custom kinds add what each used to allow (barbell: a barbell,
  plates, a power rack and benches; dumbbell: dumbbells and an adjustable bench; machine: the machines, Smith machine and
  preacher bench; cable: the cable items; bodyweight: pull-up bar and dip station). A test proves no catalog exercise that
  was allowed under the old rule is lost. Nothing is written until the person changes something.
- **UI.** Settings > Training > Equipment: quick-pick chips, then the grouped checklist (all / none per group, heaviest
  dumbbell). The welcome flow has a new Equipment step (three quick picks, then "Advanced: pick each item"); the program wizard
  step 4 has the same three cards plus "Advanced: pick each item". The heaviest dumbbell is stored but not yet used to cap
  suggested weights.
- Extra items beyond the brief: a hip abduction machine (the library has one). A machine lateral raise maps to the shoulder
  press machine item.

### Training log and exercise history (item 17)

- **Training log** (Train > Workout > All sessions): every session newest first, grouped by calendar week (Monday start;
  "This week", "Last week", then date ranges), each row with date, day or workout name (program as the subtitle), minutes,
  sets, volume and a PR count (`TrainingLog`, counted in the order the sets were done with `PrDetector`). The exercise picker
  and the estimated max chart are gone from this screen. Watch-started sessions are ordinary sessions on the phone
  (`RemoteRequestHandler.startWorkout` calls `startSession`), so they are listed; one that was never finished still shows if it
  has sets ("Unfinished"), and an empty unfinished one is hidden.
- **Session detail** (the existing summary screen): every exercise with every set (warm-ups marked), the notes box, the
  records, and **Repeat this workout** (starts a new session on the same program day; a freestyle session offers Keep this
  workout instead).
- **Exercise detail** (Train > Exercises > an exercise) has **Your history**: best set with its estimated max, heaviest weight,
  total volume, sessions and sets, the estimated max over time on the scrub-able `TrendLineChart`, best reps at each weight,
  the last 10 sessions with their sets and (PR) markers, and "No history yet" when empty (`ExerciseStatsBuilder`).
- Tests: `TrainingLogTest`, `EquipmentItemsTest`, `OnboardingFlowTest`.
