# Training gap analysis: openGym vs Intake

**Status:** research brief, no code changed. Written against `claude/optimistic-noether-fullk5` at `bd41da5`
(`IntakeDatabase.VERSION = 6`, MIGRATION_5_6 is the latest).

**Licence guard.** openGym is AGPL-3.0. This document describes what it *does* (features and
behaviour) in our own words. Nothing here was copied from it, and nothing implementing these
packages may be: no code, no text, no exercise catalogue data or animations, no design assets, no
translated strings, no standards tables transcribed from its source. Every package must be built
from the description plus public-domain or first-principles knowledge. The implementing agent must
not open the openGym tree (same rule as `docs/research/LIFTOSAUR-NOTES.md`).

**How this was surveyed.** openGym: README, ROADMAP, CHANGELOG, `docs/` (modules, trainers, watch
sync, Wear OS, data imports, integrations, routines library) and the header comments of the
`frontend/src/lib/*` modules (they state each feature's behaviour), plus the lists of views and
components. Not every screen was opened. Intake: `domain/training/` (`TrainHome`, `WorkoutLogic`
(PreviousSets, PrDetector, SwapRanking), `WeeklyVolume`, `SetRowText`, `SetEdit`, `HistoryFormat`,
`PlateDefaults`, `PlateCalculator`, `ScheduledDeload`, `KnownLifts`, `StartingWeightEstimator`,
`Progression`, `equipment/`, `log/TrainingLog`, `log/TrainingStats`, `muscles/`, `generator/`),
`ui/screens/{train,workout}`, the Train graph in `IntakeNavHost.kt`, `domain/videos/`, `wear/`,
`wearprotocol/`, `docs/WORKOUTS.md`, `VIDEOS.md`, `WEAR.md`, `IA-v5.md`, `TASKS.md`, `HANDOFF.md`.
Intake statements below come from reading code, not from running it; nothing was seen on a device.

**Schema rule.** The database is at v6. The first new schema is v7 with `MIGRATION_6_7`, then
`MIGRATION_7_8` and so on. Each step is added to `ALL` and `MIGRATION_STATEMENTS` in `Migrations.kt`
and gets a `MigrationSchemaTest` case. Released versions and migrations are never edited. If two
packages need schema, land them one at a time and re-check the version before each merge.

Legend: **has** = works as openGym's feature does for practical purposes; **partial** = something
exists but a visible part is missing; **missing** = nothing.

---

## (a) Feature table

### Planning and programs

| openGym feature | Intake today | Gap |
| --- | --- | --- |
| Routine per weekday, session = exercises with sets, reps, rest, notes | **has**: `program` / `program_day` / `program_exercise`, weekday on the day (`WorkoutSchedule.kt`, `ProgramBuilderScreen.kt`) | none |
| Today's session card, rest-day message, "train anyway" | **has**: `WorkoutSection.kt`, `TrainText.restLine`, `WorkoutSchedule.planFor` | none |
| Other ways to start: empty, from a program day, my workouts, repeat last, from a video | **has**: `WorkoutSection.kt` | none |
| Starter plans and a generator | **has**: `ProgramTemplatesScreen.kt`, `ProgramGeneratorScreen.kt` (`KnownLifts`, `StartingWeightEstimator`, `HowBuiltCard`) | none; Intake is ahead on explained generation |
| Programs area: active, other, archived, restore, duplicate, export script, import | **has**: `ProgramsScreen.kt` | none |
| Save a finished workout as a routine or add it to a program | **has**: `WorkoutSummaryScreen.kt` ("Save as workout", "Add to program") | none |
| Move one session to another day without changing the plan | **missing** | per-date override |
| Week starts Monday or Sunday | **missing** (log and volume use Monday weeks, `TrainingLog`, `WeeklyVolume.weekStart`) | setting |
| Rotation editor with rest slots | **partial**: rotation pointer (`currentDayIndex`) drives today when days have no weekday; no editor, no rest entries | editor |
| Multi-week block with %1RM or RPE per exercise per week, deload weeks | **partial**: `program.currentWeek` and `ScheduledDeload` (every N weeks a lighter week, generated programs only) | per-week targets, hand-made programs |
| Deload suggested from signals; push a deload back a week | **partial**: coach deload switch exists; no suggestion card or push-back for non-generated programs | advisor |
| Routine folders, colour, favourites, drag reorder | **missing** | |
| Stored backup exercise per slot | **missing** (runtime swap exists) | |
| Print or PDF a plan; share small file or QR | **partial**: script export/import only | PDF, QR |
| Community routine library index | **missing** | optional |
| Trainer and gym roles | **missing** | conflicts with single-user, WP-21 |

### Set and exercise structure

| openGym feature | Intake today | Gap |
| --- | --- | --- |
| Warm-up sets | **partial**: `WarmupGenerator` and `ActiveWorkoutViewModel.warmups()` exist, but no screen calls it and every logged set is `isWarmup = false` (`ActiveWorkoutScreen.kt:178`) | warm-up rows in the session |
| Drop, rest-pause, back-off, cluster, "to failure" sets | **partial**: `isFailure` column only | set kinds |
| Pyramid sets with their own reps or weight | **missing** | |
| Supersets | **missing** | |
| Circuits, AMRAP, EMOM, Tabata, run/walk with a clock player | **missing** (AMRAP exists as a rep target, `RepTarget.kt`) | |
| Timed holds, per-side, cardio by time, speed, distance, incline, carries | **missing** | |
| Per-side reps | **missing** | |
| Bodyweight exercises with added or assisted load | **partial**: `SetRowText.load` shows "BW" and "+10 lb"; no assisted model | assisted |
| Resistance bands as load | **missing** | |
| Tempo | **missing** | |
| Colour tag and note per set | **missing** | |
| Skill ladders | **missing** | |

### Active workout

| openGym feature | Intake today | Gap |
| --- | --- | --- |
| Set row with Previous column and one-tap copy | **has**: `ActiveSetRows.kt`, `PreviousSets` | none |
| Rest strip: ring, +15 s, Skip, per-exercise rest, vibrate at end | **has**: `RestStrip` in `ActiveWorkoutScreen.kt`, `TimerService`, `restSec` | none |
| Rest wheel up to 15 min, "no timer", -15 s, rest sound or flash, spoken cue | **missing** | |
| Pause the whole workout | **missing** | |
| Plate maths per set with a picture | **has**: `PlateCalculator`, `PlateChip`, `PlateVisual`, unit-aware `PlateDefaults` | none |
| Bar types (EZ, trap, Smith, none), plate counts, per-gym profiles, machine stacks, dumbbell inventory | **missing**: one bar weight and one plate list in settings | |
| Equipment you own drives swap and library | **has**: 40 items, `EquipmentRequirements`, `SwapRanking` | none |
| Live PR trophy on the set | **partial**: `PrDetector` flags a new estimated max or a rep record at a weight | weight record, volume record |
| RIR or RPE column | **missing on the phone**: logs `rpe = null` (`ActiveWorkoutScreen.kt:177`); watch has `RPE_CHOICES` | phone picker |
| Swap exercise ranked by muscle then owned equipment, weight carried over | **has**: `SwapRanking`, `ActiveWorkoutScreen` ("Swap exercise") | none |
| Add, move earlier/later, remove exercise mid-workout | **has** | none |
| Swipe set (delete with Undo, copy) | **missing** (previous-copy tap exists) | |
| Live volume, exercise chips, focus view, collapse finished | **partial**: header shows sets done and time; no volume, chips, focus view | |
| Minimised session bar and resume | **has**: `SessionBar.kt` | none |
| Keep screen awake during a workout | **missing** in the workout (only Cook mode and the watch) | |
| "Still training?" prompt, auto-finish | **missing** | |
| Per-exercise note at log time, standing note | **partial**: program notes shown; `exercise.notes` | |
| "Don't count for progression" | **missing** | |
| Finish summary: records, muscles worked, next time, repeat | **has**: `WorkoutSummaryScreen.kt`, `WorkoutSummaryViewModel` | none |
| Ask a coach mid-workout | **missing** | AI rules, WP-19 |
| Film a set | **missing** (YouTube workout videos exist and exceed openGym here: `VIDEOS.md`) | |

### Progression

| openGym feature | Intake today | Gap |
| --- | --- | --- |
| Linear, double, RPE-based, percentage of 1RM, custom expression | **has**: `Progression.kt` | none |
| Explained targets, stall leads to deload, "next time" on the summary | **has** | none |
| Triple progression, Greyskull-style, time, distance, ladder, cluster, wave | **missing** | |
| Profile default rule | **missing** | |
| Starting-weight estimate, known-lift answers | **has** (Intake is ahead) | none |
| Program script language | **has** (`docs/scripting`, Intake is ahead) | none |

### Exercise library

| openGym feature | Intake today | Gap |
| --- | --- | --- |
| Big catalogue with animations and steps | **partial**: about 94 seeded exercises, 86 with two stills | content, own data only |
| Search, muscle and equipment filters, "my equipment", hidden, custom | **has/partial**: `ExerciseLibraryScreen.kt` (plain text filter) | ranked and typo-tolerant search, aliases |
| Type filter (strength, stretch, mobility) | **missing** | |
| Detail: muscles worked, how-to, history, best reps at each weight, 1RM curve | **has**: `ExerciseDetailSheet.kt`, `ExerciseStatsBuilder` | none |
| Own exercises with own photo | **has**: `ExerciseFormSheet.kt`; no photo override on built-ins | minor |
| Favourites; similar exercises | **missing** (swap ranks similar ones) | |
| Merge two exercises | **missing** | |
| Assisted machines | **missing** | |

### History and logging

| openGym feature | Intake today | Gap |
| --- | --- | --- |
| Training log by week with PR counts; session detail; repeat | **has**: `TrainingLog`, `WorkoutHistoryScreen.kt`, `WorkoutSummaryScreen.kt` | none |
| Calendar view | **missing** | |
| Edit a saved workout | **partial**: change or delete a set's weight and reps (`SetEdit`, summary screen); no date, start, duration, add set, change exercises | |
| Log a past workout; backdate | **missing** | |
| Undo finish; reopen | **missing** | |
| Copy as text | **missing** | |
| Day-off notes; journal | **missing** | |
| Soft delete | **has** | none |

### Analytics

| openGym feature | Intake today | Gap |
| --- | --- | --- |
| Weekly hard sets per muscle against targets, 20-region body diagram, contributing exercises | **has**: `WeeklyVolume`, `MuscleWeights` (primary 1, helper 0.5), `MuscleMap`, `MuscleGeometry`, Train > This week | none (the weighted `exercise_muscle` table is still "left for later") |
| Estimated 1RM per lift and recent PRs on Progress | **has**: `TrainingStats` | formula picker (Epley only) |
| Muscle map modes: recovering, untrained | **missing** (volume mode only) | |
| Fatigue estimate, structural balance, strength levels, percent of body weight, lifetime volume | **missing** | |
| Effort chart | **missing** | |
| Year heatmap, streak | **has**: `Charts.kt`, `ProgressScreen.kt` | none |
| Challenges, levels, badges, recap, share as image | **missing** | |
| Calculators (rep-max table, RPE load, DOTS, pace) | **missing** | |

### Body, health, integrations, devices

| openGym feature | Intake today | Gap |
| --- | --- | --- |
| Body weight, photos with compare, measurements | **has** | none |
| Health Connect: workouts out, weigh-ins in | **has/partial**: `HealthConnectManager.kt`; no per-set heart rate or segments | |
| Export workouts and body data as CSV | **has**: export includes a workouts CSV (`SYNC-EXPORT.md`) | `.fit` only |
| Import from Strong, Hevy, FitNotes, generic CSV | **missing** (program script import only) | |
| Calendar feed, webhooks, Strava, WebDAV | **missing** | |
| Wear OS: quick remote, workout view, log set with reps and RPE, rest | **has/partial**: `wear/` (`MainActivity`, `WatchWorkout`, `WatchRemote`, `WatchListenerService`), `wearprotocol` | set marks, add or remove sets, holds, summary, overview, rest sync both ways |
| Rest-timer notification | **has/partial**: `TimerService` | no Log set action, no widget |
| Gym reminder by location; NFC cards | **missing** (an `nfc/` package exists for another use) | |
| AI coach, MCP | **partial**: deterministic coach; AI on tap only | WP-19 |
| Accounts, passkeys, OIDC, trainers | **n/a**: single-user with an optional sync server | not pursued |

---

## (b) Work packages (real gaps only)

Sizes are for one agent in one worktree: S under a day, M a day or two, L several days. Priority:
P1 first, P2 next, P3 later, P4 only if the owner asks. "Schema v7+" always means the next free
migration (`MIGRATION_6_7` first), added to `ALL` and `MIGRATION_STATEMENTS`, with a
`MigrationSchemaTest` case. New settings go through `SettingsRegistry` and `SettingsControls.kt`,
never onto use screens. New routes go in the Train graph above its "Add new ... routes above this
line" marker unless a second tab opens them.

### Summary table

| WP | Package | Size | Priority | Schema |
|---|---|---|---|---|
| 1 | Effort (RIR/RPE) on the phone | S | P1 | none |
| 2 | Set kinds, warm-up rows, per-side, set tag and note | M | P1 | v7+ |
| 3 | Supersets | M | P1 | v7+ |
| 4 | Rest and clock polish (wheel, no timer, -15 s, alert, pause, keep awake) | S-M | P1 | none |
| 5 | Workout ergonomics (swipe, focus, chips, live volume, steppers) | M | P2 | none |
| 6 | Records, 1RM formulas, calculators | M | P1 | none |
| 7 | Per-gym equipment profiles and loading | L | P2 | v7+ |
| 8 | Exercise kinds: holds, cardio, carries, assisted | L | P2 | v7+ |
| 9 | Progression engine v2 | M | P2 | none expected |
| 10 | Circuits, AMRAP, EMOM, intervals | L | P3 | v7+ |
| 11 | Schedule: rotation editor, moved sessions, week start | M | P2 | v7+ |
| 12 | Blocks and deload advisor | L | P3 | v7+ |
| 13 | Routine folders, colours, backups, print and share | M | P2 | v7+ |
| 14 | Library depth: search, favourites, type, merge | M | P2 | v7+ |
| 15 | Catalogue content growth | L ongoing | P3 | none |
| 16 | History: edit date and time, log past, undo finish, calendar, still-training | M | P1 | none (day notes: v7+) |
| 17 | Muscle analytics: recovery and untrained modes, balance, strength levels, effort chart | M | P2 | none |
| 18 | Import from other apps, `.fit` export | M | P1 | none |
| 19 | Optional AI session adapt | M | P4 | none |
| 20 | Wear OS parity, notification actions, widget, gym reminder | L | P3 | none |
| 21 | Motivation, sharing, integrations; trainers excluded | M | P4 | none |

### WP-1 Effort on the phone (P1, S, no schema)
- **Behaviour:** log RIR or RPE per set with a colour-coded picker and one plain line per level; a setting picks RIR or RPE; the previous session's effort shows beside Previous.
- **Domain:** `domain/training/Effort.kt`: scale conversion (RPE = 10 - RIR), colour band, label. Uses `SetResult.rpe` and the existing `set_log.rpe`.
- **Screens:** `ActiveWorkout` (`ActiveSetRows.kt`, replacing `rpe = null`), `WorkoutSummary`, `ExerciseDetailSheet`.
- **Schema:** none. **Conflicts:** none.

### WP-2 Set kinds, warm-up rows, per-side, tag and note (P1, M, schema v7+)
- **Behaviour:** a set is working, warm-up, drop, rest-pause burst, back-off or "to failure"; its menu converts it; drop and burst add a pre-filled child row; the existing warm-up ramp appears as skippable warm-up rows before the first working set (reusing `WarmupGenerator`, rounded to loadable plates); a colour tag (with shape and name, never colour alone); a set note; a per-side flag for one-sided exercises (log left and right as a pair).
- **Domain:** `SetKind`; `DropSetPlanner` (percent step rounded to a loadable weight); side pairing; volume rules (warm-ups never count, each side counts as a set) as pure functions that `WeeklyVolume`, `PrDetector` and progression all call.
- **Screens:** `ActiveWorkout`, `WorkoutSummary`, `ProgramBuilder` (default kinds per slot).
- **Schema v7+:** `set_log` gains `kind` (backfilled from `isWarmup` and `isFailure`), `side`, `tag`, `note`. Old columns stay. `wearprotocol` gets optional fields only.
- **Conflicts:** none.

### WP-3 Supersets (P1, M, schema v7+)
- **Behaviour:** group two or more exercises in a program day; logging alternates; rest runs after the last member; optional group name and rest; the builder shows brackets; the summary shows the group.
- **Domain:** `SupersetFlow` (next-set order, uneven set counts, when rest fires).
- **Screens:** `ProgramBuilder`, `ActiveWorkout`.
- **Schema v7+:** `program_exercise.groupId`, `groupRestSec`. Ad hoc grouping in a session would also need `set_log.groupId`.
- **Conflicts:** none.

### WP-4 Rest and clock polish (P1, S-M, no schema)
- **Behaviour:** -15 s beside the existing +15 s; a rest wheel up to 15 minutes and "no timer"; rest-end choices (vibrate, a tone from a short built-in list, screen flash, optional spoken cue); pause the workout (clock, rest, holds stop); keep the screen awake while training (setting, default on).
- **Domain:** `WorkoutClock` (pause spans, trained time) and `RestTimerState`, pure. Saved duration counts trained time only.
- **Screens:** `ActiveWorkout`, `timer/TimerService`, Settings > Training.
- **Schema:** none if pause spans live in the live-session state and only net duration is saved.
- **Conflicts:** speech must be on-device TTS only.

### WP-5 Workout ergonomics (P2, M, no schema)
- **Behaviour:** swipe a set left to delete with Undo, right to copy; focus view (one set at a time); exercise chips; collapse finished exercises; live session volume; steppers use the exercise's increment and snap to owned plates.
- **Domain:** `StepperRules`, `SessionVolume`.
- **Screens:** `ActiveWorkout`.
- **Schema:** none. A "don't count for progression" switch would need `workout_session.excludeFromProgression`: a separate small step if wanted.
- **Conflicts:** none. Do not add a button row per set (see (c)).

### WP-6 Records, 1RM formulas, calculators (P1, M, no schema)
- **Behaviour:** a set can earn four record kinds (heaviest weight, rep record, best estimated 1RM, biggest set volume), shown live, in the log and the summary; a formula picker (Epley, Brzycki and others with documented sources) in Settings > Training; a Tools page with a rep-max table, RPE load chart and pace.
- **Domain:** extend `PrKind` and `PrDetector`; `OneRepMaxFormula` registry replacing the fixed `OneRepMax.FORMULA`; `RpeTable`. Records stay computed, never stored.
- **Screens:** `ActiveWorkout`, `WorkoutSummary`, `ExerciseDetailSheet`, `TrainingStats`, new `Route.TrainTools` in the Train graph.
- **Schema:** none. **Conflicts:** formulas come from published sources.
- **Status (branch `agent/wp6-records`):** done for records (heaviest weight, most reps at a weight, best estimated 1RM, best session volume; computed, never stored) in the exercise sheet, Training stats and the summary's record flags; the 1RM formula setting (Epley, Brzycki, Lombardi, average) in Settings > Training, used by every estimate; a Calculators screen with a 1RM calculator, a 50-100% table and a plate loader on `PlateDefaults`. Not built: the RPE load chart and pace (left for a later pass). Unit tests only; not seen on a device.

### WP-7 Per-gym equipment profiles and loading (P2, L, schema v7+)
- **Behaviour:** one profile per place (Home, Big gym): plates with counts, dumbbell sizes, bars (barbell, EZ, trap, Smith, none), machine stack steps, bands. Steppers and plate maths follow the active profile, which the user picks; per-exercise bar choice; unloadable weights round to loadable ones and say so; optional gym-pinned exercise note. Builds on the existing ownership checklist (`EquipmentItems`) and `PlateDefaults`.
- **Domain:** `EquipmentProfile`, `LoadSnapper` (nearest loadable weight: per side for barbells, per hand for dumbbells, stack steps), `PlateCalculator` for counts.
- **Screens:** Settings > Training > Equipment, `ActiveWorkout`, `ProgramBuilder`.
- **Schema v7+:** new `equipment_profile` table (JSON inventory, `deletedAt`), `exercise.barKind`, optional `program_exercise.profileId`. Kilograms stay stored.
- **Conflicts:** location-based auto-switch excluded (WP-20).

### WP-8 Exercise kinds (P2, L, schema v7+)
- **Behaviour:** exercises can be reps, timed hold, distance, cardio (time, distance, speed or pace, incline), carry or assisted. Holds have a countdown with count-in and per-side. Bodyweight lifts can log added or assisted load, and body weight can count in volume. Weekly cardio goals.
- **Domain:** `ExerciseKind`, `SetMetrics` (volume, pace, speed, assisted direction for records).
- **Screens:** `ActiveWorkout`, `ExerciseFormSheet`, `ExerciseDetailSheet`, `ProgramBuilder`.
- **Schema v7+:** `exercise.kind`, `set_log.durationSec`, `distanceM`, `inclinePct`; `program_exercise.targetDurationSec`, `targetDistanceM`.
- **Conflicts:** distances in metres, weights in kg.

### WP-9 Progression engine v2 (P2, M, no schema expected)
- **Behaviour:** triple progression, time and distance progression, a Greyskull-style rule, wave or percentage cycles, a bodyweight ladder rule with a one-tap swap to the next rung, cluster, RPE auto-regulation, a profile default rule. Each outcome still returns a `reason`.
- **Domain:** extend `ProgressionEvaluator`; `Ladder` chains from Intake's own catalogue; script keywords in `docs/scripting/progress-rules.md`.
- **Screens:** `ProgramBuilder`, script import.
- **Schema:** none if `progression_rule.type` is text with `params` JSON (verify the converter first; if it is constrained, a migration is needed).
- **Dependencies:** WP-8 for time and distance.

### WP-10 Circuits, AMRAP, EMOM, intervals (P3, L, schema v7+)
- **Behaviour:** a group runs on the clock: circuit, AMRAP (score = full rounds plus partial), EMOM, work/rest intervals, run/walk; a full-screen player with big countdown, audio and haptic cues that keeps running with the screen off; results logged as sets.
- **Domain:** `CircuitPlan` producing deterministic timed steps from a start time (no drift, survives process death); fake-clock tests.
- **Screens:** new root route `Route.CircuitPlayer`, `ProgramBuilder` group editor.
- **Schema v7+:** `program_exercise.circuitJson` (on top of WP-3's columns).
- **Dependencies:** WP-3, WP-4, WP-8.

### WP-11 Schedule (P2, M, schema v7+)
- **Behaviour:** rotation editor with rest slots; "move today's session to another day" without changing the plan; week starts Monday or Sunday and week-based views follow.
- **Domain:** extend `WorkoutSchedule.planFor` (overrides, rest slots); shared `WeekMath` used by `WeeklyVolume`, `TrainingLog`, the streak.
- **Screens:** `Programs`, `ProgramBuilder`, Train root card.
- **Schema v7+:** `program.rotationJson`, new `schedule_override(date, programDayId, deletedAt)`. Week start is a DataStore setting.

### WP-12 Blocks and deload advisor (P3, L, schema v7+)
- **Behaviour:** any program (not only generated ones) can run as a 1 to 16 week block with per-week %1RM or RPE or a deload; a deload suggestion card when stalls and effort say so, with a one-tap push back; deload weeks still count as training.
- **Domain:** `BlockPlan` (week targets to prescriptions), `DeloadAdvisor` with a reason string; reuse `ScheduledDeload` for the "lighter week" switch.
- **Screens:** `ProgramBuilder` weeks editor, Train root card.
- **Schema v7+:** `program.blockWeeks`, new `program_week` and `program_week_target`.
- **Dependencies:** WP-9 preferred.

### WP-13 Routine organisation and sharing (P2, M, schema v7+)
- **Behaviour:** colour, folders, favourites and drag reorder for programs; a stored backup exercise per slot for a one-tap swap; print or save a plan as PDF with stills; share as file (exists) and QR for small plans; optional library index: a JSON file at a URL the user sets, fetched only when they open the screen, cached, strictly validated.
- **Domain:** `PlanPrintModel`; `LibraryIndex` parser (untrusted input).
- **Screens:** `Programs`, `ProgramBuilder`, new `Route.RoutineLibrary`.
- **Schema v7+:** `program.colorArgb`, `folderId`, `isFavourite`; new `program_folder`; new `program_exercise_backup`; all with `deletedAt`.
- **Conflicts:** the index is network access: user-set, user-initiated, off by default, noted in `docs/PRIVACY.md`.

### WP-14 Library depth (P2, M, schema v7+)
- **Behaviour:** favourites; aliases and ranked search with typo tolerance and gym shorthand; type filter; similar exercises; own photo over a built-in; merge exercise A into B (history, slots, notes move; A hidden, not removed); assisted flag.
- **Domain:** `ExerciseSearch`, reversible `ExerciseMerge` plan in one transaction, `SimilarExercises` (reuse `SwapRanking.similarity`).
- **Screens:** `ExerciseLibrary`, `ExercisePicker`, `ExerciseDetailSheet`, `ExerciseFormSheet`.
- **Schema v7+:** `exercise.isFavourite`, `aliases`, `type`, `isAssisted`, `photoOverridePath`.
- **Conflicts:** merge soft-deletes the source (`deletedAt`).

### WP-15 Catalogue content growth (P3, L ongoing, no schema)
- **Behaviour:** more exercises, steps and stills; translations later.
- **Domain:** a `tools/` validator for catalogue rows and a repo data file.
- **Conflicts:** **licence.** openGym's catalogue and animations must not be used or mirrored, ids included. Use original text and art, or public-domain data with the licence recorded in a provenance doc like `docs/FOOD-DATA.md`.

### WP-16 History (P1, M, no schema; day notes v7+)
- **Behaviour:** edit a finished workout's date, start and duration (weights and reps already editable), add a set or exercise to it; log a past workout; undo Finish and reopen for 24 hours; copy as text; calendar view in the Training log; "Still training?" after a quiet stretch with finish-by-itself that never discards.
- **Domain:** extend `SetEdit` into `SessionEditor` (validates, recomputes duration and records), `ForgottenWorkoutPolicy` (end = last set plus its rest, never "now"), `WorkoutText`.
- **Screens:** `WorkoutHistory`, `WorkoutSummary`, `ActiveWorkout` (prompt).
- **Schema:** none for editing. Optional day-off notes need `day_note(date, tag, text, deletedAt)`, a separate small step.
- **Conflicts:** edits stamp `updatedAt`; nothing is hard-deleted; the geofence variant is excluded (WP-20).

### WP-17 Muscle analytics (P2, M, no schema)
- **Behaviour:** muscle diagram modes for "still recovering" and "untrained"; a fatigue estimate from hard sets with decay; balance ratios between lifts; strength levels per lift from estimated 1RM, body weight and sex; lifts as a share of body weight; lifetime volume; effort trend (after WP-1).
- **Domain:** `MuscleFatigue`, `BalanceRatios`, `StrengthStandards`, built on `WeeklyVolume`, `MuscleWeights` and `TrainingStats`.
- **Screens:** Train > This week, `TrainingStats` route, `ExerciseDetailSheet`.
- **Schema:** none (derived). A weighted `exercise_muscle` table, if wanted, is its own v7+ step.
- **Conflicts:** show the arithmetic and label estimates; standards come from cited published sources.

### WP-18 Import from other apps, `.fit` (P1, M, no schema)
- **Behaviour:** import history from Strong and Hevy CSV, FitNotes and a generic CSV with column mapping; preview; name matching with "pick a match" for unknowns; then commit. Optional `.fit` export of one workout (CSV export already exists).
- **Domain:** `data/training/import/` parsers to a neutral `ImportedWorkout`; `ExerciseMatcher`; dedupe by date plus set signature.
- **Screens:** Settings > Data, result sheet in Train.
- **Schema:** none; source recorded in `notes`. **Conflicts:** none, all on-device.

### WP-19 Optional AI session adapt (P4, M, no schema)
- **Behaviour:** "Ask coach about this session" only on a tap, via `AiGateway` (`USER_ACTION`, `AiAuditTest` row). The reply is up to six changes to the unfinished part of the session, each shown and ticked by the user; logged sets are never touched.
- **Domain:** `SessionAdaptation` validator (strict schema, repair path, stale-index checks) applied by deterministic code.
- **Conflicts:** no training feature may need it; MCP and assistant connectors are out of scope.

### WP-20 Wear OS parity, notification actions, widget, gym reminder (P3, L, no phone schema)
- **Behaviour:** the watch gains warm-up, failure and drop marks, add or remove a set, timed hold, superset grouping, rest in step with the phone (skip and +15 s both ways), overview and summary pages; the ongoing notification gets Log set and Skip rest; a home-screen widget for today's session; an optional arrival reminder for saved places.
- **Domain:** `wearprotocol` version bump with optional fields (set ids already exist); `NotificationState`; reminder rules (at most daily, never after training).
- **Screens:** `wear/`, `WatchWorkoutMapper`, `PhoneWearListener`, Settings > Training.
- **Dependencies:** WP-2, WP-3, WP-4, WP-8 for the set marks and holds.
- **Conflicts:** **location.** Explicit permission, on-device geofence, saved places never leave the phone, off by default, documented in `PRIVACY.md`.

### WP-21 Motivation, sharing, integrations (P4, M, no schema)
- **Behaviour:** weekly streak rule with explanation, monthly challenges, badges, recap card, share a workout or recap as an image; a calendar `.ics` export; Health Connect segments and per-set heart rate.
- **Conflicts:** webhooks, Strava and WebDAV are network features: optional, off by default, routed through the sync server. **Trainers, gyms, OIDC, passkeys and admin: do not build.** They need a multi-user server and consent model that conflicts with the single-user, offline-first design; give a coach an export instead (WP-13, WP-18).

### Rule conflicts at a glance

| Package | Rule at risk | Resolution |
| --- | --- | --- |
| WP-15 | Licence | original or clearly licensed data only |
| WP-20 | Offline-first, privacy | opt-in, on-device location only |
| WP-19 | AI only on explicit action | `AiGateway`, user ticks every change |
| WP-13 | Offline-first | user-set URL, optional, cached |
| WP-4 | Offline-first | on-device TTS only |
| WP-21 | Offline-first, single user | trainers excluded, webhooks off by default |
| Every new table | Soft deletes, kg and cm | `deletedAt`; distances in metres, weights in kg |

---

## (c) Design observations from openGym

What works (take the idea, draw it differently):
- **One quiet workout screen.** One overflow menu per exercise, with the set number acting as the set's own menu, replaced rows of small buttons. The old rows survive only as opt-in switches.
- **Docked rest bar** with the next set's numbers, so you can load the bar while waiting. Intake's rest strip is close; "up next" is the part to borrow.
- **Previous values are the biggest speed-up.** Intake already has this.
- **Effort as colour plus words.** A single cell that tints by closeness to failure, with a plain-language line per level (WP-1).
- **A tiny body map as a label** on each session card, reused for volume, recovery and gaps. Intake has the figure; the card label and the recovery mode are the new parts.
- **Explained targets and "missed reps never add load".** Intake already does this.
- **Swipe with Undo** instead of confirm dialogs.
- **Colour never alone.** Tags pair colour with a shape and a name.
- **Switching areas off** without deleting data. For Intake, presets rather than more toggles on use screens.

What to avoid copying:
- **Density and settings sprawl.** openGym has hundreds of settings and dozens of sheets; one of its own releases exists to calm a screen that grew busy. Keep Intake's "no settings on use screens" rule.
- **Look-alike layout and art.** Its dark base with lime accent, card rhythm, icons, wording and animation art are its identity and licensed assets. Build new parts in Intake's Option A language (the rest bar as a glass ring, the six-region and 20-region figure).
- **Stacking every view mode.** Chips, focus view, list view, card view, dock, live volume and collapse together is too much. Pick two at most.
- **Server-centric and gamified features.** Trainers, admin, OIDC, a community server, seasonal themes and trophy shelves pull away from a calm, personal, offline app.
- **Long explanatory copy.** Prefer one plain sentence per number.
- **Standards and catalogue data presented as fact.** Show formulas and sources and label estimates.

---

## (d) Recommended build order

Each step fits one agent in one worktree; steps in a tier can run in parallel when they touch
different files. No prerequisite branch is needed.

**Tier 1: logging that feels complete (no or small schema)**
1. WP-1 Effort on the phone (S). Unblocks effort history and RPE progression.
2. WP-6 Records, formulas, tools (M). Pure domain.
3. WP-4 Rest and clock polish (S-M).
4. WP-18 Import (M). Biggest win for people switching apps; independent.
5. WP-16 History (M). Needed before power users trust the app with years of data.

**Tier 2: structure (schema steps one at a time: v7, v8, ...)**
6. WP-2 Set kinds and warm-up rows (M, v7). Later packages use these columns.
7. WP-3 Supersets (M, next version).
8. WP-5 Ergonomics (M, no schema).
9. WP-14 Library depth (M, schema).
10. WP-17 Muscle analytics (M, no schema).
11. WP-9 Progression v2 (M).

**Tier 3: deeper planning and equipment**
12. WP-7 Equipment profiles (L, schema).
13. WP-8 Exercise kinds (L, schema).
14. WP-11 Schedule (M, schema).
15. WP-13 Routine organisation and sharing (M, schema).
16. WP-12 Blocks and deload advisor (L, schema).

**Tier 4: surfaces and extras**
17. WP-10 Circuits (L), after 3, 4 and 8.
18. WP-20 Wear OS parity and surfaces (L), after 2, 3, 4 and 8.
19. WP-15 Catalogue growth (ongoing, own data).
20. WP-21 and WP-19 only if the owner asks.

**Schema sequencing.** WP-2, 3, 7, 8, 10, 11, 12, 13, 14 (and the optional day notes and
"don't count" steps) each add a migration. Land one at a time on the integration branch and re-read
`IntakeDatabase.VERSION` before each merge. If two must ship together, use one migration step.

**Verification for every package.** Unit tests for the pure domain code, a `MigrationSchemaTest`
case per schema step, `NavGraphGuardTest` for each new route, lint with 0 errors, and a plain
statement of what was seen on an emulator and what has not been seen on a device.
