# Intake v4: a calmer structure

Design only. No app code changes. The numbers come from `docs/IA-AUDIT.md`; the clickable mockup is
`design/intake-mockup-v3.html` (open it in a browser; tap the pill, the search icon and the avatar).
Nothing here has been built, and the mockup has not been seen on a real phone.

## In plain words

1. **Four tabs, not five: Today, Food, Train, Progress.** "More" goes away. Your profile, goals, settings and
   help sit behind your avatar at the top right, under the name **You**. The floating pill and slide-to-switch stay.
2. **Today is five cards, always in the same order, and you can hide any of them.** A new person starts with three.
   Logging food, water, a weigh-in or starting a workout is one tap from Today.
3. **Every feature has one home.** The "More food tools" and "More training tools" drawers are gone. Every other
   place that mentions a feature is only a shortcut to it.
4. **One Settings, in six clear groups.** Each feature keeps a single gear or "⋯" for its own options, and nothing
   about water, targets or AI is spread over three screens any more.
5. **A search icon, "Find anything", on every tab.** It finds screens, settings, foods, recipes, exercises and
   actions, so rarely used things can sit deeper without being lost. **Allergen warnings do not move**: a strip at
   the top of Food shows what you avoid, and the block-before-saving sheet is unchanged.

Nothing is removed. Every screen in the audit has a place in the table below.

## 1. The new map

```
Pill (4)                         Top bar on each tab root: title · search icon · avatar
├─ Today     5 cards, hideable   avatar -> You
│    Focus · Calories (+Log food, scan) · Water | Weight · Workout · Meals
├─ Food      Log food · Scan · allergen strip
│    Meal plan · Recipes · Eating out · My foods and meals
├─ Train     Start card (next workout / carry on / empty)
│    Programs · Exercises
└─ Progress  Check-in card · Weight and body · Nutrition · Training
     menu: Weekly report PDF

You (avatar)                     reached from every tab, and by search
├─ Goals and plan                (Profile + Your plan, one screen)
├─ Settings, six groups          Look and units · Nutrition and water · Training
│                                · Backup and data · AI (optional) · Privacy
└─ Help and tour                 replay tour, script docs, about
```

Where things went that used to live in a drawer or a hub:

| Was | Now |
| --- | --- |
| Food > More food tools > My foods | Food > **My foods and meals** (one row, a Foods / Meals switch) |
| Food > Meal templates | Same screen as My foods (the Meals side) and the **Mine** tab of Add food |
| Food > More food tools > Food database | You > Settings > Backup and data > Food database |
| Food > More food tools > Food script help | "?" in the Meal plan menu, and You > Help and tour > Scripts |
| Food > Eating out (a hub of two) > Plan B, Menu import | **Eating out** is one screen: find a place, saved options, saved menus |
| Food > Allergens (a hub of two) | **Allergen strip** on Food opens one Allergens screen (my list, strictness, a link to the hidden-ingredient guide) |
| Train > More training tools > Templates | Inside **New program** on the Programs screen |
| Train > More training tools > Performance | Progress > Weight and body > **Jump tests** |
| Train > More training tools > Program script help | "?" in the program editor, and You > Help and tour > Scripts |
| Train hub > Make one for me, Programs > Make one, no-program card | One **New program** choice list: Build, Make me one, From a template, Import |
| More > Weekly check-in | Progress, first card (Today still links to it when it is due) |
| More > Profile & goals, Settings > Targets, Your plan | You > **Goals and plan** |
| More > Backup, AI, Privacy, Settings | You > Settings groups |
| More > Help | You > Help and tour |

### Tap depth for the top 15 tasks

From Today, the pill switch counts as one tap. The full 37-task table is in the audit.

