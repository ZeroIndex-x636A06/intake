# QA log

One section per QA pass, newest last. A pass is one agent run through part of `tools/qa/CHECKLIST.md` on the seeded
emulator. How to run one: `tools/qa/README.md`. Append only; fix status is updated in place on the finding.

## Format

Each pass starts with a heading and a header block:

```
## Pass <n>: <areas covered>
Date: YYYY-MM-DD · Build: <git short hash> debug · AVD: phone30|phone35 · Seed: tools/qa/seed.sh (date of data)
Covered: <checklist sections and line numbers visited>
Not covered: <what was skipped and why>
Logcat: <crashes, ANRs, StrictMode lines seen, or "clean">
```

Each finding is a block:

```
### Q<pass>-<n> · S<1-4> · <area> · <one-line title>
Status: open | fixed in <hash> | wontfix (<why>) | cannot reproduce
Steps: 1. ... 2. ... 3. ...
Expected: ...
Actual: ...
Screenshot: design/qa/<file>.png
Suspect: <path/File.kt, if obvious; otherwise "unknown">
```

Rules:
- **ID**: `Q<pass>-<n>`, n counts from 1 within the pass and is never reused. A retest of a finding keeps its ID.
- **Severity**
  - **S1**: crash, ANR that is the app's fault, data loss, or an unsafe allergen result (a dairy food shown as
    dairy-free or clear, a warning cleared by a model or an import).
  - **S2**: broken function or wrong numbers (totals that do not add up, wrong units, a button that does nothing).
  - **S3**: layout or copy (clipping, overlap, wrong ripple, bad contrast, stale or inaccurate text).
  - **S4**: polish (spacing, animation feel, nicer empty state).
- **Area**: one of Shell, Today, Nutrition, Drinks, Recipes, Eating out, Allergens, Train, Programs, Progress, You,
  Settings, Backup, Wear, Other. Add the checklist line, for example `Nutrition C.23`.
- **Steps** must be reproducible from a seeded install with no setup beyond what is written.
- **Expected and actual** are one or two lines each; quote the text seen on screen.
- **Screenshot** is a path under `design/qa/`, named `q<pass>-<n>-<slug>.png`. An S1 or S2 without one needs a reason.
- **Suspect** is a hint, not a diagnosis. Say "unknown" if you did not look.
- Say what was seen on the emulator and what has not been seen on a device. Never claim more.

A pass ends with a summary line: counts per severity, and whether a retest is needed after fixes. The loop is test, fix,
retest; stop when a full pass over every area logs no S1, S2 or S3 finding.

---

## Pass 1 — Today, Nutrition, Drinks
Date: 2026-11-14 · Build: 4338a7b preview (com.zeroindex.intake.preview) · AVD: phone30 (zone America/New_York) · Seed: tools/qa/seed.sh adapted to the preview package (data dated 2026-11-14)
Covered: A.2, A.3, A.4, A.5 (Today to launcher), A.8 (partly); B.1, B.2, B.3, B.4 (sums checked against the seed), B.5, B.7, B.9, B.11 (weigh-in not 14 days old on this date, so not exercised), B.12, B.13, B.14, B.15 (override entries from the seed not scrolled to; only my own override), B.19 (future day); C.1, C.2, C.4 (not toggled), C.6 (day arrows), C.8 (no section switch exists), C.9, C.10, C.12, C.13, C.14 (no All/Foods/Drinks chips, Drinks is its own chip), C.15 (Mine), C.19, C.21, C.22 (g only seen), C.23 (ranch, dark chocolate, whey, yogurt, pizza; protein bar not reached: search typing failed), C.24 (flagged: milk chocolate, yogurt, whey, pizza; cheddar and butter not opened), C.29, C.30, C.32, C.33 (latte flagged, not logged), C.34, C.36 to C.38 (mac and cheese per-serving numbers recomputed from the ingredients: 566 kcal, 24 g protein, 30 g fat all agree), C.42, C.44, C.48; I.1 to I.5 partly, I.8 not run, I.9; J.1, J.2, J.14, J.17 on the screens visited.
Not covered: A.1, A.6, A.7, A.9 to A.14 (shell, rotation, font scale, dark theme, session bar, deep links) and J.7 to J.9, J.13: not run, the emulator took 5 to 30 s per action and the pass ran out of budget. B.6, B.8, B.10, B.16 to B.18, B.20 to B.24. C.3, C.5 (the hydration line no longer lives on Nutrition, see R4 item 9), C.7, C.11, C.16 to C.18, C.20, C.25 to C.28, C.31, C.35, C.39 to C.41, C.43, C.45 to C.47, C.49 to C.51; I.6, I.7, I.10 to I.12. Eat-out check: PDF import and Healthier nearby not opened.
Logcat: clean. No FATAL EXCEPTION, no ANR for com.zeroindex.*, no StrictMode lines, empty crash buffer, checked at the end of the run. No key was typed into the app.
Seen on emulator only (Android 11, software rendering, no KVM, 5 to 30 s per action). Not seen on a device: performance, real keyboard behaviour, haptics, and whether the loading flashes in Q1-6 to Q1-8 last as long on a phone. R4 and R5 items checked: R4-6 (recipes in Log food: present, see Q1-22), R4-7 (a drink logged in Drinks did not appear under a meal: tea showed only in the Drinks card and water list), R4-9 (no Drinks on Nutrition main: confirmed), R4-10 (custom drinks present), R5-9 (per-meal screen: present and working, + and - step 10 g), R5-10 (day switching on Nutrition: works, Yesterday label), R5-12 (Drinks simplified: yes), R5-2 (estimates read Check the label: yes, Eating out), R5-13 (see Q1-17).

### Q1-1 · S2 · Allergens / Nutrition C.13, B.15 · "Log anyway" from Food detail records no "Logged despite: dairy" note
Status: Fixed in 3fb7f70 (meal plan allergen sheet and the watch note, on top of 0017495 for Food detail, Log food, recipes, cook mode, drinks, photo estimate and menu items), needs retest
Steps: 1. Today > Log food, search "milk". 2. Tap + on "Milk, canned, condensed, sweetened" (USDA row). 3. Food page shows "Contains milk"; tap "Log anyway..." then "Log it anyway". 4. Back on Today scroll to Dinner.
Expected: Entry carries "Logged despite: dairy" in its name or serving label and still shows the allergen warning (CLAUDE.md rule 2, checklist C.13 and B.15).
Actual: Entry reads "Milk, canned, condensed, sweetened · 38 g" with no marker, no chip, nothing that says it was an override. Same on the edit sheet. Only the swap path (SwapRepository) calls markOverridden; the Log food path does not. The snackbar says only "Logged Milk, canned, condensed, sweetened to dinner".
Screenshot: design/qa/pass1/q1-1-logged-despite-missing.png
Suspect: ui/screens/fooddata/FoodDetailScreen.kt (the "Log anyway" path near line 333) and data/repository/FoodLogRepository.kt markOverridden, which this path never calls

### Q1-2 · S2 · Today B.14 · Deleting a log entry has no Undo
Status: Fixed in 3c62d87, needs retest
Steps: 1. Today, tap an entry (e.g. Apple under Snacks). 2. Edit sheet > "Delete this entry". 3. Screenshot straight away.
Expected: Soft delete, then an "Undo" snackbar that restores the entry; totals follow both ways.
Actual: Entry and its kcal vanish at once (Snacks 283 -> 188 kcal). No snackbar or Undo seen after three deletes (milk, fajita bowls, apple). The Undo that exists in Drinks ("Removed ... Undo") is missing here. TodayViewModel.deleteLog just calls softDelete. Seen on the emulator only; a very short snackbar would still show on an immediate capture.
Screenshot: design/qa/pass1/q1-2-delete-no-undo.png
Suspect: ui/screens/today/TodayViewModel.kt:178 deleteLog, ui/screens/today/TodayScreen.kt:193

