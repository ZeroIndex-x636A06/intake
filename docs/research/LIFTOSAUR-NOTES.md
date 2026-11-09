# Liftosaur study: what "deep training insight" means for Intake

Research notes, written 2026-11-09. Nothing here changes app code. It feeds a
future task on the board (`docs/TASKS.md`); nobody has claimed it yet.

## 1. Licence note (read first)

Liftosaur (https://github.com/astashov/liftosaur) is licensed **AGPL-3.0**.

- We read its code and docs only to learn **what features exist and how they
  work conceptually**. A feature idea is not copyrightable; its code, data and
  artwork are.
- **Nothing from Liftosaur may be copied into Intake.** That covers source
  code, the exercise-to-muscle tables, the exercise list and its wording, the
  Liftoscript grammar text, doc text, the muscle drawings and the exercise
  images. AGPL would also oblige Intake to publish its own source under AGPL
  if any of it were used.
- Everything in section 6 onwards is **original design**, built from general
  exercise-science and gym knowledge. The taxonomy and weights in section 6
  were written for this note, not transcribed from any source.
- Liftosaur's exercise images appear to come from a commercial stock-image
  library (the repo has an extractor script for gymvisual.com). Do not use them
  or imitate them closely.
- Practical clean-room rule for whoever implements this: do not keep the
  Liftosaur source open while writing the Intake code. Work from this note and
  from Intake's own specs.
- Rule 9 in `CLAUDE.md` still applies: keep model identifiers out of anything
  that comes out of this.

## 2. How Liftosaur works (feature inventory)

The clone was read at depth 1 on 2026-11-09 (about 3,400 files). Sources
consulted: `src/models/{exercise,muscle,muscleRangePresets}.ts`, `src/types.ts`,
and the `docs/` pages (`content/liftoscript.md` and `features/*.md`). Treat the
behaviour below as read from docs and code, not run on a device.

### 2.1 Exercise model

- **Exercise = (id, equipment).** A built-in exercise is a base movement id
  plus an equipment, for example "bench press" + barbell or dumbbell. The same
  movement exists as separate entries per equipment (about 164 base
  movements and 374 images/pages, one per movement-equipment pair). Each pair
  has its own image and description.
- **Equipment types (11):** barbell, cable, dumbbell, smith machine, band,
  kettlebell, bodyweight, "leverage machine" (a catch-all for plate-loaded and
  selectorised machines), medicine ball, EZ bar, trap bar. This is coarser than
  the user asked for: cable attachments, machine kinds, benches and racks are not
  modelled.
- **Per-exercise metadata:** default equipment, "kind" tags (core, pull, push,
  legs, upper, lower), a default warmup count, a starting weight per unit, and a
  body-part tag list.
- **Equipment is also a loading model, not just a label.** Each equipment item
  carries a *load solver*: plates on a bar with a base weight, a weight stack
  with pin steps, fixed weights (dumbbells, kettlebells), bands, or an
  electric step. Targets get rounded down to what you can actually load, and the
  UI explains why ("Why is the weight adjusted?") and draws the plates. Equipment
  lists are per **gym**, so a home gym and a commercial gym can differ, and
  there is a "show only available equipment" filter.
- **Unilateral and implement-count flags:** a per-exercise volume multiplier
  (a dumbbell press loads two implements, so volume doubles; a kettlebell swing
  loads one) and a unilateral flag that sums left and right reps. Both can be
  overridden per exercise.
- **Custom exercises:** name, default equipment, kinds, target muscles,
  synergist muscles, notes, an image (from the library, camera or upload),
  "clone from another exercise", and an AI autofill of muscles. Soft-deleted.
- **Exercise variations inside a program slot:** a slot can list several
  movements (split squat | Bulgarian split squat | pistol squat) and a progression
  script advances the index. Sets, reps and progression are shared.
- **Picker:** search by name and equipment words, filter by equipment, kind,
  muscle group or single muscle, favourites, multi-select, a "similar muscles"
  sort used for swaps, and "recent swaps". A swap carries the weight across by
  looking up history at the nearest reps, or the 1RM.

### 2.2 Muscle model

- **Three layers.**
  1. **Muscles (39 anatomical names)** such as the clavicular and sternal heads of
     the pectoralis major, deltoid heads, trapezius by fibre region,
     erector spinae, hamstrings, wrist flexors/extensors and so on.
  2. **Muscle groups, called "screen muscles" (23)**, each a fixed set of muscles:
     13 "main" groups (chest, lats, upper back, front/side/rear delts, biceps,
     triceps, quads, hamstrings, glutes, calves, abs) and 10 smaller ones (upper
     traps, lower back, neck, rotator cuff, forearm flexors, forearm extensors,
     obliques, hip flexors, abductors, adductors). Users can hide groups, regroup
     muscles, and add custom groups.
  3. **Body parts (9)** and the six "kinds" (upper/lower/core/push/pull/legs) as
     coarse tags.
- **Role per exercise is two-valued: target or synergist.** An exercise lists
  target muscles and synergist muscles. When counting sets, a target group gets
  1 set and a synergist group gets a fraction (a global default of 0.5, with
  optional per-group multipliers per exercise). A group containing both a target
  and a synergist muscle gets the target credit only. So the "weighting" is
  coarse: effectively 1.0, a tunable fraction, or nothing.
- **Strength vs hypertrophy bucket:** a set with 8+ reps counts towards
  hypertrophy, fewer towards strength. Default target split 30/70.
- **Per-exercise override:** a user can override the target and synergist muscles
  of any built-in exercise.

### 2.3 Insights and stats

- **Week card on the home screen:** total volume, completed sets, new PR count,
  each with a delta against the previous week.
- **Week Insights sheet:** PRs set that week (with the previous best); strength vs
  hypertrophy set split coloured against a target; sets and days per movement
  kind; then **sets per muscle group coloured against a weekly range**
  (default 10 to 18 sets for main groups; green inside, yellow within a band,
  red outside; an arrow says add or cut) and a **frequency target** (default
  2 days per week, coloured). Tap any number to see which exercises produced it
  (synergist exercises greyed). Ranges are editable per group and come with
  low / moderate / high presets (roughly 4-10, 10-18, 18-30 weekly sets, with
  frequency derived from a cap of about 11 sets per session per group).
- **Muscle map:** a front/back body drawing, shaded by relative load, available
  for a **program day**, the **whole program week**, and **today's workout**
  ("Day Muscles"), with separate strength and hypertrophy tabs and a
  percentage list ("muscles used, relative to each other"). There is also a
  per-exercise view showing which exercise drives which group.
- **Graphs screen:** user-picked line charts, drag to reorder: per exercise (max
  weight with an e1RM line, or volume), per muscle group (weekly volume or
  weekly sets), total weekly volume, bodyweight, body measurements, body fat.
  Optional overlays: bodyweight line on every chart, vertical lines where the
  program changed, shared X range. Long-press a point to see the workout, notes
  and state variables of that day. Pinch to zoom.
- **e1RM:** computed from weight, reps and RPE via an RPE-to-percentage table
  (RPE assumed 10 if missing). Shown per set in the workout (a cycling column),
  on graphs, and in PRs.
- **PRs:** per exercise, "max weight" and "max e1RM" with dates and the set behind
  them; new PRs flagged in the workout summary and the week card. Rep-max
  tables and an RM calculator exist as separate screens.
- **History:** a list of past workouts with calendar month view, share image,
  CSV import/export, workout summary with totals (time, volume, sets, reps),
  sets per muscle group, and PRs.
- **Program preview and Playground:** for any program (built-in or your own) you
  can preview every week and day, see a muscle map for it, and in the
  **Playground** complete sets virtually and press "finish day" to run the
  progression and watch the numbers evolve week by week without touching real
  data. State variables and 1RM can be edited in the sandbox.

### 2.4 Program features

- **Liftoscript, a text DSL.** One exercise per line: `Name, Equipment / sets x
  reps / weight / progress: ...`. Features:
  - sets x reps, rep ranges, multiple set groups per line, AMRAP (`5+`), per-set
    RPE (`@8`), percent of 1RM, absolute weight, "ask me the weight/RPE" flags;
  - rest timer per set and **active set timers** (`60s|30s`), plus `auto` advance
    for EMOM and Tabata circuits;
  - warmups: auto-generated by default or explicit (`warmup: 1x5 45lb, 1x3 80%`)
    or `none`;
  - set labels, exercise labels (the same lift as "main" vs "accessory" with
    separate progression);
  - **supersets** by a group name, with a superset-specific default rest;
  - weeks and days (`# Week`, `## Day`), descriptions in Markdown that carry
    forward, week/day descriptions;
  - **reuse and templates:** `...Squat` copies sets and scripts, `Squat[1-4]`
    repeats across weeks, `used: none` defines an exercise that exists only as
    a template or swap alternative;
  - **set variations** (`5x3 / 6x2 / 10x1`, with an index the script advances,
    the GZCLP pattern) and **exercise variations** (`A | B | C`);
  - **built-in progressions:** linear (`lp`, with success and failure counters
    and deload on failure), double (`dp`), reps-sum (`sum`);
  - **custom progress scripts** with state variables that persist between
    workouts, temporary variables, loops, if/else, `print`, a set of predefined
    read variables (completed reps/weights/RPE per set, set times) and write
    variables (weights, reps, RPE, timers, set/exercise variation index,
    description index), built-ins (`sum`, `min`, `max`, `floor`, `round`,
    `calculate1RM`, `calculateTrainingMax`, `rpeMultiplier`, `roundWeight`,
    `increment`, `decrement`, `sets`), and **tags** so one exercise's script can
    change another's state;
  - separate **update** scripts that can run at set completion, not only at
    workout end;
  - bodyweight, weighted and assisted exercise handling.
- **Deload:** there is no dedicated deload feature. It is built from
  `progress: none` on a week, a lower-percent week, or a `lp` failure decrement.
- **Editor:** text mode, plus a structured UI with menu actions such as "Add
  state var", "Require 2 successes", "Add deload on failure", "Reuse script
  from".
- **Built-in programs** (a large catalogue of well-known templates) that are all
  just editable Liftoscript.
- **Rest timer:** pill that starts on set completion, shows elapsed over target,
  plus/minus 15 s, turns red past target; resolution order is per-set, then
  superset default, then warmup or workout default.

### 2.5 Workout logging UX (why it feels powerful yet usable)

- The **next set is expanded** with big reps/weight fields and one big check.
  Completed rows collapse; tapping a row expands it.
- The first screen shows **what to do** (target) and **what you did last time /
  your best / the same day in an earlier week**, inline, per set.
- A header column that **cycles** between Target, Previous set, Plates, e1RM:
  one cell, four useful views.
- A **plates per side** drawing, and a "why this weight was rounded" explainer.
- The keypad has +/- that step by the smallest loadable weight.
- **"+" suffix prompts:** only AMRAP, RPE or weight-ask sets interrupt you.
- An **exercise thumbnail strip** with done/total counters, supersets sharing a
  coloured underline, swipe between exercises.
- Per-exercise **notes** that resurface next time, graph and PRs under the
  sets, and a summary screen with totals, sets per muscle group and PRs.
- Swap an exercise for this workout only, edit the program exercise from inside
  the workout, add warmup or working sets with one tap.
- Pause, always-on display, a lock-screen timer, watch support, health-app sync.

### 2.6 Exercise images and diagrams

- Per exercise and equipment there is a small and a large **image** loaded from
  their servers and cached; custom exercises can reuse another exercise's image
  or take a photo. A generic placeholder icon covers gaps.
- Muscles are shown with **front/back body drawings** (vector paths per muscle
  region) shaded by load, plus a small per-muscle image in the picker.
- The exercise images come from a stock-image library (gymvisual.com is named in
  their extractor script). That is a commercial source; we do not reuse any of it.

## 3. Intake's current state

Read: `CLAUDE.md`, `docs/SPEC-v3.md`, `docs/GOALS.md`, `docs/scripting/`,
`domain/training/`, `domain/script/ProgramScript.kt`, and the train, workout and
progress screens. Not run on a device (none available).

| Area | What Intake has today |
| --- | --- |
| Exercise library | 94 built-in exercises (`ExerciseCatalog`), custom exercises, hide/show, filter by muscle and equipment. |
| Equipment | Five kinds only: barbell, dumbbell, machine, cable, bodyweight. Used as a label and as a generator filter (`EquipmentPreset`). No loading model beyond a plate calculator. |
| Muscles | One primary muscle plus a comma-joined list of secondaries, from 12 coarse names (chest, back, shoulders, quads, hamstrings, glutes, biceps, triceps, calves, core, traps, forearms). No weights, no sub-groups (no front/side/rear delt, lats vs upper back, lower back). |
| Images | None. `ExerciseEntity.videoUri` exists but there are no bundled diagrams. |
| Program model | Program, days, exercises with sets, rep range, RPE, rest, start weight, one progression rule. Weeks are a `currentWeek` counter; there are no per-week prescriptions. |
| Progression | Linear (with deload after N misses), double progression, RPE-based, percent of 1RM, custom expression (one expression returning the next weight; variables `weight, reps, sets, completedReps, rpe, failureCount, week`). Always shows a one-line reason. |
| Scripting | A labelled-line text format with diagnostics and error codes, import/export, AI-prompt helper doc. Friendly, but no state variables, no per-set control, no reuse. |
| Generator | Pure, deterministic program generator: weekly hard-set targets per muscle by experience and goal, per-session cap, focus muscles, equipment/injury filters. This is where the best muscle-volume logic already lives, but it is not shown to the user afterwards. |
| Logging | Active workout with set grid, rest ring in a foreground service, warmup suggestions, plate visual, freestyle workouts, RPE per set. No superset support, no swap-in-workout, no per-set "last time". |
| Insights | Train tab shows a rolling 7-day count of working sets per **primary** muscle against a floor of 10. Exercise history shows an Epley e1RM line and a PR number. The Progress hub advertises "volume per muscle and personal records", but there is no weekly volume per muscle chart, no frequency view, no muscle map, no week-over-week comparison, and no program-level balance view. |
| Coach | Rule engine with stall detection, volume nudges and exercise swap suggestions; deterministic. |
| Other | Jump-test performance tracking, Wear OS companion, plate calculator, program templates. |

## 4. Gap list

Difficulty: **S** under 1 day of agent work, **M** a few days, **L** a week or
more or touches migrations and many screens.

| # | Feature | Liftosaur | Intake today | Gap | Effort |
| --- | --- | --- | --- | --- | --- |
| 1 | Weighted muscle involvement per exercise | target + synergist with a fraction | primary + unweighted secondaries, 12 names | Add weighted map and finer groups | M (data) |
| 2 | Weekly sets per muscle group vs range | colour-coded, arrow, tap for contributors | rolling 7 days, primary only, floor 10 | Replace with weighted, calendar-week, range, contributors | M |
| 3 | Frequency per muscle | days/week target | none | Add | S (once #1 exists) |
| 4 | Muscle map diagram | front/back, per day/week/program | none | Original vector body map + shading | M |
| 5 | Program muscle balance (per day, per week) | yes, plus preview | generator only, hidden | Run the same logic on any program | S-M |
| 6 | Strength vs hypertrophy split | 8-rep cut | none | Optional rep-zone split | S |
| 7 | Graphs: volume, max weight, e1RM, per muscle, bodyweight overlay | user-picked, zoom, legend | one e1RM chart per lift | Several chart types, shared chart component | M |
| 8 | e1RM quality | RPE table based | Epley only (RPE table exists in `Progression`) | Use reps+RPE table, show formula | S |
| 9 | PRs | max weight, max e1RM, week PR count | one best e1RM | Rep-range PRs, PR list, flag in summary | S-M |
| 10 | Week summary card | volume, sets, PRs, deltas | none | Add to Progress and workout summary | S-M |
| 11 | Specific equipment | 11 types plus per-gym availability and loading | 5 types | Taxonomy + user's equipment list | M-L |
| 12 | Equipment loading models | plates, stack, fixed, bands | plate calculator only | Per-equipment load rule, rounding | M |
| 13 | Exercise variations per equipment | one base movement, many equipment | each combination a separate row | Movement + equipment pair, swap equipment | M-L |
| 14 | Images for every exercise and equipment | stock images | none | See section 8 | M |
| 15 | Swap exercise by similarity | similar muscles sort, weight carried over | coach can swap in a program only | Swap in workout, ranked by shared muscles | M |
| 16 | Supersets | group name, own rest | none | Group flag + logger flow | M |
| 17 | Per-week prescriptions (5/3/1 style waves) | weeks and days with reuse | one prescription per slot | Week-specific sets/percent | L |
| 18 | Set-level control | per-set weight/RPE, AMRAP, set labels, timers | uniform sets, RPE target only | Per-set rows in builder | M |
| 19 | State variables and richer scripts | full DSL | single-expression formula | Optional state and rule library | L |
| 20 | Playground (simulate weeks) | yes | no | Pure simulator over `Progression.evaluate` | M |
| 21 | Last/best/same-day inline per set | yes | no | Query + row UI | S-M |
| 22 | Plates per side, rounding explainer | yes | plate visual exists | Add "why rounded" line | S |
| 23 | Notes that resurface | yes | session notes only | Per-exercise note carry | S |
| 24 | Per-gym equipment | yes | none | Only if #11 lands; skip first pass | M |
| 25 | Unilateral and implement-count flags | yes | none | Needed for fair volume numbers | S |
| 26 | Deload as a feature | indirect | linear rule can deload | Add a one-tap "deload week" | S-M |
| 27 | Built-in program catalogue | large | a few templates plus generator | More templates | S each |
| 28 | Health/watch/social share | yes | watch exists, no share | Skip | n/a |

## 5. Recommendations

Intake's constraints shape all of these: offline, no AI for anything that
matters, soft deletes, units stored in kg, Room v3 via `MIGRATION_2_3` (never edit
v2), domain logic in `domain/` with unit tests, new routes inside the Train or
Progress tab graph. Every schema change needs a `MigrationSchemaTest` case and
backup and sync coverage (`docs/BACKUP.md`, `server/`).

### 5.1 Must-have for "deep insight"

**M1. Weighted muscle involvement (the foundation).**
- *Data:* new table `exercise_muscle(exerciseId, muscleGroup, weight, deletedAt,
  createdAt, updatedAt)` added by a `migrateTrainingMuscles_2_3` function.
  Keep `ExerciseEntity.primaryMuscle/secondaryMuscles` for compatibility and
  backfill the new table from them on migration (primary = 1.0, secondaries =
  0.5), then refine the seed catalogue. Custom exercises get the same editor.
- *Domain:* `MuscleTaxonomy` (groups, display order, parents) and
  `MuscleCredit.forSet(exercise, set)` returning group-to-fraction, in
  `domain/training/muscle/`. Pure, with tests per convention in section 7.
- *UI:* the exercise detail sheet shows a muscle map (M3) and "Primary / Also
  works" chips. The custom-exercise form gets a three-level picker
  (Main / Helps / Barely) instead of two free lists.
- *Simple for a regular person:* default to "Main muscle" plus an "Also works"
  list; the weights and the fine groups sit behind "Fine-tune".

**M2. Weekly volume per muscle vs a range, with contributors.**
- *Data:* none beyond M1 (derived from `SetLogEntity`; skip warmups, count
  working sets only). Targets live in settings (add keys only to
  `SettingsRepository`). Defaults from the generator's existing per-experience
  ranges so the app has one source of truth.
- *Domain:* `WeeklyVolume.compute(sets, exercises, weekStart, rules)` returning
  per group: effective sets, days trained, contributing exercises, status
  (below / in range / above). Calendar week, user's week start day.
- *UI:* Progress tab card "Training balance": a bar list with the range drawn as
  a band, tap a row for contributors, a small arrow for add or cut. Replace the
  Train-tab 7-day floor list with the same component. Week picker to go back.
- *Simple:* show five or six bars by default (chest, back, shoulders, arms, legs,
  core), expand to all groups; plain-language status ("a bit low"), not codes.

**M3. Muscle map diagrams (original vector art).**
- *Data:* none. Region ids map one-to-one to groups in the taxonomy.
- *Domain:* `MuscleHeat.normalise(credit)` returns 0..1 intensity per region.
- *UI:* one reusable `MuscleMap(intensity, side)` Compose composable drawn from
  hand-authored paths (front and back). Used in the exercise sheet, the program
  day preview, the workout summary and the weekly card. Two colours plus a
  legend; labels always shown as text too (accessibility).
- *Simple:* the picture is optional decoration; every number also appears as text.

**M4. Program preview with balance (per day and per week).**
- *Domain:* reuse M2's maths on a program's prescribed sets instead of logged
  sets (`ProgramBalance.of(program)`), including the generator's `weeklySets`.
- *UI:* in the program builder and the template preview: a "Muscle balance"
  header with the weekly bars and, per day, a tiny map. Warnings when a group is
  outside range or a group is trained once a week.
- *Simple:* collapsed to one summary line ("Balanced" or "Light on rear delts")
  until tapped.

**M5. Graphs for strength and volume (shared chart component).**
- *Data:* none. *Domain:* `ExerciseSeries.of(sets)` for best set, e1RM, session
  volume, and `MuscleSeries` for weekly sets per group. e1RM should use the
  reps+RPE table already present in `Progression` and fall back to Epley, with
  the formula shown (existing rule).
- *UI:* exercise detail screen with a segmented control (Weight / e1RM / Volume)
  over `TrendLineChart`, plus a muscle weekly-sets chart. Long-press a point
  for the session. A bodyweight overlay toggle.
- *Simple:* one default chart per lift; the others are a tap away.

**M6. PR tracking and weekly summary.**
- *Data:* optional cached `pr_event` table later; start by computing on the fly.
- *Domain:* `PersonalRecords.detect(history, newSession)` for best weight at
  rep counts (1, 3, 5, 8, 10), best e1RM, and best session volume; "first time
  ever" is not a PR. Tests for ties and warmups.
- *UI:* a PR chip in the workout summary, a PR list per exercise, a week card on
  Progress with sets, volume, PRs and change against last week.
- *Simple:* celebrate quietly; the list is under the exercise, not a new tab.

**M7. More specific equipment and exercise selection.**
- *Data:* `ExerciseEntity.equipment` stays a string but takes the new taxonomy
  ids (section 6.1); `migrateTrainingEquipment_2_3` maps the five old values to
  the closest new id (machine becomes "selectorised machine" unless the
  exercise name says plate-loaded). A new setting stores the user's available
  equipment as a set of ids, replacing the five-preset enum (`EquipmentPreset`
  becomes a convenience that just fills the set).
- *Domain:* `EquipmentTaxonomy` with `family`, `loadModel` (plates, stack, fixed,
  bodyweight, band, none) and `parents` so "has a barbell and rack" implies a
  bar. The generator already filters by equipment; make it filter by this set.
- *UI:* an onboarding step and a More > Equipment screen: tick-boxes grouped by
  family, "Full gym / Home dumbbells / Bodyweight" presets on top. Exercise
  picker gets an equipment chip row and an "only what I have" switch.
- *Simple:* the preset buttons stay the default; the full list is "Customise".

**M8. Diagram for every exercise and every piece of equipment.** See section 8.
The guarantee comes from M3 (a generated muscle map for every exercise, no art
needed) plus an equipment pictogram per taxonomy id; photos are an upgrade.

### 5.2 Nice-to-have

**N1. Swap in workout, ranked by similarity.** Domain `ExerciseSimilarity.score`
= cosine similarity of muscle credit vectors, plus same-pattern and equipment
availability bonuses. UI: "Swap" in the exercise card menu, list of 5 with a
reason ("same muscles, no barbell needed"). Weight carry-over uses history at
nearest reps, else the lift ratio table below. Deterministic, no AI.

**N2. Inline "last time / best" per set.** Query previous session's set at the
same index. One muted line under the target. Hide behind a setting for new
users.

**N3. Supersets.** Add a nullable `supersetGroup` on `program_exercise` (v3
migration), and in the logger rotate between group members with a shared short
rest. UI: a "Link with next exercise" button in the builder.

**N4. Playground (simulate the program).** A pure `ProgressionSimulator` that
feeds "all sets hit" or "miss last set" into `Progression.evaluate` for N weeks
and shows the resulting weights in a table. Cheap because progression is already
pure. UI: a "Preview 8 weeks" sheet in the builder.

**N5. Per-week prescriptions.** `program_week_override(programExerciseId, week,
sets, repsMin, repsMax, percent)` to support waves and deloads. Large UI cost;
do after M1 to M7. Include a "make week N a deload" one-tap action first
(sets x 0.6, weight x 0.9) because it covers most real use.

**N6. Unilateral and implement-count flags** so volume of dumbbell and
single-arm work is comparable. Two booleans on `ExerciseEntity` with seeds.

**N7. Sets split by rep zone** (strength under 6, hypertrophy 6 to 15, endurance
above) as a secondary stat in M2, not the headline.

**N8. Script upgrades.** Add optional `warmup:`, `superset:`, per-week lines
(`week 2: 3x5 @7`) and a few named state counters to the Intake script; keep the
labelled-line style and error codes. Do **not** copy Liftoscript's syntax;
Intake's friendly style is a feature.

**N9. Exercise notes that resurface** next session; one nullable column on the
set/exercise log or a small `exercise_note` table.

**N10. Rest-timer polish:** plus/minus 15 s buttons on the ring.

### 5.3 Skip (for now)

- A full scripting language with loops, tags and update-at-set scripts. It raises
  the barrier for a regular person and Intake's custom expression plus progression
  rules cover the 95% case.
- Per-gym equipment lists and multi-gym switching.
- A 39-muscle anatomical layer in the UI. Use about 20 groups; keep finer
  regions only as drawing regions if needed.
- Social sharing, CSV import of another app's history, health-app write-back.
- AI autofill of muscles. It violates the "no AI for lookups" rule; use the
  seeded taxonomy and a clone-from-exercise button instead.
- A big built-in program catalogue as a first step; the generator plus a handful
  of templates is enough until the insight layer exists.

## 6. Original taxonomy proposals

Both were written from general knowledge of gym equipment and functional
anatomy. They are proposals; names are plain-language.

### 6.1 Equipment taxonomy

Each item has a **load model**: P = plates on a bar, S = weight stack,
F = fixed weights, B = bands (resistance levels), BW = bodyweight, N = no
load number. Items with a parent require the parent (for example an incline bench
is only useful with a bench; the user usually owns a rack and bench together).

| Family | Item | Load | Notes |
| --- | --- | --- | --- |
| Free-weight bars | Barbell (standard 20 kg / 45 lb) | P | needs plates |
| | EZ-curl bar | P | |
| | Trap / hex bar | P | |
| | Safety squat bar, cambered bar | P | rare; optional |
| | Landmine attachment | P | uses a barbell |
| Free-weight handheld | Dumbbells (fixed) | F | |
| | Adjustable dumbbells | F | step list per user |
| | Kettlebell | F | |
| | Weight plate (held) | F | |
| | Medicine / slam ball | F | |
| | Sandbag | F | |
| Racks and benches | Power rack / squat stand | N | parent of rack lifts |
| | Flat bench | N | |
| | Adjustable (incline/decline) bench | N | |
| | Pull-up bar | BW | |
| | Dip bars / parallel bars | BW | |
| | Gymnastic rings / suspension trainer | BW | |
| | Plyo box / step | N | |
| | Preacher curl bench | N | |
| | Back-extension / Roman chair | BW | |
| | Glute-ham developer | BW | |
| Cable station | Cable, high pulley | S | |
| | Cable, low pulley | S | |
| | Cable, mid / dual adjustable | S | |
| | Attachments: straight bar, rope, V-bar / close-grip handle, lat bar (wide), single handle, ankle strap | N | attachment is a property of the exercise variant, not a separate load |
| Selectorised machines | Chest press machine | S | each machine is one item |
| | Pec deck / fly machine | S | |
| | Shoulder press machine | S | |
| | Lat pulldown machine | S | |
| | Seated row machine | S | |
| | Leg press (selectorised) | S | |
| | Leg extension | S | |
| | Leg curl (seated / lying) | S | |
| | Hip abductor / adductor | S | |
| | Calf machine (seated / standing) | S | |
| | Assisted pull-up / dip machine | S | negative-load logging |
| Plate-loaded machines | Leg press (plate) | P | |
| | Hack squat | P | |
| | Pendulum / belt squat | P | |
| | Chest / shoulder / row (plate-loaded) | P | |
| | Hip thrust machine | P | |
| Guided | Smith machine | P | |
| Bands and tubing | Resistance band, loop | B | colour/level list |
| | Resistance tube with handles | B | |
| | Mini band | B | |
| Other | Sled / prowler | P | |
| | Ab wheel | BW | |
| | Stability ball | BW | |
| | Battle ropes | N | |
| | Foam roller | N | for mobility entries |
| Cardio | Treadmill, bike, rower, ski erg, elliptical, stair climber | N | out of scope for strength logic, but needed for diagrams |
| None | Bodyweight only | BW | |

Design rules:
- An **exercise** is `(movement, equipment item, optional variant)`. "Bench press"
  with barbell, dumbbell, smith machine or cable are four variants of one
  movement. The movement holds the *default* muscle map; a variant may override
  weights (a smith press shifts a little load off the stabilisers).
- The **equipment preset** buttons stay (Full gym, Home dumbbells, Bodyweight,
  Bands and a bar...), each just a pre-ticked set.
- The load model drives rounding and the plate drawing; "N" items never ask for
  a weight.

### 6.2 Muscle-group taxonomy and weighting convention

About 20 trackable groups, grouped under 6 body regions so the default UI can
show six bars. (For comparison, the current app has 12 coarse names.)

| Region | Group | Typical contents |
| --- | --- | --- |
| Chest | Chest (upper) | clavicular pec |
| | Chest (mid/lower) | sternal pec |
| Back | Lats | latissimus, teres major |
| | Mid back | rhomboids, mid and lower traps |
| | Upper traps | upper trapezius, levator |
| | Lower back | spinal erectors |
| Shoulders | Front delts | |
| | Side delts | |
| | Rear delts | rear delt, infraspinatus, teres minor (rotator cuff as helper) |
| Arms | Biceps | biceps, brachialis |
| | Triceps | long, lateral, medial heads |
| | Forearms | flexors, extensors, brachioradialis, grip |
| Legs | Quads | |
| | Hamstrings | |
| | Glutes | max; medius shown as hip abductors |
| | Hip abductors | gluteus medius/minimus, TFL |
| | Hip adductors | |
| | Calves | gastrocnemius, soleus |
| Core | Abs | rectus |
| | Obliques | |

The generator keeps working at the region level (its 10 programmed muscles map
onto regions) so existing programs and tests keep their meaning.

**Weighting convention.** Each exercise lists groups with a weight from a fixed
three-step scale. Fixed steps keep data entry easy and diffs reviewable.

| Weight | Name | Meaning | Examples |
| --- | --- | --- | --- |
| 1.0 | Main | The muscle the movement is chosen for, or that limits the set | quads in a squat; chest in a bench press; lats in a pulldown |
| 0.5 | Strong help | Works hard through a big range of the lift | triceps and front delts in a bench press; glutes in a squat; biceps in a row |
| 0.25 | Light help | Stabiliser or short-range contributor | core in a squat; forearms in a row; rear delts in a bench |

Rules for authors (and for the custom-exercise form):
1. Every exercise has at least one 1.0 group, and at most three.
2. Two-joint isolation (curl, pushdown) has one 1.0 and at most one 0.25.
3. Compound lifts total no more than about 3.0 across all groups, so one
   set cannot count as five sets of anything.
4. A group appears once; if two regions in a group apply, use the larger.
5. Only **working sets** count. Warmups never count. A set counts if it was
   taken to about 4 reps from failure or closer (RPE 6 or higher when logged;
   when RPE is missing, assume it qualifies), so very light sets do not inflate
   volume.
6. Volume credit for a set = weight above x 1 set. Bodyweight, unilateral
   (both sides summed as one set) and two-implement exercises count as **one
   set**, never doubled; only tonnage-style "volume" charts apply the implement
   multiplier.
7. Totals are shown rounded to half a set; the UI says "about".
8. A user override per exercise replaces the seeded weights and is kept through
   backup and sync.

Default weekly ranges (editable, consistent with `docs/GOALS.md` generator
numbers rather than invented here): from the generator, roughly 8-10 hard sets per
main group for newer lifters, 10-14 intermediate, 12-20 advanced; smaller groups
(forearms, obliques, hip abductors) default to "no target" and just display.
Frequency default: twice a week for main groups, as in the generator.

Lift-ratio table for swap weights (N1): store approximate working-weight ratios
between movement pairs (for example dumbbell press about 0.35 of a barbell press
per hand) authored from experience, not scraped; only used when no history exists.

## 7. Where Intake should be simpler than Liftosaur

- Default to six body regions and one number per region. Expand on tap.
- Text everywhere a picture is shown (accessibility, and in case drawings fail).
- Plain status words ("a bit low", "about right", "a lot"), the numbers one tap
  away.
- No required setup: every insight works from the first week using defaults;
  settings only refine.
- No scripting needed to get the benefit of any of the above.

## 8. Diagram strategy (every exercise and every piece of equipment)

Goal: **100% coverage with zero licence risk**, with photos as an optional layer.

### 8.1 Layered plan

1. **Generated muscle map for every exercise (original art, 100% coverage).**
   One front and one back body outline drawn by us as Compose `Path`s (or
   vector drawables) with one region per muscle group in section 6.2. Shade by
   `MuscleHeat`. Works for custom exercises automatically. This is the "diagram
   for every exercise" guarantee.
2. **Equipment pictogram for every taxonomy item (original art).** About 45
   simple line icons in one consistent style (24 dp grid, 2 px stroke), authored
   as `ImageVector`s in `ui/components/icons/equipment/`. One per row in
   section 6.1. Cable attachments get small badges or a single cable icon plus
   a text label.
3. **Optional photo pair per exercise: Free Exercise DB.** Two frames
   (start and end position) for about 876 exercises. Matched to Intake's
   catalogue by name through a reviewed mapping table in `tools/`.
   Unmapped and custom exercises fall back to layer 1 plus the equipment
   icon.
4. **Optional per-exercise line art** drawn by us for the 20 to 30 most-used
   lifts (squat, bench, deadlift, row, press and so on), if the photos look
   dated. Same style as the equipment icons.

### 8.2 Concrete sources and licences

| Source | What | Licence | Status | Use |
| --- | --- | --- | --- | --- |
| Free Exercise DB (github.com/yuhonas/free-exercise-db) | 876 exercises as JSON (name, level, mechanic, equipment, primary/secondary muscles, instructions) and 2 JPG frames each | **Unlicense** (public domain dedication). The repo's `LICENSE.md` contains the Unlicense text and the README badge says the same. | **Verified 2026-11-09** against the raw `LICENSE.md` and README. | Photos, plus the muscle and equipment fields as a cross-check. See caveat below. |
| Original Compose vector icons (ours) | Equipment pictograms, muscle map, key-lift line art | Ours (same as the app) | n/a | Primary. |
| Tabler Icons | 5,000+ outline icons, includes some gym items | **MIT** | `LICENSE` read on 2026-11-09 | Optional generic icons (dumbbell-like, timer). Keep the copyright notice in the app's licences screen. Check each needed icon exists. |
| Lucide | outline icons | **ISC** | `LICENSE` read | Same use as Tabler, as an alternative. |
| Phosphor Icons | icon set with weights | **MIT** | `LICENSE` read | Same. |
| Material Icons / Symbols (Google) | `fitness_center` and similar | **Apache-2.0** | `LICENSE` read | Already available in Compose; keep as UI icons. |
| game-icons.net | many gym-like icons (barbell, kettlebell, dumbbell...) | **CC BY 3.0** for most, with a few per-author CC0; the repo's `license.txt` says "Creative Commons 3.0 BY or CC0 if mentioned" | Header read 2026-11-09 | Allowed only with visible attribution per author and a link back. Prefer our own icons; use only if we accept an attribution screen. |
| Wikimedia Commons muscle/anatomy drawings | body diagrams | **Varies per file** (CC0, CC BY, CC BY-SA, public domain) | **Not checked** | Only if a specific file is checked individually; BY-SA is a problem for an app bundle. Not needed under this plan. |

Not to use: Liftosaur images and muscle drawings (AGPL repo, commercial stock
source), gymvisual.com, any "free" exercise image site without an explicit licence
file, and photos scraped from fitness-site CDNs.

**Caveats about Free Exercise DB.**
- Its `LICENSE.md` is the Unlicense, but it is a dataset compiled from earlier
  community lists (the README says it was restructured from wrkout/exercises.json).
  The photos' original provenance was not independently traced. Unlicense is a
  dedication by the maintainer, so the practical risk is the maintainer not being
  the true rights holder of every image. Treat this as a small, accepted risk and
  record the commit hash we take images from. If that risk is not acceptable,
  keep layers 1, 2 and 4 only; the plan still covers everything.
- The README points to a paid dataset for commercial use (2,500+ exercises with
  muscle-highlighted images). That one is not Unlicense. Do not mix it in.
- Muscle names there are coarse (17 primary names); do not copy its muscle
  assignments blindly. Use it only to flag disagreements with our own table.

### 8.3 Packaging

- Intake is offline-first and the APK size matters. Photos must be **bundled
  or downloaded on explicit user action**, never fetched silently. Suggestion:
  a build-time script `tools/build_exercise_images.py` (like
  `tools/build_food_db.py`) that selects only mapped exercises, resizes to
  about 480 px, encodes WebP and writes `app/src/main/assets/exercise_img_v1/`
  plus an index. Measure the size before committing; if it is over a few MB,
  make it an optional "Download exercise photos" pack in Settings.
- `*.db` files are committed only for `food_ref_v1.db` (`CLAUDE.md` rule 6);
  keep the image pack as plain files, not a database.
- Add a "Licences and credits" row in More > Help listing Free Exercise DB
  (Unlicense, courtesy credit even though not required) and any MIT/ISC
  notices we actually ship.
- Custom exercises can take a user photo (stored locally, never synced unless the
  user's sync setting already covers files; check `docs/PRIVACY.md`).
- Fallback order at render time: user photo, bundled photo, generated muscle map
  with equipment icon. The UI never shows an empty box.

## 9. Suggested order of work

1. M1 weighted muscles and taxonomy (migration, seed, tests).
2. M2 weekly volume with range, then M4 program balance, using the same code.
3. M3 muscle map (original art) and M7 equipment taxonomy and picker.
4. M5 graphs and M6 PRs and week card.
5. M8 images (equipment icons first, photos last).
6. Then N1 swap, N2 inline last-time, N6 flags, N4 playground, N3 supersets, N5 weeks.

All of it is domain-first and unit-testable without a device. None of the UI has
been seen on a screen; each step needs a device check before it is called done.

## 10. Open questions for the user

- Is bundling about N MB of exercise photos acceptable, or should photos be an
  optional download?
- Are 20 groups too many for the default view? (Plan: show six regions first.)
- Should weekly volume count by calendar week or the rolling 7 days the Train tab
  uses today?
- Is attribution to Free Exercise DB, and the accepted provenance risk above,
  fine?
