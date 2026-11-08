# Where this build is up to

Last updated 2026-11-08. Read this first when picking the work back up.

Everything below is pushed to `claude/app-build-k2ag1d`. The build is green:
`./gradlew assembleDebug assembleRelease test` all pass, 111 unit tests, zero
failures.

---

## Done

| Phase | Scope | State |
| --- | --- | --- |
| 1 | §1–§4 scaffold, Room schema, navigation, theme | **complete** |
| 2 | §5 Today screen, macro ring, manual and quick-add logging | **complete** |
| 3 | §7 allergen engine, §5.1 search, §5.2 barcode | **complete** |
| 4 | §8 adaptive TDEE, §11 Progress tab | **complete** |
| 6 | §10 progression rules, active workout, builder, history | **complete** |
| 8 | §13 settings and privacy screen (export/import still to do) | **partial** |

What that means concretely:

- 23 Room tables, soft-delete throughout, schema JSON committed, no destructive
  migration fallback.
- Today screen logs food through Recents, local+USDA search, barcode scanning,
  quick add and hand-entered foods. Every path runs the allergen engine first.
- Allergen engine blocks on a match behind a full-width red sheet naming the
  matched term and where it was found.
- TDEE measured from the user's own intake and weight trend, recalculated
  nightly by WorkManager, with confidence bands and a target-shift notification.
- Progress tab charts weight trend, measurements, adherence heatmap and
  calories against target, all on Compose Canvas.
- Training: all five progression rule types as tested pure functions, a custom
  expression parser that cannot crash, active workout with prefilled sets, rest
  timer as a foreground service, plate calculator, warmup ramp, program builder
  and per-exercise 1RM history.
- Settings covers units, theme, API keys with a test button, allergen profile,
  targets, rest timer, plates. Privacy screen written.

---

## Not done

### Phase 5 — meal plan, cook mode, shopping list (§9)

This is the next thing to pick up, and it was mid-flight.

Already written: `data/seed/FoodSeed.kt` seeds ~40 staple foods per §16's advice
and returns them keyed by name, specifically so a recipe seeder can reference
them as ingredients. It is not yet called from anywhere.

Still to write:

1. `data/seed/PlanSeed.kt` — the 12-week plan: `PlanWeekEntity` per week with
   main dish and protein type, `RecipeEntity` with instructions and
   `RecipeIngredientEntity` rows pointing at `FoodSeed`'s foods,
   `ShoppingListItemEntity` rows grouped by `StoreCategory` with pantry staples
   flagged, and `EatOutOptionEntity` rows (some with a null `planWeekId`, which
   means "applies to every week").
2. `PlanRepository` already exists with the reads this needs — weeks, week
   recipes, ingredients, shopping list, eat-out options, check/reset.
3. `ui/screens/plan/PlanViewModel` + rewrite `PlanScreen` — horizontal strip of
   weeks 1–12 with the current one highlighted and persisted
   (`SettingsRepository.currentPlanWeek` already stores it), the active week's
   dish/protein/servings/time, three collapsible sections (shopping list, step
   by step, other ways to eat it), and "Log this meal" writing the recipe's
   macros to the current slot as one `FoodLog` via
   `FoodLogRepository.logPrepared`.
4. Cook mode — `Route.CookMode(recipeId)` is already declared and the bottom bar
   already hides itself for it. Needs: full screen, one step at a time, large
   type, oversized targets, `keepScreenOn`, swipe between steps, and a tappable
   inline timer per step that has a duration in it, driven by the existing
   `TimerService` (it already does notification + vibrate + survives leaving the
   app).
5. "Can't eat what I cooked" — a persistent button on the Plan tab opening a
   sheet of `EatOutOption`s filtered to the current week's protein type, each
   logging its estimate to the current slot in one tap. Must work offline; the
   data is already local.
6. Shopping list — generated from the active week's recipes, grouped by store
   section, pantry staples collapsed by default, checkable, check state resets
   when the week changes (`PlanRepository.resetChecks` exists), manual
   additions, and a share-as-text action.

