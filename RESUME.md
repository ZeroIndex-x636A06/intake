# Where this build is up to

Read this first when picking the work back up. Branch:
`claude/app-build-k2ag1d`.

## Done

All of SPEC.md's built phases and every amendment feature are implemented:

- Core: Today, food logging with the allergen engine, barcode, adaptive TDEE,
  Progress, training with progression rules, settings.
- Hydration, explosive performance metrics, NFC meal tags, weekly PDF report.
- OpenRouter layer: photo estimate, Recipe Hunter, Plan B, menu import, daily
  insight, model picker and in-app benchmark.
- Health Connect with BMR-netted workout calories.
- JSON/CSV export, encrypted nightly backups over WebDAV, SFTP and SMB.
- Sync server (`server/intake_sync.py`), sync client, device pairing UI.
- Wear OS companion (`wear/`, `wearprotocol/`).
- Adaptive landscape and tablet layout.
- Privacy screen and `docs/PRIVACY.md`.
- Intake script (`docs/scripting/`, `domain/script/`): hand-written meal plans and
  programs. Plan tab: Import plan (`.md`/`.txt`/`.json`, detected by content) and
  Export plan as script. Train tab: Import program; program builder: Export program
  as script. Errors open a diagnostics sheet with "How to fix"; warnings and allergen
  hits show in the preview. Program import is one Room transaction with ids from
  `seedId` (program name + day index + exercise name), so re-import updates in place
  and missing rows are soft-deleted. `ExerciseDoc.weight` is
  `program_exercise.startWeightKg` (in schema v2 and `MIGRATION_1_2`), used as the
  first prescription when a lift has no history. The wiki is bundled into assets by
  the `copyScriptDocs` task in `app/build.gradle.kts` and shown by
  `ScriptReferenceScreen` (`domain/docs/Markdown.kt` parses it).

- Hydration v2 (SPEC-v3 "Hydration v2", `docs/HYDRATION.md`): every water-log row has a drink type, a frozen
  hydration factor, caffeine mg and pure-alcohol ml (`migrateHydration_2_3`, schema v3 only; v2 rows and v2
  backup or sync rows become plain water). The pure rules are in `domain/hydration/` (`Drinks`, `CaffeineAlcohol`,
  `WeatherAdjustment`, `SweatLoss`, `HydrationPlan.compose`; `HydrationTarget.compute` is unchanged). Opt-in
  Open-Meteo weather: `data/weather/WeatherRepository` fetches when Today opens if the cached forecast is not from
  today (no WorkManager job), location from the coarse permission rounded to 0.1 degree, or a typed city or ZIP
  geocoded once. Settings live in their own DataStore (`HydrationPrefs`, "intake_hydration"). UI: the Today card has
  drink chips, the sheet has the target breakdown, an add-a-drink picker and an Advanced section (drink values,
  measured sweat rate); Settings has a "Water" section for the weather switch. Seeded Tea and Coffee presets are
  added once to existing installs. Alcohol rows are not written to Health Connect.

- Navigation and IA rebuild (SPEC-v3 "Information architecture"):
  - `ui/navigation/FloatingPillBar.kt` is the floating pill (vertical on Medium+
    widths). Touch is one `awaitEachGesture` on the whole pill; the logic is pure in
    `PillGeometry.kt` (`PillHitTest`, `PillSlideTracker`). The pill is the Scaffold
    `bottomBar`, so its height reaches every screen through `contentPadding`.
  - `IntakeNavHost` has one nested graph per tab (`TabGraph`), so each tab has its own
    back stack. Screens several tabs open (script reference, AI settings, settings,
    backup, performance) are registered in each graph that links to them. Add new
    routes inside the owning tab's graph, above its marker comment.
  - `TabPolicy` decides switch / pop to root / nothing. Fade-through between tabs,
    shared-axis X inside a tab, via `ui/motion/Motion.kt` (tokens, `LocalReduceMotion`,
    which follows `ANIMATOR_DURATION_SCALE == 0`). Predictive back is enabled in the manifest.
  - Hubs live in `ui/screens/hub/` (`HubScreen` + `HubItem`): Food, Recipes, Eating out,
    Allergens, Train. More and Help are in `ui/screens/more/`. `PlanScreen` is now the
    "Meal plans" route; `TrainScreen` is the "Programs" route.
  - Script docs are split by `ScriptTopic`: a food or training entry shows only its pages,
    with an "All script docs" link.
  - Placeholders awaiting other work: Food > Recipes "Import from a link", Food >
    Allergens "My allergens", More "Body type, phase and baseline", Help "Replay the tour".

