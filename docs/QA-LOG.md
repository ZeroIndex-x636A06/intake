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
Status: Verified fixed in Pass 5a (4321255): Food page Log anyway and the recipe path both wrote 'Logged despite: dairy' and Today showed it. Was: Fixed in 3fb7f70 (meal plan allergen sheet and the watch note, on top of 0017495 for Food detail, Log food, recipes, cook mode, drinks, photo estimate and menu items), needs retest
Steps: 1. Today > Log food, search "milk". 2. Tap + on "Milk, canned, condensed, sweetened" (USDA row). 3. Food page shows "Contains milk"; tap "Log anyway..." then "Log it anyway". 4. Back on Today scroll to Dinner.
Expected: Entry carries "Logged despite: dairy" in its name or serving label and still shows the allergen warning (CLAUDE.md rule 2, checklist C.13 and B.15).
Actual: Entry reads "Milk, canned, condensed, sweetened · 38 g" with no marker, no chip, nothing that says it was an override. Same on the edit sheet. Only the swap path (SwapRepository) calls markOverridden; the Log food path does not. The snackbar says only "Logged Milk, canned, condensed, sweetened to dinner".
Screenshot: design/qa/pass1/q1-1-logged-despite-missing.png
Suspect: ui/screens/fooddata/FoodDetailScreen.kt (the "Log anyway" path near line 333) and data/repository/FoodLogRepository.kt markOverridden, which this path never calls

### Q1-2 · S2 · Today B.14 · Deleting a log entry has no Undo
Status: Verified fixed in Pass 5a (4321255): Undo snackbar shown after Delete; Undo restored the entry. Was: Fixed in 3c62d87, needs retest
Steps: 1. Today, tap an entry (e.g. Apple under Snacks). 2. Edit sheet > "Delete this entry". 3. Screenshot straight away.
Expected: Soft delete, then an "Undo" snackbar that restores the entry; totals follow both ways.
Actual: Entry and its kcal vanish at once (Snacks 283 -> 188 kcal). No snackbar or Undo seen after three deletes (milk, fajita bowls, apple). The Undo that exists in Drinks ("Removed ... Undo") is missing here. TodayViewModel.deleteLog just calls softDelete. Seen on the emulator only; a very short snackbar would still show on an immediate capture.
Screenshot: design/qa/pass1/q1-2-delete-no-undo.png
Suspect: ui/screens/today/TodayViewModel.kt:178 deleteLog, ui/screens/today/TodayScreen.kt:193

### Q1-3 · S2 · Nutrition C.10 / Log food · Typing in the Log food search drops and reorders characters
Status: Still failing in Pass 6: on the preview build `input text "almond%smilk"` in Log food search gave an ANR twice and the text was lost with the killed app; see Q6-3. Was: Partly fixed in 504dae6: open-time allergen scan burst cut from about 85 s to about 45 s of CPU on the debug emulator; typing still drops injected keys on the emulator. Needs a preview-build and real-device check. Was: Still failing in Pass 5b: fast typed 'almond milk' in Log food search (input text): the field kept 'almo', every key took 3 to 7 s, and the app hit an ANR twice (08:16 and 08:23 device time); typed key by key every character is kept. Logged again as Q5b-1. Was: Fixed again in ae7e373, needs retest. Was: Still failing in Pass 5a: fast typed text in Log food search loses its tail (protein -> protei, ranch -> ran, milk -> mil, almond milk -> almo, zzqxvv -> zzqx; 5 of 5 bursts), slow key-by-key typing is intact; logged again as Q5a-3. Was: Fixed in f4d3c5d, needs retest
Steps: 1. Log food > Search. 2. Type "protein" with `adb shell input text protein` (also "ranch").
Expected: Field shows exactly what was typed.
Actual: Field showed "proet" for protein, "ranhc" and "ranhh" for ranch; the search then reports "Nothing matches". "milk", "latte", "wine" came through intact. The text field value is `state.query`, which comes back through a heavy `combine` flow, the usual cause of lost and reordered keystrokes under load. Seen on a slow software-rendered emulator only; not confirmed on a device, but a real phone typing quickly while results rebuild could hit the same path.
Screenshot: design/qa/pass1/q1-3-search-drops-chars.png
Suspect: ui/addfood/LogFoodScreen.kt:403 (TextField bound to state.query), ui/addfood/AddFoodViewModel.kt:364 (uiState combine); keep the typed text in local state

### Q1-4 · S2 · Allergens I.9 / Nutrition C.10 · "No allergens" chip on foods that do contain other allergens (and on foods with no ingredient list)
Status: Verified fixed in Pass 5a (4321255): chips now read 'Fits your allergens' or 'Only the name was checked'; the review-import sheet wording could not be reached (needs an AI or network import). Was: Fixed in 3fb7f70 (Nutrition trace chips, on top of 0017495), needs retest. Still open: the review-import sheet says "Ingredient check passed" for clear items
Steps: 1. Log food, scroll the default list or open Mine. 2. Read the chip on Egg, whole, cooked; Sourdough bread; Salmon fillet, baked; Peanut butter; and in Drinks on Beer and Wine.
Expected: Chip says what was checked, e.g. "No milk found", or lists the other allergens. Foods with no ingredient list say so (LogFoodScreen.kt:273 comment promises that).
Actual: All of them show a green tick "No allergens": egg (contains egg), sourdough (wheat), salmon (fish), peanut butter (peanuts), beer (barley), wine. Apple and Banana, which have no ingredient list at all, also read "No allergens". To a person who avoids only dairy it reads as an all-clear for the food, and a literal reading is false. Wine in particular can carry milk or egg fining agents.
Screenshot: design/qa/pass1/q1-4-no-allergens-chip.png (Egg, Sourdough); Drinks: design/qa/pass1/q1-15-drinks-my-drinks.png (Beer)
Suspect: ui/allergen/AllergenBanner.kt:117-133 chip wording; ui/addfood/LogFoodScreen.kt:273

### Q1-5 · S2 · Recipes C.36 / I.4 · Recipes list shows no allergen status
Status: Verified fixed in Pass 5a (4321255): every recipe row has a chip; the verdicts differ between screens, see Q5a-1 and Q5a-2. Was: Fixed in 0017495 (recipe list chips) and f4d3c5d (Checking placeholder), needs retest
Steps: 1. Nutrition > More > Recipes. 2. Look at the five rows under My recipes.
Expected: Each row has an allergen chip: fajita bowls and salmon clear, mac and cheese and turkey chili contains, grandma's soup unknown or "not checked" (C.36).
Actual: Rows show only "566 kcal · 24 g protein · per serving" (soup: "4 servings"). Baked mac and cheese, a dairy recipe for a strict dairy user, looks identical to the clear ones. The chips exist elsewhere (recent recipes in Log food, recipe detail), so the list is inconsistent.
Screenshot: design/qa/pass1/q1-5-recipes-no-chips.png
Suspect: ui/screens/plan/ (recipes hub list), ui/addfood/RecipeRows.kt has the chip that the list lacks

### Q1-6 · S3 · Nutrition C.10 / I.1 · Dairy search results show with no chip for several seconds, then chips pop in
Status: Verified fixed in Pass 5a (4321255): 'Checking...' chip while the verdict is computed; no chipless dairy row seen. Was: Fixed in f4d3c5d (Checking... chip until the verdict arrives; Mine is scanned whole in the merge), needs retest
Steps: 1. Log food > Search "milk". 2. Screenshot at once, then 10 s later.
Expected: A row never appears without its allergen verdict, or shows a "checking" state.
Actual: First frame: Milk and cereal bar, Milk buttermilk dried, Condensed milk, Dry nonfat milk, all USDA dairy, are plain rows with no chip. About 10 s later each gets a red "Contains milk" chip and the list reflows. Tapping + in that window still opens the blocked Food page (I checked after), so nothing is added silently; the rows look safe until the chips arrive. Slow emulator, but the gap is real: verdicts come from a second flow (rowAllergens) after the rows.
Screenshot: design/qa/pass1/q1-6-dairy-rows-no-chip-yet.png
Suspect: ui/addfood/AddFoodViewModel.kt:335 rowAllergens

### Q1-7 · S3 · Shell J.11 · False empty and error copy while data loads
Status: Verified fixed in Pass 5b (5822073): Today cold start shows blank frames, no 'No target yet', '0 kcal eaten' or 'No weigh-in yet'; the Nutrition loading frame was not caught. Was: Fixed again in ae7e373, needs retest. Was: Still failing in Pass 5a: Today cold open shows 'No target yet', '0 kcal eaten', 'No weigh-in yet' and Nutrition shows 'Nothing yet' on every meal while it loads; More, Drinks and Log food are fixed; logged again as Q5a-6. Was: Fixed in f4d3c5d, needs retest
Steps: 1. Open Nutrition > More; Log food; Drinks; a logged food's info icon on the meal screen. 2. Screenshot within 1 to 5 s.
Expected: Spinner or blank until loaded.
Actual: More says "No recipes yet", "No meal templates yet", "No saved options yet" for about 10 s (the seed has 5, 2 and 2). Log food says "Nothing logged yet ... Foods you log often land here". Drinks says "No drinks yet" and "Nothing logged yet today". The meal screen's info icon shows "This food is not on this phone." with a Back button before the food loads. All turn into real content afterwards. Slow emulator makes it long, but the first frame of each is wrong text.
Screenshot: design/qa/pass1/q1-7-false-empty-loading.png, design/qa/pass1/q1-7b-food-not-on-phone.png
Suspect: ui/screens/nutrition/NutritionMoreScreen.kt, ui/addfood/LogFoodScreen.kt, ui/screens/drinks/DrinksScreen.kt:242,308, ui/screens/fooddata/FoodDetailScreen.kt:126 (null food treated as missing before first emit)

### Q1-8 · S3 · Nutrition C.6 · Nutrition opens with "Thu, Jan 1" and "Reading today...", then the layout jumps
Status: Verified fixed in Pass 5a (4321255): date is right from the first frame; a 'Reading today...' box holds the space. Was: Fixed in f4d3c5d, needs retest
Steps: 1. Tap the Nutrition tab (also after relaunch).
Expected: Real date from the first frame; no layout shift.
Actual: Day pill reads "Today / Thu, Jan 1" (epoch 0) with a "Reading today..." box, no allergen strip yet; seconds later it becomes "Sat, Nov 14" and the strip, tip card and ring push everything down.
Screenshot: design/qa/pass1/q1-8-jan1-placeholder.png
Suspect: ui/screens/nutrition/NutritionHomeScreen.kt:216, NutritionHomeViewModel.kt initial state

### Q1-9 · S3 · Today B.12 / Nutrition · Macro percentage labels overlap and clip on small segments
Status: Verified fixed in Pass 5a (4321255): one labelled P/C/F line, nothing clipped. Was: Fixed in 3c62d87, needs retest (one labelled line "P x%  C y%  F z%" kept in the merge at eed25fb; per-segment labels dropped)
Steps: 1. Today, scroll to the Drinks card (coffee). 2. Open any low-protein food (Banana, Wine) and read the bar under the macro chips.
Expected: Labels for each segment readable.
Actual: Drinks card: "2" is cut off before "98%". Banana: "4% 93%" with the 4% clipped and the fat label cut to "3". Wine: "4% 96%" clipped the same way. Labels are placed by segment start with no minimum spacing.
Screenshot: design/qa/pass1/q1-9-pct-label-clipped.png
Suspect: ui/components (macro split bar used by MealSlotCard.kt and the food page)

### Q1-10 · S3 · Today B.1 / B.11 · Next arrow on today goes to a future day, which then says "today" and shows the weight as current
Status: Verified fixed in Pass 5a (4321255): Next day arrow is inert on today. Was: Fixed in 3c62d87, needs retest
Steps: 1. Today, tap the right arrow on Sat Nov 14. 2. Scroll to the top.
Expected: Next is disabled or inert on today (B.1); at minimum copy fits the day.
Actual: Moves to "Sun, Nov 15" (no "Tomorrow" label). Water says "Nothing logged yet today." and the ring says "2,260 kcal left". Weight tile shows "192.0 lb -1.9 lb this week" for a day that has not happened, with no age of the last weigh-in. Nutrition's day pill has the Next arrow greyed on today, so the two tabs disagree.
Screenshot: design/qa/pass1/q1-10-future-day.png
Suspect: ui/screens/today/TodayScreen.kt header arrows, ui/screens/today/components/HydrationCard.kt:81

### Q1-11 · S3 · Nutrition C.2 / Drinks · Meals list on Nutrition and the per-meal screens leave out drinks, so rows do not add to the total
Status: Verified fixed in Pass 5a (4321255): Nutrition has a Drinks row; Breakfast 596 + Lunch 572 + Dinner 618 + Snacks 283 + Drinks 54 = 1,505 eaten at that point. Was: Fixed in eed25fb, needs retest
Steps: 1. Nutrition tab. 2. Add Breakfast 596 + Lunch 572 + Dinner 519 + Snacks 283 = 1,970. Compare with "2024 eaten" in the ring (before I deleted anything).
Expected: Every logged kcal is visible somewhere on the screen, or the drinks have a row.
Actual: The coffee with sugar (54 kcal) is counted in the ring but there is no Drinks row on Nutrition and the per-meal screen does not list it. It is visible only on Today. (After my deletions: 1,357 eaten vs 1,303 in meal rows.)
Screenshot: design/qa/pass1/q1-11-meals-miss-drinks.png
Suspect: ui/screens/nutrition/NutritionHomeScreen.kt meal rows, NutritionMealScreen.kt

### Q1-12 · S3 · Nutrition C.8 · "More: plan, recipes, eating out, nutrients" names a nutrients entry that is not in More
Status: Verified fixed in Pass 5a (4321255): row now reads 'plan, recipes, templates, shopping, eating out' and matches More. Was: Fixed in eed25fb, needs retest
Steps: 1. Nutrition, scroll to the bottom row. 2. Tap it.
Expected: More lists what the row says.
Actual: More has Meal plan, Recipes, Meal templates, Shopping list, Eating out. No nutrients entry (the "More nutrients" expander is on the dashboard card).
Screenshot: design/qa/pass1/q1-7-false-empty-loading.png (the More screen)
Suspect: ui/screens/nutrition/NutritionHomeScreen.kt:182

### Q1-13 · S3 · Recipes C.37 · Recipe with no nutrition has a greyed "Log a serving" and no reason
Status: Verified fixed in Pass 5a (4321255): on the recipe page Log a serving is enabled for the estimated soup; the greyed + in Log food > Mine is Q5a-11. Was: Fixed in eed25fb, needs retest
Steps: 1. More > Recipes > Grandma's vegetable soup.
Expected: Button either asks for macros or logs as "not listed" (C.37), or says why it is off.
Actual: "Log a serving" is disabled grey. The only hint is the grey line above, "Nutrition: not listed. The page gave none and the ingredients could not be matched." No way to add macros from here.
Screenshot: design/qa/pass1/q1-13-log-serving-disabled.png
Suspect: ui/screens/plan/RecipeDetailScreen.kt

