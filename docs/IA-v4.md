# Intake v4: a calmer structure

Design only. No app code changes. The numbers come from `docs/IA-AUDIT.md`; the clickable mockup is
`design/intake-mockup-v3.html` (open it in a browser; tap the pill, the search icon and the avatar).
Nothing here has been built, and the mockup has not been seen on a real phone.

## In plain words

1. **Four tabs, not five: Today, Food, Train, Progress.** "More" goes away. Your profile, goals, settings and
   help sit behind your avatar at the top right, under the name **You**. The floating pill and slide-to-switch stay.
2. **Today is four cards in a fixed order, read top to bottom: what is left, what is next, quick log, what I ate.**
   You can hide any of them, and a new person starts with three. Logging food, water, a weigh-in or starting a
   workout is one tap from Today.
3. **Food is one screen, not eight.** Plan, Recipes, Saved meals and Eating out become four sections of the Food
   tab, with Log food on top. My foods moves inside Log food. Database credits and script help move to Settings and
   Help. The "More tools" drawers are gone, and every other mention of a feature is only a shortcut to its home.
4. **One Settings, in six clear groups.** Backup and sync become one simple "Sync and export" screen, and the weekly check-in and report become one screen. Each feature keeps a single gear or "⋯", and nothing
   about water, targets or AI is spread over three screens any more.
5. **A search icon, "Find anything", on every tab.** It finds screens, settings, foods, recipes, exercises and
   actions, so rarely used things can sit deeper without being lost. **Allergen warnings do not move**: a strip at
   the top of Food shows what you avoid, and the block-before-saving sheet is unchanged.

Nothing is removed. Every screen in the audit has a place in the table below. Room is reserved in Train for a
weekly muscle map, an exercise page with a photo and a diagram, and an equipment picker with icons (section 4).

## 1. The new map

```
Pill (4)                         Top bar on each tab root: title · search icon · avatar
├─ Today     4 cards, hideable   avatar -> You
│    Calories (+Log food, scan) · Next up · Water | Weight · Meals
├─ Food      ONE screen: allergen strip · Log food · scan
│    sections: Plan · Recipes · Meals · Eating out
│    (My foods is a tab inside Log food)
├─ Train     Start card · This week muscle map
│    Programs · Exercises (photo, diagram, equipment picker)
└─ Progress  Weekly check-in card · Weight and body · Nutrition · Training

You (avatar)                     reached from every tab, and by search
├─ Goals and plan                (Profile + Your plan, one screen)
├─ Settings, six groups          Look and units · Nutrition and water · Training
│                                · Sync and export · AI (optional) · Privacy
└─ Help and tour                 replay tour, script docs, about
```

Where things went that used to live in a drawer or a hub:

| Was | Now |
| --- | --- |
| Food > Meal plans | Food > **Plan** section (the Food tab opens on it) |
| Food > Recipes | Food > **Recipes** section |
| Food > Meal templates | Food > **Meals** section (saved meals, NFC), and the **Mine** tab of Log food |
| Food > More food tools > My foods | **Mine** tab inside Log food (foods, saved meals, recipes). Editing and sharing a food opens from there |
| Food > More food tools > Food database & credits | You > Settings > Nutrition and water > Data sources > Food database (credits line included) |
| Food > More food tools > Food script help | "?" in the Plan menu, and You > Help and tour > Scripts |
| Food > Eating out (a hub of two) > Plan B, Menu import | Food > **Eating out** section: find a place, saved options, saved menus |
| Food > Allergens (a hub of two) | **Allergen strip** on Food opens one Allergens screen (my list, strictness, a link to the hidden-ingredient guide) |
| Train > More training tools > Templates | Inside **New program** on the Programs screen |
| Train > More training tools > Performance | Progress > Weight and body > **Jump tests** |
| Train > More training tools > Program script help | "?" in the program editor, and You > Help and tour > Scripts |
| Train hub > Make one for me, Programs > Make one, no-program card | One **New program** choice list: Build, Make me one, From a template, Import |
| More > Weekly check-in, Progress > Weekly report | **One screen, "Weekly check-in"** (summary, suggestions, export/print). Entry: the Progress card, with a Today link when it is due |
| More > Profile & goals, Settings > Targets, Your plan | You > **Goals and plan** |
| More > Backup & sync, AI, Privacy, Settings | You > Settings groups. Backup & sync becomes **Sync and export** (one simple screen) |
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
| 7 | Weekly check-in (and its report) | 2 | 2 | Progress > Weekly check-in card (1 from Today when it is due) |
| 8 | Log the planned meal | 3 | **1** | Today > Next up > Log |
| 9 | See this week's plan | 2 | **1** | Food (opens on the Plan section) |
| 10 | Find a safe place to eat | 3 | **2** | Food > Eating out |
| 11 | Edit my allergens | 3 | **2** | Food > allergen strip |
| 12 | Change an exercise in my program | 4 | **3** | Train > active program > exercise |
| 13 | Look up an exercise | 3 | **2** | Find > result |
| 14 | Change units or theme | 2 | 2 | You > Look and units |
| 15 | Change my calorie or protein target | 4 | **3** | You > Goals and plan > Edit |

