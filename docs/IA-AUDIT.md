# Information architecture audit (v3 build)

Why this exists: the user used the v3 build and said, "this is much better but I feel like there is still just
SO much going on that it feels complicated. I like that the app does so much, but I feel like menus and
navigation needs to be streamlined a little better." This audit counts what "so much" is. The proposal that
answers it is `docs/IA-v4.md`, with a clickable mockup in `design/intake-mockup-v3.html`.

**Method and limits.** Everything below was read from the code at `f5e6089`: `Destinations.kt`,
`IntakeNavHost.kt`, `ui/screens/**`, `addfood/`, `onboarding/`. Nothing was run, and no UI was seen on a device
(there is no emulator in the cloud containers). Control counts are approximate: they count buttons, rows, switches
and menu items found in the screen's source. Tap depth means taps **from Today**, the pill switch counts as one tap,
and the last tap is the one that reaches the control or screen that does the job.

## Headline numbers

| Measure | Count |
| --- | --- |
| Distinct routes | **44** (51 registrations: 7 screens are registered in 2 or 3 tab graphs) |
| Bottom sheets (call sites) | **21** |
| Dialogs (call sites) | **25** |
| Reachable surfaces in all | **90** (44 + 21 + 25) |
| Top-level tabs | 5 (Today, Food, Train, Progress, More) |
| Cards and blocks on Today, at most | **13** (10 cards, 1 button, 2 notes) |
| Duplicate entry points (same feature, two or more rows) | **16** pairs, listed below |
| Features reachable from 3 or more places | **12** |
| Screens doing two or more jobs | **14** |
| Places a setting can be changed | **10** screens and sheets |
| Collapsed "more" or "advanced" drawers | **8** |
| Jargon terms a normal person has to decode | **22** |
| Everyday-or-rare tasks audited | 37 |
| Tasks that take 4 or more taps | **10** (4 of them everyday tasks) |
| Concepts met in the first minute | **12** named concepts (5 tabs + 7 Today things), plus 4 macro numbers |

## 1. Inventory

### 1a. Tab roots and the welcome flow

| Route | Paths in (tap depth) | Purpose | What it shows |
| --- | --- | --- | --- |
| `Today` | Home (0). Pill (1) | The day: what is left, water, workout, meals | Up to **13 blocks** (10 cards, the Log food button and 2 notes), plus the date header and a "Meals" heading. The cards: focus, baseline (first week only), ring with 3 macros, water (4 chips), workout, AI insight, and 4 meal slots |
| `Food` | Pill (1) | Food hub | A Log food button, a scan link, a first-visit tip, **5 rows** (Meal plans, Recipes, Meal templates, Eating out, Allergens), then a "More food tools" drawer with **3 more rows**. 10 things to tap |
| `Train` | Pill (1) | Train hub | A workout card (3 buttons, or 4 choices with no program), **3 rows**, a "More training tools" drawer with **3 more rows**. About 9 things |
| `Progress` | Pill (1) | Charts and reports | Log weight button, range chips (4), **5 cards** (weight, adherence, calories, weekly summary, performance), **4 link rows** (Weekly check-in, Training stats, Weekly report, Health Connect). About 11 blocks |
| `More` | Pill (1) | Profile, settings, help | **7 rows** (Profile & goals, Weekly check-in, Settings, Backup & sync, AI, Privacy, Help), a privacy note, a version line |
| `Onboarding` | First run (0); Help > Show the welcome again (3) | Nine-step setup | 9 steps |

### 1b. Food screens