- Training rework (SPEC-v3): no program on a new install; templates are in code
  (`data/seed/ProgramTemplates.kt`) and copied into editable rows; exercise
  library (`domain/training/generator/ExerciseCatalog.kt`, topped up on every
  open, hidden exercises stay hidden); exercise library screen with history and
  estimated max; manual builder with plain-language progression; freestyle
  workouts (pick exercises as you go, optional "Save as a program day"); the
  deterministic generator `generateProgram(GeneratorInputs): GeneratedProgram`
  in `domain/training/generator/` (call it from baseline/onboarding, then save
  with `TrainingRepository.createProgramFromBlueprint`). No schema change.
  Existing installs keep every program and session: nothing is deleted, the
  old "Upper / lower, four days" program is now just an ordinary program (it can
  be archived, which is a soft delete), and the library gains the new exercises.
  The workout card on Today is now built (see Profile & goals below).

- Profile, baseline week and goal builder (SPEC-v3 "Engines"): More → Profile & goals
  (`ui/screens/profile/`), Your plan, the baseline card and Today's workout card
  (`ui/screens/today/components/TodayCards.kt`). Pure engine in `domain/goals/` (readiness rule,
  Mifflin-St Jeor / Katch-McArdle, measured burn blend, phase offsets and safety rails, macros,
  carb cycling, refeeds, diet breaks, water via `HydrationTarget`). Data glue in
  `data/profile/ProfileRepository.kt`; `work/BaselineWorker.kt` checks daily and sends one
  "Your plan is ready to build" notification. **No schema change**: the profile, baseline marker,
  applied-plan history and week pattern are JSON values in DataStore (so, like other settings,
  they are not in backup or sync); applied targets are normal `nutrition_target` rows.
  `TargetRepository` applies the Advanced week pattern per weekday only while the stored target
  still equals the one it was built from. `ProgramGeneratorViewModel` now starts from the profile's
  training answers. Formulas and sources: `docs/GOALS.md`.

- **Meal plans of any length, recipes by hand and from a link** (v3, schema 3). `meal_plan` holds a
  name, notes, `startDate` (epoch day), `loopMode` (NONE, REPEAT, REPEAT_N + `loopCount`), `isActive`
  (exactly one) and the plan-wide "Every week" JSON (`metaJson`). `plan_week.planId` ties a week to its
  plan; weeks are 1..N, up to 104. The week is computed (`domain/plan/PlanSchedule.kt`); "make this the
  current week" moves the start date, nothing else is stored. `MIGRATION_2_3` (`migrateMealPlans_2_3`) turns
  existing weeks into one active "My plan" (id `plan-default`, starts today) and moves the week-0 JSON onto
  it, retiring week 0 softly; `LegacyPlanRepair` does the same for a v2 backup or v2 sync peer.
  The script header gained `starts:` and `repeat:` (diagnostics `bad-start-date`, `bad-repeat`); importing
  a file whose name matches a plan updates that plan. Food > Meal plans can create, switch and archive
  plans and add, duplicate, move, remove and edit weeks. Recipes: `RecipeEditorScreen` (picked foods or
  free text, live macros, incomplete list, normal-rules allergen scan), `RecipeImportScreen` (JSON-LD, then
  microdata, then an optional "Try with AI"; `domain/recipe/`, `data/recipe/`). Ingredient pickers use
  `FoodRepository.search`. Not built: a per-day meal choice (the model is one main dish per week).

- Onboarding and Today tour (branch `agent/onboarding-tour`). `Route.Onboarding` is the start destination
  on a first run (`ui/onboarding/OnboardingScreen`, nine steps from `domain/onboarding/OnboardingFlow`).
  Step, units-seeded, start choice, pending destination, tour-due and per-tab tips-seen live in their own
  DataStore file `intake_onboarding` (`data/onboarding/OnboardingRepository`), so they are device-local and
  outside `intake_settings` and any backup. Answers are saved through the existing `ProfileRepository` /
  `SettingsRepository` / `AllergenRepository` as they are given. Auto-skip: any food log row, weigh-in row,
  workout session row or profile basics, unless the flow was already started or is being replayed.
  Today tour: `Modifier.tourTarget(id)`, `TourState`, `TourOverlay` (`ui/onboarding/Tour.kt`), pure plan in
  `domain/onboarding/TourPlan.kt`; targets ring, add food, water, baseline/workout card, "One thing to do today" card (`coach`) and the pill; steps with no target are skipped. First-visit tips:
  `TabTip` in the Food, Train, Progress and More hubs. Help has Replay the tour, Show the welcome again and
  Show screen tips again. New DAO methods `hasAnyRow` / `hasAnySession`; no schema change.
