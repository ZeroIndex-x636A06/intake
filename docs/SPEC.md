# SPEC.md — Personal Nutrition and Training App

You are building an offline-first Android app for a single user. It will never ship to the
Play Store. It is installed by sideloading a signed release APK. There are no accounts, no
server, no analytics, no subscriptions, and no multi-user logic anywhere.

**Work one phase at a time.** The phase table is at the end of this file. Do not scaffold
future phases ahead of the one you are on. At the end of every phase, produce a signed
release APK that installs and runs, and stop for review.

---

## 1. Stack

- Kotlin, targeting the current stable Android SDK. `minSdk 29`.
- Jetpack Compose with Material 3. No XML layouts, no Fragments.
- Single-Activity architecture, Navigation Compose for routing.
- Room for all persistence. This is the single source of truth.
- Kotlin Flows end to end. The UI observes Room; nothing polls.
- Hilt for dependency injection.
- Retrofit + OkHttp + kotlinx.serialization for the three network calls the app makes.
- CameraX for capture, ML Kit barcode scanning on-device.
- Vico or Compose canvas for charts. No WebViews anywhere in the app.
- WorkManager for the nightly trend recalculation.

Verify current stable versions of each of these before pinning them, rather than using
versions from memory. Use a Gradle version catalog in `gradle/libs.versions.toml`.

## 2. Architecture

Three layers, strictly:

- `data/` — Room entities, DAOs, Retrofit services, repositories. Repositories are the only
  thing that touches DAOs or network.
- `domain/` — pure Kotlin. Calculation logic lives here: TDEE estimation, weight trend
  smoothing, 1RM formulas, progression rule evaluation, macro targets. No Android imports
  in this package. This is the part that gets unit tested.
- `ui/` — Compose screens plus ViewModels. ViewModels expose a single immutable `UiState`
  data class per screen. Screens are stateless and take state plus callbacks.

## 3. Non-negotiable constraints

1. Every feature works with no network except barcode lookup, food search, and AI photo
   estimation. Those three degrade to manual entry with a clear message, never a crash and
   never a blank screen.
2. No data leaves the device except the image sent for AI estimation and the barcode or
   search term sent for food lookup. State this in an in-app privacy screen.
3. API keys are user-supplied at runtime, stored in `EncryptedSharedPreferences`. Never
   hardcode a key, never put one in the repo, never commit one. The app is fully functional
   without any key, minus the AI feature.
4. All timestamps stored as UTC epoch millis. All dates the user sees derived from their
   local zone at render time. Never store a local date string.
5. All weights stored internally in kilograms and all lengths in centimeters. Display unit
   is a setting, converted at the edge. Default the display to pounds and inches.
6. Dark and light themes both fully supported, following the system setting.
7. Full keyboard and TalkBack accessibility: content descriptions on every icon button,
   touch targets at least 48dp, no color-only status indicators.
8. Nothing is ever hard deleted. Every entity has a `deletedAt` nullable column and queries
   filter on it. This is a personal dataset with no backups but the ones the user makes.

---

## 4. Data model

Create these Room entities. Every one gets: `id` (UUID string, primary key), `createdAt`,
`updatedAt`, `deletedAt` (nullable). Index every foreign key and every column used in a
date range query.

### Food and logging

**Food** — name, brand (nullable), barcode (nullable, indexed), servingSizeGrams,
servingLabel ("1 patty", "1 cup"), kcal, protein, carbs, fat, fiber, sugar, sodium,
saturatedFat, source (`MANUAL | BARCODE | SEARCH | AI_PHOTO | RECIPE`), allergenTags
(comma-joined string), isVerified, isFavorite, timesLogged. All macro values are per 100g
internally; serving math happens at display.

**FoodLog** — foodId, loggedAt, mealSlot (`BREAKFAST | LUNCH | DINNER | SNACK`),
quantityGrams, sourcePhotoUri (nullable), aiConfidence (nullable), wasEdited. Store a
denormalized copy of the food's macros at log time so editing a food later never rewrites
history.

**Recipe** — name, servings, instructions, totalTimeMinutes, sourceWeekNumber (nullable,
links to the meal plan).

**RecipeIngredient** — recipeId, foodId, quantityGrams, displayOrder.

**MealTemplate** — a saved combination logged in one tap. name, plus MealTemplateItem rows.

### Targets and body

**BodyMetric** — measuredAt, weightKg, bodyFatPercent (nullable), waistCm, chestCm, armCm,
thighCm, hipCm, notes, photoUri (nullable). All nullable but weightKg.