| Route | Paths in (depth) | Purpose | Size | Duplicates |
| --- | --- | --- | --- | --- |
| `MealPlans` | Food > Meal plans (2); onboarding hand-off | Plan viewer, plan manager, shopping list, cook and log, eat-out fallback | **About 32 controls**: week strip, plan menu (4 items), week menu (6), recipe card with 6 accordions, Cook, Log, "Can't eat what I cooked?" | Plan B is also reached from here |
| `Recipes` | Food > Recipes (2) | Recipe library plus 3 ways to add | 3 add rows + the list | Import is also reached from Recipe Hunter |
| `RecipeHunter` | Recipes > Recipe Hunter (3) | AI recipe finder (optional) | 6 sections, model picker sheet | AI settings link |
| `RecipeImport` | Recipes > Import (3); Hunter > Import (4) | Read a recipe from a link | 3 controls | 2 entry points |
| `RecipeEditor` | Recipes > Add (3); Detail > Edit (4) | Write a recipe | About 6 controls and an ingredient sheet | |
| `RecipeDetail` | Recipes > row (3) | View, log a serving, cook, edit | 5 controls | |
| `CookMode` | Detail > Cook (4); Meal plans > Cook (3) | Step-by-step cooking | 10 controls | 2 entry points |
| `EatingOut` | Food > Eating out (2) | A hub that holds **two rows** | 2 rows | Pure pass-through |
| `PlanB` | Eating out > Plan B (3); Meal plans > eat-out sheet (4) | Find a safer place to eat nearby | 8 controls, 3 sections | 2 entry points |
| `MenuSources` | Eating out > Menu import (3) | Import and check restaurant menus | 10 controls, add sheet, review sheet | |
| `MealTemplates` | Food > Meal templates (2) | Saved meals, log in one tap, NFC tag | 6 controls, 2 sheets | Today "save as meal prep" feeds it |
| `Allergens` | Food > Allergens (2) | A hub that holds **two rows** | 2 rows | Pure pass-through |
| `MyAllergens` | Allergens > My allergens (3); More > Settings > My allergens (3) | Pick what you avoid, strictness | 3 controls plus the list | **2 homes** (Food and Settings) |
| `AllergenKeywords` | My allergens > allergen (4) | Keywords per allergen | 13 controls | |
| `AllergenReference` | Allergens > reference (3) | Read-only hidden-ingredient guide | Long text | |
| `MyFoods` | Food > More food tools > My foods (3) | Every food on the phone | List | The Add food sheet has a "My foods" tab too |
| `FoodDetail` | My foods > row (4); Add food > My foods | View, edit, contribute to Open Food Facts | 11 controls, 2 dialogs | |
| `FoodData` | Food > More food tools > Food database (3); More > Settings > Food database (3); FoodDetail > link | OFF search and contribute switches, licences | 4 controls | **3 entry points** |

### 1c. Train screens

| Route | Paths in (depth) | Purpose | Size | Duplicates |
| --- | --- | --- | --- | --- |
| `Programs` | Train > Programs (2); onboarding | Program list, session, weekly sets, recent sessions, add a program | **17 controls, 7 headings**, 5 jobs | Workout start repeated from the hub |
| `ProgramBuilder` | Programs > Edit (3); Train > Build (2, with no program) | Edit days, exercises, progression | **28 controls**, 4 dialogs, an exercise sheet | Script help link |
| `ProgramTemplates` | Train > More training tools > Templates (3); Programs > template (3); no-program card (2) | Copy a ready-made program | 2 controls | **3 entry points** |
| `ProgramGenerator` | Train > Make one for me (2); Programs (3); no-program card (2); Today workout card (1); Your plan | Questions, then a program with reasons | 8 controls, 5 sections | **5 entry points** |
| `ExerciseLibrary` | Train > Exercises (2) | Browse, search, add, hide | 11 controls, 7 headings; detail sheet; form sheet | Also embedded as a picker in the builder and in the workout |
| `Performance` | Train > More training tools > Performance (3); Progress > Add jump test (2) | Log jump tests | 2 controls | **2 homes**, registered in 2 graphs |
| `ActiveWorkout` | Today > Start > Start today's workout (2); Today > Resume (1); Train > Carry on (2) | Log sets, rest, plates, finish | 10 controls, picker sheet, save-as-day dialog | |

### 1d. Progress screens