- **Coach engine (agent/coach-engine).** `domain/coach/`: `CoachEngine.review` (weight trend vs pace, data gate,
  diet break, recovery, strength stall, protein, volume, water; all thresholds in `CoachConfig`), `DailyFocusPicker`,
  `CoachAdjustments` (deload and easier-day applied to prescriptions in `TrainingRepository.prescriptionsFor`),
  `CoachSchedule`, `SuggestionHistory`, `ProteinIdeas`, `CoachReword`. `data/coach/CoachRepository` builds the
  inputs from the tables and runs Apply; `CoachReworder` is the optional AI wording (reuses the daily-insight model
  choice). `work/CoachWorker` is a daily job plus one notification. Screen: `ui/screens/coach/` (route
  `CoachCheckIn`, linked from the Today card, More and Progress). Schema: new `coach_suggestion` table in
  `migrateCoach_2_3` (backup, export and sync). Settings: `coach_*` DataStore keys. Rules and thresholds:
  `docs/COACH.md`. Settings in backup/export: `domain/backup/PortableSettings.kt`, `data/settings/PortableStore.kt`,
  `BackupService` (`settings` section, preview line); see `docs/BACKUP.md`.

- **Motion and clarity polish (this pass).** `domain/motion/MotionRules.kt` (reduce-motion rule, stagger schedule, once-a-day
  protein rule, pulse rule, count-up text, rest fraction), `ui/motion/MotionKit.kt` and `MotionCanvas.kt` (press scale, shimmer,
  stagger, count-up, `MoreDetail`, pulse/flash, `AnimatedCheck`, `BurstOverlay`, `WaterWave`, `RestRing`, chart draw-in).
  New DataStore keys `reduce_motion` (portable) and `last_celebration_day` (device-local). `AppFlowViewModel` now takes
  `SettingsRepository` for the reduce-motion switch. `TimerState.totalSeconds`. `data/training/ProgramEdits.kt` holds the pure row
  builders behind `moveDay`, `duplicateDay` and `copySettingsToOthers`. Screens changed: Today, Food and Train hubs, Recipes, Plan,
  Active workout, Progress, Settings, Coach check-in, plus small copy changes. Full list in `docs/DESIGN-MOTION.md`.
- **AI policy audit, usage meter and cost analysis (agent branch `worktree-agent-a9ca96cc2a21aa1d3`).** Full
  write-up with the audit table, the cost formula and a priced example: `docs/AI-COSTS.md`.
  - Gateway guard (`AiGateway.authorize`, pure rule in `domain/ai/AiGuard.kt`): every call carries an
    `AiFeature` and an `AiTrigger` (`USER_ACTION` or `OPT_IN_SCHEDULE`); refused with nothing sent when the
    trigger is missing or not allowed for the feature (debug build throws), "Use AI features" is off, a
    schedule's own switch is off, there is no key, or the calendar month's spend has reached the cap. The
    gateway talks to the app through `AiEnvironment` (Hilt-bound `DefaultAiEnvironment`; a fake in tests).
    The benchmark now goes through the gateway too (`AiFeature.BENCHMARK`).
  - Settings: `ai_master_enabled` (default on), `ai_monthly_budget_usd` (default 2.00), `ai_uses_<FEATURE>`,
    `ai_menu_auto_refresh` (default off) are portable; `ai_usage_reset_at` is device-local.
  - Usage log: table `ai_usage` (`AiUsageEntity`/`AiUsageDao`), added in `migrateAiUsage_2_3` (v3, no v4). Per
    device: not in `BackupTables`, so not backed up or synced. Cost is the reported `usage.cost`, else catalog
    price x tokens (`domain/ai/cost/AiSpend`). Settings -> AI shows the master switch, budget with 80% notice
    and 100% block, the per-feature meter (today/week/month) with a soft "Reset counters", the estimated
    cost screen (editable uses a day, measured tokens beat the benchmark cost beat the built-in sizes) and a
    "Scheduled AI" card (daily insight, coach reword, weekly menu refresh, all off). UI is in
    `ui/screens/ai/AiUsageCards.kt`; maths in `domain/ai/cost/`.
  - Fixed: the weekly menu job is no longer registered for everyone (opt-in, `MenuIngestionWorker.apply`);
    structured menu pages (schema.org `MenuItem`) are read with no model (`domain/ai/MenuStructured.kt`) and
    never marked clear; router entries with a negative price are never suggested or priced.
  - Entry points (photo, Recipe Hunter, Plan B, menu import, coach reword, recipe "Try with AI") hide or say
    why through `AiGateway.isConfigured()` / `unavailableReason()`.
  - `AiAuditTest` fails if a new file calls the gateway without being added to the audit table.