| # | Task | Now | v4 | v4 path |
| --- | --- | --- | --- | --- |
| 1 | Log food | 1 | 1 | Today > Log food |
| 2 | Scan a barcode | 2 | **1** | Today > scan icon |
| 3 | Log water | 1 | 1 | Today > water chip |
| 4 | Weigh in | 2 | **1** | Today > Weight tile |
| 5 | Start today's workout | 2 | **1** | Today > Start, straight into the session |
| 6 | See how I'm doing | 1 | 1 | Progress |
| 7 | Weekly check-in | 2 | 2 | Progress > check-in card (1 from Today when it is due) |
| 8 | Log the planned meal | 3 | **1** | Today > Meals > planned meal > Log |
| 9 | See this week's plan | 2 | 2 | Food > Meal plan |
| 10 | Find a safe place to eat | 3 | **2** | Food > Eating out |
| 11 | Edit my allergens | 3 | **2** | Food > allergen strip |
| 12 | Change an exercise in my program | 4 | **3** | Train > active program > exercise |
| 13 | Look up an exercise | 3 | **2** | Find > result |
| 14 | Change units or theme | 2 | 2 | You > Look and units |
| 15 | Change my calorie or protein target | 4 | **3** | You > Goals and plan > Edit |

Over all 37 audited tasks: taps added up go from **102 to 87**, and tasks at 4 or more taps go from **10 to 3**
(import a meal plan, write an NFC tag, pick an AI model: all rare). One task gets longer: "Make me a program"
when you already have a program goes from 2 to 3 taps, because the four entry points collapse into one list
(a person with no program still sees the choices on Train, at 2).

## 2. The decisions

### 2a. Four tabs, and You behind the avatar

Options considered:

| Option | Verdict |
| --- | --- |
| A. Keep five: Today, Food, Train, Progress, You | Calm enough, but "You" would hold only settings that most people open a few times in the app's life, and it spends a tab slot on them |
| B. Four tabs, Progress folded into You | Puts charts and a settings list on one screen. That is two jobs, and "how am I doing" would sit under a label that does not say so |
| **C. Four tabs, You behind the avatar** | **Chosen.** The four tabs are the four things people do daily: eat, train, check progress, and the day itself. Settings are rare, so they are one tap away from any tab but do not take a slot |

Why it is calmer: the pill is shorter, so each target is wider and slide-to-switch is easier to land. A
beginner sees the whole map in a glance. The avatar is a familiar pattern, and it shows the person's initial, so
it reads as "my stuff". The avatar sits in the same place on every tab root. It opens a pushed screen with its
own back arrow, not a fifth tab.

If the user disagrees, A is a one-line change to the pill (a fifth item opening the same screen).

### 2b. Today: five cards, fixed order, all hideable

| # | Card | Contents | Notes |
| --- | --- | --- | --- |
| 1 | **Focus** | The one thing to do today (coach rule). Also carries the first-week baseline progress and the optional AI insight as a second line | Replaces three cards: focus, baseline, insight |
| 2 | **Calories** | Ring with 3 macros, "400 kcal left", **Log food** button and a scan icon | The hero. Date arrows stay |
| 3 | **Water and weight** (two tiles side by side) | Water: +250 / +500 chips and a gear. Weight: last weigh-in and a **Weigh in** button | Counts as one card slot. Gear opens You > Nutrition and water |
| 4 | **Workout** | Next workout with **Start** (goes straight into the session), or **Carry on**, or "Make a program" | |
| 5 | **Meals** | One card, four compact rows (Breakfast, Lunch, Dinner, Snacks) with entries and "+". The planned meal shows on its slot with **Log** and **Cook** | Replaces four separate slot cards |

Rules:
* The order never changes, and a hidden card leaves no gap. **Edit Today** at the bottom opens a sheet of five
  switches. It is the only place that setting lives.
* **Defaults depend on the welcome answers.** "I don't train" hides Workout. Weight stays hidden until the first
  weigh-in. A new person sees three cards: Calories, Water, Meals. The Focus card appears once there is something
  to say.
* If everything is hidden, Today shows one line, "Nothing here. Edit Today", and the pill still works.
* The tour and tips shrink with it: one coach mark per visible card.

### 2c. One home per feature, and what "deep-link" means

Rule: a feature has one screen that owns its data and options. Every other row that mentions it is a plain link
(same label, no copy of its settings), or removed.

