# Task board

> **Latest:** Option A parts 2 and 3 are finished on `claude/optimistic-noether-fullk5`. See `docs/HANDOFF.md`.

The brief is `docs/SPEC-v3.md`. To claim a task, put your branch name next to it
and push that change before you start. When you finish, move the task to Done
with its merge commit. Read `CLAUDE.md` before you claim anything.

## In progress

**Round 5 Drinks, items 12 and 13** (branch `worktree-agent-adf99e3f71c45bf4e`, not pushed, not seen on a device). Tea/sugar numbers fixed (plain-brew check, sugars known, tile and label rounding, drink icon, 4.2 g teaspoon);
Drinks page simplified to one list with a size sheet. No schema change. See `RESUME.md` and `docs/HYDRATION.md`, "Round 5 corrections".

**Round 5, Nutrition (agent M): items 9, 10, 11 of `docs/FEEDBACK-R5.md`** (this worktree branch, not pushed, not seen on a device). Per-meal screen
(`NutritionMealScreen`, route `NutritionMeal`), day switcher on the Nutrition tab (future days blocked), meal template editor lists "Recent" then
"All saved" (`domain/template/TemplatePicker.kt`). No schema change. See `RESUME.md`.

**Videos tab, round 5 item 14** (branch `worktree-agent-a03c7ea740999eae8`, not pushed, not seen on a device). One filter row with a Filters sheet,
Saved as a compact row near the top, overflow menu for the key note, quota and cache clearing, infinite scroll with `nextPageToken` (search and
Recommended rows), Hide Shorts by orientation (`ShortsRule`, `part=player`), a vertical Shorts player, "More like this", and opt-in yt-dlp
downloads with direct playback and a Downloaded view. **Schema v6** (`MIGRATION_5_6`). See `docs/VIDEOS.md`, `docs/PRIVACY.md` and `RESUME.md`.

**Progress charts: weight trend accuracy and scrubbing** (FEEDBACK-R4 items 4 and 5; branch `worktree-agent-a7ca2649551f0d88b`, not pushed, not seen on a device).
Gap-aware smoothed trend (`WeightTrend.smoothByTime`), trend broken across gaps over 14 days, y-axis labels in the display unit, a clearer headline,
and press-and-drag scrubbing with tooltip and haptic tick on the Progress line charts (`domain/charts/ChartMath.kt`). No schema change. See `RESUME.md`.