Over all 37 audited tasks: taps added up go from **102 to 84**, and tasks at 4 or more taps go from **10 to 2**
(write an NFC tag, pick an AI model: both rare). One task gets longer: "Make me a program"
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

### 2b. Today: four cards, one reading order

The user said Today "isn't bad" but is "a little jumbled". The jumble is real: up to 13 blocks, three of them
(focus, baseline, insight) saying "here is something to do", a workout card far from the planned meal, and four
separate meal cards. v4 gives the page one reading order, **what is left, what is next, quick log, what I ate**,
and one card per question.

| # | Card | Answers | Contents |
| --- | --- | --- | --- |
| 1 | **Calories** | What is left today? | Ring with 3 macros, "400 kcal left", **Log food** and a scan icon. Date arrows stay |
| 2 | **Next up** | What should I do next? | One list, at most three rows, in the order they happen: the coach's one thing (with the first-week progress and the optional AI insight as its second line), the planned meal (**Log** and **Cook**), today's workout (**Start**, straight into the session) |
| 3 | **Water and weight** (two tiles) | Quick log | Water: +250 / +500 and a gear. Weight: last weigh-in and **Weigh in** |
| 4 | **Meals** | What did I eat? | One card, four compact rows with entries and "+" |

Rules:
* Replaces up to nine old blocks (focus, baseline, insight, workout, 4 slot cards, notes) with the same
  information in fewer containers. "Next up" is the only new idea: it merges three cards that all meant "do this".
* The order never changes, and a hidden card leaves no gap. **Edit Today** at the bottom opens four switches and is
  the only place that setting lives.
* **Defaults depend on the welcome answers.** "I don't train" removes the workout row from Next up. Weight stays
  hidden until the first weigh-in. A new person sees three cards: Calories, Water, Meals. Next up appears only
  when it has something to say.
* If everything is hidden, Today shows one line, "Nothing here. Edit Today", and the pill still works.
* One coach mark per visible card. Every tab root uses the same top bar and the status-bar inset (see the bug in
  the audit, 2j).

### 2c. One home per feature, and what "deep-link" means

Rule: a feature has one screen that owns its data and options. Every other row that mentions it is a plain link
(same label, no copy of its settings), or removed.

| Feature | Home | Links in |
| --- | --- | --- |
| Weight and body | Progress > Weight and body | Today Weight tile, Focus card |
| Weekly check-in, with its report | Progress, first card | Today Next up row when it is due, the notification. Nothing else |
| Water log | Today water chips and sheet | Search |
| Water settings | You > Settings > Nutrition and water | Today water gear, "How the target adds up" |
| Targets and goals | You > Goals and plan | Settings > Nutrition and water (a single link row), Today Focus card |
| Allergens | Food > allergen strip > Allergens | Add food footer, plan import preview, Search |
| Food database and keys | You > Settings > Nutrition and water > Data sources | Food detail "Contribute" link, credits line |
| My foods | Log food > Mine | Search |
| Saved meals | Food > Meals | Log food > Mine, Today "save as meal prep" |
| Health Connect | You > Settings > Nutrition and water > Data sources | Progress shows its data |
| Jump tests | Progress > Weight and body | Search |
| Programs and generator | Train > Programs > New program | Today workout card "Make a program" |
| Exercises | Train > Exercises | Pickers in the builder and the workout, Search |
| Script docs | You > Help and tour > Scripts | A "?" in the editors (Plan menu, program editor, recipe import) |
| AI settings | You > Settings > AI | Contextual "Turn on AI" prompts |
| Replay the tour | You > Help and tour | none |