Docs: `docs/SPEC.md`, `docs/SPEC-amendments.md`, `docs/SPEC-v3.md`, `docs/BACKUP.md`, `docs/AI-COSTS.md`,
`docs/BENCHMARK.md`, `docs/PRIVACY.md`, `server/README.md`.

## Verified

- JVM unit tests pass.
- Backup transports (WebDAV, SFTP with host-key pinning, SMB) tested against
  real servers with `INTAKE_REMOTE_IT=1`.
- Two-device sync through the real server, with `INTAKE_SYNC_IT=1`.
- Both release APKs (`app-release.apk`, `wear-release.apk`) were built and
  signed with the same certificate.

## Not built

- The user's actual 12-week plan content. The meal plan module (SPEC section 9: week
  strip, shopping list, steps, cook mode, "can't eat what I cooked", plan-file
  import) is built and data-driven, but ships empty. Fill it by assigning saved
  recipes or importing a plan file (`docs/MEAL-PLAN.md`). `FoodSeed` is still not
  paired with a recipe seeder.
- Demo data loader in Settings (`SettingsRepository.demoDataLoaded` exists).

## Device-testing fixes (branch `worktree-agent-acbf47c924ce706e7`)

- Root cause of the Menu import, Meal templates, Health Connect, Training stats and AI settings crashes: a second `EmptyState`
  composable in `ui/components/ScreenScaffold.kt` (now `FullScreenMessage`) that a titled call inside a `LazyColumn` bound to.
  Guarded by `NoShadowedComposablesTest` and `NavGraphGuardTest`.
- Barcode scanner layout, full-height Log food sheet, OFF language filter (`domain/food/OffLanguage.kt`), product thumbnails
  (`ui/images/ProductImages.kt`, rules in `domain/food/ProductImagePolicy.kt`), week range labels (`domain/report/WeekLabel.kt`),
  pill backdrop blur (`ui/navigation/PillBackdrop.kt`).
- Schema v3 gains `food.imageUrl` (`migrateFoodUi_2_3`). New portable setting `off_other_languages`.
- NOT verified on a device: any of the UI above, the camera preview z-order, the blur, the sheet insets, and image loading
  over the network. The language rule is tested against captured responses in `src/test/resources/off`.

## NOT verified

- The motion polish was never seen running (no emulator): sweeps, count-ups, the protein burst, the water wave, stagger,
  check morphs, the rest ring, chart draw-in, shimmer, press scale, haptics, the new Settings layout and the folded hubs.
  Haptic types (`Confirm`, `ToggleOn`/`ToggleOff`) and `Modifier.onGloballyPositioned` off-screen detection are untested on hardware.
- Onboarding and the Today tour were never seen on a screen (no emulator): the step transitions, the IME
  behaviour on About you, the spotlight cut-out and bubble placement (phone and the vertical rail), the
  scroll-to-target on Today, TalkBack announcements and the notification permission prompt are unchecked.
  Pure logic is unit-tested.
- Coach: no device run. Not seen: the check-in screen and Today card layout, the weekly notification and its tap
  to open the screen, the Health Connect signals (sleep, resting HR) feeding the recovery rule, and Apply against a
  real program. HRV is not captured by the app yet. The pure rules, schedule, reword guard, settings export and the
  migration are unit-tested.
- Settings restore: the DataStore write path is not exercised by a JVM test (the encode/decode rules are).

- Hydration v2 on a device: the coarse location prompt and fix, the live Open-Meteo calls from the app (the URLs
  and response shapes were checked with curl from the build container, and the parsers are unit-tested against
  those responses), the drink chips and water sheet layout, and the Settings, Water section have never been seen
  on a screen. The logged-workout sweat estimate has not run against a real session. The planned-training-day flat
  bonus is only used when a session has started but has no time yet, because the app has no "today is a training
  day" signal to read.
- Meal plans and recipes by hand and from a link: the plan manager, week strip, recipe editor and link-import
  screens have never been seen on a screen (no emulator). The repository code that talks to Room
  (create, switch, archive, duplicate, move and remove weeks, re-import by name) is not covered by a database
  test, only its pure parts (schedule, id scoping, migration SQL, parsers) are. The link fetch has not run
  against a real site.