**Drinks, round 4 items 7, 9, 10** (this worktree branch, not pushed, not seen on a device). A drink logged from Drinks, Today's water card
or the watch belongs to no meal (new `MealSlot.DRINKS`, shown as a "Drinks" group on Today); Drinks left the Nutrition meals card and the More
list (still one tap from Today's water card, and a "Drinks" chip in Log food); custom versions of drinks with add-ins, saved as "My drinks".
**Schema v5** (`MIGRATION_4_5`: `food.customDrink`). See `docs/HYDRATION.md`, "Drinks have no meal" and "Custom drinks".

**Round 4, Agent E: recipes in Log food, cook mode everywhere, meal templates without a plan** (branch `worktree-agent-aa92edb758b4dbe50`,
not pushed, not seen on a device). Items 6, 11 and 14 of `docs/FEEDBACK-R4.md`. Saved recipes show in Log food search, Mine and
recents (`domain/recipe/RecipeSearch.kt`, `ui/addfood/RecipeSearchViewModel.kt`); one `RecipeLogger` is the only recipe logging path.
Cook button on every recipe screen; cook mode has a servings-scaled ingredient checklist (`domain/recipe/IngredientScaling.kt`),
steps one at a time, tap-to-start timers, and "Done cooking" offers "Log a serving". Meal templates are made and edited on their own
screen (`MealTemplateEditorScreen`), "meal prep" wording is now "meal template", and Nutrition, More has a plan-free Shopping list.
The plan-dependency audit is in `docs/MEAL-PLAN.md`. No schema change.

**Recipe nutrition and shopping amounts** (branch `worktree-agent-a2198826f8ff9a2b4`, not pushed, not seen on a device). A recipe page that
lists only calories no longer shows 0 g protein, carbs and fat: they read "not listed", or an offline estimate from the ingredient lines
(`domain/recipe/IngredientEstimator.kt`) labelled as such. The meal-plan shopping list now adds up quantities by unit
(`domain/plan/ShoppingQuantities.kt`). No schema change.

Nothing. Option A parts 1 to 3 are built and merged on `claude/optimistic-noether-fullk5`, with a first emulator pass
(`RESUME.md`, first section). That branch still has to be merged into `claude/app-build-k2ag1d` and `main`.

**Rules that still hold for new screens.** Do not add settings to use screens: a control goes into
`docs/SETTINGS-INVENTORY.md`, `domain/settings/SettingsRegistry.kt` and `ui/screens/settings/SettingsControls.kt`
(`SettingsRegistryTest` fails otherwise). Register a screen only one tab opens inside that tab's graph; a screen two tabs open
goes once at the root of `IntakeNavHost`. Lists that can be long must be lazy rows, never a `forEach` inside one `item`.

**Recipe Hunter, round 5 items 4 to 8** (this worktree branch, not pushed, not seen on a device). Cuisines (22 chips including Any, Japanese, Korean...),
section headings with the note on its own line and short "Contains dairy" / "Under protein goal" chips (the full reasons in the body text), every reason
listed when a recipe fails on several counts, non-AI page readers (WP Recipe Maker, Tasty Recipes, an Ingredients list; microdata already existed),
the optional "Use AI to read pages the phone can't" per-search switch (counted in cost and cap, results labelled "Read by AI", never plain "Passed"),
and "Recent searches" (last 20, own preferences file, not in backups, soft delete, open without spending or Run again). **No schema change.**
Item 8 is design only: `docs/ROADMAP-allergen-cooking.md`. See `RESUME.md`.

## Round 4 merge on `claude/optimistic-noether-fullk5`: done, emulator-checked

Navigation transitions, Progress charts, drinks (schema v5), recipes in Log food / Cook / meal templates, Recipe Hunter substitutions and nearby menus are merged and verified (unit tests, `MigrationSchemaTest`, lint 0 errors, 4 to 5 upgrade on the emulator). Details and what still needs a phone: RESUME.md, "Round 4 merge and emulator smoke". Open: on-device check of the above.

## Round 4 items 1 to 3: nearby by travel time, menu discovery, nutrition PDF (branch `worktree-agent-a3df9485fbad45854`)

Done and unit-tested, linted (0 errors); not run on a device. (1) Nearby search rebuilt: radius in minutes (walk or drive), Overpass restaurants with no AI, per-restaurant menu discovery
(saved, chain table, website links, optional AI), streamed list with Stop. (2) "Read it with AI" on the nutrition-menu follow-up card. (3) McDonald's USA PDF: position-based table reader.
Open: Overpass live check and the chain URLs from a phone (see RESUME.md); a schema change would still be needed to store fibre, sat fat, sugar and sodium for imported items.

## Find nearby: Swap and Eating out

Branch `worktree-agent-a4d8ea88c7c1d444a`. Swap ranks nearby and local options by closeness to the planned meal (kcal and protein). Eating out has "Find healthier options nearby": imported menus first, AI search only on tap, a deterministic healthier score (`NearbyRanking`). Unit-tested and linted; nothing run on a device. Imported menus have no fibre, sat fat, sugar or sodium columns, so those are unknown for them (a schema change would be needed to store them).

## Preview launch crash and ring labels

Done on `claude/optimistic-noether-fullk5`: DB version 4 with `MIGRATION_3_4` repairing older version-3 files (the cause of the crash on launch after updating), and ring percentage labels placed clear of the ring (`RingLabelLayout`). Emulator only; the owner's phone has not confirmed either. See `RESUME.md`.

## Watch: workout link and quick remote

Branch `worktree-agent-a978a2d098487061e`. Compiled, linted and unit-tested only; nothing has run on a watch.

- The watch did nothing with workouts because the owner's phone is the preview build and the watch app had no matching
  variant. Added a `preview` build type to `wear/` (same id suffix, same debug key). A watch service now also wakes the
  app and posts a "workout in progress" notification. See `docs/WEAR.md`.
- The watch home is a quick remote when no workout runs: today at a glance, add water, add food (planned meal, recents,
  favourites, saved meals, quick add), start a workout. Requests queue offline as "pending, will sync" and are idempotent.

## Bugs fixed from device testing

- **Quick add accepted absurd values** (348416 g of protein). Per-entry limits in `domain/nutrition/QuickAddLimits.kt`, checked by the form, the save path and the watch's calorie request. Not seen on a device.
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

## Recipe Hunter returned too few results: done on branch `worktree-agent-a413caf0d3be660a6` (not pushed, not seen on a device)

Cause: a 5 recipe target, 3 rounds, and the model both searching and copying whole recipes (full ingredients and steps) inside one capped reply, so each round yielded about one recipe. Now the model only lists pages (10 a round, 2 searches); the app downloads and reads each page itself 4 at a time (`RecipeHunter.kt`), until 12 are kept or 4 rounds or $0.08 is reached. Pure rules in `domain/ai/HuntRules.kt`. "Find more" continues with exclusions. Near misses (within 10%) go to "Close matches"; pages with no nutrition are kept and labelled; unreadable pages are listed as "Could not check" and cannot be saved. Not tried with a live key.

## Next up (claimed by the coordinating session)

**AI benchmark fixes.** From the user's run on the phone (screenshot, 2026-11-09).
1. **A rate limit ends the whole run.** One HTTP 429 ("Too many requests just now") stopped every model. Treat 429 as transient: honour `Retry-After` (or use exponential backoff with jitter), pace requests (for example one at a time with a small gap), retry a few times, and only then skip that one model. A 429 is not an account-level failure; only a bad key, no credit or being offline end the run.
2. **Privacy-incompatible models are offered and then fail.** Several candidates fail with "No provider for this model meets your privacy setting". Before showing candidates, check each model's providers against the privacy setting (data collection deny / zero-retention), using OpenRouter's per-model endpoints data. Show only compatible models, or mark the others "Not available with your privacy setting" and leave them unselected, so a run never wastes a slot on them.
3. **Weak and free-tier candidates.** The default candidates include very small or free-tier models. Free tiers have tiny rate limits, which probably caused the 429. Exclude `:free` variants by default, and rank candidates by capability first (structured output support, enough context, and web-search support for Recipe Hunter and Plan B), then by price.
4. **Too much text.** Collapse the per-model "Not saved" list into one summary line, e.g. "2 saved · 4 skipped (why)", with the reasons behind a tap. Keep the cost line and the cap.
5. **Layout bugs, which also confirm the top-inset bug.** The "Run benchmark" title and header are drawn under the status bar. At the bottom, the card's last button sits behind the floating pill. Content needs top status-bar padding and bottom padding of the pill height plus the gesture inset, on every scrolling screen, not just the hubs.

## AI reliability and benchmark fixes: done on branch `worktree-agent-a9f053dada012173b` (not pushed, not seen on a device)

B1 timeouts, structured output, repair, backoff, failure logging; B2 photo units and layout; B3 camera owner; B4 PDF import;
B7 all five benchmark items (429 per model, privacy-compatible candidates, no free tier and capability ranking, compact
summary, top and bottom insets). The remaining device check is listed in `RESUME.md`.

## Open: anyone can claim these

Left over from round 3 (`docs/R3-BACKLOG.md`, status section):
- F's simpler food layout: DONE on `claude/optimistic-noether-fullk5` as Option 1 refined into `nutrition-tab-v3` (see `RESUME.md`, round 3). Still to check on a phone: hold-to-split press and haptic, the planned-meal row with a real plan, the Salt setting.
- Watch workouts in the training log: done for workouts started from the watch (they are normal phone sessions, see
  `docs/WEAR.md`). A workout the watch would record itself (heart rate, GPS) would still need an `external_session` table.
- "Find this product" goes to search only, not scan.
- On-phone PDF reading needs Android 15 or newer; older phones use AI. `pdfbox-android` not added.
- "Open AI settings" button only on Swap; not on Menu import, Recipe import, insights or Make this a workout.

Suggestions from the emulator pass and a comparison with paid apps (not started; the user picks):
- Home-screen widget (calories and water left, quick add) and launcher shortcuts (Log food, Scan, Start workout).
- Reminders as notifications: meals, water, weigh-in and the progress photo (today the photo reminder is only a line on You).
- lb plate increments in progression (the smallest step now follows the plate list; check program suggestions in lb).
- A larger exercise catalog (94 lifts today), supersets, and a muscle-volume page with a week picker.
- Micronutrients: store vitamins and minerals from USDA so "More nutrients" can total them.
- The first weekly check-in appears on day one of a new install; hold it until there is a week of data.
- The default allergen profile avoids dairy (right for this user); a public build should start with none.
- Small ones: the training log shows "240 min" where the summary shows "241"; a session left open counts up to 4 hours;
  the body map does not tell main muscles from helpers; "Core · Bodyweight · bodyweight" in the exercise list.

Each task links to the section of `docs/SPEC-v3.md` that defines it.

- Nothing open right now. Feedback from device testing will land here.
- Done in worktree `worktree-agent-acb080647531a0b9a` (FEEDBACK-R4 item 8): back navigation is now the exact mirror of push (300 ms slide, underneath dims), predictive back follows it, tab switches cross-fade; in `ui/navigation/NavTransitions.kt`. Not seen on a device.
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

- Round 3 (phone feedback), on `claude/optimistic-noether-fullk5` at `06871e3`: see `docs/R3-BACKLOG.md` (status at the top)
  and `RESUME.md` (first section). Verified by unit tests and lint; not yet seen on the owner's phone.
- Option A part 3, Train and Videos, and part 2, Nutrition (merged on `claude/optimistic-noether-fullk5`; briefs in
  `docs/briefs/`, details and what was left out in `RESUME.md`). Walked on an emulator, with the fixes listed there.
- `preview` build type (`:app:assemblePreview`): the debug build as its own app, "Intake Preview", id
  `com.zeroindex.intake.preview`, so a new build installs beside the one already on the phone.

- Recipe import improvements (branch `worktree-agent-ac631d26a40887bae`; see `docs/RECIPE-IMPORT.md`): notes, full nutrition (fibre, sugar, saturated fat, sodium in mg), photo (recipe-page image policy beside the Open Food Facts one), per-line allergen tags with the summary chip scrolling to them, prep/cook/total times, ingredient groups (a label per ingredient; recipe screen, cook mode, editor, shopping list), clean-up of WPRM artifacts and entities, and one primary action on the recipe screen with Open original, Add to this week, Edit and Delete (confirmed, soft) in a menu. Schema change: `migrateRecipes_2_3`. Tested on hand-written WPRM and Tasty fixtures. Not seen on a device.
- Health Connect sync (FEEDBACK-R2 B6): first sync reads 30 days, later syncs 14 or the gap, a per-type result line and last sync time on the Health Connect screen, failures in plain words, and a fake-client test seam (`HealthDataSource`). Not run on a device; see `RESUME.md`.
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

- Menu import, McDonald's Ireland PDF (branch `claude/optimistic-noether-fullk5`): download checks, one error card, the duplicate heading, the allergen-chart reader, the nutrition follow-up card and the merge are done and unit-tested. Open: seeing PdfRenderer's text for the tick glyphs on a phone; the "page list" error is fixed (Android regex anchor difference, see RESUME.md).

## Merge of recipe nutrition, Recipe Hunter list pages and nearby healthier options

Done on `claude/optimistic-noether-fullk5`: all three branches merged, duplicate in-screen headings removed, PRIVACY.md updated. Emulator-checked
screens are in RESUME.md. Still needs a phone with an OpenRouter key: Recipe Hunter Search and Find more, nearby search, Choose anyway, shopping totals.

## Recipe Hunter round 4 items 12, 13, 15: done on branch `worktree-agent-a7d55e0a2707dbac5` (not pushed, not seen on a device)
- 12 "Could be adapted" (substitution engine, section, Save adapted copy), 13 "Saved" chip by URL, 15 recipe images. Rules in docs/ALLERGENS.md. Unit-tested only.

Nutrition meals card without Drinks, and a location-denied note with an app-settings button on the nearby card: done on `claude/optimistic-noether-fullk5`, unit-tested, not seen on a device.

Feedback R5 item 18, square press ripple on rounded controls: done on `worktree-agent-a65d6d2a107e6b2eb` (not pushed, not seen on a device). Shared components and the Train switch fixed; screen-level ones listed in the commit message.

## Roadmap

Ideas that are designed or wanted but not started. Nothing here is claimed. A roadmap entry is not a promise to build it: each says what has to be true first.

**Allergen-aware cooking guide** (design note only: `docs/ROADMAP-allergen-cooking.md`; from phone feedback round 5, item 8). Given a dish or recipe,
suggest how to make it safely for the user's allergens. The owner: "This will need to be VERY WELL dialed in and will likely need to use AI. This is a
feature that MUST work correctly." The note sets out the goals, the non-negotiable safety rules (local deterministic scanning has the final say; AI may
only propose; every proposed ingredient is re-scanned; cross-contact warnings; never clear a warning; label checks on substitutes), the pipeline
(deterministic `Substitutions` first, then AI proposals for what it cannot place, then a local verification pass, then a confidence level or a refusal),
an evaluation plan (a fixed dish set per allergen, a 100% safety pass target before release, regression tests), the refusal states in the UI, and the open
questions. **Do not start building before the open questions are answered and the evaluation set exists.**