| Route | Paths in (depth) | Purpose | Size | Duplicates |
| --- | --- | --- | --- | --- |
| `WorkoutHistory` | Progress > Training stats (2) | History, volume per muscle, PRs | Long list | Named "Training stats" in one place and "history" in another |
| `WeeklyReport` | Progress > Weekly report (2) | Printable PDF of the week | 3 controls | |
| `CoachCheckIn` | More > Weekly check-in (2); Progress > Weekly check-in (2); Today focus card (1); weekly notification | The week at a glance and suggestions to apply | 9 controls, 2 drawers | **4 entry points** |
| `HealthConnect` | Progress > Health Connect (2) | Watch and scale data, workout calories switch | 4 controls | The workout-calories switch is also in Settings |
| `BodyMetrics` | Progress > Log weight (2); Progress weight card (2); Today focus card (1); tab tip | Log weight and measurements | 1 control plus fields | **4 entry points** |

### 1e. More screens

| Route | Paths in (depth) | Purpose | Size | Duplicates |
| --- | --- | --- | --- | --- |
| `Settings` | More > Settings (2) | Preferences | **32 controls, 12 headings**: Look and units (5 rows), Targets (4 + drawer of 2), Training (2), Water (weather), Allergens link, Food database link, Help, Advanced drawer (about 8) | Registered in Food and More graphs. Holds links to 4 other screens |
| `Privacy` | More > Privacy (2) | What leaves the phone | 5 sections, about 20 items | |
| `Backup` | More > Backup & sync (2); Settings banner (3) | Encrypted backup, device sync, export | 14 controls + sync section (7) | Registered in Food and More graphs |
| `AiSettings` | More > AI (2); Settings > Advanced > AI (4); Today, Food hub, Plan B, Recipe Hunter prompts | AI switch, keys, models, budget, cost, benchmark | 10 controls, 6 headings, plus usage and benchmark cards | **7 entry points**, registered in 3 graphs |
| `Help` | More > Help (2) | Tour replays and script docs | **9 rows** | Settings also has "Replay the tour" |
| `Profile` | More > Profile & goals (2); Today focus card (1); Your plan > Edit (3) | Body, goal, activity, training availability, baseline week | **9 controls, 8 headings**, Advanced section | Goal weight and rate are also in Settings |
| `YourPlan` | Profile > Build plan (3); Today baseline card (1) | Calories, macros, water and training with the reasons | 12 controls, 2 dialogs | Overlaps Settings > Targets |
| `ScriptReference` | Help rows (3); Food > More food tools (3); Train > More training tools (3); Programs (3); Meal plans menu (3); builder | The bundled script wiki | 2 controls | Registered in 3 graphs, **6 entry points** |

### 1f. Sheets and dialogs (grouped)

**Bottom sheets (21 call sites).**

| Sheet | Where it opens (depth) | Size and notes |
| --- | --- | --- |
| Add food | Today > Log food (1); Food > Log food (2); slot "+" | **6 tabs** (Recents, Search, Barcode, Photo, Quick add, My foods) plus portion step and allergen step |
| Allergen warning | Inside Add food and plan import | Blocks saving. Stays as is |
| Water and drinks | Today water card (1) | **9 controls, 5 headings**: add drink, today's entries, how the target adds up, daily target settings, quick-add drinks editor, Advanced (drink values, sweat rate). Several jobs in one sheet |
| Slot picker, Log entry actions | Today long-press (2) | 2 sheets |
| Choose a recipe for the week, eat-out "Find something that works", plan manager (2 sheets) | Meal plans (3 to 4) | 4 sheets inside the meal plan screen |
| Ingredient | Recipe editor | |
| Exercise picker, exercise form, exercise detail (2 call sites), builder exercise edit | Builder, workout, library (3 to 5) | 5 call sites for one exercise concept |
| Script diagnostics | Programs, plan import | |
| Meal template add, NFC write | Meal templates | |
| Menu source: add, review, detail (3 call sites) | Menu import | |
| AI model picker (2 call sites) | AI settings, Recipe Hunter | |

**Dialogs (25 call sites).** Most are confirmations and small number inputs: save as meal prep, protein type,
archive a program, save workout as a day (1), builder edits (4), import previews (2), plan manager (2), your-plan
overrides (2), food detail (2), backup server identity (2), allergen keywords (3), my allergens (1), benchmark
(1), onboarding (1), manual eat-out estimate. These do not need restructuring, only fewer parents.