**NutritionTarget** — effectiveFrom, kcal, proteinG, carbsG, fatG, isAutoCalculated.
Targets are a dated series, not a single row, so history stays accurate when goals change.

**TdeeEstimate** — calculatedFor (date), estimatedTdee, confidence, rollingIntakeAvg,
weightTrendSlope, daysOfData. Written nightly by WorkManager.

### Training

**Exercise** — name, primaryMuscle, secondaryMuscles, equipment, isBodyweight,
defaultRestSec, notes, videoUri (nullable).

**Program** — name, description, isActive, currentWeek, currentDayIndex.

**ProgramDay** — programId, dayIndex, name.

**ProgramExercise** — programDayId, exerciseId, displayOrder, targetSets, targetRepsMin,
targetRepsMax, targetRpe (nullable), progressionRuleId (nullable), restSec.

**ProgressionRule** — name, type (`LINEAR | DOUBLE_PROGRESSION | RPE_BASED |
PERCENTAGE_1RM | CUSTOM`), and a params JSON blob. See §7 for how these evaluate.

**WorkoutSession** — programDayId (nullable, so freestyle sessions work), startedAt,
finishedAt, notes, bodyweightKg (captured at session start for bodyweight lift math).

**SetLog** — sessionId, exerciseId, setIndex, weightKg, reps, rpe (nullable), isWarmup,
isFailure, restTakenSec, completedAt.

### Meal plan

**PlanWeek** — weekNumber, mainDishName, proteinType, notes.

**PlanWeekRecipe** — planWeekId, recipeId, role (`MAIN | VARIATION`).

**EatOutOption** — planWeekId (nullable; null means it applies to every week), venueName,
whatToOrder, cautionNote, estimatedKcal, estimatedProtein.

**ShoppingListItem** — planWeekId, name, quantity, category (`PRODUCE | MEAT | PANTRY |
FROZEN | OTHER`), isChecked, isPantryStaple.

### Allergens

**AllergenProfile** — a single row. avoidedAllergens (comma-joined), severityLevel
(`AVOID | STRICT`), notes.

**AllergenAlias** — allergen, aliasTerm. Seed this table at first run with the dairy
aliases: milk, butter, cream, cheese, whey, casein, caseinate, sodium caseinate, lactose,
ghee, curds, milkfat, buttermilk, custard, half and half, condensed milk, evaporated milk,
nonfat dry milk, milk solids, lactalbumin, lactoglobulin, rennet casein, dairy.

### Migrations

Write an explicit Room migration for every schema change from version 1 forward. Never use
`fallbackToDestructiveMigration`, not even in debug. Export the Room schema JSON to
`schemas/` and commit it.

---

## 5. Today screen and food logging

### Today screen

The home screen. Opens to today, swipe left and right to change days.

- A macro ring at the top: calories consumed against target in the center, three arcs for
  protein, carbs, fat. Protein is visually dominant since it's the number that matters.
- Four meal slot cards below. Each lists its logged foods with kcal and protein, and has a
  plus button. Long-press a food to edit or delete, swipe to copy to another slot.
- A remaining-calories line that also shows remaining protein in grams, because "120g of
  protein left with 400 calories" is the actionable framing.
- Bottom bar: Today, Plan, Train, Progress, More.

### Adding food — five paths from one sheet

One "Add food" button opens a sheet with five tabs. Recents is the default tab and shows
the 30 most-logged foods, because in practice 90% of logging is repetition.

1. **Search.** Query the USDA FoodData Central API (free key, user supplies it in
   settings). Search the local Food table first and show those results above remote ones.
   Debounce 300ms. Cache every remote result into the local Food table so the database
   grows into a personal one that works offline.
2. **Barcode.** CameraX preview plus ML Kit barcode scanning, on-device. On a hit, look up
   Open Food Facts by barcode. Parse the allergen tags from the response and store them.
   If the product isn't found, offer to create it manually with the barcode attached, and
   keep that barcode locally so the next scan of the same item resolves instantly.
3. **AI photo.** See §6.
4. **Quick add.** Four number fields for raw macros. No food record, no name required. For
   when the user knows the numbers and doesn't care about the record.
5. **My foods.** Custom foods, recipes, and saved meal templates.

---

## 6. AI photo estimation

1. CameraX capture, or pick from gallery. Show a framing hint overlay suggesting the user
   include a fork, hand, or standard plate for scale.
2. Compress to max 1024px on the long edge, JPEG quality 85. Never send a full-resolution
   image; it costs tokens and adds nothing.