### Phase 7 — AI photo estimation (§6)

Nothing written yet beyond the `ai_correction` table and
`ApiKeyStore.anthropicKey()`, both in place.

Needs: CameraX capture or gallery pick; compress to 1024px long edge at JPEG
quality 85; an Anthropic Messages API service (Retrofit, same pattern as
`UsdaService`) with a JSON-only system prompt that includes the user's allergen
profile so the model can speak to it; **defensive parsing** that strips code
fences and tolerates prose, falling back to showing the raw text with a "log
manually" path rather than an error; an editable result list where every field
is a text field and nothing saves until confirmed; the `portionAssumption`
string shown prominently with a 0.5x–2x scale slider; the photo URI stored on
the `FoodLog`; and accuracy tracking that writes an `AiCorrectionEntity` on each
edit and surfaces a running bias note once there are 20+ corrections.

`AllergenWarningSheet` already takes an `aiReasoning` parameter and displays it
verbatim, which is what §7.4 asks for.

### Phase 8 — Health Connect, export and import (§12, §13)

Settings and the privacy screen are done. Still needed:

- Full JSON export of every table through the Storage Access Framework, and an
  import that validates and shows a diff summary before writing. Every DAO
  already has a plain `all()`/`allX()` suspend read specifically for this.
- Per-domain CSV export: food logs, workouts, body metrics.
- A settings prompt when no export has happened in 30 days
  (`SettingsRepository.lastExportAtMillis` is already stored).
- Health Connect: read steps, active calories, total calories, weight, sleep,
  heart rate; write nutrition per `FoodLog`, weight per `BodyMetric`, exercise
  sessions per `WorkoutSession`. Granular permissions, every screen rendering
  correctly with Health Connect absent, sync on resume and after writes,
  debounced, never on a timer. The `addActiveCaloriesToTarget` setting is
  already there and already defaults off.

### Demo data

A "load demo data" action in Settings, off by default, writing ~3 weeks of food
logs, weigh-ins and a few logged sessions so the app can be explored
immediately. `SettingsRepository.demoDataLoaded` already exists as the flag.
Deliberately opt-in: this is a personal dataset and seeding fake history into it
by default would be wrong.

---

## Things worth knowing before you touch anything

- **`domain/` must not import `android.*`.** That rule is what keeps 111 tests
  runnable without a device.
- **Every read filters `deletedAt IS NULL`.** Nothing is hard deleted, ever.
- **Macro columns on `food` are per 100 g** and named `*Per100g` to make that
  impossible to misread. `food_log` stores the absolute amounts for the entry,
  copied at log time, which is what makes editing a food later leave history
  alone.
- **Timestamps are UTC epoch millis.** Day boundaries come from
  `DayRange`/`Clock`, never from a stored local date. The DST tests exist
  because that is where this goes wrong.
- **The allergen engine runs on every path.** If you add a sixth way to add
  food, it goes through `AllergenRepository.scan` before anything is written.
- **Instrumented test method names must be camelCase**, not backticked names
  with spaces — D8 rejects spaces in dexed lambda class names below DEX 040.

## Known gaps and judgment calls already made

- Whole-word matching means a derived word like "creamery" is *not* caught by
  the alias "cream". There is a test pinning that behaviour, since it is the
  cost of not flagging "butternut".
- Plant-milk qualifiers and free-from claims are softened to amber rather than
  cleared, so "oat milk" does not throw a red block but is never silently
  passed either.
- `TrainingRepository.consecutiveFailures` currently returns 0 or 1 from the
  last session only. Real consecutive-failure tracking across sessions would
  make LINEAR deloads more faithful to §10; the engine already takes the count
  as a parameter, so it is a repository-side change.
- The protein hit rate on the Progress tab counts days at or above 90% of
  target protein, over days that were logged at all — a day with nothing logged
  is a missing measurement, not a miss.