## 2. Problems, with counts

### 2a. Duplicate entry points: 16 pairs

1. Weekly check-in: More row **and** Progress row (and the Today focus card, and the notification).
2. Backup: More row **and** a conditional banner at the top of Settings.
3. AI settings: More row, Settings > Advanced button, and 5 contextual prompts.
4. Replay the tour: Help row **and** Settings > Help.
5. My allergens: Food > Allergens **and** Settings > Allergens.
6. Food database: Food > More food tools **and** Settings > Food database.
7. Performance (jump tests): Train > More tools **and** Progress.
8. "Make one for me": Train hub row, the no-program card, the Programs screen, the Today workout card, Your plan.
9. Program templates: More tools drawer, no-program card, Programs screen.
10. Script help: Food drawer, Train drawer, Programs, Meal plan menu, builder, and 6 rows in Help.
11. Log food: Today button, Food hub button, 4 slot "+" buttons, Today tip, tab tip.
12. Add workout calories to the target: Settings > Advanced **and** Health Connect.
13. "Reword my check-in with AI": AI settings **and** Check-in settings.
14. Goal weight and rate of change: Settings > Targets **and** Profile > Your goal (and Your plan overrides).
15. Log weight: Today focus card, Progress button, Progress weight card, tab tip.
16. Recipe import: Recipes row **and** Recipe Hunter.

### 2b. Features reachable from 3 or more places: 12

AI settings (7), script docs (about 12 including the Help rows), "Make one for me" (5), program templates (3),
weekly check-in (4), log weight (4), profile and goals (3), allergen editing (3), food database (3), performance
tests (3), creating or picking an exercise (library, builder, workout), editing calorie and protein targets (3 screens: Settings, Profile, Your plan).

### 2c. Screens doing two or more jobs: 14

| Screen | Jobs |
| --- | --- |
| Meal plan | Viewer, plan manager (new, import, export), shopping list, cook and log, eat-out fallback (5) |
| Programs | Today's session, weekly sets, recent sessions, program list, add a program (5) |
| AI settings | Switch, keys, models, budget, cost, benchmark (6) |
| Water sheet | Log a drink, explain the target, edit the target, edit quick-add drinks, sweat lab (5) |
| Settings | Preferences, nutrition targets, water, links out to 4 screens (4) |
| Profile | Inputs, advanced options, baseline week, applied plans (4) |
| Progress | Charts, coach link, report links, data-source link (4) |
| Backup | Backup, device sync, export (3) |
| Food detail | View, edit, contribute (3) |
| Exercise library | Browse, detail, create and edit (3) |
| More | Settings hub, help, privacy statement, about (4) |
| Food hub | One action, five destinations, three buried tools (3) |
| Train hub | A workout card, three destinations, three buried tools (3) |
| Recipes | An "add" menu and a library (2) |

Two more screens exist only to hold two rows each: **Eating out** and **Allergens**.

### 2d. Settings scattered across 10 screens and sheets

| Where | What can be changed there |
| --- | --- |
| Settings | Weight and length units, theme, reduce motion, wallpaper colours; auto or manual target, goal weight, rate, protein, fat, calories; default rest, vibrate; weather water adjustment; bar weight, plates, add workout calories, USDA key, AI link (**5 groups plus an Advanced drawer, 12 headings**) |
| Profile | Build, goal, pace, activity, carb cycling, calorie floor |
| Your plan | Calorie, protein and fat overrides |
| Water sheet | Volume unit, daily target adjustments, quick-add drinks, drink values, sweat rate |
| AI settings | AI switch, budget, free models, 3 scheduled-AI switches, model per task |
| Food database | Open Food Facts search, contributing |
| Health Connect | Workout calories in the target |
| Check-in settings | Notification, AI reword |
| My allergens | Strictness and keywords |
| Backup | Schedule, server, device sync |