- No emulator or on-screen visual check of any screen (this includes the script import sheets and the reference screen, and the whole training rework: library, builder, generator wizard and preview, freestyle workout, templates).
- Profile & goals, Your plan, the Today baseline and workout cards, and the Android 13 notification
  permission prompt have never been seen on a screen or a device. The "plan is ready" notification
  (channel `baseline-week`) and the daily `BaselineWorker` schedule are untested on hardware, and
  the worker's timing under Doze is not checked. The numbers are covered by 93 JVM tests; the
  data glue (`ProfileRepository`, DataStore round trip on a device) is compiled but has no
  database-level test.
- The watch's feed for a freestyle workout: a freestyle exercise appears on the watch only once a set is logged for it.
- The floating pill: slide gesture, spring highlight, haptics, colours in both themes, the edge
  back-gesture rule, TalkBack, and the nested-graph tab back stacks (save/restore, pop to
  root, duplicated shared destinations) are compiled and unit-tested for their pure logic only.
- No real OpenRouter calls. The client, prompts and parsing are tested only
  against fakes and fixtures. That includes the new gateway guard, usage log, budget and cost model
  (fake HTTP layer); the Settings -> AI switch, budget, meter and cost cards have not been seen on a
  screen, and structured menu parsing has only been run on hand-written JSON-LD, not real restaurant pages.
  PDF menus still go to the model (no PDF text extractor without a new dependency).
- No real watch and phone pairing. The Data Layer path has not run on devices.
- No NFC hardware. Tag writing and reading are untested on a device.
- Health Connect has not run on a device.
- Vision (photo estimate) is not benchmarked. No photos with known answers are
  bundled, so the picker shows it as "not benchmarked".

## Invariants to keep

- Keys are user-supplied and kept in EncryptedSharedPreferences. They are sent
  only in headers and never logged.
- Nothing is hard-deleted. Rows get `deletedAt`, and every read filters
  `deletedAt IS NULL`. Sync relies on this.
- Timestamps are UTC epoch millis. Day boundaries come from `DayRange`/`Clock`.
  Weights are stored in kg and lengths in cm. Pounds and inches are display
  only.
- Never use `fallbackToDestructiveMigration`.
- A model can raise an allergen warning but never clear one. The local engine
  decides.
- Room schema v2 is FROZEN at 4952404 (the released APK). Never edit `2.json` or
  `MIGRATION_1_2`. All newer schema changes go into `MIGRATION_2_3` (v3, unreleased) as
  small `migrateXxx_2_3` functions. `MigrationSchemaTest` upgrades the released v2 to v3.
- A v2 backup imports into v3: missing columns take their entity defaults, and
  `LegacyRows` marks non-`seed-` `allergen_alias` rows custom, as the migration does.
- `domain/` must not import `android.*`.
- Every way to add food goes through `AllergenRepository.scan` first.
- Macro columns on `food` are per 100 g. `food_log` stores absolute amounts
  copied at log time.
- Instrumented test method names must be camelCase. D8 rejects spaces in dexed
  lambda class names below DEX 040.

## Build environment notes

- AGP 9 has built-in Kotlin support. Do not apply `org.jetbrains.kotlin.android`.
  The root `build.gradle.kts` `buildscript` block raises Kotlin and KSP, so keep
  it in step with `libs.versions.toml`.
- KSP is pinned at 2.3.12 because the 2.3.13 plugin marker is incompletely
  published.
- Charts use Compose Canvas, not Vico.
- `local.properties` holds the SDK path and signing properties. It is untracked.
  Never commit or print it.
- Release signing properties: `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`,
  `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. The wear module reads the same
  ones, so both APKs get the same key.

## Known gaps and judgment calls

- Not stored by program import: day notes and exercise notes (no columns). New
  exercises from a script get primary muscle "other".
- Plan export writes shopping rows the recipes would not generate themselves;
  generated lists and the plan-wide pantry go back as they came. Check marks are not exported.
- The mockup's "optional web assistant" is not built. It would add a network
  path beyond those in SPEC section 3.2. Amend the spec or drop it from the
  design before anyone writes it.
- Whole-word allergen matching does not catch derived words such as "creamery".
  A test pins this.
- `TrainingRepository.consecutiveFailures` looks only at the last session.
- The sync server stores rows as plaintext SQLite, by design.
- The MCP server from the amendments roadmap is not built.