3. Send base64 to the Anthropic Messages API with a system prompt instructing a response of
   JSON only, no prose, no markdown fences.
4. Schema:

```json
{
  "items": [{
    "name": "string",
    "estimatedGrams": 0,
    "kcal": 0,
    "protein": 0,
    "carbs": 0,
    "fat": 0,
    "confidence": "high | medium | low",
    "containsAllergens": ["string"],
    "allergenReasoning": "string"
  }],
  "totalKcal": 0,
  "portionAssumption": "string",
  "overallConfidence": "high | medium | low"
}
```

5. Parse defensively. Strip fences if present, catch malformed JSON, and on failure show
   the raw text with a "log manually" fallback rather than an error toast.
6. Present results as an editable list before anything is written. Every field is a text
   field the user can correct. Nothing saves until they confirm.
7. Show the `portionAssumption` string prominently, e.g. "assumed a 2-cup bowl", with a
   slider to scale all values from 0.5x to 2x at once. This is the single highest-value
   correction affordance and it should be impossible to miss.
8. Store the photo URI on the FoodLog so the entry can be reviewed later.
9. Track estimation accuracy over time: when the user edits an AI estimate, record the
   delta. Surface a running "AI estimates have run about 15% low for you" note on the
   camera screen once there are 20 or more corrections.

Photo-based calorie estimation is approximate. Treat it in the UI as a fast first guess the
user corrects, never as a measurement.

---

## 7. Allergen engine

This is the feature no commercial app does well, and it is the reason the app exists. It
runs on every single food that enters the system, from any path.

1. On any food add or scan, run the name, brand, and ingredient text against the
   AllergenAlias table, case-insensitive, whole-word matching so "buttermilk" matches but
   "butternut" does not.
2. On a match, block the save behind a full-width red confirmation sheet naming the exact
   matched term and where it was found. Require an explicit "log it anyway" tap. Never a
   toast, never a small banner.
3. For Open Food Facts results, trust their structured allergen tags as primary and run the
   text scan as a second pass. Show a "may contain" warning separately from a "contains"
   warning, in amber rather than red.
4. For AI photo results, pass the allergen profile into the prompt so the model is asked
   directly whether the dish likely contains the listed allergens, and return its
   reasoning. Display that reasoning verbatim, since a restaurant dish is a judgment call
   and the user needs to see the basis for it.
5. Maintain a Safe Foods list: any food logged three or more times with no allergen flag
   gets a green check in search results.
6. A reference screen, reachable in two taps from anywhere, holding the hidden dairy
   checklist and the per-venue eat-out notes. This gets used standing at a counter, so it
   must load instantly from local data with no network call.
7. A third, softer tier, "Check the label", for foods that commonly hide dairy. It never blocks and
   never downgrades a stronger hit; see docs/ALLERGENS.md.

---

## 8. Adaptive TDEE

Do not use Harris-Benedict or Mifflin-St Jeor as anything but a day-one seed. Real
maintenance is measured from the user's own data.

- Smooth daily weights with an exponentially weighted moving average, alpha 0.1, to strip
  out water fluctuation. Display the trend line, not raw weigh-ins, everywhere but the raw
  log itself.
- Once 14 days of both weight and intake exist, calculate:
  `TDEE = mean daily intake - (weight trend slope in kg/day × 7700 kcal/kg)`
- Recalculate nightly via WorkManager using a 28-day rolling window. Weight recent days
  more heavily.
- Report confidence based on data density: fewer than 14 days is LOW, 14 to 28 is MEDIUM,
  more than 28 days with fewer than 3 missing logs is HIGH.
- When the user sets a rate goal ("lose 0.5 lb/week"), derive the calorie target from the
  measured TDEE, not a formula. Recalculate the target weekly and notify when it shifts
  more than 100 kcal, explaining why.
- Set protein target at 1.6–2.2 g/kg of goal bodyweight, defaulting to 2.0. Fat at a
  minimum of 0.6 g/kg. Carbs fill the remainder.

When data is too sparse to be meaningful, say so plainly in the UI rather than showing a
confident number. A wrong TDEE presented confidently is worse than no TDEE.

---

## 9. Plan tab — the 12-week meal plan

- A horizontal strip of week numbers 1 through 12 at the top, current week highlighted and
  persisted.
- The active week shows: main dish name, protein type, servings, total time.
- Three collapsible sections: Shopping list, Step by step, Other ways to eat it.
- "Log this meal" writes the recipe's macros straight to the current meal slot as a single
  FoodLog, computed from the recipe's ingredients divided by servings.