| Feature | Home | Links in |
| --- | --- | --- |
| Weight and body | Progress > Weight and body | Today Weight tile, Focus card |
| Weekly check-in | Progress, first card | Today Focus card, notification |
| Water log | Today water chips and sheet | Search |
| Water settings | You > Settings > Nutrition and water | Today water gear, "How the target adds up" |
| Targets and goals | You > Goals and plan | Settings > Nutrition and water (a single link row), Today Focus card |
| Allergens | Food > allergen strip > Allergens | Add food footer, plan import preview, Search |
| Food database and keys | You > Settings > Backup and data | Food detail "Contribute" link |
| Health Connect | You > Settings > Backup and data | Progress shows its data |
| Jump tests | Progress > Weight and body | Search |
| Programs and generator | Train > Programs > New program | Today workout card "Make a program" |
| Exercises | Train > Exercises | Pickers in the builder and the workout, Search |
| Script docs | You > Help and tour > Scripts | A "?" in the editors (Meal plan, program editor, recipe import) |
| AI settings | You > Settings > AI | Contextual "Turn on AI" prompts |
| Replay the tour | You > Help and tour | none |

### 2d. Short hubs

| Hub | Primary items | "Everything else" |
| --- | --- | --- |
| Food | Meal plan, Recipes, Eating out, My foods and meals (4) + Log food, scan, allergen strip | None needed. The drawer is gone because nothing is left to put in it |
| Train | Programs, Exercises (2) + the Start card | None needed |
| Progress | Check-in, Weight and body, Nutrition, Training (4 cards) | Menu: Weekly report |
| You | Goals and plan, Settings (6 groups), Help and tour | Each settings group has its own **Advanced** disclosure |

A hub needs an "All …" list only when it has more than about four things. None of the four does, which is the
point: the long tail went into the right owner screen (Allergens, Eating out, My foods and meals, Programs) instead
of into a drawer.

### 2e. One Settings

It is the second block of the You screen, six rows. Each opens one page with a short list and, if needed, one
collapsed **Advanced** section.

| Group | Takes in |
| --- | --- |
| **Look and units** | Weight, length and volume units (volume used to live in the water sheet), theme, reduce motion, wallpaper colours |
| **Nutrition and water** | Target mode (automatic or by hand), fine-tune protein and fat, calorie floor, carb cycling, add workout calories (the Settings and Health Connect copies become one), water target adjustments, weather, drink values, sweat rate, quick-add drinks. Goal weight and rate stay in Goals and plan, with a link row here |
| **Training** | Default rest, vibrate, bar weight, plates |
| **Backup and data** | Backup and sync, export and import, Health Connect, Food database and USDA key, Open Food Facts search and contribute |
| **AI (optional)** | Master switch, key, budget, free models, scheduled AI, models per task; usage, cost and the benchmark under Advanced. "Reword my check-in" lives only here |
| **Privacy** | What leaves this phone. The statement stays one tap away because it is a feature |

Feature gears and "⋯" menus (the only other place options live): Today water gear, Meal plan menu (new, import,
export, script help), Programs menu, Recipes menu (Recipe Hunter), Progress menu (weekly report), Check-in gear
(notification), Allergens gear (strictness).

The water sheet on Today keeps four things: add a drink, today's entries, how the target adds up (read-only),
and a gear. Its settings, drink editor and sweat lab move to You > Nutrition and water.

### 2f. Find anything: yes, with limits

**Decision: adopt it as a search icon in the top bar of every tab root. Do not hide it behind a long-press on the
pill.** A long-press-and-drag is the same gesture as slide-to-switch, and a hidden gesture teaches nothing.

It earns its place because the structure above is deliberately shallow-and-narrow: we demote rare things and
rely on search as the safety net. It also makes "where does X live?" cheap to answer for a bodybuilder with 200
exercises, 40 foods and 12 programs.

