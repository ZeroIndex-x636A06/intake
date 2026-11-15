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
Status: Partly fixed: fixed in 0017495 for Food detail, Log food, recipes, cook mode, drinks, photo estimate and menu items, needs retest. Open: wear and meal-plan logging have no override path
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
Status: Partly fixed: fixed in 0017495 (chip says Fits your allergens / Only the name was checked / No ingredient list: check the label), needs retest. Open: the review-import sheet still says "Ingredient check passed" for clear items; NutritionMeal and NutritionHome chips lack the trace wording
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
Status: Partly fixed: Nutrition tip stays hidden once the log has rows (3c62d87), needs retest. Open: Train and Progress first-run tips have no history check
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
Status: Partly fixed: sweet potato, sweet corn, sweet peppers, yams, peanut butter and meat fixed in 3c62d87. Open: whey powder has a generic icon
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