### 2d. Food: one screen, four sections

The user, looking at the Food hub: "the features on the food page could be combined rather than so separated."
Eight rows become one screen, because four of them are one job (decide, make, log) and three are not food
features at all.

```
Food
  [ Avoiding milk, peanuts, sesame    Edit ]        allergen strip
  [ Log food ]  [ scan ]
  Plan | Recipes | Meals | Eating out               section switch (opens on Plan)
  ... the chosen section's content ...
```

| Section | Holds (was) |
| --- | --- |
| **Plan** | This week and the shopping list (Meal plans). Menu: new, import, export, "?" |
| **Recipes** | Library, New recipe, Import from a link, Cook. Find recipes (AI) in the menu (Recipe Hunter) |
| **Meals** | Saved meals and NFC tags (Meal templates) |
| **Eating out** | Find a safer place, saved options (Plan B) and saved menus (Menu import) |

What moved out of Food: **My foods** is the **Mine** tab inside Log food, because that is where a person wants
it; a food's edit and "Contribute to Open Food Facts" open from there. **Food database & credits** goes to
Settings > Nutrition and water > Data sources. **Food script help** goes to the "?" in the Plan menu and to Help. **Allergens** is
the strip.

This is a real merge, so it carries a risk: the Plan section is the longest screen in the app today (about 32
controls). It keeps its own menu and sub-headings and is the only section that scrolls long.

### 2d-2. The other hubs

| Hub | Primary items | "Everything else" |
| --- | --- | --- |
| Food | One screen with four sections (above) | None. The drawer is gone because nothing is left to put in it |
| Train | Start card, This week (muscle map), Programs, Exercises | None needed |
| Progress | Weekly check-in, Weight and body, Nutrition, Training (4 cards) | None. The report PDF is an action inside the check-in |
| You | Goals and plan, Settings (6 groups), Help and tour | Each settings group has its own **Advanced** disclosure |

A hub needs an "All ..." list only when it has more than about four things. None of these does: the long tail went
into the right owner screen instead of into a drawer.

### 2e. One Settings

It is the second block of the You screen, six rows. Each opens one page with a short list and, if needed, one
collapsed **Advanced** section.

| Group | Takes in |
| --- | --- |
| **Look and units** | Weight, length and volume units (volume used to live in the water sheet), theme, reduce motion, wallpaper colours |
| **Nutrition and water** | Data sources (Health Connect, Food database and USDA key, Open Food Facts search and contribute) under Advanced. Target mode (automatic or by hand), fine-tune protein and fat, calorie floor, carb cycling, add workout calories (the Settings and Health Connect copies become one), water target adjustments, weather, drink values, sweat rate, quick-add drinks. Goal weight and rate stay in Goals and plan, with a link row here |
| **Training** | Default rest, vibrate, bar weight, plates |
| **Sync and export** | One simple screen with two parts. **Sync**: the optional self-hosted server is the off-device copy; a new phone pairs with it and pulls. **Export and import**: the local JSON or CSV file through the file picker, with an optional passphrase (open question) |
| **AI (optional)** | Master switch, key, budget, free models, scheduled AI, models per task; usage, cost and the benchmark under Advanced. "Reword my check-in" lives only here |
| **Privacy** | What leaves this phone. The statement stays one tap away because it is a feature |

Feature gears and "⋯" menus (the only other place options live): Today water gear, Meal plan menu (new, import,
export, script help), Programs menu, Recipes menu (Recipe Hunter), Check-in gear
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

### 2i. Decided: one weekly screen, simpler sync

Two decisions from the user, taken as given.

**Weekly check-in and Weekly report are the same thing** ("those are the same thing"). One screen, "Weekly check-in",
with three parts: the **week summary**, the **coach suggestions** with Apply and Dismiss, and the printable report
as an **Export / print this week** action. The week chips show ranges ("Nov 9–15"). It has one entry, the Progress
card; the Today Next up row and the weekly notification only deep-link to it. The Progress report row and the
Progress menu disappear. This takes two audit duplicates (check-in in More and Progress; report as its own row)
down to none.