## Round 5 items 1 to 3: nearby chains, batched AI lookups, imports waiting for review (branch `worktree-agent-a2bb1c5265e1024f0`)

Done and unit-tested, not run on a device, no schema change. Safety: estimates and sources that state no allergen information never read as clear (`MenuAllergenEvidence`, docs/ALLERGENS.md). Missed restaurants: 26 chains added to `nearby_chains.json`, alias and name-prefix matching (DQ Grill & Chill, Popeyes Louisiana Kitchen), batched AI lookups (8 places a call, 3 calls), "Look up the rest with AI". Imports: every import is kept as "To review" with "Review import" and a confirmed "Discard import" (`StagedImports`).

## Round 5 items 15 to 17: done on branch `worktree-agent-a96ef628ee9862072` (not pushed, not seen on a device)
- 15 Body diagram: original anatomical front/back figure with 20 muscle regions, weighted per-muscle week volume, tap a muscle for its contributing exercises. Preview `design/mockup-r5/muscle-map.png`.
- 16 Equipment: 40 specific items in four groups, quick picks, requirements per exercise used by the generator, swaps and the library, legacy migration, Settings checklist, welcome-flow Equipment step with "Advanced: pick each item", wizard step. No schema change (JSON set in the profile).
- 17 Training log lists all sessions by week with PR counts; session detail shows every set, notes and Repeat; exercise detail has "Your history".
- Unit-tested only; not seen on an emulator or a phone. See `docs/WORKOUTS.md`.
