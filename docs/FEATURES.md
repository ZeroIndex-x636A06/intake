# Intake: feature inventory by screen

For the mockup redesign. Built from the code on branch `claude/optimistic-noether-fullk5`, not from the design docs:
the route table (`ui/navigation/Destinations.kt`, `IntakeNavHost.kt`), the screens under `ui/screens/`,
`ui/onboarding/`, `ui/addfood/`, `ui/allergen/`, the settings registry (`domain/settings/SettingsRegistry.kt`), the
defaults in `data/settings/*.kt`, the workers in `work/`, and the watch app in `wear/`. Where a design doc said
something the code does not do, the code wins and this file says what the code does.

Conventions used below:

- **[off by default]** means the feature or switch is off until the person turns it on.
- **[setting: `id`]** names the settings-registry id (`SettingsRegistry.kt`) that changes the screen. "Settings" means
  Settings > the group named.
- **[needs key]** means the feature needs an API key the person adds (OpenRouter, USDA, YouTube).
- **[AI]** means the feature calls an AI model. AI is optional, runs only on a tap or a scheduled switch that is off by
  default, and never decides targets, allergens, plans, lookups or parsing.
- Sheets and dialogs are named where they open. "Empty" and "loading" states are listed where the code has them.

---

## 1. Top-level structure

### 1.1 First run and welcome flow

- **Welcome (onboarding)**, route `Onboarding`. Shown before the tabs on a first run, and again from Settings > About
  and help > "Show the welcome again". Full screen, no pill. "Skip setup" in the top bar.
  Steps in order: Welcome · Units and language · About you · Goal · Equipment · Foods you avoid (allergens) · How do you
  want to start · Your plans · Optional extras · Done.
  - Welcome: "Get started". Says there is no account, no internet and no AI needed.
  - Units: body weight, height, drinks, food search language, "Show products in other languages" [off by default].
  - About you: sex, birth year, body fat % (optional), first weigh-in.
  - Goal: goal, build, phase, pace. "Advanced: pick each item" for detail.
  - Equipment: three presets plus "Advanced: pick each item".
  - Foods you avoid: allergen list, "Add your own", Skip.
  - How to start: "Learn my baseline first (recommended)" or "Use the estimate now".
  - Your plans: optional, pick a plan to open when finished, or "I'll set up plans later".
  - Optional extras: Health Connect, Backups, AI shortcuts. None needed; all off until set up.
  - Done: "You're set", "Go to Today".
- **Tour**: a spotlight overlay that dims the screen and cuts out targets. Buttons: Skip tour, Back, Next, Done.
  Replay from Settings > About and help > "Replay the tour".
- **Tab tips**: one card per tab, shown once, with "Got it" and an action button (for example "Log food" on Food).
  Shown again with "Show screen tips again" [setting: `tips`].

### 1.2 Floating pill bar (the four tabs)

Floating pill at the bottom, shown on tab roots only. Four items, in this order:

| Tab | Icon | Root screen |
| --- | --- | --- |
| Today | calendar-today | Today |
| Food | fork and knife | Nutrition (route; label is Food since design v6) |
| Train | fitness | Train |
| Insights | line chart | Progress (route; label is Insights since design v6) |

- Tapping the current tab pops that tab to its root.
- Each tab keeps its own back stack.
- On a wide screen (a tablet or foldable) the pill becomes a vertical rail on the side.
- Pushed screens, sheets and full-screen tasks hide the pill. The session bar (1.4) sits above the pill on tab roots.

### 1.3 Top bars and the avatar

- Every tab root has its title on the left and the **avatar** on the right (a 32 dp initial disc, 48 dp target). The
  avatar opens **You** (section 6). It is in the same place on all four tabs.
- Pushed screens have a back arrow, a title, and at most two actions. Settings is never an action on a pushed screen;
  it is the gear on You.
- Full-screen tasks (Log food, Active workout, Make a workout, Welcome) have a back or minimise arrow and their own
  commit action.

### 1.4 Global overlays

- **Minimised workout bar (session bar)**: while a workout is running it shows above the pill on every tab root:
  "Workout" with the elapsed time and a "Resume" action. Tapping it reopens the Active workout. Back on the Active
  workout minimises it; it never discards. Discarding is only in the Finish dialog.
- **Snackbars**: short messages at the bottom, above the pill. Many have an **Undo** action (for example after a
  delete or a log that was flagged).
- **Tour overlay**: see 1.1.
- **Bottom sheets** (modal, dismiss by swiping or tapping outside): slot picker, entry actions, swap, plan manager, plan
  import preview, exercise detail and form, recipe ingredient picker, drink options, AI model picker, filters.
- **Dialogs**: confirm deletes ("Delete this …?", "Keep it"), discard changes, name prompts, the "Log it anyway"
  allergen confirmation.
- **Notifications** and the Android permission prompts are described in section 8.8.

---

## 2. Today (tab 1)

Route `Today`. Title is the date ("EEE, MMM d") with previous and next arrows. A "Back to today" chip appears when
viewing another day. Viewing a past day shows that day's entries. Everything below is one
scrolling list, top to bottom. Cards shown only on the current day are marked.

1. **Baseline week card** (current day, only while a baseline runs). Title "Baseline week", "Collecting" or "Ready",
   the day count ("day 3 of 7"), **See my baseline** (opens Profile & goals), **Build my plan** (opens Your plan).
2. **Export reminder** (only when due) [setting: `exportReminder`, on by default]. A note "Without sync, this phone holds
   the only copy" with **Export now** (opens Settings > Sync and export).
3. **Calories card** (always). Ring with "N kcal left" ("No target yet" if no target). Eaten, protein, carbs and fat
   tiles, a "More detail" control. Buttons: **Log food** (primary), **Scan a barcode**, **Estimate from a photo** [AI].
   A blank card of the same size shows until the first read.
4. **Next up** [setting: `todayCard.next`, on by default; current day only]. Up to three rows, in order:
   - the planned meal: **Log it**, **Swap** (opens the swap sheet), **Open plan** (Meal plan, section 3);
   - the coach's one focus line (Coach, section 8.9);
   - today's workout: **Start**, or "Rest day" with **Train anyway", "Workout in progress" with **Resume**, or "No program
     yet" with **Make one** (Make me a program, section 4).
5. **Water card** [setting: `todayCard.water`, on by default]. Litres so far, a bar, today's drinks as rows with a minus
   and plus stepper (for example "Water 250 ml x3"). Minus removes the last one; plus adds one more of the same drink and
   size. **Log a different drink** opens the Drinks page (section 3.7). Empty: "Nothing logged yet today."
   The drink values and sweat-rate panel ("Drink values", "Measure my sweat rate") sits under Advanced here and in
   Settings > Water and drinks.
6. **Weight tile** [setting: `todayCard.weight`, on by default]. Last weigh-in, or "No weigh-in yet", and **Weigh in**
   (a small sheet with the weight field and a save).
7. **Activity card** [setting: `todayCard.activity`, on by default]. A card of activity figures.
8. **Today's insight** (optional, [AI], [off by default]). One suggestion a day with Dismiss and "Why this suggestion?"
   Shown only when Daily insight is on and has produced a line.
9. **Meals**. Heading "Meals" and the hint "Long-press an entry to edit". One card per meal slot: Breakfast, Lunch,
   Dinner, Snacks, Drinks. Each slot has its entries, "Not logged" when empty, **Add food**, and "Log a drink".
   - Swipe an entry to copy it to another meal.
   - Long-press an entry: the entry actions sheet (edit grams, copy to another meal, move to another meal, delete this
     entry, cancel). Copy and move open the slot picker.
   - **Save as meal template** on a slot: a name dialog ("Name", Save, Cancel). Saved templates are in Food > Library > Meal
     templates.
   - Meal-slot rows show a "Contains your allergen" or "May contain" chip where it applies (section 8.1).

Empty and loading states: skeleton calorie card until the first read; "No target yet"; "Nothing logged yet today";
"No program yet".