**Too many ways to sync and back up.** The new model has two, and says what each is for:

| | Role | Where |
| --- | --- | --- |
| **Sync** | The optional self-hosted server is the off-device copy. A new phone pairs with it and pulls. If you sync, you do not need a backup | You > Settings > Sync and export |
| **Export and import** | A local JSON or CSV file through the file picker. Stays | Same screen |
| ~~Nightly encrypted remote backup~~ (WebDAV, SFTP, SMB, destinations, schedule, server identity prompts) | **Removed** | |

"Backup & sync" is renamed **Sync and export** and is one simple screen. It sheds the schedule, the destination
list and the server-identity dialogs of the old backup. Consequences to build around: the restore story becomes
"pair, then pull", so the sync server's pairing needs to be easy to find on a fresh install (first-run welcome
should offer "Restore from my server" and "Import a file"); `docs/BACKUP.md` and the Privacy screen's "Nightly
backup" line need updating; and people with no server rely on the export file alone (see the open questions).

## 3. Old to new, every screen

"Stays" means the screen is kept as is, only its parents change. The 21 sheets and 25 dialogs are covered in the
last two blocks.

### Routes

| Old route | Verdict | New home and notes |
| --- | --- | --- |
| `Today` | **Rebuilt** | Four cards (section 2b) |
| `Food` | **Becomes the one Food screen** | Allergen strip, Log food, scan, four sections: Plan, Recipes, Meals, Eating out |
| `Train` | **Slimmed, with room for insight** | Start card, This week muscle map, 2 rows |
| `Progress` | **Regrouped** | Weekly check-in, Weight and body, Nutrition, Training |
| `More` | **Becomes You** | Avatar, not a tab |
| `Onboarding` | **Stays**, copy updated | Four tabs, avatar, Edit Today. Sets Today defaults |
| `MealPlans` | **Becomes the Plan section** | Food > Plan. Week / Shopping switch. Menu holds new, import, export, "?" |
| `Recipes` | **Becomes the Recipes section** | List with New recipe and Import from a link; Recipe Hunter in the menu |
| `RecipeHunter` | **Demoted** | Recipes menu, "Find recipes (AI, optional)" |
| `RecipeImport`, `RecipeEditor`, `RecipeDetail` | **Stay** | Recipes. Detail and list rows get a Cook button |
| `CookMode` | **Stays** | Cook button from the Meals card, the plan, and recipe rows |
| `EatingOut` | **Becomes the Eating out section** | Find a place (was `PlanB`), saved options, saved menus (was `MenuSources`) |
| `PlanB` | **Merged** into the Eating out section | Top of the section |
| `MenuSources` | **Merged** into the Eating out section | "Saved menus" with Add a menu |
| `MealTemplates` | **Becomes the Meals section** | Food > Meals; also the Mine tab in Log food |
| `MyFoods` | **Moved into Log food** | The Mine tab, Foods list. Opens as a full list from there |
| `FoodDetail` | **Stays** | From the Mine tab in Log food |
| `FoodData` | **Moved** | You > Settings > Nutrition and water > Data sources > Food database |
| `Allergens` (hub of two) | **Removed** | The allergen strip opens the Allergens screen |
| `MyAllergens` | **Renamed** Allergens | The list, strictness gear, guide row |
| `AllergenKeywords` | **Stays** | Drill-in from an allergen |
| `AllergenReference` | **Stays** | Linked row on Allergens, "Hidden-ingredient guide" |
| `Programs` | **Slimmed** | Active program first, list below, New program (Build, Make me one, From a template, Import) |
| `ProgramBuilder` | **Stays** | From the active program row |
| `ProgramTemplates`, `ProgramGenerator` | **Stay**, one entry | Choices in New program. The Today workout card keeps one "Make a program" link |
| `ExerciseLibrary` | **Stays**, detail becomes a page, equipment picker | Photo, diagram, muscle map, history (section 4) |
| `Performance` | **Moved** | Progress > Weight and body > Jump tests |
| `ActiveWorkout` | **Stays** | Info button per exercise opens the detail page. Rest ring, plates unchanged |
| `WorkoutHistory` | **Renamed**, moved | Progress > Training ("Training log") |
| `WeeklyReport` | **Merged** into Weekly check-in | The PDF is the "Export / print this week" action on the check-in screen |
| `CoachCheckIn` | **Becomes "Weekly check-in", the one weekly screen** | Progress first card. Three parts: the week summary, the coach suggestions (Apply or Dismiss), Export / print this week. Week chips show ranges such as "Nov 9–15". Settings behind its gear; past check-ins inside |
| `HealthConnect` | **Moved** | You > Settings > Nutrition and water > Data sources |
| `BodyMetrics` | **Becomes a small sheet** for weight; measurements in Progress | Today Weight tile and Progress |
| `Settings` | **Flattened** into You | Six groups (2e) |
| `Privacy` | **Moved** | You > Settings > Privacy |
| `Backup` | **Simplified, renamed Sync and export** | You > Settings > Sync and export. Sync (the server) plus local export and import. The nightly encrypted remote backup and its destinations are **removed** (see 2i) |
| `AiSettings` | **Moved** | You > Settings > AI. Usage, cost, benchmark under Advanced |
| `Help` | **Trimmed** | You > Help and tour: Replay tour, Show tips again, Scripts (one row, then the wiki), About |
| `Profile` + `YourPlan` | **Merged** | You > Goals and plan: Your plan on top, "About you" below |
| `ScriptReference` | **Stays** | Reached from "?" in editors and from Help |

