# Task board

The brief is `docs/SPEC-v3.md`. To claim a task, put your branch name next to it
and push that change before you start. When you finish, move the task to Done
with its merge commit. Read `CLAUDE.md` before you claim anything.

## In progress

**Claimed by the coordinating session**

- Debug APK build for device testing.

## Bugs fixed from device testing

Branch `worktree-agent-acbf47c924ce706e7`. None of this has been seen on a device (no emulator in the cloud container).

- **Crashes on Menu import, Meal templates, Health Connect, Training stats and Settings -> AI.** One cause: two public
  composables named `EmptyState` in `ui/components` (`States.kt` and `ScreenScaffold.kt`). A call with `title` and `body`
  bound to the `ScreenScaffold` one, which is `fillMaxSize()` plus `verticalScroll()`, and threw "measured with an infinity
  maximum height" inside a `LazyColumn` item. The whole-screen one is now `FullScreenMessage`; `NoShadowedComposablesTest`
  fails if a package declares one public composable name twice.
- Nav graph guard (`NavGraphGuardTest`): every route has a `composable<...>`, and every `navigate(Route.X)` in a tab graph targets a
  route registered in that same graph. Today and More now register the screens their cards open, so the pill stays on the tab.
- Barcode: the camera sits in a bounded, clipped 4:3 card; Back, torch and the manual entry are drawn above it; system back leaves the scanner.
- Log food sheet: full height under the status bar (status bar padding inside the sheet), results fill the height.
- Open Food Facts search asks for the phone's language then English (`langs`), picks `product_name_<lang>`, then `product_name_en`, then
  `product_name`, and hides results whose name is in another script, unless "Show products in other languages" is on.
- Product thumbnails (Open Food Facts only, https, online, with search on); generic icons for USDA and offline foods.
- Weekly check-in chips show the week as a period ("Nov 9-15", "Oct 30 - Nov 5", with the year across years).
- Floating pill: frosted-glass backdrop blur on Android 12 and up, translucent fill below.
- Schema: `food.imageUrl` in `migrateFoodUi_2_3`, with a `MigrationSchemaTest` case.

## Open: anyone can claim these

Each task links to the section of `docs/SPEC-v3.md` that defines it.

- Nothing open right now. Feedback from device testing will land here.
- Known gaps worth picking up:
  - shared-element transition from recipe card to detail;
  - watch rest ring;
  - Health Connect HRV for the coach;
  - reading PDF menu text without AI, which needs a dependency check first;
  - scroll-to-target for the tour on the two-column layout.

## Needs the user

- Test on real devices. None of the v3 UI has been seen on a screen yet. Check:
  - the floating pill and its slide gesture;
  - barcode scanning on the release build;
  - the new screens.
- A new signing key will be generated for the next release. Export a backup, uninstall, install, then import.
- Optional: deploy the sync server (`server/README.md`), add an OpenRouter key
  and run the benchmark, and set a backup destination.

## Done (v3)

- Exercise diagrams, custom exercises and workouts, and the plan creator's answers (branch `worktree-agent-adb67bf9ac0ff4da0`; see
  `docs/EXERCISE-IMAGES.md`, `docs/WORKOUTS.md`, `docs/GOALS.md`). Schema change: `migrateExercises_2_3`. Not seen on a device.
  Follow-ups from `docs/research/LIFTOSAUR-NOTES.md`: the weighted `exercise_muscle` table (seam in `MuscleMap.highlightFor`),
  the equipment taxonomy and "what I own" setting, weekly volume per muscle group.

- Motion and clarity polish and the small follow-ups (branch `worktree-agent-ac43c051116cef0e9`; see `docs/DESIGN-MOTION.md`):
  reduce-motion setting, ring sweep and count-up, protein burst, water wave, stagger, check morphs, rest ring, chart
  draw-in, shimmer, press feedback, per-screen declutter, Settings regrouped (duplicate Allergens removed, Replay the tour added),
  AMRAP label, mentioned-as-avoided notes, `ProgramEdits` tests. No schema change. Not seen on a device. Skipped: shared-element
  transition and the watch rest ring.
- AI policy audit, usage meter and cost analysis (branch `worktree-agent-a9ca96cc2a21aa1d3`; see `docs/AI-COSTS.md`).
  Every OpenRouter call site audited and gated by one guard in `AiGateway` (trigger, master switch, key, monthly
  budget); weekly menu job made opt-in; structured menu pages read without a model; usage meter, budget cap and
  estimated-cost screen in Settings -> AI. Schema change: `ai_usage` in `migrateAiUsage_2_3` (per device, not in
  backup or sync). Not seen on a device; no live OpenRouter calls.
- Onboarding and Today tour: nine-step welcome flow, auto-skip for installs with data, Today coach marks, first-visit
  tips per tab, Help replay rows (branch `agent/onboarding-tour`; see `RESUME.md`). No schema change. Not seen on a device.
- Coach engine, "One thing to do today" card, and settings/profile in backup and export (branch `agent/coach-engine`).
  Rules and thresholds in `docs/COACH.md`; backup keys in `docs/BACKUP.md`. New table `coach_suggestion` in
  `migrateCoach_2_3`. Not seen on a device.

- Hydration v2: drink types and hydration factors, caffeine and alcohol tracking and top-ups, opt-in Open-Meteo
  weather, sweat loss from logged workouts with a measured sweat rate, and a target breakdown in the water sheet
  (branch `worktree-agent-a18b02fa9b8eab30d`; see `docs/HYDRATION.md`). Schema change: `migrateHydration_2_3`.
  Not seen on a device.
- Navigation: floating pill, Food/Train/Progress/More hubs, split script docs
  (4485a76).
- Allergens v2: 15 built-in allergens, custom allergens and keywords,
  negation-aware matching, a new import preview (7eeaff5). Its schema edits now
  live in `MIGRATION_2_3` (see Schema repair).
- Schema repair: 3c7600b. v2 is restored byte-for-byte to 4952404.
  The allergen columns and `allergen_definition` moved into `migrateAllergens_2_3`,
  next to `migrateFoodOff_2_3`. Other agents add `migrateXxx_2_3` functions to
  `MIGRATION_2_3_STATEMENTS`. `MigrationSchemaTest` upgrades the released v2 to v3.
- Profile, baseline week and goal builder (4f4c902). More → Profile & goals; the baseline week with a daily WorkManager check and one
  notification; "Your plan" with a why, an (i) and an Edit on every number; Today's baseline and
  workout cards. Pure engine in `domain/goals/`, formulas in `docs/GOALS.md`. No schema change.
  The Hydration v2 task should add `WaterAdjustment` lines to `WaterPlanner`; the Coach engine can
  reuse `GoalBuilder` with `PlanOverrides` and `ProfileRepository.applyPlan`.
- Training v3: start from scratch, templates, exercise library, freestyle
  workouts, program generator (05b7466).
- Food database, barcode fixes, Open Food Facts search and contribution (ede46d6).
- Flexible meal plans, recipe editor, recipe import from a link (71e4911).