**Cook mode.** Full-screen, screen-stays-awake, one step at a time with large type. Any
step containing a duration gets a tappable inline timer that runs as a foreground service
with a notification, so it survives leaving the app. Swipe between steps. This is used with
greasy hands on a propped-up phone, so touch targets are oversized and there is no
precision tapping anywhere in it.

**Shopping list.** Generated from the active week's recipes, grouped by store section, with
pantry staples separated out and collapsed by default. Checkable, and check state resets
when the week changes. Allow manual additions. Include a share-as-text action.

**"Can't eat what I cooked."** A persistent button on the Plan tab. Opens a sheet showing
EatOutOption rows filtered to the current week's protein type, each with venue, exact
wording to order, a caution note, and estimated macros. One tap logs the estimate to the
current meal slot. This is used in a parking lot deciding where to eat, so it must be
reachable in two taps and work with no network.

---

## 10. Train tab

A program is data with progression rules attached, not a static list edited by hand every
week. Build that.

**Program builder.** Create a program, add days, add exercises per day with target sets, a
rep range, optional RPE target, rest time, and a progression rule.

**Progression rules.** Implement these evaluable types in `domain/`, as pure functions that
take `(previous session's SetLogs for that exercise, rule params)` and return the next
prescription plus a human-readable reason string:

- `LINEAR` — add X kg if all target sets hit the target reps. After N consecutive failures,
  deload by Y percent.
- `DOUBLE_PROGRESSION` — work up the rep range at a fixed weight; once the top of the range
  is hit on every set, add weight and drop back to the bottom of the range.
- `RPE_BASED` — adjust load so the logged RPE converges on the target RPE, using an
  RPE-to-percentage table.
- `PERCENTAGE_1RM` — prescribe a percentage of an estimated 1RM that updates as new maxes
  are set.
- `CUSTOM` — a small expression evaluator over the variables `weight`, `reps`, `sets`,
  `completedReps`, `rpe`, `failureCount`, `week`. Support arithmetic, comparisons, and
  if/else. Do not build a full language. A recursive-descent parser over about 20 tokens is
  enough, and it must never crash the app on a malformed rule — surface a validation error
  in the builder instead.

Always show the reason string next to a prescription: "185 lbs, up 5 from last week (all
sets hit 8 reps)". A number with no explanation is why people abandon these apps.

**Active workout screen.** The current exercise with its prescription, a set-by-set entry
grid pre-filled with prescribed values so logging a set is one tap when it goes to plan.
Auto-start the rest timer on set completion, with the remaining seconds in a notification
and an optional vibration at zero. Show last session's numbers for the same exercise inline
for comparison. Plate calculator showing which plates per side for the prescribed weight,
configurable for available plates and bar weight. Warmup set generator. Swipe to the next
exercise, and allow reordering mid-session since a rack might be taken.

**History and analytics.** Per exercise: estimated 1RM over time (Epley, and show the
formula used), volume per session, and a personal record list. Per muscle group: weekly set
counts, flagging groups under 10 working sets a week. Session duration trends.

---

## 11. Progress tab

- Weight: raw points with the smoothed trend line over them, selectable 1M / 3M / 6M / 1Y /
  All ranges.
- Measurements: one line per site, toggleable.
- Progress photos: a date-sorted grid, plus a two-up comparison view where the user picks
  any two dates. Store these in app-private storage, excluded from the media scanner so
  they don't appear in the gallery.
- Nutrition adherence: a calendar heatmap of days logged, and calories against target as a
  bar chart with the target as a reference line.
- A weekly summary card: average intake, average weight change, estimated TDEE with its
  confidence level, total training volume, and protein target hit rate.

Never display a streak counter that resets to zero on one missed day. Show "18 of the last
21 days logged" instead. The reset-to-zero pattern reliably makes people quit after one bad
week.

---

## 12. Health Connect

Integrate Health Connect as the bridge to Samsung Health rather than targeting Samsung
Health directly. Its SDK is not generally available for third-party writes, and Health
Connect is the supported path.

- Read: steps, active calories, total calories burned, weight, sleep duration, heart rate.
- Write: nutrition records for each FoodLog, weight from BodyMetric, and exercise sessions
  from WorkoutSession.
- Request permissions granularly and let the user deny any of them without breaking
  anything. Every screen must render correctly with Health Connect entirely absent, since
  it may not be installed.
- Sync on app resume and after any write, debounced. Never sync on a timer.
- When active calories are available, offer to add them to the daily target, off by default
  and clearly labeled, because wearable calorie burn estimates are unreliable and
  double-counting them is the most common way people stall a cut without knowing why.