### Sheets

| Sheet | Verdict |
| --- | --- |
| Add food (called Log food) | **Four tabs, not six**: Find (recents and search together), Scan, Mine (my foods, saved meals, recipes), Quick add.
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

## 4. The new screens in the mockup, and room for training insight

The user wants much more specific equipment and exercise choice, deeper insight "at the level of Liftosaur"
(for example which muscle groups get targeted), and a diagram for every exercise and every piece of equipment.
Another agent is researching the content. This design only reserves the places, so that work does not have to
change the structure.

| Slot | Where | What goes there |
| --- | --- | --- |
| **This week** card | Train tab, under the Start card | A body map with **sets per muscle** this week, shaded by volume against a target. Tap opens Progress > Training for the detail (per muscle, per week, per exercise). Home of the analysis stays in Progress; Train only shows the summary |
| **Exercise page** | Exercises > an exercise, and the info button in the active workout | Photo on top, then a **diagram** (movement), the muscle map (main and secondary), the equipment with its own diagram, last results, history, Edit |
| **Equipment picker** | Exercise form, Exercises filter, and Settings > Training > My equipment | A grid of equipment with icons (barbell, dumbbell, cable, machine, kettlebell, band, bodyweight, and more). "My equipment" filters the library and the builder to what the person has |
| **Muscle filter** | Exercises | The same body map, tap a muscle to filter |

Data notes: the muscle map and "sets per muscle" can be drawn from `primaryMuscle`, `secondaryMuscles` and logged
sets, all of which exist. Photos, diagrams and equipment icons are **new content**: they need a source, a
licence and an APK-size decision, and a `photoUri` field (in `MIGRATION_2_3`, while v3 is unreleased). **There is
no bundled image set today.** The mockup shows labelled placeholders only.

Other new screens in the mockup:
* **Food** as one screen with four sections, and **Meal plan** inside it (Week or Shopping list, one menu).
* **Active workout**: unchanged in behaviour. The visible change is the info button per exercise, the "?" for how
  the weight is chosen, and one overflow for plates and notes.

## 5. How to build it (for a later task, not done here)

Order that keeps each step small and reviewable:
1. **Navigation only.** Remove the drawers and the two pass-through hubs, move rows, rename, add the avatar and
   You. No data change. Also fix the cross-tab pill jump by making You and the shared screens root-level
   destinations instead of registering them in several graphs.
2. **Today cards and Edit Today.** Visibility flags go in DataStore (and the portable settings allow-list, so a
   backup carries them).