The unit setting is split: weight and length in Settings, volume in the water sheet. Weather sits in Settings
while the rest of water sits in the water sheet. The Settings screen's Advanced drawer hides the AI link and
the API keys. Three of these settings are changeable in two places (see duplicate pairs 12, 13 and 14).

### 2e. "More tools" junk drawers: 8

1. Food hub > More food tools (3 rows).
2. Train hub > More training tools (3 rows).
3. Settings > Show advanced settings (about 8 controls, including keys and the AI link).
4. Settings > Fine-tune protein, fat and calories.
5. Profile > Advanced.
6. Water sheet > Advanced.
7. Check-in settings.
8. Past check-ins.

The first two are the worst: they hide **My foods**, **Food database**, **Templates** and **Performance**, which are
real features, behind a disclosure with a generic name.

### 2f. Jargon: 22 terms

Baseline week, Plan B, Recipe Hunter, Meal templates (and NFC tag), Menu import and Menu sources, Cook mode,
Program script and Food script, Progression rules, AMRAP, RPE, Estimated max, Performance (jump tests), Health
Connect, TDEE and "adaptive" targets, Carb cycling, Calorie floor, Hydration factors, Sweat rate, FoodData
Central, Open Food Facts, OpenRouter (and "model per task"), Sync (device sync).

### 2g. Common tasks deeper than 3 taps

37 tasks were walked through in the code (table in section 3). **10** take 4 or more taps. Four are everyday
tasks: cook a recipe (4), add a restaurant menu (4), change an exercise in my program (4), and change the calorie
or protein target (4). The user's own main import, a meal plan, is 4 taps.

### 2h. What a new user meets in the first minute

After the nine-step welcome, Today shows **7 named things** (focus card, baseline week, the ring with its 3
macros, water, workout, insight, meals) under **5 tabs** (Today, Food, Train, Progress, More). That is **12
concepts**, then a coach-mark tour and a tip on each tab visit. "Baseline week" and "Insight" are concepts the
person has not asked for yet. The Food tab opens with 5 rows plus 3 hidden, Train with a workout card plus 6, and
More with 7. Allergens, the one feature with a safety job, sits as row 5 of 5 on Food, not on the surface.

### 2i. Other things worth knowing

* **Today does not show the planned meal.** SPEC-v3 says it should. Logging the plan means Food > Meal plans >
  Log this meal (3 taps).
* **Weigh-in is not on Today.** It is reachable from the focus card only on the days the coach picks it.
* **Cross-tab links change the pill.** Today opens `BodyMetrics`, `Profile` and `CoachCheckIn` inside the Progress or More
  graph, so the pill highlight jumps away from Today. Registering the same screen in several graphs
  (`AiSettings` x3, `ScriptReference` x3, `Settings` x2, `Backup` x2, `Performance` x2) is a workaround for the
  same problem.
* **Food graph holds `Settings` and `Backup` registrations with no visible link from a Food screen.** Dead
  registrations; harmless but a sign that "where does this live" has no single answer.
* **Exercise detail has no photo and no muscle map.** It shows name, muscles (text), equipment, rest, and history.
  The entity already has `primaryMuscle`, `secondaryMuscles` and `videoUri`, so a muscle map can be drawn from data
  the app already has; a photo needs a new field and a source.

## 3. Task depth, old and new

Tap depth is from Today. "New" is the design in `docs/IA-v4.md`. It is a reading of the code and the design, not a
stopwatch on a phone.