---

## 13. Export, import, settings

- Full JSON export of every table to a user-chosen location via the Storage Access
  Framework. This is the backup, and it must round-trip losslessly.
- Import validates against the schema and shows a diff summary before writing.
- CSV export per domain: food logs, workouts, body metrics. Flat, spreadsheet-friendly, one
  row per record.
- A settings prompt if no export has happened in 30 days.

**Settings screen.** API keys for Anthropic and USDA, with a test-connection button on
each. Units. Allergen profile editor. Nutrition targets, manual or auto. Theme. Rest timer
defaults. Available plates and bar weight. Export and import. A privacy screen stating
exactly what leaves the device and when. Version and build info.

---

## 14. Testing

Unit tests are required for `domain/`: TDEE calculation, weight trend smoothing, every
progression rule type, the custom rule parser, unit conversion, and allergen matching
(including the buttermilk-versus-butternut case). Room DAO tests with an in-memory
database. Compose UI tests for the log-a-food and complete-a-set flows. Do not write UI
tests for everything; those two flows are where regressions actually hurt.

---

## 15. Build and sideload — Linux

Provide these as working files, not instructions:

1. A `.gitignore` that excludes the keystore, `local.properties`, and any key files.
2. A keystore generation command in the README:

```bash
keytool -genkey -v -keystore ~/.android/personal-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias mealtrain
```

3. Signing config in `app/build.gradle.kts` reading from `local.properties`, which stays
   untracked. Fail the release build with a clear message if the keystore is missing.
4. Release build type with minify and resource shrinking enabled, plus ProGuard keep rules
   for kotlinx.serialization, Room, and Retrofit models. Verify the release APK actually
   runs — these three are exactly what R8 tends to break.
5. Build and install:

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

6. README notes: enable Developer Options and USB debugging on the phone; on most distros
   `adb` comes from the `android-tools` or `android-sdk-platform-tools` package; if the
   device shows as unauthorized, accept the RSA prompt on the phone; if it doesn't show at
   all, add a udev rule for the vendor ID.
7. Version the app with a `versionCode` derived from the build date so successive sideloads
   upgrade in place rather than requiring an uninstall. Note in the README that changing
   the signing key later forces an uninstall, which loses all data, so the keystore should
   be backed up somewhere that isn't the laptop.

---

## 16. Build order

Complete one phase, produce an installable release APK, stop for review. Do not start the
next phase until the user confirms.

| Phase | Scope | Done when |
| --- | --- | --- |
| 1 | §1–§4. Scaffold, Room schema, navigation shell, theme. | A signed APK installs and the empty tabs render. |
| 2 | §5. Today screen, manual and quick-add logging, macro ring. | A day's food can be logged by hand. |
| 3 | §7 plus barcode and search from §5. | Scanning a dairy product blocks with a red sheet. |
| 4 | §8, §11. Body metrics, weight trend, adaptive TDEE, Progress tab. | The trend line looks right after two weeks of weigh-ins. |
| 5 | §9. Meal plan module, cook mode, shopping list, eat-out swaps. | A week 1 dinner can be cooked entirely from the app. |
| 6 | §10. Program builder, progression rules, active workout. | A full session logs and the next week's weights are right. |
| 7 | §6. AI photo estimation. | A photo of a real plate produces a correctable estimate. |
| 8 | §12, §13. Health Connect, export and import, settings polish. | A full export round-trips into a fresh install. |

AI photo estimation sits at phase 7 deliberately. It is the most demo-able feature and the
least load-bearing, and building it first is how these projects end up as a working camera
screen attached to nothing.

### Known failure points

- **R8 on the release build.** Debug works, release crashes on a serialization or Room
  call. Build and install the release APK at phase 1, not at phase 8.
- **Room migrations.** The first time the schema changes after real data exists, a
  destructive migration will be the tempting fix. Write the migration instead.
- **The AI returning prose.** Models wrap JSON in fences or add a sentence of preamble. The
  parser has to strip and tolerate that, and needs a fallback path rather than an error
  state.
- **Compose recomposition.** A macro ring that recalculates on every keystroke makes the
  log screen feel sluggish. Watch for unstable lambda parameters and unkeyed lists.
- **Health Connect permissions.** Behavior differs across Android versions and the app has
  to survive every permission being denied.
- **The food database.** This is the real gap versus MyFitnessPal. USDA covers whole foods
  well and branded items poorly. Open Food Facts is the reverse and its data quality
  varies. Expect the user to hand-enter their regular items once, roughly 40 foods, after
  which it stops mattering.