3. **Merges** (the four Food sections, Allergens, Goals and plan).
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
| Hiding Today cards by default | A new person may not know Workout exists | The Train tab is always there; Next up suggests it; Edit Today is a visible text button |
| Food becomes one screen | The Plan section is long, and four sections behind a switch hide two of them from first glance | Section switch is always visible and sticky; Plan opens first; search finds any section |
| "Make me a program" gets one tap longer for people who already have a program | Honest regression | Newcomers (no program) still see the choices on Train; Today's workout card keeps a link |
| Progress becomes a summary that people tap into | Three or four charts may be too little for a bodybuilder | Every card opens to the full detail that exists today (training stats, volume per muscle, PRs); nothing is dropped |
| Cross-graph navigation | You and shared screens must work from every tab without moving the pill | Make them root-level destinations (step 1) |
| Onboarding, tour and tips copy mention More | Stale text | Update the nine steps, tour marks and tab tips together with step 1 |
| The mockup is not the app | It was not seen on a device, and animation, haptics and sheet behaviour are not modelled | Build step 1 and 2 first, then look at it on the user's phone before the rest |
| Wear OS and sync server | Untouched by this design | No change expected; confirm the watch's "Today" mirrors only data, not layout |

## 7. Open questions for the user

1. **Four tabs with You behind the avatar, or five with a You tab?** I recommend four. Does Settings behind the
   avatar sound right, or do you want a visible tab? Is "You" the right name ("Me", "Profile", a gear)?
2. **Is one Food screen with four sections (Plan, Recipes, Meals, Eating out) what you meant by combining?** Or do
   you want Plan and Recipes merged even further, into one list?
3. **Should My foods live only inside Log food (the Mine tab)?** It means one fewer place, but no standalone
   "all my foods" list on Food.
4. **Today: is "Next up" the right way to calm it?** It merges the one thing to do, the planned meal and the
   workout. Is four cards right, and should Weight sit on Today at all?
5. **Find anything.** Ship it in the first version, or later? It is the one piece that adds a new thing.
6. **Training insight.** Which views matter most: sets per muscle per week, volume trends per exercise,
   frequency, balance (push against pull), strength standards? Liftosaur is the bar; which three of its views do
   you actually use?
7. **Exercise photos and diagrams.** Where should they come from: ones you add yourself (no licence needed, empty at
   first), a free licensed set bundled in the APK (adds size, needs a licence check), or drawn diagrams made for
   the app? The muscle map needs no photos.
8. **Equipment.** Should "My equipment" be one list for the whole app, or a profile per gym (home, work)?
9. **Jump tests under Progress.** They are an athlete's measurement. Hide them until switched on?
10. **Make-a-program placement.** Are you happy that it moves into one New program list, one tap longer for
    someone who already has a program?
11. **Allergen strip.** On Food only, or also a thin line on Today?
12. **Old names.** "Plan B" and "Recipe Hunter" are yours. Rename them as proposed, or keep the names and add a
    plain-words subtitle?
13. **The weekly check-in's home.** Progress (proposed), or only the Next up row on check-in day?
14. **Export passphrase.** Should the JSON or CSV export keep an optional passphrase (an encrypted file), or be plain only? Plain is simpler, and the file is yours to protect.
15. **Sync-less people.** Without a server, the export file is the only off-device copy. Should the app remind you to export now and then (a quiet monthly nudge), or never nag?
16. **Old backups.** Anyone who set up the nightly remote backup would lose it. Is there anyone but you? If not, we can drop it with no migration. A remote backup already on a server can be read once by the restore path, then removed.

## 8. User feedback after the mockup (2026-11-09)

These decisions are firm, so build them into the v4 implementation.

- **One canonical home per feature, and no extra links.** "Settings has links to stuff that already exists elsewhere. There doesn't need to be links all over the app."
  - Remove every cross-link row whose target already has a home. For example, Settings links to My allergens, Food database, Replay the tour and AI tools.
  - Settings holds only settings.
  - A feature's own options live on that feature, behind its gear icon.
- **Weekly check-in lives in one place.** Today it appears in both More and Reports. It belongs in Progress, with the Next-up card on Today linking to it on check-in day. Remove the entry in More/You.
- **Profile gets progress photos.**
  - Add photos to the profile, front/side/back optionally, with a date. They are stored privately in app files, never synced or uploaded, and the export includes them only if the user opts in.
  - Show the photos as a timeline next to the weight and body-measurement trend: a side-by-side compare of two dates and a slider.
  - A gentle reminder to take a photo every 2–4 weeks is optional.
- **The pill gets a blur.** A frosted backdrop on Android 12+, with a translucent fallback on older versions. This is being done in the current bug-fix round.