| # | Task | Old path | Old | New path | New |
| --- | --- | --- | --- | --- | --- |
| 1 | Log food (search) | Today > Log food | 1 | Today > Log food | 1 |
| 2 | Scan a barcode | Today > Log food > Barcode tab | 2 | Today > scan icon | 1 |
| 3 | Log water | Today > water chip | 1 | Today > water chip | 1 |
| 4 | Weigh in | Progress > Log weight | 2 | Today > Weight tile | 1 |
| 5 | Start today's workout | Today > Start (opens Train) > Start today's workout | 2 | Today > Start (goes straight in) | 1 |
| 6 | Start an empty workout | Train > Or start an empty workout | 2 | Train > Empty workout | 2 |
| 7 | See the weight trend | Progress | 1 | Progress | 1 |
| 8 | Weekly check-in | More > Weekly check-in | 2 | Progress > check-in card | 2 |
| 9 | Log the planned meal | Food > Meal plans > Log this meal | 3 | Today > Meals > planned meal > Log | 1 |
| 10 | See this week's plan | Food > Meal plans | 2 | Food > Meal plan | 2 |
| 11 | See the shopping list | Food > Meal plans > scroll > Shopping list | 3 | Food > Meal plan > Shopping tab | 3 |
| 12 | Cook a recipe | Food > Recipes > recipe > Cook | 4 | Food > Recipes > Cook button on the row | 3 |
| 13 | Add a recipe by hand | Food > Recipes > Add a recipe | 3 | Food > Recipes > New recipe | 3 |
| 14 | Import a recipe from a link | Food > Recipes > Import from a link | 3 | Food > Recipes > Import from link | 3 |
| 15 | Find a safe place to eat | Food > Eating out > Plan B | 3 | Food > Eating out | 2 |
| 16 | Add a restaurant menu | Food > Eating out > Menu import > Add source | 4 | Food > Eating out > Add a menu | 3 |
| 17 | Edit my allergens | Food > Allergens > My allergens | 3 | Food > allergen strip | 2 |
| 18 | Edit one allergen's keywords | Food > Allergens > My allergens > allergen | 4 | Food > allergen strip > allergen | 3 |
| 19 | Create a program by hand | Train > Programs > Build a program | 3 | Train > Programs > New program | 3 |
| 20 | Make me a program | Train > Make one for me | 2 | Train > Programs > New program > Make me one (2 when you have none) | 3 |
| 21 | Change an exercise in my program | Train > Programs > Edit > exercise | 4 | Train > active program > exercise | 3 |
| 22 | Look up an exercise | Train > Exercises > result | 3 | Find > result | 2 |
| 23 | Add a custom exercise | Train > Exercises > Add | 3 | Train > Exercises > Add | 3 |
| 24 | See PRs and training stats | Progress > Training stats | 2 | Progress > Training | 2 |
| 25 | Change units or theme | More > Settings | 2 | You > Look and units | 2 |
| 26 | Change calorie or protein target | More > Settings > Targets > Fine-tune | 4 | You > Goals and plan > Edit | 3 |
| 27 | Change the water target | Today > water > scroll to Daily target settings | 2 | Today > water gear | 2 |
| 28 | Measure my sweat rate | Today > water > Advanced > Measure | 4 | Today > water gear > Advanced | 3 |
| 29 | Add workout calories to the target | More > Settings > Show advanced > toggle | 4 | You > Nutrition and water > toggle | 3 |
| 30 | Back up now / set up backup | More > Backup & sync | 2 | You > Backup and data | 2 |
| 31 | Turn on AI and add a key | More > AI | 2 | You > AI | 2 |
| 32 | Pick the model for one AI task | More > AI > Model per task > picker | 4 | You > AI > Model per task > picker | 4 |
| 33 | Replay the tour | More > Help > Replay the tour | 3 | You > Help and tour | 2 |
| 34 | Read the script docs | More > Help > All script docs | 3 | You > Help and tour > Scripts (1 from an editor's ? icon) | 3 |
| 35 | Export the weekly report PDF | Progress > Weekly report | 2 | Progress > menu > Weekly report | 2 |
| 36 | Write a meal to an NFC tag | Food > Meal templates > meal > Write | 4 | Food > My foods and meals > meal > Write | 4 |
| 37 | Import a meal plan | Food > Meal plans > menu > Import | 4 | Food > Meal plan > menu > Import (3 from the empty state) | 4 |

Total taps over the 37 tasks: **102 now, 87 in v4**. Tasks at 4 or more taps: **10 now, 3 in v4**, none of them
everyday. One task gets worse (making a program when you already have one goes from 2 to 3) and is called out in
the risks.