### Q1-14 · S3 · Allergens C.23 · "Dark chocolate 70%" ("may contain milk") reads "Contains milk"
Status: Not retested in Pass 6 (waiting for the owner's decision). Was: Partly fixed: 0017495 words it as "(trace)" but it stays a hard red warning; owner decision pending Pass 5a: still a red 'May contain milk (trace)' chip on Dark chocolate 70%; no change expected until the owner decides.
Steps: 1. Log food, open the default list, find Dark chocolate 70%.
Expected: A may-contain verdict, worded as a trace (C.23).
Actual: Red chip "Contains milk", the same wording as Milk chocolate bar. Safe direction, but it overstates and a person cannot tell trace from ingredient.
Screenshot: design/qa/pass1/q1-4-no-allergens-chip.png (Dark chocolate row)
Suspect: domain/allergen engine chip text, ui/allergen/AllergenBanner.kt

### Q1-15 · S3 · Drinks C.32 / I.3 · My drinks cards omit the oat-milk "Check the label" note that Mine shows
Status: Verified fixed in Pass 5a (4321255): My drinks card shows 'Check the label: milk'. Was: Fixed in 0017495 (My drinks cards show the verdict chips), needs retest
Steps: 1. Drinks page, look at "Black tea + 2 tbsp o...". 2. Log food > Mine, same drink.
Expected: Same note in both.
Actual: Mine shows "Check the label: milk". The Drinks card shows only "9.1 fl oz · 17 kcal"; one tap logs it with no note. Names are also cut ("Coffee, black + 3 ts..."). Logging it showed only "Added 9.1 fl oz Black tea + 2 tbsp oat milk".
Screenshot: design/qa/pass1/q1-15-drinks-my-drinks.png
Suspect: ui/screens/drinks/DrinksScreen.kt my-drinks cards

### Q1-16 · S3 · Eating out C.44 · Saved eat-out options are dead cards
Status: Verified fixed in Pass 5a (4321255): tapping a saved option opens a slot sheet and logs it; sheet title is odd, see Q5a-19. Was: Fixed in eed25fb, needs retest
Steps: 1. More > Eating out. 2. Tap Burrito Barn (invented).
Expected: Open it, log it, or swap it; "Choose anyway" path (C.44).
Actual: Nothing happens on tap. The only control is a bin icon (no confirm seen). A saved option cannot be logged from here. They show "Check the label" for estimates, which is right.
Screenshot: design/qa/pass1/q1-16-eatout-dead-cards.png
Suspect: ui/screens/hub/ (eating out screen, saved options list)

### Q1-17 · S4 · Nutrition C.21 · Food page: top figure and printed label differ (188 vs 190, 105 vs 110, 152 vs 150 kcal)
Status: Verified fixed in Pass 5a (4321255): top kcal chip equals the printed label (190 and 190); the page now shows a rounded figure while the entry logs the exact one, see Q5a-22. Was: Fixed in eed25fb, needs retest
Steps: 1. Open Peanut butter, Banana or Rolled oats from Log food. 2. Compare the kcal chip with the Nutrition Facts Calories.
Expected: R5 item 13 said the 65 vs 70 kcal mismatch was wrong.
Actual: A note under the label now explains FDA-style rounding, but the two numbers still sit on one screen and differ. A rounded figure on a food that is logged at the exact figure may still read as an error.
Screenshot: design/qa/pass1/q1-17-calories-top-vs-label.png
Suspect: ui/screens/fooddata/FoodDetailScreen.kt, NutritionLabel

### Q1-18 · S4 · Nutrition · First-run tip shown to a user with three weeks of data, and it comes back
Status: Verified fixed in Pass 5a (4321255): no first-run tip on Nutrition or Train with the seed; Progress tip not opened. Was: Fixed in 3fb7f70 (Train and Progress tips check history, on top of 3c62d87), needs retest
Steps: 1. Nutrition tab. 2. Leave and return after a relaunch.
Expected: A tip only for new users, and it stays dismissed after Got it.
Actual: "Log what you eat, plan meals and check allergens here. Start by logging your next meal." shows above the ring, with Got it and Log food; seen again on later visits (I did not tap Got it).
Screenshot: design/qa/pass1/q1-18-first-run-tip.png
Suspect: ui/onboarding/TabTip.kt:25

### Q1-19 · S4 · Nutrition J.18 · Number formatting differs: "2393 eaten" next to "2,260 kcal"
Status: Verified fixed in Pass 5a (4321255): Nutrition reads '1,505 eaten'; Today meal cards still lack the comma, see Q5a-14. Was: Fixed in 3c62d87, needs retest
Steps: 1. Nutrition, go back one day (Fri Nov 13).
Expected: "2,393" as on Today.
Actual: Nutrition ring text "2393 eaten" and "1357 eaten"; Today shows "2,393".
Screenshot: design/qa/pass1/q1-19-eaten-no-comma.png
Suspect: ui/screens/nutrition/NutritionHomeScreen.kt

### Q1-20 · S4 · Today B.12 · Meal cards are tall and the macro percentages are unlabelled
Status: Verified fixed in Pass 5b (5822073): meal cards are compact (entries about 55 dp apart, labelled P/C/F line, Save as meal template row inside the card). Was: Fixed again in ae7e373, needs retest. Was: Still failing in Pass 5a: percentages are now one labelled P/C/F line, but each meal card is still tall (blank band between the last entry and Log food, a separate Save as meal template row); logged again as Q5a-14. Was: Fixed in 3c62d87 and eed25fb (tighter cards, labelled P/C/F line), needs retest
Steps: 1. Today, scroll to Meals.
Expected: Four compact rows (B.12).
Actual: Five full cards (the fifth is Drinks), each with a bar, three unnamed percentages, a "+ Log food" row and a separate "Save as meal template" row; large gaps between entries; one meal fills most of a screen. Percentages do not say protein, carbs, fat.
Screenshot: design/qa/pass1/q1-20-today-meal-cards.png
Suspect: ui/screens/today/components/MealSlotCard.kt

### Q1-21 · S4 · Nutrition C.42 · Meal templates: heading repeated three times, no allergen verdict, log note not checked
Status: Verified fixed in Pass 5a (4321255): one title, template chips, and the log note names the plant milk; the log message has no Undo and says 'to snack', see Q5a-9. Was: Fixed in eed25fb (one heading, N saved note) and 0017495 (template verdict chips), needs retest
Steps: 1. More > Meal templates. 2. ... on Post-workout shake > log to Snacks.
Expected: One title; a verdict before one-tap logging; note for plant milk.
Actual: "Meal templates" is the bar title, the page title and the section title. The rows have no chip. Logging the shake (it holds almond milk) says "Logged Post-workout shake to snack" (lowercase, singular) with no check-the-label note.
Screenshot: design/qa/pass1/q1-21-templates.png
Suspect: ui/screens/templates/

### Q1-22 · S4 · Nutrition C.15 · Log food default list: empty "RECENT" header, and saved recipes missing
Status: Verified fixed in Pass 5a (4321255): no empty RECENT header; all five recipes are under Mine. Was: Fixed in f4d3c5d, needs retest
Steps: 1. Log food, wait for the list.
Expected: Recent foods and recipes, then everything saved (R5 item 11).
Actual: For several seconds "RECENT" is followed directly by "RECENT RECIPES". When loaded, only 3 recipes appear; Baked mac and cheese and Grandma's soup are absent, so a saved recipe cannot be found without searching.
Screenshot: design/qa/pass1/q1-22-recent-recipes.png
Suspect: ui/addfood/RecipeSearchViewModel.kt, ui/addfood/LogFoodScreen.kt

### Q1-23 · S4 · Nutrition C.18 · "Open Food Facts could not be reached. The sources above still work." with nothing above
Status: Verified fixed in Pass 5a (4321255): now 'Open Food Facts could not be reached. Check your connection and try again.'. Was: Fixed in 3c62d87, needs retest
Steps: 1. Log food, type a word with no local match (offline).
Expected: Plain message that fits what is on screen.
Actual: Heading "PACKAGED FOODS (OPEN FOOD FACTS)", then "The sources above still work", then "Nothing matches", with no sources above.
Screenshot: design/qa/pass1/q1-3-search-drops-chars.png
Suspect: ui/addfood/LogFoodScreen.kt

### Q1-24 · S4 · Allergens C.12 · Warning sheet copy says "matched an ingredient" when the match was the name
Status: Verified fixed in Pass 5a (4321255): sheet says 'matched the name' for a name match and 'the name and the ingredient list' for a recipe. Was: Fixed in 0017495 (warning sheet says where the match was found), needs retest
Steps: 1. Log anyway on a USDA milk row (see Q1-1).
Expected: Copy fits the evidence.
Actual: "Your allergen profile matched an ingredient", but the evidence card says "milk found in the name". The USDA row has no ingredient list.
Screenshot: design/qa/pass1/q1-24-sheet-copy.png
Suspect: ui/allergen/AllergenWarningSheet.kt

### Q1-25 · S4 · Nutrition C.15 · Wrong food icons (cookie for sweet potato)
Status: Verified fixed in Pass 5a (4321255): Sweet potato has a plant icon; whey powder and the chili icon were not re-checked. Was: Fixed in 3fb7f70 (whey powder, SUPPLEMENT icon, on top of 3c62d87), needs retest
Steps: 1. Log food list, find Sweet potato, baked.
Expected: Icon fits the food (R5 item 13 complained of a cookie on tea).
Actual: Sweet potato has a cookie icon; Turkey chili has a burger; Whey protein powder a fork and knife.
Screenshot: design/qa/pass1/q1-25-cookie-icon.png
Suspect: ui/addfood/ food icon mapping

### Q1-26 · S4 · Eating out C.44 · Menu items judged clear show no chip; the menu info card is a centred paragraph
Status: Verified fixed in Pass 5b (5822073): Menu import intro is left-aligned and shorter; item chips read Fits your allergens, Contains ice cream and milk, Contains buttermilk, Contains butter and buttermilk. Was: Fixed again in ae7e373, needs retest. Was: Still failing in Pass 5a: the per-item chips are fixed (Fits your allergens, Contains ...), but the intro card on Menu import is still a centred multi-line paragraph; logged again as part of Q5a-13. Was: Fixed in 0017495 (menu item chips) and eed25fb (intro left-aligned, shorter), needs retest
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
Status: Verified fixed in Pass 5a (4321255): Log a serving on Baked mac and cheese opens the Food safety sheet; Log it anyway wrote '(Logged despite: dairy)' and Undo shows. Was: Fixed in bc0c873 (AllergenLogGate on the recipe page: scan, warning sheet, "Logged despite" note; LogPathAuditTest pins every log path), and 795a8d4 (servings and Undo on both the clean and "Log it anyway" paths), needs retest
Steps: 1. Nutrition > More > Recipes > Baked mac and cheese (pill reads "Contains an allergen", three lines tagged dairy). 2. Tap "Log a serving". 3. Tap Snacks. 4. Go to Today and scroll to Snacks.
Expected: The Food safety sheet (as Cook mode's "Done cooking" > Log a serving > Dinner shows, and as Meal plan's "Log this meal" shows: "Not logged: mac and cheese, butter, cheddar, cheese, milk matches your allergen profile"), and "Logged despite: dairy" if the person chooses to go on.
Actual: The slot sheet closes, the button turns to "Logged · log another", and Today shows "Baked mac and cheese 566 kcal · 24 g protein" under Snacks, nothing marking it. A strict-dairy user logs a cheese dish in two taps. The recipe editor's own copy says the opposite: "You can still save it, but it will not log without a warning." Three paths to the same action behave three ways (recipe page: silent; Cook mode: sheet; Plan: refused).
Screenshot: design/qa/pass2/q2-1-mac-logged-snacks.png, design/qa/pass2/q2-1b-recipe-log-no-warning.png, design/qa/pass2/q2-1c-cook-mode-warns.png
Suspect: ui/screens/plan/RecipesViewModel.kt:136 logServing, ui/screens/plan/RecipeDetailScreen.kt:298; reuse the pending-scan flow in CookModeViewModel.confirmPending

### Q2-2 · S2 · Recipes C.40 / I.9 · A new recipe with no ingredients says "Passed allergen check"
Status: Verified fixed in Pass 5a (4321255): a new recipe with no ingredients says 'Only the name was checked', and 'Contains milk' once '1 cup milk' is pasted. Was: Fixed in bc0c873 (empty recipe wording), needs retest
Steps: 1. Recipes > Add a recipe. 2. Look at the allergen line before typing anything.
Expected: No clear verdict for an empty recipe ("nothing to check yet"). Nothing says it passed unless an ingredient list was checked (I.9).
Actual: Below "No macros yet" the green chip reads "Passed allergen check" with "None yet." ingredients. It flips to "Contains cheddar, milk" once lines are added, which is right, but the empty state is a false all-clear.
Screenshot: design/qa/pass2/q2-2-empty-recipe-passed.png
Suspect: ui/screens/recipe/RecipeEditorScreen.kt (verdict row when the draft has no lines)

### Q2-3 · S3 · Recipes C.38 / C.39 · Recipe page has no servings stepper and "Log a serving" takes no amount, no undo, and offers a Drinks slot
Status: Verified fixed in Pass 5a (4321255): servings stepper in 0.5 steps, Dinner/Snacks/Lunch/Breakfast only, Undo snackbar. Was: Fixed in 795a8d4 (servings stepper, Undo, Drinks slot removed), needs retest
Steps: 1. Recipes > Chicken fajita bowls. 2. Look for a servings control; open the three-dot menu (Add to this week, Edit recipe, Delete recipe). 3. Log a serving.
Expected: C.38 describes a servings stepper that rescales ingredients and per-serving numbers; a way to log more or less than one serving; an Undo after logging.
Actual: The page shows "4 servings · Prep 15 ..." as text only; the stepper exists only inside Cook mode (4 to 6 servings scaled 480/640/260/240/28 g to 720/960/390/360/42 g, correct). Log a serving always logs exactly 1 ("1 serving" label). The slot list is Breakfast, Lunch, Dinner, Snacks, Drinks, so a recipe can be filed under Drinks. After logging there is only the button text change, no snackbar, no Undo. Done cooking offers "Log a serving of ...? You can change the amount later on Today" even when a serving was just logged from the page.
Screenshot: design/qa/pass2/q2-3-log-serving-slots.png
Suspect: ui/screens/plan/RecipeDetailScreen.kt, ui/screens/plan/PlanSheets.kt (slot picker)

### Q2-4 · S3 · Recipes C.39 / J.6 · Cook mode: clipped ingredient list, oversized close button, "Swipe left or right" shown for a one-step recipe
Status: Verified fixed in Pass 5a (4321255): close icon is normal size, ingredient list has a 'More ingredients below' cue, no swipe hint for one step. Was: Fixed in 795a8d4 (cook mode list, close icon in a 72dp touch target, one-step hint), needs retest
Steps: 1. Recipes > Chicken fajita bowls > Cook. 2. Screenshot with ingredients showing.
Expected: Nothing cut mid-row; a normal-size close control; navigation hints only when there is more than one step.
Actual: The ingredient box ends in the middle of the last row ("28 g Olive oil" cut through by the next header) with no scroll cue. The × is drawn as a 100 px rounded square box, far larger than the controls beside it. "STEP 1 OF 1" has a back arrow button and the hint "Swipe left or right to move between steps" below the step text, partly under the bottom buttons. The steps text for this recipe is one block, so there is nothing to swipe to.
Screenshot: design/qa/pass2/q2-4-cook-layout.png
Suspect: ui/screens/plan/CookModeScreen.kt

### Q2-5 · S3 · Recipes C.38 · "Estimated from ingredients" picks wrong foods and weights; the totals add up but the inputs are off
Status: Verified fixed in Pass 5a (4321255): soup now matches a can as 411 g and celery has a weight (5 of 6 lines, ~62 kcal); the stock still matches 'Soup, chicken broth' in a vegetable soup; the '1 cup milk' line was not re-run. Was: Fixed in eb928a4 (ingredient matching), needs retest
Steps: 1. Recipes > Grandma's vegetable soup > "Show how each line was matched". 2. Add a recipe with "200 g pasta", "100 g cheddar cheese", "1 cup milk" via Paste a list, save, open it, "Show how each line was matched".
Expected: "milk" resolves to ordinary milk; a "can" of tomatoes is a can's weight; vegetable soup does not use beef stock.
Actual: Sums are right (soup: 50+44+22+125 = 241 kcal / 4 = 60 shown as "~60"; pasta bake: 742+408+265 = 1,415 / 4 = 354 shown as "~354"). The matches are not: "1 cup milk" = "Milk, sheep, fluid" 245 g 265 kcal (whole cow milk would be about 150 kcal, so about +29 kcal per serving); "1 can diced tomatoes" = 123 g, 22 kcal (a can is about 400 g); "4 cups stock" in a vegetable soup = "Soup, stock, beef, home-prepared" 125 kcal; "3 stalks celery" is "no weight known for stalk" and left out, so the estimate is low. The card says "4 of 6 ingredient lines" and "Not the site's numbers", which is honest, but a person will trust ~60 kcal.
Screenshot: design/qa/pass2/q2-5-estimate-matches.png, design/qa/pass2/q2-5b-soup-matches.png
Suspect: domain/recipe (RecipeNutritionEstimator food ranking and unit weights)

### Q2-6 · S3 · Recipes C.43 · Shopping list adds up correctly but lists cooked weights as things to buy, and the aisles are inconsistent
Status: Verified fixed in Pass 5a (4321255): dairy aisle, one aisle per kind, dairy marks on butter, cheddar and milk; amounts 1.41 lb chicken, 12.9 oz spaghetti, 2 cups milk are consistent with the recipe lines. Was: Fixed in eb928a4 (shopping list forms and Dairy aisle), needs retest. Old saved shopping rows keep their old aisles
Steps: 1. More > Shopping list. 2. Pick Baked mac and cheese, Chicken fajita bowls and Salmon, sweet potato and broccoli (x1 each). 3. Scroll to the amounts.
Expected: Amounts a person can buy; one sensible section per kind of item.
Actual: Arithmetic checked and right: chicken breast 480 g = 1.06 lb, spaghetti 840 g = 1.85 lb, black beans 260 g = 9.2 oz, butter 56 g = 2 oz, cheddar 340 g = 12 oz, milk 488 g = 1.08 lb, rice 640 g = 1.41 lb. But the recipes' lines are cooked weights ("640 g White rice, cooked", "840 g Spaghetti, cooked", "480 g Chicken breast, cooked"); the list drops "cooked", so it says "White rice 1.41 lb" and "Spaghetti 1.85 lb" (dry rice and pasta would be about a third of that). Milk is a weight (1.08 lb), not a volume. Black beans, butter, cheddar and milk are all under "OTHER", while rice is under "PANTRY STAPLES" and spaghetti under "PANTRY & GRAINS". Dairy items for a strict-dairy user carry no marker (mac and cheese is pickable). The 2 servings on "Salmon..." matches its recipe.
Screenshot: design/qa/pass2/q2-6-shopping-list.png
Suspect: ui/screens/shopping/ShoppingListViewModel.kt and the aggregation in domain/ (state words dropped; category map)

### Q2-7 · S3 · Eating out · Healthier nearby with location denied says "use a zip code instead" and there is no zip field
Status: Verified fixed in Pass 5a (4321255): Healthier nearby now has 'Use a zip code instead'; the zip lookup itself was not run (no network). Was: Fixed in 6976495 (zip search), needs retest. Zip lookup not verified on a real network
Steps: 1. More > Eating out, scroll to "Find healthier options nearby". 2. "Allow location once and search" > Deny.
Expected: Either a zip code field or no mention of one.
Actual: Red text "Location is off for Intake. Allow it, or use a zip code instead." with buttons "Allow location once and search", "Open app settings", "Change how far in Settings (10 min drive)". No zip field. The zip option exists only in the Swap sheet and only with an AI key ("Use a zip code instead (AI search)"). HealthierNearby.kt also sets "Could not get a location fix. Try a zip code instead." The no-location state itself is clear and does not crash.
Screenshot: design/qa/pass2/q2-7-nearby-zip-copy.png
Suspect: ui/screens/swap/SwapSheet.kt:401 and ui/screens/hub/HealthierNearby.kt:136,167 (shared host)

### Q2-8 · S3 · Eating out C.45 · Menu import with AI off: typing is accepted then refused, and every failed import leaves a source behind
Status: Verified fixed in Pass 5a (4321255): Paste text says AI is needed before typing and Import menu stays disabled; no source was left behind. Was: Fixed in 6976495 (import sources kept only on success), needs retest
Steps: 1. Eating out > Menu import > Add source > restaurant "Test Cafe" > Paste text, enter three item names > Import menu. 2. Add source > Pick PDF > mcdonalds-ireland-allergen-chart.pdf (picker Search route) > Import menu.
Expected: The Paste text tab says it needs AI before the person types; a failed import either leaves nothing or a row that can be retried or removed from the list.
Actual: Text: red "Import failed: AI is off. Turn on "Use AI features" in Settings → AI to use this." and a new saved source "Test Cafe · Pasted text · Never imported · 0 items · Never run". PDF on API 30 (PdfRenderer text unavailable): "No table read on this phone. Reading a PDF on the phone needs Android 15 or later. Nothing was sent anywhere. AI is off or has no key: turn on "Use AI features" and add a key in Settings → AI to use it." with a disabled "Read it with AI" and "Not now"; the source is saved as "Irish McD · PDF · local file · 14 Nov · 0 items · Failed". The explanation is good and honest. The sources stay as empty rows. PDF text extraction on API 35 not tested.
Screenshot: design/qa/pass2/q2-8-text-import-ai-off.png, design/qa/pass2/q2-8b-pdf-api30.png
Suspect: ui/screens/menu/MenuSourcesViewModel.kt (source row is written before the run), MenuSourcesScreen.kt add sheet

### Q2-9 · S3 · Recipes C.42 / I.5 · Meal template editor and list show no allergen verdict; the block comes only at Log, with no reason or way on
Status: Verified fixed in Pass 5a (4321255): template list and editor show chips; a template with dairy opens the Food safety sheet with Log it anyway. Was: Fixed in bc0c873 (template sheet), needs retest. The template editor food picker rows still have no allergen chip
Steps: 1. Meal templates > Post-workout shake > Edit > Add a food or recipe > Cheddar cheese > Save changes. 2. Template row: "451 kcal · 18 g protein · No NFC tag". 3. More > Log now > Breakfast.
Expected: Dairy shown where it is added and on the row; a log of a dairy template blocked or warned (I.5).
Actual: The editor lists Cheddar cheese with "403 kcal per 100 g" and Total 451 kcal (338 + 113, right) and no chip; the saved row has no chip. Log now is blocked: "Not logged: Cheddar cheese now matches your allergen profile", which is safe, but there is no "Log it anyway" path and no reason beyond the food name. The add picker's first row is Baked mac and cheese and it has no chip either.
Screenshot: design/qa/pass2/q2-9-template-blocked.png, design/qa/pass2/q2-13-template-editor.png
Suspect: ui/screens/templates/MealTemplateEditorScreen.kt, MealTemplatesScreen.kt

### Q2-10 · S3 · Eating out · "Healthier options" shows items the app itself calls "Not a standout choice", and published menu macros are labelled "estimate"
Status: Verified fixed in Pass 5a (4321255): no 'Not a standout choice' rows; Sunrise Diner item says 'published on the menu'. Was: Fixed in 6976495 (healthier list filtering, published labels), needs retest
Steps: 1. More > Eating out (wait for the list). 2. Read the cards under "From your menus and saved options".
Expected: A list headed "Healthier options" holds options that rank well; macros from a menu's own published numbers are not called an estimate.
Actual: Fish and chips (880 kcal), Black bean burrito and Oatmeal with berries sit in the list with green text "Not a standout choice". Every card ends its numbers with "estimate", including the Sunrise Diner items whose seed rows have macrosPublished = true. Items whose ingredients were read show "No allergens found" while the same items on Menu import show no chip at all (Q1-26), so the two screens disagree about the same item. The saved options show "Check the label" (right for estimates). Also, the first frame of this screen has no Healthier list and the nearby card is at y 890; the list appears a few seconds later and moves the card down 600 px, so a tap aimed at the card can land elsewhere.
Screenshot: design/qa/pass2/q2-10-healthier-list.png
Suspect: ui/screens/hub/HealthierNearby.kt, domain/swap/NearbyRanking.kt:119, ui/screens/hub/FoodHubScreens.kt:210

### Q2-11 · S4 · Recipes C.36 / C.37 · Recipes list shows no kcal for recipes whose numbers are estimated
Status: Verified fixed in Pass 5a (4321255): list row reads '~62 kcal . per serving, estimated'. Was: Fixed in 795a8d4 (~kcal on the recipe list), needs retest
Steps: 1. More > Recipes after the soup page has shown "~60 kcal" (also my new "Test pag", "~354").
Expected: The list row shows the estimate with a ~ like the detail page.
Actual: The row says only "4 servings", the same as a recipe with no numbers; the page shows "~60 kcal, ~6 g protein".
Screenshot: none (text only, row seen in dump: "Grandma's vegetable soup / 4 servings")
Suspect: ui/screens/hub or plan recipe list row (uses stored kcalPerServing only)

### Q2-12 · S4 · Recipes · "Show the 1 flagged ingredient lines"
Status: Verified fixed in Pass 5a (4321255): 'Show the 1 flagged ingredient line' and '1 line is tagged below'. Was: Fixed in 795a8d4 (plurals), needs retest
Steps: 1. Recipes > Turkey chili with sour cream, read the chip's accessibility text (and the card says "1 line is tagged below").
Expected: Singular for one line.
Actual: Chip description "Show the 1 flagged ingredient lines".
Screenshot: design/qa/pass2/q2-12-chili-plural.png
Suspect: ui/screens/plan/RecipeDetailScreen.kt:201

### Q2-13 · S4 · Recipes / Meal plan / Import · Titles repeated and import copy says "Paste a recipe page" for an address field
Status: Verified fixed in Pass 5b (5822073): Recipes hub card reads 'Import from a link' and 'Paste a recipe link'; the Meal plans bar title and heading are single; the Import screen itself was not opened. Was: Fixed again in ae7e373, needs retest. Was: Still failing in Pass 5a: bar and heading titles are fixed on Import, Meal template and Meal plans, but the Recipes hub card still says 'Paste a recipe page' (the field takes an address); logged again as part of Q5a-13. Was: Fixed in 795a8d4 (copy and titles), needs retest. The plan slot is time-of-day based because plans store no slot
Steps: 1. Recipes > Import from a link. 2. Meal templates > Edit. 3. More > Meal plan.
Expected: One title per screen; copy that matches the control.
Actual: Bar "Import a recipe" over heading "Import from a link", whose copy says "Paste a recipe page" above a field labelled "Recipe web address" (the page cannot be pasted, only fetched). Bar "Meal template" over "Edit meal template". Bar "Meal plans" over "Your meal plan" over "Plan1". The plan screen's footer "Can't eat what I cooked? Find a safer option nearby" uses "safer" (I.9). The offline failure itself is plain: "Could not reach that page. Check the address and your connection." (C.41 passes).
Screenshot: design/qa/pass2/q2-13-template-editor.png
Suspect: ui/screens/recipe/RecipeImportScreen.kt:225, ui/screens/templates/MealTemplateEditorScreen.kt, ui/screens/plan/PlanScreen.kt

### Q2-14 · S4 · Recipes C.42 / R5-11 · Template "Add a food or recipe": the "Recent" block looks alphabetical, and "All saved" repeats the same foods
Status: Verified fixed in Pass 5a (4321255): Recent follows last use (Banana, Peanut butter, Chicken fajita bowls, Salmon ...), then All saved A to Z, with chips. Was: Fixed in 795a8d4 (template picker ordering), needs retest
Steps: 1. Meal templates > Edit > Add a food or recipe.
Expected: Recent items in order of use, then everything saved once.
Actual: RECENT lists Baked mac and cheese, Chicken fajita bowls (recipes first), then Apple, Banana, Bell pepper, Black beans, Broccoli, Cheddar cheese in A to Z order, followed by ALL SAVED starting with Apple again.
Screenshot: design/qa/pass2/q2-14-template-picker.png
Suspect: ui/screens/templates/MealTemplateEditorViewModel.kt

### Q2-15 · S4 · Today / Meal plan · A recipe assigned to a plan week shows on Today as "SNACK · PLANNED"
Status: Verified fixed in Pass 5a (4321255): planned week recipe shows as 'DINNER . PLANNED' with 'Contains your allergen'; but the Swap sheet then says nothing is planned, see Q5a-5. Was: Fixed in 795a8d4 (PlannedMealSlot), needs retest. The plan slot is time-of-day based because plans store no slot
Steps: 1. More > Meal plan > Make a plan (4 weeks) > Choose a recipe for week 1 > Baked mac and cheese. 2. Today.
Expected: A week-level recipe is not turned into a snack for today.
Actual: Next up shows "SNACK · PLANNED · Baked mac and cheese · Contains your allergen · 566 kcal · P 24 · C 49 · F 30". The plan gave no slot or day. The allergen label is right.
Screenshot: none (seen after relaunch; not captured)
Suspect: ui/screens/today (next-up planned item) and MealPlanViewModel

### Q2-16 · S4 · Nutrition C.11 · Searching "almond milk" in Log food returns milk chocolate and no pointer to Drinks
Status: Verified fixed in Pass 5b (5822073): 'almond milk' typed key by key shows 'Drinks: almond milk. Drinks matching "almond milk" are logged from Drinks' and no milk chocolate; the 'almo' ranking is the round 2 item below, Q5b-1. Was: Fixed again in ae7e373, needs retest. Was: Still failing in Pass 5a: typing 'almo' still lists Salmon fillet, baked first under My foods and no pointer to Drinks was seen for the plant milk; logged again as part of Q5a-3 (the field drops characters so 'almond milk' could not be typed whole). Was: Fixed in bc0c873 (almond milk ranking), needs retest. "almo" still ranks Salmon first under My foods
Steps: 1. Log food > Search > "almond milk" (typed slowly), wait for the list.
Expected: The plant milk, or a line saying drinks are in the Drinks chip (C.11).
Actual: USDA rows "Candies, milk chocolate, with almonds" ("Contains milk chocolate", right), Hershey's, Toblerone. No almond milk; the seeded "Almond milk, unsweetened" is a drink and is excluded from this search. The first frame showed the chocolate with an amber "Check the label" before it turned red (same lag as Q1-6). "almo" returns "Salmon fillet, baked" first under My foods (substring match).
Screenshot: design/qa/pass2/q2-16-search-almo.png
Suspect: ui/addfood/AddFoodViewModel.kt (search scope)

Summary for Pass 2: 16 findings. S1: 1 (Q2-1). S2: 1 (Q2-2). S3: 8 (Q2-3 to Q2-10, with Q2-9 possibly fixed at HEAD). S4: 6 (Q2-11 to Q2-16). A retest is needed: rebuild the preview at HEAD, re-run the allergen block (I.4, I.5) and the recipe log paths, and run the tests that need a key or network (Recipe Hunter results, To review, nutrition-menu Skip, Healthier nearby results) on a build with a test key and a stub server, because none of them could be reached here.

Round 2 fixes merged (3fb7f70, bc0c873, eb928a4, 6976495, 795a8d4). Every status above is "needs retest" on a rebuilt preview; nothing here was seen on a device.

### Open from round 2
- Cannot verify on emulator in Pass 6 (9a42584): the seed still has no MAY_CONTAIN recipe or template to Swap to. Was: Cannot verify on emulator in Pass 5a (4321255): the seed has no MAY_CONTAIN recipe or template to Swap to; the CONTAINS case works (Swap dinner lists Turkey chili with sour cream as 'Choose anyway'). Was: Fixed in b73f977, needs retest: Swap logs a MAY_CONTAIN recipe or template without showing the allergen sheet (SwapGate and the warning sheet now cover Swap, Eating out and Healthier nearby).
- Verified fixed in Pass 5a (4321255): picker rows show 'Fits your allergens', 'Only the name was checked', 'Contains ...' and 'May contain milk (trace)'. Was: Fixed in b73f977, needs retest: the template editor's food picker rows have no allergen chip (chips added).
- Verified fixed in Pass 5a (4321255): Butter, Cheddar cheese and Milk carry red 'Contains ...' marks. Was: Fixed in b73f977, needs retest: shopping list dairy items have no allergen marker for a dairy-avoiding user.
- Cannot verify on emulator in Pass 6 (9a42584): no saved shopping rows from an older build in the seed. Was: Cannot verify on emulator in Pass 5a (4321255): the seed has no saved shopping rows from an older build. Was: Fixed in b73f977, needs retest: old saved shopping rows keep their old aisles (aisle repair).
- Partly verified in Pass 7 (b702563): 'almo' lists Salmon fillet, baked (the only food with that substring) and a 'Drinks: almo' pointer row; no Almond food exists to rank against. Was: Fixed in b738d31, needs retest (ranking only; the fast-typing ANR is Q6-3). Was: Still failing in Pass 6: 'almo' typed key by key (1 per s) still lists Salmon fillet, baked first under My foods, no Drinks pointer for 'almo'; 'almond' gives the Almond rows first (design/qa/pass6/q6-almo.png). Was: Still failing in Pass 5b: 'almo' typed key by key lists Salmon fillet, baked first under My foods and Salmon, sweet potato and broccoli under Recipes, and no Almond food row appeared (Q5b-1). Was: Still failing in Pass 5a (4321255): 'almo' still lists Salmon fillet, baked first under My foods (see Q2-16, Q5a-3). Was: Fixed in b73f977, needs retest: the substring "almo" ranks Salmon first under My foods (word-start ranking).
- Still open: Q2-7 zip lookup is not verified on a real network.
- Still open: Q1-14 (Dark chocolate "may contain milk" shown as a hard red warning) is waiting for the owner's decision.

Ripple backlog: a scan found no spots where a clickable is declared before its clip, so no ripple fixes were needed.

---

## Pass 3 — Train
Date: 2026-11-15/16 · Build: 57671ba preview (com.zeroindex.intake.preview) · AVD: phone30 (zone Asia/Tokyo, chosen so midnight was far off; the first seed at America/New_York was abandoned at 00:52 local) · Seed: tools/qa/seed.sh adapted to the preview package (temporary copy, deleted). The emulator was restarted and re-seeded once mid-pass.
Covered: A.3, A.4, A.13 (session bar on Train, Today, Progress), A.11 and A.10 (Train tab only, font 1.5 and landscape), A.12 (dark, Train Exercises only); D.1 to D.6, D.9, D.11 to D.13 (no set edit, see Q3-9), D.15, D.17 (generator, equipment step and starting weights only), D.20 to D.24, D.25 (no empty state, see Q3-10), D.26 (paste-link error only), D.35 (letters and a minus sign rejected in the weight field), D.37 (Done went to Today because the workout was resumed from Today), J.1 to J.5, J.12 (Q3-2), J.14, J.17, J.18 to J.20 on those screens. R5 items 14 to 17 and the owner's list: Videos layout is no longer jumbled (header card, chips Workouts only, Hide Shorts, Filters, search and a "more" menu, clean "Videos need a connection" error); the key copy no longer says Intake never downloads; the muscle figures are real front and back anatomical figures (Train, workout summary, exercise detail, Six regions and Show detail); Equipment has 30+ specific items and "Advanced: pick each item" in the program generator; Training log lists all 6 sessions grouped by week; per-exercise stats (best set, est. max, volume, sessions, chart, best reps, last 3 sessions) are on the exercise detail sheet. Checked against the seed: session volumes (working sets, warm-ups excluded) 5,790 / 8,300 / 5,145 / 7,940 / 6,794 / 9,432 lb, set counts, week totals (14,734 and 19,236), Back Squat total volume 10,309.6 lb, 3 sessions, 11 sets, est. max 197.3 lb (Epley), PR deltas; a live workout (50x8 and 55.5x8 = 844 lb, est. max 70.3 up 3.6 lb) all agree. Train first-run tip: hidden, correct. Program day advanced to Lower B after finishing Upper B.
Not reached: "Saved at the top", "load more" and Shorts detection by orientation (a fake YouTube key was saved to see the keyed layout; with no network nothing loads, so no results to test; the fake key stayed only in that emulator's app data and the reseed cleared it). Offline-download Downloaded view contents (no network). Onboarding equipment picker (needs a fresh install; the same picker was seen in the program generator). D.7 beyond the countdown (screen-off, vibrate), D.8, D.10 relaunch resume, D.14 (Pull-Up in a workout), D.16, D.18, D.19, D.27 to D.34, D.36, D.38 (the 17:30 time did show on the session). A.1, A.2, A.5 to A.9, A.14. Dark theme on the workout screens. Progress and Today beyond the session bar.
Logcat: clean. No FATAL EXCEPTION, no ANR for com.zeroindex.*, no StrictMode lines, empty crash buffer, checked at the end. The fake key string does not appear in logcat.
Seen on emulator only (Android 11, software rendering, no KVM). Not seen on a device: performance, haptics, real keyboard and IME behaviour, vibration at rest end, how the layout jump in Q3-11 feels on a fast phone. Screenshots are at scale 1.9 of the 1080x2280 screen (540x1200).

### Q3-1 · S3 · Settings / Train D.6 · Imperial users get a 44.1 lb bar and plates of 55.12, 44.09, 33.07, 22.05, 11.02, 5.51, 2.76 lb
Status: Verified fixed in Pass 6 (9a42584): fresh install, lb: bar 45, plates 45,35,25,10,5,2.5. Was: Fixed in 504dae6, needs retest. Was: Still failing in Pass 5b: fresh install: bar is 45 lb, but 'Plates available per side (lb)' still reads 55.12,44.09,33.07,22.05,11.02,5.51,2.76. Logged as Q5b-3. Was: Fixed in cc7bc40, needs retest (a lb user now gets a 45 lb bar and lb plates; a lb user who deliberately stored a 20 kg bar now also gets 45 lb)
Steps: 1. Seeded install (units lb). 2. You > Settings > Training.
Expected: a bar of 45 lb and plates of 45, 35, 25, 10, 5, 2.5 lb (the subtitle in Settings says "2:00 rest · 44.1 lb bar" too).
Actual: Bar weight "44.1 lb"; "Plates available per side (lb, comma separated)" holds the metric plates converted to lb. The plate maths and the smallest step up use them. The Barbell (Olympic) row still reads "2 in sleeves, 20 kg bar" for a lb user.
Screenshot: design/qa/pass3/q3-1-plates.png
Suspect: data/settings (defaults converted from kg), ui/screens/settings Training

### Q3-2 · S3 · Train J.12 · Videos "Paste a YouTube link": the field and the Open button are hidden behind the keyboard and the page will not scroll to them
Status: Verified fixed in Pass 5b (5822073): Paste a YouTube link field is scrolled into view above the keyboard; the Open this video button is below it, the keyboard action reaches it. Was: Fixed in cc7bc40, needs retest
Steps: 1. Train > Videos with no key. 2. Tap the link field, type.
Expected: the page scrolls so the field stays visible.
Actual: the keyboard covers the field and "Open this video"; swiping the page does nothing. The text can only be seen after Back hides the keyboard. (After Enter the error "That does not look like a YouTube video link." is clear.)
Screenshot: design/qa/pass3/q3-2-keyboard.png
Suspect: ui/screens/videos/VideosSection.kt (NoKeyCard, imePadding)

### Q3-3 · S2 · Train D.21 · Exercise detail muscle map leaves out the main muscle for Pull-Up
Status: Verified fixed in Pass 5b (5822073): Pull-Up detail draws the lats on the back figure and says 'Main: lats. Also: biceps'. Was: Fixed in cc7bc40, needs retest
Steps: 1. Train > Exercises, search "Pull-Up", open it. 2. Look at Muscles worked.
Expected: Lats (the listed primary muscle: "Lats (also biceps)") highlighted on the back figure and "Main: lats. Also: biceps".
Actual: only the biceps (and forearm region in Six regions) are highlighted; the caption and screen-reader text say "Also: biceps" with no Main. Lats are never drawn, so the back figure is empty. Squat (quads) and the session summary draw their main muscles correctly.
Screenshot: design/qa/pass3/q3-3-pullup-lats.png
Suspect: domain muscle mapping for Lats (likely the same for Dumbbell Row), ui/components MuscleMap

### Q3-4 · S4 · Train D.21 · Exercise detail "Your history": misaligned grid and inconsistent numbers
Status: Verified fixed in Pass 5b (5822073): Back squat history: BEST SET 160 lb x 7, est. max 197.3 lb, TOTAL VOLUME 10,310 lb, aligned grid; Pull-Up grid aligned. A bodyweight total of '0 lb' is Q5b-16. Was: Fixed in cc7bc40, needs retest
Steps: 1. Exercise detail of Barbell Back Squat (seed).
Expected: BEST SET and HEAVIEST on one baseline; numbers formatted like the rest of the app.
Actual: "BEST SET" sits lower than "HEAVIEST" and its value is on the next row while HEAVIEST's value is beside the label; "TOTAL VOLUME 10309.6 lb" has no thousands separator and a decimal (the log shows "9,432 lb"); weights read "160.0 lb" here and "160.0 × 7" without a unit in the session summary. The chart has no date axis and no label for its top point; "Last 3 sessions · 1 records so far" should be "1 record".
Screenshot: design/qa/pass3/q3-4-detail-grid.png
Suspect: ui/screens/exercises detail sheet

### Q3-5 · S4 · Shell A.13 · "Workout in progress" card and the session bar both show, and the bar covers content
Status: Verified fixed in Pass 5b (5822073): one entry point on Train, Today and Progress (the session bar); no duplicate card; Progress content scrolls under the floating bar. Was: Fixed in cc7bc40, needs retest (the bar and card are split; could not confirm why the bar covered Progress content, so the retest must check it)
Steps: 1. Start a workout, minimise it. 2. Look at Train, then Today, then Progress.
Expected: one entry point; list content clear of the bar.
Actual: Train shows the "WORKOUT IN PROGRESS · Resume workout" card and the session bar above the pill at once; Today shows its own "Workout in progress · Resume" row under the bar; on Progress the bar covers the card labels ("AVG KCAL / DAY"). The bar works (resumes the workout, counts time and rest).
Screenshot: design/qa/pass3/q3-5-bar-and-card.png
Suspect: ui/navigation (session bar), Train and Today hub screens

### Q3-6 · S4 · Train D.20 · "Bodyweight · bodyweight" repeated in every bodyweight row of the library and detail
Status: Verified fixed in Pass 5b (5822073): library rows read 'Core · Bodyweight' and 'Lats (also biceps) · Bodyweight · rest 120s'. Was: Fixed in cc7bc40, needs retest
Steps: 1. Train > Exercises.
Expected: "Core · Bodyweight".
Actual: "Core · Bodyweight · bodyweight" (also "Lats (also biceps) · Bodyweight · bodyweight · rest 120s" on the detail). The equipment filter itself is right: with My equipment on, only barbell, dumbbell and bodyweight rows appear, no machines or cables. The row cards also have almost square corners next to the rounded cards elsewhere.
Screenshot: design/qa/pass3/q3-6-library-labels.png
Suspect: ui/screens/exercises row subtitle

### Q3-7 · S3 · Shell A.10 / A.11 · Landscape at font scale 1.5: rail labels clipped ("Nutritio", "Progres")
Status: Verified fixed in Pass 5b (5822073): font 1.5 in landscape on the emulator: rail labels Today, Nutrition, Train, Progress are whole on Today, Nutrition and Progress. Was: Fixed in cc7bc40, needs retest
Steps: 1. `settings put system font_scale 1.5`, rotate to landscape. 2. Train.
Expected: full labels or icons only.
Actual: the side rail clips two labels. At 1.5 in portrait the Train root is fine (pill labels fit). Landscape at normal font was not checked.
Screenshot: design/qa/pass3/q3-7-rail-clip.png
Suspect: ui/navigation rail

### Q3-8 · S3 · Programs D.17 · "Make me one" starting weights are not loadable: 149, 109, 49, 179, 124, 169, 144 lb
Status: Verified fixed in Pass 5b (5822073): Make me one gives Bench press 135 lb, Barbell row 110 lb, Overhead press 50 lb to start, with the home-gym equipment. Was: Fixed in cc7bc40, needs retest (the program generator has no heaviest-dumbbell field, it reads the profile; starting weights now round to loadable values and cap at the dumbbell limit)
Steps: 1. Train > Programs > Make me one, accept defaults to the end.
Expected: starting weights rounded to what the user's plates can make (for example 150, 110, 50 lb).
Actual: "Bench press 149 lb to start", "Barbell row 109 lb", "Overhead press 49 lb" and so on, probably a result of the metric plate list in Q3-1. The questions and results otherwise honour equipment (no machines seen in the first three days; not every exercise name was read).
Screenshot: design/qa/pass3/q3-8-generator-weights.png
Suspect: domain program generator / plate rounding

### Q3-9 · S3 · Train D.13 · A finished session cannot be edited: set rows and exercise names do nothing when tapped
Status: Verified fixed in Pass 5b (5822073): tapping a set in a finished session opens an edit sheet (weight, reps, done, delete this set, Save); the screen is still titled Workout summary with a Done button. Was: Fixed in cc7bc40, needs retest
Steps: 1. Train > All sessions > a session. 2. Tap "Set 1: 160.0 lb × 7" or "Barbell Back Squat".
Expected: edit a set (checklist D.13), or the rows look read-only; an exercise name opens its detail.
Actual: nothing happens; the screen is titled "Workout summary" with a Done button even when opened from the log, and there is no edit or delete.
Screenshot: design/qa/pass3/q3-9-session-sets.png
Suspect: ui/screens/train summary and history

### Q3-10 · S3 · Videos D.23 / D.25 · With no key there is no Saved or Downloaded entry, and with a key Saved has no visible place
Status: Verified fixed in Pass 5b (5822073): Train > Videos with no key starts with 'Saved · 0 / No saved videos yet'; a Downloaded entry without a key was not seen. Was: Fixed in cc7bc40, needs retest
Steps: 1. Train > Videos with no key (offline downloads on or off). 2. With a key, open the "more" menu.
Expected: Saved and Downloaded reachable at the top, with an empty state ("No saved videos yet").
Actual: with no key the screen is the key card plus "Paste a YouTube link" only (copy is correct and no longer contradicts the download switch). "Downloaded videos" exists only in the more menu, which only exists with a key, so downloads are unreachable without one. With a (fake) key the top row is Search, more menu, chips Workouts only, Hide Shorts, Filters; no Saved chip or section was visible with zero saved videos, so "Saved at the top" could not be confirmed. The offline-downloads switch shows the yt-dlp warning dialog ("Before you turn this on", ToS, suspension risk) and turns on only after "I understand, turn it on".
Screenshot: design/qa/pass3/q3-10-nokey-videos.png (menu: design/qa/pass3/videos-key-menu.png, warning: design/qa/pass3/dl-warning.png)
Suspect: ui/screens/videos/VideosSection.kt

### Q3-11 · S3 · Train D.6 / J.4 · Marking a set done shifts the rows down, so a quick second tap un-ticks the wrong set
Status: Verified fixed in Pass 5b (5822073): ticking sets 1 and 2 in a row: both ticked, rows did not move, the rest bar sits at the bottom. Was: Fixed in cc7bc40, needs retest
Steps: 1. Start Upper B. 2. Tap done on set 2, then promptly on set 3.
Expected: rows stay put under the finger.
Actual: the rest bar appears above the card and pushes every row down about 100 px; the second tap landed on set 2 and unticked it (set 3 stayed open). The countdown, +15 s and Skip work. A "New estimated max" row also appears under a PR set and pushes the next row down. Weight shows "50.0" before a set is done and "50" after it. Whether this is as easy to hit on a phone is not known.
Screenshot: design/qa/pass3/q3-11-set-rows.png
Suspect: ui/screens/train ActiveWorkout rest bar placement

### Q3-12 · S4 · Settings API keys · The OpenRouter Save button looks different from the other two
Status: Verified fixed in Pass 5b (5822073): the three Save buttons are the same grey pill. Was: Fixed in cc7bc40, needs retest
Steps: 1. Settings > API keys, scroll.
Expected: the three Save buttons share one style.
Actual: OpenRouter's Save is a very faint tile with no clear shape; USDA and YouTube have a grey pill. The YouTube key row after saving reads "YouTube key saved, encrypted on this device." with Dismiss, fine. (Not a Train screen; seen on the way.)
Screenshot: none kept (the API keys screen was not saved); see the Settings API keys screen.
Suspect: ui/screens/settings ApiKeys

Summary for Pass 3: 12 findings. S1: 0. S2: 1 (Q3-3). S3: 7 (Q3-1, 2, 7, 8, 9, 10, 11). S4: 4 (Q3-4, 5, 6, 12). A retest is needed after fixes, plus the parts that need a real key and network: Saved at the top, load more, Shorts hiding by orientation, downloads. Nothing here was seen on a device.

---

## Pass 4 — Progress, settings, backup, profile
Date: 2026-11-15 · Build: 57671ba preview (com.zeroindex.intake.preview, the prebuilt APK; later commits only touched docs) · AVD: phone30 (zone America/Los_Angeles, set so midnight was far off; the pass ran 12:35 to 14:50 local) · Seed: tools/qa/seed.sh adapted to the preview package (temporary copy, deleted; data dated 2026-11-15, which is a Sunday). The app was wiped with `pm clear` once for the first-run and import checks.
Covered: A.1 (first run, all 10 onboarding steps, equipment picker Advanced, allergen step), A.2 and A.5 to A.9 only where they touch the tabs visited (Today, Nutrition, Progress, Settings), A.10/A.11 (font 1.5 on Nutrition and Progress; landscape on Progress), A.12 (dark on Today, Nutrition, Progress), A.14 (the only deep link in the manifest: `intake://log-template/<id>` by NDEF_DISCOVERED, good id and bad id); E.1 to E.3 (numbers recomputed from the seed), E.4 not run (no weigh-in logged), E.5/E.6 not reached, E.7/E.8 (averages), E.9 (looked for it, see Q4-23), E.10, E.11, E.12 (PDF saved and its table compared with the seed), E.16 (scrubbing, tooltip), E.17, E.19, E.21 not run (no 1 y data), E.22, E.23 (empty Progress on the fresh install), E.25, E.26; F.1 to F.3, F.5, F.7, F.16 (empty profile); G Appearance and units (7 of 12 controls; the Today card switches not toggled), Nutrition (12 of 15, Contest and Reverse show only in their phase), Water and drinks (Daily water target not edited, weather, city lookup offline, Quick sizes read), Training (read all, 3 changed), Health Connect (unavailable state only), Food database (6 flipped except the OFF account fields), Videos (all 4 read, 2 flipped), Reminders (11 of 12 read, 4 flipped), Sync and export (all rows, JSON export plain and encrypted), AI optional (see Q4-17), API keys (OpenRouter fake key saved, tested, removed), About and help (all 7 rows read, privacy screen read); H.1 to H.6, H.8, H.9 to H.11 partly (see below), H.12; J checks on every screen seen.
Not reached: E.4, E.5, E.6, E.9 (no such block, Q4-23), E.13, E.14, E.15, E.18, E.20 (the card range stayed Mon to Sun with check-in day Friday, not judged), E.21, E.24; F.4, F.6, F.8 to F.15; Settings: Today card switches, Daily water target, Measure my sweat rate, Drink values, Weather permission sheet, the 17 Health Connect switches and "Allow all Health Connect permissions" (hidden because Health Connect is unavailable on Android 11), OFF username and password (typed text was lost on Back, nothing saved), Export reminder and Reminders actually posting a notification, CSV exports, Linked devices and Stop syncing here (shown only when paired), AI group (Q4-17), Menu import, USDA and YouTube key tests; H.5 wrong passphrase retry (blocked by Q4-8), H.7 uninstall variant (replaced by pm clear), H.10 only via schema 99 file; A.2 rotation of other tabs, A.6, A.7; Settings search box. Dark theme for Settings was not looked at.
Logcat: two ANRs for com.zeroindex.intake.preview. (a) 13:23, while scrolling Settings > Water and drinks with Advanced expanded: main thread in Compose layout measure only (no app frame), 116 percent CPU, software rendering. I put it down to the emulator and did not log it as a finding. (b) 14:19, tapping Open in the "Encrypted backup" passphrase dialog: main thread inside `BackupCrypto.key` (PBKDF2) called from `ExportViewModel.unlockImport`, that is the app, see Q4-8. No FATAL EXCEPTION, no StrictMode lines, empty crash buffer. The fake OpenRouter key and the fake passphrases do not appear in logcat.
Seen on emulator only (Android 11, software rendering, no KVM, 5 to 40 s per action, 3-button navigation). Not seen on a device: performance, the feel of the encrypted import freeze, real notifications, gesture navigation in landscape (Q4-22), Health Connect on API 34+, the Play Store button. Screenshots are scale 1.9 of the screen (540x1200). Q3-1 (44.1 lb bar and metric plates for a lb user) still reproduces on this build; it is already logged and fixed in cc7bc40, so it is not logged again here.
Checked and fine: weigh-in values (192.0 lb latest, trend 193.5 lb, 13 weigh-ins in 1 M, 17 in All, goal 185.0 lb, waist 36.3 in, chest 41.7 in, arm 14.4 in; all agree with the seed), the 13 to 15 day gap drawn dotted, scrubbing the weight chart shows "Mon 9 Nov 2026 194.0 lb · Manual" per weigh-in, the range chip survives rotation, the check-in table and the exported PDF agree with a hand sum of the seeded days (Mon 9 Nov 2,449 kcal ... Sat 14 Nov 2,393), switching lb/in/fl oz/g to kg/cm/ml/oz and back leaves every value unchanged on Today, Progress, You, Edit profile (183 cm, 87.1 kg, 83.9 kg goal, 72 in, 192 lb, 185.0 lb), a full export, `pm clear`, import round trip gives an export that is identical row for row and setting for setting (562 changes: allergen profile 1, body metric 17, food 32 new plus the 4 seeded drinks, food log 224, hydration 120, recipes 5, templates 2 and 7 items, menu source 1 and items 8, program 1, days 4, exercises 17, workout sessions 6, set logs 87, coach suggestions 2, settings 27), import of a truncated file, an unrelated JSON, random bytes, a schema 99 file and a file with a text in a number field all stop with a message and change nothing, importing the same file again reports 0 changes, the encrypted file is not readable as text, export file names and "Last export" update, a fake OpenRouter key saves, tests ("Could not reach OpenRouter") and removes, the offline city lookup says "No place found. Check the spelling, or check that you are online.", Videos group text is now accurate (Intake downloads a video only if you turn on Offline downloads), the pairing code field is empty (see Q4-24).

### Q4-1 · S2 · Progress E.7, E.8 · Average calories and "days logged" change with the range, and a logged day goes missing
Status: Verified fixed in Pass 5b (5822073): AVG KCAL / DAY is 2,104 on 1M, 3M, 6M, 1Y and All (hand average of the 20 seeded days); 8 days over matches the CSV. Was: Fixed in de5ed6e, needs retest
Steps: 1. Seeded install, Progress with 1M (or 3M, 6M). 2. Read "This week" AVG KCAL / DAY and the Calories vs target caption. 3. Tap All and read both again.
Expected: the same 21 logged days (26 Oct to 15 Nov) give the same average in every range: 2,118 kcal (hand average of the seeded days, today's partial 650 kcal included), "21 logged days" and 9 days over the 2,260 target.
Actual: 1M, 3M and 6M show "2,102", "Calories per day across 20 logged days, target 2260 kcal, 8 days over." All shows 2,118. One over-target day (2,448 kcal on 1 Nov, or 2,449 on 9 Nov, either gives 2,102) is missing from the daily series in the shorter ranges. 1 Nov 2026 is the day the clocks go back in this zone.
Screenshot: design/qa/pass4/q4-1-days-logged-and-calories.png and q4-1-all-range-2118.png
Suspect: ui/screens/progress/ProgressViewModel.kt (series built from observeDailyIntake) and data/db/dao/FoodLogDao.kt observeDailyIntake (fixed 86,400,000 ms buckets from an offset taken at `from`, a lead only, not confirmed)

### Q4-2 · S2 · Progress E.7 · Days logged: headline says 21 of 21, the grid says "20 of 28", and today's square is empty
Status: Verified fixed in Pass 5b (5822073): '20 of the last 28 days logged' in the headline and under the grid; legend explains filled, pale, grey and outline. Was: Fixed in de5ed6e, needs retest
Steps: 1. Seeded install (today has 650 kcal logged). 2. Progress, scroll to Days logged.
Expected: one number. Today's square filled, since food is logged today.
Actual: headline "21 of the last 21 days logged" and the line under the grid "20 of 28 days logged." Today's square (bottom right) is an empty outline, yesterday's is paler than the rest with no legend. The first row of seven squares is empty, because the grid is 28 days and only 21 have data, which makes the card look like it is missing a week.
Screenshot: design/qa/pass4/q4-1-days-logged-and-calories.png
Suspect: ui/screens/progress/ProgressScreen.kt (adherence grid, line 589), same cause as Q4-1 possible

### Q4-3 · S3 · Progress E.1 · The card titled "This week" is labelled "last month" and shows range data
Status: Verified fixed in Pass 5b (5822073): card is titled Averages with the range caption. Was: Fixed in de5ed6e, needs retest
Steps: 1. Progress, 1M. 2. Look at the card titled "This week" (AVG KCAL / DAY, AVG WEIGHT CHANGE, MAINTENANCE KCAL, PROTEIN TARGET HIT).
Expected: either week figures, or a title that matches the range.
Actual: title "This week" with the caption "last month" (and "last 6 months", "all time" on the other chips). The numbers cover the whole range (30 days, 6 months...), and "Protein target hit 24%" is always the last 21 days whatever the chip says.
Screenshot: design/qa/pass4/q4-3-this-week-caption.png
Suspect: ui/screens/progress/ProgressScreen.kt WeeklySummaryCard (title "This week", caption = state.range.caption); the protein figure comes from refreshAdherence (fixed 21 days)

### Q4-4 · S3 · Progress E.11 · Weekly check-in: the same week gives different intake, weight and protein numbers in three places
Status: Verified fixed in Pass 5b (5822073): week Nov 9 to 15: top card 1,903 kcal and 193.2 lb (-0.3 lb), Your week 1,903 kcal, -0.3 lb, 7 of 7 days; hand average of the seeded days is 1,903. Was: Fixed in de5ed6e, needs retest
Steps: 1. Seeded install, Progress > Weekly check-in, week of Nov 9 to 15. 2. Compare the top card, the "Your week" list and the PDF (Export PDF).
Expected: one figure for each.
Actual: top card AVG INTAKE 1,972 kcal and AVG WEIGHT 193.0 lb "· -0.5 LB"; "Your week" list: Calories (average) 2,193 kcal, Weight trend "−0.9 lb (−0.6% a week)", Protein (average) 135 g, Days logged "6 of 7"; PDF table average row: 2,058 kcal (after the NFC log in Q4-25, 1,972 before), protein 122 g, "7 of 7 days logged". The list leaves out today's partial day, the card and the PDF include it, and the protein suggestion says "135 g on the 6 days you logged".
Screenshot: design/qa/pass4/q4-4-checkin-mismatch.png
Suspect: domain/coach/CoachEngine.kt (logged days filter, line 605) against the summary card and the PDF writer

### Q4-5 · S2 · You / Progress F.7, J.18 · Water and weights shown in ml and kg for a user on fl oz and lb
Status: Verified fixed in Pass 5b (5822073): Your plan: 'Water ... 103.1 fl oz', '0.54 fl oz per lb x 192.5 lb', 'body weight (192.5 lb)'; check-in water suggestion in fl oz. Was: Fixed in de5ed6e, needs retest
Steps: 1. Seeded install (lb, in, fl oz). 2. You > How this was worked out. 3. Progress > Weekly check-in, the Water suggestion.
Expected: "103.1 fl oz a day", "16.9 fl oz on each of your 4 training days", "87.1 kg" shown as lb.
Actual: Your plan: "3,050 ml a day", "35 ml per kg x 87.1 kg = 3,050 ml, plus 500 ml on each of your 4 training days.", "Protein 2.4 g/kg of body weight (87.1 kg)". Check-in: "You averaged 2,254 ml a day against a target of 3,157 ml (71%)". Every other screen (You card, Today, Settings) shows fl oz and lb.
Screenshot: design/qa/pass4/q4-5-plan-ml-kg.png
Suspect: domain plan explanations (YourPlan) and domain/coach suggestion text

### Q4-6 · S2 · You / Settings F.2, G Nutrition · "Work my targets out from my data" on: You says "worked out from your data", the numbers are still the manual ones
Status: Verified fixed in Pass 5b (5822073): auto targets on: You and Settings both show 2,341 kcal, 166 g protein; turning it off leaves the computed values in place, which loses the hand-set macros, Q5b-8. Was: Fixed in de5ed6e, needs retest
Steps: 1. Seeded install (manual 2,260 kcal, 170 g protein). 2. Settings > Nutrition, switch "Work my targets out from my data" on. 3. Settings > Nutrition > Recalculate now. 4. You.
Expected: either the targets change to the calculated ones, or the card keeps saying "set by hand".
Actual: You header changes to "worked out from your data" and Calories reads "from your weight trend and a gentle pace" over **2,260 kcal**, Protein 170 · 70 · 245 g (the manual numbers). How this was worked out on the same screen says 2,338 kcal, 209 g protein, 238 g carbs, 61 g fat ("There is no baseline yet, so this is an estimate"). Today still shows 2,260. Recalculate now gives no message and changes nothing (Progress says "Needs 14 days of weigh-ins and logs" although 21 days are logged, see Q4-1). Switching it off again keeps 2,260 (good).
Screenshot: design/qa/pass4/q4-6-auto-targets.png and q4-5-plan-ml-kg.png
Suspect: data/repository TargetRepository (auto target row not written), ui/screens/you

### Q4-7 · S3 · You / Settings G Nutrition · Your plan ignores the Protein and Fat settings
Status: Verified fixed in Pass 5b (5822073): plan uses 1.9 g/kg: 166 g = 1.9 x body weight 192.5 lb, and the Settings text says the same. Was: Fixed in de5ed6e, needs retest
Steps: 1. Settings > Nutrition shows Protein 1.9 g per kg, Fat 0.8 g per kg. 2. Turn auto targets on, You > How this was worked out.
Expected: protein 1.9 g per kg (about 159 g at the 83.9 kg goal weight, as the setting's own text says "Per kg of goal weight").
Actual: "Protein 2.4 g/kg of body weight (87.1 kg)", 209 g, "fat at least 0.7 g/kg" (61 g). The settings are not used, and the setting says per kg of goal weight while the plan screen uses current weight.
Screenshot: design/qa/pass4/q4-5-plan-ml-kg.png
Suspect: domain plan/targets calculator

### Q4-8 · S2 · Backup H.5 · Opening an encrypted backup freezes the app (ANR on the emulator)
Status: Verified fixed in Pass 5b (5822073): encrypted import shows 'Encrypting and saving...' on export, then a wrong passphrase gives 'That passphrase did not open it. Try again.' and the right one the preview; no ANR in those runs. One ANR happened while typing into the dialog on the first attempt, Q5b-17. Was: Fixed in de5ed6e, needs retest
Steps: 1. Settings > Sync and export, type passphrase "qapass1", Export everything, Save (file .intake). 2. Import a backup, pick the .intake file. 3. In "Encrypted backup" type any passphrase and tap Open.
Expected: a spinner, the UI stays alive, the preview or "wrong passphrase" appears.
Actual: the app stops answering, "Input event dispatching timed out ... Waited 5007ms", ANR, the app is killed and the user lands on the launcher. Trace: main thread in `PKCS5S2ParametersGenerator.generateDerivedKey` (210,000 iterations) called from `BackupCrypto.key`, `BackupCrypto.decrypt`, `BackupService.preview`, `ExportViewModel.unlockImport`. On a real phone this is a freeze of a fraction of a second to seconds, not an ANR, but the work is on the main thread. The wrong-passphrase retry (H.5) could not be reached because of it.
Screenshot: design/qa/pass4/q4-8-encrypted-import.png (the dialog before Open)
Suspect: ui/screens/backup/ExportViewModel.kt unlockImport (viewModelScope.launch on Main), domain/backup/BackupCrypto.kt

### Q4-9 · S3 · Backup H.4 · Import error messages are wrong or technical
Status: Verified fixed in Pass 5b (5822073): truncated file: 'This backup is damaged or incomplete'; bad number: 'A value in the body metric data (the "weightKg" field) is damaged, so the backup cannot be ...'. Was: Fixed in de5ed6e, needs retest
Steps: 1. Import intake-export (first 300,000 bytes only), then a copy where one body_metric weightKg is the text "abc".
Expected: "This backup is damaged or incomplete" for the first, a plain sentence for the second.
Actual: truncated file: "That file is not an Intake backup (it is not JSON)." (it is an Intake backup that is cut off). Bad number: "The body_metric table in the backup does not match this app: Failed to parse literal '"abc"' as a double value at path: $.weightKg JSON input: .....m":36.5,"createdAt":1791813000000". Nothing was imported in either case (Today stayed empty), so the safety part is fine.
Screenshot: design/qa/pass4/q4-9-import-error.png
Suspect: data/backup/BackupService.kt (ImportException messages)

### Q4-10 · S2 · Allergens A.1 · A fresh install already avoids "Milk and dairy"
Status: Verified fixed in Pass 5b (5822073): fresh install step 6 reads 'Nothing selected yet'; Nutrition strip says 'No allergens set. Add yours'. Was: Fixed in de5ed6e, needs retest
Steps: 1. `pm clear`, launch. 2. Welcome, Get started, Continue through to step 6 "Foods you avoid". 3. Finish, Nutrition tab.
Expected: nothing selected for a new user ("You avoid 0"); the user chooses.
Actual: step 6 opens with "You avoid 1." and Milk and dairy highlighted; pressing Continue keeps it. Nutrition shows the red chip "Avoiding: Milk and dairy" and Settings > Allergens has it On at "Avoid" with "180 keywords · you avoid this" though the user never chose it. A user who has no dairy allergy gets warnings they did not ask for and cannot tell the profile was not theirs; a user who skips the step believing "nothing chosen" is wrong.
Screenshot: design/qa/pass4/q4-10-fresh-allergen.png
Suspect: the default allergen_profile row created at first run (data/db seed or AllergenRepository)

### Q4-11 · S3 · Onboarding A.1 · Equipment step: two cards selected at once, and a kg bar for a lb user
Status: Verified fixed in Pass 5b (5822073): Advanced is selected alone and its list opens; 'Barbell (Olympic): 2 in sleeves, 45 lb bar'; the grey 1990, 70, 170 placeholders in About you are still there, Skip leaves them empty. Was: Fixed in de5ed6e, needs retest (the bar-weight half was already fixed by cc7bc40)
Steps: 1. Fresh install, step 2 keep lb. 2. Step 5 "What can you train with?", tap "Advanced: pick each item".
Expected: Advanced selected alone, or the list shown under the selected card. Weights in lb.
Actual: "Full gym" stays highlighted and "Advanced" also highlights, with every item ticked (Full gym's set); the item list sits below the fold with no cue. First item reads "Barbell (Olympic): 2 in sleeves, 20 kg bar" (the same text is in Settings > Training). Also step 3 "About you" shows 1990, 70 and 170 as grey placeholders that look like filled values, and Skip leaves them empty (good, no weigh-in was created).
Screenshot: design/qa/pass4/q4-11-equipment.png
Suspect: ui/onboarding equipment step, domain equipment catalogue labels

### Q4-12 · S3 · You F.16 · Empty profile claims a plan "worked out from your data"
Status: Verified fixed in Pass 5b (5822073): empty profile: 'Add your basics to build a plan', Goals and plan 'waiting for your details', 'No target yet'. Was: Fixed in de5ed6e, needs retest
Steps: 1. Fresh install, skip every question. 2. You.
Expected: prompts only; no claim that anything was worked out.
Actual: the card says "You" with avatar "Y" (no name) and "Add your basics to build a plan" (good), but Goals and plan reads "worked out from your data", Calories "from your weight trend and a steady pace" with "No target yet", Protein "2.0 and 0.8 g per kg" with "—", Phase "Maintain · steady". Today shows "Drink some water: 0 of 84.5 fl oz so far, a little behind for this time of day" from an unknown weight.
Screenshot: design/qa/pass4/q4-12-empty-you.png
Suspect: ui/screens/you YourPlan header strings

### Q4-13 · S3 · Settings G Nutrition · "Show salt or sodium on the dashboard: Off" still shows a Sodium tile
Status: Verified fixed in Pass 5b (5822073): with Off the Nutrition dashboard shows Fibre, Sat fat and Sugar only. Was: Fixed in de5ed6e, needs retest
Steps: 1. Settings > Nutrition, set Off. 2. Nutrition tab.
Expected: no sodium or salt tile ("Adds one nutrient tile in Nutrition").
Actual: the grid shows Fibre, Sat fat, Sugar and "Sodium 148 mg" with Off, with Sodium, and on the fresh install; with Salt it shows "Salt 0.4 g" (correct conversion). Off changes nothing.
Screenshot: design/qa/pass4/q4-13-sodium-off.png
Suspect: ui/screens/nutrition nutrient grid (always four tiles)

### Q4-14 · S3 · Settings G Training, E.10 · "Show jump tests in Progress" adds nothing to Progress
Status: Verified fixed in Pass 5b (5822073): with the switch on, Progress shows a Jump tests card with Log a jump test; a test (20 in vertical, 90 in reach, total 110.0 in) was logged and listed. Deleting it has no confirmation, Q5b-10. Was: Fixed in de5ed6e, needs retest
Steps: 1. Settings > Training, switch "Show jump tests in Progress" on. 2. Progress, scroll to the end.
Expected: a jump test card or entry.
Actual: Progress is identical (weight, This week, Days logged, Calories, Measurements, Weekly check-in, Training stats). No card, no way to log a jump, with the setting on (also after import, where it is true).
Screenshot: design/qa/pass4/q4-14-jump-tests.png
Suspect: ui/screens/progress (jump card missing or needs data and says nothing)

### Q4-15 · S3 · Settings G Training · "Heaviest dumbbell" is blank though the profile holds 36 kg
Status: Verified fixed in Pass 5b (5822073): 'Heaviest dumbbell (lb)' reads 79.4. Was: Fixed in de5ed6e, needs retest
Steps: 1. Seeded install, Settings > Training, scroll to Free weights, Dumbbells (adjustable) is ticked. 2. Read the "Heaviest dumbbell (lb)" field. 3. The export's profile_json has "dumbbellMaxKg":36.0.
Expected: 79.4 lb (or 79) in the field.
Actual: empty field with the placeholder only.
Screenshot: none kept (Settings > Training, Free weights, below the Barbell rows)
Suspect: ui/screens/settings equipment control (dumbbellMaxKg not read back)

### Q4-16 · S3 · Settings G Health Connect · "Needs an update" on a phone that has no Health Connect, and the button does nothing
Status: Verified fixed in Pass 5b (5822073): reads 'Health Connect is not installed on this phone'; the button opens the Play Store page in the only browser here. Was: Fixed in de5ed6e, needs retest
Steps: 1. Android 11 emulator, Settings > Health Connect. 2. Tap "Open in Play Store".
Expected: "Health Connect is not available on this phone" (Android 11 has none and there is no Play Store here), and a button that says what happened if it cannot open the store.
Actual: "Health Connect needs an update. Nothing else in Intake depends on it." with an outlined "Open in Play Store"; tapping it gives no screen change and no message. None of the 17 read and write switches or "Allow all Health Connect permissions" are shown. No crash.
Screenshot: design/qa/pass4/q4-16-health-connect.png
Suspect: ui/screens/settings HealthStatusHeader

### Q4-17 · S2 · Settings G AI · The AI group shows none of its main controls
Status: Verified fixed in Pass 5b (5822073): AI group shows Use AI features, Privacy for AI requests, Suggest free models, Daily insight, Reword my check-in. Was: Fixed in de5ed6e, needs retest
Steps: 1. Seeded install. 2. Settings > AI (optional) (summary "Off"). 3. Wait 30 s, leave and enter again.
Expected: Use AI features, Monthly budget, Privacy for AI requests, Suggest free models, Daily insight, Reword my check-in, Refresh saved menus weekly, then Advanced.
Actual: the page is the intro line and an "Advanced 14" toggle only. Expanding Advanced shows the 5 model rows ("No capable model yet", "Change model") and the rest. The 7 main controls never appear, so AI cannot be switched on from Settings, while other screens say "Turn on "Use AI features" in Settings → AI" (QA-LOG Q2 menu import). Reproduced twice, no exception in logcat.
Screenshot: design/qa/pass4/q4-17-ai-missing.png
Suspect: ui/screens/settings/SettingsScreens.kt GroupContent / ui/screens/ai/AiSettingControls.kt AiSettingControl (main ids render empty)

### Q4-18 · S3 · Settings G Water, J.20 · Copy points to screens that no longer exist
Status: Verified fixed in Pass 5b (5822073): Water copy reads 'Log a drink from the water card on Today or from Log food'; privacy points to 'Settings > Food database'. Was: Fixed in de5ed6e, needs retest
Steps: 1. Settings > Water and drinks. 2. Settings > About and help > What leaves this phone, Packaged food search.
Expected: real paths. Nutrition has no Drinks section now (R4 item 9).
Actual: "Logging a drink is in Nutrition > Drinks." and "The size buttons in Nutrition > Drinks. Remove one with the cross, or add your own." Privacy: "Switch it off in Food, Food database & credits." (the setting is Settings > Food database).
Screenshot: design/qa/pass4/q4-18-stale-water-copy.png
Suspect: domain/settings/SettingsRegistry.kt group blurb, ui/screens/settings/PrivacyScreen.kt

### Q4-19 · S3 · Settings J.18 · Distances in km for a user on inches and miles
Status: Verified fixed in Pass 5b (5822073): Settings > Nutrition says 3 mph, 25 mph and '3.2 miles out for 10 min drive'; privacy says 6.2 miles. The Swap sheet still says kilometre, Q5b-14. Was: Fixed in de5ed6e, needs retest
Steps: 1. Seeded install (in, lb). 2. Settings > Nutrition, Eat out nearby; Settings > Water, weather.
Expected: miles (or the units setting).
Actual: "Walking is about 5 km/h and driving about 40 km/h ... (about 5.1 km out for 10 min drive)", "(about 0.9 km out for 15 min walk)", weather "rounds it to about 10 km".
Screenshot: design/qa/pass4/q4-13-sodium-off.png (the Eat out text, lower on the screen)
Suspect: domain/settings eat-out blurb, ui/screens/settings/WeatherSettings.kt

### Q4-20 · S3 · Settings G Reminders · Check-in day says "None picks the day your baseline started" but there is no None
Status: Verified fixed in Pass 5b (5822073): copy reads 'Tap the chosen day again to clear it' and tapping Fri twice cleared it. Was: Fixed in de5ed6e, needs retest
Steps: 1. Settings > Reminders, Check-in day.
Expected: a None chip, or no sentence about it.
Actual: seven weekday chips, none selected on the seed ("None picks the day your baseline started, else Monday."). Once a day is tapped it cannot be cleared.
Screenshot: none (Reminders screen, top)
Suspect: domain/settings/SettingsRegistry.kt coachWeekday

### Q4-21 · S3 · Settings J.21 · Reminder times in 24 hour format on a 12 hour phone
Status: Verified fixed in Pass 5b (5822073): reminder times read 6:00 AM, 7:00 AM, 12:00 PM, 5:00 PM. Was: Fixed in de5ed6e, needs retest
Steps: 1. Settings > Reminders. 2. Compare with Sync and export "Last export: Nov 15, 1:39 PM" and the status bar (10:23).
Expected: one clock style, the phone's.
Actual: chips "6:00, 7:00, 12:00, 17:00, 18:00, 19:00, 20:00", "6:30 ... 10:00", "16:00, 18:00, 20:00, 21:00, 22:00" while times elsewhere are 12 hour with PM.
Screenshot: none (Reminders screen)
Suspect: ui/screens/settings reminder time chips

### Q4-22 · S3 · Shell A.10 · Landscape: Progress content runs under the navigation bar
Status: Verified fixed in Pass 5b (5822073): landscape on the emulator (3-button bar): Today, Nutrition and Progress content ends before the system bar. Was: Fixed in de5ed6e, needs retest
Steps: 1. Seeded install, Progress, 6M. 2. `adb shell settings put system user_rotation 1`.
Expected: content ends at the system bar; the range stays (it does).
Actual: the pill becomes a vertical rail on the left (good), but the Log weight button, the "All" chip, the card edge and "last 6 months" run under the 3-button bar on the right ("last 6 mon" is cut). Seen with 3-button navigation on the emulator; not seen with gesture navigation.
Screenshot: design/qa/pass4/q4-22-landscape.png
Suspect: ui/navigation rail insets

### Q4-23 · S3 · Progress E.9 · "Training stats" opens "Training log" and the lifts block is not on Progress
Status: Verified fixed in Pass 5b (5822073): Progress has a Training card with estimated 1RM (Back squat 197.3 lb), recent records and sets this week; Training stats opens the log. Was: Fixed in de5ed6e, needs retest
Steps: 1. Progress, scroll to the end, tap "Training stats" ("Workout history, volume per muscle and personal records").
Expected: a screen named like the row with volume and records, and E.9 (estimated 1RM, PR list, volume trend, sessions per week).
Actual: the title is "Training log" and it lists the 6 sessions by week with "4 PR" badges; no volume per muscle, no 1RM, no PR list. Progress itself has no training block at all.
Screenshot: design/qa/pass4/q4-23-training-log.png
Suspect: ui/screens/progress ProgressScreen link, ui/screens/train history

### Q4-24 · S4 · Settings H.1 · The pairing code field shows a realistic code as a hint
Status: Verified fixed in Pass 5b (5822073): hint is XXXX-XXXX. Was: Fixed in de5ed6e, needs retest
Steps: 1. Settings > Sync and export, not paired.
Expected: an obviously fake hint.
Actual: "QCBQ-8FQS" in the same grey as the URL hint "https://intake-sync.example.com". The field is empty (uiautomator: EditText text ""), so nothing is pre-filled or stored and "Pair this device" stays disabled; the earlier worry is not a bug in what the app does, only a hint that looks like a real code. The device name is pre-filled with "Unknown Android SDK built for x86_64" (emulator).
Screenshot: design/qa/pass4/q4-24-pairing-placeholder.png
Suspect: ui/screens/backup/SyncSection.kt:99 placeholder

### Q4-25 · S3 · Nutrition A.14 · The NFC template link logs at once, to the wrong meal, and a bad id says nothing visible
Status: Verified fixed in Pass 5b (5822073): intake://log-template/<id> shows 'Log Usual breakfast?' with meal chips, then 'Logged Usual breakfast to breakfast. Almond milk, unsweetened: check the label for dairy' with Undo; a bad id shows 'Meal tag not found'. The chip row is clipped, Q5b-12. Was: Fixed in de5ed6e, needs retest
Steps: 1. Seeded install at 10:40. 2. `adb shell am start -a android.nfc.action.NDEF_DISCOVERED -d intake://log-template/<id of "Usual breakfast">`. 3. Same with `.../not-a-real-id`.
Expected: "Usual breakfast" goes to Breakfast; a bad id says it found no such template.
Actual: the app opens Nutrition and logs it with no confirmation: "Logged Usual breakfast to lunch. Almond milk, unsweetened: check the label for dairy" (+599 kcal under Lunch, the allergen note is shown). The Undo disappeared before I could tap it. The bad id: no message in a screenshot 8 s later and nothing logged, no crash.
Screenshot: design/qa/pass4/q4-25-nfc-lunch.png
Suspect: domain/nfc/TemplateLink.kt and the meal slot choice in the link handler

### Q4-26 · S2 · Backup H.2, H.3 · Importing a backup overwrites newer settings even when it says "0 changes"
Status: Verified fixed in Pass 5b (5822073): preview offers 'Replace these settings with the backup's'; unticked, the newer Salt setting survived and the dialog says 0 changes. It is ticked by default, Q5b-15. Was: Fixed in de5ed6e, needs retest
Steps: 1. Seeded install, Settings > Nutrition: Show salt or sodium = Sodium; export everything. 2. Change it to Salt (Nutrition tab shows "Salt 0.4 g"). 3. Settings > Sync and export > Import a backup, pick the earlier file: the preview says "Import 0 changes?" with "Settings: units, look and feel, ... (27 preferences)" and "Where both sides changed a row, the later change wins." 4. Tap Import ("Imported 0 changes.").
Expected: newer settings kept, as the rows are ("food: 4 kept (newer here)" is shown for rows), or the preview says the settings will be replaced.
Actual: the Nutrition tile is back to "Sodium". Every setting in the file replaces the phone's value silently. An old backup imported on top of a used app resets units, reminders, targets mode and the profile.
Screenshot: none (text only; before and after are the Nutrition tab)
Suspect: data/backup/BackupService.kt settings apply (no last-change check for settings)

### Q4-27 · S4 · Progress, Settings · Polish
Status: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed again in ae7e373, needs retest. Was: Partly fixed in de5ed6e, needs retest. Still open: Settings (d), (f), (g); Progress (a), (b), (e)
Steps: seeded install.
Actual: (a) the weight chart has two y labels and no date labels; the tooltip box ("Mon 9 Nov 2026 194.0 lb · Manual") covers neighbouring points and does not show the trend value. (b) Measurement charts are labelled "Waist over time, in in." and the y axis of Chest and Arm spans 41.6 to 42.0 and 14.2 to 14.6, so a flat line looks meaningful. (c) Settings home: "kg · cm · ml · System" leaves out the portion unit. (d) Nutrition "Advanced 7" shows 5 controls on a Cut, and Carb cycle shift stays visible with Carb cycling off. (e) Progress "Recalculate now" is dim grey on dark. (f) Edit your profile > About you > "Profile & goals" (three names for one screen) and the profile shows height as "72" and "192" without decimals while You shows "72.0 in", so saving rounds 183 cm to 182.88 cm (not confirmed, not saved). (g) Photo reminder, Export reminder and the others save with no confirmation; the Include progress photos switch sits off the right edge of the other switches. (h) Checklist E.1 and F.1 are stale: Progress has chips 1M, 3M, 6M, 1Y, All (not 4 w, 12 w ...), the Weekly check-in row is at the end of Progress, not first, and You shows "72.0 in", not "6 ft 0 in".
Screenshot: design/qa/pass4/q4-scrub-tooltip.png
Suspect: various

Summary for Pass 4: 27 findings. S1: 0. S2: 8 (Q4-1, 2, 5, 6, 8, 10, 17, 26). S3: 17 (Q4-3, 4, 7, 9, 11, 12, 13, 14, 15, 16, 18, 19, 20, 21, 22, 23, 25). S4: 2 (Q4-24, 27). A retest is needed after fixes. Not tested here and still open: the wrong-passphrase retry (Q4-8), the 17 Health Connect switches and the permission sheet (API 34+), reminders actually posting, CSV exports, Today card switches, and everything on a device.


## Pass 5a — Retest and areas A-D
Date: 2026-11-15 (device zone America/Los_Angeles, 18:30 to 21:10 local; the test ended well before midnight) · Build: 4321255, the prebuilt release/intake-preview.apk (com.zeroindex.intake.preview), fresh install after uninstall · AVD: phone30 · Seed: tools/qa/seed.sh copied to a temporary script pointed at the preview package (data dated 2026-11-15)
Covered: every Pass 1 and Pass 2 "needs retest" item and the round 2 "open" list (46 items, see their Status lines). Area A: A.2, A.3, A.4, A.8, A.10 (landscape: Today, Nutrition, Train), A.11 (font 1.5 on Today and Nutrition), A.12 (dark: Nutrition), A.13 (minimised workout, session bar, resume after force-stop). Area B: B.1, B.2, B.3, B.4 (sums), B.7, B.8 (water plus and minus), B.9, B.10 (weigh-in 191.4 lb), B.12 to B.15, B.19 (not run), B.20, B.21. Area C: C.1 to C.2, C.6, C.9 to C.15, C.16 (Quick add, blocked name), C.17 (permission denied, typed barcode offline), C.18, C.19, C.21, C.23, C.29 to C.32, C.36 to C.44, C.46 (Swap sheet), C.47, C.48, C.49, plus a day's logging: dinner by search (Salmon x2), a recipe at 1.5 servings (Chicken fajita bowls), a dairy recipe with Log it anyway (Baked mac and cheese), a template (Post-workout shake), a drink, a weigh-in, a plan week, a shopping list of three recipes. Area D: D.1 to D.5, D.6 (tick sets, rest bar), D.8 (add set not run), D.9, D.10, D.11 (discard asks twice), D.12, D.14, D.15, D.20 to D.24, a full Upper B session (8 sets, finished, summary 1,545 lb / 8 sets / 15 min, which I checked: 3 x 8 x 50 + 4 x 0 x 5 + 1 x 6 x 57.5).
Not covered: A.1, A.5, A.6, A.7, A.9, A.14; B.5, B.6, B.11, B.16 to B.18, B.22 to B.24 (B.23 needs a restart of the zone); C.3, C.4, C.5, C.7, C.8, C.20, C.22, C.25 to C.28, C.33 to C.35, C.45 (PDF), C.50, C.51; D.7 (rest timer vibration, screen off), D.13, D.16 to D.19, D.25 to D.32 (long names, empty states, ripple), D.27. No AI, no network, no camera hardware, no real location (the no-key, offline and denied states only). The first two "Fixed" statuses of Q1-4 (review-import sheet) and Q2-7 (zip lookup) rest on screens that need a network.
Logcat: no FATAL EXCEPTION, empty crash buffer, no StrictMode lines. Two ANRs for com.zeroindex.intake.preview. 18:59 ("Input dispatching timed out ... MotionEvent", I long-pressed an entry on Today straight after a uiautomator dump): the saved trace has the app's main thread idle in the message loop, treated as the emulator. 19:10 ("Input dispatching timed out ... KeyEvent keyCode=43", typing into the Log food search after clearing it): the main thread is running (state R) inside Compose recomposition, see Q5a-4. Each killed the app; it was relaunched and the data was intact. No key was typed into the app.
Method notes: screenshots from emu-ui.py are 540x1200 for a 1080x2280 screen, so y must be multiplied by 1.9, not 2 (x by 2); the tap coordinates used here come from `dump`. `adb shell input text` into the Log food search field keeps only the first few characters (see Q5a-3); other fields (recipe paste list, template name, weigh-in) took the full text. The long-press that opens an entry's edit sheet (Today says "Long-press an entry to edit") is needed; a plain tap on a Today entry did nothing in my two tries, which I did not log because the tap may have missed.
Seen on emulator only (Android 11, software rendering, 3-button navigation bar, no KVM). Not seen on a device: performance, real keyboard and typing speed, haptics, the right-edge clipping in Q5a-8 (it is the side navigation bar of the 3-button mode in landscape; a gesture-navigation phone has no such bar), Health Connect, camera. Retest tally: 46 items, 37 verified fixed, 7 still failing, 2 cannot verify (the seed has no MAY_CONTAIN recipe for the Swap gate and no old shopping rows for the aisle repair).

### Q5a-1 · S2 · Recipes C.36, I.4, I.9 · Grandma's vegetable soup shows a green "Fits your allergens" while its page says "Allergens not checked"
Status: Verified fixed in Pass 5b (5822073): Grandma's soup is amber 'Only the name was checked' in the list, Mine and the plan picker; the recipe page says 'Allergens not checked', see Q5b-6. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Nutrition > More > Recipes. 2. Read the chip on Grandma's vegetable soup (ingredients: carrots, onion, celery, canned tomatoes, stock, salt and pepper; no ingredient was matched to a food). 3. Open the recipe. 4. Log food > Mine, scroll to RECIPES.
Expected: the same, honest verdict on every screen; an unchecked recipe is never shown as clear.
Actual: the list row and the Log food Mine row show a green tick "Fits your allergens". The recipe page's Ingredients header shows an amber "Allergens not checked". For a strict-dairy user the list reads as an all-clear for a recipe the app itself says it did not check (stock can carry dairy).
Screenshot: design/qa/pass5a/q5a-1-recipes-list-verdicts.png (list), design/qa/pass5a/q5a-1b-soup-page-not-checked.png (page), design/qa/pass5a/q5a-1c-mine-soup-fits.png (Log food > Mine)
Suspect: the recipe-row allergen summary used by ui/screens/plan (RecipesViewModel) and ui/addfood/RecipeRows.kt versus the recipe page's verdict in RecipeDetailScreen.kt

### Q5a-2 · S3 · Recipes C.36, I.4 · The same recipe gets three different verdicts on three screens
Status: Verified fixed in Pass 6 (9a42584): the Swap sheet now reads 'Only the name was checked' for Chicken fajita bowls and Salmon (see Q5b-2). Was: Still failing in Pass 5b: the list, Mine, the recipe page and the plan picker now agree ('Only the name was checked' for Chicken fajita bowls and Salmon), but the Swap sheet shows green 'Fits your allergens' for both. Logged as Q5b-2. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Look at Chicken fajita bowls (five ingredient lines, notes "Dairy-free as written") in Nutrition > More > Recipes, in Log food > Mine and on its recipe page; also in the template picker.
Expected: one verdict.
Actual: Recipes list: amber "Only the name was checked". Log food > Mine, template picker and recipe page: green "Fits your allergens". Salmon, sweet potato and broccoli behaves the same.
Screenshot: design/qa/pass5a/q5a-1-recipes-list-verdicts.png, design/qa/pass5a/q5a-1c-mine-soup-fits.png
Suspect: the Recipes hub list checks the name only

### Q5a-3 · S2 · Nutrition C.10, C.18 · Fast typing in the Log food search drops the end of the text
Status: Still failing in Pass 6: same as Q1-3, logged as Q6-3. Was: Partly fixed in 504dae6: open-time allergen scan burst cut from about 85 s to about 45 s of CPU on the debug emulator; typing still drops injected keys on the emulator. Needs a preview-build and real-device check. Was: Still failing in Pass 5b: same as Q1-3: 'almond milk' became 'almo'. Characters were also lost in the Quick add name and the recipe name at the same speed on this emulator, so part of the loss may be the IME, but only the Log food search ANRs. Q5b-1. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Log food > Search > tap the field. 2. `adb shell input text protein` (also ranch, milk, almond, "almond milk", zzqxvv). 3. Read the field and the results.
Expected: the field shows what was typed.
Actual: protein -> "protei", ranch -> "ran", milk -> "mil", almond -> "almo", zzqxvv -> "zzqx", "almond milkzz" -> "almo". The results follow the shortened text ("almo" lists Salmon fillet first). Typing the four keys of "milk" one by one with a 1.5 s gap gave "milk". The recipe paste list, a template name and the weigh-in field took the same fast input in full, so it is this field. The source keeps the text in local state (LogFoodScreen.kt:128), so the cause is probably the main thread being busy recomposing the results (see Q5a-4); it may be much rarer on a phone.
Screenshot: design/qa/pass5a/q5a-3-search-drops-chars.png ("protei"), design/qa/pass5a/q5a-3b-search-drops-chars-2.png ("zzqx")
Suspect: ui/addfood/LogFoodScreen.kt TextField (value = query) and the work done on each onQueryChange

### Q5a-4 · S2 · Nutrition C.10 · ANR while typing in the Log food search (main thread recomposing)
Status: Still failing in Pass 6: ANR again (input dispatching timed out on the last key, main thread in compose layout), logged as Q6-3. Was: Partly fixed in 504dae6: open-time allergen scan burst cut from about 85 s to about 45 s of CPU on the debug emulator; typing still drops injected keys on the emulator. Needs a preview-build and real-device check. Was: Still failing in Pass 5b: two ANRs in Log food search on input text 'almond milk' (08:16, 08:23); the saved trace has the main thread idle in the message loop, the log shows 3 to 7 s per key event in that screen, while Quick add took 9 characters with no delay. Q5b-1. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Log food > Search with "almo" in the field. 2. Tap the clear (x) and send `input text "almond milk"` at once.
Expected: the app stays responsive.
Actual: "ANR in com.zeroindex.intake.preview ... Input dispatching timed out ... KeyEvent keyCode=43" at 19:10:51; the saved trace has the main thread in state R in androidx.compose.runtime GapComposer recompose (no app frame), the app was killed and the launcher showed. Together with Q5a-3 this points at the search screen doing a lot of work per keystroke.
Screenshot: none (no UI after the kill; the trace is in /data/anr on the emulator, not kept)
Suspect: ui/addfood/LogFoodScreen.kt results list and the three flows behind it (AddFoodViewModel, RecipeSearchViewModel)

### Q5a-5 · S3 · Nutrition C.46 · Swap sheet: "PLANNED" is set one letter per line, and the first frame says nothing is planned
Status: Verified fixed in Pass 5b (5822073): first frame reads 'Reading your plan, recipes and meal templates'; header is 'PLANNED' with a chip ending in an ellipsis. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Nutrition > More > Meal plan > Make a plan > Choose a recipe for week 1 > Baked mac and cheese. 2. Today > Next up shows "DINNER . PLANNED Baked mac and cheese". 3. Tap Swap and screenshot at once, then after 10 s.
Expected: a readable header, and a sheet that agrees with Today.
Actual: first frame: "NOTHING PLANNED FOR DINNER. There is no planned meal to compare with ..." although Today lists a planned dinner. Loaded: the planned-meal card squeezes its label to a column of single letters (P, L, A, N, N, E, D) beside a red chip that is cut off ("Contains mac and cheese, butter, cheddar, cheese,") with no ellipsis.
Screenshot: design/qa/pass5a/q5a-5-swap-header-planned-vertical.png, design/qa/pass5a/q5a-5b-swap-nothing-planned.png
Suspect: ui/screens/swap/SwapSheet.kt planned-meal card row

### Q5a-6 · S3 · Shell J.11 / Today B.19 · Loading frames show false "no data" copy on Today and Nutrition
Status: Verified fixed in Pass 5b (5822073): Today cold start shows blank frames, no false empty copy. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Force-stop and relaunch the app (or switch to Nutrition) and screenshot during the first 5 to 40 s.
Expected: blank or a spinner until the data is read.
Actual: Today shows a grey ring "0 kcal eaten", "Eaten 0, No target yet", Protein "0 / - g", "No weigh-in yet" and an empty Meals section; a few seconds later it becomes 3,888 eaten, "1,628 calories over 2,260" and 191.4 lb. Nutrition shows "Reading today..." in an empty 650 px box with "Nothing yet" under Breakfast and Lunch, then fills in. A person with a target and a weigh-in sees "No target yet" and "No weigh-in yet" first.
Screenshot: design/qa/pass5a/q5a-6-today-cold-open-no-target.png, design/qa/pass5a/q5a-6b-nutrition-nothing-yet-loading.png
Suspect: ui/screens/today/TodayViewModel.kt initial state, ui/screens/nutrition/NutritionHomeViewModel.kt initial state

### Q5a-7 · S3 · Train D.14, D.6 · Pull-Up shows "0 LB" and "Previous 0 x 10", and says "No rule and no history yet - pick a working weight"
Status: Verified fixed in Pass 5b (5822073): Pull-Up shows 'BW x 10', column '+LB', 'Bodyweight: same reps as last time'. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Train > Start workout (Upper B). 2. Open Pull-Up (4 x 5-10).
Expected: bodyweight sets show reps only (or bodyweight plus added weight), never "0 lb" (D.14); no contradictory hint.
Actual: PREVIOUS reads "0 x 10", "0 x 9", "0 x 8", "0 x 8" and every set has a LB field with 0. Above them the hint reads "No rule and no history yet - pick a working weight", although the previous column is filled and a bodyweight lift has no working weight. The session volume therefore counts these sets as 0 lb.
Screenshot: design/qa/pass5a/q5a-7-pullup-zero-lb.png
Suspect: ui/screens/train workout screen set rows, the hint text for exercises without a rule

### Q5a-8 · S3 · Shell A.10 · Landscape: the right-hand edge of every tab root runs under the navigation bar
Status: Verified fixed in Pass 5b (5822073): landscape on the emulator (3-button bar): content ends before the bar on Today, Nutrition, Progress. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. `adb shell settings put system accelerometer_rotation 0; ... user_rotation 1`. 2. Today (scroll to Next up), Nutrition (More nutrients, meal rows), Train > Videos.
Expected: content ends before the navigation bar, the rail on the left is fine (A.10).
Actual: the right ~30 px of content is cut by the system bar: the "Start" button of Lower B is half hidden, "Log it" and the allergen chip run to the edge, the "More nutrients" chevron is clipped, the + buttons of the meal rows are cut, the Videos tab chip is cut, a paragraph runs under the bar ("... and a"). The rail on the left and the top bar are fine. The shots are halved from 2280x1080.
Screenshot: design/qa/pass5a/q5a-8-landscape-today.png, design/qa/pass5a/q5a-8b-landscape-nutrition.png, design/qa/pass5a/q5a-8c-landscape-train.png
Suspect: the content insets in ui/navigation (rail layout) ignore the end navigation-bar inset

### Q5a-9 · S3 · Nutrition C.42 · Logging a meal template gives no Undo, leaves an inline message that never goes away, and says "to snack"
Status: Not retested in Pass 7 (b702563): meal template log not run. Was: Fixed in 01f950c, needs retest. Was: Still failing in Pass 6: a snackbar 'Logged Post-workout shake to Snacks. Almond milk, unsweetened: check the label for dairy' with Undo now appears, but Undo removed nothing (Q6-2). The log dialog still offers a Drinks slot for a food template. Was: Fixed in 504dae6, needs retest. Was: Still failing in Pass 5b: logging Post-workout shake to Snacks gave no message and no Undo at 1, 3, 4 and 12 s; the entry was logged. Logged as Q5b-4. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Nutrition > More > Meal templates > ... on Post-workout shake > Log now > Snacks. 2. Look at the screen for 10 s and later.
Expected: a snackbar with Undo as every other log path now has (food, recipe, drink); one-line message.
Actual: a text line "Logged Post-workout shake to snack. Almond milk, unsweetened: check the label for dairy" appears above Saved and pushes the list down by about 200 px; it stays, with no Undo and no dismiss. "snack" is lowercase and singular where the slot is "Snacks". The recipe path says "Logged Baked mac and cheese, 1 serving, to snack" too.
Screenshot: design/qa/pass5a/q5a-9-template-log-inline.png
Suspect: ui/screens/templates/MealTemplatesScreen.kt log result, the slot name in the message

### Q5a-10 · S3 · Allergens I.5 / Nutrition · A template saved from Today copies "(Logged despite: dairy)" into the item name, and the check then matches that note
Status: Verified fixed in Pass 5b (5822073): a template saved from 'Baked mac and cheese (Logged despite: dairy)' shows 'Contains mac and cheese', not 'dairy'. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Today: Snacks holds "Baked mac and cheese (Logged despite: dairy)". Tap Save as meal template (name "Snacks prep"). 2. Meal templates shows a red "Contains dairy and mac and cheese". 3. Log now > Breakfast.
Expected: the template keeps the food names; the sheet names the real matches (mac and cheese, butter, cheddar ...).
Actual: the chip lists "dairy", which is the word in the override note, as a match. The Food safety sheet shows two name matches ("dairy" found in the name, "mac and cheese" found in the name) and an ingredient line that begins "Apple, Peanut butter, Baked mac and cheese (Logged despite: dairy), Banana ...". The next log of this template would carry the note forward as part of a name, so a later entry can read "(Logged despite: dairy)" without a person having chosen it. The sheet did offer "Log it anyway" and "Don't log it" (right). I deleted the template afterwards (the confirmation dialog is clear).
Screenshot: design/qa/pass5a/q5a-10-template-save-dialog.png (the note in the item names behind the dialog), design/qa/pass5a/q5a-10b-template-list-dairy.png
Suspect: ui/screens/today (Save as meal template reads the logged name, which carries the override suffix), data/repository FoodLogRepository.markOverridden

### Q5a-11 · S3 · Nutrition C.15 / C.37 · Log food > Mine lists Grandma's soup as "kcal not listed" with a greyed + and no reason
Status: Verified fixed in Pass 5b (5822073): Log food > Mine shows 'Recipe . per serving . ~62 kcal . ~3 g protein' for the soup. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Log food > Mine, scroll to RECIPES.
Expected: the same figure as the recipe page and list (~62 kcal estimated), or a reason the + is off.
Actual: "Recipe . per serving . kcal not listed . protein not listed" with a grey, inactive +. The Recipes list and the page show "~62 kcal . per serving, estimated" and the page logs it.
Screenshot: design/qa/pass5a/q5a-1c-mine-soup-fits.png
Suspect: ui/addfood/RecipeRows.kt uses stored kcalPerServing only (as the Recipes list did before 795a8d4)

### Q5a-12 · S3 · Recipes C.40 · Leaving the recipe editor discards a typed draft without asking
Status: Verified fixed in Pass 5b (5822073): back from an edited recipe asks 'Discard changes? This recipe has changes that are not saved.'. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Recipes > Add a recipe, type a name, Add ingredients > Paste a list > two lines > Add these. 2. System back, back.
Expected: "Discard this recipe?" or a saved draft (nothing is hard-deleted; a typed draft is the user's work).
Actual: back closes the keyboard, back again returns to the list; the recipe count stays at 5 and the draft is gone, no prompt.
Screenshot: design/qa/pass5a/q5a-12-recipe-editor.png (the editor before leaving)
Suspect: ui/screens/recipe/RecipeEditorScreen.kt back handling

### Q5a-13 · S3 · Recipes, Menu import · Leftover copy and clipping from Q1-26, Q2-13 and new
Status: Verified fixed in Pass 5b (5822073): (a) intro is left-aligned, (b) hub card says 'Import from a link', (c) tab reads 'From my foods', (d) the red chip ends in an ellipsis. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Nutrition > More > Recipes (card text, chip). 2. Recipes > Add a recipe > Add ingredients (tab row). 3. Eating out > Menu import.
Expected: left-aligned short intro; "address" wording that fits the field; labels that end in an ellipsis or wrap.
Actual: (a) the Menu import intro card is still a centred bold title plus a six-line centred paragraph. (b) the Recipes hub card still reads "Paste a recipe page. Read straight off the page, no AI" while the screen it opens says "Paste a recipe link" and asks for a web address. (c) the first tab of the Add ingredients sheet reads "From my" (cut from "From my foods"). (d) The red recipe chip reads "Contains mac and cheese, butter, cheddar, cheese," and stops at the chip edge, with no ellipsis, in the Recipes list, in Mine and in Swap.
Screenshot: design/qa/pass5a/q5a-13-clipped-tab.png, design/qa/pass5a/q5a-1-recipes-list-verdicts.png
Suspect: ui/screens/menu/MenuSourcesScreen.kt, ui/screens/plan recipes hub, ui/screens/recipe ingredient sheet, ui/allergen chip text overflow

### Q5a-14 · S4 · Today B.12 · Meal cards are still tall, and kcal totals lack the thousands comma
Status: Verified fixed in Pass 5b (5822073) for card height: a meal of 1,000 kcal or more was not on screen, so the comma was not checked. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Today after dinner has 1,476 kcal; scroll to Meals.
Expected: compact meal rows (B.12) and "1,476 kcal" like the ring's "3,888".
Actual: each entry has about 70 px of air around it and 130 px between the last entry and "+ Log food", then a separate "Save as meal template" row; Breakfast is taller than a screen at three entries. Dinner reads "1476 kcal" and Snacks "1187 kcal".
Screenshot: design/qa/pass5a/q5a-14-today-meal-cards.png
Suspect: ui/screens/today/components/MealSlotCard.kt

### Q5a-15 · S4 · Train D.6 · A ticked set shows "50" where its neighbours show "50.0"
Status: Verified fixed in Pass 5b (5822073): all set weights read 50.0, ticked or not. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Start Upper B > Incline Dumbbell Press > tick set 1.
Expected: "50.0" in every row.
Actual: set 1 reads "50", sets 2 and 3 read "50.0".
Screenshot: design/qa/pass5a/q5a-15-set-weight-50.png
Suspect: the set row weight formatting once the set is logged

### Q5a-16 · S4 · Allergens C.10 · "May contain creamy" on Almond butter, creamy
Status: Verified fixed in Pass 6 (9a42584): the chip now reads 'May contain dairy' with the line '"creamy" found in the name - often made with milk and dairy, but not always.' (design/qa/pass6/q6-almond-butter.png); the word 'creamy' is gone from the chip. Was: Cannot verify on emulator: typing 'almo' showed no USDA rows within 30 s (and fast typing cannot be used), so the almond butter chip was not seen. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Log food > Search "almo" > USDA rows.
Expected: no dairy claim for almond butter, or a clear one.
Actual: amber chip "May contain creamy", which names the word, not a food. Safe direction, but it reads as nonsense and teaches the person to ignore the chip.
Screenshot: design/qa/pass5a/q5a-16-almond-butter-creamy.png
Suspect: the dairy keyword list contains "creamy" (and "cream" prefix matching) in domain/allergen

### Q5a-17 · S4 · Recipes C.40 · "Not counted: needs grams and macros per 100 g" under "200 g pasta"
Status: Verified fixed in Pass 5b (5822073): '200 g pasta' reads 'Not counted: needs macros per 100 g'; '1 cup milk' needs grams and macros; 'Contains milk' shows. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Recipes > Add a recipe > Add ingredients > Paste a list: "200 g pasta", "1 cup milk". 2. Add these.
Expected: the weight is taken (the sheet says "Weights give grams"); the line says it needs macros.
Actual: both lines read "Not counted: needs grams and macros per 100 g", although 200 g was typed. "Contains milk" is right.
Screenshot: design/qa/pass5a/q5a-12-recipe-editor.png
Suspect: ui/screens/recipe/RecipeEditorScreen.kt line status text

### Q5a-18 · S4 · Drinks / Train · Duplicate rows: "Milk, whole" twice in Drinks, "Back squat" and "Barbell Back Squat" in the library
Status: Verified fixed in Pass 5b (5822073): Train > Exercises lists 'Back squat' once; the Drinks list was not rechecked. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest. Note: the duplicates were QA seed artefacts, and the seed was fixed.
Steps: 1. Drinks, scroll the list. 2. Train > Exercises.
Expected: one row per food or exercise.
Actual: two identical "Milk, whole . 61 kcal / 100 ml . Contains milk and whole milk" rows; "Back squat" (built in) beside "Barbell Back Squat" (Custom).
Screenshot: design/qa/pass5a/q5a-18-drinks-duplicate-milk.png, design/qa/pass5a/q5a-18b-exercises-duplicate-squat.png
Suspect: seed generator (tools/qa/seed/generate.py) or a backup import that does not match by name

### Q5a-19 · S4 · Eating out C.44 · Logging a saved option uses its description as the sheet title
Status: Verified fixed in Pass 5b (5822073): sheet title is 'Log Burrito Barn (invented) to'. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Eating out > Saved eat-out options > tap Burrito Barn (invented).
Expected: "Log Burrito Barn (invented) to".
Actual: "Log Chicken burrito bowl: rice, black beans, fajita veg, salsa. No cheese, no sour cream. to". The same sentence is the card's headline in Healthier options.
Screenshot: design/qa/pass5a/q5a-19-eatout-sheet-title.png
Suspect: ui/screens/hub saved option card (name and description swapped for the title)

### Q5a-20 · S4 · Shell A.11 · Font scale 1.5: the Protein and Carbs tiles on Today wrap and lose "g"
Status: Verified fixed in Pass 5b (5822073): font 1.5 on Today: '26 / 159 g' on one line in all three tiles. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. `adb shell settings put system font_scale 1.5`. 2. Today.
Expected: "250 / 170 g" on one line or wrapped the same way in all three tiles.
Actual: Protein and Carbs show "250g" with "/ 170" above it and no unit after 170; Fat shows "157 / 70 g". Nutrition meal rows cut names ("Rolled oats, dr...") which is acceptable.
Screenshot: design/qa/pass5a/q5a-20-font15-today.png
Suspect: ui/screens/today macro tiles

### Q5a-21 · S4 · Nutrition C.43 · Plan: the recipe picker shows no allergen verdict, and the week card has a "Not set PROTEIN" tile
Status: Verified fixed in Pass 5b (5822073): plan picker rows carry chips (Contains ..., Only the name was checked) and the week card says 'PROTEIN TYPE / Not set'. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. More > Meal plan > Make a plan > Choose a recipe for week 1. 2. Pick Baked mac and cheese.
Expected: Baked mac and cheese carries a chip in the picker for a strict-dairy user; a tile that means grams of protein does not say "Not set".
Actual: the picker lists all five recipes with a name and servings and no chip (the dairy ones look like the clear ones). The week card shows "Not set / PROTEIN" next to "6 SERVINGS" and "50 min", which is the protein type, not grams. After picking, the card does say "Contains an allergen" and "Check the labels before cooking" (right).
Screenshot: none (dump text only)
Suspect: ui/screens/plan/PlanSheets.kt recipe picker, PlanScreen.kt tile

### Q5a-22 · S4 · Nutrition C.21 · The food page shows a rounded kcal figure, but logs the exact one
Status: Verified fixed in Pass 5b (5822073): the chip on the food page reads 188 for Peanut butter, the same as the entry; the printed label on the same page reads 190, Q5b-13. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest. Note: the food page now shows the exact logged kcal.
Steps: 1. Log food > Search "milk" > Milk, canned, condensed, sweetened (38 g): chip reads 120 kcal, list row 123 kcal; log it anyway. 2. Peanut butter: chip 190, list 188; Today logs 188.
Expected: the figure on the page equals the entry.
Actual: Today shows 123 kcal and 188 kcal (the exact values); the page showed 120 and 190 (label rounding). Seen on two foods.
Screenshot: design/qa/pass5a/q5a-22-food-page-120.png, design/qa/pass5a/q5a-22b-pb-190.png
Suspect: ui/screens/fooddata/FoodDetailScreen.kt kcal chip uses the label rounding

### Q5a-23 · S4 · Nutrition C.17 · Scan with camera denied: a grey back-arrow circle sits on the card's top-left corner
Status: Verified fixed in Pass 5b (5822073): no grey arrow on the card; an empty left margin remains. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Log food > Scan > Deny.
Expected: the explanation card with no stray control.
Actual: "Camera access is off" card (clear text, Allow camera, Add a product by hand, and a typed barcode that fails with "No connection. You can still add this barcode by hand.") with a round grey arrow overlapping the card's top-left corner.
Screenshot: design/qa/pass5a/q5a-23-scan-back-arrow.png
Suspect: ui/addfood scan view (the camera preview's back button drawn over the fallback card)

### Q5a-24 · S4 · Allergens I.9 · Apple and Banana carry a green verified tick beside the name and an amber "Only the name was checked"
Status: Verified fixed in Pass 5b (5822073): Banana shows only 'Only the name was checked', no green tick. Was: Verified fixed in Pass 5b (5822073) for (a), (b), (c), (f): (d), (e), (g) were not rechecked; weight chart has date labels and four y labels, measurement charts read 'in inches. Latest 36.7 in', Settings home lists the portion unit, the profile title is 'Profile & goals' and height reads 72.0. Was: Fixed in ae7e373, needs retest.
Steps: 1. Log food, default list.
Expected: one signal.
Actual: a green tick (content description "Logged before with no allergen flag") next to the name, then "Only the name was checked" beneath. The tick reads as "safe", the chip as "unchecked".
Screenshot: design/qa/pass5a/q5a-24-tick-and-name-only.png
Suspect: ui/addfood/LogFoodScreen.kt recent-row tick

Summary for Pass 5a: 24 new findings. S1: 0. S2: 3 (Q5a-1, 3, 4). S3: 10 (Q5a-2, 5, 6, 7, 8, 9, 10, 11, 12, 13). S4: 11 (Q5a-14 to 24). Retests: 46 items, 37 verified fixed, 7 still failing (Q1-3, Q1-7, Q1-20, Q1-26, Q2-13, Q2-16, the "almo" ranking item), 2 cannot verify. The allergen block was re-run on the paths touched (Log food, Food page, recipe page, template log, Swap, shopping, menu items, Quick add): no clear verdict was shown for a food with dairy in its name or ingredients; the one false all-clear is Q5a-1 (an unchecked recipe). A new pass is needed after fixes; the stop condition is not met (new S2 and S3 findings).


---

## Pass 5b — Retest and areas E-J
Date: 2026-11-16 (device zone America/Los_Angeles; the device clock was set to 08:00 on the 16th with automatic time off, so the pass ended around 16:30 local, far from midnight) · Build: 5822073, the prebuilt release/intake-preview.apk (com.zeroindex.intake.preview), fresh install after uninstall; no Gradle run. The version row reads "Intake 0.1.0+2026-11-16 · build 26111602"; it carries no commit, so I took the hash from the brief · AVD: phone30 · Seed: tools/qa/seed.sh copied to an excluded temporary script pointed at the preview package, deleted at the end. The seed holds 20 logged days (27 Oct to 15 Nov, 15 Nov a partial 640 kcal) and nothing for the 16th, so Today starts empty; every Progress figure below was checked against the app's own Food log CSV.
Covered: every "needs retest" item (70: Pass 1 and 2 "Fixed again" 6 plus the round 2 bullet, Pass 3 12, Pass 4 27, Pass 5a 24; see their Status lines). Area A: A.1 (all 10 onboarding steps on a fresh install), A.5 (back on Nutrition goes to Today), A.7 (Nutrition sub-screen kept across tabs), A.10/A.11 (font 1.5 on Today, landscape at 1.5 on Today, Nutrition, Progress), A.12 (dark: Log food, Progress, Today), A.13, A.14 (`am start -d intake://log-template/<id>`, plain and NDEF, good and bad id). Area E: E.1 to E.3, E.4 (weigh-in 191.2 lb with a waist), E.7, E.8, E.9 (Training card), E.10 (jump tests: switch, log, delete), E.11 (check-in numbers against the CSV), E.12 (PDF saved), E.13 (no reword control with AI off), E.16, E.19 on the way, E.23 (fresh Progress), E.24 (no Health Connect numbers implied), E.25, E.26 (dark). Area F: F.1 to F.3, F.7, F.8, F.14, F.16, F.10/F.11/F.12 (About and help rows, Script language, What leaves this phone). Area G: Appearance and units (units, Today card switches), Nutrition (auto on and off, calories by hand 1000, salt and sodium Off, Salt), Water and drinks (target 100 fl oz saved and shown in Settings home and You), Training (read all), Health Connect, Reminders (check-in day set and cleared, times), Sync and export (JSON plain/encrypted, CSV Food log, Workouts, Body metrics), AI, API keys (read, no key typed), About and help, settings search ("salt"). Area H: H.2, H.3, H.4 (truncated file, bad number), H.5 (wrong passphrase, then right), H.6, H.7 (encrypted export, uninstall, fresh install, import: Today, targets, allergen, cards and averages all match), H.8, H.11, H.12. Area I: I.1 to I.4, I.5 (template and swap), I.8 (Strict to Avoid and back), I.9, I.11 (allergen profile after import), menu item verdicts. Area J: J.1, J.2, J.7, J.8, J.10, J.12, J.14, J.18, J.20 on the screens above.
Not reached: A.2, A.6 (the pill is hidden on pushed screens, so a tap on the current tab cannot pop a pushed screen; Train and Progress have no pushed screen to test), A.9 (animations are off), E.5, E.6, E.14, E.15 (photos), E.17, E.18, E.20, E.21, E.22; F.4, F.5, F.6, F.9, F.13, F.15; G Food database, Videos, the 17 Health Connect switches, Drink values, sweat rate, weather permission, reminders actually posting; H.1 on a first look, H.9, H.10; I.2, I.6, I.7, I.10, I.12 and the custom keyword test; D (only the workout paths needed by the retests); camera, AI, network, real location (no key, offline, denied states only). The checklist D.24 and G Videos copy was not re-read.
Logcat: no FATAL EXCEPTION and an empty crash buffer for com.zeroindex.*. ANRs for com.zeroindex.intake.preview: 08:16 and 08:23 (Log food search, `input text "almond milk"`, Q5b-1; each time the app was killed and the launcher showed; the traces show the main thread idle in the message loop at dump time, the input dispatcher logged "spent 2881 to 4156 ms processing KeyEvent" for single keys in that screen) and 10:29 (first attempt at typing into the Encrypted backup passphrase dialog, main thread running in CoreTextField.notifyFocusedRect, Q5b-17; two later attempts at the same dialog did not ANR). ANR in com.android.quicksearchbox at 08:24 and a StrictMode death of org.chromium.webview_shell (the system browser stub, opened by Health Connect's Play Store button) are the emulator's, not Intake's. No StrictMode line from Intake. The fake passphrases ("qapass1", "wrongpass") do not appear in logcat for the part of the pass after the last `logcat -c` (the fresh install onward); earlier buffers were cleared along the way.
Method notes: tap coordinates come from `dump`, which lists the nodes in the screen's own pixels (1080 x 2280; screenshots are half size). `adb shell input text` on this emulator loses characters in other fields too (Quick add name "almond milk latte" kept "almond milk", the recipe name "Draft recipe" kept "Draft r"), so a lost tail alone is not proof against the app; the proof here is the ANR and the 3 to 7 s per key in Log food search against no delay in Quick add. `input text` of a first letter such as "w" with no focused field opened the Android quick search box; I treated that as my own error.
Seen on emulator only (Android 11, software rendering, no KVM, 3-button navigation, 5 to 40 s per action). Not seen on a device: typing speed and IME behaviour, performance, haptics, the landscape bar with gesture navigation, notifications, Health Connect, camera, the feel of the encrypted export's wait. Retest tally: 70 items, 62 verified fixed, 7 still failing (Q1-3, Q3-1, Q5a-2, Q5a-3, Q5a-4, Q5a-9, the round 2 "almo" ranking item), 1 cannot verify (Q5a-16). Q4-27 and Q5a-14 are verified for the parts listed in their Status lines.

### Q5b-1 · S2 · Nutrition C.10, C.11 · Log food search: fast typing still loses characters, ANRs, and "almo" still ranks Salmon
Status: Still failing in Pass 6: ANR twice, 114 CPU seconds to open Log food, 'almo' still lists Salmon first; slow typing is kept. Logged as Q6-3. Was: Partly fixed in 504dae6: open-time allergen scan burst cut from about 85 s to about 45 s of CPU on the debug emulator; typing still drops injected keys on the emulator. Needs a preview-build and real-device check. Was: open
Steps: 1. Today > Log food > Search > tap the field. 2. `adb shell input text "almond%smilk"` (also run twice from a fresh launch). 3. Read the field and wait 20 s. 4. Clear it and type a, l, m, o, n, d, space, m, i, l, k one key per 4 s. 5. Clear and type a, l, m, o.
Expected: every character kept, no ANR; "almond milk" shows the Drinks pointer; Almond results above Salmon.
Actual: the field kept "almo" (4 of 11 characters) and the app hit an ANR twice (08:16, 08:23); logcat shows 2.9 to 4.2 s per key event in this screen, while the Quick add name field took 9 characters in a burst with no delay. Typed one key per 4 s, "almond milk" is kept and the screen shows "Drinks: almond milk. Drinks matching "almond milk" are logged from Drinks" with no milk chocolate (Q2-16's title is fixed). "almo" lists "Salmon fillet, baked" under My foods and recents and "Salmon, sweet potato and broccoli" under Recipes, and no Almond row appeared within 30 s. Characters were also lost in Quick add and the recipe name at the same speed, so part of this may be the emulator's IME.
Screenshot: design/qa/pass5b/q5b-1-search-drops-chars.png (also q5b-1-slow-typing-ok.png, q5b-1-almo-salmon-first.png, q2-16-almond-milk-pointer.png)
Suspect: ui/addfood/LogFoodScreen.kt, AddFoodViewModel (work per keystroke on the main thread)

### Q5b-2 · S2 · Recipes / Allergens C.46, I.4 · The Swap sheet says "Fits your allergens" for recipes every other screen says were only name-checked
Status: Verified fixed in Pass 6 (9a42584): Swap shows 'Only the name was checked' on both recipes, 'Choose anyway' on Turkey chili, red chip on the planned mac and cheese; the Today planned card shows 'Contains your allergen' (design/qa/pass6/q6-swap-sheet.png, q6-today-planned.png). Was: Fixed in 504dae6, needs retest
Steps: 1. Plan a week with Baked mac and cheese (Nutrition > More > Meal plan > Make a plan > Choose a recipe). 2. Today > Next up > Swap. 3. Compare Salmon, sweet potato and broccoli and Chicken fajita bowls with the Recipes list, Log food > Mine, the recipe page and the plan picker.
Expected: one verdict. The recipes have no matched ingredients, so "Only the name was checked".
Actual: the list, Mine, the recipe pages and the plan picker all read amber "Only the name was checked". The Swap sheet shows a green tick "Fits your allergens" on both (and the planned mac and cheese correctly red). A green all-clear for a recipe the app says it did not check, for a strict-dairy user.
Screenshot: design/qa/pass5b/q5b-2-swap-fits.png (contrast: recipes-list2.png, recipe-fajita.png)
Suspect: the Swap candidate verdict (SwapGate or the sheet's own scan) versus the recipe verdict used elsewhere

### Q5b-3 · S3 · Settings / Train D.6 · Plates for a lb user are still the metric plates converted to lb
Status: Verified fixed in Pass 6 (9a42584): fresh install, lb: bar 45, plates 45,35,25,10,5,2.5 (design/qa/pass6/q6-plates-fresh.png). Was: Fixed in 504dae6, needs retest
Steps: 1. Fresh install, lb, skip the questions. 2. You > Settings > Training.
Expected: plates of 45, 35, 25, 10, 5, 2.5 lb (Q3-1).
Actual: Bar weight 45 lb (fixed), but "Plates available per side (lb, comma separated)" reads 55.12,44.09,33.07,22.05,11.02,5.51,2.76; the plate maths and the smallest step up use them. The Settings home row says "45.0 lb bar".
Screenshot: design/qa/pass5b/q5b-3-plates-metric.png
Suspect: data/settings plate default (not unit-aware)

### Q5b-4 · S3 · Nutrition C.42 · Logging a meal template still gives no confirmation and no Undo
Status: Not retested in Pass 7 (b702563): meal template log not run. Was: Fixed in 01f950c, needs retest. Was: Still failing in Pass 6: the confirmation and an Undo button now appear (design/qa/pass6/q6-template-log-1s.png), but Undo did not remove the entries in 4 tries, 1.5 to 4 s after the log (Today went 1 to 8 shakes); logged as Q6-2. Was: Fixed in 504dae6, needs retest
Steps: 1. Nutrition > More > Meal templates > "..." on Post-workout shake > Log now > Snacks. 2. Look at the screen at 1, 3, 4 and 12 s. 3. Open Today.
Expected: a snackbar "Logged ... " with Undo, as the recipe and food paths give (Q5a-9).
Actual: nothing appears, not even the old inline line; the sheet closes and the list is unchanged. Today's Snacks shows +338 kcal, so the entry was logged. The dialog also offers a Drinks slot for a food template.
Screenshot: design/qa/pass5b/q5b-4-template-log-no-undo.png
Suspect: ui/screens/nutrition meal templates (the log result message is dropped)

### Q5b-5 · S3 · Recipes C.36, I.4 · Recipe list rows show no allergen chip, and no kcal for the soup, for several seconds
Status: Verified fixed in Pass 6 (9a42584): the list holds back the rows until the verdicts are known, then every row has its chip (q6-recipes-first.png, q6-recipes-8s.png). Was: Fixed in 504dae6, needs retest
Steps: 1. Nutrition > More > Recipes, screenshot as soon as the list appears, then 5 to 10 s later.
Expected: a "Checking..." chip as in Log food (Q1-6), or the row held back.
Actual: the first frames show Baked mac and cheese (dairy) with no chip at all, and Grandma's vegetable soup as "4 servings"; the red chip and "~62 kcal . per serving, estimated" arrive later. A dairy recipe looks like the clear ones while it loads.
Screenshot: design/qa/pass5b/q5b-5-recipes-no-chip.png
Suspect: ui/screens/recipes list (verdict computed after first draw)

### Q5b-6 · S4 · Recipes C.36 · The same unchecked recipe has two wordings
Status: Verified fixed in Pass 6 (9a42584): list and recipe page both say 'Only the name was checked'. Was: Fixed in 504dae6, needs retest
Steps: 1. Recipes list: Grandma's vegetable soup. 2. Open it.
Expected: one wording.
Actual: the list, Mine and the plan picker say "Only the name was checked"; the recipe page's Ingredients header says "Allergens not checked".
Screenshot: design/qa/pass5b/q5b-6-soup-wording.png
Suspect: ui/screens/recipes detail header

### Q5b-7 · S3 · Today / Nutrition B.1, C.6 · The Today tab and the Nutrition tab keep different days
Status: Verified fixed in Pass 6 (9a42584): Today on Sat, Nov 14 then Nutrition shows Sat, Nov 14. Was: Fixed in 504dae6, needs retest
Steps: 1. Today > Previous day twice (Sat, Nov 14). 2. Switch to Nutrition. 3. Switch back.
Expected: shared state ("Arrows share state with Nutrition's day", B.1, C.6), or the checklist is stale.
Actual: Today stays on Sat, Nov 14; Nutrition shows "Today, Mon, Nov 16". No screenshot kept (taken from the two dumps).
Screenshot: none (reason: seen in dumps only, the two screens differ in the date node)
Suspect: separate day state in the Today and Nutrition view models

### Q5b-8 · S3 · Settings / You G Nutrition, F.2 · Switching "Work my targets out from my data" on and off replaces the hand-set macros, and the protein basis differs
Status: Verified fixed in Pass 6 (9a42584): auto on then off keeps 2,260 kcal and 170 / 70 / 245 g. Note: retyping and saving the calories afterwards gives 166 / 70 / 242 g (1.9 and 0.8 g per kg of body weight, as the setting says). Was: Fixed in 504dae6, needs retest
Steps: 1. Seeded install (manual 2,260 kcal, 170 / 70 / 245 g). 2. Settings > Nutrition: switch auto on, then off, then type 2260 and Save. 3. You, Today.
Expected: the hand-set numbers come back, or the switch warns that they will be replaced.
Actual: calories can be retyped, but protein, fat and carbs are now 159, 67 and 255 g (from 1.9 and 0.8 g per kg of the 185 lb goal weight) and stay so on You, Nutrition and Today, with no message. With auto on, the plan says 166 g = 1.9 x body weight 192.5 lb; the manual path uses the goal weight while the setting says "Per kg of body weight".
Screenshot: design/qa/pass5b/q5b-8-targets-changed.png (Today after the round trip: 159 / 67 / 255 g)
Suspect: data/settings targets, domain target maths

### Q5b-9 · S3 · Settings / You F.8, G Nutrition · A manual target of 1,000 kcal is accepted with no floor warning
Status: Layout fixed in b738d31 (Q6-1), needs retest. Was: Still failing in Pass 6: the floor warning now shows ('1000 kcal is below the usual floor of 2024 kcal (1.1 x your calories at rest). It is saved as you typed it...'), but it is drawn inside the field row and squeezes the 'Calories by hand' label to one letter per line; logged as Q6-1. Was: Fixed in 504dae6, needs retest
Steps: 1. Settings > Nutrition > Calories by hand 1000 > Save ("Allow calories below the floor" is off, "Normally never below 1.1 x calories at rest"). 2. You, Today, Nutrition.
Expected: a warning that the target is below the floor (F.8).
Actual: "Target set to 1000 kcal by hand." You shows "1,000 kcal"; no warning on any screen. The Save button turns grey during entry with no reason.
Screenshot: design/qa/pass5b/q5b-9-1000-kcal-no-warning.png
Suspect: ui/screens/settings Nutrition, domain target floor

### Q5b-10 · S3 · Progress E.10, J.14 · Deleting a jump test has no confirmation and no Undo
Status: Verified fixed in Pass 6 (9a42584): 'Jump test deleted' snackbar with Undo (Undo itself did not restore, see Q6-2). Was: Fixed in 504dae6, needs retest
Steps: 1. Settings > Training > Show jump tests in Progress on. 2. Progress > Log a jump test > vertical 20, reach 90 > Log jump test. 3. Tap the delete icon on the row.
Expected: a confirmation or an Undo (nothing is hard-deleted, but the user has no way back).
Actual: the row is gone at once, with no message.
Screenshot: design/qa/pass5b/q5b-10-jump-delete-no-undo.png
Suspect: ui/screens/progress jump tests

### Q5b-11 · S4 · Progress J.10 · Empty cards: "Calories vs target" has a blank body, and Measurements shows only its title
Status: Verified fixed in Pass 6 (9a42584) for the seeded 1M view: Calories vs target has its chart and sentence, Measurements has its three charts; the fresh-install empty state was not re-run. Was: Fixed in 504dae6, needs retest
Steps: 1. Fresh install, Progress, scroll. 2. On the seed at 1M, scroll to Measurements.
Expected: an empty-state line like the Weight trend and Training cards.
Actual: "Calories vs target" shows a title, a legend dot and 200 px of white. On the seed at 1M "Measurements" is a bare title bar with nothing under it (the charts exist under All).
Screenshot: design/qa/pass5b/q5b-11-empty-chart.png
Suspect: ui/screens/progress calories and measurements cards

### Q5b-12 · S3 · Nutrition A.14 · The NFC template dialog clips its meal chips, and offers no Snacks
Status: Verified fixed in Pass 6 (9a42584): the NFC dialog wraps its chips onto a second row and Snacks is offered (q6-nfc-dialog.png). Was: Fixed in 504dae6, needs retest
Steps: 1. `adb shell am start -a android.nfc.action.NDEF_DISCOVERED -d intake://log-template/<id of Usual breakfast>` at midday.
Expected: all meals as chips; "Usual breakfast" preselected on Breakfast, or a reason for the default.
Actual: "Log Usual breakfast?" shows Breakfast, Lunch (preselected, by the time of day), Dinner and a fourth chip cut off at the right edge. The dialog opens over whatever screen is open (the weekly check-in). Logging then shows a snackbar with Undo (good). A plain `am start -d` (VIEW) is delivered to the running app and shows nothing; only the NDEF action opens the dialog.
Screenshot: design/qa/pass5b/q5b-12-nfc-dialog-clip.png
Suspect: ui/navigation NFC link dialog

### Q5b-13 · S4 · Nutrition C.21 · Food page: the top figure (188 kcal) and the printed label (190) differ again
Status: Verified fixed in Pass 6 (9a42584): the chip still reads 188 and the label 190, but the page now has a line explaining that the label rounds calories to the nearest 5. Was: Fixed in 504dae6, needs retest
Steps: 1. Log food > Mine > Peanut butter.
Expected: one figure (Q1-17 fixed this; Q5a-22 made the chip equal the logged value).
Actual: the chip row reads 188 KCAL, the Nutrition Facts block on the same page reads Calories 190.
Screenshot: design/qa/pass5b/q5b-13-188-vs-190.png
Suspect: ui/screens/food detail (exact value versus label rounding)

### Q5b-14 · S3 · Eating out J.18 · The Swap sheet speaks in kilometres to a user on lb and miles
Status: Verified fixed in Pass 6 (9a42584): the Swap sheet says 'Your place is rounded to about 0.7 miles'. Was: Fixed in 504dae6, needs retest
Steps: 1. Today > Next up > Swap, read the "Eat out nearby" card.
Expected: miles, as Settings > Nutrition now says ("3.2 miles out for 10 min drive").
Actual: "Your place is rounded to about a kilometre and sent once, when you tap."
Screenshot: design/qa/pass5b/q5b-14-swap-km.png
Suspect: ui/screens/swap nearby card string

### Q5b-15 · S3 · Backup H.2, H.3 · Import preview: the settings box is ticked by default, and the line names groups that do not match the change
Status: Cannot verify on emulator: after switching Show salt or sodium to Salt (tap not confirmed) the import preview of an encrypted backup made before it said 'Settings: the same as on this phone, nothing to change'; the settings-box default was not seen. Was: Fixed in 504dae6, needs retest
Steps: 1. Export an encrypted backup. 2. Change Settings > Nutrition > Show salt or sodium to Salt. 3. Import the backup, enter the passphrase.
Expected: newer settings kept by default, and the line naming what differs.
Actual: "Import 1 changes? ... Settings: reminders and videos (1 differ from this phone)" with "Replace these settings with the backup's" ticked; the setting I changed (Nutrition) is not named. Unticking it works (the title becomes "Import 0 changes?" and the Salt setting survives), so the safety part is fixed; the default is what overwrites.
Screenshot: design/qa/pass5b/q5b-15-import-settings-default.png
Suspect: ui/screens/settings import preview

### Q5b-16 · S4 · Train D.14 · Pull-Up history shows "TOTAL VOLUME 0 lb"
Status: Verified fixed in Pass 6 (9a42584): TOTAL VOLUME reads '81 reps', BEST SET 'Reps only'. Was: Fixed in 504dae6, needs retest
Steps: 1. Train > Exercises > Pull-Up, scroll to Your history.
Expected: reps only for a bodyweight lift (D.14), not "0 lb".
Actual: BEST SET "Reps only", HEAVIEST "No weight", TOTAL VOLUME "0 lb", SESSIONS "3 . 10 sets".
Screenshot: design/qa/pass5b/q5b-16-bodyweight-volume.png
Suspect: ui/screens/exercises detail sheet

### Q5b-17 · S2 · Backup H.5, J.17 · ANR while typing into the Encrypted backup passphrase dialog (first attempt)
Status: Verified fixed in Pass 6 (9a42584): in the import passphrase dialog 'wrongpass' typed 9 of 9 characters in 8 s with no ANR; the wrong passphrase gave 'That passphrase did not open it. Try again.' and the right one the preview. The export dialog also kept 7 of 7. Was: open (not reproduced in two later attempts). Note after the merge: the export passphrase field kept 7/7 characters without an ANR; the import dialog is untested.
Steps: 1. Sync and export > Import a backup > pick the .intake file. 2. Tap the Passphrase field, `input text wrongpass`, tap Open.
Expected: no ANR.
Actual: once, at 10:29:45, the app was killed with an ANR; the trace's main thread is running (state R) in CoreTextField.notifyFocusedRect (compose text field), not in BackupCrypto, so the PBKDF2 move to a background thread (Q4-8) works: later runs showed the wrong-passphrase message ("That passphrase did not open it. Try again.") and the right one gave the preview, with no ANR. The first tap on the field also needed a second tap before it took the typed text. The same text-field cost shows up in Q5b-1.
Screenshot: none (reason: the ANR killed the screen; see /data/anr trace of that time)
Suspect: unknown (text field recomposition under a dialog)

### Q5b-18 · S2 · Check-in · (owner report) A week with almost nothing logged says "All on track" and "Nothing to change"
Status: Verified fixed in Pass 6 (9a42584): a seeded week with 2 near-empty days shows 'AVG INTAKE 210 kcal · 2 DAYS LOGGED', 'Protein target hit 0 of 2 logged days · 0%', a LOG MORE card 'Not enough data this week to adjust', no 'All on track', and the 'Suggestions' header is whole. With both cards dismissed it still shows them tagged Dismissed. See Q6-4 and Q6-5 for the numbers around it. Was: Fixed in 504dae6, needs retest
Steps: (owner, real phone, older preview build) Progress > Weekly check-in, week Nov 9-15.
Expected: a week with 2 barely-logged days is called "not enough data", never "on track"; the protein line and the intake average use the same logged days.
Actual: Avg weight "195.0 lb · +0.0 lb"; AVG INTAKE 210 kcal (an average over partial days, not real eating); Avg burn 2,230 kcal; "Protein target hit 0 of 7 days · 0%" (the 7 counted unlogged days); then "Check-in of Mon 16 Nov" with an "All on track" pill and "Nothing to change this week. Your numbers are where they should be, so the best move is to keep going." The "Suggestions" header wrapped as "Suggestion / s" beside the caption "You decide. Nothing changes until you tap Apply."
Cause: the coach counts only days of 800 kcal or more, so no day qualified and the data-gap card was the only output; when that card had been dismissed in the last 7 days it was suppressed, no rows were left, and the screen treated "no rows" as "on track". The protein line used the whole week as its denominator, and the header put the caption in the same row as the title.
Fix: CoachEngine now returns a verdict (on track, suggestions, not enough data, nothing new but needs attention); on track is claimed only with enough full days and no contradiction (low protein hit rate, intake far under target); the data-gap card is never suppressed; low protein hit rate gives a protein suggestion when enough days are logged; the protein hit count and AVG INTAKE share one day set and say "N days logged"; a flat or single-reading weight change reads "no change" or is omitted; the header caption sits under the title.
Screenshot: owner's phone (not committed). It shows the Weekly check-in screen with the figures above; a chat-heads bubble with other people's photos is also visible, which is why the image is not in the repo.
Suspect: domain/coach/CoachEngine.kt, ui/screens/checkin/WeeklyCheckInScreen.kt, ui/components/Controls.kt (SectionLine)

Summary for Pass 5b: 17 new findings. S1: 0. S2: 3 (Q5b-1, 2, 17). S3: 10 (Q5b-3, 4, 5, 7, 8, 9, 10, 12, 14, 15). S4: 4 (Q5b-6, 11, 13, 16). Retests: 70 items, 62 verified fixed, 7 still failing, 1 cannot verify. A new pass is needed after fixes; the stop condition is not met. The allergen block was re-run on Log food, Mine, the recipe pages, the plan picker, Swap, templates, menu items, the NFC link and the import: nothing with dairy in its name or ingredients showed as clear; the one wrong all-clear is Q5b-2 (an unchecked recipe in Swap), and Q5b-5 hides a dairy recipe's chip for a few seconds. Nothing here was seen on a device.


---

## Pass 6 — Full retest
Date: 2026-11-16 (device zone America/Los_Angeles, device clock 11:51 to about 14:45, far from midnight; automatic time was off) · Build: the prebuilt release/intake-preview.apk (com.zeroindex.intake.preview), built after 504dae6 (file time 19:31 UTC; the version row reads "Intake 0.1.0+2026-11-16 · build 26111602", the same as Pass 5b, so it carries no commit; head 9a42584) · AVD: phone30 · Seed: tools/qa/seed.sh copied to excluded temporary scripts pointed at the preview package (deleted at the end). The app was installed fresh (uninstall, install, `cmd package compile -m speed -f`), checked on a fresh install for the lb plates, then seeded. A second seed with two near-empty days in the last week (a banana-sized 200 and 220 kcal entry, nothing else since 9 Nov) was used for the weekly check-in. No Gradle was run; the app, wear and wearprotocol build folders were deleted for disk space.
Search performance on this build (fresh process, seeded data, speed-compiled, 4 emulator cores, software rendering, top sampled every 2 to 3 s): cold start to Today quiet: 71 s wall, 143 CPU s. Today then tap Log food: the app's CPU stays at 170 to 330 percent for about 55 s (114 CPU s) and goes quiet at about 62 s; after that 0 percent. Typing `adb shell input text "almond%smilk"` into the Log food search field after it was quiet: the command took 22 s and 29 s, and the app hit an ANR both times (12:22:09 and 12:27:21 device time; "Input dispatching timed out ... Waited 5009ms for KeyEvent" on the last key; the log shows 7.9 s and 4.2 s spent on single key events; the ANR trace shows the main thread running in Compose layout and RenderNode creation, 95 CPU s on the main thread in total, plus a busy JIT thread). The app was killed each time, so how much text was kept could not be read. Same text into Quick add on the same build: 15.7 s, no ANR, the field kept "almond " (7 of 12 characters, so the emulator's injected input loses characters in other fields too). One keyevent per second (a, l, m, o, n, d, space, m, i, l, k): all 11 characters kept, no ANR, and the screen showed "Drinks: almond milk. Drinks matching "almond milk" are logged from Drinks". "almo" typed slowly lists Salmon fillet, baked first; "almond" lists Almond butter, Oil, Flour, Nuts almonds from the USDA rows after about 20 s.
Covered: A.1 (welcome, Skip setup), A.3 to A.7 on the screens visited, A.10 to A.14 (font 1.5 and dark on Today; landscape at 1.5 dark; the NFC dialog); B.1, B.2, B.3, B.12 to B.15 on the day with 8 template logs, planned card chips (B planned meal), Swap sheet in full; C meal templates, recipes list and page, shopping list (amounts and the Clear button), Log food search paths, Quick add, food page (peanut butter, almond butter), More; D Pull-Up history; E.1 to E.3 Progress charts, jump tests (log and delete), E.11 weekly check-in (full week, sparse week, dismiss); F You (goals and macros); G Settings Nutrition (auto switch, calories by hand 1000, floor), Training (plates, jump switch), Sync and export (encrypted export, import with passphrase, wrong passphrase, preview); H.2 to H.5; I.1 to I.4 and I.9 on Log food, recipes, Swap, templates; J.1, J.2, J.10, J.14, J.17, J.18 on the screens above.
Not covered: onboarding steps 2 to 10 (Pass 5b did them); D.1 to D.13 (no workout was started or finished, no program edit); E.5 to E.9, E.13 to E.26 (photos, PDF export, Health Connect); F.4 to F.6; most of G (Food database, Videos, the 17 Health Connect switches, Reminders posting); H.1, H.6 to H.12; I.5 to I.8, I.10 to I.12 (menu import, PDF, custom keywords, Strict to Avoid); eat-out nearby search, camera, AI, network, real location (no key, offline). The import-settings default (Q5b-15) could not be exercised.
Logcat: no FATAL EXCEPTION and an empty crash buffer for com.zeroindex.*. ANRs for com.zeroindex.intake.preview: 12:22:09 and 12:27:21 (Log food search `input text`, Q6-3). Both times the buffer was inspected before the next `logcat -c`; from the import tests onward (cleared at 14:08) the buffer shows no ANR, no StrictMode line and no fake passphrase text ("qapass1", "wrongpass" counted 0). No other ANR of the app. No key was typed into the app.
Method notes: the emulator needs 5 to 30 s per action; tap coordinates come from `dump`; a swipe that started over the open keyboard typed "almog" through Gboard's swipe typing (my error, not logged); a Today tap at y 2126 after the snackbar had gone opened an entry's share-of-calories sheet (my error).
Seen on emulator only (Android 11, software rendering, no KVM, 3-button navigation). Not seen on a device: typing speed, IME behaviour, performance, haptics, notifications, Health Connect, camera, the NFC read itself (the dialog was opened with an NDEF intent), the timing of snackbars on a fast phone.

### Q6-1 · S2 · Settings / You G Nutrition, F.8 · The low-calorie floor warning breaks the Nutrition settings layout
Status: Not retested in Pass 7 (b702563): ran out of emulator time (3 to 5 s frames). Was: Fixed in b738d31, needs retest. Was: open
Steps: 1. Seeded install. 2. You > Settings > Nutrition. 3. Calories by hand: clear, type 1000, tap Save. 4. Leave the keyboard (Back).
Expected: a warning under the field, with the label, field and Save button where they were.
Actual: the warning appears (amber box: "1000 kcal is below the usual floor of 2024 kcal (1.1 x your calories at rest). It is saved as you typed it. Eating this low for long costs muscle, energy and mood; consider a higher number.") but it sits in the same row as the field and Save, which are pushed to the left edge; the "Calories by hand" label and its help line are squeezed into a column one letter wide, running down the page for several screens. Typing 2260 and saving again restores the layout. The Save button is grey while the warning shows, and the text says the value "is saved".
Screenshot: design/qa/pass6/q6-1-floor-row-collapsed.png, design/qa/pass6/q6-1-floor-warning-layout.png
Suspect: ui/screens/settings Nutrition calories-by-hand row (the warning composable placed inside the Row)

### Q6-2 · S2 · Nutrition / Progress C.42, E.10, J.14 · Undo on the "Logged Post-workout shake" and "Jump test deleted" snackbars did not undo
Status: Not retested in Pass 7 (b702563): ran out of emulator time (3 to 5 s frames). Was: Fixed in 01f950c, needs retest. Was: open (emulator timing may play a part: see Steps)
Steps: 1. Nutrition > More > Meal templates > "..." on Post-workout shake > Log now > Snacks. 2. The snackbar "Logged Post-workout shake to Snacks. Almond milk, unsweetened: check the label for dairy" shows with Undo (confirmed in a screenshot). 3. Tap Undo (1.5 s, 4 s and about 10 s after the log, 4 tries in all). 4. Today. Also Progress > Log a jump test (20 and 90) > Delete this jump test > "Jump test deleted · Undo" > tap Undo (3 tries at 1.5 and 3 s).
Expected: the entries (three foods, 338 kcal) are removed again; the jump test comes back.
Actual: the snackbar goes away after the tap, but Today's total only grew (1 shake to 8 shakes, 2,600 kcal) and the jump test stayed deleted ("No jump tests yet"). A control (Today entry Delete, then Undo) could not be timed on this emulator, so the snackbar action may be missed because the app takes seconds to react; but Undo was visible and tapped within the snackbar's life in the template case.
Screenshot: design/qa/pass6/q6-2-template-undo-gone.png, design/qa/pass6/q6-2-jump-undo-snackbar.png (template snackbar: q6-template-log-1s.png)
Suspect: the template log and jump test Undo callbacks (ui/screens/nutrition meal templates, ui/screens/progress jump tests)

### Q6-3 · S2 · Nutrition C.10, J.17 · Log food search: `input text` still gives an ANR on the preview build, and the screen needs about a minute of CPU to open
Status: Still open in Pass 7 (b702563): "almo" typed 1 key per 3 s into Log food search kept all 4 characters and gave no ANR; fast `input text` was not retried. Was: open
Steps: 1. Fresh process, seeded data (speed-compiled). 2. Today > Log food, wait until the CPU is quiet (about 62 s, 114 CPU s). 3. Tap Search foods, `adb shell input text "almond%smilk"`.
Expected: no ANR; a human typing pace is kept (it is), and the screen is usable soon after it opens.
Actual: ANR twice in two runs (numbers in the pass header); the key events took 4 to 8 s each; the field text is lost with the killed app. Typed one key per second, all 11 characters are kept and no ANR. "almo" still ranks Salmon fillet first. The same text into Quick add took 15.7 s without ANR and kept 7 characters, so part of the loss is the emulator's input. The ANR trace shows the main thread in Compose layout (RenderNode creation) for the whole of it.
Screenshot: design/qa/pass6/q6-3-search-anr-launcher.png (after the ANR the launcher is in front); q6-typed-slow.png, q6-quickadd-fast.png
Suspect: ui/addfood/LogFoodScreen.kt (rows composed and laid out on the main thread while the allergen scan runs; the scan finishing is what the 55 s of CPU is)

### Q6-4 · S3 · Progress E.11 · Weekly check-in: the week card and the suggestions describe different weeks
Status: Not retested in Pass 7 (b702563): ran out of emulator time (3 to 5 s frames). Was: Fixed in b738d31, needs retest. Was: open
Steps: 1. Seeded install, Mon 16 Nov, 650 kcal logged today. 2. Progress > Weekly check-in.
Expected: the card and the suggestions refer to the same days, or the suggestions say which days they use.
Actual: the card is "Week of Nov 16-22", "AVG INTAKE 650 kcal · 1 DAY LOGGED", "Protein target hit 0 of 1 logged day · 0%", and says logged days "average far below your target"; the suggestion below it, headed "Check-in of Mon 16 Nov", says "You averaged 135 g of protein on the 6 days you logged; the target is 170 g (79%)". Today's partial day is the only "logged day" of the week card. The week chips open scrolled so the selected week (Nov 16-22) is the last chip and partly off screen.
Screenshot: design/qa/pass6/q6-4-checkin-week-card.png, design/qa/pass6/q6-4-checkin-suggestions.png
Suspect: ui/screens/checkin/WeeklyCheckInScreen.kt (selected week versus the coach's trailing 7 days)

### Q6-5 · S3 · Progress E.11 · Sparse week: the LOG MORE card counts 0 days and 6 weigh-ins while the card above says 2 days and 5
Status: Not retested in Pass 7 (b702563): ran out of emulator time (3 to 5 s frames). Was: Fixed in b738d31, needs retest. Was: open
Steps: 1. Second seed (two days of about 200 kcal in the last week). 2. Progress > Weekly check-in > chip Nov 9-15.
Expected: one set of counts.
Actual: the week card says "AVG INTAKE 210 kcal · 2 DAYS LOGGED", "Days logged 2 of 7", "Weigh-ins 5"; the LOG MORE card says "You logged food on 0 of 7 days and weighed in on 6 (need 5 and 3)". The "0 of 7" is days of 800 kcal or more and the "6" a different window, but neither is said. (The verdict itself is right: "Not enough data this week to adjust", no "All on track", the Suggestions header is whole, and a dismissed card stays on the screen tagged Dismissed.)
Screenshot: design/qa/pass6/q6-5-checkin-sparse-logmore.png (also q6-checkin-sparse2.png, q6-checkin-alldismissed.png)
Suspect: domain/coach/CoachEngine.kt data-gap card text

### Q6-6 · S3 · Nutrition C.45 · Shopping list "Clear" empties the list and the picks at once, with no confirmation and no Undo
Status: Not retested in Pass 7 (b702563): ran out of emulator time (3 to 5 s frames). Was: Fixed in 01f950c, needs retest. Was: open
Steps: 1. Nutrition > More > Shopping list. 2. Tick Baked mac and cheese and Chicken fajita bowls ("9 items · 0 ticked"). 3. Tap Clear.
Expected: a confirmation or an Undo.
Actual: the nine items and both picks vanish with no message.
Screenshot: design/qa/pass6/q6-6-shopping-clear.png
Suspect: ui/screens/shopping

### Q6-7 · S4 · Progress / Nutrition J.20 · Date and nutrient wording differs between screens for a lb and fl oz user
Status: Still failing in Pass 7 (b702563): Food > Diary still reads "Fibre" and "Sat fat" for a lb / fl oz user; date style on Today (Mon, Nov 23, 2026) and Training log (Mon, Nov 23, 2026) now agree; Insights chart and plan form not re-read. See Q7-3. Was: Fixed in b738d31, needs retest. Was: open
Steps: 1. Progress (chart ends): "Sun 18 Oct 2026", "Mon 16 Nov 2026". 2. Today: "Mon, Nov 16". 3. Meal plan > Make a plan: "First day of week 1 (yyyy-mm-dd)". 4. Nutrition: "Fibre" and "Sat fat" while Settings > Nutrition uses US units.
Expected: one date style and one spelling for a user on lb, in and fl oz.
Actual: day-month on Progress, month-day on Today, ISO in the plan form; "Fibre" next to imperial units. Chart text alternatives read "target 2260 kcal" where the screen says "2,260 kcal".
Screenshot: design/qa/pass6/q6-progress-4.png
Suspect: date formatters in ui/screens/progress, ui/screens/mealplan; strings

### Q6-8 · S4 · Recipes C.36, I.4 · The dairy chip lists the recipe's own name as a matched term
Status: Partly fixed in Pass 7 (b702563): recipe cards and the recipe page chip now list butter, cheddar, cheese, cream, milk; the Swap sheet still reads "Contains mac and cheese, butter, cheddar, ..." (Q7-14). Was: Fixed in b738d31, needs retest. Was: open
Steps: 1. Nutrition > More > Recipes (or the plan picker, or Swap).
Expected: matched ingredient words only, for example butter, cheddar, cheese, cream, milk.
Actual: "Contains mac and cheese, butter, cheddar, cheese, cream, milk and whole milk": the first term is the recipe name.
Screenshot: design/qa/pass6/q6-recipes-8s.png
Suspect: recipe verdict match list includes the name match

Summary for Pass 6: 8 new findings. S1: 0. S2: 3 (Q6-1, Q6-2, Q6-3). S3: 3 (Q6-4, 5, 6). S4: 2 (Q6-7, 8). Retests: 29 items (Pass 1 to 5b statuses with "needs retest" or "Partly fixed", Q5b-17, and three round 2 bullets): 17 verified fixed, 8 still failing (Q1-3, Q5a-3, Q5a-4, Q5b-1, Q5a-9, Q5b-4, Q5b-9, and the "almo" ranking bullet), 3 cannot verify on the emulator (Q5b-15 and two round 2 bullets with no seed rows), 1 not retested (Q1-14, owner decision). The allergen block was re-run on Log food (Recent, almond results), the recipe list, plan picker, Swap, templates, the planned card, shopping and the NFC dialog: nothing with dairy in its name or ingredients showed as clear; Almond butter, creamy shows "May contain dairy" with its reason. A new pass is needed after fixes; the stop condition is not met. Nothing here was seen on a device.

---

## Pass 7 — Design v6 and training features
Date: 2026-11-23 (device zone America/Los_Angeles, device clock 10:51 to 13:25, far from midnight) · Build: the prebuilt release/intake-preview.apk (com.zeroindex.intake.preview, 189 MB), repo head b702563 · AVD: phone30 · Seed: a temporary copy of tools/qa/seed.sh pointed at the preview package (data dated 2026-11-23). The seed script itself got stuck at "Import a backup" (Settings is regrouped; the Import button is below the fold), so the import was finished by hand through the picker.
Covered: screenshot comparison with design/mockup-v6 for Today, QuickLog, FoodDiary, FoodPlan, Recipes, FoodLibrary, LogFood, Drinks, RecipeDetail, CookMode, Train, ActiveWorkout, WorkoutSummary, Programs, Insights, You, Settings (side by side in design/qa/pass7/compare/, app shots at half size in design/qa/pass7/). Dark: Today, Food Diary, Log food, Settings, ActiveWorkout. Font 1.5: Today, Food Diary, Settings, ActiveWorkout (Log food landed on the Scan source and the camera prompt, not a clean shot). Landscape: Today (rail) and ActiveWorkout. Functional: all five dock tabs, Quick log sheet and water 8 fl oz with Undo (49.4 to 57.4 and back), Food segments Diary / Plan / Recipes / Library, Library to Meal templates, Shopping list, Eating out, Restaurant menus, My foods, Drinks; Today strip paging back a week and the date heading opening the month calendar; Log food source filter (Mine and Search chips; no ANR typing "almo" at 3 s per key); recipe Log a serving gate and "Find a safer swap" (opens Swap, logs nothing); Cook mode; Help rows Script docs, What leaves this phone, Credits and licences (Replay the tour: see Q7-17); Settings > Training. Training: full workout (Upper B) with rest bar, -15 s and +15 s taps, Skip, rest preset sheet (30 s), pause and resume, Finish, summary, change date/time/length with the future-end check, add and delete a set, Reopen, Repeat, Discard; a second workout (Lower B) with RIR on, a new record on the summary; Training stats; Calculators with Epley (233.5 lb for 200 x 5) then Brzycki (225 lb), Training log List and Calendar (7 training days), Log a past workout dialog.
Not covered: onboarding and its "Set up" buttons (the seed skips the welcome; a fresh onboarding was not run); the Quick log tiles other than the water pills (only the sheet and water were tapped; the other tiles were not each opened); Log food source chips beyond Mine and Search; allergen tiers "Checking...", "May contain" and "Fits" on every screen (seen: Fits your allergens, Only the name was checked, Contains, Check the label; "May contain" not seen); Logging a past workout to completion and seeing it in the calendar (the dialog opened; the AM/PM change and Log it were not completed because the app was killed, Q7-16); the "Still training?" prompt (3 h idle, not triggered); effort in RPE mode; keep-awake on the workout screen (setting is on; the screen timeout was not observed); Dark and 1.5 and landscape for Food Diary, Log food and Settings in landscape; the Q6-1, 2, 4, 5, 6 retests.
Logcat: no FATAL EXCEPTION for com.zeroindex.* and an empty crash buffer. Two ANRs for com.zeroindex.intake.preview (input dispatch, 12:30:14 and 12:33:53). The 12:30 trace has the main thread composing TrainingCalendarView.DayCell (TrainingCalendarView.kt:226) with 1,116 CPU seconds on that thread since start; the 12:33 trace has an idle main thread (emulator stall). Two earlier ANRs at 11:05 and 11:08 came while my own uiautomator dumps and the seed script ran together (my error, not logged). The one FATAL EXCEPTION line seen is the uiautomator tool ("UiAutomationService already registered"), not the app. No key was typed into the app.
Seen on emulator only (Android 11, software rendering, no KVM, 3 to 5 s frames, 3-button navigation). Not seen on a device: touch feel, haptics, the rest sound, keep-awake, real timing of the rest bar and snackbars, performance. The -15 s and +15 s taps were accepted (the countdown kept running) but the exact effect on the countdown could not be measured with 10 to 30 s between tap and picture.
Match summary per screen (mockup left, app right):
- Today: close. Header, 7-day strip, dark hero ring and macro bars, Next up card, water card, dock all match; baseline and export cards absent (seed state).
- QuickLog: close, 9 of 11 tiles (hidden ones follow owner decision 1); lime Food and ink Start workout tiles, water pills 8, 16.9, 24 fl oz (mockup 8, 12, 16, 24: owner decision). "Measurements" breaks mid-word (Q7-2).
- FoodDiary: close. Order is Protein, Carbs, Fat (mockup Protein, Fat, Carbs); "Avoiding: Milk and dairy" chip is under the date, not in the header; the tip card is absent.
- FoodPlan: empty state only (no plan in the seed), no mockup match possible; "No plan yet" appears twice.
- Recipes: close. Recipe cards truncate the allergen chip (Q7-12).
- FoodLibrary: close in structure (six rows); the mockup is a grid of cards, the app a single list.
- LogFood: close (source chips, Favourites, chips on rows). Chips row cuts "Photo" at the edge.
- Drinks: close.
- RecipeDetail: does not match: "Ingredients" heading is vertical (Q7-13).
- CookMode: dark as drawn, but step text invisible (Q7-15).
- Train: close. ActiveWorkout: close, with the extra RIR column when effort is on. WorkoutSummary: close; the edit card and records card are new.
- Programs, Insights, You, Settings: close.
Retests ("needs retest" items, 11): 1 partly verified (almo ranking), 1 partly fixed (Q6-8), 1 still failing (Q6-7), 1 still open (Q6-3, not reproduced at 3 s per key), 7 not retested (Q5a-9, Q5b-4, Q6-1, Q6-2, Q6-4, Q6-5, Q6-6).

### Q7-1 · S3 · Settings / Sync and export · "Export everything" and "Import a backup" labels clipped
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. You > Settings > Sync and export, scroll to the bottom.
Expected: two buttons whose two-line labels fit.
Actual: the labels wrap to two lines inside a one-line-high pill; "everything" and "backup" lose their descenders.
Screenshot: design/qa/pass7/q7-1-sync-buttons-clipped.png
Suspect: ui/screens/settings Sync and export button row (fixed height)

### Q7-2 · S3 · Quick log · "Measurements" breaks mid-word ("Measureme / nts")
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Tap the centre +.
Expected: the label fits or wraps at a word break.
Actual: the Measurements tile reads "Measureme" and "nts".
Screenshot: design/qa/pass7/q7-2-quicklog-measurements-wrap.png
Suspect: ui/components Quick log tile text size or tile width

### Q7-3 · S3 · Food Diary · "Fibre" and "Sat fat" for an imperial user (Q6-7), and the carbs target has no unit
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Food > Diary.
Expected: one spelling for a lb user; "/245 g" like protein ("/170 g") and fat ("/70 g").
Actual: "Fibre" and "Sat fat"; Carbs reads "101 /245" with no "g"; at font scale 1.5 it reads "101 /" and the target is cut off.
Screenshot: design/qa/pass7/q7-3-diary-fibre-carbs.png
Suspect: Diary macro tiles (width), nutrient label strings

### Q7-4 · S3 · ActiveWorkout · Rest sheet: the Done button sits under the system navigation bar
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Start a workout. 2. Tap "Rest 2:00".
Expected: Done fully visible.
Actual: the sheet's bottom is behind the 3-button bar; "Done" is half hidden (the sheet has no bottom inset).
Screenshot: design/qa/pass7/q7-4-rest-sheet-done-clipped.png
Suspect: rest sheet ModalBottomSheet insets

### Q7-5 · S3 · Workout summary · The "would end in the future" error is clipped
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Summary > Change date, time and length. 2. Set the length to 45 with start 11:40 AM at 11:51 AM.
Expected: the full message and a disabled Save.
Actual: Save is disabled, but the message "That workout would end in the future. Pick an earlier start or a shorter length." is cut after the first line.
Screenshot: design/qa/pass7/q7-5-edit-time-error-clipped.png
Suspect: the edit dialog's fixed content height

### Q7-6 · S3 · Workout summary · The "Set removed from this workout / Undo" snackbar is under the system navigation bar
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Summary > tap a set > Delete this set.
Expected: the snackbar above the nav bar, readable, with Undo.
Actual: the snackbar sits at the very bottom behind the nav buttons, text and Undo are washed out and overlapped by the back and home icons. Undo is easy to miss. (Add a set shows no snackbar and no Undo at all.)
Screenshot: design/qa/pass7/q7-6-undo-snackbar-under-navbar.png
Suspect: the summary screen's SnackbarHost has no navigation-bar inset

### Q7-7 · S3 · ActiveWorkout · "PREVIOUS" header breaks to "PREVIOU / S" when effort is on
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Train > Settings > Training > Rate effort: RIR. 2. Start Lower B.
Expected: header on one line.
Actual: "PREVIOU" over "S"; the previous cell also wraps ("140.0 x" / "10").
Screenshot: design/qa/pass7/q7-7-active-previous-header-wrap.png
Suspect: set table column weights

### Q7-8 · S3 · ActiveWorkout · The rest bar covers the set row being edited
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Log set 1 (rest bar appears). 2. Tap the weight of set 3 (keyboard opens).
Expected: the focused row stays visible.
Actual: the rest bar floats over the list and half hides set 3 while it is focused.
Screenshot: design/qa/pass7/q7-8-rest-bar-covers-set-row.png
Suspect: rest bar placement above the keyboard

### Q7-9 · S2 · Workout summary / history · Reopen then Finish turns a 7 minute workout into 78 minutes
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Finish Upper B (7 min). 2. Summary > Change date, time and length: start 10:40 AM, length 45, Save. 3. Reopen this workout (timer shows 1:17:31). 4. Finish.
Expected: the length stays what it was (or the user's 45), or the app asks.
Actual: Training log and Train show "Upper B ... 78 min · 800 lb · 2 sets"; the edit is lost and the idle time is counted. This also raised the water target (Q7-24).
Screenshot: design/qa/pass7/q7-9-reopen-length-78min.png
Suspect: reopen clears endedAt and Finish recomputes from startedAt to now

### Q7-10 · S3 · Training stats and Calculators · The same 1RM is shown with two values
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Formula Brzycki. 2. Train > Training stats.
Expected: one figure per set.
Actual: "ESTIMATED 1RM: Romanian Deadlift 186.7 lb from 140.0 x 10" and "Best estimated 1RM 186.5 lb from 140 lb x 10" on the same screen (140 x 36 / 27 = 186.67). Calculators with Epley and 200 x 5 shows 233.5 lb while the formula printed under it gives 233.3. Changing the formula does change the figures (233.5 to 225, Brzycki 200 x 36 / 32 = 225 correct).
Screenshot: design/qa/pass7/q7-10-1rm-186-5-vs-186-7.png (also design/qa/pass7/calc-epley-200x5.png)
Suspect: rounding to 0.5 in one place only

### Q7-11 · S3 · ActiveWorkout · Landscape: Finish and the "..." button are under the right-hand navigation bar
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Start a workout. 2. Rotate to landscape.
Expected: top bar inside the safe area.
Actual: "Finish" reads "Finis h" (cut by the bar) and the "..." is half hidden.
Screenshot: design/qa/pass7/q7-11-landscape-finish-under-navbar.png
Suspect: no horizontal system-bar inset on the workout screen

### Q7-12 · S3 · Recipes / Allergens · Allergen chips are truncated on recipe cards and the recipe page
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Food > Recipes. 2. Open Baked mac and cheese.
Expected: chip wording in full ("Only the name was checked", "Contains butter, cheddar, ...").
Actual: cards read "Contains butter, ..." and "Only the name w..."; the recipe page chip "Contains butter, cheddar, cheese, cream, milk and ..."; the Swap sheet "Contains mac and cheese, butter, cheddar, che...". The safety wording is cut.
Screenshot: design/qa/pass7/q7-12-recipe-chip-truncated.png
Suspect: chip maxLines = 1 with ellipsis

### Q7-13 · S2 · Recipe page · "Ingredients" heading drawn one letter per line
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Food > Recipes > Baked mac and cheese, scroll to Ingredients.
Expected: heading and chip on one row.
Actual: the heading column is squeezed to a few dp by the chip; "I n g r e d i e n t s" fills about 350 dp vertically. Same row at font scale 1.0.
Screenshot: design/qa/pass7/q7-13-ingredients-heading-vertical.png
Suspect: Row with the heading and the verdict chip (no weight on the chip)

### Q7-14 · S3 · Swap · The matched-term list still starts with the recipe's own name (Q6-8)
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Recipe Log a serving > Lunch > Find a safer swap.
Expected: ingredient words only.
Actual: "Contains mac and cheese, butter, cheddar, cheese, milk, cream and whole milk". (The food safety sheet lists "mac and cheese" as "found in the name", which is accurate there.) "Find a safer swap" opened Swap and logged nothing.
Screenshot: design/qa/pass7/q7-14-swap-name-term.png
Suspect: Swap row chip text

### Q7-15 · S2 · Cook mode · Step text is near-black on the dark background
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Food > Recipes > Baked mac and cheese > Cook.
Expected: readable step text on the dark Cook screen.
Actual: "Boil the pasta, make the cheese sauce with milk and butter, bake until bubbling." is drawn in a near-black colour on #0C0F0E and cannot be read; the ingredient panel and the lime Done cooking button are fine. "INGREDIE NTS" also breaks mid-word. The rest is dark as the owner asked, and "Screen stays awake" is shown.
Screenshot: design/qa/pass7/q7-15-cookmode-step-invisible.png
Suspect: ui/screens/cook text colour uses the light theme's ink

### Q7-16 · S2 · Training log · ANR with the main thread in TrainingCalendarView.DayCell
Status: open (emulator load may play a part)
Steps: 1. Train > All sessions > Calendar > Log a past workout. 2. Tap AM, press Back to hide the keyboard.
Expected: no ANR.
Actual: input dispatch ANR at 12:30:14; the app was killed and the launcher came up. The main thread was in androidx.compose ComposedModifier materialize under TrainingCalendarView.DayCell (TrainingCalendarView.kt:226), and the process had 1,116 CPU seconds on its main thread. Logging a past workout was therefore not completed. The default start time in that dialog was 06:00 PM on today's date, 5 hours after the device time, so Log it needed a change first.
Screenshot: none (reason: the ANR killed the screen; trace in the device's /data/anr, not committed)
Suspect: ui/screens/train/TrainingCalendarView.kt DayCell (composed modifier per cell, recomposed under the dialog)

### Q7-17 · S3 · You · "Replay the tour" shows no tour
Status: open (needs a second look)
Steps: 1. You > Replay the tour.
Expected: the Today tour starts.
Actual: Today opens; no tour card appeared in 55 s on the emulator.
Screenshot: none
Suspect: unknown

### Q7-18 · S3 · Workout summary · Add a set gives no confirmation or Undo
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Summary > Add a set > Add.
Expected: a message with Undo (Delete this set has one, see Q7-6).
Actual: a third set appears; no snackbar.
Screenshot: none (the set list is the only change)
Suspect: ui/screens/train workout summary

### Q7-19 · S4 · Workout summary · Small number and unit mismatches
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Finish a 2-set workout (7:32 elapsed).
Expected: consistent units and rounding.
Actual: the summary says "8 minutes", the edit dialog "7"; the exercise header reads "2 x 8 @ 50" with no unit while the sets read "50 lb x 8"; the active workout reads "50.0 lb" on one run and "50 lb" on the repeated one, and a typed "150" stays "150" next to "140.0".
Screenshot: none (numbers in the summary, edit dialog and active workout; see q7-5 for the dialog)
Suspect: number formatting helpers

### Q7-20 · S3 · You · "GOALS AND PLAN" header is squeezed into a narrow column on a fresh install
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Fresh install, skip onboarding. 2. Avatar.
Expected: the eyebrow on one line beside or above the pill.
Actual: "GOALS / AND / PLAN / waiting / for your / details" wraps in a 60 dp column beside the lime "Phase and pace: choose in your profile" pill.
Screenshot: none (reason: seen on the fresh install before the seed import; not saved)
Suspect: You goals card header row

### Q7-21 · S4 · Shell · Content shows through the 3-button navigation bar
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Open Today or Food and scroll.
Expected: a solid or scrimmed system bar area.
Actual: card text ("Water 25.4 of 103.1 fl oz", "Breakfast 596 kcal") is visible behind the back/home/recents icons. The disabled "Compare" button on You is very pale.
Screenshot: design/qa/pass7/Today.png
Suspect: edge-to-edge without a navigation scrim on 3-button navigation

### Q7-22 · S3 · ActiveWorkout · Dark theme: the pause button is barely visible
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Dark theme. 2. Start a workout.
Expected: the pause icon in the text colour.
Actual: pause is drawn in a dim grey on the dark background.
Screenshot: design/qa/pass7/dark-ActiveWorkout.png
Suspect: icon tint

### Q7-23 · S3 · Font scale 1.5 · Clipping on several screens
Status: Partly fixed in f85edd6, needs retest. Remaining: the Food segmented control and the carbs target at 1.5x. Was: open
Steps: 1. font_scale 1.5.
Expected: no clipping.
Actual: Food segmented control shows "Library" cut at the right edge (not scrollable by sight); Today "Open check-in" is fine but the Camera-permission card on Log food > Scan clips its "Open app settings" button; the ActiveWorkout set rows go to two lines and the RIR column header disappears (a bare "-" is left), and the top bar title wraps "0:46 . set 1 of / 15".
Screenshot: design/qa/pass7/f15-ActiveWorkout.png, design/qa/pass7/f15-Today.png, design/qa/pass7/f15-FoodDiary.png
Suspect: segmented control, camera card, set row layout

### Q7-24 · S4 · Water target · The target jumps after a short workout with no explanation
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Seeded Today shows "49.4 of 103.1 fl oz". 2. Finish a 7 minute workout, then a 9 minute one.
Expected: a small change at most, or a reason.
Actual: Today then reads "of 138.6 fl oz" and You reads "145.4 fl oz . adjusts for the day"; previous days still show 103.1.
Screenshot: none (numbers on Today and You)
Suspect: water target workout adjustment (also fed by Q7-9's 78 minutes)

### Q7-25 · S4 · Context and copy · Leftovers from the redesign
Status: Fixed in f85edd6, needs retest. Was: open
Steps: 1. Settings. 2. Food > Plan and Shopping list. 3. Today date picker.
Expected: no stale names or doubled titles.
Actual: the Settings row is still "Nutrition" (tab is Food); "Shopping list" appears twice (bar and heading); the Plan empty state says "No plan yet" and "No meal plan yet"; Library's My foods opens the Log food screen, not a list of foods; the Today date picker starts weeks on Sunday while the Training calendar starts on Monday; the segmented controls do not fill the track (empty space on the right).
Screenshot: design/qa/pass7/FoodPlan.png
Suspect: strings, Library routes

Summary for Pass 7: 25 new findings. S1: 0. S2: 4 (Q7-9, 13, 15, 16). S3: 17 (Q7-1 to 8, 10 to 12, 14, 17, 18, 20, 22, 23). S4: 4 (Q7-19, 21, 24, 25). Worked as designed: dock and Quick log, water one-tap with Undo, Food segments and Library entries, 7-day strip paging and the calendar, Find a safer swap, rest bar with -15 s, +15 s, Skip, rest presets, pause and resume, RIR column and menu, records on the summary, Training stats, Calculators and the 1RM formula setting, history edit with the future check, delete a set with Undo, Reopen, Repeat, Discard, Training log List and Calendar. Not tested: see Not covered. A retest is needed after fixes.

---
## Pass 8 — Retest of Pass 7 fixes
Date: 2026-11-24 (device zone set to Europe/London so the seed was not near midnight; the status bar showed a clock 4 hours ahead of `date`, an emulator zone quirk) · Build: the prebuilt release/intake-preview.apk (com.zeroindex.intake.preview, 0.1.0+2026-11-24, build 26112406), repo head 291cc06 · AVD: phone30 (Android 11, no KVM, software rendering, 3-button navigation) · Seed: a temporary copy of tools/qa/seed.sh pointed at the preview package (data dated 2026-11-23 and earlier; Today starts with nothing eaten). All findings are **emulator only**; nothing here was seen on a phone.
Seed note: the script reached Settings but stopped, because "Skip setup" now starts the 6-step Today tour and Settings is a hub whose "Sync and export" page holds the Import button. The import was finished by hand through the picker (561 changes). tools/qa/seed.sh scrolls to "Import a backup" but not through the hub, so it may still stall on a fresh install.
Screenshots: design/qa/pass8/ (half size or smaller, 4.2 MB, no personal photos).
Method: one action at a time, no concurrent dumps and seed runs. Screenshots lag a screen change by 2 to 10 s on this emulator, so a screenshot taken straight after a tap can show the old screen; dumps were used to confirm.

### Retest table
| ID | Status | Evidence (emulator only) |
| --- | --- | --- |
| Q7-1 sync buttons clipped | Verified | q7-1-sync-buttons.png: "Export everything (JSON)" and "Import a backup" wrap to 3 and 2 lines inside taller pills, nothing cut |
| Q7-2 "Measurements" mid-word | Verified | q7-2-quicklog-measurements.png: one line (the label is a smaller size than its neighbours, see Q8-4) |
| Q7-3 Fibre, Sat fat, carbs unit | Verified | q7-3-diary-fiber-carbs-unit.png: "Fiber", Carbs "/245 g". "Sat fat" is still the label (see Q6-7). 1.5x not rechecked |
| Q7-4 rest sheet Done under nav bar | **Still failing** | q7-4-rest-sheet-done-still-clipped.png: the sheet's "Done" button is still cut in half by the navigation bar (its centre is at y 2133, the bar starts about 2145) |
| Q7-5 future-end error clipped | Verified | q7-5-future-error.png: the whole message shows on two lines and Save is disabled. The helper line "The sets move with the start time." is half covered by the error (Q8-5) |
| Q7-6 delete-set snackbar under nav bar | Verified | q7-6-set-removed-snackbar.png: above the bar, readable, Undo visible |
| Q7-7 "PREVIOUS" header | Verified (RPE mode) | q7-7-rpe-header.png: header on one line; the previous cell has two lines on purpose ("140.0 x 10" over "RPE 8"). RIR mode not run |
| Q7-8 rest strip covers focused row | Verified | q7-8-keyboard-open-strip-hidden.png: with the keyboard open on set 3's weight the strip is gone and the row is visible. q7-8-strip-returns-after-keyboard.png: Back, and the strip (0:12 left, -15 s, +15 s, Skip) is back. A screenshot 8 s after the tap still showed the strip (q7-8-keyboard-just-opened-strip-still-shown.png), so the hide lags on this emulator |
| Q7-9 reopen + finish keeps length | Verified | q7-9-summary-9min.png (9 min), then length set to 45 and start 05:20; Reopen showed the timer at 45:21 (was 1:17:31); Finish: q7-9-reopen-finish-46min.png "46 minutes". Train and Training log both read "46 min · 800 lb · 2 sets". Note: the start time moved from my edited 05:20 to 06:49 (finish time minus the length), so the edited start is not kept (Q8-6) |
| Q7-10 two values for one 1RM | Verified | q7-10-training-stats-186-7.png: Romanian Deadlift 186.7 in both places; q7-10-calculator-epley-233-3.png: 200 x 5 Epley = 233.3 lb, same as the formula |
| Q7-11 landscape Finish under nav bar | Verified | q7-11-landscape-finish-ok.png: Finish and "..." fully inside the safe area, right-hand bar solid |
| Q7-12 allergen chips truncated | Verified | q7-12-recipe-cards-chips-wrap.png: card chips wrap to 4 lines and read in full; recipe page and Swap sheet in full. See Q8-3 for the chip drawing |
| Q7-13 "Ingredients" heading vertical | Verified | q7-13-ingredients-heading.png: heading on one line, chip wraps on the right |
| Q7-14 Swap matched terms start with the recipe name | Verified | q7-14-swap-no-name-term-but-breakfast.png: "Contains butter, cheddar, cheese, milk, cream and whole milk". But see Q8-1 |
| Q7-15 Cook mode step text near-black | Verified | q7-15-cookmode-readable-grey-navbar.png: white step text, "INGREDIENTS · 0 OF 4 READY" on one line, Done cooking above the bar. The nav bar is a mid grey here (Q8-2) |
| Q7-16 Log a past workout / ANR | Verified | Train log > Calendar > Log a past workout: default 06:00 PM shows the future error (Log it disabled); tapped AM, Back to close the keyboard, chose Lower B, Log it. No ANR from this flow. q7-16-past-workout-logged.png, q7-16-calendar-shows-logged-workout.png: Nov 24 lists "Upper B 46 min" and "Lower B 60 min · 0 sets". The calendar is Monday-first. The default start is still a future time (Q8-7) |
| Q7-17 Replay the tour | Verified | q7-17-replay-tour.png: "Tour, 1 of 6" starts on Today in about 15 s |
| Q7-18 Add a set: no Undo | Verified | q7-18-set-added-undo.png: "Set added to this workout. Undo" above the bar; Undo took the set count 4 back to 3 |
| Q7-19 number and unit mismatches | Partly | Summary "9 minutes" and the edit dialog "9" now agree; the exercise header reads "2 x 8 @ 50 lb" with a unit. The active workout still reads "50.0 lb" while the summary reads "50 lb" |
| Q7-20 You "GOALS AND PLAN" header | Not reached | needs a fresh install; the seeded You page (header over a lime pill, one column) is fine |
| Q7-21 content through the nav bar | Verified | q7-21-light-today-navbar.png (solid light bar), q7-21-dark-today-navbar.png (solid black), landscape-*.png (solid right-hand bar). Cook mode is grey (Q8-2) |
| Q7-22 dark pause button | Verified | q7-22-dark-pause-visible.png: pause drawn in white |
| Q7-23 font scale 1.5 | Partly | q7-23-font15-food-library-clipped.png: Food segmented control still cuts "Library" at the right edge, and the dock label "Insights" touches its edge (**still failing**). q7-23-font15-activeworkout.png: set rows now stack (LB, REPS, effort under the previous value), but the top bar title still wraps ("0:00 . set 1 of / 15") and the effort field has no label (Q8-8). dark-font15-settings.png: Settings fine. Camera card on Log food > Scan not retested |
| Q7-24 water target jump | Partly | Settings > Water and drinks now explains it ("Raises the target on hot days and after workouts", Training-day increase 16.9 fl oz). But after a 46 min workout and a 60 min empty one, Today and You read 150.5 fl oz (it was 103.1), with no breakdown on either screen |
| Q7-25 leftovers | Partly | Verified: Settings group is "Food"; "Shopping list" now has the heading "What are you making?"; Plan empty state reads "Your plan / No meal plan yet" once; the segmented control fills the track; My foods opens the user's foods ("Filter your foods", Favourites) but the screen is titled "Log food". **Still failing: both date pickers (Today, Food Diary) start the week on Sunday** (q8-monday-first-todaypicker-sunday.png, q8-monday-first-foodpicker-sunday.png), while the Training calendar starts on Monday. Device locale is en-US, so a locale rule may be doing this; if Monday-first is the rule, the pickers do not follow it |

Older items:
| ID | Status | Evidence |
| --- | --- | --- |
| Q5a-9 meal template log message | Partly | q5a-9-template-log-snackbar.png: one snackbar "Logged Post-workout shake to Snacks. Almond milk, unsweetened: check the label for dairy" with Undo, no lingering inline line, "Snacks" capitalised. The slot list still offers "Drinks" for a food template |
| Q5b-4 template log no confirmation | Verified | same screenshot |
| Q6-1 floor warning layout | Verified | q6-1-floor-warning-layout.png: 1000 kcal shows the warning in its own box under the row; Save and the field stay in place. The label column beside the field is narrow ("Calories by / hand" with the help text wrapping to 6 lines), which is cramped but not collapsed. Restored 2260 afterwards (see Q8-9) |
| Q6-2 Undo does nothing | Partly | Template: log then Undo took Today from 338 back to 0 eaten; a second log + Undo left 338 (so Undo works). Diary entry delete + Undo and the jump test Undo: not confirmed, because the model turn between tap and Undo is longer than the snackbar; Progress jump test not reached |
| Q6-3 Log food search ANR | Not reproduced, ranking Still failing | "dark" and "almo" typed 2 to 3 s per key: no ANR, all characters kept. Fast `input text` not retried. "almo": first row in MY FOODS AND RECENTS is Salmon fillet; a "Drinks: almo" row sits above it |
| Q6-4 check-in week vs suggestions | Verified | q6-4-checkin-window-stated.png: card is "Week of Nov 16-22", suggestions say "Check-in of Tue, Nov 24, 2026 · Based on the 7 days Nov 18-24". The windows still differ but are stated |
| Q6-5 LOG MORE counts | Not reached | needs the sparse-week seed |
| Q6-6 Shopping Clear | Verified | q6-6-shopping-clear-undo-snackbar.png: "Shopping list cleared. Undo"; Undo brought back the 9 items and both picks (the list then read "1 ticked", which I cannot explain: I had ticked nothing, so it may have been my tap) |
| Q6-7 wording / dates | Partly | Insights date style "Mon, Oct 26, 2026" matches Today; Diary "Fiber" but "Sat fat"; plan form not checked |
| Q6-8 recipe name in terms | Verified | cards, recipe page and Swap sheet list ingredient words only |

Other coverage:
- New behaviour: rest strip hides with the keyboard and returns after (Q7-8); "Set added" Undo (Q7-18); Settings group "Food" (Q7-25); Monday-first pickers fail (Q7-25).
- "Still training?" prompt: set the device clock +4 h after one set; the prompt appeared ("Nothing has been logged for about 4 hours. Your last set was at 08:34"), "Finish at 08:34" gave a 2 minute workout. still-training-prompt.png. (The clock was set back afterwards, see Q8-10.)
- RPE mode: effort cell opens RPE 10, 9, 8, 7, 6 with "2 left in the tank" wording; RPE 8 saved on set 1.
- Quick log tiles: Quick calories (Log food in quick add), Meal template, Weight, Measurements (opens the same Log weight form with its measurement fields), Progress photo (Add a photo), Drink (LOGGED TODAY) all open. Scan barcode and the camera paths were not opened.
- Chips: "Fits your allergens", "Only the name was checked", "Check the label: ...", "Contains ..." and "May contain milk (trace)" (may-contain-chip.png) seen. The "Checking..." chip was not caught (USDA rows briefly show no chip while the scan runs).
- Onboarding (Settings > About and help > Show the welcome again): steps 1 to 10 walk with Skip and Continue above the nav bar (welcome-step1.png). Step 9 has three "Set up" buttons (Health Connect, Backups, AI shortcuts). "Set up Health Connect" opens the Health Connect screen ("not installed on this phone"). Backups and AI shortcuts were not reliably reached (Q8-7).
- Landscape: Food Diary, Log food, Settings and Train/ActiveWorkout (rail on the left, solid bar on the right, nothing under the bar): landscape-*.png, q7-11-landscape-finish-ok.png. Log food shows about one row of the list in landscape.
- Dark: Today, ActiveWorkout, Quick log, Settings (1.5x), Cook mode not rechecked in dark.

### Q8-1 · S3 · Recipe > Log a serving > Find a safer swap · The swap always says "breakfast" and logs to breakfast
Status: open
Steps: 1. Food > Recipes > Baked mac and cheese > Log a serving. 2. Choose Lunch (or Dinner) in the slot list. 3. In the safety sheet tap "Find a safer swap". 4. On the Swap sheet tap Choose on Chicken fajita bowls.
Expected: "Swap lunch" (the chosen slot) and the swapped recipe logged to that slot.
Actual: the sheet reads "Swap breakfast / Nothing planned for breakfast ... Choosing one logs it to breakfast" for Lunch and for Dinner; Choose logged "Chicken fajita bowls to breakfast" (572 kcal under Breakfast in the Diary, which I then deleted). The first Undo tap came too late to test.
Screenshot: design/qa/pass8/q7-14-swap-no-name-term-but-breakfast.png, design/qa/pass8/q8-1-swap-logged-to-breakfast.png
Suspect: the slot is not passed from the Log a serving sheet to the Swap route (default Breakfast)

### Q8-2 · S4 · Cook mode · Navigation bar is a mid-grey band under a near-black screen
Status: open
Steps: 1. Light theme. 2. Recipe > Cook.
Expected: a bar colour that matches the dark screen (the Dark theme uses black, Light uses near white).
Actual: the bar is a mid grey (about 60 percent lightness), a third colour not used elsewhere.
Screenshot: design/qa/pass8/q7-15-cookmode-readable-grey-navbar.png
Suspect: NavigationBarScrim uses the light-theme scrim on a screen with its own dark background

### Q8-3 · S4 · Recipe page · The wrapped allergen chip draws its text outside its pill
Status: open
Steps: 1. Recipe > Baked mac and cheese, look at the chip beside "Ingredients".
Expected: padding on all sides of the 5-line text.
Actual: the first and last lines ("Contains butter," and "milk") touch or cross the pill's top and bottom edges; the pill is about as tall as the text.
Screenshot: design/qa/pass8/q7-13-ingredients-heading.png
Suspect: chip with fixed or minimum height and maxLines removed

### Q8-4 · S4 · Quick log · The "Measurements" label is smaller than the other tile labels
Status: open
Steps: 1. Tap the centre +.
Expected: the same label size on every tile.
Actual: "Measurements" is about 20 percent smaller so it fits on one line.
Screenshot: design/qa/pass8/q7-2-quicklog-measurements.png

### Q8-5 · S4 · Workout summary edit dialog · The helper line is overlapped by the error
Status: open
Steps: 1. Change date, time and length, set a length that ends in the future.
Expected: helper and error one under the other.
Actual: "The sets move with the start time." is cut in half by the red message below it.
Screenshot: design/qa/pass8/q7-5-future-error.png

### Q8-6 · S4 · Workout summary · Reopen then Finish moves the start time and drops the edited start
Status: open
Steps: 1. Finish a workout, change its start to 05:20 and length to 45. 2. Reopen, Finish.
Expected: the edited start, or a question.
Actual: length kept (46 min) but the start became the finish time minus the length (06:49).
Screenshot: design/qa/pass8/q7-9-reopen-finish-46min.png

### Q8-7 · S3 · Onboarding / Past workout · Back from a "Set up" target drops the welcome flow; past workout default is in the future
Status: open (partly unverified)
Steps: 1. About and help > Show the welcome, go to step 9, tap "Set up Health Connect", press Back.
Expected: return to step 9.
Actual: the screen behind was About and help (where I started it), and the welcome flow was gone; a second run ended on You after tapping "Set up Backups", so I could not confirm those two buttons. On a fresh install the destination after Back is unknown. Also: "Log a past workout" opens with 06:00 PM today (a future time at most hours) and a red error, so the first thing the user must do is change AM or PM.
Screenshot: none for the Back case (screen change not captured); past workout: design/qa/pass8/q7-16-past-workout-logged.png after the fix-up
Suspect: welcome flow state not kept across navigation; default start time

### Q8-8 · S4 · Font scale 1.5 · ActiveWorkout effort field has no label and the title wraps
Status: open
Steps: 1. font_scale 1.5. 2. Start a workout with RPE on.
Expected: LB, REPS and RPE all labelled; the title on one line.
Actual: LB and REPS have labels, the effort field is a bare "-"; "0:00 . set 1 of / 15" wraps.
Screenshot: design/qa/pass8/q7-23-font15-activeworkout.png

### Q8-9 · S4 · Settings > Food · Re-saving the same calories changes protein and carbs
Status: open
Steps: 1. Calories by hand 2260, Save (after 1000 and back to 2260). 2. You.
Expected: protein 170 g, carbs 245 g unchanged (they were 170 / 70 / 245 before).
Actual: You reads "166 . 70 . 242 g" (1.9 g per kg of 192 lb); the hand-set grams were recomputed from the per-kg values.
Screenshot: none (numbers on You; the Today ring read 170 / 245 / 70 before)

### Q8-10 · S3 · ActiveWorkout · Timer stays at 0:00 (probably my clock change)
Status: open, needs a clean repro
Steps: 1. Set the emulator clock forward 4 h (`date`), accept "Finish at ...", set it back 4 h. 2. Start Upper A.
Expected: the timer counts up.
Actual: "0:00 . set 1 of 15" for more than 5 minutes, on two workouts and after a force-stop. The first Pass 8 workouts, before the clock change, counted normally. This is very likely caused by moving the wall clock back, so it may not occur on a phone, but a clock set back by the user or a time-zone change could do the same.
Screenshot: none (reason: a frozen number is not visible in one frame)

### Logcat
No FATAL EXCEPTION for com.zeroindex.* and an empty crash buffer. Two ANRs for com.zeroindex.intake.preview: 09:11:35 and 09:14:44 (device clock), both "Input dispatching timed out ... Waited 5005ms for FocusEvent(hasFocus=false)", both within seconds of dismissing the Today / Food date picker (Back) or switching night mode, and both with the main thread idle in the looper (epoll) and system_server at 48 percent CPU. I read them as emulator stalls, not an app hang (the app was at 236 CPU seconds, not the 1,116 of Pass 7), but the trigger was a dialog losing window focus, so a device check is worth it. The Q7-16 path (Log a past workout) produced no ANR. The app was killed by each ANR and relaunched by hand.

### Summary for Pass 8
Retests: 35 items (Q7-1 to 25 and 10 older). Verified 24: 19 from Pass 7 (Q7-1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 21, 22) and 5 older (Q5b-4, Q6-1, Q6-4, Q6-6, Q6-8); Still failing 1 (Q7-4); Partly 4 of Pass 7 (Q7-19, 23, 24, 25) and 4 older (Q5a-9, Q6-2, Q6-3, Q6-7); Not reached 2 (Q7-20, Q6-5). New: 10 findings, S1 0, S2 0, S3 3 (Q8-1, 7, 10), S4 7 (Q8-2, 3, 4, 5, 6, 8, 9).
Not verifiable on an emulator: touch feel and haptics, the rest sound and vibration, keep-awake, real snackbar timing (Undo was tested only where a script could tap within 2 s), performance and ANR rates on a phone, gesture navigation, Health Connect, the camera and barcode paths, Android 15 behaviour.

### Fixes after Pass 8 (merged from the three fix-pass8 branches)
Every item below is **Fixed in 8607249, needs retest** (not yet seen on an emulator or a phone).
- Q7-4 rest sheet Done under the nav bar: Fixed in 8607249, needs retest.
- Q7-19 number and unit mismatches: Fixed in 8607249, needs retest.
- Q7-23 font scale 1.5: Fixed in 8607249, needs retest.
- Q7-24 water target jump: Fixed in 8607249, needs retest. The breakdown is now shown. A past workout with 0 sets still counts toward the sweat adjustment (owner decision).
- Q7-25 leftovers: Fixed in 8607249, needs retest. The titles are fixed. The week start in the date pickers was reverted to the locale default (owner decision): a Monday-first region such as en-GB also changed the input format to dd/MM/yyyy and the headline to "Tue, 24 Nov", which is worse for a US user than a Sunday-first week. All pickers still use one shared helper.
- Q5a-9 meal template log message: Fixed in 8607249, needs retest.
- Q6-7 / Q7-3 wording (Saturated fat): Fixed in 8607249, needs retest.
- Q8-1 to Q8-10: Fixed in 8607249, needs retest.
- Q6-3 Log food search ranking: No code path found; NameMatchRank already ranks Almond first. Retest, and note which screen or search source shows Salmon first.


## Pass 9 — Retest of Pass 8 fixes
Date: 2026-11-24 (device zone Europe/London) · Build: release/intake-preview.apk (com.zeroindex.intake.preview, repo head 2894563, fixes from 8607249), **plus a one-line local patch to `Segmented` (see Q9-1)** · AVD: phone30 (Android 11, no KVM, software rendering, 3-button navigation) · Seed: tools/qa/seed.sh pointed at the preview package (temporary copy, not committed). **Emulator only**; nothing here was seen on a phone.
Screenshots: design/qa/pass9/ (half size, 4.9 MB, no personal photos).

**Read this first: the APK as shipped in release/ crashes on the Food tab and the Train tab (Q9-1, S1).** Every result below except the three crash reproductions was taken on the same commit rebuilt with a one-line fix to `Segmented` (installed over the preview APK; the source change was not committed, it is in Q9-1 below). Until that is fixed in the branch, none of Food, Train, Settings > Appearance and units, or anything else that draws a `Segmented` can be opened.

### Seed script
`tools/qa/seed.sh` (temporary copy with `PKG` and `APK` pointed at the preview build) **worked end to end on a clean install**: install, skip the welcome, skip the tour, You, Settings hub, Sync and export, Import a backup, picker, "Imported 567 changes", back to Today. About 13 minutes on this emulator, 3.5 of them the install. A second run with `--no-install` also worked (401 changes, a sparse seed). One caveat: its swipes are hard-coded to y 1800 to 600, so it stalls ("not found after scrolling: Import a backup") if the screen is smaller than 1080x2280. It failed once for exactly that reason after I had set `wm size 720x1520`; it passed after `wm size reset`. Not an app problem, but the script would be sturdier with swipes taken from `wm size`.

### Retest table (items marked "Fixed in 8607249, needs retest")
| ID | Status | Evidence (emulator only) |
| --- | --- | --- |
| Q7-4 rest sheet Done under nav bar | Verified | q7-4-rest-sheet-done-visible.png: "Done" is a whole pill above the navigation bar, nothing cut |
| Q7-19 number and unit mismatches | Verified | q7-19-summary-units.png: summary "3 x 8 @ 50 lb", "Set 1: 50 lb x 8"; the active workout header reads "3 x 8-12 . 50 lb" (the set rows show "50.0" under an "LB" column header, which is a column unit, not a mismatch) |
| Q7-23 font scale 1.5 | Verified | q7-23-font15-food-segmented.png (Diary, Plan, Recipes, "Library" all whole, text shrinks), q7-23-font15-train-segmented.png, dock "Insights" inside its pill; q8-8-font15-activeworkout-title.png: title "0:20 . set 1 of 15" on one line. See Q9-2 for a new 1.5x problem |
| Q7-24 water target jump | Verified | q7-24-today-water-breakdown.png: the Today water card shows "Base 103.1 fl oz + workout +10.1" with a caret, tapping it expands "Base: 103.1 fl oz (35 ml per kg ...)" and "Workout sweat: +10.1 fl oz (21 min of training at a moderate (0.8 L/h) rate. Replaces the flat training-day amount)". q7-24-you-water-line.png: You reads "113.3 fl oz / Water / Base 103.1 fl oz + workout +10.1". The 0-set past workout rule was not rechecked |
| Q7-25 leftovers | Verified, with a note | q7-25-myfoods-title.png: the screen is titled "My foods". q7-25-today-datepicker-sunday-first-enUS.png: the Today picker (device locale en-US) shows "Nov 24, 2026", Sunday-first, buttons "Today" and "Show this day". That is the locale default, as decided; not failed. The Food Diary picker and a Monday-first locale were not run |
| Q5a-9 template log message | Verified | q5a-9-template-snackbar-snacks.png: one snackbar "Logged Post-workout shake to Snacks. Almond milk, unsweetened: check the label for dairy" with Undo. The slot list is Breakfast, Lunch, Dinner, Snacks, no "Drinks". See Q9-3 for a capitalisation difference elsewhere |
| Q6-7 / Q7-3 Saturated fat | Verified | q6-7-diary-saturated-fat-food-segmented.png: Food Diary shows "Saturated fat 4.2 g" and "Fiber"; Carbs "/245 g". Plan form and 1.5x not checked |
| Q8-1 swap goes to the picked slot | Verified | Recipe > Log a serving > Lunch > Find a safer swap: q8-1-swap-lunch-recipe.png "Swap lunch / Nothing planned for lunch"; q8-1-swap-logged-to-lunch.png "Logged Chicken fajita bowls to lunch". From Cook mode (Done cooking > Log a serving > Dinner): q8-1-swap-dinner-from-cook-mode.png "Swap dinner". The Food Diary afterwards listed one Chicken fajita bowls under Lunch and one under Dinner (none under Breakfast). My two Undo taps came after the snackbar had gone, so Undo is still untested (as in Pass 8) |
| Q8-2 Cook mode nav bar | Verified | q8-2-cookmode-navbar-dark.png: the bar is black, the same as the screen |
| Q8-3 wrapped allergen chip | Verified | q8-3-recipe-chip-padding.png: the 5-line "Contains butter, cheddar, cheese, cream, milk and whole milk" chip has padding on all four sides |
| Q8-4 quick log labels | Verified | q8-4-quicklog-labels.png: "Measurements", "Quick calories", "Meal template", "Scan barcode" and the rest are one size, "Measurements" on one line; "Progress photo" wraps to two lines |
| Q8-5 helper line vs error | Verified | q8-5-helper-and-error.png: "The sets move with the start time." and the red future-end message are stacked, nothing overlaps; Save is disabled |
| Q8-6 Reopen then Finish | Verified | Finished a workout, set its start to 11:00 AM and length to 20, Reopen (timer read 20:25), Finish: q8-6-reopen-finish-keeps-start.png "Tuesday 24 November, 11:00", "21 minutes" (the 20 minutes plus the 25 s it was open). The edited start is kept and the length is kept |
| Q8-7 Back from Set up, shortcuts, past workout | Verified | After Show the welcome again (and also on a fresh install after clearing data), step 9: tapped Set up Health Connect, Set up Backups (opens Sync and export) and Set up AI shortcuts, each time Back returned to "Step 9 of 10" (q8-7-back-returns-to-step9.png, q8-7-fresh-install-back-to-step9.png). Log a past workout opens at 12:30 PM with the clock at 1:32 PM, no error, "Blank workout" preselected and "Log it" enabled (q8-7-past-workout-default-start.png, q8-7-past-workout-log-it-enabled.png); Log it went straight to a 60-minute summary |
| Q8-8 font 1.5 ActiveWorkout | Verified | q8-8-font15-activeworkout-rpe-label.png: with RPE on, LB, REPS and RPE are all labelled, title on one line. See Q9-2 |
| Q8-9 same calories keep macros | Verified, with a note | Settings > Food: with 2260 unchanged the Save button is disabled (greyed), so the same value cannot be re-saved; You kept "170 . 70 . 245 g". Changing to 2270 and Save gave "166 . 70 . 244 g" (recomputed from 1.9 g per kg), which is the expected result of a real change |
| Q8-10 timer after the clock goes back | **Still failing** | Started Upper B at 12:24 (0:51 at 12:25:02), set the device clock back 2 h (10:25). q8-10-timer-after-clock-back.png: the title reads "0:00 . set 1 of 15" at once and was still "0:00" about 70 s of device time later (second frame 10:26). It does not keep counting; it waits for the wall clock to catch up with the start. After the clock was put back to the right time the timer showed the right elapsed time (2:57). The Pass 8 note (frozen for minutes) stands. Only a manual clock change does this; S3 was probably high, S4 on a phone |
| Q7-20 You "GOALS AND PLAN" | Verified | q7-20-you-goals-header-fresh-install.png: on a fresh install (data cleared) the dark card header is "GOALS AND PLAN / waiting for your details" on its own lines, over the lime pill, with "No target yet" and the explanation below; nothing squeezed |
| Q6-5 LOG MORE counts | Verified | A sparse seed (food on only 2 days, about 200 kcal each): q6-5-sparse-checkin.png: "For the 7 days Nov 18-24: you logged food on 2 days, 0 of them full days (800 kcal or more), and weighed in on 6 days. The check needs 5 full days and 3 weigh-ins." The week card in the same screen says "Avg intake . 2 days logged". Both say 2 days now and the card says what a full day is. The card window (Nov 18-24) differs from the chip week (Nov 16-22) and is stated |
| Q6-3 "almo" ranking | Partly (finding below) | No ANR with 2 s per key |
| Scan barcode tile | Partly | Quick log > Scan barcode opens the scanner (scan-barcode-tile-emulator-no-camera.png; the emulator's virtual camera then draws a test scene, scan-barcode-virtual-camera-and-typed-code.png) with "Or type the barcode number" and Look up. A real scan and a typed lookup (needs network, the emulator is offline) were not completed |
| "Checking..." allergen chip | Not reached | Typed "milk" in Log food with a screenshot every few seconds: the rows showed their final chips ("Contains milk, milk chocolate, milk powder and whole milk") and "Searching Open Food Facts..." but no "Checking..." chip |

### Q6-3 "almo": which surface ranks Salmon above Almond
- **Log food > All sources:** a "Drinks: almo" row first ("Drinks matching "almo" are logged from Drinks"), then MY FOODS AND RECENTS with **Salmon fillet, baked as the only row**. No Almond food is listed. (q6-3-logfood-allsources-almo.png) The USDA "Almond butter, creamy" is not shown in All sources.
- **Log food > Mine:** same as All sources, Salmon fillet only.
- **Log food > Recipes:** "Salmon, sweet potato and broccoli" only (there is no Almond recipe in the seed). **Log food > USDA:** "Almond butter, creamy" only. **Log food > OFF:** "Open Food Facts could not be reached" (offline).
- **Library > My foods (Filter your foods):** "Almond milk, unsweetened" first, then "Salmon fillet, baked". Correct order. (q6-3-myfoods-almo-almond-first.png)
- **Food > Recipes > Search your recipes:** "Salmon, sweet potato and broccoli" only. (q6-3-recipes-almo.png)
- **Library > Drinks:** "Almond milk, unsweetened" (twice, see Q9-6) and USDA almond milks; no Salmon.
So the one place where Salmon is above Almond is Log food (All sources and Mine). It is not a ranking inversion: the Almond milk food that My foods lists is a drink and Log food leaves it out of the food list, pointing to the "Drinks: almo" row above instead. Someone typing "almo" in Log food sees Salmon first and no Almond food.

### Regressions checked
- Train segmented control (Workout / Exercises / Videos): fine at normal and at 1.5x (regress-train-segmented-normal.png, q7-23-font15-train-segmented.png), after the Q9-1 patch.
- Chips on recipe cards: padded, wrap, all readable (regress-recipe-card-chips.png).
- Quick log grid: see Q8-4; "Start workout" is the dark tile.
- Timer on a normal workout (no clock change): counted up through set logging, a rest sheet, an app kill and relaunch (the workout kept counting, 6:42 after the relaunch), pause (frozen at 8:26 for 47 s, regress-timer-paused.png) and resume (8:57 about 30 s later).
- Today strip (Wed 18 to Tue 24, today dark, regress-today-strip.png) and the Training log calendar (Monday-first, 7 training days, Nov 24 marked; no ANR on the DayCell; regress-training-calendar.png).

### New findings
### Q9-1 · S1 · Segmented control · The app crashes on opening the Food tab, the Train tab, and any other screen with a segmented control
Status: open. Found in release/intake-preview.apk (head 2894563 and 8607249, "Segmented fit" change).
Steps: 1. Install the APK, open Today. 2. Tap Food (or Train). (Settings > Appearance and units, Allergens, Menu sources, Weather settings, progress photos and Food detail use the same control.)
Expected: the tab opens.
Actual: the app closes at once. Four times in a row (Food, Train, and Quick log > Start workout which opens Train): `java.lang.IllegalStateException: measure() may not be called multiple times on the same Measurable` at `ControlsKt$Segmented$1$1$1.measure` (Controls.kt:202). In the measure policy each segment is measured twice: once to find the height (`first`), then again for the final size.
Screenshot: none worth keeping (the launcher is all that is left); the stack is in logcat.
Suggested fix, tried here and it worked on the same build (Food, Train and the 1.5x tabs all rendered): take the height from the intrinsics instead of a first measure. In `Controls.kt` replace the two lines
`val first = measurables.mapIndexed { i, m -> m.measure(Constraints(minWidth = widths[i], maxWidth = widths[i])) }` and `val height = first.maxOfOrNull { it.height } ?: 0`
with `val height = measurables.mapIndexed { i, m -> m.maxIntrinsicHeight(widths[i]) }.maxOrNull() ?: 0`, and keep the single `m.measure(... minHeight = height, maxHeight = height)` pass. A unit or instrumented test that composes `Segmented` once would have caught this.

### Q9-2 · S4 · Font scale 1.5 · ActiveWorkout exercise names and muscle lists break in the middle of words
Status: open.
Steps: 1. font_scale 1.5. 2. Start Upper B or Lower B.
Expected: whole words on each line, or a layout that gives the name more width.
Actual: the exercise card header column is only about 3 words wide (thumbnail, name, "Form" and the "..." button side by side): "Incline Dumbb/ell Press", "Romani/an Deadlif/t", "hamstring/s, glutes". The title bar and the set rows (Q8-8) are fine.
Screenshot: design/qa/pass9/q8-8-font15-activeworkout-title.png, q8-8-font15-activeworkout-rpe-label.png

### Q9-3 · S4 · Swap · The "Logged ... to lunch" message uses a lower-case slot name
Status: open.
Steps: 1. Recipe > Log a serving > Lunch > Find a safer swap > Choose.
Expected: "Logged Chicken fajita bowls to Lunch" (the meal-template message says "to Snacks").
Actual: "Logged Chicken fajita bowls to lunch" (and "to dinner" from Cook mode).
Screenshot: design/qa/pass9/q8-1-swap-logged-to-lunch.png

### Q9-4 · S3 · ActiveWorkout and workout summary · Repeated "not responding" kills on the emulator, reproducible on dialogs
Status: open, needs a device check. Emulator only.
What I saw: 15 ANRs for the preview app in about 3.5 hours (10:47 to 13:50 device time), all "Input dispatching timed out ... Waited 5 s for FocusEvent(hasFocus=false)" or a MotionEvent, nearly all within a minute of opening or closing a dialog or sheet on ActiveWorkout or the workout summary (rest sheet, Save in "Change date, time and length", the "Still training?" dialog). The "Still training?" dialog, which a reopened workout shows when its last set is old, was reproducible: five tries, each ended with the app killed after tapping "Keep going", "Finish at ..." or Back, so I could not dismiss it. The main thread in the ANR traces was busy in Compose recomposition and drawing (ActiveWorkoutScreen, ExerciseThumb) rather than blocked on I/O; the app used about 100 percent of a core on those screens, against 0 percent on Today. I also ran the screen at 720x1520 (same dp) with the same result, so it is not only pixel count. The Pass 8 ANRs looked the same. Unknown whether a phone shows it. Worth a profile of what recomposes every second while a workout is open (timer tick reading state at the top of the screen, exercise photos).
Also: touching the app in its first minute after a cold start produced the same ANR, so every step in this pass waited about a minute after launch.
Screenshot: none (a killed app is not visible)

### Q9-5 · S4 · Train · Starting a workout while a reopened one is unfinished leaves a ghost entry
Status: open, not isolated.
Steps: 1. Finish a workout, Reopen it (it is the running workout again). 2. Leave the app (it was killed by the ANR in Q9-4). 3. Train > Start workout.
Expected: "Resume" or a question.
Actual: a new Upper B started, and the Training log then listed both: the new one and the reopened one as "Upper B, 0 min . 3 sets . 1,200 lb".
Screenshot: none (found in the log list only)

### Q9-6 · S4 · Drinks · "Almond milk, unsweetened" is listed twice under ON THIS PHONE
Status: open, may be the seed.
Steps: 1. Quick log > Drink (or Log food > "Drinks: almo"), type "almo".
Expected: one row.
Actual: two identical rows (15 kcal / 100 ml, "Check the label: milk"), then the USDA almond milks. My foods lists the same product once (1 cup (240 ml), 36 kcal), so the seed may hold both a drink and a food of that name.
Screenshot: none kept

### Logcat
Four FATAL EXCEPTIONs, all the Q9-1 `Segmented` measure crash (10:44, 10:46, 10:53, 10:55 device time, on the unpatched APK). None on the patched build. 15 ANRs (Q9-4), 0 after 13:50. The ANR counter was reset by the second seed run, so the totals above are from the log read at 13:51.

### Summary for Pass 9
Retests: 22 items. Verified 18 (Q7-4, 7-19, 7-23, 7-24, 7-25, 5a-9, 6-7/7-3, 8-1, 8-2, 8-3, 8-4, 8-5, 8-6, 8-7, 8-8, 8-9, 7-20, 6-5); Still failing 1 (Q8-10); Partly 2 (Q6-3, Scan barcode tile); Not reached 1 (the "Checking..." chip). New: 6 findings, S1 1 (Q9-1), S2 0, S3 1 (Q9-4), S4 4 (Q9-2, 3, 5, 6).
Not verifiable on an emulator: touch feel, haptics and the rest sound, keep-awake, real snackbar timing (Undo for Q8-1 and Q6-2 was not caught again), performance and ANR rates on a phone, gesture navigation, Health Connect, a real camera or barcode scan, a typed barcode lookup (offline), Android 15, a Monday-first locale (date pickers).
Device state left: clock restored (the device zone is still Europe/London), font scale 1.0, screen size reset, the sparse seed loaded.

### Fixes after Pass 9
Fixed in 266fa4b unless noted. Emulator smoke test (phone30, preview APK built from this commit): Food (Diary, Plan, Recipes, Library), Train, Quick log > Start workout, Settings > Appearance and units and Food at font scale 1.5 all opened; no FATAL and no ANR in logcat. Nothing here was seen on a phone.
- **Q9-1** Fixed. `Segmented` takes its height from `maxIntrinsicHeight` and measures each segment once. Smoke-tested on emulator. The other custom `Layout`s (Tour, WeeklyCheckIn) measure once. Needs retest on a phone.
- **Q9-4** Fixed (probable cause), needs retest on a phone. The one-second clock state was read at the top of `ActiveWorkoutScreen`, so the whole screen (list, thumbnails, set rows) recomposed every second; it is now read only inside the header. Emulator: ActiveWorkout about 15 to 30 percent of a core with a workout running (it was about 100 percent), Train and Today at 0 percent. Rest sheet open/close and "Still training?" > Keep going worked, no ANR (it stayed dismissed). Workout summary CPU was not re-measured.
- **Q8-10** Fixed, smoke-tested on emulator. Cause: the saved start was re-anchored with the app's hybrid clock, which never runs backwards, so the start never looked to be in the future. It now uses the wall clock, and the display counts from now at once. Clock set back 2 h: timer read 0:14 then 0:56 (counting).
- **Q9-2** Fixed. Name and muscle line keep whole words (two lines, ellipsis); the Form button is icon-only above 1.25x. Seen on emulator at 1.5x.
- **Q9-3** Fixed. "Logged ... to" messages use the slot's display name (`MealSlot.label`), except the watch replies.
- **Q9-5** Not reproduced. Start already resumes a running (reopened) workout. A finished workout can no longer be saved with an end before its start or last set (the likely "0 min" entry). Needs retest.

## Pass 10 — Regression on build 26112415
Date: 2026-11-24 (device zone Europe/London) · Build: release/intake-preview.apk (com.zeroindex.intake.preview, versionCode 26112415, repo head 1984f26) · AVD: phone30 (Android 11, no KVM, software rendering, 3-button navigation) · Seed: tools/qa/seed.sh via a temporary copy pointed at the preview package (not committed, deleted). The seed worked on a clean install at the default 1080x2280 screen ("Imported 570 changes"). **Emulator only**; nothing here was seen on a phone.
Screenshots: design/qa/pass10/ (about 90 PNGs, 3.2 MB, quantised, no personal photos). Final counts for the whole pass: **FATAL 0, ANR 0** (logcat crash buffer and "ANR in", read at the end; the device was never restarted, so the buffer covers the pass).

### Result per goal
| Goal | Result | Evidence |
| --- | --- | --- |
| 1 Crash sweep | **Pass** (0 FATAL). Layout findings Q10-1 to Q10-3 | Today, Food (Diary, Plan, Recipes, Library), Train (Workout, Exercises, Videos), Insights (1M, 3M, 6M, 1Y, All), You, Settings > Appearance and units (every segment: kg/lb, cm/in, ml/fl oz, g/oz, System/Light/Dark) at font 1.0 light, 1.0 dark, 1.5 dark, and Appearance at 1.5 light. food-1x, food-plan-1x, food-recipes-1x, food-library-1x, train-*, appear-light, appear-dark, d-*.png, f-*.png, appear-f15*.png. Settings > Training was also scrolled and its number field edited. The segmented control (Q9-1) no longer crashes anywhere |
| 2 Workout end to end | **Pass**, with notes | Started Upper B, logged 3 sets of Incline Dumbbell Press and 2 of Pull-Up. Rest bar: -15 s, +15 s and Skip worked; the rest sheet ("Rest 2:00" chip) opened and closed, its +15 s (2:00 to 2:15) and Done worked, Done fully visible above the nav bar (rest-sheet.png). Add set worked (set 5, 6 rows appear); **no Undo is offered for Add set on ActiveWorkout** (addset2.png: no snackbar; the way back is the Delete in the set dialog or leaving it empty). Un-ticking a logged set takes it out again (unlog.png, removed from the working-sets count). Pause froze the clock (13:19 on two dumps 20 s apart, paused.png) and Resume continued. Finish dialog: "You logged 5 sets" with Finish workout / Keep going / Discard. Summary: 15 min, 1,200 lb, 5 sets; the numbers add up (3 x 8 x 50 lb = 1,200; Pull-Up counts as bodyweight). Summary > Add a set > Add worked (4 x 8, volume to 1,600); **Delete this set shows "Set removed from this workout. Undo"** (summary-delete-then-undo.png) and Undo restored the set on the other try (3 sets back); the Add-a-set snackbar was not caught in time. "Change date, time and length": start 3:27 PM, length 20 saved ("The sets move with the start time"); Reopen (timer 20:18 at once, the 20 minutes plus the time open), one more set, Finish: summary kept the edited start (15:27) and shows 22 minutes (summary-after-reopen.png), a 5th working set |
| 2 CPU | see table below | |
| 2 / Q9-5 ghost entry | **Pass (not reproduced)** | After Reopen, Train shows "Your workout is running. Tap the bar above the menu to go back into it." and a Resume card; no second workout can be started (q95-top.png, q95-train.png). Training log: one Upper B for Nov 24 (22 min, 5 sets), no "0 min" row (log.png) |
| 3 "Still training?" | **Pass** | No sets: "No set has been logged in this workout for about 4 hours. Keep going, or discard it." with Discard / Keep going (still-nosets.png); Keep going dismissed it and it stayed dismissed. With a set: needed the idle threshold at 1 h and a re-entry (see note): "Nothing has been logged for about 2 hours. Your last set was at 21:10. Finish this workout at 21:10, or keep going." with Keep going / Finish at 21:10 (still-sets.png); Finish at 21:10 went to the summary (still-finished.png). **Clock set back 2 h during a workout: the timer kept counting** (0:09, then 0:39 30 s later; clockback.png) — Q8-10 stays fixed. Auto time was switched back on and the clock reset to the host time (17:21) afterwards; the threshold was restored to 3 h |
| 4 Food end to end | **Pass** | Search "salmon" > Log one serving: "Logged Salmon fillet, baked to Dinner" with Undo (food-search-log.png). Recipe serving from search: "Logged Salmon, sweet potato and broccoli to Dinner" with Undo (food-recipe-log.png). My foods > + on Chicken breast: "Logged Chicken breast, cooked to Dinner" with Undo (myfoods-logged.png). Meal template (Library > Meal templates > Post-workout shake > Snacks): "Logged Post-workout shake to Snacks. Almond milk, unsweetened: check the label for dairy" with Undo (template-snackbar.png); logging twice then Undo left one (Snacks 338 kcal = one shake, diary-meals.png). Water: + adds a drink (54.4 to 71.3 fl oz); + has no Undo by design, - shows "Removed water" with Undo and Undo restored it (water-minus.png, water-undo.png; note the message does not say how much). Swap: Recipe (Baked mac and cheese) > Log a serving > Lunch > the "This contains an ingredient you avoid" screen > Find a safer swap > "Swap lunch / Nothing planned for lunch" > Choose Chicken fajita bowls: **"Logged Chicken fajita bowls to Lunch"** with Undo (swap.png, swap-logged.png). Q9-3 fixed. Cook mode: nav bar black, text readable (cook.png). Diary arithmetic: Dinner 1,137 kcal = 2 x 309 + 519 (I logged the salmon twice by tapping twice) |
| 5 Pass 9 "Verified" spot-check | **No regression** | Rest sheet Done visible (rest-sheet.png). Today water card "Base 103.1 fl oz + workout +x" with a caret (today-water*.png). Library > My foods is titled "My foods" (myfoods.png). Food segmented control at 1.5x (f-diary.png to f-library.png). Recipe page has an "Ingredients" heading beside the wrapped allergen chip, padded on all sides (swap-logged.png, mac.png). The "Contains ..." chips wrap in Recipes and Swap |
| 6 Contextual | 8 findings below | |

### CPU (top -b -d 5 -n 2, process com.zeroindex.intake.preview, percent of one core; first sample of each pair includes the screen change)
| Screen | Samples | Note |
| --- | --- | --- |
| ActiveWorkout, workout running, one set logged | 26.9, 13.0 | Pass 9 fix holds: was about 100 percent |
| Workout summary (idle) | 0.0, 0.0 | |
| Today with a running workout (mini bar timer ticking) | 114 (opening), 18.0; then 11.9, 15.0, 16.6 | Today without a running workout was not sampled this pass |
No ANR on any of them, including every dialog and sheet on ActiveWorkout and the summary (Q9-4 not seen again).

Notes on "Still training?": its idle clock restarts on Keep going and lives in the screen (`remember`), so after Keep going the next question comes only after a full threshold or after leaving and re-entering the screen; I tested the with-sets variant by lowering the threshold to 1 h, re-entering and moving the clock to 23:15. The stored time of a set logged after a manual clock change comes from the app's never-backwards clock, so after my 2 h rewind the set was stamped 21:10 (not 17:20) and the finished workout read 360 minutes (start 15:10, end 21:10). That is a result of my clock changes and not a finding, but it does inflate the water target (+67.6 fl oz, today-water2.png) until that session is edited or deleted.

### New findings
### Q10-1 · S3 · Food Diary · The macro tiles cut off the "/target g" text
Status: open. Steps: Food > Diary. Expected: "165 /245 g" whole. Actual: at 1.0x the Carbs tile's "/245 g" loses the "g" at the card edge (food-1x.png; also dark, d-diary.png); at 1.5x Carbs reads "165 /24" and Fat "34 /70 g" is cut (f-diary.png), the ring's "kcal left" also touches the ring. The tile is too narrow for the number plus target.
Screenshot: design/qa/pass10/food-1x.png, f-diary.png
### Q10-2 · S3 · Font scale 1.5 · Settings > Appearance and units, the Theme row label breaks inside words
Status: open. Steps: font_scale 1.5, Settings > Appearance and units, look at Theme. Actual: the label column is about one letter wide: "The / me" and "Syste / m follow" with the three-way control taking the width (appear-f15.png, appear-f15-light.png). The other rows (Weight, Length, Drinks, Food portions) are fine. A Segmented that has three long options should drop under the label. Same family as Q9-2.
### Q10-3 · S4 · Font scale 1.5 · Insights "Log weight" tile breaks "measurements" into "measurement / s"
Status: open. Also "Training stats" wraps to two lines (f-ins.png). The You card at 1.5x wraps "170 . 70 . 245 g" and "4 days . 60 min" awkwardly (you-f15.png).
### Q10-4 · S4 · Workout summary, "Still training?" · 24-hour times on a 12-hour phone
Status: open. The summary header reads "Tuesday 24 November, 16:27", the dialog "Your last set was at 21:10" and "Finish at 21:10", while the phone clock, the date picker's start time ("04:27 PM") and the rest of the app are 12-hour. Use the phone's setting (Pass 9 showed "11:00" in the same place, so it has always been 24-hour).
### Q10-5 · S4 · Today water · The target while a reopened workout runs counts from the edited start, not the timer
Status: open. Steps: finish a workout, edit its start to 1.5 h ago (length 20 min), Reopen. The workout timer reads 26 min, but the Today water card reads "Base 103.1 fl oz + workout +44" (147.1 target); after Finish the same workout gives +12 (target 115, 28 minutes). 44 fl oz matches about 97 minutes at 0.8 L/h, so the running figure uses the wall clock since the start. Expected: the length the timer shows.
Screenshot: design/qa/pass10/today-running.png (water dump in the log), today-water*.png after
### Q10-6 · S4 · Theme Dark while the phone is in light mode · Status bar and nav bar
Status: open, check on a device. With Theme set to Dark in the app and the phone in light mode (3-button nav), the status-bar clock and icons are near invisible (dark on dark) and the system navigation bar is light grey under a near-black app (appear-dark.png, d-today.png, f-*.png). Cook mode's bar is black (Q8-2 holds), so only the normal screens are affected.
### Q10-7 · S4 · Copy and context nits (no functional effect)
- Allergen screen when logging a recipe serving says "The food cannot be saved until you choose what to do" and "SAVE BLOCKED" (swipe-dialog.png). The action is logging, not saving.
- USDA rows say "100 g (100 g)" for the portion (search-yogurt2.png).
- Food Diary macro tiles show 272 + 306 + 659 = 1,237 kcal against "1,222 eaten": the tiles use 4/9/4 on the grams, the total uses the foods' own kcal (a 1 percent gap, food-1x.png).
- Saved recipes (Salmon, sweet potato and broccoli, Grandma's vegetable soup, Chicken fajita bowls) show "Only the name was checked" although they list ingredients (recipes-list.png). May be the seed's ingredient data; if the recipes have ingredient lines it should say the ingredients were checked.
- "Removed water" does not say how much (water-minus.png).
- Cook mode: the servings stepper sits tight under the "INGREDIENTS . 0 OF 4 READY" label (cook.png).
- Adding a 4th identical set on the summary made a "PR" chip appear beside Set 2 and Set 3 area (summary-addset-undo.png was taken before; the chip is in the later view); I could not tell which set it belongs to or why a repeated 50 x 8 is a record.
### Q10-8 · S4 · ActiveWorkout · Add set has no Undo
Status: open, may be by design. Add set on the summary has the Undo path (a soft delete, not caught on screen in time); on ActiveWorkout the new empty row has no message (addset2.png). Un-ticking and Delete this set work.

### Summary for Pass 10
Retests: Q9-1, Q9-2, Q9-3, Q9-4, Q9-5 and Q8-10 all hold on the emulator. FATAL 0, ANR 0. New: Q10-1 and Q10-2 (S3), Q10-3 to Q10-8 (S4); S1 0, S2 0. 
Not verifiable on an emulator: touch feel, haptics, the rest sound, keep-awake, real snackbar timing (I caught Undo only by tapping within about a second), CPU and ANR rates on a phone (the emulator is software rendered), gesture navigation, Health Connect, a camera or barcode scan, network lookups (offline), Android 15, a 24-hour phone setting, a Monday-first locale.
Device state left: emulator killed; auto time on and the clock reset to the host time; font scale 1.0; theme Light; "Still training?" threshold back to 3 h; seed loaded (plus today's test entries).

### Fixes after Pass 10
Fixed in 16ab543 unless noted. Emulator smoke test (phone30, preview APK versionCode 26112418 built from this commit, seed loaded): Today, Food (Diary, Plan, Recipes, Library), Train (Workout, Exercises, Videos), Insights (1Y), You and Settings > Appearance and units opened at font 1.0 and 1.5; Theme Dark on a light phone; a workout with Add set + Undo and Finish. FATAL 0, ANR 0. Nothing here was seen on a phone.
- **Q10-1** Fixed, smoke-tested on emulator (1.0 and 1.5). The tile shows "77 g" and "of 170 g" on two lines, so no digit is cut. The ring's "kcal left" still touches the ring at 1.5x (not changed). Needs retest on a phone.
- **Q10-2** Fixed, smoke-tested at 1.5. `SettingRow` takes `stackOnLargeFont`: above 1.15x the unit and Theme controls go under their label, so no word breaks.
- **Q10-3** Fixed, smoke-tested at 1.5. Insights tiles ("Training stats", "Log weight") stack above 1.15x; the You plan figures stack one per row.
- **Q10-4** Fixed, smoke-tested: the summary header read "Tuesday 24 November, 6:55 PM" on the 12-hour phone. "Still training?" uses the same `ClockLabel` (not seen on emulator this time). Needs retest on a 24-hour phone.
- **Q10-5** Fixed (domain test `WorkoutClock.loadMs`), not re-reproduced on emulator. The water sweat bonus for a running workout uses the trained time the timer shows (no reopen gap, no pauses), and re-runs on pause, resume and reopen. Needs retest.
- **Q10-6** Fixed, smoke-tested: Theme Dark on a light phone shows light status bar icons and a dark navigation bar (`enableEdgeToEdge` styles follow the app's resolved theme). Needs a check on a phone and with gesture navigation.
- **Q10-7** Fixed, in parts. (a) The allergen sheet says "Logging blocked", "not logged" and "Nothing is logged until you choose what to do"; the block itself is unchanged. (b) "100 g (100 g)" now reads "100 g". (c) The macro tiles' kcal are the 4/4/9 figures scaled to add up to the eaten total (tiles 298 + 442 + 765 = 1,505 eaten, smoke-tested). (d) Saved recipes with ingredient lines now say "Ingredient names checked" (seen in Recipes); the scan does read every line's words, so "Only the name was checked" was untrue for them, but they are still never green unless every line has a label behind it, and a warning is never downgraded; a recipe with no lines still says "Only the name was checked". (e) "Removed water" says the amount ("Removed 8 fl oz water"), not seen on emulator. (f) Cook mode: space under the ingredients label, not seen on emulator. (g) Identical 4th set: a set equal to an earlier one earns no estimated max, heaviest or rep record (tests). What remains is the session-volume record (more sets than the best day), which the chip now calls "Volume PR"; needs an owner decision on whether that should count at all.
- **Q10-8** Fixed, smoke-tested: Add set on ActiveWorkout posts "Set added to this exercise. Undo"; Undo took the new row out (rows 6, then 6 again), and it never hides a set that has been logged.

## Pass 11 — Retest of Pass 10 fixes
Date: 2026-11-24/25 (device zone Europe/London) · Build: release/intake-preview.apk (com.zeroindex.intake.preview, versionCode 26112418, repo head 740c01e) · AVD: phone30 (Android 11, no KVM, 3-button navigation) · Seed: tools/qa/seed.sh through a temporary copy pointed at the preview package (not committed), clean install, default 1080x2280, "Import 573 changes". The worker restarted mid-pass; the emulator was restarted and the clock set back to the seeded day (Nov 24, 14:00) so Today kept its data. **Emulator only**; nothing here was seen on a phone.
Screenshots: design/qa/pass11/ (small PNGs, no personal photos). Counts: **FATAL 0. App ANR 1** (Q11-1, fast typed text in Log food search, see below; not reproduced typing slowly). The only other "ANR in" lines are system processes (systemui, phone) during the emulator boot after the restart.

### Result per Pass 10 fix
| Item | Result | Evidence |
| --- | --- | --- |
| Q10-5 water target, reopened workout | **Verified** | Edited start to 6:05 PM (about 96 min before), length 20, Reopen. Timer 20:14 on the workout, Today water card "Base 103.1 fl oz + workout +10.1" (target 113.3) with the timer at 21:25. After Finish (23 min) the same +10.1. Pass 10 showed +44 for the same setup. reopened.png, water-reopened.png |
| Q10-4 12-hour time in "Still training?" | **Verified** | "Nothing has been logged for about 1 hour. Your last set was at 7:54 PM" with "Finish at 7:54 PM" (still.png); summary header "Tuesday 24 November, 7:53 PM". Threshold was lowered to 1 h and the clock moved forward, then both restored |
| Q10-7e "Removed N fl oz water" | **Verified** | "Removed 16.9 fl oz water" with Undo (water-removed.png). The 12 fl oz removal snackbar was missed, the total fell 54.4 to 42.4 |
| Q10-7f Cook mode spacing | **Verified** | Gap between the "INGREDIENTS · 0 OF 4 READY" label and the servings stepper (cook.png) |
| Q10-6 dark app on light phone | **Verified on emulator** | Theme Dark: light status bar icons, dark nav bar on Today, Food, Train, Insights, Appearance (appear-dark.png, dk-*.png). Not checked with gesture navigation or on a phone |
| Q10-1 macro tiles | **Verified** | "115 g / of 170 g" two lines, no cut digit at 1.0 (food1.png) and 1.5 (f-diary.png) |
| Q10-1 ring "kcal left" at 1.5 | **Still failing (known open)** | Diary ring: "kcal left" runs over the ring stroke (f-diary.png). Today ring at 1.5 is fine (f-today.png) |
| Q10-2 Theme row at 1.5 | **Verified** | Unit and Theme controls sit under their labels, no broken words, all segments tapped, no crash (f-appear.png, f-appear-light.png) |
| Q10-3 Insights tiles and You card at 1.5 | **Verified** | Tiles stack and read whole; You plan figures one per row, first row "36 · 72.0 in · 192.0 lb · goal 185.0 lb" wraps cleanly (f-ins.png, f-you.png) |
| Q10-8 Add set Undo on ActiveWorkout | **Verified** | "Set added to this exercise. Undo" (aw-addset.png); Undo took the row out (6 rows, set count 18 unchanged). The earlier taps missed the snackbar, so it needs a tap inside about 2 s on this emulator |
| Tiles sum to "eaten" | **Verified** | 448 + 676 + 900 = 2,024 (food1.png) |
| USDA "100 g" rows | **Verified** | "Yogurt, plain, nonfat · 100 g · Dairy and Egg Products" (usda.png, usda2.png) |
| "Ingredient names checked" on saved recipes | **Verified** | Amber, not green, on Salmon, Soup, and others; Turkey chili shows the red "Contains cream, milk and yogurt" (recipes2.png) |
| "Volume PR" chip | **Not reached / Partly** | Seeded summaries show a plain "PR" chip, not "Volume PR", always on Set 2 (lowerA3.png, lowerA4.png); see Q11-3. I could not make a session that beats the best day's volume |
| Q10-7a allergen sheet wording, Q10-7g repeat-set records | **Not reached** | Not exercised this pass |

### Crash sweep
No FATAL. Today, Food (Diary, Plan, Recipes, Library), Train (Workout, Exercises, Videos), Insights (1M, 3M, 6M, 1Y, All), You, Settings > Appearance and units (kg/lb, cm/in, ml/fl oz, g/oz, System/Light/Dark) at 1.0 light, 1.0 dark and 1.5 dark; Appearance again at 1.5 light. Workout start, set, Add set + Undo, pause none, Finish, edit date/time/length, Reopen, Finish, "Still training?" all ran. Units were put back to lb, in, fl oz, g.

### New findings
### Q11-1 · S4 (maybe emulator) · Log food search · Fast typing causes an ANR
Steps: Today > Log food > Search, `adb shell input text yogurt` (six keys at once). The app ran at about 165 percent CPU for 28 s and "Input dispatching timed out" (KeyEvent 'o') followed. Typing the same word one key every 4 s gave no ANR and results. Probably the emulator's speed, but search work per keystroke looks heavy; check on a phone and consider debouncing.
### Q11-2 · S4 · Food Diary ring at 1.5x · "kcal left" touches the ring
Same as the known open item (f-diary.png).
### Q11-3 · S4 · Workout summary · "PR" chip sits on Set 2, label is not "Volume PR"
Steps: Train > Recent sessions > Lower A (Nov 19) > Exercises. Back squat: Set 1 160 lb x 7 is the heaviest weight and best estimated max, yet the "PR" chip is on Set 2 (160 x 6). Walking Lunge and Dumbbell Lateral Raise also show it on Set 2. A reader cannot tell what the chip means (lowerA3.png).
### Q11-4 · S4 · Weight rate wording does not agree across screens
Today: "-1.9 lb this week". Insights 1M: "-0.3 lb/week" while the chart falls about 3 lb over the month; Averages: "-0.3 lb avg weight change" with no period (ins-b1.png, ins-b5.png). Different bases may be intended, but the labels should say which.
### Q11-5 · S4 · Insights measurements · Change chips have no unit
"Waist 36.3 in  -0.39", "Chest 41.7 in  +-0" (ins-b4.png). Add "in"; "+-0" for no change reads oddly.
### Q11-6 · S4 · Insights Maintenance · "Needs 14 days of weigh-ins and logs" with 21 days logged
The card shows a dash and the message while Days logged says 21 of 28 and 17 weigh-ins exist (ins-b5.png). If the rule is 14 consecutive days, say so.
### Q11-7 · S4 · Duration of the same workout differs
Summary after the second Finish read "23 minutes", Train > Recent sessions "22 min" for the same session; a 1-minute session reads "0 min" / "0 minutes" (trainlog.png). Pick one rounding.
### Q11-8 · S4 · You card at 1.0 · "4 days · 60 min" wraps ("60 / min")
you1.png. Also "1.9 g per kg" for 170 g at 87.1 kg (1.95) rounds down, and Today lists macros Protein/Carbs/Fat while the Diary tiles are Protein/Fat/Carbs.

### Summary for Pass 11
Verified: Q10-1 (tiles), Q10-2, Q10-3, Q10-4, Q10-5, Q10-6 (emulator), Q10-7 b/c/d/e/f, Q10-8. Still failing: ring "kcal left" at 1.5x on the Diary. Not reached: Q10-7a, Q10-7g, "Volume PR" label. New: Q11-1 to Q11-8, all S4; S1-S3 none.
Not verifiable on an emulator: touch feel, haptics, real snackbar timing, CPU and ANR rates on a phone (Q11-1), gesture navigation, a 24-hour phone setting, Health Connect, camera and barcode, network lookups, Android 15.
Device state left: auto time on, zone Europe/London, font 1.0, theme Light, "Still training?" 3 h, units lb/in/fl oz/g, emulator stopped.

## Pass 12 — Video player and transcripts (build 26112501)
Date: 2026-11-24/25 (device zone Europe/London, clock set to Nov 24 14:00, auto time restored) · Build: app-preview.apk (com.zeroindex.intake.preview, versionCode 26112501, schema v7, repo head 85f1877) · AVD: phone30 (Android 11, no KVM, software rendering, WebView 83) · **Emulator only**; nothing here was seen on a phone.
Screenshots: design/qa/pass12/ (58 small PNGs, 2.8 MB, no personal photos). Counts: **FATAL 0 in the app. App ANR 3** (Q12-2 twice, once cold start with queued taps). System-process FATALs and a system_server restart appeared only during the 189 MB APK install (emulator memory).

### What needed network or keys
The emulator has no network of its own. I pointed it at the container's egress proxy (`settings put global http_proxy 10.0.2.2:40231`, removed afterwards), which let the app's own HTTPS reach YouTube (oEmbed worked, the title and thumbnail loaded). There is **no YouTube API key**, so search, Recommended and chapters from the API were not reachable. yt-dlp (Python) ignores the Android proxy, so **real caption fetching was not possible**. YouTube's embedded player did not start in this WebView (see Q12-1/video table). To still exercise the Intake controls I made a test video: a 2-minute H.264 test pattern pushed into the app's `files/video_downloads` plus matching `downloaded_video` and `video_cache` rows (description with 5 chapters) written with sqlite into the app database. That plays through **Media3**, the same Intake chrome as the YouTube backend, not through YouTube.

### Result per item
| Item | Result | Evidence |
| --- | --- | --- |
| 1 Upgrade 26112418 to 26112501 over the seeded data | **Verified** | `pm install -r` over the seeded install: opens on Today with all data (236 kcal left, 21 days, program, week strip), database `user_version` 7, `downloaded_video` present, no app FATAL. upg-today.png. (The install itself needed a push and `pm install` because `adb install` of the 189 MB APK killed the emulator's system_server twice; emulator, not the app) |
| 2 Video page, thumbnail then play | **Verified (Media3), Failing/Not reached (YouTube)** | Pasted link: oEmbed title and thumbnail, page with Save, Share, Open in YouTube (vp2.png). Downloaded test video: poster with play button and duration chip, tap play and the Intake controls appear (d2.png) |
| 2 YouTube embed plays | **Not reached** | The IFrame page loaded, but YouTube's `youtubei/v1/player` call returned HTTP 400 inside WebView 83 and the player never reported ready. The page shows a black box (pl3.png, via DevTools). Unknown whether a current WebView on a phone behaves the same |
| 2 12 s fallback to YouTube's own controls | **Verified (it switches), Failing (result)** | After about 12 s the controller loaded the plain embed (DevTools: iframe `youtube-nocookie.com/embed/...`), but it stayed black. In fallback mode Intake's chrome is not drawn at all, so no "YouTube" badge, no error, no Try again (Q12-3) |
| 2 Controls: play/pause, replay at end, speed chip, repeat chip, fullscreen button, chapter name label, ticks, elapsed/remaining | **Partly** | All draw (d2.png, end1.png: replay icon and lime controls at the end, ticks on the scrubber, chapter name top-left). But see Q12-1: the scrubber row covers the whole video, so play/pause, back/forward 10 s, speed and repeat cannot be tapped |
| 2 Double-tap seek, scrubber drag | **Not reached** | Taps on the video seek to the tapped x position instead (Q12-1); not tested separately |
| 2 Auto-hide after 3 s | **Verified** | Controls gone about 3 s after the last tap while playing (d4 not kept, c3.png); they stayed up at the end of the video (end1.png) |
| 2 Chapter tap seeks without reload, shows "Playing" | **Verified** | Tapped Push-ups (1:00): "Playing" moved to Push-ups, video continued from there with no reload (ch2.png) |
| 2 Fullscreen landscape and back | **Failing (emulator ANR)** | Tapping Fullscreen while the video plays: the activity rotates and the app hits "Input dispatching timed out" and is killed, 2 of 2 tries (Q12-2). Not seen working |
| 2 Error state offline | **Verified** | Proxy off, saved video, tap play: "Could not reach YouTube. Check your connection." with **Try again** and **Open in YouTube** (err1.png). Try again returns to the same state, no hang. The "YouTube" badge there is dark grey on black, barely visible (Q12-4) |
| 2 Shorts player | **Not reached** | No API key, so no Shorts to open; a pasted link opens the long-form page |
| 2 CPU (top, one core) | see below | |
| 3 Transcript card, yt-dlp OFF | **Verified** | "Turn on direct playback in Settings to fetch transcripts automatically. Until then, paste the transcript below." with "Open Settings > Videos" (mw1.png) |
| 3 Paste path and drafted rows | **Verified, one nit** | Pasted four timed lines ("0:12 do 3 sets of 12 squats", "1:05 lunges 10 reps each side", ...) and Read this text: "Using the transcript you pasted (5 lines with times)" (I had a stray fifth line), 3 rows; Bodyweight squat 3 x 12 "At 0:12 in the video"; Lunges "At 1:05 in the video · Each side" as a new exercise; Plank "At 3:30" (dr4.png, dr3.png). See Q12-5 for the Plank row |
| 3 Save and the ▶ chip | **Verified** | Save workout, then Programs > My workouts shows it (3 exercises); Edit exercises shows chips "▶ 0:12", "▶ 1:05", "▶ 3:30" (ed1.png); "Watch the video" in the menu. Tapping ▶ 1:05 opens the video page and the player starts at 1:05 with Push-ups as the chapter (chip2.png; shown on the downloaded Media3 video after I relinked the workout to it, because the YouTube player does not run here) |
| 3 yt-dlp on, Get transcript on a real video | **Not reached** | yt-dlp cannot use the proxy. With the option on and no route: the video page spins about 3.5 minutes while yt-dlp starts (Q12-6), then "Could not get this video directly. YouTube may have changed..." ; Get transcript ends with "Could not get the captions. YouTube may have changed. Try again later, or paste the transcript..." (tr-result.png). Real captions never fetched |
| 4 Settings yt-dlp dialog wording | **Verified** | "It does not use the YouTube key you added; that key is only for search and video details." No sentence about the key being suspended (ytdlp-dialog.png). Opt-in turned off again afterwards |
| 5 Crash sweep 1.0 and 1.5 | **Pass (0 FATAL)** | Today, Food (Diary, Plan, Recipes, Library), Train (Workout, Exercises, Videos), Insights (1M, All), You, Settings > Appearance and units (kg/lb, System/Dark/Light) at font 1.0 (sw10-*.png) and 1.5 (sw15-*.png). Reduced ranges: not every unit segment or Insights range this pass. 1.5 Diary ring "kcal left" still touches the ring, as before |

### CPU (top -b -d 5 -n 2, app process, percent of one core)
| State | Samples | Note |
| --- | --- | --- |
| Video page, Media3 test video playing | 113, 98; later 238, 127 | Software H.264 decode and software GL on the emulator; not a phone number |
| Video page, video ended/paused | 0.0, 1.8 | Ticker is quiet when nothing plays |
| YouTube fallback embed (black, WebView) | app 1.6, 1.6; WebView renderer about 8 | |
Paused-while-visible was reached only through the ended state: I could not tap pause (Q12-1).

### New findings
### Q12-1 · S2 · Video player · The scrubber row fills the whole video, so most controls cannot be tapped
Steps: play any video (Media3 test video), let controls show, tap the centre play/pause, the back/forward 10 s buttons, the speed "1x" chip or the repeat chip. Actual: the seek bar is drawn at the vertical middle of the video, through the centre buttons (d2.png, end1.png, speed.png), and a tap anywhere in its horizontal span seeks to that position instead of pressing the button (centre button tap at x=540 moved 1:35 to about 1:15; tapping the speed chip at x=695 jumped to about 1:30 and the speed stayed 1x, speed.png). Only Fullscreen (outside the bar's width) responds. The accessibility tree lists "Seek bar" at the middle (y 571 of a 284 to 872 player). Likely cause: `Scrubber` is a Box with `heightIn(min = 48.dp)` and a `Canvas(Modifier.fillMaxSize())`, inside a Row inside a bottom-aligned Column, so it takes the player's whole height (PlayerChrome.kt about lines 287 to 296, 424 to 470). Fix: a fixed height (`height(48.dp)` or `wrapContentHeight`) and draw the track at its centre. Same code path for YouTube and Media3.
Screenshot: design/qa/pass12/d2.png, end1.png, speed.png
### Q12-2 · S3 (maybe emulator) · Fullscreen · ANR on entering fullscreen while a video plays
Steps: play the Media3 video, tap Fullscreen. Actual: the system rotates, the app stops responding and is killed ("Input dispatching timed out ... Waited 5003ms for FocusEvent", twice in two tries, 14:37 and 14:46). ANR trace: main thread Runnable, rebuilding `IntakeNavHost` (reflection-based serializer lookup in `NavDestinationBuilder`) after the rotation recreated the activity, while video decoding used the other CPU. Probably the emulator's speed, but every fullscreen entry recreates the whole nav graph; check on a phone and consider handling orientation changes without recreation. The third ANR (15:15) was a cold start with queued taps (emulator).
### Q12-3 · S3 · YouTube player · Fallback embed shows no chrome, badge or error when the frame is dead
In fallback mode `PlayerContent` skips `PlayerChrome`, which also hides the "YouTube" badge VIDEOS.md says is always on screen. If the frame never draws (as here), the person sees a plain black rectangle with no message, no Try again, no Open in YouTube (pl3.png). Keep the badge and an error or a way out after the fallback also fails.
### Q12-4 · S4 · Player error state · The "YouTube" badge is nearly invisible
Dark grey text on the black error panel (err1.png). Should be white like in the controls.
### Q12-5 · S4 · Make this a workout · Pasted transcript rows share one "From:" text and AMRAP leaks to the next row
With "2:15 pushups AMRAP" (a name the library did not match) followed by "3:30 plank hold for 45 secs", the Plank row is "At 3:30 in the video · As many reps as you can" with reps 1, so the AMRAP and a missing "45 secs" (only "seconds" may be read) come from neighbouring text. Every row's "From:" quote is the same run of lines, not the line it came from (dr3.png, dr4.png). The card also says "Read from the video's captions" for pasted text. My fixture used "pushups" without a hyphen, so retest with "push-ups".
### Q12-6 · S3 · Video page with yt-dlp on · A bare spinner for minutes while offline
With Offline downloads on and no route, opening a video shows only a spinner (no title, chapters, Save) for about 3.5 minutes (emulator, Python start-up and retries) before the page appears with "Could not get this video directly". The page should show what it has at once and resolve the stream in the background. The messages for an offline phone also say "YouTube may have changed" rather than "no connection".
### Q12-7 · S4 · Chapter list marks "Playing" before play
The first chapter shows "Playing" on a video that has not started (d1 view). Minor.

### Summary for Pass 12
Verified: upgrade path 26112418 to 26112501 (data intact, schema v7), error state with Try again/Open in YouTube, chapter tap seek with "Playing", auto-hide, transcript card with the option off, paste path with times, Each side and AMRAP rows, Save, the "▶ m:ss" chips opening the video at that second (Media3), the yt-dlp dialog wording, sweep at 1.0 and 1.5 with 0 FATAL. Failing: Q12-1 (S2), Q12-3 (S3). Open: Q12-2 (S3, maybe emulator), Q12-6 (S3), Q12-4, Q12-5, Q12-7 (S4). Not reached: YouTube embed playback and the IFrame controller path, Shorts player, real caption fetch, double-tap seek, scrubber drag, pause and speed menu (Q12-1), fullscreen round trip.
Not verifiable on an emulator: touch feel, real playback performance, CPU on a phone, the YouTube player in a current WebView, gesture navigation, ANR rates on a phone.
Device state left: auto time on, font 1.0, theme Light, proxy setting removed, opt-in off, emulator stopped. Test rows left in the seeded data (saved video "QA12 Squat and lunge follow-along", downloaded test video, workout "QA12 Squat and lunge follow-along"); re-seed with tools/qa/seed.sh before the next pass.

### Fixes after Pass 12
Branch agent/fix-pass12. Needs retest on the next build. Merged first from agent/fix-pass11 (**Q11-1 to Q11-8, needs retest**): Log food search settles on one trimmed query (250 ms per source, reference database skipped under 2 letters); Diary ring text capped at 1.1x font scale; set-level PR chips on the set that set the record and the session volume chip only on the exercise; one weekly weight rate (4-week trend slope) on Today, Insights and Averages with the period named; measurement change chips with a unit and a proper zero; maintenance card says how many days have a weigh-in, a log, and both; one workout duration rounding with "<1 min"; You card grams per kg from target and weight, plan figures on one line.

| ID | Cause | Change | Test |
| --- | --- | --- | --- |
| Q12-1 | The scrubber's Canvas filled the whole parent, so its tap/drag handlers covered the centre buttons and chips | Scrubber is a fixed 48 dp strip (24 dp in Shorts); touch input is limited to it; the double-tap layer stays underneath the buttons | Seen on the emulator: seek bar bounds are 48 dp high; play/pause, +-10 s, speed menu, repeat chapter and scrubber drag each do their own job. Double-tap could not be sent through adb in time, so not seen |
| Q12-2 | Rotation recreated the activity and rebuilt the nav host; also the fullscreen dialog set its window insets in a SideEffect that ran after every recomposition | MainActivity declares `configChanges` (orientation, screenSize, screenLayout, smallestScreenSize, keyboardHidden) so fullscreen is an in-process rotation and the player keeps its position; the dialog window is set up once | Seen: no activity recreation, same process, playback continued from the same position in fullscreen. **Not fixed on the emulator**: the app still got "Input dispatching timed out" 10 to 20 s after entering fullscreen (3 of 3 tries, including once paused); the main thread was in relayout/frame work. Unknown whether a phone shows it |
| Q12-3 | The fallback embed skipped all chrome | Fallback keeps the YouTube badge and an "Open in YouTube" button; an error from the embed (connection error, HTTP error on the embed document, a player error code posted by the frame) opens the full error panel with Try again; 15 s with no sign of life shows a note | Embed page test (YoutubeEmbedTest). On the emulator with the proxy the embed reported "the owner does not allow it" and the panel showed the badge, message and Open in YouTube. The 15 s note was not reached |
| Q12-4 | The badge was drawn under the dark error panel | Badge drawn above the panel, white | Seen on the emulator |
| Q12-5 | Rows quoted a window across lines; a modifier took the nearest mention in any line; "push-ups" spellings did not match common names | Each row quotes only its own lines; numbers and modifiers attach to the exercise in their own line or an adjacent one, "AMRAP" and "each side" only within their line (or as a whole-line lead-in); push-up/pull-up/sit-up/chin-up spellings fold to one form; pasted transcript labelled as such | TranscriptDrafterTest (QA fixture with four spellings, leak, lead-in, two-lines-away) |
| Q12-6 | Resolving the yt-dlp stream blocked the whole page, had no deadline and blamed YouTube for every failure | Page shows its details at once and a "Getting the video" picture; stream resolve has a 20 s deadline and falls back to the YouTube embed; no network, timeout and extraction failures have their own words; offline is detected before starting yt-dlp | DirectFailureTest. Not seen on the emulator |
| Q12-7 | The chapter list marked chapter 1 before the video had started | Marked only once started | PlayerLogicTest; seen on the emulator (no "Playing" before start) |

Smoke test on the emulator (phone30, Android 11, software rendering) with the downloaded 2-minute test video: no app FATAL; app ANR 3, all on entering fullscreen (see Q12-2). Not verified: the YouTube player in a current phone WebView, real caption fetching, Shorts, double-tap seek.

### Q12-2 follow-up: fullscreen without a Dialog (branch agent/fix-fullscreen, build 26112507)
**Root cause.** Not a recomposition loop. On the emulator the ANR trace reason is `Waited 5006ms for FocusEvent(hasFocus=false)`: fullscreen was a Compose `Dialog`, a second window. Adding it took window focus from the activity, and the activity had to acknowledge that focus change from a main-thread frame that was already very slow (the same frame did the rotation relayout of the whole page, the Dialog window setup with a class-verification pause, a second `PlayerView` build, and the inset dispatch to the new window). Evidence from the trace and log: `Davey! duration=6002ms` then `20752ms`, `Skipped 857 frames`, `Screen frozen for +4s462ms`, `Long monitor contention ... WindowManagerService.addWindow`, a blocking GC (4.5 s), and main-thread samples taken every 3 s showing different work each time (PlayerView constructor inflating Media3's whole control layout at `IntakeVideoPlayer.kt` PlayerSurface, inset dispatch, text measure, draw) and an idle looper at the end, so no state was being written every frame. Paused video hit it too because none of that work depends on playback.
**Change.** Fullscreen is drawn by `FullscreenLayer` (new `PlayerFullscreen.kt`), a layer at the root of `IntakeApp` in the activity's own window: no Dialog, no second window, no focus change. `IntakeVideoPlayer` registers a `FullscreenEntry` with the app-wide `FullscreenHost` while fullscreen. Each controller owns one video view (`Media3PlayerController.playerView`, built once, lazy; the YouTube controller already owned its WebView); the inline slot and the fullscreen slot only hold it, so entering and leaving re-parents one view and inflates nothing. The controller, its position and its playing state never change. System bars are hidden and the orientation is set to sensor landscape once when the layer appears; both are put back once when it goes (the orientation it had before, not a fixed value). Back leaves fullscreen (the layer's `BackHandler` is registered after the NavHost's). Leaving the page while fullscreen ends fullscreen. Touches do not reach the page under the layer. The controls are padded clear of a camera cutout. `FullscreenHostTest` covers enter/leave and the stale-leave rule.
**Verified (emulator phone30, Android 11, software rendering, preview build 26112507).** Enter fullscreen from the inline button, 10 times in all (playing 4: three by the exit button and one by Back; paused 3 or more, by Back and by the exit button; the rest in an ended or uncertain state), 55 to 90 s in fullscreen each: **app ANR 0, app FATAL 0** (the only FATAL lines in the logs were the uiautomator dump tool crashing on a busy UI, not the app). Landscape and no system bars in fullscreen, portrait and bars back after exit, same process and same position (a 0:05 start played to 0:39 across a Back round trip). Controls used in fullscreen: back 10, forward 10, play/pause, replay, show/hide by tap. Inline after the round trips: fullscreen button, play/pause/replay, back/forward 10, speed menu opens, scrubber tap seeks. CPU in fullscreen (`top`, 5 s samples): about 90 to 240% of 400 while playing (software video drawing at 2280x1080 on an emulator), 1 to 3% paused or ended. Before the change the app was killed on the first entry.
**Not verified.** A phone (the emulator is slow and software rendered; whether a phone ever showed the ANR is unknown). The YouTube (WebView) backend in fullscreen. Camera-cutout padding. Leaving the page by navigation while fullscreen (written, not exercised; Back from fullscreen was). TalkBack: the page under the layer is still in the accessibility tree.
