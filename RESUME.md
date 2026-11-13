# Where this build is up to

Read this first when picking the work back up. Latest work: branch
`claude/optimistic-noether-fullk5` (not yet merged into `claude/app-build-k2ag1d`).

## Round 4 merge and emulator smoke (branch `claude/optimistic-noether-fullk5`, schema v5)

Six `--no-ff` merges on this branch: navigation transitions, Progress charts, drinks (schema v5, `MIGRATION_4_5`), recipes in Log food / Cook / meal templates, Recipe Hunter substitutions, and nearby menus (last; only `RESUME.md` conflicted, both sides kept). Verified at the last merge: `:app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :wearprotocol:test :wear:lintDebug` all pass (app 1908 tests, 3 skipped, 0 failed; wearprotocol 14; app lint 0 errors, wear lint 0 errors).

**Seen on the Android 11 emulator (software rendering, so slow):** upgrade from the bc8e5e4 debug build (a quick-add food and 8 fl oz of water logged) to the merged debug build with `adb install -r`: data kept, `user_version` 5, no crash. Back animation, captured with screenrecord at 30x animator scale (`design/r3-smoke/r4-back-anim-*.png`): the leaving screen stays on top and slides right while the screen beneath is revealed. Progress: weight chart has y-axis labels (178, 180, 182 lb) and a long press-and-drag shows the scrub tooltip with date, weight and source (`r4-progress-*.png`, 14 weigh-ins seeded into the emulator DB). Today shows the Water (hydration) card with the logged drink; Nutrition tab has a Drinks meal slot and no Drinks entry under More; Log food has the Drinks chip and a saved recipe in search; Drinks > Customise opens. Recipe screen: Cook beside Log when the recipe has steps (it is hidden without steps); Cook mode shows the ingredient checklist. Meal templates: the editor and the Shopping list open. Recipe Hunter opens with no key and shows the gate with "Set up AI tools". Eating out: Settings shows "Eat out nearby: how far" (10 / 15 min, Walk / Drive); the nearby card has "Use AI to find menus I can't"; tapping search with location denied returns to the idle card with no message, with location allowed the emulator has no network and shows "Could not reach the map service". No entries for `com.zeroindex.intake` in the crash log.

**Still needs a phone:** the real back/push feel at 60 fps, predictive back; Overpass and chain-page results with a real position and network; the OpenRouter paths (menu AI, Recipe Hunter search); real PDFs through `PdfRenderer`; Health Connect; the watch pair. A recipe with no listed calories still logs as "kcal not listed" (not changed). Denying location gives no explanation text, only the idle card (worth a look).

## Navigation transitions (FEEDBACK-R4 item 8, branch `worktree-agent-acb080647531a0b9a`)

- Push and back are now mirrors in `ui/navigation/NavTransitions.kt` (`NavSpecs`, 300 ms FastOutSlowIn both ways; the screen underneath moves a quarter width left and dims to 0.85). Tab switches cross-fade in 150 ms. Predictive back uses the same back motion via `predictivePopEnterTransition`/`predictivePopExitTransition`. Checked by `NavTransitionsTest`. Not seen on a device; the owner's phone is the test.

## Progress charts: weight trend and scrubbing (FEEDBACK-R4 items 4 and 5; branch `worktree-agent-a7ca2649551f0d88b`, not pushed)