* Results are grouped: **Actions** (Log water, Weigh in, Import a meal plan), **Screens and settings** ("Water
  target", "Backup"), **Foods and meals**, **Recipes**, **Exercises**, **Programs**.
* Local and deterministic. No network, no AI, no key. A static index of destinations with synonyms
  ("macros" finds Goals and plan, "TDEE" finds Goals and plan) plus Room `LIKE` queries on names.
* Allergens: a food result shows the allergen status chip, and tapping it runs the normal check.
* Cost: about 120 index entries to keep current (a unit test can fail the build when a route has no entry),
  one screen. If it feels like too much, it is the easiest piece to drop without touching the rest.

### 2g. Allergens stay prominent

* A strip at the top of Food: "Avoiding milk, peanuts, sesame · Edit". With none set it says "No allergens set.
  Add yours" in amber, so the absence is visible too.
* The Add food sheet keeps its footer line and the full-screen block when something matches. A model or an
  import can still only raise a warning.
* Search shows the same chip next to foods. Nothing about matching, tiers or the "never downgrade" rule changes.
* The Allergens hub of two rows is gone. The screen is now the list itself, with strictness in a gear and the
  hidden-ingredient guide as a linked row.

### 2h. Jargon, renamed

| Now | v4 label |
| --- | --- |
| Baseline week | Your first week |
| Plan B | Eating out: find a safer place |
| Menu import, Menu sources | Restaurant menus |
| Meal templates (and NFC tag) | Saved meals (with "Write to a tag" inside) |
| Recipe Hunter | Find recipes (AI, optional) |
| Cook mode | Cook (step by step) |
| Performance | Jump tests |
| Program script, Food script | "Write it as text", with the word script only in Help |
| Progression rules | How the weight goes up |
| Estimated max | Estimated 1-rep max |
| AMRAP | As many reps as you can (AMRAP) |
| TDEE, "adaptive" | Your real maintenance calories |
| OpenRouter, "model per task" | AI key, "Which model for what" |
| Sync | Sync between your devices |
| FoodData Central, Open Food Facts | Food database (names in the credits line) |
| Carb cycling, Calorie floor, Hydration factors, Sweat rate | Kept, but only under Advanced, each with a one-line "what this does" |
| Health Connect | Kept (it is the Android name), with "Watch and scale data" under it |
| RPE | Kept, with a tap-for-explanation |

## 3. Old to new, every screen

"Stays" means the screen is kept as is, only its parents change. The 21 sheets and 25 dialogs are covered in the
last two blocks.

### Routes

| Old route | Verdict | New home and notes |
| --- | --- | --- |
| `Today` | **Rebuilt** | Five cards (section 2b) |
| `Food` | **Slimmed** | Log food, scan, allergen strip, 4 rows. Drawer removed |
| `Train` | **Slimmed** | Start card, 2 rows |
| `Progress` | **Regrouped** | Check-in, Weight and body, Nutrition, Training. Report links become a menu |
| `More` | **Becomes You** | Avatar, not a tab |
| `Onboarding` | **Stays**, copy updated | Four tabs, avatar, Edit Today. Sets Today defaults |
| `MealPlans` | **Stays**, Week / Shopping switch | Food > Meal plan. Menu holds new, import, export, "?" |
| `Recipes` | **Stays** | List with New recipe and Import from link; Recipe Hunter in the menu |
| `RecipeHunter` | **Demoted** | Recipes menu, "Find recipes (AI, optional)" |
| `RecipeImport`, `RecipeEditor`, `RecipeDetail` | **Stay** | Recipes. Detail and list rows get a Cook button |
| `CookMode` | **Stays** | Cook button from the Meals card, the plan, and recipe rows |
| `EatingOut` | **Becomes the screen** | One screen: find a place (was `PlanB`), saved options, saved menus (was `MenuSources`) |
| `PlanB` | **Merged** into Eating out | Section at the top of Eating out |
| `MenuSources` | **Merged** into Eating out | "Saved menus" section with Add a menu |
| `MealTemplates` | **Merged** | My foods and meals > Meals; Mine tab in Add food |
| `MyFoods` | **Merged and promoted** | Food > My foods and meals > Foods |
| `FoodDetail` | **Stays** | From My foods and meals and from Add food |
| `FoodData` | **Moved** | You > Settings > Backup and data > Food database |
| `Allergens` (hub of two) | **Removed** | The allergen strip opens the Allergens screen |
| `MyAllergens` | **Renamed** Allergens | The list, strictness gear, guide row |
| `AllergenKeywords` | **Stays** | Drill-in from an allergen |
| `AllergenReference` | **Stays** | Linked row on Allergens, "Hidden-ingredient guide" |
| `Programs` | **Slimmed** | Active program first, list below, New program (Build, Make me one, From a template, Import) |
| `ProgramBuilder` | **Stays** | From the active program row |
| `ProgramTemplates`, `ProgramGenerator` | **Stay**, one entry | Choices in New program. The Today workout card keeps one "Make a program" link |
| `ExerciseLibrary` | **Stays**, detail becomes a page | Photo, muscle map, history (section 4) |
| `Performance` | **Moved** | Progress > Weight and body > Jump tests |
| `ActiveWorkout` | **Stays** | Info button per exercise opens the detail page. Rest ring, plates unchanged |
| `WorkoutHistory` | **Renamed**, moved | Progress > Training ("Training log") |
| `WeeklyReport` | **Demoted** | Progress menu |
| `CoachCheckIn` | **Moved** | Progress first card; settings behind its gear; past check-ins inside |
| `HealthConnect` | **Moved** | You > Settings > Backup and data |
| `BodyMetrics` | **Becomes a small sheet** for weight; measurements in Progress | Today Weight tile and Progress |
| `Settings` | **Flattened** into You | Six groups (2e) |
| `Privacy` | **Moved** | You > Settings > Privacy |
| `Backup` | **Moved** | You > Settings > Backup and data |
| `AiSettings` | **Moved** | You > Settings > AI. Usage, cost, benchmark under Advanced |
| `Help` | **Trimmed** | You > Help and tour: Replay tour, Show tips again, Scripts (one row, then the wiki), About |
| `Profile` + `YourPlan` | **Merged** | You > Goals and plan: Your plan on top, "About you" below |
| `ScriptReference` | **Stays** | Reached from "?" in editors and from Help |

### Sheets

| Sheet | Verdict |
| --- | --- |
| Add food | **Four tabs, not six**: Find (recents and search together), Scan, Mine (foods, saved meals, recipes), Quick add. Photo estimate becomes an icon inside Find, only when AI is on |
| Allergen warning | **Stays exactly as is** |
| Water and drinks | **Slimmed** to add, entries, how it adds up, gear (settings move to You) |
| Slot picker, Log entry actions | Stay |
| Meal plan sheets (choose recipe, eat-out finder, plan manager x2) | Stay. The eat-out finder links to Eating out |
| Ingredient, Script diagnostics, Meal template add and NFC write, Menu source add and review (3) | Stay |
| Exercise picker, form, detail, builder edit (5 call sites) | **Detail becomes the shared page**; the others stay |
| AI model picker (2) | Stay |

### Dialogs (25)

All stay. They are confirmations and number inputs. None creates a navigation problem, and several lose a
parent screen with the merges above (your-plan overrides, allergen keywords, backup identity).

## 4. The new screens in the mockup

* **Exercise detail** (photo and muscle map). Today the detail is a sheet with text. The page puts a photo (or a
  placeholder when there is none) on top, a front and back muscle map highlighting primary and secondary muscles,
  then equipment and rest, last results, and Edit. The muscle map can be drawn from `primaryMuscle` and
  `secondaryMuscles`, which already exist. A photo needs a new `photoUri` (in `MIGRATION_2_3`, while v3 is
  unreleased) and a source for built-in exercises; **there is no bundled image set**, and licensing and APK size
  need a decision (see open questions). The mockup shows labelled placeholders.
* **Meal plan week.** Week strip, today's meal first, a Week / Shopping switch, one menu.
* **Active workout.** Unchanged in behaviour. The visible change is the info button per exercise, the "?" for how
  the weight is chosen, and one overflow for plates and notes.

## 5. How to build it (for a later task, not done here)

Order that keeps each step small and reviewable:
1. **Navigation only.** Remove the drawers and the two pass-through hubs, move rows, rename, add the avatar and
   You. No data change. Also fix the cross-tab pill jump by making You and the shared screens root-level
   destinations instead of registering them in several graphs.
2. **Today cards and Edit Today.** Visibility flags go in DataStore (and the portable settings allow-list, so a
   backup carries them).
3. **Merges** (Eating out, My foods and meals, Allergens, Goals and plan).
4. **Find anything** (`domain/search`, unit-tested, with a "every route has an entry" test).
5. **Exercise detail page**, then the photo field.

No Room change is needed until step 5. Everything respects the existing rules: offline first, no AI unless the
person asks, soft deletes, allergen checks local and deterministic.

## 6. Risks

| Risk | Why it matters | Mitigation |
| --- | --- | --- |
| Muscle memory | The user has just learned five tabs and More | Four tabs is a removal, not a move. The first launch after the update shows a one-time "Where did More go?" note that points at the avatar |
| Settings are less discoverable behind an avatar | Some people never tap avatars | Search covers it; Help and the welcome flow mention it; the avatar shows initials so it looks tappable |
| "Fewer places" can feel like "features removed" | The user said, "I like that the app does so much" | The old to new table above is shipped with the change as a "what moved" note in Help; search finds old names (synonyms for "Meal templates", "Plan B", "Performance") |
| Search index drifts | A new screen without an entry is invisible to search | Unit test that fails when a route has no index entry |
| Merged screens get long | Eating out and Goals and plan each absorb two screens | Sections with headings, collapsed "Advanced", and a sticky first action |
| Hiding Today cards by default | A new person may not know Workout exists | The Train tab is always there; Focus card suggests it; Edit Today is a visible text button |
| "Make me a program" gets one tap longer for people who already have a program | Honest regression | Newcomers (no program) still see the choices on Train; Today's workout card keeps a link |
| Progress becomes a summary that people tap into | Three or four charts may be too little for a bodybuilder | Every card opens to the full detail that exists today (training stats, volume per muscle, PRs); nothing is dropped |
| Cross-graph navigation | You and shared screens must work from every tab without moving the pill | Make them root-level destinations (step 1) |
| Onboarding, tour and tips copy mention More | Stale text | Update the nine steps, tour marks and tab tips together with step 1 |
| The mockup is not the app | It was not seen on a device, and animation, haptics and sheet behaviour are not modelled | Build step 1 and 2 first, then look at it on the user's phone before the rest |
| Wear OS and sync server | Untouched by this design | No change expected; confirm the watch's "Today" mirrors only data, not layout |

## 7. Open questions for the user

1. **Four tabs with You behind the avatar, or five with a You tab?** I recommend four. Does Settings behind the
   avatar sound right, or do you want a visible tab?
2. **Is "You" the right name?** Alternatives: "Me", "Profile", or just a gear.
3. **Should Find anything ship in the first version, or later?** It is the one piece that adds a new thing rather
   than removing one.
4. **Today defaults.** Is three cards for a new person (Calories, Water, Meals) right, and should Weight sit on
   Today at all, given you can weigh in from Progress?
5. **Is Progress right for jump tests?** They are an athlete's measurement. We could hide them until switched on.
6. **Exercise photos.** Where should they come from: ones you add yourself (needs no licence, empty at first), a
   free licensed set bundled in the APK (adds size, needs a licence check), or none? The muscle map needs no photos.
7. **Make-a-program placement.** Are you happy that it moves into one New program list, one tap longer for
   someone who already has a program?
8. **Allergen strip.** Do you want it on Food only, or also as a thin line on Today?
9. **How much of the old names to keep?** "Plan B" and "Recipe Hunter" are yours. Rename them as proposed, or keep
   the names and add a plain-words subtitle?
10. **The weekly check-in's home.** Progress (proposed) or Today only, as the Focus card on check-in day?