### Q1-3 · S2 · Nutrition C.10 / Log food · Typing in the Log food search drops and reorders characters
Status: Fixed in f4d3c5d, needs retest
Steps: 1. Log food > Search. 2. Type "protein" with `adb shell input text protein` (also "ranch").
Expected: Field shows exactly what was typed.
Actual: Field showed "proet" for protein, "ranhc" and "ranhh" for ranch; the search then reports "Nothing matches". "milk", "latte", "wine" came through intact. The text field value is `state.query`, which comes back through a heavy `combine` flow, the usual cause of lost and reordered keystrokes under load. Seen on a slow software-rendered emulator only; not confirmed on a device, but a real phone typing quickly while results rebuild could hit the same path.
Screenshot: design/qa/pass1/q1-3-search-drops-chars.png
Suspect: ui/addfood/LogFoodScreen.kt:403 (TextField bound to state.query), ui/addfood/AddFoodViewModel.kt:364 (uiState combine); keep the typed text in local state

### Q1-4 · S2 · Allergens I.9 / Nutrition C.10 · "No allergens" chip on foods that do contain other allergens (and on foods with no ingredient list)
Status: Fixed in 3fb7f70 (Nutrition trace chips, on top of 0017495), needs retest. Still open: the review-import sheet says "Ingredient check passed" for clear items
Steps: 1. Log food, scroll the default list or open Mine. 2. Read the chip on Egg, whole, cooked; Sourdough bread; Salmon fillet, baked; Peanut butter; and in Drinks on Beer and Wine.
Expected: Chip says what was checked, e.g. "No milk found", or lists the other allergens. Foods with no ingredient list say so (LogFoodScreen.kt:273 comment promises that).
Actual: All of them show a green tick "No allergens": egg (contains egg), sourdough (wheat), salmon (fish), peanut butter (peanuts), beer (barley), wine. Apple and Banana, which have no ingredient list at all, also read "No allergens". To a person who avoids only dairy it reads as an all-clear for the food, and a literal reading is false. Wine in particular can carry milk or egg fining agents.
Screenshot: design/qa/pass1/q1-4-no-allergens-chip.png (Egg, Sourdough); Drinks: design/qa/pass1/q1-15-drinks-my-drinks.png (Beer)
Suspect: ui/allergen/AllergenBanner.kt:117-133 chip wording; ui/addfood/LogFoodScreen.kt:273

### Q1-5 · S2 · Recipes C.36 / I.4 · Recipes list shows no allergen status
Status: Fixed in 0017495 (recipe list chips) and f4d3c5d (Checking placeholder), needs retest
Steps: 1. Nutrition > More > Recipes. 2. Look at the five rows under My recipes.
Expected: Each row has an allergen chip: fajita bowls and salmon clear, mac and cheese and turkey chili contains, grandma's soup unknown or "not checked" (C.36).
Actual: Rows show only "566 kcal · 24 g protein · per serving" (soup: "4 servings"). Baked mac and cheese, a dairy recipe for a strict dairy user, looks identical to the clear ones. The chips exist elsewhere (recent recipes in Log food, recipe detail), so the list is inconsistent.
Screenshot: design/qa/pass1/q1-5-recipes-no-chips.png
Suspect: ui/screens/plan/ (recipes hub list), ui/addfood/RecipeRows.kt has the chip that the list lacks

### Q1-6 · S3 · Nutrition C.10 / I.1 · Dairy search results show with no chip for several seconds, then chips pop in
Status: Fixed in f4d3c5d (Checking... chip until the verdict arrives; Mine is scanned whole in the merge), needs retest
Steps: 1. Log food > Search "milk". 2. Screenshot at once, then 10 s later.
Expected: A row never appears without its allergen verdict, or shows a "checking" state.
Actual: First frame: Milk and cereal bar, Milk buttermilk dried, Condensed milk, Dry nonfat milk, all USDA dairy, are plain rows with no chip. About 10 s later each gets a red "Contains milk" chip and the list reflows. Tapping + in that window still opens the blocked Food page (I checked after), so nothing is added silently; the rows look safe until the chips arrive. Slow emulator, but the gap is real: verdicts come from a second flow (rowAllergens) after the rows.
Screenshot: design/qa/pass1/q1-6-dairy-rows-no-chip-yet.png
Suspect: ui/addfood/AddFoodViewModel.kt:335 rowAllergens

### Q1-7 · S3 · Shell J.11 · False empty and error copy while data loads
Status: Fixed in f4d3c5d, needs retest
Steps: 1. Open Nutrition > More; Log food; Drinks; a logged food's info icon on the meal screen. 2. Screenshot within 1 to 5 s.
Expected: Spinner or blank until loaded.
Actual: More says "No recipes yet", "No meal templates yet", "No saved options yet" for about 10 s (the seed has 5, 2 and 2). Log food says "Nothing logged yet ... Foods you log often land here". Drinks says "No drinks yet" and "Nothing logged yet today". The meal screen's info icon shows "This food is not on this phone." with a Back button before the food loads. All turn into real content afterwards. Slow emulator makes it long, but the first frame of each is wrong text.
Screenshot: design/qa/pass1/q1-7-false-empty-loading.png, design/qa/pass1/q1-7b-food-not-on-phone.png
Suspect: ui/screens/nutrition/NutritionMoreScreen.kt, ui/addfood/LogFoodScreen.kt, ui/screens/drinks/DrinksScreen.kt:242,308, ui/screens/fooddata/FoodDetailScreen.kt:126 (null food treated as missing before first emit)

### Q1-8 · S3 · Nutrition C.6 · Nutrition opens with "Thu, Jan 1" and "Reading today...", then the layout jumps
Status: Fixed in f4d3c5d, needs retest
Steps: 1. Tap the Nutrition tab (also after relaunch).
Expected: Real date from the first frame; no layout shift.
Actual: Day pill reads "Today / Thu, Jan 1" (epoch 0) with a "Reading today..." box, no allergen strip yet; seconds later it becomes "Sat, Nov 14" and the strip, tip card and ring push everything down.
Screenshot: design/qa/pass1/q1-8-jan1-placeholder.png
Suspect: ui/screens/nutrition/NutritionHomeScreen.kt:216, NutritionHomeViewModel.kt initial state

### Q1-9 · S3 · Today B.12 / Nutrition · Macro percentage labels overlap and clip on small segments
Status: Fixed in 3c62d87, needs retest (one labelled line "P x%  C y%  F z%" kept in the merge at eed25fb; per-segment labels dropped)
Steps: 1. Today, scroll to the Drinks card (coffee). 2. Open any low-protein food (Banana, Wine) and read the bar under the macro chips.
Expected: Labels for each segment readable.
Actual: Drinks card: "2" is cut off before "98%". Banana: "4% 93%" with the 4% clipped and the fat label cut to "3". Wine: "4% 96%" clipped the same way. Labels are placed by segment start with no minimum spacing.
Screenshot: design/qa/pass1/q1-9-pct-label-clipped.png
Suspect: ui/components (macro split bar used by MealSlotCard.kt and the food page)

### Q1-10 · S3 · Today B.1 / B.11 · Next arrow on today goes to a future day, which then says "today" and shows the weight as current
Status: Fixed in 3c62d87, needs retest
Steps: 1. Today, tap the right arrow on Sat Nov 14. 2. Scroll to the top.
Expected: Next is disabled or inert on today (B.1); at minimum copy fits the day.
Actual: Moves to "Sun, Nov 15" (no "Tomorrow" label). Water says "Nothing logged yet today." and the ring says "2,260 kcal left". Weight tile shows "192.0 lb -1.9 lb this week" for a day that has not happened, with no age of the last weigh-in. Nutrition's day pill has the Next arrow greyed on today, so the two tabs disagree.
Screenshot: design/qa/pass1/q1-10-future-day.png
Suspect: ui/screens/today/TodayScreen.kt header arrows, ui/screens/today/components/HydrationCard.kt:81