**Settings that change Today:** the four card switches above plus Activity (Settings > Appearance and units, "Today card:
…"); Water target and quick sizes (Water and drinks); Volume unit (Appearance and units); Weight unit; Portion unit;
Salt or sodium (Nutrition); Work my targets out from my data (Nutrition); Export reminder (Reminders); Weekly check-in
notification (Reminders).

---

## 3. Food (tab 2, route `Nutrition`)

> Design v6: the tab is labelled **Food** and has segments **Diary / Plan / Recipes / Library**. Diary is the home below; Plan and Recipes host the screens in 3.3 and 3.4; Library lists Meal templates, Shopping list, Eating out, Restaurant menus, My foods and Drinks. The "More" list (3.2) is gone from the UI; the route stays registered. The centre + of the dock opens the Quick log sheet (see `docs/briefs/DESIGN-V6.md` d1).

Route `Nutrition`. The tab is the food diary and dashboard. Plans, recipes, templates, shopping and eating out are
behind one row, **More**.

### 3.1 Nutrition home (tab root)

From top to bottom:

1. **Day switcher**: "Today", "Yesterday", or the date. Previous and next arrows; the next arrow is disabled on today
   ("Next day, not available: today is the last day"). Tapping the date opens a day picker with "Show this day".
2. **Allergen strip** (compact): "Avoiding: <your allergens> ›" when set, or "No allergens set. Add yours" (amber) when
   empty. Opens the Allergens screen (3.8).
3. **Food tip** (tab tip, once). Action: **Log food**.
4. **Overview card**: calorie ring with the day's total against target, the three macro cards (protein, fat, carbs) with
   eaten and target, and a **Show macro split** / **Hide macro split** control (hold to show). A **More nutrients** section
   with "Caffeine from drinks", "Alcohol from drinks", "Water counted", and a salt or sodium tile when [setting: `showSodium`]
   is not Off. Empty and loading: "Reading today's…" / "Reading that day…".
5. **Meals card**: rows for Breakfast, Lunch, Dinner, Snacks and Drinks. Each row lists its entries, "Nothing yet" when
   empty, and a **+** to add. A planned meal shows as a line with **Log it**, **Open plan** and **Swap**.
6. **More** row: "More: plan, recipes, templates, shopping, eating out" → the More list (3.2).

Log food from the calorie tile, the tab tip or a meal row opens **Log food** (3.6).

### 3.2 More (pushed)

Route `NutritionMore`. Five cards:

- **Meal plan**: the active plan's name, or "No plan yet. Import one, write one or start from a template." → 3.3.
- **Recipes**: "No recipes yet…" when empty → 3.4.
- **Meal templates**: → 3.9.
- **Shopping list**: "Pick recipes and meal templates and get one list with the amounts added up. No meal plan needed." → 3.10.
- **Eating out**: "No saved options yet" or the count of saved options → 3.5.

### 3.3 Meal plan (pushed)

Route `MealPlans`, screen `PlanScreen`. Empty: "No plan yet", "Make a plan of any length, or import one."

- **Plan options** (top bar menu): New plan, Import a plan file, Export plan as script, Script reference (section 6.7).
- **Plans sheet**: a list of plans with Active, Use this plan, Edit, Archive. "New plan" form: name, first day of week 1
  (yyyy-mm-dd), "When it ends": Stop, or Repeat N times.
- **Week strip** with **Add a week** and the week's menu: Edit this week, Duplicate this week, Move earlier, Move later,
  Remove this week, Add a week at the end.
- **Dish cards** for each day: main dish, protein type, servings, totals ("Macros are incomplete" when partial), how to
  serve it. Each dish has **Log this meal** (with a macro-entry dialog when the numbers are unknown), **Cook this meal**
  (cook mode, 3.4), **Change recipe**, and "Can't eat what I cooked?" → **Find another option nearby** (the swap sheet,
  3.11).
- **Every week** accordion: standing breakfasts, standing snacks, hidden dairy checklist, "Pantry: buy once".
- **Eating out** accordion for the week, with the eat-out card and "Say this when ordering" notes.
- **Share shopping list** (text).
- Week edit sheet: main dish, protein, other ways to eat it (one per line), notes for the week, **Change the recipe**,
  **Save week**.
- Import preview: "Import this plan?", new exercises and allergen findings listed; "Can't import this file" on error.
  The plan file's own allergen notes can add warnings but never remove one. Size limit 2 MB.
- Allergen findings on imported plans: "Blocked", "May contain", "Mentioned as avoided" (section 8.1).

Empty states: "No meal plan yet" with **Make a plan**, **Import a plan file**, **Write it yourself**.

### 3.4 Recipes (pushed) and recipe screens

Route `Recipes`. Hub with: **Add a recipe** ("Type in the name, ingredients and steps"), **Import from a link** ("Paste a
recipe link. Intake reads the recipe data on that page, with no AI"), **Recipe Hunter** [AI optional] and "My recipes"
list. Empty: "No recipes yet" and the add and import choices.

- **Recipe Hunter** (route `RecipeHunter`). Finds recipes that fit protein per serving (at least), calories (no more
  than), servings, maximum prep minutes, a cuisine (about twenty choices or "Any cuisine"), a required filter, and
  free-text words. Checks every result against your allergens on this phone. Toggle **Use AI to read pages the phone
  can't** [AI, needs key; off]. Buttons: **Search recipes**, **Find more**, **Change the search**, **Run again**,
  **Open results**. Shows "Recent searches", "Working - this can take a minute or two", "Nothing passed every check" and
  "Nothing met every limit". Result cards: "Read by AI", "Check allergens", "Not checked", "Nutrition not stated on the
  page", "Ingredients checked locally", **View recipe**, **Save adapted copy**, **Open the page**. A sheet shows
  suggested swaps before a copy is saved. "Set up AI tools" when no key.
- **Recipe import** (route `RecipeImport`). "Recipe link", **Read this page** (no AI). Status: "Fetching the page…".
  Errors: "This page has no recipe data we can read." Fallback **Try with AI** [AI, needs key]. Preview with name,
  servings, minutes, ingredients (lines starting with ## are group headings), steps, notes; **Save recipe**.
- **Recipe editor** (route `RecipeEditor`). Name, servings, total minutes, ingredients (sheet: from my foods, type it,
  paste a list, group), steps (one per line), notes. Per-serving macros; "The total above is lower than the real dish
  until these have grams and macros." Allergen chips. Discard prompt on leaving with changes ("Keep editing", "Discard").
- **Recipe detail** (route `RecipeDetail`). **Log a serving** (servings stepper, "Logged · log another"), **Cook**,
  **Add to this week**, **Open the original page**, **Recipe options** (Edit recipe, Delete recipe with a confirm).
  Shows protein, carbs, per-serving values, ingredients, steps, notes, "Check allergens" / "Contains an allergen".
  "Estimated from ingredients" card (per line match, "Pick food", "Hide the ingredient matches").
- **Cook mode** (full screen, route `CookMode`). One step per page, swipe left or right. Checklist of ingredients with
  "Fewer servings" / "More servings" and Hide / Show. Timer chips (cook timers, see section 8.8). **Done cooking** →
  **Log a serving to** a meal; **Close without logging**.

### 3.5 Eating out (pushed)

Route `EatingOut`. **Find healthier options nearby** [AI optional] (the nearby search: location, a zip code, or AI for
menus the phone cannot read; see 3.11). **Menu import** → 3.12. **Saved eat-out options**: each with "Fits your
allergens" or an allergen chip, Remove (with confirm "Remove this saved option?"). Empty: "Nothing saved yet". Loading:
"Reading your saved options…".

### 3.6 Log food (full screen)

Route `LogFood`. Shared; opened from Today, Nutrition and the meal screens. Top bar: Back, "Log food". Five tabs:

- **Search**: search field, "Search foods", results from your foods, recipes, the offline food database (USDA, works
  offline), Open Food Facts (online, "Packaged foods (Open Food Facts)"), and "Also search USDA online (your key)"
  [needs key; off]. Sections: "Favourites", "Recent", "Recipes (saved on this phone)", "Restaurant menus (imported, works
  offline)". Empty: "Nothing yet", "No match". Errors: "Open Food Facts could not be reached…".
- **Scan** (barcode): camera scanner. "Looking that barcode up…", "Not found in Open Food Facts", "Scan again",
  "Scan another". A barcode this phone has not seen needs a connection; the barcode is kept with the food afterwards.
- **Photo**: take or pick a photo and estimate the food from it [AI, needs key]. "Photo estimate" is the food's source
  label afterwards.
- **Mine**: "My foods and recents", "Create a food" (name, brand, serving, calories, protein, carbs, fat, fibre, sugar,
  sodium, saturated fat), "Add a food by hand".
- **Quick add**: calories with an optional name (no food lookup).

Every row shows its allergen check result (section 8.1); "Checking allergens" while the check runs. A flagged item asks
**Log it anyway** (only after the person confirms it is safe) or **Don't log it**.

Drinks are logged from the **Drinks** page (3.7); a search that looks like a drink shows "Looking for a drink? Search
Drinks for …".

### 3.7 Drinks (pushed)

Route `Drinks` (and `DrinksSearch`, opened with the words typed in Log food). Sections:

- **My drinks**, **Recent**: tap to log the last size; press and hold for options.
- **Search drinks**, with "Clear the search". Results grouped as "On this phone", "Food database (USDA, works offline)",
  "Packaged drinks (Open Food Facts)", "USDA online (your key), saved to this phone" [needs key; off].
- **Logged today** strip with totals (caffeine, sugar) and "Nothing logged yet today."
- Drink sheet: custom amount, Edit, Customise (add-ins with amounts), Delete this drink.
- Empty: "No drinks yet", "No drink matches".

**Customise drink** (route `CustomDrink`): "What goes in" (add-ins with amounts, or a search of any food), "The finished
drink" (name), **Log it once**, **Save as My drink**, **Save changes**, **Delete this drink**.

**Settings:** quick sizes (Water and drinks > "Quick sizes"); drink values (Water and drinks, Advanced); volume unit
(Appearance and units); Open Food Facts search, language, other-language products (Food database).

### 3.8 Allergens (pushed)

Route `Allergens`, opened from the strip. The one place to set allergens (safety data, so not in Settings).

- **Your allergens**: switch list with a search field. Empty: "No allergens set", "Nothing is switched on, so nothing will
  be flagged."
- **How careful should Intake be?**: **Avoid** (warn when something you avoid shows up) or **Strict**.
- **Add an allergen**, **Add your own allergen** (name).
- **Keywords** (route `AllergenKeywords`, one per allergen): each keyword with a level, **Contains**, **May contain** or
  **Check the label**. **Add keyword** (word or phrase; how firmly it flags). "Yours" and "Built in" keywords. "Hide
  built-in reminders". Remove this allergen (confirm).
- **Hidden-ingredient reference**: every term that means the allergen is present.
- **Recently flagged**: foods, recipes and saved options flagged in the last while. Empty: "Nothing flagged in your recipes
  or saved eat-out options."

### 3.9 Meal templates (pushed)

Route `MealTemplates`. A saved meal that logs in one tap. Empty: "No meal templates yet. Tap 'Save as meal template'…".

- Each row: **Log now**, **Edit**, **Write to another NFC tag**, **Delete template** (confirm "Delete this meal template?").
- **New meal template**. Editor (route `MealTemplateEditor`): name, "Foods and recipes" list, **Add a food or recipe** (picker:
  Recent, All saved, the offline food database), **Save template** / **Save changes**.
- **Write to an NFC tag (optional)**: sheet "Hold the back of your phone near a writable NFC tag." See section 8.7.

### 3.10 Shopping list (pushed)

Route `ShoppingList`. Pick recipes and meal templates; one list with the amounts added up, grouped. "Pantry staples" can
be left out. Actions: **Share** (text), **Clear**, "Add an item". Empty: "Nothing to shop for yet. Save a recipe or make a
meal template, then pick them here."

### 3.11 Swap sheet (dialog over the current screen)

Route `SwapSheet`. Opened from a planned meal (Today, Nutrition, meal plan). A bottom sheet with:

- **Planned** card (the planned meal), then sections "Plan and recipes", "Saved", "Eat out nearby".
- Each option: "Fits your allergens", "Choose", "Choose anyway", "Estimate", "Saved for offline", "Save for offline".
- **Find healthy options nearby** [AI optional]. Toggles and buttons: "Search near me", "Allow location once and search",
  "Use a zip code instead", "Use my location instead", "Look up the rest with AI" [AI, needs key], "Stop searching".
  Errors: "Location is off for Intake…", "Open app settings", "Search did not finish". Nearby search runs on your phone
  first; the AI search runs only when you tap it.
- Empty: "No other recipes yet…", "Nothing here."

**Settings:** Eat out nearby: how far [setting: `eatOutRadius`]; AI settings.

### 3.12 Menu import (pushed)

Route `MenuSources`. Local copies of restaurant menus you trust, refreshed weekly if you turn that on.

- **Add source** sheet: Restaurant (name), Paste URL, Pick PDF, Paste text. Options: "Read the link", "Choose a PDF",
  "Paste a link", "No table read on this phone", "Read it with AI" [AI, needs key], "Not now" / "Skip".
- **Saved sources** list: each with status ("Never imported", "Running", "To review", "Failed", "Never run"), **Show items
  and actions**, **Refresh now**, **Remove source**. Empty: "No menus yet."
- **Review imported menu** sheet: each item "Accepted" or to review, with allergen match notes ("Allergen match · review",
  "Possible match · review", "Check with staff before ordering"), **Accept item**, **Reject**, **Add nutrition menu**,
  **Discard import** (confirm "Discard this import?").
- **Settings:** Refresh saved menus weekly [setting: `menuAutoRefresh`; AI, off by default].

### 3.13 Nutrition meal (pushed)

Route `NutritionMeal`. Opened by a meal row. One meal of one day.

- Header "Choose the meal" (slot), **More for this meal** menu: Clear meal (confirm), Save as meal template.
- **Time for new entries** (time pill; Set time / Reset).
- Totals card: total calories, protein, carbs, fat.
- Foods list with a stepper per item and swipe to remove. "That is the smallest portion. Swipe the item to remove it."
- **Add food** (opens Log food for this slot), **Quick add calories** (name optional, calories, "Protein, carbs and fat stay
  not listed."), **Save as meal template**, **Done**.
- Empty: "Log something in this meal first." when saving an empty meal.

### 3.14 Food detail (pushed)

Route `FoodDetail`. Shared by Log food, Drinks and My foods. Shows: a picture (when there is one), a source label
("Open Food Facts", "USDA database", "USDA search", "Photo estimate", "From a recipe", "Built-in drink", "Made by you",
"Scanned or edited by you"), a portion stepper ("Half a serving less" / "Half a serving more"), the nutrition label per
serving and per 100 g (or 100 ml for drinks), ingredients, the allergen check result, and:

- **Log** to the meal, with the slot picker. "Log anyway…" if flagged (asks to confirm).
- **Swap for another** (the swap sheet, 3.11).
- **More: edit, contribute, remove** → **Edit** (food editor with all label fields, "Save changes"),
  **Contribute to Open Food Facts** (shows exactly what will be sent; needs an Open Food Facts account; "Send"),
  **Remove from my foods** (confirm; past entries keep their numbers).
- Errors: "This food is not on this phone."

**Settings:** Food database (Open Food Facts search, language, other languages, contributing, username and password).

### 3.15 Food database settings (Settings)

See section 6.5, group "Food database".

---

## 4. Train (tab 3)

Route `Train`. Segmented control at the top: **Workout** | **Exercises** | **Videos**. A **Programs** icon sits in the top
bar.

### 4.1 Workout section

From top to bottom:

1. **Workout in progress** card (when a session runs): **Resume**.
2. **Today** card: today's program day with its exercises and **Start**. Rest day: "Rest day", **Train anyway**. No
   program: "No program yet", "Make a program in Programs". Empty day: "No exercises in this day yet."
3. **Make me a program** (route `ProgramGenerator`, section 4.5). "Make one for me" when there is no program.
4. **Other ways to start**: **Empty workout** ("Add exercises as you go"); **From a program** ("Pick any day of any
   program", a picker sheet "Start from a program"); **My workouts** ("Saved routines you can start any time", picker
   sheet); **From a video** ("Make a workout from a YouTube video", switches to Videos); **Repeat last**.
5. **This week**: muscle volume per region ("Under", "In range", "Over" against the weekly sets target). Tap a muscle to see
   the exercises that counted. Legend: "A set counts fully for its main muscle and by half for the helpers." Loading:
   "Loading this week".
6. **Recent sessions**: rows with date, title, "Unfinished" for an open session. **All sessions** opens the training log
   (4.6). Empty: "Finished workouts show up here, with their volume and records."
7. **Programs** row: "Your plan, My workouts, make or import one" → Programs (4.2).

Tab tip once: "Build, generate or pick a program, or just start an empty workout."

### 4.2 Programs (pushed)

Route `Programs`. Empty: "No active program."

- **Active program** card: Edit program, Duplicate, Export as a script, Archive.
- **Your other programs**: Make active, Edit, Duplicate, Export as a script, Archive. "Restore" and "Show"/"Hide" for archived.
- **New program** choices: **Make me one** ("Answer a few questions and get a plan that fits your days, time and
  equipment"), **Build from scratch** ("Name it, add days, pick exercises, set sets, reps and progression"), **From a
  template** → 4.4, **Import a script** ("Open a program saved as text, here or from a friend"; "Script help").
- **My workouts** section (saved routines): Start, Edit exercises, Watch the video, Rename, Duplicate, Delete ("It leaves
  My workouts. Sessions you already logged are kept in your history.").

### 4.3 Program builder (pushed)

Route `ProgramBuilder`. "Add days, then exercises. Everything saves as you go."

- Name and notes (rename dialog), **Active** / **Make active**.
- **Days**: named days (Push, Pull, Legs, Upper, Lower, Full body, or "Day 1"), **Add a day**, per-day menu: Rename, Set
  weekday, Copy to My workouts, Duplicate this day, Delete this day (confirm).
- **Exercises** per day: sets, reps range (from, to), effort 1 to 10 (optional), rest in seconds, progression ("Keep the
  weight as I set it", a percent of estimated max, or a formula, with "That reads fine." / "Check it"), "Use these
  settings for other exercises" (just this one, others on this day, whole program), move up and down, remove from this
  day. "Removed exercise" if the library entry is gone.
- **Rotation**: "Next in the rotation".
- **Share or write by hand**: **Export as script**, **Script reference** (section 6.7). **Create program** for a new one.
- Import preview: "Import this program?" or "Update this program?", new exercises added to the library, matched exercises,
  progression rules. Hidden, not erased.

### 4.4 Program templates (pushed)

Route `ProgramTemplates`. "Pick one to make your own copy. Change anything afterwards." Each card: **Use this template**,
**See the days** / **Hide the days**.

### 4.5 Make me a program (full screen)

Route `ProgramGenerator`. A questionnaire, one question per step, with **Back**, **Next**, **Skip**:

- main goal; how much you have trained; known numbers (optional);
- days a week ("Three good days beat five you skip");
- session length (shorter sessions get fewer sets);
- equipment: "Every item: barbells, racks, dumbbells, machines and cables", "Dumbbells and your own bodyweight", "Your own
  bodyweight and a pull-up bar", or "Advanced: pick each item";
- muscles to bring up (optional; "about 25% more sets");
- areas to protect (optional; exercises that load them are left out);
- **Advanced**: hard sets per muscle per week, rep ranges, compound and isolation lifts, deload. "Back to the usual numbers."

Result: **Why this plan** (how the plan was built, which answers were used, "Used" / "Not used", sets per muscle against the
aim), a day preview, and **Save and make it my program** or **Save, but keep my current program**. **Change my answers**.
No AI is offered on this screen.

### 4.6 Training log (pushed)

Route `WorkoutHistory`. Rows of finished and unfinished sessions with dates. Opens a session summary (4.9). Empty: "No sessions yet."

### 4.7 Exercises section (tab segment)

- Search ("Search exercises"), filters: muscle, equipment, "My equipment" (show only what your equipment can do), "Show
  hidden exercises" ("Nothing is hidden." when none). Empty: "No exercise matches with your equipment…", "No exercise matches.
  Clear a filter, or add it as your own."
- Rows with a "Custom" badge for your own exercises.
- **Exercise detail** sheet: photo (fade between or side by side with a second photo, or "No photo yet"), "Muscles worked"
  (six regions), "How to" (show, edit, hide), **Edit**, **Hide** / **Show again**, **Your history**: best set, heaviest,
  total volume, sessions, estimated max over time, best reps at each weight, warm-up sets.
- **Add your own exercise** / **Edit exercise** form: name, photo (take a photo, choose from gallery, remove), muscles,
  equipment, bodyweight exercise, usual rest between sets (seconds), how to (optional).

### 4.8 Active workout (full screen)

Route `ActiveWorkout`. Top bar: back arrow (minimise: "Minimise the workout. It keeps running."), title, **Finish**.

- Exercise cards: set rows with previous numbers ("Previous"), reps, weight, a done control per set, **Use last time's
  numbers**, **Pick a weight**, **Show the plates** (plate chips per side: "Per side: 45 + 25 + 2.5"), **Add set**,
  set-level form notes, **Swap exercise** (sheet; "Not in your equipment"), move earlier or later, remove.
- **Rest strip** when resting: countdown, **Skip**, rest vibrates when it ends (setting: Vibrate when rest ends).
- **Up next** row.
- **Add exercise** (exercise picker sheet, with "Add your own exercise").
- **Finish workout** → dialog "Finish workout?" ("Nothing logged yet" if no sets) with **Finish workout** / **Keep going**.
  **Discard** asks "Discard this workout?" (**Discard** / **Keep going**).
- Empty workout: "Empty workout", "Add an exercise".

**Settings:** default rest (seconds), rest vibrate, bar weight, plates available per side, equipment (Training).

### 4.9 Workout summary (pushed or after Finish)

Route `WorkoutSummary`. Opened after Finish, or read-only from the training log. Shows: "Workout <date and time>", exercises
with sets ("No working sets were logged in this workout."), **Change this set** (reps, weight, delete this set with undo),
**Records** (personal records beaten: "estimated 1-rep max … up …"), stats tiles (duration, volume, sets), **Muscles worked**
(region bars), **Next time** (suggested next numbers), **Repeat this workout**. **Keep this workout?** (name, notes, **Save
as workout** to My workouts, **Add to program**, Done). Missing session: "This workout is not here any more."

### 4.10 Jump tests (pushed)

Route `Performance`. "Explosive performance." Fields for standing reach and jump reach (with "These do not add up" check),
note (optional), **Log jump test**, "Test the same way each time" guidance. History list with "Trend is on Progress",
**Delete this jump test**. Empty: "No jump tests yet."

**Settings:** Show jump tests in Insights [setting: `jumpTests`, off by default]. When off, Progress does not show the card,
but this screen is still reachable from its route.

### 4.11 Videos segment

Covered in section 8.6 (cross-cutting) with the screens here:

- **Videos home**: "Paste a YouTube link", search ("Search workout videos"), filter chips (muscle group, style, duration),
  toggles **Workouts only** [default on], **Hide Shorts** [default on], **Saved** strip ("Saved · N", "See all", "No saved
  videos yet."), **Recommended for you** (from this phone's data; "Finding videos for you"; "Nothing recommended right
  now"), search results with badges **Workout**, **Maybe**, **Not a workout**. States: "Searching YouTube", "Could not
  search", "No videos to show", "No more pages: today's free searches are nearly used up." Overflow: "How Videos works",
  "Clear cached video information". Without a key: "Videos use YouTube and your own free key", "Add a key in Settings >
  API keys", "How do I get a key?".
- **Video detail** (route `VideoDetail`): player (YouTube, or direct playback when on), chapters, "Hide description" /
  "Show description", **Share link**, **Make this a workout**, "Download to this phone" (only when offline downloads are on),
  **Cancel download**, "Downloaded", **More like this**, Save to watch later and "Remove from saved". Message: "You already
  made a workout from this. Open it".
- **Make this a workout** (full screen, route `MakeWorkout`): found chapters and exercise lines ("No chapters or exercise
  lines found."), "What is in the video itself", transcript or exercise list paste ("Read this text"), **Improve with AI**
  [AI, needs key], a draft list with **Include** / **Left out** per exercise, name, sets, seconds or reps, rest, "Use
  another", "Pick from library", count reps or timed, move, remove. **Save workout**, **Save and start**. "Turn on at least
  one exercise to save."
- **Shorts player**: "YouTube Short", "Swipe up for the next Short."

---

## 5. Insights (tab 4, route `Progress`)

Route `Progress`. Subtitle: "Patterns over time, without the noise." **Log weight** button. Tab tip once.

From top to bottom:

1. **Weekly check-in** link card: "The week's summary, suggestions to apply, and a PDF to export or print" → 5.1.
2. **Training stats** link card: "Workout history, volume per muscle and personal records" → 5.2.
3. **Range selector** (for the charts below).
4. **Weight trend** card: daily weigh-ins and a smoothed trend line. Empty: "No weigh-ins in this range yet. Log your weight and
   the trend line appears here." **Log weight** button.
5. **Sleep, steps and heart rate** charts from Health Connect (sleep, steps, resting heart rate). Shown only for the
   record types the person allows (section 6.5, Health Connect). Empty and "Health Connect" status lines.
6. **Days logged** (adherence) and **Calories vs target** cards. "Calories logged" and "Target" lines.
7. **Measurements**: waist, chest, thigh lines; the log for each from body metrics (5.3).
8. **Progress photos** card: latest photo and "Progress photo" count → Progress photos (6.3).
9. **Averages** card: weekly averages. "Needs 14 days of weigh-ins and logs" until there is enough data.
10. **Maintenance estimate** (TDEE) card: "Recalculate now"; a confidence line ("Treat that maintenance figure as
    provisional…"); "Recalculating…".
11. **Jump tests** card (only when Show jump tests in Insights is on): "Log a jump test", trend or "Not enough jump tests to
    show a trend yet."

Settings that change Progress: Show jump tests in Insights; Health Connect record switches; units (Weight, Length); Work
my targets out from my data.

### 5.1 Weekly check-in (pushed)

Route `WeeklyCheckIn`. One screen: the weekly summary, suggestions, and past check-ins.

- **Your week** card: weight trend, calories average, protein average, days logged, weigh-ins, workouts, sleep average,
  average steps and resting heart rate where there is data ("Avg weight", "Avg intake", "Avg burn", "Avg sleep",
  "Resting HR", "Avg steps"). Week chips with the date range. "Adding up the week" while computing.
- **Export PDF** and **Print** (the week as a document).
- **Suggestions** (up to four; lowest priority first). Each has a title, a one-line reason with the numbers, **Why this?**
  (and "Hide the why"), **Apply** (says what it does, runs the change) and **Dismiss**. Status after: "Applied", "Dismissed",
  "Not decided". "You decide. Nothing changes until you tap Apply." Kinds: weight trend, calories, protein, water, training
  volume, strength stall, recovery, diet break, data gaps.
- Empty states: "No check-in yet" with **Check in now**; "All on track"; "Not enough data" ("Not enough logged this week to
  judge. Log full days to get suggestions."); "No suggestions".
- Footer: "This is not medical advice. If something feels wrong, rest and talk to a doctor."

Settings: Weekly check-in notification, Check-in day [setting: `coachNotify`, on; `coachWeekday`]; Reword my check-in
[AI, off].

### 5.2 Training stats (pushed)

Route `TrainingStats`. Estimated 1-rep max per main lift (Epley: weight x (1 + reps / 30); "An estimate, not a tested max");
"Recent records" ("No records beaten yet."); "Sets this week" ("No working sets this week."); **Open the training log**.

### 5.3 Log weight / body metrics (pushed)

Route `BodyMetrics`. Titled "Log weight". Fields: weight, body fat % (optional), measurements (optional), notes. **Log weight**.
Also reachable from Today's Weight tile.

---

## 6. You (avatar) and Settings

### 6.1 You (pushed from the avatar on any tab)

Route `You`. Top bar: back arrow, "You", and a **gear** that opens Settings.

1. **Profile card**: initial, name (optional), basics. Tap to open Profile & goals.
2. **Goals and plan** card: Calories, "Protein · fat · carbs", Water, Training, "Phase and pace", "How this was worked out".
   Tap to open Your plan. Empty: "Add your basics to build a plan" with **Open Profile & goals**.
3. **Progress photos** card: latest photos, "A progress photo is due." when due, **Add photo**, **Compare**. Empty: "No photos yet.
   Front, side and back are all optional." Note: "Not in exports unless you switch it on in Settings > Sync and export."
4. **Help** card: "Replay the tour, read the script docs, privacy and credits." → Tour, Script language (section 6.7), What leaves this
   phone (privacy statement), credits (Settings > About and help).

### 6.2 Profile & goals (pushed)

Route `Profile`. "What your plan is built from. It stays on this phone."

- **Basics**: name (optional), sex (for the energy formulas), birth year, body fat % (optional, "Leave empty if unsure"),
  **Your build**.
- **Your goal**: phase, **How fast?** pace. "Add your basics and a weigh-in to see this in kilograms or pounds."
- **Day-to-day activity**: outside training. Once the baseline has steps from Health Connect, they replace this choice.
- **Training you can do**: days a week, which days (optional), minutes per session, experience, training goal ("Match my
  goal above"), focus muscles (optional), areas to go easy on (optional; "Lifts that load these areas are left out of a made
  plan."). **Build my plan now** or **Build my plan from my baseline**. "Needs your sex, birth year, height and a weigh-in."
- **Baseline week**: "Let Intake learn your real numbers". **Start my baseline week** / **Stop the baseline**. Progress card
  ("Collecting", "Ready"). "Build my plan".
- **Weight** row: "Saved as a weigh-in", **Log it**.
- **Plans you applied**: history.
- **Good to know**: notes.

**Settings:** calorie-related and protein or fat targets are changed in Settings > Nutrition, not here (one home per target).

### 6.3 Progress photos (pushed) and Compare

- **Progress photos** (route `ProgressPhotos`): **Add a photo** ("Every angle is optional."; Camera or Gallery), a timeline
  grouped by date ("No photos yet. Photos are stored on this phone only."), **Compare two dates**, per photo: hide, close
  viewer. "On another device" for synced photos without a local copy.
- **Compare photos** (route `PhotoCompare`): date one and date two chips, a divider slider ("Drag to compare"), weight and body
  on those dates. "Add photos on two dates to compare" and "Those two dates have no angle in common, or are the same day."

### 6.4 Your plan (pushed)

Route `YourPlan`. "Every number says why. Change any of them before you apply." Calories, protein, carbs, fat and water with
the reason for each and the source ("Built from your measured burn", "Built from your baseline", "Built from a formula"). The
week's reverse-diet calories and "Heads up" note ("Intake will not change your target by itself, so switch it when the week
comes."). **Training**: "Edit", "See the full program", **Create this program when I apply** (it becomes the active program;
the current one is kept). **Apply this plan** ("Plan applied"; **Open my program**). Empty: "Not quite enough to build a
plan", "Fill in Profile & goals first.", **Open Profile & goals**. Loading: "Reading your profile", "Working out your daily
burn", "Building your numbers".

### 6.5 Settings (pushed)

Route `Settings`. Top bar: back arrow, "Settings". **Search settings** field at the top (matches control names and group
names; "No setting matches …"). Then **twelve group rows**, in this order, each with its blurb:

| # | Group | Route |
| ---: | --- | --- |
| 1 | Appearance and units | `SettingsGroup(appearance)` |
| 2 | Nutrition | `SettingsGroup(nutrition)` |
| 3 | Water and drinks | `SettingsGroup(water)` |
| 4 | Training | `SettingsGroup(training)` |
| 5 | Health Connect | `SettingsGroup(health)` |
| 6 | Food database | `SettingsGroup(fooddb)` |
| 7 | Videos | `SettingsGroup(videos)` |
| 8 | Reminders | `SettingsGroup(reminders)` |
| 9 | Sync and export | `SettingsGroup(sync)` |
| 10 | AI (optional) | `SettingsGroup(ai)` |
| 11 | API keys | `SettingsGroup(keys)` |
| 12 | About and help | `SettingsGroup(about)` |

Each group page has its controls in order; the **Advanced** disclosure (Expand / Collapse) holds the advanced controls. A
search result opens the group and scrolls to the control (the `focus` argument).

The full list, with defaults from the code:

**Appearance and units** (12 controls, all shown, none advanced)
- Weight: kg or lb [default lb]. Length: cm or in [default in]. Drinks and water: ml or fl oz [default US fl oz].
- Food portions: g or oz [default g].
- Theme: System, Light, Dark [default System]. Use wallpaper colours [off]. Reduce motion [off].
- Today card switches: Next up, Water, Weight, Meals, Activity [all on].

**Nutrition** (15 controls, 7 of them advanced)
- Work my targets out from my data [on]. Calories by hand (number). Protein per kg [2.0, minimum 0.6]. Fat per kg [0.8].
- Add active calories to my target [off]. Show salt or sodium on the dashboard [Off, Sodium or Salt].
- Eat out nearby: how far (segmented).
- Recalculate from my data (action) and "Recalculate now".
- Advanced: Allow calories below the floor [off]; Carb cycling [off]; Carb cycle shift; Refeed days per week; Diet break every
  (weeks); Contest prep: weekly loss; Reverse diet: calories added per week.

**Water and drinks** (11 controls)
- Daily water target (ml; empty = calculated). Adjust the target for the day [on]. Training-day increase.
- Adjust water for weather [off]. Weather location (approximate or city/ZIP). City or ZIP code. **Forget this location** (action).
- Quick sizes (chips: the sizes on the Today and watch quick buttons).
- Advanced: Drink values (table: strength, caffeine, hydration weight); Measure my sweat rate (duration); Use my measured
  sweat rate [off].

**Training** (8 controls)
- Default rest [120 s]. Vibrate when rest ends [on]. Bar weight. Plates available per side (chips).
- My equipment: preset and items (chips). Show jump tests in Insights [off, new].
- Advanced: Weekly sets target per muscle region.

**Health Connect** (17 controls; 1 action; the per-type switches reflect the permission Android has granted)
- **Allow all Health Connect permissions** (action). If Health Connect is missing or needs an update, a banner says so, with
  **Open in Play Store**.
- Read switches: Exercise sessions, Total calories burned, Active calories, Basal metabolic rate, Steps, Sleep, Heart rate,
  Resting heart rate, Weight, Body fat, Lean body mass, Older history (only on Health Connect versions that have the history
  permission). A switch that is on means the permission is granted; off opens Health Connect or asks for it.
- Write switches: Nutrition, Weight, Hydration, Workouts.
- Status line: "Last synced"; **Sync now**; "Syncs when you open the app and after you log something, never on a timer."

**Food database** (6 controls)
- Search Open Food Facts while typing [on]. Food search language (chips; "Packaged foods are listed when they have a name in
  this language or in English"). Show products in other languages [off]. Allow contributing to Open Food Facts [off].
- Open Food Facts username (text). Open Food Facts password (secret; sent only over HTTPS in the request body when you contribute).
- The USDA key is under API keys.

**Videos** (4 controls)
- Show only workouts by default [on]. Personalise Recommended from this phone's data [on]. Hide Shorts [on].
- Offline downloads and direct playback [off]. "Videos need internet and your own YouTube key (under API keys)." Not an
  official YouTube feature.

**Reminders** (12 controls; all off except the check-in and export ones)
- Weekly check-in notification [on]. Check-in day (chips; default is the baseline week's start weekday, else Monday).
- Progress photo reminder [off; every 2, 3 or 4 weeks]. Export reminder [on].
- Workout reminder [off] and its time [17:00]. Meal reminders [off] with breakfast [07:30], lunch [12:00] and dinner [18:30].
- Water reminders [off] and their hours [08:00 to 20:00 by default].
- "Nothing is sent anywhere." All reminders are local notifications (section 8.8).

**Sync and export** (10 controls; 5 actions)
- Self-hosted sync server URL. Pairing code (text, for example "XXXX-XXXX"). Name this device.
- **Linked devices** and **Stop syncing here** (actions). Conflicts show as "Conflicts to review" with "Keep current" and
  "Use the other version".
- Export passphrase (secret, optional; blank leaves the JSON plain). Include progress photos in the export [off].
- **Export everything (JSON)**, **Export spreadsheets (CSV)**, **Import a backup** (actions). Import shows the backup's
  file, a passphrase prompt if encrypted, and "Nothing is deleted. Where both sides changed a row, the later change wins."

**AI (optional)** (21 controls; 19 settings, 2 actions)
- Use AI features [master switch; no effect without an OpenRouter key]. Monthly budget (USD; warning at 100%, calls pause
  until next month unless you raise it).
- Privacy for AI requests (radio). Suggest free models [off]: "Free models are often paid for with your data."
- Daily insight [off]. Reword my check-in [off]. Refresh saved menus weekly [off].
- Advanced: model per task (pickers, with "Refresh model list and prices"); uses a day per AI feature; **Reset usage
  counters** and **Run benchmark** (actions). The benchmark runs only on tap, with a spend cap.

**API keys** (3 controls)
- OpenRouter key. USDA FoodData Central key. YouTube Data API key (new). Keys are saved encrypted on this phone, sent only
  in request headers, and shown as "Key saved · not tested" until a test passes.

**About and help** (7 controls; 6 actions, 1 read-only)
- **Replay the tour**, **Show the welcome again**, **Show screen tips again**, **Script language help**, **What leaves this
  phone** (privacy statement), **Credits and licences**. Version (read-only).

Note on counts: `SettingsRegistry.kt` has 126 entries (109 stored settings, 16 actions, 1 read-only line; 25 advanced; 28
new). The comment above the list in that file still says 125 and 108; the list itself is the authority.

### 6.6 What leaves this phone (pushed)

Route `Privacy`. A plain list: "Never leaves" (food log, weight, measurements, workouts, meal plan, allergen profile),
then "Only when you use it" items with a **Needs your key** or **On, can be switched off** or **Off until you turn it on**
label: food search (USDA), barcode lookup and packaged search (Open Food Facts), contributing (Open Food Facts), weather
(Open-Meteo), model list, AI photo estimate, Recipe Hunter, nearby restaurants (OpenStreetMap Overpass, no key), menu import
and AI search.

### 6.7 Script language (pushed) and Script docs

Route `ScriptReference`. The scripting wiki bundled in the app: Overview, Programs, Progress rules, Meal plans, Errors,
Optional AI prompt (with **Copy prompt**), Example program, Example plan. "All script docs" list. Reached from Settings
> About and help, Help, the Programs and Plan option menus, and import errors.

---

## 7. Watch app (Wear OS companion)

The watch is a remote for the phone. The phone stays the source of truth and there is no AI on the watch. Same package
family and signing key as the phone build (see `docs/WEAR.md`). Screens:

1. **Home**: "INTAKE"; Eaten (kcal against target), Protein, Water (against target, in the phone's volume unit). Buttons:
   **Add water**, **Add food**, and **Start workout** or **Workout running** (**Add sets on the phone**). "Open Intake on your
   phone once to load today." and "Phone not connected. Numbers show when it is." Pending requests show as "<what>: pending,
   will sync". Tap to dismiss.
2. **Add water**: "ADD WATER"; the phone's quick sizes, a custom amount with a plus and minus picker ("Less water" / "More
   water"), **Add this amount**.
3. **Add food**: "ADD FOOD"; a meal-slot chip ("Tap to change"). Rows: **Log it** (today's planned meal), **Recent foods**,
   **Favourites** ("Star foods on the phone to find them here."), **Saved meals**, **Quick add calories**.
4. **Saved meals**: "SAVED MEALS"; "Save a meal on the phone to find it here."
5. **Quick add**: "QUICK ADD"; a number picker plus the slot.
6. **Start workout**: "START WORKOUT"; **Today's program day**, **Empty workout** ("The phone starts it and the watch shows it.").
   "A workout is already running." when one is.
7. **Workout** (when a session runs on the phone): the active set screen with "LOG SET", "Prescribed", "One rep fewer",
   "One rep more", RPE, Rest ("RESTING", Skip), "All sets done" with "Finish the session on your phone." The screen stays on
   during a workout. A "Workout in progress. Tap to log sets" ongoing notification.

Allergen rules on the watch: CONTAINS is refused and the phone's message is shown. MAY CONTAIN is not logged from the watch
("finish on your phone"). Every request is checked again on the phone when it arrives.

Not checked on a real watch (per `docs/WEAR.md`): pairing and Data Layer delivery both ways, the notifications and their
permission prompt, round-screen layout, and the phone handlers end to end.

---

## 8. Cross-cutting systems

### 8.1 Allergen checking

Rule: allergen safety is local and deterministic. A model or an imported file can raise a warning, never clear one.

Where it appears:

- Log food rows (search, scan, photo, mine, quick add), Food detail, the drinks page, Today and Nutrition meal rows.
- Recipes and Recipe Hunter results, recipe detail and editor.
- Meal plans, planned meals, eat-out options, swap options and nearby results, menu import review.
- Shopping list and meal templates (blocked on CONTAINS).
- Watch requests (refused on CONTAINS; "may contain" is not logged from the watch).
- Allergens screen: recently flagged list.

Chips and labels, as the code shows them:

| Label | Meaning |
| --- | --- |
| **Fits your allergens** | Checked against the label or panel and nothing matched. Shown only for a result that was really read. |
| **Only the name was checked** | Only the name was read (a generic food, a drink, a recipe with no ingredient lines). Not a statement about ingredients. Never shown as a pass. |
| **Checking…** / **Checking allergens** | The check is still running. Shown instead of a blank, never as a clear. |
| **May contain** | A possible trace or a soft match. An amber review, not a block. "May contain · soft match", "Possible cross-contact". |
| **Contains** / **Contains your allergen** | A matched ingredient you avoid. Blocks the item until you confirm. |
| **Check the label** | A keyword set to "Check the label": nothing is blocked; you are reminded to read the pack. |
| **Not checked** | No check was possible for this row. Shown in amber. |
| **Blocked** | Plan and template imports: the item cannot be used. |
| **Mentioned as avoided** | The name of an avoided item appears in a note. Not a warning by itself. |

Sheets and dialogs: the food safety check sheet ("Food safety check", "This contains an ingredient you avoid", "Don't log
it" / "Log it anyway", "Only log it anyway if you have personally verified this food is safe for you"); the plan allergen
sheet ("Log despite your allergen?"); the override dialog; and "Choose anyway" on swap and nearby options.

Allergen list: switched on in Allergens (Avoid or Strict). Keywords and their levels per allergen. Built-in reminders can be
hidden per allergen.

### 8.2 Units

- Stored: weight in kg, length in cm, nutrition per 100 g (or 100 ml for drinks). Timestamps are epoch milliseconds.
- Display settings: weight (kg or lb, default lb), length (cm or in, default in), drinks and water (ml or fl oz, default
  fl oz), food portions (g or oz, default g). The Settings label says "Stored in kg whatever you pick."
- Shown on: every weight, height, water, drink and portion field, the Today and Progress charts, the watch (volume unit).

### 8.3 Backup, export, sync

- **Export everything (JSON)**: all data. With a passphrase it is encrypted; blank passphrase gives plain JSON. Optional
  progress photos (zip). Result shown with a time ("MMM d, h:mm a").
- **Export spreadsheets (CSV)**: plain, not encrypted.
- **Import a backup**: pick the file; a passphrase prompt if encrypted; then the preview with "Nothing to change" or
  "Replace these settings with the backup's" (settings are optional to replace). Nothing is deleted; for a row changed on
  both sides the later change wins.
- **Export reminder** on Today when due [on by default].
- **Device sync** (optional, self-hosted server, see `docs/SYNC-EXPORT.md`): server URL, pairing code, device name,
  **Pair this device**, **Sync now**, **Linked devices**, **Remove**, **Add another device**, **Stop syncing here**. Status:
  "Not paired", "Syncing…", "Sync failed". Conflicts: "Keep current" or "Use the other version". Off until a server is set.

### 8.4 Health Connect

- Settings > Health Connect: read and write switches per record type (section 6.5). The switch reflects the Android
  permission, not a separate Intake setting.
- Status: "Health Connect is not installed on this phone" / "needs an update" / "not available on this phone"; first sync
  reads the last 30 days; "Could not read everything" when a read fails.
- Where it shows: Progress (sleep, steps, resting heart rate charts; Health Connect status), Today's Activity card, the
  profile's activity (steps replace the day-to-day activity choice once the baseline has them), the maintenance estimate,
  and the Add active calories switch under Nutrition.
- Writes (only if switched on): nutrition, weight, hydration, workouts.
- Sync runs when the app opens and after a log is saved, never on a timer.
- Optional step in onboarding ("Health Connect: Steps, sleep and weight from your watch or scale.").

### 8.5 AI (optional)

Rule: no feature needs AI. Targets, allergens, plans, lookups and parsing never use AI.

- **Master switch**: "Use AI features" in Settings > AI. No effect without an OpenRouter key: "No key · AI off".
- **Key**: OpenRouter key, encrypted, tested with **Test connection**; **Replace key**, **Remove key**. The key goes only in a
  request header or body.
- **Budget and usage**: monthly budget in USD (a warning at 100%), today, week and month counts, "Why calls failed this
  month", **Reset counters** (history stays on the phone), estimated cost by feature, "Uses a day" per feature.
- **Where AI appears** (each runs only on a tap or on its switch):
  - Log food: **Estimate from a photo** and the Photo tab (photo estimates).
  - Recipe Hunter: web search for recipes; **Use AI to read pages the phone can't**.
  - Recipe import: **Try with AI** when the page has no readable data.
  - Menu import: **Read it with AI** when a PDF has no readable table.
  - Eat out and swap: **Look up the rest with AI** / **Use AI to find menus I can't** / **Search with AI instead**.
  - Make a workout from a video: **Improve with AI**.
  - Today's insight [off by default; one a day, scheduled].
  - Coach check-in rewording [off by default].
  - Refresh saved menus weekly [off by default; scheduled].
  - Benchmark: **Run benchmark** (your own spend, capped, on tap).
- **Model choice**: a picker with Free or Paid tags and "Suggested", "Your choice", "May keep your prompts"; privacy setting
  (Privacy for AI requests) filters which models can be used.
- Shown in: Settings > AI, Privacy ("What leaves this phone"), and the first-run "AI shortcuts" extra (off until a key).

### 8.6 Videos

- Needs internet, and your own YouTube Data API key (Settings > API keys) for search. Without a key, the Videos segment shows
  the key steps. Recommended videos come from this phone's own data (switch: personalise).
- Filters: workouts only [on], hide Shorts [on], muscle group, style, duration. Badges: Workout, Maybe, Not a workout.
- Saved videos to watch later; a saved strip on the Videos segment.
- Video detail: player, chapters, share link, make this a workout.
- **Make this a workout** reads chapters and the description on the phone first; no AI unless you tap **Improve with AI**.
  The result is a My workout (Train).
- Direct playback and offline downloads: [off by default; setting "Offline downloads and direct playback"]. Not an official
  YouTube feature. Downloads show "Downloaded" and can be cancelled.
- Cache: "Clear cached video information".

### 8.7 NFC tags

- A meal template can be written to an NFC tag (Meal templates > Write to an NFC tag, or "Write to another NFC tag"). Hold the
  back of the phone near a blank, writable tag (NTAG21x sticker). Messages: "Hold the back of your phone near a writable NFC
  tag.", "This tag is read-only…", "This tag does not support NDEF links…", "Start writing again".
- Tapping a written tag opens Intake (it may be closed) at a dialog "Log <template name>?": meal chips (the slot defaults to
  the time of day), any allergen warning listed, and a **Log** button. If the template is gone: "Meal tag not found" with
  "That tag's meal no longer exists. Write the tag again from Food, Library, Meal templates." A tag that is not an Intake tag is
  ignored, or reported as "This tag does not hold a meal Intake can read."
- The tag holds only the template's link (intake://template/<id>), not the meal's data.
- Rows show "No NFC tag" when no tag is written.

### 8.8 Reminders and notifications

Local notifications only; nothing is sent to a server. Channels and their switches (section 6.5, Reminders):

- **Workout today**: on training days at the chosen time, until today's workout is done [off].
- **Meal reminders**: at each meal's time, only when nothing is logged for that meal [off].
- **Drink some water**: every two hours, only when behind for the time of day [off].
- **Progress photo due**: when the photo reminder interval has passed [off].
- **Export reminder**: an export due note [on]; also a note on Today.
- **Weekly check-in is ready**: opens the weekly check-in [on]. The notification opens the screen.
- **Calorie target updated**: sent when the measured maintenance estimate moves the target ("Now N kcal"). No separate switch was
  found in the registry.
- **Timers**: "Timer finished" and "Cook timer finished" (rest timer and cook-mode step timers, shown as a running notification
  while counting).
- **Watch**: "Workout in progress. Tap to log sets" (ongoing), "Not logged" when a watch request is refused.
- Failure notices: "Reminder check failed", "Coach check-in failed" (background jobs).
- Android asks for the notification permission when it is first needed.

### 8.9 Weekly check-in and coach

- The coach is a set of plain rules (no AI decides anything). It looks at the last one or two weeks and offers up to four
  suggestions with the reasons from your own numbers. **Nothing changes until you tap Apply.**
- Runs once a day in the background and does nothing until the check-in is due: on the chosen weekday, or on the next day
  the phone is used. The first check-in waits for some food or a weigh-in in the last 14 days.
- Surfaces: the Today "One thing to do today" line (the coach's focus), the Progress check-in card, the check-in screen
  (5.1), and the notification.
- A dismissed or applied suggestion is not repeated for 7 days; after a calorie change the calorie rule stays quiet for 14 days.
- Settings: notification on or off, check-in day, reword with AI [off].

### 8.10 Scripting (plans and programs as text)

- Plans and programs can be exported as a script (text) and imported from one (Meal plans and Programs option menus, Import
  a plan file, Import a script). Import shows a preview; errors show as "Nothing was imported" with each line and "How to fix".
  Nothing changes in the app until the import is confirmed.
- The language is documented in the bundled wiki (section 6.7). "Write it yourself" starts from an empty plan.

### 8.11 Motion, theme and accessibility settings

- Theme (System, Light, Dark), Use wallpaper colours [off: the designed palette is used], Reduce motion [off].
- Tab switches cross-fade; pushes slide; sheets slide up. With Reduce motion or the system's animations off, transitions are
  a short cross-fade.

---

## 9. Screen count and route list (mockup checklist)

Counts are from `ui/navigation/Destinations.kt` (49 `Route` entries; 4 nested tab graphs), `IntakeNavHost.kt`, and the
watch's `RemoteScreens.kt` and `MainActivity.kt`.

- **Tab roots: 4** (Today, Food, Train, Insights; routes Today, Nutrition, Train, Progress), plus the centre + (Quick log sheet).
- **Full screens and pushed screens: 43**, plus the Welcome flow (1) and the swap sheet (1, a dialog destination).
  Total route destinations: **49**.
- **Settings pages: 1 home + 12 group pages** (one route with a group argument).
- **Watch screens: 7** (Home, Add water, Add food, Saved meals, Quick add, Start workout, Active workout).
- **Sheets and dialogs**: not counted as screens. Twenty UI files define bottom sheets and about 47 alert-dialog call sites
  exist in the UI code.

### 9.1 Route list

Tab roots:
1. `Today`
2. `Nutrition`
3. `Train`
4. `Progress`

Welcome:
5. `Onboarding` (10 steps)

Nutrition:
6. `NutritionMore`
7. `MealPlans`
8. `Recipes`
9. `RecipeHunter`
10. `RecipeImport`
11. `RecipeEditor` (new or edit)
12. `RecipeDetail`
13. `CookMode`
14. `EatingOut`
15. `MenuSources`
16. `MealTemplates`
17. `MealTemplateEditor` (new or edit)
18. `ShoppingList`
19. `NutritionMeal`
20. `Allergens`
21. `AllergenKeywords`
22. `LogFood` (Search, Scan, Photo, Mine, Quick add)
23. `FoodDetail`
24. `Drinks`
25. `DrinksSearch`
26. `CustomDrink`
27. `SwapSheet` (dialog)

Train:
28. `Programs`
29. `ProgramBuilder`
30. `ProgramTemplates`
31. `ProgramGenerator`
32. `VideoDetail`
33. `MakeWorkout`
34. `Performance` (jump tests)
35. `ActiveWorkout`
36. `WorkoutSummary`

Progress:
37. `WorkoutHistory` (training log)
38. `TrainingStats`
39. `WeeklyCheckIn`
40. `BodyMetrics` (log weight)

Shared (registered once at the root):
41. `You`
42. `Settings`
43. `SettingsGroup` (12 pages: Appearance and units, Nutrition, Water and drinks, Training, Health Connect, Food database,
    Videos, Reminders, Sync and export, AI (optional), API keys, About and help)
44. `Privacy` (What leaves this phone)
45. `Profile` (Profile & goals)
46. `YourPlan`
47. `ProgressPhotos`
48. `PhotoCompare`
49. `ScriptReference`

Note: the list above has 49 entries. The 12 settings group pages are one route with a parameter, so they are shown once.

### 9.2 Sheets and dialogs (not routes)

Entry actions and slot picker (Today); plan manager and week editor, plan import preview (Meal plans); recipe ingredient
picker and cook-mode servings (Recipes); swap sheet (dialog route); nearby search sheet; menu source and review sheets;
food safety check and allergen override dialogs; drink options and custom amount; allergen keyword add dialog; video filters,
saved-video menu and video overflow; program import preview, exercise edit sheet, progression choice; exercise picker,
exercise detail, exercise form; workout picker and program picker; finish workout dialog; set edit dialog; swap exercise
sheet; profile weight row; photo add sheet; key entry and model picker sheets; sync pairing and conflict cards; import
preview; watch NFC write sheet; script diagnostics sheet.

---

## 10. Items behind a setting or off by default (quick list)

- Off by default: Show jump tests in Insights; Daily insight; Reword my check-in (AI); Refresh saved menus weekly; Suggest free
  models; Use the offline download and direct playback option; Adjust water for weather; Add active calories to my target;
  Carb cycling and the other advanced nutrition options; Show salt or sodium (Off); Show products in other languages;
  Contribute to Open Food Facts; Include progress photos in the export; Workout reminder; Meal reminders; Water reminders;
  Progress photo reminder; Use my measured sweat rate; Use wallpaper colours; Use AI to read pages the phone can't; Use AI
  features needs an OpenRouter key before anything runs.
- On by default: Weekly check-in notification; Export reminder; Next up, Water, Weight, Meals and Activity cards on Today;
  Work my targets out from my data; Adjust the target for the day; Default rest and vibrate; Show only workouts by default;
  Personalise Recommended; Hide Shorts; Open Food Facts search while typing.
- Needs a key: AI features (OpenRouter), USDA online search and the USDA key, YouTube search and the YouTube key.
- Needs a connection: barcode lookup of unknown products, Open Food Facts, USDA online, video search, weather, nearby search, AI.
- Works with no network, no account and no AI: logging, the offline food database, your foods and recipes, plans, templates,
  allergens, targets, the coach rules, training, progress and backups. The watch remote needs the phone to be reachable to
  apply its requests.
