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