### Q1-11 · S3 · Nutrition C.2 / Drinks · Meals list on Nutrition and the per-meal screens leave out drinks, so rows do not add to the total
Status: Fixed in eed25fb, needs retest
Steps: 1. Nutrition tab. 2. Add Breakfast 596 + Lunch 572 + Dinner 519 + Snacks 283 = 1,970. Compare with "2024 eaten" in the ring (before I deleted anything).
Expected: Every logged kcal is visible somewhere on the screen, or the drinks have a row.
Actual: The coffee with sugar (54 kcal) is counted in the ring but there is no Drinks row on Nutrition and the per-meal screen does not list it. It is visible only on Today. (After my deletions: 1,357 eaten vs 1,303 in meal rows.)
Screenshot: design/qa/pass1/q1-11-meals-miss-drinks.png
Suspect: ui/screens/nutrition/NutritionHomeScreen.kt meal rows, NutritionMealScreen.kt

### Q1-12 · S3 · Nutrition C.8 · "More: plan, recipes, eating out, nutrients" names a nutrients entry that is not in More
Status: Fixed in eed25fb, needs retest
Steps: 1. Nutrition, scroll to the bottom row. 2. Tap it.
Expected: More lists what the row says.
Actual: More has Meal plan, Recipes, Meal templates, Shopping list, Eating out. No nutrients entry (the "More nutrients" expander is on the dashboard card).
Screenshot: design/qa/pass1/q1-7-false-empty-loading.png (the More screen)
Suspect: ui/screens/nutrition/NutritionHomeScreen.kt:182

### Q1-13 · S3 · Recipes C.37 · Recipe with no nutrition has a greyed "Log a serving" and no reason
Status: Fixed in eed25fb, needs retest
Steps: 1. More > Recipes > Grandma's vegetable soup.
Expected: Button either asks for macros or logs as "not listed" (C.37), or says why it is off.
Actual: "Log a serving" is disabled grey. The only hint is the grey line above, "Nutrition: not listed. The page gave none and the ingredients could not be matched." No way to add macros from here.
Screenshot: design/qa/pass1/q1-13-log-serving-disabled.png
Suspect: ui/screens/plan/RecipeDetailScreen.kt

### Q1-14 · S3 · Allergens C.23 · "Dark chocolate 70%" ("may contain milk") reads "Contains milk"
Status: Partly fixed: 0017495 words it as "(trace)" but it stays a hard red warning; owner decision pending
Steps: 1. Log food, open the default list, find Dark chocolate 70%.
Expected: A may-contain verdict, worded as a trace (C.23).
Actual: Red chip "Contains milk", the same wording as Milk chocolate bar. Safe direction, but it overstates and a person cannot tell trace from ingredient.
Screenshot: design/qa/pass1/q1-4-no-allergens-chip.png (Dark chocolate row)
Suspect: domain/allergen engine chip text, ui/allergen/AllergenBanner.kt

### Q1-15 · S3 · Drinks C.32 / I.3 · My drinks cards omit the oat-milk "Check the label" note that Mine shows
Status: Fixed in 0017495 (My drinks cards show the verdict chips), needs retest
Steps: 1. Drinks page, look at "Black tea + 2 tbsp o...". 2. Log food > Mine, same drink.
Expected: Same note in both.
Actual: Mine shows "Check the label: milk". The Drinks card shows only "9.1 fl oz · 17 kcal"; one tap logs it with no note. Names are also cut ("Coffee, black + 3 ts..."). Logging it showed only "Added 9.1 fl oz Black tea + 2 tbsp oat milk".
Screenshot: design/qa/pass1/q1-15-drinks-my-drinks.png
Suspect: ui/screens/drinks/DrinksScreen.kt my-drinks cards

### Q1-16 · S3 · Eating out C.44 · Saved eat-out options are dead cards
Status: Fixed in eed25fb, needs retest
Steps: 1. More > Eating out. 2. Tap Burrito Barn (invented).
Expected: Open it, log it, or swap it; "Choose anyway" path (C.44).
Actual: Nothing happens on tap. The only control is a bin icon (no confirm seen). A saved option cannot be logged from here. They show "Check the label" for estimates, which is right.
Screenshot: design/qa/pass1/q1-16-eatout-dead-cards.png
Suspect: ui/screens/hub/ (eating out screen, saved options list)

### Q1-17 · S4 · Nutrition C.21 · Food page: top figure and printed label differ (188 vs 190, 105 vs 110, 152 vs 150 kcal)
Status: Fixed in eed25fb, needs retest
Steps: 1. Open Peanut butter, Banana or Rolled oats from Log food. 2. Compare the kcal chip with the Nutrition Facts Calories.
Expected: R5 item 13 said the 65 vs 70 kcal mismatch was wrong.
Actual: A note under the label now explains FDA-style rounding, but the two numbers still sit on one screen and differ. A rounded figure on a food that is logged at the exact figure may still read as an error.
Screenshot: design/qa/pass1/q1-17-calories-top-vs-label.png
Suspect: ui/screens/fooddata/FoodDetailScreen.kt, NutritionLabel

### Q1-18 · S4 · Nutrition · First-run tip shown to a user with three weeks of data, and it comes back
Status: Fixed in 3fb7f70 (Train and Progress tips check history, on top of 3c62d87), needs retest
Steps: 1. Nutrition tab. 2. Leave and return after a relaunch.
Expected: A tip only for new users, and it stays dismissed after Got it.
Actual: "Log what you eat, plan meals and check allergens here. Start by logging your next meal." shows above the ring, with Got it and Log food; seen again on later visits (I did not tap Got it).
Screenshot: design/qa/pass1/q1-18-first-run-tip.png
Suspect: ui/onboarding/TabTip.kt:25

### Q1-19 · S4 · Nutrition J.18 · Number formatting differs: "2393 eaten" next to "2,260 kcal"
Status: Fixed in 3c62d87, needs retest
Steps: 1. Nutrition, go back one day (Fri Nov 13).
Expected: "2,393" as on Today.
Actual: Nutrition ring text "2393 eaten" and "1357 eaten"; Today shows "2,393".
Screenshot: design/qa/pass1/q1-19-eaten-no-comma.png
Suspect: ui/screens/nutrition/NutritionHomeScreen.kt

### Q1-20 · S4 · Today B.12 · Meal cards are tall and the macro percentages are unlabelled
Status: Fixed in 3c62d87 and eed25fb (tighter cards, labelled P/C/F line), needs retest
Steps: 1. Today, scroll to Meals.
Expected: Four compact rows (B.12).
Actual: Five full cards (the fifth is Drinks), each with a bar, three unnamed percentages, a "+ Log food" row and a separate "Save as meal template" row; large gaps between entries; one meal fills most of a screen. Percentages do not say protein, carbs, fat.
Screenshot: design/qa/pass1/q1-20-today-meal-cards.png
Suspect: ui/screens/today/components/MealSlotCard.kt

### Q1-21 · S4 · Nutrition C.42 · Meal templates: heading repeated three times, no allergen verdict, log note not checked
Status: Fixed in eed25fb (one heading, N saved note) and 0017495 (template verdict chips), needs retest
Steps: 1. More > Meal templates. 2. ... on Post-workout shake > log to Snacks.
Expected: One title; a verdict before one-tap logging; note for plant milk.
Actual: "Meal templates" is the bar title, the page title and the section title. The rows have no chip. Logging the shake (it holds almond milk) says "Logged Post-workout shake to snack" (lowercase, singular) with no check-the-label note.
Screenshot: design/qa/pass1/q1-21-templates.png
Suspect: ui/screens/templates/