What was off, found by reading the code and pinned with tests in `domain/charts/ChartMathTest.kt` (the owner's actual data was not available):
- **The trend lagged months behind the dots.** `WeightTrend.smooth` steps the average once per weigh-in, whatever the gap. After 20 daily weigh-ins near
  100 kg and a 150-day silence, two readings near 95 kg moved the trend only to about 99.5. That is the owner's "cluster, flat line, two points at the
  edge", and it also understated "−0.2 lb/week". `WeightTrend.smoothByTime` now makes alpha the pull of one day (after g days it is 1 − 0.9^g); with a
  weigh-in every day it equals the old result exactly. Only the Progress chart uses it (`BodyRepository.observeTrend`); the TDEE and coach code still call `smooth`.
- **The trend line was drawn straight across the gap.** It now breaks at more than 14 days (`ChartMath.runs`) and a faint dashed bridge joins the runs.
- **Dots and trend were on different time bases.** Dots used the real instant, the trend used day index × 86 400 000 (UTC midnight), so it sat up to
  half a day off in US zones. Both are real instants now (`ChartMath.localDayStartMillis`). Axis is by date, not index (it already was).
- **The headline "195.0" was the latest raw weigh-in, labelled "raw weigh-ins".** It now says "latest weigh-in, <date>" and a "Trend X" line sits under it.
- **No y-axis labels.** Gridlines now fall on round values with labels in the display unit (`ChartMath.niceTicks`).
- Checked and fine: kg to lb uses 0.45359237 and displays after a single rounding (tested over 1000.0 to 4000.0 in 0.1 lb steps, and a test shows why
  rounding the kilograms first would shift 195.0 to 195.1); day bucketing uses the clock's zone, not UTC (tested).
- **Not changed, only noted:** `WeighInDedupe` keeps the earliest of two readings inside 10 minutes, with no preference for a source, and a second
  app that stamps the same weigh-in more than 10 minutes apart would show as two points (both are then averaged into one day for the trend). Which app
  wrote a row is not stored (only `HEALTH_CONNECT` or `MANUAL`), so the tooltip says "Health Connect", not the app name; adding it needs a v5 migration.

Scrubbing (`TrendLineChart` in `ui/components/Charts.kt`, used by weight, sleep, steps, resting HR, the measurement lines and the 1RM chart): press and
drag sideways to get a guide line, the nearest dot enlarged and a tooltip (date, value and unit, source). The gesture claims only a horizontal move
(`awaitHorizontalTouchSlopOrCancellation`), so a vertical drag still scrolls the page. A haptic tick (`TextHandleMove`) fires when the nearest point
changes. Nearest-point maths is `ChartMath.nearestIndex`. Not seen on a phone or an emulator: gesture feel, haptics and tooltip placement are unverified.
The Overlay chart (bars plus line) and the calorie bars have no scrubbing.

## Drinks: no meal, off the Nutrition tab, custom drinks (round 4 items 7, 9, 10)

Worktree branch, compiled, linted and unit-tested only; nothing was run on an emulator or a phone.

- **Item 7, root cause.** `DrinkRepository.log` defaulted its slot to `slotAt(at)`, which is `TemplateLink.slotFor(time of day)`, and
  `logOfType` (Today's water card, the watch, quick sizes) always used it. Food detail did the same when opened with no meal (it is opened from
  Drinks with none). So a 12:30 coffee from Drinks was a Lunch row. Now those paths write `MealSlot.DRINKS` and Food detail picks it for a drink with
  no meal given. Logging from a meal's Log food still passes that meal and stays in it. `MealSlot` is stored by name in a TEXT column, so the new value
  needed **no schema change**; existing rows keep their slot. Today lists the four meals and a "Drinks" card only once it has an entry (its row opens
  Drinks, not Log food). The day's kcal and sugar still sum over every row. Meal pickers use `MealSlot.MEALS`; the move/copy sheet also offers Drinks.
- **Item 9.** The Drinks row is gone from the Nutrition meals card and from Nutrition > More (and `NutritionActions.onOpenDrinks`). Drinks is reached from
  Today's water/drinks card and from a "Drinks" chip next to Mine and Quick add in Log food. Nothing else in Log food changed.
- **Item 10.** `domain/drinks/CustomDrink.kt` holds the maths (add-in list, volume, per-100 ml, caffeine and strength after dilution, allergen union);
  `data/repository/CustomDrinkFood.kt` turns a draft into a food row; `ui/screens/drinks/CustomDrinkScreen.kt` and `CustomDrinkViewModel` are the screen
  (`Route.CustomDrink`, root). A saved "My drink" is a food row with `customDrink` (recipe JSON) set and its nutrition per 100 ml, so it shows in Drinks
  (a "My drinks" section on top, with Edit and Delete) and in the drinks search. **Schema v5** (`MIGRATION_4_5`, `MigrationSchemaTest` case).
  Built-in add-in values are USDA FoodData Central reference figures typed into the catalog, not looked up in the bundled database at run time; any food from
  the search (the person's own or the bundled USDA file) can also be an add-in.
- **Not verified:** any of the screens on an emulator or phone; the layout of the My drinks section and the scroll index on the Drinks page; the
  watch path (it goes through `logOfType`, covered only by the source guard and `DrinkLoggerTest`); the effect on an install upgraded from v4 (covered by the
  migration test only). Another branch that also bumps the schema to 5 will conflict with `MIGRATION_4_5`; renumber one of them.

## Round 4, items 6, 11 and 14 (branch `worktree-agent-aa92edb758b4dbe50`)

Built on `claude/optimistic-noether-fullk5` at bc8e5e4 (merged in first). No schema change (no MIGRATION_4_5): everything uses the
existing tables; the one new DAO query (`FoodLogDao.observeRecentPrepared`) is a read.

- **Item 6, recipes in Log food.** `RecipeSearchViewModel` (own view model, so `AddFoodViewModel` is untouched) feeds three places in
  `LogFoodScreen`: a "Recipes (saved on this phone)" section in search results, "Recipe" rows in Mine, and "Recent recipes" with Recent.
  Ranking and recents are pure (`RecipeSearch`, `RecipeSearchTest`). A row shows per-serving kcal and protein or "not listed". The "+" logs one
  serving through `RecipeLogger.logServings` (the recipe screen's `logServing` now delegates to it): unknown protein, carbs or fat stay
  unlisted, and the local allergen scan runs first (`RecipeLogger.scan`, over every ingredient line); a match waits behind the warning sheet.
  A recipe with no calorie figure cannot be logged from search (it says to open it in Recipes). Recents come from logged rows with no food
  behind them, matched to recipes by name, so a quick add with the same name counts.
- **Item 14, Cook.** Cook mode already existed (timers, wake lock, big controls) but only the Plan screen opened it. There is now a **Cook** button
  beside "Log a serving" on the recipe screen (shown when the recipe has steps). Cook mode got a checklist at the top (ticks, group headers,
  hide/show) scaled by a servings stepper (`IngredientScaling`, `IngredientScalingTest`), and "Done cooking" on the last step offers "Log a
  serving" (slot picker, allergen scan, Undo). Timers (`CookSteps.durations`, "1-2 hrs" and "10 minutes" tests added) and the notification
  service were already there and are unchanged.
- **Item 11, templates without a plan.** `MealTemplateEditorScreen` makes and edits templates (name, then foods, USDA database foods and
  recipes with amounts); Meal templates has "New meal template", Edit, and a confirmed soft delete; Log to a chosen slot and the optional NFC
  tag are as before. A recipe goes in as a 100 g "1 serving" food (`RecipeFood`), unknown figures stay unlisted. `saveSlotAsTemplate` now
  carries a logged meal's unlisted figures into the template's foods (before, an unknown protein became 0 g). "Meal prep" wording is "meal
  template" on Today, Meal templates, More, Swap and Privacy. **Shopping list** (Nutrition, More): `StandaloneShopping` builds a list from
  chosen recipes and templates with the plan list's own quantity adding; ticks and picks live in saved UI state only, not the database.
  Audit of what needs a plan: `docs/MEAL-PLAN.md`, last section.

Not verified: none of this has been run on a phone or an emulator. Unit tests and lint only. Not seen: the Log food rows and sheets, the editor
and its search, cook mode's checklist and Done dialog, the shopping list screen. The shopping list does not survive leaving the app for long
(process death keeps it through saved state; clearing the app does not). Recipe foods made for templates appear in Mine and search like the
prepared-meal foods `saveSlotAsTemplate` always made. Wear's "Saved meals" label is unchanged.

## Recipe Hunter: Could be adapted, Saved badge, images (agent F, round 4 items 12, 13, 15; not pushed)

- Item 12: `domain/allergen/Substitutions.kt` (rules in docs/ALLERGENS.md) plus a "Could be adapted" section and "Save adapted copy". Recipe Hunter now scans the
  title with the free-text rules and the lines strictly, so "Vegan Mac and Cheese" with clean lines stays in the main results. No schema change.
- Item 13: "Saved" chip on cards, matched by URL (`HuntKeys.urlKey`) against My recipes when results load.
- Item 15: `RecipeImages` (og:image, twitter:image, first large img) behind the JSON-LD image (string, array, ImageObject, @graph reference); thumbnail on the card, saved with the recipe.
- Existing matcher gap, NOT changed (outside this task): an ingredient line "non-dairy milk" scans CONTAINS (milk), while "vegan", "oat", "dairy-free <x>" lines scan check-the-label. Titles like "Vegan Mac and Cheese" are already check-the-label. A one-rule fix in `AllergenMatcher` (treat "non-dairy" before a keyword as NON_DAIRY check-the-label, when claims apply) is proposed; the owner of the matcher should decide.
- Not verified: on a phone or emulator; real pages; the swaps are text rules, so odd ingredient wording can be missed (it then refuses, the safe side). Step text of an adapted copy is the original's.

## Round 4, items 1 to 3: nearby finder, nutrition-menu AI offer, OSU PDF (branch `worktree-agent-a3df9485fbad45854`)

**Item 3, the OSU McDonald's PDF.** The text is selectable (26 pages, 463 items). `NutritionTableParser` read lines and could not use it:
the column headings are rotated (no heading line), every gram column is followed by a "% Daily Value" column (21 numbers a row, not 12),
and a name wraps over up to seven lines centred on the numbers, mixed with "9.7 oz" and "(274 g)". New `domain/menu/PositionedNutritionTable.kt`
reads by word position: number runs give the columns, the rotated headings name them, name lines go to the nearest row, section headings
are kept. All 463 rows of the file read with none skipped (checked offline with `pdftotext -bbox` words fed to it). `MenuIngestor` runs both readers
and keeps the one with more rows. A table with no allergen column gives `allergensStated = false`: its items are at best "check the label", never
"no allergens listed". Fixture: page 1 words in `test/resources/menu/mcdonalds-usa-nutrition-page1-fragments.tsv`; values asserted were read off the PDF.

**Item 2.** The nutrition follow-up card shows "Read it with AI" (tap only, key needed, says what is sent and that it is billed on the person's key) when the phone's reader finds no table in a chosen PDF or a linked PDF. It reuses the AI menu path and merges through `MenuNutritionMerge` as before.

**Item 1, nearby rebuilt.** Radius is now a travel time (Settings > Nutrition, one registry row `eatOutRadius`, stored as `eat_out_minutes` 10/15 and
`eat_out_travel` walk/drive, default 10 min drive; the old `eat_out_radius_mi` is read once to migrate). Search: Overpass (one request, honest User-Agent, no key) ->
nearest first, one row per chain -> per restaurant, two at a time (max 30): saved source, bundled chain table (`assets/nearby_chains.json`), the restaurant's website links
(`MenuLinkScorer`), then AI only if the card switch is on (nutrition page address with web search, else labelled estimates; max 6 lookups). Every menu goes through the normal menu import
and is saved as a source. The live list shows each row's state; picks join the list as each menu arrives; Stop works at any point. If the map service fails the card offers "Search with AI instead" (a tap).
A zip code still uses the old AI search. Pure parts are in `domain/nearby/` with `NearbyDomainTest` (radius, Overpass JSON fixture, chain match, link scoring, saved-source match, pipeline).
Chain table status: see below. Docs: PRIVACY.md (3 new rows), AI-COSTS.md (row 11), SETTINGS-INVENTORY.md row 18.

**Not verified.** Overpass could not be reached from the cloud container (the proxy closed the connection), so the live request and answer shape are
written to the documented API and tested on a hand-made fixture only. The OSU PDF was parsed from word boxes made by poppler, not from Android's `PdfRenderer`
(its fragment shapes are not seen on a device; the reader assumes word or line fragments with boxes). Chain pages: many are script-driven pages and may need AI; the table was
checked from a cloud container only. Nothing was run on an emulator or phone.

## Merge of three agent branches (branch `claude/optimistic-noether-fullk5`)

Merged with `--no-ff`, one at a time, each followed by compile and the unit tests: recipe nutrition (`worktree-agent-a2198826f8ff9a2b4`), Recipe
Hunter list pages and Find more (`worktree-agent-a413caf0d3be660a6`), eating-out healthier nearby and Choose anyway
(`worktree-agent-a4d8ea88c7c1d444a`). Only RESUME.md and docs/AI-COSTS.md conflicted (kept both sides). No code conflicts; the nearby/menu work did
not touch the menu scanner or MenuLogRepository, so the allergen-chart reader and MenuItemMatcher are unchanged. Duplicate in-screen headings removed
from the Recipes and Eating out hubs, Your plan, What leaves this phone, Log weight, the Recipe editor and the not-found Food screen (subtitles kept).
PRIVACY.md now lists the Recipe Hunter page fetch. Seen on the debug build on the emulator (screens in `design/r3-smoke/merge-*.png`): Today,
Nutrition, More, Eating out with the healthier-nearby block (no imported menus, AI not set up), Recipes, Recipe Hunter (AI not set up), the recipe editor, a
hand-made recipe showing "Nutrition: not listed", Menu import. Not seen: Recipe Hunter and nearby searches with a key, Choose anyway, shopping totals,
any of it on a phone.

## Recipe nutrition and shopping amounts (branch `worktree-agent-a2198826f8ff9a2b4`)

Root cause of "0 g protein, 0 g carbs, 444 kcal": the page's JSON-LD gives only `"calories":"444 calories"`; the parser correctly left the macros
null, but the recipe screen, the import preview and "Log a serving" turned null into 0. Now: unknown shows "not listed"; a log row for an unknown
macro stores 0 plus `protein`/`carbs`/`fat` in `unlistedNutrients` (no migration; `CoreMacro`); the schema.org parser also reads
`@id`-referenced and array nutrition, "12g"/"12 grams"/"1,200"/"3,5 g" strings, and prefers kcal over kJ. Missing macros are estimated offline from the
ingredient lines against the bundled USDA file and shown as "Estimated from ingredients" with per-line matches behind a tap. Shopping list:
quantities are summed per item across the week's recipes by the same line reader. Not verified on a device or emulator. Corrections to
the estimate's matches last only while the recipe screen is open. The plan stores no servings, so shopping amounts use each recipe as written.

## Find nearby, two modes (branch `worktree-agent-a4d8ea88c7c1d444a`)

One finder (`PlanBFinder.search`, `NearbyMode`) with two rankings in `domain/swap/NearbyRanking.kt`. Swap ranks by closeness to the planned meal's calories and protein (deltas shown as chips); Eating out has a new "Find healthier options nearby" button (`ui/screens/hub/HealthierNearby.kt`) that shows imported menus and saved options first (no AI), then an optional AI search on tap, ranked by a healthier score with a short reason. No schema change; no new AI feature. Compiled, unit-tested and linted only; not seen on an emulator or a device.

**Nearby follow-up.** The AI prompt (both modes) now sends allergens and the profile notes, asks for 8 to 15 options across many restaurants with the usual modifications, and "Search again for more" sends what is shown so results add rather than repeat. Orders and modification notes are read as free text, so "Whopper without cheese" is not a cheese hit (shown as "Check with the restaurant" when that was the only finding). "Choose anyway" on blocked options (Swap, Eating out nearby, imported menus) opens a warning dialog and logs with "Logged despite: dairy" on the entry's name and serving label; no schema change. Unit-tested and linted only. Imported menu items stored as CONTAINS at import time are still excluded from the healthier and swap menu lists (the stored status is not re-derived).

## Round 3 (phone feedback), branch `claude/optimistic-noether-fullk5`

Head `06871e3`. What the owner sent from the phone is in `docs/FEEDBACK-R3.md` and `docs/R3-BACKLOG.md` (the status section
at the top lists what is done and what is left). Verified: 1599 unit tests and 0 lint errors (app and wear).

- **Recipe Hunter yields 10 to 20 results (branch `worktree-agent-a413caf0d3be660a6`).** Old loop: target 5, 3 rounds, the model searched, fetched and copied full recipes in one reply capped at 6,000 tokens, so about one recipe a round came back (the owner saw "3 kept of 3 reviewed" for $0.098). Now the model returns only page addresses (10 a round, 2 searches); the app fetches and reads each page (`RecipePageParser`, 4 at a time), checks allergens and the page's published protein, kcal and time locally, and loops until 12 are kept, 4 rounds or the $0.08 cap. "Find more" (a tap) continues with exclusions. Close matches (within 10%), "Nutrition not stated on the page" and "Could not check" groups; a legend explains Check the label and Check allergens. Rules and tests: `domain/ai/HuntRules.kt`, `HuntRulesTest`. Unit-tested only; no live AI call and not seen on a device or emulator. Pages that need JavaScript or have no recipe data land in "Could not check".
- **Menu import download checks (partial; McDonald's Ireland PDF).** A link now fails clearly when the site answers 401, 403 or 429, or sends HTML where a PDF was expected (`domain/menu/DownloadClassifier.kt`, `Fetched.Refused`): "<host> refused the download (HTTP 403). Open the link in your browser, save the PDF, then choose it with 'Choose a PDF'", with a Choose a PDF button on the error card. The PDF marker is looked for in the first 1 KB. The menu screen shows one error card (not two) and no second "Menu import" heading. The client still sends the honest User-Agent. The McDonald's file itself is an allergen chart with no nutrition values and our PDF parser reads it; see docs/TASKS.md. Unit-tested; not seen on a device.
- **Allergen charts (McDonald's Ireland PDF).** That file is an allergen chart (tick glyphs in a grid, no calories or macros), so there is no nutrition table to read. `domain/menu/AllergenChartParser.kt` reads such charts from positioned text (PdfRenderer fragments, Android 15+): the 19 allergen columns are matched to the headings by a regular grid, a tick is Contains and an asterisk May contain, Vegetarian/VE is a tag, a blank cell never clears anything (every item is at best "check the label"), no kcal or macros are invented. On the real file: 156 items, 411 marks, 0 skipped tables (JVM test on positioned words from `pdftotext -bbox`; PdfRenderer's own fragments for the tick glyph are not seen on a device). After saving a chart the screen asks for the nutrition menu (Choose a PDF, Paste a link, Skip; skip is stored per source in a device-local setting, and the row keeps an Add nutrition menu action). `MenuItemMatcher` + `MenuNutritionMerge` merge the two files: nutrition from the nutrition file, allergens the union, harder verdict wins, "Matched N of M items". A weekly refresh of the chart keeps the nutrition. The AI PDF path now shows the real failed request's message instead of "No menu items", and its prompt forbids inventing nutrition. **Root cause of "page list could not be found":** `PdfDocument.valueAt` read a reference with the backslash-G regex anchor plus `find(start)`. Android's regex engine anchors that at the start of the text, the JVM at `start`, so on a phone every `15 0 R` came back as `15` and the catalog's /Pages was never found, while every JVM test passed. Reproduced by running the compiled parser on the Android 11 emulator (d8 + app_process): failed before, `pages=2` after. Fixed with `Regex.matchAt`; a test now bans that anchor in main sources. It only showed on the AI path, because the phone's own PdfRenderer text path never calls our parser.
- **Launch crash after updating a preview (fixed).** Version 3 had been edited in place (`deloadEveryWeeks`, `activeKcalDay`, and others earlier) after preview builds had already created a v3 file on the phone, so the next build died on open with Room's "cannot verify the data integrity" on every launch. Reproduced on the emulator (preview from 9344e7f, then the head build); upgrades from 06871e3 never crashed. The database is now version 4: `MIGRATION_3_4` (`migrateRepair_3_4`) replays the structural half of 2 -> 3 and skips what the file already has. `MigrationSchemaTest` covers an older v3 file, a current one and a repeat run. **Rule from now on: a preview APK counts as shipped, so schema changes after one went out need a new version, never an edit to the last one.** WearBridge feeds and PhoneWearListener requests also catch and log failures now. Confirmed on the emulator (upgrade and fresh install); not on the owner's phone.
- **Ring percentages.** Labels were centred at a fixed radius, so wide ones overlapped the ring. `domain/nutrition/RingLabelLayout.kt` places each measured label 6 dp outside the ring and nudges it outward if two touch; the ring's box is wider. Unit-tested; seen on the emulator with a held press (`design/r3-smoke/nutrition-v3-held-fixed.png`), not on a phone.
- **Quick add limits.** A typed value such as 348416 g of protein was saved. Per-entry limits now live in `domain/nutrition/QuickAddLimits.kt` (kcal 5000, protein/carbs/fat 500 g, fibre/sugar 300 g, sodium 20000 mg): the form shows an inline error and disables Log it, the save path refuses, and the watch's calorie request is refused with a message. Unit-tested; not seen on a device.
- **AI.** The real cause of "No provider for this model meets your privacy setting" was `temperature` sent together with
  `require_parameters` to models whose providers do not accept it. Fixed. Added a fallback to the other compatible models
  and a clearer message.
- **Photo estimate.** Asks up to three short questions with choices when it is unsure. No questions when it is confident.
- **Health Connect.** Allow all button; permission for older history; weigh-ins read back a year; Activity card on Today;
  sleep, steps and resting HR charts on Progress; sleep no longer counted twice. Schema `migrateHealthActive_2_3`.
- **Reminders.** Workout, meals, water and progress photo notifications (settings 122 to 129).
- **Tour.** Waits for scrolling to settle before moving the highlight.
- **Recipe import.** Reads recipe data wrapped in CDATA; strips tracking parameters (`utm_*`) before fetching.
- **Food.** Servings read as "1 serving (43 g)" style. Macro split bar on the food page, Today's meals and the Nutrition
  dashboard.
- **Restaurant menus.** In Swap and in Log food. Nutrition PDFs are read on the phone on Android 15 and newer; older phones use AI.
- **Nutrition tab v3** (`design/mockup-r3/nutrition-tab-v3.html`, option 1 "Day at a glance"). The root is now: compact
  "Avoiding: ... >" chip; one overview card (124 dp calorie ring, "N kcal left", "of X kcal", "N eaten"; Protein, Carbs and
  Fat cards with grams, target, bar and kcal; Fibre, Sat fat, Sugar and Sodium or Salt in a 2x2 grid, "not listed" never 0;
  "More nutrients" holding the reference-limit note); a Meals card (Breakfast, Lunch, Dinner, Snacks, Drinks, each with
  an icon tile, foods and kcal, a mini macro bar, a "+" to Log food for that slot, "Planned: ... Log it / Swap" for the
  planned meal when the slot is empty); and a "More: plan, recipes, eating out, nutrients" row to the new pushed
  `Route.NutritionMore` (Plan, Recipes, Meals, Eating out, Drinks). Holding the round pie button on the card draws the
  eaten arc as protein, carbs and fat segments with each percentage just outside the ring, and adds "P% of kcal" to the
  macro cards. Pure maths: `domain/nutrition/MacroSplit.kt` `MacroRingSplit` (9 tests). No schema change. The old Search,
  Scan, Photo tiles, planned-meal card, Drinks card and the four-section switch were removed from the root; Search, Scan
  and Photo are reached from each meal's "+" (Log food). Tapping a meal row opens Log food for that slot (there is no
  per-slot screen; entries are edited from Today). Verified: 1608 unit tests, 0 lint errors.
  Seen on the emulator (debug build): chip, ring, cards, grid, meal rows with mini bars, "Nothing yet", Drinks row,
  the held state (ring segments 26/48/26 with labels, "% of kcal" on the cards) and the More screen; screenshots
  `design/r3-smoke/nutrition-v3-*.png`. Not seen on the emulator: the "Planned: ... Log it / Swap" row (no plan loaded),
  the haptic, the held press with a finger on a phone, the Salt setting. Not on a phone at all yet.
- **Earlier round-3 items** are listed in `docs/FEEDBACK-R3.md`.

**Seen on an emulator** (Android 11 image, software emulation; see `docs/HANDOFF.md` section 4), debug build: setup skipped,
then Today, Nutrition, Train, Progress, You and Settings opened; the Reminders group and the Health Connect group opened;
Log food search for "cheddar" returned USDA results with the allergen chip. No crash and no "not responding" kill in logcat.
The Health Connect group says "Health Connect needs an update" on this image, so its switches were not seen. Screenshots:
`design/r3-smoke/`. Not seen: the tour, the photo estimate questions, the food page macro bar, reminders firing.

**Not seen anywhere yet:** nothing in round 3 has run on a physical phone. The Health Connect data, reminders, the photo
questions, the PDF reader and the video playback are unit-tested or compiled only.

## Option A parts 2 and 3, and the first emulator pass (branch `claude/optimistic-noether-fullk5`)

Both WIP branches (`agent/nutrition-wip`, `agent/train-wip`) are merged and finished. Verified on the merged result:
1440 unit tests, 0 lint errors (app and wear), `MigrationSchemaTest`, and the sync server tests.

- **Nutrition (part 2).** New Nutrition home (allergen strip, Search/Scan/Photo, today's planned meal, dashboard, Drinks row,
  Plan/Recipes/Meals/Eating out); Log food is a full screen with Search, Scan, Photo, Mine and Quick add; Food detail with the
  Nutrition Facts label; Drinks from the food database with one log flow (`DrinkLogger`); the Swap sheet replaces Plan B.
  Removed: the old Food hub, `Route.PlanB`, `Route.MyFoods`. No schema change beyond `migrateDrinks_2_3` and
  `migrateNutrientsListed_2_3`.
- **Train and Videos (part 3).** Train home with Workout, Exercises and Videos; Programs area; rebuilt active workout (previous
  sets, rest strip, plate line, swap, live PR, minimised session bar); post-workout summary; Videos with the user's YouTube key,
  cache and purge, paste-a-link without a key, the iframe player, and Make this a workout. Weighted muscles use the **fallback**
  (main 1.0, helpers 0.5); there is no `exercise_muscle` table. No schema change beyond `migrateTraining_2_3`.
- **Not built, from the briefs:** micronutrient totals (foods only store macros, fibre, sugar, sodium and saturated fat); a nutrition
  label photo for Open Food Facts; the extra USDA portions in Log food; a separate muscle volume page with a week picker;
  player fullscreen; a "last result" column in Exercises.

**Seen on an emulator** (Android 11 image, software emulation, so slow; see `docs/HANDOFF.md` section 4). Walked: all nine
onboarding steps, the Today tour, Today, Nutrition home, Log food (search, scan permission, photo off state, quick add), Food
detail and logging, the allergen block and "Log anyway" confirmation for a dairy food, Drinks and a drink log, the Swap sheet,
Train (all three sections), the program generator, an empty workout from start to summary, Programs, Progress, You, every
Settings group, Allergens, the weekly check-in and Profile. Fixed from that pass:

- Crash when the Today tour was skipped or finished (`Tour.kt` measured a child that was gone).
- Freeze opening the exercise picker and the Exercises tab: every catalog row was composed inside one list item. They are lazy
  rows now (`exerciseRows`); the photo index loads off the main thread; a catalog id is hashed once.
- Freeze while typing a food search: ranking ran on the main thread and re-tokenised names inside the sort. Allergen matching
  for rows also moved off the main thread.
- Search results stayed scrolled to the bottom when results arrived above the Open Food Facts section.
- Summary and Make-a-workout bottom buttons sat under the system navigation bar.
- lb users got a 44.1 lb bar and kg plates; defaults now follow the weight unit (`PlateDefaults`).
- Next up showed water in ml whatever the unit; Drinks listed Sparkling water before Water; Progress repeated its title and
  had three Log weight buttons; "1 sets"; "1 1 slice"; the generated program's Save button was under a very long preview.

**Not seen anywhere:** Android 12+ behaviour (pill blur, predictive back), a real camera or barcode, YouTube playback and search
(no key, no network from the emulator), AI calls, Health Connect, the watch app, sync, light/dark theme switching, tablets.

## Option A part 1 (branch `worktree-agent-ad4c01822a3a47d62`)

Built from `docs/IA-v5.md` (section 9 wins), `docs/SETTINGS-INVENTORY.md` and `design/mockup-v5/option-a.html`.

- **Shell.** Pill has four tabs (Today, Nutrition, Train, Progress); More is gone. `ShellPolicy` (pure) decides pill and avatar
  visibility, the avatar target (`Route.You`) and back on a root. Every tab root is `TabRootScreen` (top bar with title and avatar,
  bottom padding = `PillHeight` + navigation inset); every pushed screen is `PushedScreen` (`IntakeTopBar` with back arrow, no pill);
  cook mode, active workout and the welcome flow are `FullScreenTask`. The pill hides on pushed screens (`Motion.pillExit`, instant
  with reduce motion). Pushes slide horizontally (300 ms, 250 ms back, underneath screen moves a quarter, no scale, no fade); tabs
  cross-fade 150 ms; reduce motion is a 100 ms cross-fade. The status-bar scrim is gone: the top bar owns the top inset.
  Shared screens are registered once at the root of `IntakeNavHost`; `NavGraphGuardTest` enforces that.
- **Today.** Calorie ring with kcal left, eaten and target; protein, fat and carbs always visible; Log food, Scan and Photo; Next up
  (the active plan's main recipe for this week with Swap and Log it, the coach's one thing, the next workout); Water (drinks with a
  minus and a plus, "Log a different drink"); Weight tile; Meals; the monthly export reminder. Card switches are in Settings.
- **You.** Header (name from `profile_display_name`), read-only Goals and plan with Change per row, Progress photos (timeline, add from
  camera or gallery, Compare with a draggable divider), Help (opens Settings > About and help). Gear opens Settings.
- **Settings.** Home with search plus 12 group pages. The registry (`SettingsRegistry`) holds the 113 inventory controls; `SettingControl`
  renders each by id. Old option UIs were removed from Today, the hydration sheet, Profile, Your plan, the coach gear, the AI screen, Food
  database and Health Connect.
- **Sync and export.** The remote WebDAV, SFTP and SMB backup, its worker, scheduler, transports, UI, jsch and smbj and their ProGuard rules are
  removed; `BackupCredentialStore.clearDestinationSecrets` runs on launch and `nightly-backup` is cancelled. See `docs/SYNC-EXPORT.md`.
- **Merged.** `WeeklyCheckInScreen` (summary, chips, suggestions, Export or Print PDF) replaces the weekly report and the check-in.
  `AllergensScreen` replaces Allergens hub, My allergens and Allergen reference.
- **Schema v3 only.** `migrateProgressPhotos_2_3` adds `progress_photo` (metadata; synced and exported as metadata only, files never).
  `MigrationSchemaTest` has a case. v2 and `MIGRATION_1_2` are untouched.
- **Settings left as storage only (consumer comes later).** `portionUnit` (existing food screens still follow the weight unit),
  `showSodium`, `eatOutRadius`, `onlyWorkouts`, `personalise`, the YouTube key (saved and tested) and `weeklySets` (the Muscle volume view
  is Train's). Jump tests really gate the Progress card.
- **Not verified on a device (no emulator):** every screen, the transitions and predictive back, the pill hide animation, the insets, the
  compare slider, camera and gallery, the Swap dialog, Health Connect switches, key tests, the keyboard on Settings fields.

## Done

- Health Connect sync and recipe import (branch `worktree-agent-ac631d26a40887bae`; `docs/RECIPE-IMPORT.md`).
  Health Connect: reads go through `HealthDataSource` (real: `HealthConnectSource`; tests use a fake). `HealthReader` does the reading with no database: first sync 30 days, later 14 days or the gap since the last sync (max 30), every page followed, each type read only if its own permission is granted, one failing type reported and the rest still read, aggregates that fail fall back to summing records. The screen shows "Last synced", a per-type line ("Steps: 12 of 30 days · Weight: 6 records · Sleep: none (no permission)"), today's steps and sleep, and any failure in words. The line is kept in settings (`healthLastReport`). Records from our own package are skipped only for the types we write (weight, exercise).
  Recipes: notes, prep/cook/total, fibre/sugar/saturated fat/sodium, photo address, ingredient groups, per-line allergen tags, clean-up, and a single primary button. Schema v3 `migrateRecipes_2_3`: `recipe` gains `notes`, `prepMinutes`, `cookMinutes`, `imageUrl`, `fiberPerServing`, `sugarPerServing`, `sodiumMgPerServing`, `saturatedFatPerServing`, `ingredientGroups`; `recipe_ingredient.groupLabel`; `shopping_list_item.note`. New `domain/recipe/RecipeHtmlExtras.kt` (WPRM and Tasty notes and groups), `RecipeDisplay.kt` (times, groups, line tags), `ui/images/RecipePhoto.kt`.

- Exercise diagrams, custom workouts and a plan creator that uses its answers (branch `worktree-agent-adb67bf9ac0ff4da0`;
  docs/EXERCISE-IMAGES.md and docs/WORKOUTS.md): bundled photos (85 of 94 catalog exercises, Free Exercise DB pinned
  commit), a 20-group muscle map with six coarse regions, an exercise sheet from library, picker, builder and active workout,
  custom exercises with own photo and muscle picker, saved workouts (program with `isStandalone`), profile focus muscles, avoid areas
  and weekdays reaching the generator, weekday scheduling for Today and the coach, a starting-weight estimator, a one-line
  reason per exercise and "How this plan was built". Schema: `migrateExercises_2_3` (exercise.photoPath, program.isStandalone,
  program_day.weekday, program_exercise.notes). Seam left for the weighted `exercise_muscle` table (see EXERCISE-IMAGES.md).

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
  `BackupService` (`settings` section, preview line); see `docs/SYNC-EXPORT.md`.

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

Docs: `docs/SPEC.md`, `docs/SPEC-amendments.md`, `docs/SPEC-v3.md`, `docs/SYNC-EXPORT.md`, `docs/AI-COSTS.md`,
`docs/BENCHMARK.md`, `docs/PRIVACY.md`, `server/README.md`.

## AI reliability and the photo estimator (branch `worktree-agent-a9f053dada012173b`)

Done: per-feature timeouts, 429/5xx backoff with Retry-After, lenient reply repair plus one repair retry, plain errors with no
model text, failure kinds on `ai_usage` (schema step `migrateAiFailureKind_2_3`, after `migrateRecipes_2_3`), photo estimator as a
list of foods (untick, rename, resize, one tap to log, units from the weight setting, context note, Cancel), shared
`CameraOwner` (B3), PDF menu import in bounded chunks off the main thread with a size cap and Cancel (B4), benchmark rate-limit
handling, privacy-checked ranked candidates, compact results and a status-bar scrim (B7). See `docs/AI-COSTS.md` and `docs/PRIVACY.md`.

Not verified: no OpenRouter key and no device. No live call was made, the repair prompt and JSON-mode fallback have not been tried
on a real model, the PDF splitter was checked only on the Arby's PDF (classic xref) and synthetic files (including a compressed
object stream), the camera fix and every new screen are unseen, and OpenRouter's real 429 and endpoint payloads are only as the
docs describe them. A phone already on an earlier v3 debug build must be reinstalled (v3 schema gained a column).

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

- Health Connect sync and the Health Connect screen have never run on a device. The fake-client tests prove the reading, paging, permission and mapping logic; they cannot prove what a real Health Connect returns or whether the permission dialog grants what the screen shows. READ_HEALTH_DATA_HISTORY (data older than 30 days before the first grant) and READ_HEALTH_DATA_IN_BACKGROUND are not requested: foreground reads of the last 30 days do not need them.
- Recipe screens (photo, menu, line tags, highlight and scroll, groups in cook mode, editor group field, shopping note) have not been seen on a device, and photos have not been fetched over a real network. Parsing is tested on small hand-written fixtures plus a manual read of the structure of two real pages; the two real pages were not committed.
- Exercise photos, the muscle map, the exercise sheet, the custom exercise form (camera, gallery, tap-the-body picker),
  My workouts, the profile focus/avoid/weekday pickers, the Today rest-day card and "How this plan was built" were never
  seen on a device (no emulator). The map outlines were only checked as a rendered preview, not in Compose. The camera
  FileProvider and permission flow, the fade animation and reduce-motion behaviour, and thumbnail scrolling performance are untested.

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
- No real watch and phone pairing. The Data Layer path has not run on devices. The watch's workout link was fixed and a
  quick remote added (`docs/WEAR.md`): the cause was a missing `preview` build type in `wear/`, so the watch could not pair
  with the owner's Intake Preview phone app. All of it is compiled, linted and unit-tested (`RemoteProtocolTest`, 14 wearprotocol
  tests) only. The pair must be installed as the same variant; `docs/WEAR.md` has the tasks and APK paths.
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
- Nutrition meals card lists only Breakfast, Lunch, Dinner, Snacks (no Drinks row; day totals still include drinks). Denied location on the nearby card shows a note and an Open app settings button. Unit-tested only, not seen on a device.