### Q1-22 · S4 · Nutrition C.15 · Log food default list: empty "RECENT" header, and saved recipes missing
Status: Fixed in f4d3c5d, needs retest
Steps: 1. Log food, wait for the list.
Expected: Recent foods and recipes, then everything saved (R5 item 11).
Actual: For several seconds "RECENT" is followed directly by "RECENT RECIPES". When loaded, only 3 recipes appear; Baked mac and cheese and Grandma's soup are absent, so a saved recipe cannot be found without searching.
Screenshot: design/qa/pass1/q1-22-recent-recipes.png
Suspect: ui/addfood/RecipeSearchViewModel.kt, ui/addfood/LogFoodScreen.kt

### Q1-23 · S4 · Nutrition C.18 · "Open Food Facts could not be reached. The sources above still work." with nothing above
Status: Fixed in 3c62d87, needs retest
Steps: 1. Log food, type a word with no local match (offline).
Expected: Plain message that fits what is on screen.
Actual: Heading "PACKAGED FOODS (OPEN FOOD FACTS)", then "The sources above still work", then "Nothing matches", with no sources above.
Screenshot: design/qa/pass1/q1-3-search-drops-chars.png
Suspect: ui/addfood/LogFoodScreen.kt

### Q1-24 · S4 · Allergens C.12 · Warning sheet copy says "matched an ingredient" when the match was the name
Status: Fixed in 0017495 (warning sheet says where the match was found), needs retest
Steps: 1. Log anyway on a USDA milk row (see Q1-1).
Expected: Copy fits the evidence.
Actual: "Your allergen profile matched an ingredient", but the evidence card says "milk found in the name". The USDA row has no ingredient list.
Screenshot: design/qa/pass1/q1-24-sheet-copy.png
Suspect: ui/allergen/AllergenWarningSheet.kt

### Q1-25 · S4 · Nutrition C.15 · Wrong food icons (cookie for sweet potato)
Status: Fixed in 3fb7f70 (whey powder, SUPPLEMENT icon, on top of 3c62d87), needs retest
Steps: 1. Log food list, find Sweet potato, baked.
Expected: Icon fits the food (R5 item 13 complained of a cookie on tea).
Actual: Sweet potato has a cookie icon; Turkey chili has a burger; Whey protein powder a fork and knife.
Screenshot: design/qa/pass1/q1-25-cookie-icon.png
Suspect: ui/addfood/ food icon mapping

### Q1-26 · S4 · Eating out C.44 · Menu items judged clear show no chip; the menu info card is a centred paragraph
Status: Fixed in 0017495 (menu item chips) and eed25fb (intro left-aligned, shorter), needs retest
Steps: 1. Eating out > Menu import > Sunrise Diner (invented).
Expected: A positive verdict or "checked" mark per item.
Actual: Garden salad, Chocolate shake, Classic cheeseburger and Pancake stack show "Contains milk" (correct against the ingredients). Black bean burrito, Fish and chips, Grilled chicken plate and Oatmeal show nothing, so clear and unchecked look alike. The intro card on Menu import is a long centred paragraph.
Screenshot: design/qa/pass1/q1-26-menu-no-chip.png
Suspect: ui/screens/menu/


Summary for Pass 1: 26 findings. S1: 0. S2: 5 (Q1-1 to Q1-5). S3: 11 (Q1-6 to Q1-16). S4: 10 (Q1-17 to Q1-26). A retest is needed after fixes, and the allergen block (CHECKLIST I) must be re-run after any change to Q1-1, Q1-4 or Q1-5. Most of the checklist for these areas is unwalked (see Not covered), so a second pass over A, B and C is needed for the stop condition.

Pass 1 fixes merged (0017495, f4d3c5d, 3c62d87, eed25fb). Known leftovers, still open: Train and Progress first-run tips have no history check (Q1-18); whey powder has a generic icon (Q1-25); wear and meal-plan logging have no override path (Q1-1); the review-import sheet still says "Ingredient check passed" for clear items (Q1-4); NutritionMeal and NutritionHome chips lack the trace wording (Q1-4); Q1-14 is a hard red warning with "(trace)" wording, owner decision pending. Everything marked fixed needs a retest on the emulator and a device.


## Pass 2 — Recipes, templates, plan, menus
Date: 2026-11-14 (device date, zone America/New_York) · Build: app/ source at fae542e, preview APK built before the Pass 1 fixes (com.zeroindex.intake.preview) · AVD: phone30 · Seed: tools/qa/seed.sh adapted to the preview package (data dated 2026-11-14)
Covered: Recipes list, detail (clear, dairy, estimated, soup), Log a serving, Cook mode (servings stepper, tick, Done cooking, allergen gate), recipe editor (paste list, live allergen verdict, estimate from ingredients, save), recipe import offline, Recipe Hunter no-key state, shopping list (3 recipes, amounts recomputed), meal templates (list, editor, add food, log with a dairy item), meal plan (empty state, make a plan, add a dairy recipe, Log this meal, Cook this meal), Eating out (saved options, Healthier options, Healthier nearby with location denied), Menu import (saved source, text with AI off, PDF on API 30), Allergens screen (strictness Avoid and back, keyword list, add keyword, turn off a built-in); C.19 (typed food blocked), C.39, C.40, C.41, C.43, C.45 (API 30 behaviour only), C.47, C.48 to C.50; C.16 and C.17 (Quick add logged-blocked, Scan permission denied, typed barcode offline), C.18, C.11 (partly), C.28 (menu has no contribute entry on a USDA food); I.4, I.5, I.8, I.9, I.10, I.11; J.1, J.2, J.6, J.10, J.14 on the screens visited.
Not covered: C.3, C.7, C.20, C.25 to C.27, C.31, C.35, C.46 (swap sheet), C.51 (a custom keyword was added; no food carrying it was created), I.6 (menu items cannot be edited from the list), I.7 (needs Open Food Facts, no network), I.12 (no watch). The staged "To review" import flow, the nutrition-menu request after an allergen-only import with Skip, Recipe Hunter results ("Could be adapted" layout, removed chips, thumbnails, Recent searches) and Healthier nearby results: all need an AI key or network; only the no-key and no-location states could be seen. PDF text extraction is not testable on API 30. J.7 to J.9, J.13 not run. Pass 1 findings were not re-logged; several were fixed after the APK was built (see below), so they were not retested here.
Logcat: no FATAL EXCEPTION from com.zeroindex.*. One FATAL EXCEPTION in com.android.documentsui (the system file picker) right after a file was picked: emulator, not the app. One ANR for com.zeroindex.intake.preview (22:34, "Input dispatching timed out ... FocusEvent") while the Food safety sheet was open: the saved trace shows the app's main thread idle in the message loop, system_server at 52% CPU, one low-memory process kill earlier in the run. Treated as the emulator, not a finding. No StrictMode lines. Typed text in several fields lost or reordered characters ("Test pag", "oa t", "dry pa"); the same as Q1-3 on this older build, and Q1-3 was fixed afterwards.
Method note: HEAD ce8638f contains the Pass 1 fixes (cbc5133 and others) that this APK does not. Every finding below was checked against the HEAD source where it matters; where HEAD may already differ it says so. The APK was not rebuilt because a Gradle build starves the emulator for the rest of the pass.
Pass 1 checks verified in this pass: R4-11 (templates need no plan: yes, "No meal plan needed"), R4-14 (Cook button next to Log: yes), R4-6 (recipes in Log food: yes), R5-3 (could not be exercised), R5-5 and R4-12/13/15 (need a key; the no-key Recipe Hunter says so plainly and offers "Have a link? Import it with no AI"), R5-11 (recipe and food lookup in the template editor lists recents then all saved: present, see Q2-14). Q1-13 (greyed Log a serving on Grandma's soup) did not reproduce: here the soup shows "~60 kcal" from its ingredients and Log a serving is enabled.
Seen on emulator only (Android 11, software rendering, no KVM). Not seen on a device: performance, real keyboard, whether Cook mode keeps the screen awake (the chip says "Screen stays awake", no wake lock was visible in `dumpsys power`; not conclusive), haptics, Android 15 PDF text.

### Q2-1 · S1 · Recipes C.36-C.38, I.4, I.5 · "Log a serving" on a recipe page logs a dairy recipe with no warning and no override note
Status: Fixed in bc0c873 (AllergenLogGate on the recipe page: scan, warning sheet, "Logged despite" note; LogPathAuditTest pins every log path), and 795a8d4 (servings and Undo on both the clean and "Log it anyway" paths), needs retest
Steps: 1. Nutrition > More > Recipes > Baked mac and cheese (pill reads "Contains an allergen", three lines tagged dairy). 2. Tap "Log a serving". 3. Tap Snacks. 4. Go to Today and scroll to Snacks.
Expected: The Food safety sheet (as Cook mode's "Done cooking" > Log a serving > Dinner shows, and as Meal plan's "Log this meal" shows: "Not logged: mac and cheese, butter, cheddar, cheese, milk matches your allergen profile"), and "Logged despite: dairy" if the person chooses to go on.
Actual: The slot sheet closes, the button turns to "Logged · log another", and Today shows "Baked mac and cheese 566 kcal · 24 g protein" under Snacks, nothing marking it. A strict-dairy user logs a cheese dish in two taps. The recipe editor's own copy says the opposite: "You can still save it, but it will not log without a warning." Three paths to the same action behave three ways (recipe page: silent; Cook mode: sheet; Plan: refused).
Screenshot: design/qa/pass2/q2-1-mac-logged-snacks.png, design/qa/pass2/q2-1b-recipe-log-no-warning.png, design/qa/pass2/q2-1c-cook-mode-warns.png
Suspect: ui/screens/plan/RecipesViewModel.kt:136 logServing, ui/screens/plan/RecipeDetailScreen.kt:298; reuse the pending-scan flow in CookModeViewModel.confirmPending

### Q2-2 · S2 · Recipes C.40 / I.9 · A new recipe with no ingredients says "Passed allergen check"
Status: Fixed in bc0c873 (empty recipe wording), needs retest
Steps: 1. Recipes > Add a recipe. 2. Look at the allergen line before typing anything.
Expected: No clear verdict for an empty recipe ("nothing to check yet"). Nothing says it passed unless an ingredient list was checked (I.9).
Actual: Below "No macros yet" the green chip reads "Passed allergen check" with "None yet." ingredients. It flips to "Contains cheddar, milk" once lines are added, which is right, but the empty state is a false all-clear.
Screenshot: design/qa/pass2/q2-2-empty-recipe-passed.png
Suspect: ui/screens/recipe/RecipeEditorScreen.kt (verdict row when the draft has no lines)

### Q2-3 · S3 · Recipes C.38 / C.39 · Recipe page has no servings stepper and "Log a serving" takes no amount, no undo, and offers a Drinks slot
Status: Fixed in 795a8d4 (servings stepper, Undo, Drinks slot removed), needs retest
Steps: 1. Recipes > Chicken fajita bowls. 2. Look for a servings control; open the three-dot menu (Add to this week, Edit recipe, Delete recipe). 3. Log a serving.
Expected: C.38 describes a servings stepper that rescales ingredients and per-serving numbers; a way to log more or less than one serving; an Undo after logging.
Actual: The page shows "4 servings · Prep 15 ..." as text only; the stepper exists only inside Cook mode (4 to 6 servings scaled 480/640/260/240/28 g to 720/960/390/360/42 g, correct). Log a serving always logs exactly 1 ("1 serving" label). The slot list is Breakfast, Lunch, Dinner, Snacks, Drinks, so a recipe can be filed under Drinks. After logging there is only the button text change, no snackbar, no Undo. Done cooking offers "Log a serving of ...? You can change the amount later on Today" even when a serving was just logged from the page.
Screenshot: design/qa/pass2/q2-3-log-serving-slots.png
Suspect: ui/screens/plan/RecipeDetailScreen.kt, ui/screens/plan/PlanSheets.kt (slot picker)

### Q2-4 · S3 · Recipes C.39 / J.6 · Cook mode: clipped ingredient list, oversized close button, "Swipe left or right" shown for a one-step recipe
Status: Fixed in 795a8d4 (cook mode list, close icon in a 72dp touch target, one-step hint), needs retest
Steps: 1. Recipes > Chicken fajita bowls > Cook. 2. Screenshot with ingredients showing.
Expected: Nothing cut mid-row; a normal-size close control; navigation hints only when there is more than one step.
Actual: The ingredient box ends in the middle of the last row ("28 g Olive oil" cut through by the next header) with no scroll cue. The × is drawn as a 100 px rounded square box, far larger than the controls beside it. "STEP 1 OF 1" has a back arrow button and the hint "Swipe left or right to move between steps" below the step text, partly under the bottom buttons. The steps text for this recipe is one block, so there is nothing to swipe to.
Screenshot: design/qa/pass2/q2-4-cook-layout.png
Suspect: ui/screens/plan/CookModeScreen.kt

### Q2-5 · S3 · Recipes C.38 · "Estimated from ingredients" picks wrong foods and weights; the totals add up but the inputs are off
Status: Fixed in eb928a4 (ingredient matching), needs retest
Steps: 1. Recipes > Grandma's vegetable soup > "Show how each line was matched". 2. Add a recipe with "200 g pasta", "100 g cheddar cheese", "1 cup milk" via Paste a list, save, open it, "Show how each line was matched".
Expected: "milk" resolves to ordinary milk; a "can" of tomatoes is a can's weight; vegetable soup does not use beef stock.
Actual: Sums are right (soup: 50+44+22+125 = 241 kcal / 4 = 60 shown as "~60"; pasta bake: 742+408+265 = 1,415 / 4 = 354 shown as "~354"). The matches are not: "1 cup milk" = "Milk, sheep, fluid" 245 g 265 kcal (whole cow milk would be about 150 kcal, so about +29 kcal per serving); "1 can diced tomatoes" = 123 g, 22 kcal (a can is about 400 g); "4 cups stock" in a vegetable soup = "Soup, stock, beef, home-prepared" 125 kcal; "3 stalks celery" is "no weight known for stalk" and left out, so the estimate is low. The card says "4 of 6 ingredient lines" and "Not the site's numbers", which is honest, but a person will trust ~60 kcal.
Screenshot: design/qa/pass2/q2-5-estimate-matches.png, design/qa/pass2/q2-5b-soup-matches.png
Suspect: domain/recipe (RecipeNutritionEstimator food ranking and unit weights)

### Q2-6 · S3 · Recipes C.43 · Shopping list adds up correctly but lists cooked weights as things to buy, and the aisles are inconsistent
Status: Fixed in eb928a4 (shopping list forms and Dairy aisle), needs retest. Old saved shopping rows keep their old aisles
Steps: 1. More > Shopping list. 2. Pick Baked mac and cheese, Chicken fajita bowls and Salmon, sweet potato and broccoli (x1 each). 3. Scroll to the amounts.
Expected: Amounts a person can buy; one sensible section per kind of item.
Actual: Arithmetic checked and right: chicken breast 480 g = 1.06 lb, spaghetti 840 g = 1.85 lb, black beans 260 g = 9.2 oz, butter 56 g = 2 oz, cheddar 340 g = 12 oz, milk 488 g = 1.08 lb, rice 640 g = 1.41 lb. But the recipes' lines are cooked weights ("640 g White rice, cooked", "840 g Spaghetti, cooked", "480 g Chicken breast, cooked"); the list drops "cooked", so it says "White rice 1.41 lb" and "Spaghetti 1.85 lb" (dry rice and pasta would be about a third of that). Milk is a weight (1.08 lb), not a volume. Black beans, butter, cheddar and milk are all under "OTHER", while rice is under "PANTRY STAPLES" and spaghetti under "PANTRY & GRAINS". Dairy items for a strict-dairy user carry no marker (mac and cheese is pickable). The 2 servings on "Salmon..." matches its recipe.
Screenshot: design/qa/pass2/q2-6-shopping-list.png
Suspect: ui/screens/shopping/ShoppingListViewModel.kt and the aggregation in domain/ (state words dropped; category map)

### Q2-7 · S3 · Eating out · Healthier nearby with location denied says "use a zip code instead" and there is no zip field
Status: Fixed in 6976495 (zip search), needs retest. Zip lookup not verified on a real network
Steps: 1. More > Eating out, scroll to "Find healthier options nearby". 2. "Allow location once and search" > Deny.
Expected: Either a zip code field or no mention of one.
Actual: Red text "Location is off for Intake. Allow it, or use a zip code instead." with buttons "Allow location once and search", "Open app settings", "Change how far in Settings (10 min drive)". No zip field. The zip option exists only in the Swap sheet and only with an AI key ("Use a zip code instead (AI search)"). HealthierNearby.kt also sets "Could not get a location fix. Try a zip code instead." The no-location state itself is clear and does not crash.
Screenshot: design/qa/pass2/q2-7-nearby-zip-copy.png
Suspect: ui/screens/swap/SwapSheet.kt:401 and ui/screens/hub/HealthierNearby.kt:136,167 (shared host)

### Q2-8 · S3 · Eating out C.45 · Menu import with AI off: typing is accepted then refused, and every failed import leaves a source behind
Status: Fixed in 6976495 (import sources kept only on success), needs retest
Steps: 1. Eating out > Menu import > Add source > restaurant "Test Cafe" > Paste text, enter three item names > Import menu. 2. Add source > Pick PDF > mcdonalds-ireland-allergen-chart.pdf (picker Search route) > Import menu.
Expected: The Paste text tab says it needs AI before the person types; a failed import either leaves nothing or a row that can be retried or removed from the list.
Actual: Text: red "Import failed: AI is off. Turn on "Use AI features" in Settings → AI to use this." and a new saved source "Test Cafe · Pasted text · Never imported · 0 items · Never run". PDF on API 30 (PdfRenderer text unavailable): "No table read on this phone. Reading a PDF on the phone needs Android 15 or later. Nothing was sent anywhere. AI is off or has no key: turn on "Use AI features" and add a key in Settings → AI to use it." with a disabled "Read it with AI" and "Not now"; the source is saved as "Irish McD · PDF · local file · 14 Nov · 0 items · Failed". The explanation is good and honest. The sources stay as empty rows. PDF text extraction on API 35 not tested.
Screenshot: design/qa/pass2/q2-8-text-import-ai-off.png, design/qa/pass2/q2-8b-pdf-api30.png
Suspect: ui/screens/menu/MenuSourcesViewModel.kt (source row is written before the run), MenuSourcesScreen.kt add sheet

### Q2-9 · S3 · Recipes C.42 / I.5 · Meal template editor and list show no allergen verdict; the block comes only at Log, with no reason or way on
Status: Fixed in bc0c873 (template sheet), needs retest. The template editor food picker rows still have no allergen chip
Steps: 1. Meal templates > Post-workout shake > Edit > Add a food or recipe > Cheddar cheese > Save changes. 2. Template row: "451 kcal · 18 g protein · No NFC tag". 3. More > Log now > Breakfast.
Expected: Dairy shown where it is added and on the row; a log of a dairy template blocked or warned (I.5).
Actual: The editor lists Cheddar cheese with "403 kcal per 100 g" and Total 451 kcal (338 + 113, right) and no chip; the saved row has no chip. Log now is blocked: "Not logged: Cheddar cheese now matches your allergen profile", which is safe, but there is no "Log it anyway" path and no reason beyond the food name. The add picker's first row is Baked mac and cheese and it has no chip either.
Screenshot: design/qa/pass2/q2-9-template-blocked.png, design/qa/pass2/q2-13-template-editor.png
Suspect: ui/screens/templates/MealTemplateEditorScreen.kt, MealTemplatesScreen.kt

### Q2-10 · S3 · Eating out · "Healthier options" shows items the app itself calls "Not a standout choice", and published menu macros are labelled "estimate"
Status: Fixed in 6976495 (healthier list filtering, published labels), needs retest
Steps: 1. More > Eating out (wait for the list). 2. Read the cards under "From your menus and saved options".
Expected: A list headed "Healthier options" holds options that rank well; macros from a menu's own published numbers are not called an estimate.
Actual: Fish and chips (880 kcal), Black bean burrito and Oatmeal with berries sit in the list with green text "Not a standout choice". Every card ends its numbers with "estimate", including the Sunrise Diner items whose seed rows have macrosPublished = true. Items whose ingredients were read show "No allergens found" while the same items on Menu import show no chip at all (Q1-26), so the two screens disagree about the same item. The saved options show "Check the label" (right for estimates). Also, the first frame of this screen has no Healthier list and the nearby card is at y 890; the list appears a few seconds later and moves the card down 600 px, so a tap aimed at the card can land elsewhere.
Screenshot: design/qa/pass2/q2-10-healthier-list.png
Suspect: ui/screens/hub/HealthierNearby.kt, domain/swap/NearbyRanking.kt:119, ui/screens/hub/FoodHubScreens.kt:210

### Q2-11 · S4 · Recipes C.36 / C.37 · Recipes list shows no kcal for recipes whose numbers are estimated
Status: Fixed in 795a8d4 (~kcal on the recipe list), needs retest
Steps: 1. More > Recipes after the soup page has shown "~60 kcal" (also my new "Test pag", "~354").
Expected: The list row shows the estimate with a ~ like the detail page.
Actual: The row says only "4 servings", the same as a recipe with no numbers; the page shows "~60 kcal, ~6 g protein".
Screenshot: none (text only, row seen in dump: "Grandma's vegetable soup / 4 servings")
Suspect: ui/screens/hub or plan recipe list row (uses stored kcalPerServing only)

### Q2-12 · S4 · Recipes · "Show the 1 flagged ingredient lines"
Status: Fixed in 795a8d4 (plurals), needs retest
Steps: 1. Recipes > Turkey chili with sour cream, read the chip's accessibility text (and the card says "1 line is tagged below").
Expected: Singular for one line.
Actual: Chip description "Show the 1 flagged ingredient lines".
Screenshot: design/qa/pass2/q2-12-chili-plural.png
Suspect: ui/screens/plan/RecipeDetailScreen.kt:201

### Q2-13 · S4 · Recipes / Meal plan / Import · Titles repeated and import copy says "Paste a recipe page" for an address field
Status: Fixed in 795a8d4 (copy and titles), needs retest. The plan slot is time-of-day based because plans store no slot
Steps: 1. Recipes > Import from a link. 2. Meal templates > Edit. 3. More > Meal plan.
Expected: One title per screen; copy that matches the control.
Actual: Bar "Import a recipe" over heading "Import from a link", whose copy says "Paste a recipe page" above a field labelled "Recipe web address" (the page cannot be pasted, only fetched). Bar "Meal template" over "Edit meal template". Bar "Meal plans" over "Your meal plan" over "Plan1". The plan screen's footer "Can't eat what I cooked? Find a safer option nearby" uses "safer" (I.9). The offline failure itself is plain: "Could not reach that page. Check the address and your connection." (C.41 passes).
Screenshot: design/qa/pass2/q2-13-template-editor.png
Suspect: ui/screens/recipe/RecipeImportScreen.kt:225, ui/screens/templates/MealTemplateEditorScreen.kt, ui/screens/plan/PlanScreen.kt

### Q2-14 · S4 · Recipes C.42 / R5-11 · Template "Add a food or recipe": the "Recent" block looks alphabetical, and "All saved" repeats the same foods
Status: Fixed in 795a8d4 (template picker ordering), needs retest
Steps: 1. Meal templates > Edit > Add a food or recipe.
Expected: Recent items in order of use, then everything saved once.
Actual: RECENT lists Baked mac and cheese, Chicken fajita bowls (recipes first), then Apple, Banana, Bell pepper, Black beans, Broccoli, Cheddar cheese in A to Z order, followed by ALL SAVED starting with Apple again.
Screenshot: design/qa/pass2/q2-14-template-picker.png
Suspect: ui/screens/templates/MealTemplateEditorViewModel.kt

### Q2-15 · S4 · Today / Meal plan · A recipe assigned to a plan week shows on Today as "SNACK · PLANNED"
Status: Fixed in 795a8d4 (PlannedMealSlot), needs retest. The plan slot is time-of-day based because plans store no slot
Steps: 1. More > Meal plan > Make a plan (4 weeks) > Choose a recipe for week 1 > Baked mac and cheese. 2. Today.
Expected: A week-level recipe is not turned into a snack for today.
Actual: Next up shows "SNACK · PLANNED · Baked mac and cheese · Contains your allergen · 566 kcal · P 24 · C 49 · F 30". The plan gave no slot or day. The allergen label is right.
Screenshot: none (seen after relaunch; not captured)
Suspect: ui/screens/today (next-up planned item) and MealPlanViewModel

### Q2-16 · S4 · Nutrition C.11 · Searching "almond milk" in Log food returns milk chocolate and no pointer to Drinks
Status: Fixed in bc0c873 (almond milk ranking), needs retest. "almo" still ranks Salmon first under My foods
Steps: 1. Log food > Search > "almond milk" (typed slowly), wait for the list.
Expected: The plant milk, or a line saying drinks are in the Drinks chip (C.11).
Actual: USDA rows "Candies, milk chocolate, with almonds" ("Contains milk chocolate", right), Hershey's, Toblerone. No almond milk; the seeded "Almond milk, unsweetened" is a drink and is excluded from this search. The first frame showed the chocolate with an amber "Check the label" before it turned red (same lag as Q1-6). "almo" returns "Salmon fillet, baked" first under My foods (substring match).
Screenshot: design/qa/pass2/q2-16-search-almo.png
Suspect: ui/addfood/AddFoodViewModel.kt (search scope)

Summary for Pass 2: 16 findings. S1: 1 (Q2-1). S2: 1 (Q2-2). S3: 8 (Q2-3 to Q2-10, with Q2-9 possibly fixed at HEAD). S4: 6 (Q2-11 to Q2-16). A retest is needed: rebuild the preview at HEAD, re-run the allergen block (I.4, I.5) and the recipe log paths, and run the tests that need a key or network (Recipe Hunter results, To review, nutrition-menu Skip, Healthier nearby results) on a build with a test key and a stub server, because none of them could be reached here.

Round 2 fixes merged (3fb7f70, bc0c873, eb928a4, 6976495, 795a8d4). Every status above is "needs retest" on a rebuilt preview; nothing here was seen on a device.

### Open from round 2
- Swap logs a MAY_CONTAIN recipe or template without showing the allergen sheet.
- The template editor's food picker rows have no allergen chip.
- Shopping list dairy items have no allergen marker for a dairy-avoiding user.
- Old saved shopping rows keep their old aisles.
- The substring "almo" ranks Salmon first under My foods.
- Q2-7 zip lookup is not verified on a real network.
- Q1-14 (Dark chocolate "may contain milk" shown as a hard red warning) is still waiting for the owner's decision.

---

## Pass 3 — Train
Date: 2026-11-15/16 · Build: 57671ba preview (com.zeroindex.intake.preview) · AVD: phone30 (zone Asia/Tokyo, chosen so midnight was far off; the first seed at America/New_York was abandoned at 00:52 local) · Seed: tools/qa/seed.sh adapted to the preview package (temporary copy, deleted). The emulator was restarted and re-seeded once mid-pass.
Covered: A.3, A.4, A.13 (session bar on Train, Today, Progress), A.11 and A.10 (Train tab only, font 1.5 and landscape), A.12 (dark, Train Exercises only); D.1 to D.6, D.9, D.11 to D.13 (no set edit, see Q3-9), D.15, D.17 (generator, equipment step and starting weights only), D.20 to D.24, D.25 (no empty state, see Q3-10), D.26 (paste-link error only), D.35 (letters and a minus sign rejected in the weight field), D.37 (Done went to Today because the workout was resumed from Today), J.1 to J.5, J.12 (Q3-2), J.14, J.17, J.18 to J.20 on those screens. R5 items 14 to 17 and the owner's list: Videos layout is no longer jumbled (header card, chips Workouts only, Hide Shorts, Filters, search and a "more" menu, clean "Videos need a connection" error); the key copy no longer says Intake never downloads; the muscle figures are real front and back anatomical figures (Train, workout summary, exercise detail, Six regions and Show detail); Equipment has 30+ specific items and "Advanced: pick each item" in the program generator; Training log lists all 6 sessions grouped by week; per-exercise stats (best set, est. max, volume, sessions, chart, best reps, last 3 sessions) are on the exercise detail sheet. Checked against the seed: session volumes (working sets, warm-ups excluded) 5,790 / 8,300 / 5,145 / 7,940 / 6,794 / 9,432 lb, set counts, week totals (14,734 and 19,236), Back Squat total volume 10,309.6 lb, 3 sessions, 11 sets, est. max 197.3 lb (Epley), PR deltas; a live workout (50x8 and 55.5x8 = 844 lb, est. max 70.3 up 3.6 lb) all agree. Train first-run tip: hidden, correct. Program day advanced to Lower B after finishing Upper B.
Not reached: "Saved at the top", "load more" and Shorts detection by orientation (a fake YouTube key was saved to see the keyed layout; with no network nothing loads, so no results to test; the fake key stayed only in that emulator's app data and the reseed cleared it). Offline-download Downloaded view contents (no network). Onboarding equipment picker (needs a fresh install; the same picker was seen in the program generator). D.7 beyond the countdown (screen-off, vibrate), D.8, D.10 relaunch resume, D.14 (Pull-Up in a workout), D.16, D.18, D.19, D.27 to D.34, D.36, D.38 (the 17:30 time did show on the session). A.1, A.2, A.5 to A.9, A.14. Dark theme on the workout screens. Progress and Today beyond the session bar.
Logcat: clean. No FATAL EXCEPTION, no ANR for com.zeroindex.*, no StrictMode lines, empty crash buffer, checked at the end. The fake key string does not appear in logcat.
Seen on emulator only (Android 11, software rendering, no KVM). Not seen on a device: performance, haptics, real keyboard and IME behaviour, vibration at rest end, how the layout jump in Q3-11 feels on a fast phone. Screenshots are at scale 1.9 of the 1080x2280 screen (540x1200).

### Q3-1 · S3 · Settings / Train D.6 · Imperial users get a 44.1 lb bar and plates of 55.12, 44.09, 33.07, 22.05, 11.02, 5.51, 2.76 lb
Status: open
Steps: 1. Seeded install (units lb). 2. You > Settings > Training.
Expected: a bar of 45 lb and plates of 45, 35, 25, 10, 5, 2.5 lb (the subtitle in Settings says "2:00 rest · 44.1 lb bar" too).
Actual: Bar weight "44.1 lb"; "Plates available per side (lb, comma separated)" holds the metric plates converted to lb. The plate maths and the smallest step up use them. The Barbell (Olympic) row still reads "2 in sleeves, 20 kg bar" for a lb user.
Screenshot: design/qa/pass3/q3-1-plates.png
Suspect: data/settings (defaults converted from kg), ui/screens/settings Training

### Q3-2 · S3 · Train J.12 · Videos "Paste a YouTube link": the field and the Open button are hidden behind the keyboard and the page will not scroll to them
Status: open
Steps: 1. Train > Videos with no key. 2. Tap the link field, type.
Expected: the page scrolls so the field stays visible.
Actual: the keyboard covers the field and "Open this video"; swiping the page does nothing. The text can only be seen after Back hides the keyboard. (After Enter the error "That does not look like a YouTube video link." is clear.)
Screenshot: design/qa/pass3/q3-2-keyboard.png
Suspect: ui/screens/videos/VideosSection.kt (NoKeyCard, imePadding)

### Q3-3 · S2 · Train D.21 · Exercise detail muscle map leaves out the main muscle for Pull-Up
Status: open
Steps: 1. Train > Exercises, search "Pull-Up", open it. 2. Look at Muscles worked.
Expected: Lats (the listed primary muscle: "Lats (also biceps)") highlighted on the back figure and "Main: lats. Also: biceps".
Actual: only the biceps (and forearm region in Six regions) are highlighted; the caption and screen-reader text say "Also: biceps" with no Main. Lats are never drawn, so the back figure is empty. Squat (quads) and the session summary draw their main muscles correctly.
Screenshot: design/qa/pass3/q3-3-pullup-lats.png
Suspect: domain muscle mapping for Lats (likely the same for Dumbbell Row), ui/components MuscleMap

### Q3-4 · S4 · Train D.21 · Exercise detail "Your history": misaligned grid and inconsistent numbers
Status: open
Steps: 1. Exercise detail of Barbell Back Squat (seed).
Expected: BEST SET and HEAVIEST on one baseline; numbers formatted like the rest of the app.
Actual: "BEST SET" sits lower than "HEAVIEST" and its value is on the next row while HEAVIEST's value is beside the label; "TOTAL VOLUME 10309.6 lb" has no thousands separator and a decimal (the log shows "9,432 lb"); weights read "160.0 lb" here and "160.0 × 7" without a unit in the session summary. The chart has no date axis and no label for its top point; "Last 3 sessions · 1 records so far" should be "1 record".
Screenshot: design/qa/pass3/q3-4-detail-grid.png
Suspect: ui/screens/exercises detail sheet

### Q3-5 · S4 · Shell A.13 · "Workout in progress" card and the session bar both show, and the bar covers content
Status: open
Steps: 1. Start a workout, minimise it. 2. Look at Train, then Today, then Progress.
Expected: one entry point; list content clear of the bar.
Actual: Train shows the "WORKOUT IN PROGRESS · Resume workout" card and the session bar above the pill at once; Today shows its own "Workout in progress · Resume" row under the bar; on Progress the bar covers the card labels ("AVG KCAL / DAY"). The bar works (resumes the workout, counts time and rest).
Screenshot: design/qa/pass3/q3-5-bar-and-card.png
Suspect: ui/navigation (session bar), Train and Today hub screens

### Q3-6 · S4 · Train D.20 · "Bodyweight · bodyweight" repeated in every bodyweight row of the library and detail
Status: open
Steps: 1. Train > Exercises.
Expected: "Core · Bodyweight".
Actual: "Core · Bodyweight · bodyweight" (also "Lats (also biceps) · Bodyweight · bodyweight · rest 120s" on the detail). The equipment filter itself is right: with My equipment on, only barbell, dumbbell and bodyweight rows appear, no machines or cables. The row cards also have almost square corners next to the rounded cards elsewhere.
Screenshot: design/qa/pass3/q3-6-library-labels.png
Suspect: ui/screens/exercises row subtitle

### Q3-7 · S3 · Shell A.10 / A.11 · Landscape at font scale 1.5: rail labels clipped ("Nutritio", "Progres")
Status: open
Steps: 1. `settings put system font_scale 1.5`, rotate to landscape. 2. Train.
Expected: full labels or icons only.
Actual: the side rail clips two labels. At 1.5 in portrait the Train root is fine (pill labels fit). Landscape at normal font was not checked.
Screenshot: design/qa/pass3/q3-7-rail-clip.png
Suspect: ui/navigation rail

### Q3-8 · S3 · Programs D.17 · "Make me one" starting weights are not loadable: 149, 109, 49, 179, 124, 169, 144 lb
Status: open
Steps: 1. Train > Programs > Make me one, accept defaults to the end.
Expected: starting weights rounded to what the user's plates can make (for example 150, 110, 50 lb).
Actual: "Bench press 149 lb to start", "Barbell row 109 lb", "Overhead press 49 lb" and so on, probably a result of the metric plate list in Q3-1. The questions and results otherwise honour equipment (no machines seen in the first three days; not every exercise name was read).
Screenshot: design/qa/pass3/q3-8-generator-weights.png
Suspect: domain program generator / plate rounding

### Q3-9 · S3 · Train D.13 · A finished session cannot be edited: set rows and exercise names do nothing when tapped
Status: open
Steps: 1. Train > All sessions > a session. 2. Tap "Set 1: 160.0 lb × 7" or "Barbell Back Squat".
Expected: edit a set (checklist D.13), or the rows look read-only; an exercise name opens its detail.
Actual: nothing happens; the screen is titled "Workout summary" with a Done button even when opened from the log, and there is no edit or delete.
Screenshot: design/qa/pass3/q3-9-session-sets.png
Suspect: ui/screens/train summary and history

### Q3-10 · S3 · Videos D.23 / D.25 · With no key there is no Saved or Downloaded entry, and with a key Saved has no visible place
Status: open
Steps: 1. Train > Videos with no key (offline downloads on or off). 2. With a key, open the "more" menu.
Expected: Saved and Downloaded reachable at the top, with an empty state ("No saved videos yet").
Actual: with no key the screen is the key card plus "Paste a YouTube link" only (copy is correct and no longer contradicts the download switch). "Downloaded videos" exists only in the more menu, which only exists with a key, so downloads are unreachable without one. With a (fake) key the top row is Search, more menu, chips Workouts only, Hide Shorts, Filters; no Saved chip or section was visible with zero saved videos, so "Saved at the top" could not be confirmed. The offline-downloads switch shows the yt-dlp warning dialog ("Before you turn this on", ToS, suspension risk) and turns on only after "I understand, turn it on".
Screenshot: design/qa/pass3/q3-10-nokey-videos.png (menu: design/qa/pass3/videos-key-menu.png, warning: design/qa/pass3/dl-warning.png)
Suspect: ui/screens/videos/VideosSection.kt

### Q3-11 · S3 · Train D.6 / J.4 · Marking a set done shifts the rows down, so a quick second tap un-ticks the wrong set
Status: open
Steps: 1. Start Upper B. 2. Tap done on set 2, then promptly on set 3.
Expected: rows stay put under the finger.
Actual: the rest bar appears above the card and pushes every row down about 100 px; the second tap landed on set 2 and unticked it (set 3 stayed open). The countdown, +15 s and Skip work. A "New estimated max" row also appears under a PR set and pushes the next row down. Weight shows "50.0" before a set is done and "50" after it. Whether this is as easy to hit on a phone is not known.
Screenshot: design/qa/pass3/q3-11-set-rows.png
Suspect: ui/screens/train ActiveWorkout rest bar placement

### Q3-12 · S4 · Settings API keys · The OpenRouter Save button looks different from the other two
Status: open
Steps: 1. Settings > API keys, scroll.
Expected: the three Save buttons share one style.
Actual: OpenRouter's Save is a very faint tile with no clear shape; USDA and YouTube have a grey pill. The YouTube key row after saving reads "YouTube key saved, encrypted on this device." with Dismiss, fine. (Not a Train screen; seen on the way.)
Screenshot: none kept (the API keys screen was not saved); see the Settings API keys screen.
Suspect: ui/screens/settings ApiKeys

Summary for Pass 3: 12 findings. S1: 0. S2: 1 (Q3-3). S3: 7 (Q3-1, 2, 7, 8, 9, 10, 11). S4: 4 (Q3-4, 5, 6, 12). A retest is needed after fixes, plus the parts that need a real key and network: Saved at the top, load more, Shorts hiding by orientation, downloads. Nothing here was seen on a device.
