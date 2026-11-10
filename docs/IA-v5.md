# Intake v5: use screens and settings, kept apart

Design only. No app code changes. This is the buildable spec. It applies the user's round-2 decisions
(`docs/FEEDBACK-R2.md`) on top of `docs/IA-v4.md`. Where this file and v4 disagree, **this file wins**; where it
disagrees with `FEEDBACK-R2.md`, that file wins. The clickable mockup is `design/intake-mockup-v4.html`. Nothing here is
built and the mockup has not been seen on a device (there is no emulator in the cloud containers).

## In plain words

1. **Use screens are for using and building. Settings is for configuring.** Today, Food, Train and Progress have no
   gear icons and no option switches. Every setting lives in **Settings** (nine groups, denser than anything else
   in the app, because it is rarely opened). Settings holds only settings: no links to features that have a home elsewhere.
2. **Four tabs, as in v4: Today, Food, Train, Progress.** The avatar opens **You** (profile, goals and plan,
   progress photos, help). Settings is the gear in You's top bar.
3. **Food and drinks are one area.** Food opens on the full nutrition dashboard, then Log food, Log a drink, and the
   four sections (Plan, Recipes, Meals, Eating out). Drinks come from the same database as food, in categories,
   with size quick buttons, on their own page inside Food. Any food or drink you look up shows a **nutrition label**.
4. **Train is training.** The Train root is *Workout / Exercises / Videos*. The Workout section shows today's session
   with one big Start button, other ways to start, this week's muscle volume, and recent sessions. Programs
   (creating, editing, managing) is a menu one tap inside Train. Videos is the third Train section.
5. **Pushed screens behave like screens.** Every one has a top bar with a back arrow, a plain horizontal slide in
   and out (no shrink, no odd fade), correct top inset, and nothing under the floating pill, because on pushed
   screens the pill is not shown.

## 1. Tab map and top bars

```
Today      Calories ring + protein/fat/carbs · Next up · Water and weight · Meals
Food       allergen strip · nutrition dashboard (calories, macros, fibre, sugar, sat fat, salt, ...)
           [Log food] [Log a drink] [scan]
           sections: Plan | Recipes | Meals | Eating out
           pushed:   Drinks · Log food · Food detail (label) · Allergens · plan/recipe/cook screens
Train      sections: Workout | Exercises | Videos
           Workout: today's session · other ways to start · This week (muscles) · recent sessions
           pushed:   Programs (menu) · Program editor · Active workout · Workout summary
                     Exercise detail · Muscle volume · Training log · Video detail · Make a workout
Progress   Weekly check-in card · Weight and body · Nutrition · Training (long-term)
           pushed:   Weekly check-in · measurements · charts
You        (avatar, every tab root)  profile card · Goals and plan · Progress photos · Help and tour
           top bar: Settings gear
Settings   nine groups, each its own page (section 4)
```

### Top bars

| Kind | Left | Title | Right |
| --- | --- | --- | --- |
| **Tab root** (Today, Food, Train, Progress) | none (the screen title is the tab name, 22 sp, left aligned) | Tab name. Today shows the date with its arrows instead, see section 3 | **Avatar** (32 dp, 48 dp target, initial on a pale disc), always in the same place. A search icon is reserved to its left for "Find anything", which comes later (v4 default) |
| **Pushed screen** | **Back arrow** (48 dp target) | Screen name, one line, ellipsised | At most two actions: one primary icon, one "⋯" for actions (never settings) |
| **Full-screen task** (Log food, Active workout, Make a workout) | Back arrow (Active workout: "minimise", it never discards) | Task name | The task's own commit action (Finish) |
| **You** | Back arrow | You | **Gear** that opens Settings |
| **Settings** and each group | Back arrow | Settings / group name | none |

Rules for "⋯": it holds actions (new, import, export, duplicate, archive). It never holds a toggle that changes behaviour.

## 2. Navigation rules

### 2a. Back arrows and system back

1. Every pushed screen has the back arrow in its top bar. The arrow and the system back button and gesture do the
   same thing. Predictive back is supported (the screen under it is visible during the gesture).
2. The only screens without an arrow are the four tab roots. System back on a tab root other than Today goes to
   Today; on Today it leaves the app.
3. Back from a screen opened over another tab returns to that tab's screen. Shared screens (Log food, Drinks,
   Food detail, Exercise detail, Settings, You) are **registered once at the root of the nav graph**, not in several tab
   graphs, so the pill does not jump tabs (this removes the audit's duplicate registrations).
4. Tapping the current tab's pill item pops to its root (the existing `TabPolicy`).
5. Active workout back arrow minimises the session. Train and Today then show a **session bar** above the pill
   ("Push A · 24:10 · Resume"). Discarding is only inside the Finish sheet.

### 2b. Transitions

Reported bug B5: the menu close animation looks wrong. One rule set replaces every ad hoc animation:

| Change | Motion | Duration |
| --- | --- | --- |
| **Push** a screen | New screen slides in from the end edge. The old screen moves 25% toward the start and is dimmed by a scrim up to 12%. **No scale, no fade of the old screen's content** | 300 ms, FastOutSlowIn |
| **Pop** (back) | The exact reverse. The screen being closed slides out toward the end edge | 250 ms |
| **Switch tab** (pill) | Cross-fade only, 150 ms. The pill's highlight slides. No movement of content | 150 ms |
| **Bottom sheet** (small choices only: slot picker, swap, add set note) | Slides up from the bottom; closing slides down. Scrim fades. No shrink | 250 ms |
| **Full-screen task** (Log food, Make a workout, Workout summary) | Same as push. They are pages, not sheets | 300 ms |
| **Reduce motion** on, or the phone has animations off | Cross-fade 100 ms for every case above | 100 ms |

Menus ("⋯") are anchored popups: fade and 90%-to-100% scale are fine **inside the menu only**. This is the one place
scale is allowed, and it must not run on the screen itself.

### 2c. Insets and the floating pill

The pill floats over content on **tab roots only**. On pushed screens it is hidden (it slides down with the screen
transition), so there is nothing to collide with and a sticky action bar can sit at the bottom.

| Layer | Rule |
| --- | --- |
| Top | Every screen is a `Scaffold` with a `TopAppBar` that consumes `statusBars` and `displayCutout` top insets. A hub or list must never draw under the status bar (audit 2j) |
| Bottom on a tab root | Content scrolls behind the pill. The scaffold's bottom bar is the pill. Every scrolling list uses `contentPadding` from the scaffold **plus 16 dp**, so the last item clears the pill (pill 64 dp + 12 dp gap + navigation bar inset). A sticky action sits above the pill, not under it |
| Bottom on a pushed screen | No pill. `contentPadding` bottom = navigation bar inset (+ IME when a field is focused). A sticky bottom action bar uses `navigationBarsPadding()` |
| Side | `displayCutout` horizontal and system bar horizontal insets, for landscape and foldables. Wide screens keep the vertical pill rail and `readableWidth()` |
| Keyboard | `imePadding()` on every screen that has a text field. The focused field scrolls into view |
| Test | A `ScreenInsetsTest` (like `NavGraphGuardTest`) fails if a route's composable does not take and apply `contentPadding`. It would have caught the Food hub and the benchmark screen |

Mockup note: the mockup's phone frame draws a status bar and the pill so the spacing can be seen.

### 2d. Other navigation rules

* **One home per feature.** A summary may drill into its detail (a card to its page). A second *door* to the same
  settings or data is not allowed.
* **Settings holds only settings.** No row in Settings opens a feature page (My allergens, Food database page, Replay the
  tour, AI tools are all gone from it). The one pointer in the other direction is a disabled state: a feature that is
  off says "Off in Settings" and the line opens that group.
* **Exception, stated once: Allergens.** The allergen list, keywords and strictness live on the **Allergens screen,
  opened from the strip at the top of Food**. They are safety data and must be where food is chosen. Settings has no
  allergen row.
* **Pushed screens never contain tabs of other screens.** Section switches (Food, Train) exist only on a tab root.

## 3. Per-screen contents, in priority order

"Use" screens list what is on them from top to bottom, and then **what moved to Settings**.

### 3a. Today

Top bar: date title with arrows (previous, next), avatar.

1. **Calories card.** Ring with "N kcal left", and under it **three always-visible macros: Protein, Fat, Carbs**,
   each "eaten / target g" with a thin bar in the macro's colour. This replaces the "More detail" disclosure.
   Buttons: **Log food** (primary) and scan icon.
2. **Next up** (hidden when empty). At most three rows in the order they happen: the coach's one thing, the planned
   meal (**Log**, **Cook**), today's workout (**Start**, straight into the session).
3. **Water and weight** tiles.
   * **Water tile** shows litres so far, a bar, and **today's logged drinks as rows with a − and a + stepper**
     ("Water 250 ml ×3", "Coffee 240 ml ×1"). − removes the last one of that drink, + adds one more of the same
     drink and size. That is all the card does. A row at the bottom, **Log a different drink**, opens the Drinks
     page (Food > Drinks, pushed over Today; back returns to Today).
   * **Weight tile** shows the last weigh-in and **Weigh in** (a small sheet).
4. **Meals.** One card, four compact rows with entries and "+".

**Moved to Settings:** the card switches (Next up, Water, Weight, Meals; v4 had them behind an "Edit Today" button),
the water unit, the water target, quick-add presets. Today has **no Edit Today button and no gear**.

Defaults by welcome answers as in v4 section 2b (new person: Calories, Water, Meals; no workout row if "I don't train").

### 3b. Food (combined with hydration)

Top bar: "Food", avatar. Details in section 5.

1. Allergen strip ("Avoiding milk, peanuts, sesame · Edit"; amber "No allergens set. Add yours" when empty).
2. **Nutrition dashboard** (the whole day, full detail).
3. **Log food**, **Log a drink**, scan icon.
4. Section switch (sticky): **Plan | Recipes | Meals | Eating out**.

**Moved to Settings:** salt-or-sodium display, the food database switches, USDA key, Open Food Facts login, Health
Connect, targets and how they are calculated.

### 3c. Train

Details in section 6.

### 3d. Progress

Top bar: "Progress", avatar. Unchanged in spirit ("Progress is good as it is"), with the merged check-in:

1. **Weekly check-in card** (first card): week range chip ("Nov 9–15"), one-line summary, count of suggestions,
   **Open check-in**. When a check-in is due it is highlighted. Opens the one weekly screen (3e).
2. **Weight and body**: range chips (4 w, 12 w, 6 m, 1 y), weight chart with goal line, measurements, **Weigh in**,
   body fat. **Jump tests** appear here only if switched on in Settings > Training.
3. **Nutrition**: calories and protein adherence over the range, average macros, average hydration.
4. **Training (over time)**: estimated 1-rep max lines for the main lifts, **PR list**, volume trend, sessions per
   week. The per-week muscle view is **not** here; it is Train > This week (section 6, one home).

**Moved to Settings:** the Health Connect row (now Settings > Data sources). Removed: the Weekly report row and the
Progress menu (the PDF is an action inside the check-in); the training log (now Train).

### 3e. Weekly check-in (one screen, pushed from Progress)

Top bar: back arrow, "Weekly check-in", "⋯" (**Export / print this week**, **Past check-ins**). Week chips with ranges.
Contents: 1) **Summary** (food: calories and protein against target, hydration; body: weight change; training: sessions
and sets; a plain sentence for each). 2) **Suggestions**, each with the reason, **Apply** (says what it does) and
**Dismiss**. 3) Past check-ins list. No gear: the weekday and the notification live in Settings > Reminders; "Reword
with AI" lives only in Settings > AI.

### 3f. You (profile)

Reached by the avatar on any tab root. Top bar: back arrow, "You", **gear** (Settings).

1. **Profile card**: initial, name (optional), age, height, weight, goal in one line.
2. **Goals and plan**: your plan on top (calories, macros, water, training, each with the reason), then the goal
   inputs: phase, pace, goal weight, rate. **Read-only numbers here; a manual override lives in Settings >
   Nutrition** (one home for each target; today there are three).
3. **About you**: sex, birth year, height, body fat (optional), build, day-to-day activity. These are data about you,
   not preferences, so they stay out of Settings.
4. **Progress photos** (section 8). Latest photos, **Add photo**, **Compare**.
5. **Help and tour**: replay the tour, show tips again, show the welcome again, Scripts (the wiki), About.

Moved out of the profile: the "Training you can do" block (days, minutes, experience, goal, focus muscles, avoid areas
and weekdays) is asked by **Make me a program** in Train > Programs and remembered there. The equipment list moves to
Settings > Training. Carb cycling, calorie floor, refeed, diet break and protein/fat per kg move to Settings >
Nutrition.

### 3g. Allergens (one screen, from the Food strip)

Top bar: back arrow, "Allergens". Top to bottom: **1) Your allergens** (switch list with a search field, "Add your own"),
**2) How careful** (Avoid / Strict), **3) Keywords** per allergen (tap an allergen, a pushed page lists keywords to
add or remove), **4) Hidden-ingredient guide** (the old reference, read-only, as a section below on the same screen,
collapsible per allergen). This merges `MyAllergens` and `AllergenReference`; `AllergenKeywords` stays as the drill-in.
The warning sheet inside Log food is unchanged. A model or an import can only raise a warning, never clear one.

### 3h. Plan, Recipes, Meals, Eating out, and the cooking screens

As v4 section 2d, with one change that follows from the rule: **menus hold actions only** (new, import, export, "?").
They already do. Nothing else changes in this round, and the Plan section's long screen stays the one exception to
"short".

## 4. Settings layout

Settings is a list of nine group rows. A row shows the group name and a one-line summary ("kg · cm · ml · Dark"). A
group opens its own page (back arrow). Each page is dense and may have a collapsed **Advanced** section. Every
control has a one-line "what this does". The mockup's Settings frame lists all nine and opens any of them.

| # | Group | Holds |
| --- | --- | --- |
| A | **Appearance and units** | Units, theme, motion, Today's cards |
| B | **Nutrition** | How targets are worked out, display of nutrients |
| C | **Water and drinks** | Target, adjustments, quick sizes, drink values, sweat rate |
| D | **Training** | Rest timer, bar and plates, equipment, volume targets |
| E | **Data sources** | Health Connect, food database, USDA key |
| F | **Videos** | YouTube key, default filter |
| G | **Reminders** | Check-in day and notification, photo reminder, export reminder |
| H | **Sync and export** | Server pairing, devices, export and import |
| I | **AI (optional)** | Switch, key, budget, models, AI helpers |

The privacy statement is read-only text. It sits at the bottom of the Settings list as one line, "What leaves this
phone", opening the statement. It is documentation, not a setting.

### 4a. Inventory: every setting, its old home and its new home

"Use screen?" means the setting could be changed from a screen people use daily (Today, a water sheet, a feature
gear, a check-in), which is what the user objected to. "Planned" means it exists only in the v4 design, not in the app.

| # | Setting | Old home (code) | Use screen? | New home |
| --- | --- | --- | --- | --- |
| 1 | Weight unit | Settings (`weightUnit`) | no | A. Appearance and units |
| 2 | Length unit | Settings (`lengthUnit`) | no | A |
| 3 | Volume unit | Water sheet (`volumeUnit`) | **yes** | A |
| 4 | Theme: system, light, dark | Settings (`themeMode`) | no | A |
| 5 | Use wallpaper colours | Settings (`dynamicColor`) | no | A |
| 6 | Reduce motion | Settings (`reduceMotion`) | no | A |
| 7 | Today card: Next up | Planned "Edit Today" | **yes** | A |
| 8 | Today card: Water | Planned "Edit Today" | **yes** | A |
| 9 | Today card: Weight | Planned "Edit Today" | **yes** | A |
| 10 | Today card: Meals | Planned "Edit Today" | **yes** | A |
| 11 | Work my targets out from my data (auto or by hand) | Settings (`autoTargets`) | no | B. Nutrition |
| 12 | Calories by hand | Settings, Your plan | **yes** (Your plan) | B |
| 13 | Protein, g per kg | Settings, Profile Advanced, Your plan (`proteinPerKg`) | **yes** (3 homes) | B |
| 14 | Fat, g per kg | Settings, Profile Advanced, Your plan (`fatPerKg`) | **yes** (3 homes) | B |
| 15 | Allow calories below the floor | Profile Advanced | no | B (Advanced) |
| 16 | Carb cycling on or off | Profile Advanced | no | B (Advanced) |
| 17 | Carb cycle shift, percent | Profile Advanced (`carbCycleShiftPct`) | no | B (Advanced) |
| 18 | Refeed days per week | Profile Advanced | no | B (Advanced) |
| 19 | Diet break every N weeks | Profile Advanced | no | B (Advanced) |
| 20 | Contest prep: weekly loss, percent | Profile Advanced | no | B (Advanced, shown only in that phase) |
| 21 | Reverse diet: kcal added per week | Profile Advanced | no | B (Advanced, shown only in that phase) |
| 22 | Add active calories to my target | Settings advanced, Health Connect (`addActiveKcal`, 2 homes) | **yes** (HC) | B |
| 23 | Show salt or sodium on the dashboard | Planned (new) | no | B |
| 24 | Base daily water target | Water sheet, Your plan "Daily water" (`hydrationBase`) | **yes** | C. Water and drinks |
| 25 | Adjust the target for the day | Water sheet (`hydrationDynamic`) | **yes** | C |
| 26 | Training-day increase | Water sheet (`hydrationTrainingBonus`) | **yes** | C |
| 27 | Adjust water for weather | Settings (`weatherEnabled`) | no | C |
| 28 | Weather location: this phone or a place (city or ZIP), forget | Settings (`locationMode`, `place`) | no | C |
| 29 | Quick sizes: which size buttons show, custom sizes | Water sheet "Quick-add drinks" | **yes** | C |
| 30 | Drink values: hydration factor, caffeine mg, strength percent, reset | Water sheet Advanced (`drinkOverrides`) | **yes** | C (Advanced) |
| 31 | Measure my sweat rate (before, after, drunk, minutes) | Water sheet Advanced | **yes** | C (Advanced) |
| 32 | Use my measured sweat rate | Water sheet Advanced (`sweatUse`) | **yes** | C (Advanced) |
| 33 | Default rest, seconds | Settings (`defaultRestSec`) | no | D. Training |
| 34 | Vibrate when rest ends | Settings (`restVibrate`) | no | D |
| 35 | Bar weight | Settings advanced (`barWeight`) | no | D |
| 36 | Plates available per side | Settings advanced (`plates`) | no | D |
| 37 | My equipment (one list, presets on top) | Profile "Training you can do" (`equipment`, `equipmentPreset`) | no | D |
| 38 | Show jump tests in Progress | Planned (v4 default) | no | D |
| 39 | Weekly sets target per muscle region | Planned (Liftosaur notes M2) | no | D (Advanced) |
| 40 | Health Connect: which record types to read (one switch each) | Health Connect screen | no | E. Data sources |
| 41 | Search Open Food Facts while typing | Food database (`offSearch`) | no | E |
| 42 | Show products in other languages | Food database (`offOtherLanguages`) | no | E |
| 43 | Allow contributing to Open Food Facts | Food database (`offContribute`) | no | E |
| 44 | Open Food Facts username and password | Food database | no | E |
| 45 | USDA FoodData Central key | Settings advanced (`usdaKey`) | no | E |
| 46 | YouTube Data API key | Planned (new) | no | F. Videos |
| 47 | Show only workouts by default | Planned (new) | no | F |
| 48 | Personalise Recommended from this phone's data | Planned (new) | no | F |
| 49 | Weekly check-in notification | Check-in gear (`coachNotify`) | **yes** | G. Reminders |
| 50 | Check-in day of the week | Check-in gear (`coachWeekday`) | **yes** | G |
| 51 | Progress photo reminder: off, every 2, 3 or 4 weeks | Planned (v4 section 8) | no | G |
| 52 | Export reminder (monthly, only when sync is off) | Planned (v4 default) | no | G |
| 53 | Sync server URL | Backup, Sync section (`syncServer`) | no | H. Sync and export |
| 54 | Pairing code and device name | Backup, Sync section | no | H |
| 55 | Linked devices: add, remove, stop syncing here | Backup, Sync section | no | H |
| 56 | Export passphrase (optional) | Backup | no | H |
| 57 | Include progress photos in the export (off by default) | Planned (v4 section 8) | no | H |
| 58 | AI on or off | AI settings (`aiEnabled`) | no | I. AI |
| 59 | OpenRouter key | AI settings | no | I |
| 60 | Monthly AI budget | AI settings (`aiBudget`) | no | I |
| 61 | AI privacy level | AI settings (`aiPrivacy`) | no | I |
| 62 | Suggest free models | AI settings (`aiAllowRetaining`) | no | I |
| 63 | Daily AI insight | AI settings (`dailyInsight`) | no | I |
| 64 | Reword my check-in with AI | AI settings **and** Check-in gear (`coachAiReword`) | **yes** (2 homes) | I |
| 65 | Refresh menus automatically (AI) | AI settings (`menuAutoRefresh`) | no | I |
| 66 | Model per task | AI settings (`aiModel_*`) | no | I (Advanced) |

**Not settings, so not in the count** (the other things people expect to find in Settings):

| Item | Why it is not a setting | Home |
| --- | --- | --- |
| Sex, birth year, height, body fat, build, activity | Data about you | You > About you |
| Phase, pace, goal weight, goal rate | Goal inputs | You > Goals and plan |
| Training days, session minutes, experience, goal, focus muscles, avoid areas, weekdays | Questions for one action | Train > Programs > Make me a program (remembered) |
| Allergen list, keywords, strictness | Safety data (the stated exception) | Allergens screen from Food |
| Meal plan active week, plan menus | State and actions | Food > Plan, its "⋯" |
| Replay tour, welcome, tips | Actions | You > Help and tour |
| Usage, cost, benchmark | Read-only reports and an action | Settings > AI, below the controls |
| Last synced, "N records" per type | Read-only status | Settings > Data sources, Sync and export |
| Privacy statement | Documentation | One line at the bottom of Settings |

**Removed outright** (no new home, per `FEEDBACK-R2.md`): backup destination (protocol, host, port, user, password,
private key), backup schedule, backup passphrase, server identity trust dialogs (`backupDestination`, `backupSchedule`).
`MIGRATION_2_3` need not touch them; the restore path reads an old remote backup once if it exists (v4 question 16).

**Totals** (counted from the table above):

* **66 settings** in nine groups (A 10, B 13, C 9, D 7, E 6, F 3, G 4, H 5, I 9).
* **19** are marked "use screen": settings that could be changed from Today, a water sheet, a feature gear, the
  check-in or the Your plan screen. All 19 now live only in Settings. **15** of them exist in the app today (#3, 12, 13,
  14, 22, 24, 25, 26, 29, 30, 31, 32, 49, 50, 64); **4** (#7 to #10, the Today card switches) were only planned
  in v4 behind an "Edit Today" button and are placed in Settings from the start.
* **6** settings had two or three homes and now have one: #12 (2), #13 (3), #14 (3), #22 (2), #24 (2), #64 (2).
* **11 more** were not on a use screen but sat in the wrong place (the profile's Advanced block, the old Settings
  screen's Advanced drawer): #15 to #21, #35, #36, #37, #45. They are regrouped, not moved off a use screen.
* **Removed:** the remote backup settings (destination, schedule, passphrase).

If you edit the table, recount.

### 4b. Settings page contents, in order

Each page leads with the three or four settings people change, then **Advanced**.

* **A. Appearance and units**: Weight, Length, Volume (segmented); Theme; Wallpaper colours; Reduce motion. **Today's
  cards**: four switches. Preview note: "Calories is always shown."
* **B. Nutrition**: *Targets*: Work them out from my data (switch). If off: Calories, Protein g/kg, Fat g/kg fields.
  If on: the same fields as a "fine-tune" disclosure. *Dashboard*: Salt or sodium. *Active calories*: Add active
  calories to my target. **Advanced**: calorie floor, carb cycling (with shift %), refeed days, diet break, and the
  two phase-specific numbers. Each has a one-line explanation. "Recalculate from my data" is an action button.
* **C. Water and drinks**: Daily target (base), Adjust for the day (switch), Training-day increase, Weather (switch and
  location), **Quick sizes** (chips for each size with × and an Add custom size field; the unit system sets the
  defaults), **Advanced**: Drink values (a table by drink type), Measure my sweat rate, Use my measured rate.
* **D. Training**: Default rest, Vibrate; Bar weight, Plates per side; **My equipment** (preset chips, then icon
  grid); **Advanced**: Show jump tests, Weekly sets target by region (six numbers, "Reset to suggested").
* **E. Data sources**: *Health Connect* (status, per-type switches, "Last synced: N records" for each type, request
  permissions); *Food database* (Open Food Facts search, other languages, contribute, login); *USDA key*.
* **F. Videos**: YouTube key (paste, Save, Test, Remove; stored in the key store), Show only workouts by default,
  Personalise Recommended. One-line note: "Videos need internet and your own key. Intake never downloads a video."
* **G. Reminders**: Weekly check-in (switch and day chips), Progress photo (off, 2, 3, 4 weeks), Export (switch;
  greyed with a note when sync is on).
* **H. Sync and export**: *Sync* (server URL, pair, devices, stop syncing here, last sync status) and *Export and
  import* (JSON, CSV, Import, optional passphrase, Include progress photos). The old backup UI is gone.
* **I. AI (optional)**: Switch, Key, Budget, Privacy level, Free models, Daily insight, Reword check-in, Auto-refresh
  menus; **Advanced**: Model per task, usage and cost, benchmark.

## 5. Food and drinks

### 5a. Food screen, top to bottom

```
Food                                                        (avatar)
[ Avoiding milk, peanuts, sesame                    Edit ]
Today · 1,540 kcal eaten · 540 left                       < Mon 10 >
  Calories bar  (eaten, target)
  Protein  112 / 150 g   Carbs 168 / 210 g   Fat 48 / 62 g        (macro bars)
  Fibre 18 / 30 g · Sugar 52 g · Saturated fat 14 / 20 g · Salt 3.1 / 5.0 g
  Hydration 1.6 / 2.5 L     (opens Drinks)
  > More nutrients (potassium, calcium, iron, vitamin C ... when known)
[ Log food ]  [ Log a drink ]  [ scan ]
Plan | Recipes | Meals | Eating out
...the section...
```

Priority and rules:

1. **Allergen strip** first (safety), as v4.
2. **Nutrition dashboard**, one card. Day arrows share state with Today. Rows, in order: calories, protein, carbs, fibre,
   fat, saturated fat, sugar, salt or sodium (setting B), then a collapsed **More nutrients** with every
   micronutrient the logged foods carry. A value that is **unknown for a food is shown as "—", not 0**, and the card
   says "N foods have no fibre value" when that affects a total. Targets show only where a target exists
   (calories, protein, fat, carbs; fibre, saturated fat and salt use common reference limits and are labelled as such).
3. **Hydration line** inside the dashboard: litres against target, plus caffeine, sugar and alcohol from drinks if any.
   Tapping it opens the Drinks page. Hydration is a nutrient row, not a second feature.
4. **Log food / Log a drink / scan** (scan opens Log food on its Scan tab).
5. **Sections** (sticky): Plan, Recipes, Meals, Eating out, as v4.

Nothing on this screen configures anything.

### 5b. Log food (full screen, was a bottom sheet)

Pushed from Today, Food, a meal slot's "+", and a plan row. Top bar: back arrow, "Log food", slot chip ("Breakfast ▾").

1. Search field (autofocus after a tap, not on open from a barcode link).
2. Tabs: **Find** (recents, then results), **Scan**, **Mine** (my foods, saved meals, recipes), **Quick add** (calories,
   macros by hand; estimate from a photo, AI, optional, only on tap).
3. Find has scope chips **All · Foods · Drinks** (a filter on the same database, not a link to the Drinks page).
4. Each result: name, brand, kcal and the portion, allergen chip, **+** to add the default portion. Tap the row to
   open Food detail.
5. Footer line (kept from today): "Allergen check runs before saving."

The full-screen allergen block is unchanged.

### 5c. Food detail with the nutrition label

Opened from any result of search, barcode, My foods, a recipe ingredient, a drink. Top bar: back arrow, name, "⋯"
(**Edit**, **Contribute to Open Food Facts**, **Photograph the nutrition label**, **Delete** as soft delete).

1. Name, brand, source chip (Open Food Facts, USDA, mine), allergen chips from the local engine.
2. **Portion**: amount field and unit (g, ml, serving, cup; units follow Settings), with a stepper. The label rescales live.
3. **Nutrition label card** in the familiar layout: "Nutrition Facts", serving line, a heavy rule, **Calories** large,
   then Total fat, Saturated fat, Trans fat, Cholesterol, Sodium (or Salt), Total carbohydrate, Dietary fibre, Total
   sugars (Added sugars when present), Protein, then vitamins and minerals present, then the footnote "% Daily Value
   is based on a 2,000 kcal diet". It is drawn from the stored per-100 g (or per-100 ml) values times the portion.
   A toggle switches the columns between **Per portion** and **Per 100 g**. Fields the source does not give show
   "not listed", never 0, and are skipped in %DV.
4. **Ingredients and allergens** block: the ingredient text with matches underlined, "Contains / May contain" tags.
   A model can raise a warning, not clear one (unchanged).
5. Sticky bottom bar: slot chip and **Add to log** (primary).

The label is a **view of data**, not an image we generate. For contributions we follow `FEEDBACK-R2.md`: structured values
as today plus an optional "Photograph the nutrition label" step; we never upload a generated image.

### 5d. Drinks page (Food > Drinks)

Pushed from "Log a drink" on Food, the hydration line, and "Log a different drink" on Today. Top bar: back arrow, "Drinks".

1. **Hydration summary**: litres against target, a bar, and four small totals from drinks today: caffeine mg, sugar g,
   kcal, alcohol g. (Hydration counts water volume and nutrition, `FEEDBACK-R2.md`.)
2. **Log with one tap**: a "Drink" selector (recent drinks as chips: Water, Coffee, Sparkling water, Diet cola, then
   "Other…") and under it the **size quick buttons**. US units: 8 oz glass, 12 oz can, 16.9 oz bottle, 20 oz bottle.
   Metric: 250 ml, 330 ml, 500 ml and 750 ml (the volume unit in Settings picks the set; the full list the user
   can edit is Settings > Water and drinks, Quick sizes). A **+ Custom** button opens a number field. A tap logs and
   shows "Added 330 ml Diet cola · Undo" (snackbar) at the bottom. The editable list of sizes is Settings > Water and drinks.
3. **Search**: a field "Search drinks" over the **same food database**, scoped to drinks.
4. **Browse by category**: a grid of categories: Water, Sparkling and mineral, Soda, Juice, Coffee, Tea, Energy drinks,
   Sports drinks, Milk and plant milks, Smoothies, Beer, Wine, Spirits and mixers, Other. Tap one to open the **category
   page** (pushed): "Soda" lists every soda in the database (Cola, Diet cola, Lemon-lime, Root beer, Ginger ale ...),
   each row with kcal and sugar per 330 ml, a **+** that logs the **selected size**, and a tap that opens Food detail with the label.
   Categories can have sub-categories shown as chips ("All sodas · Cola · Diet · Citrus · Root beer").
5. **Today's drinks**: the list of entries with a ⊖ (remove, soft delete) and the volume, newest first. This is where
   Today's water card shows the same rows.

**Data notes (for the engineer, `MIGRATION_2_3`, with a `MigrationSchemaTest` case):**
a drink is a `food` row with `kind = DRINK`, a `categoryId` path (a small `food_category` table, seeded, soft deletes), a
`servingMl`, and per-100 ml nutrition (the same columns as food); optional `caffeineMgPer100ml`, `abv`, and
`hydrationFactor` (defaults per category, overridable from Settings, which is the existing `DrinkOverrides`).
Existing hydration entries become log entries of the seeded "Water" food (and the other existing drink types map to
categories). The hydration total is `sum(volume x factor)`. The USDA and Open Food Facts importers set `kind` from
their own beverage categories; a deterministic mapper does it (no AI). Allergen checks run on drinks the same way
(milk in a latte, sulphites in wine).

## 6. Train

### 6a. Research: patterns from established lifting apps

Source note: the public product and help pages for Strong, Hevy, JEFIT and Liftosaur were fetched on 2026-11-10 and
read as data only (feature lists and marketing copy); one page (Fitbod's) refused the request (HTTP 403), so for
Fitbod, and for the detailed screen layouts of all five, this section also relies on **general knowledge of these
apps' well-known patterns**. Nothing was copied; no assets, text or layouts are reproduced. `docs/research/LIFTOSAUR-NOTES.md`
holds the licence rule (Liftosaur is AGPL; ideas only).

| Pattern | Seen in (public) | What we adopt, and why |
| --- | --- | --- |
| **A Start area with today's routine** on the main training screen | Strong (templates and "start empty workout" on the main workout screen), Hevy (Quick start and Routines), Liftosaur (the day, with one Start), Fitbod (today's generated workout) | **Adopt.** The first thing on Train is today's session with a single Start button. It answers "what do I do now" in one tap, from Train or from Today |
| **Empty workout and repeat** as secondary starts | Strong, Hevy | **Adopt**, as three small starts under the hero: Empty workout, Repeat last, My workouts. Training without a program must work |
| **Routines and programs behind a menu, not the main screen** | Hevy (routine folders), JEFIT (plans in a planner), Strong (templates), Liftosaur (programs page) | **Adopt** exactly as the user asked: Train's root is for training; the program list, editor and "make one" live in a **Programs** menu inside Train |
| **Exercise library as its own area** with search, muscle and equipment filters, and a detail page with history | Hevy, JEFIT (instructions, 1,400+ exercises), Strong, Liftosaur | **Adopt** as the second Train section (Exercises). Detail is a page with media, muscles, records and history |
| **History / log** (list and calendar) | Strong and Hevy (History), JEFIT | **Adopt** as "Recent sessions" on Train plus **All sessions**. One home: Train (not Progress) |
| **Active workout: set rows with a Previous column, quick check-off, rest timer, plate math, swap** | Strong, Hevy (previous, live timer), Liftosaur (plates and bar from settings, RPE), Fitbod (swap), JEFIT (notes) | **Adopt** all five. Previous values are the biggest speed-up: the set row shows last time and the target, one tap copies it. Plates per side appear under the focused weight; swap respects "my equipment" |
| **Live PR notification** during the workout | Hevy ("live personal record notification") | **Adopt, quietly**: a small trophy on the set and a line in the summary, no pop-up that blocks logging |
| **Post-workout summary** with volume, duration, PRs, muscles worked | Hevy (muscle distribution), Strong (PRs, best sets), JEFIT (muscle recovery), Fitbod | **Adopt**: a summary page with the muscle map, sets per muscle, volume, PRs and next-time changes |
| **Weekly muscle volume** (sets per muscle per week) | Hevy ("sets per muscle group per week", "muscle distribution"), Liftosaur (prescribed vs completed weekly volume), JEFIT (recovery breakdown) | **Adopt** as the **This week** card on Train with a Muscle volume page; six regions with plain status words; detail on tap. Ranges and contributors as Liftosaur notes M2 |
| **Progress charts: 1RM, volume, PRs over time** | Strong PRO (best sets, 1RM), JEFIT (1RM and charts), Liftosaur, Hevy | **Adopt** in Progress > Training and on the exercise page. Long term, so not on Train |
| **Programming depth** (progressions, deload, periodisation) | Liftosaur (scripts), JEFIT (periodisation) | **Keep what we have** (program editor, script text, "How the weight goes up"), but behind the Programs menu, never on the training screen |
| **Social feed, coach assignment, cloud accounts** | Hevy, JEFIT, Strong | **Not adopted.** Offline-first and private; sync is the self-hosted server |
| **AI-generated plan** | Fitbod, JEFIT | **Kept optional and local first**: our "Make me a program" generator is deterministic; any AI is a separate, explicit tap |

### 6b. Train root

Top bar: "Train", avatar. A **section switch** under it: **Workout | Exercises | Videos** (same pattern as Food's). Opens on Workout.
When a session is running, a session bar sits above the pill on every tab root.

**Workout section, top to bottom:**

1. **Today's session card** (hero). Day name and program ("Push A · Week 3 of 8"), duration estimate, the exercise list
   (five lines max: name, sets × reps, last weight), a tiny muscle map of what it works, and **Start workout** (primary).
   * Mid-session: "Push A · 24:10" and **Resume**.
   * Rest day: "Rest day. Next: Pull A on Thursday." with **Start a day anyway** (chips of the other days).
   * No program: "No program yet." with **Empty workout** and a plain pointer to the Programs menu (one line, not a hub).
2. **Other ways to start**: three small buttons: **Empty workout**, **Repeat last** (Push A, Mon), **My workouts**
   (saved one-offs, from `MyWorkoutsSection`).
3. **This week** card: the six-region body map with sets per region this calendar week, bars and plain words
   ("a bit low", "about right"), a one-line hint ("Light on rear delts"). Tap opens **Muscle volume** (6c). This is the
   place of "weekly muscle volume" from the Liftosaur notes (M2, M3).
4. **Quick stats** strip: sessions this week, volume, streak (days trained in a row relative to plan). Plain numbers, no link.
5. **Recent sessions** (last 4): date, name, duration, volume, PR count chip. Tap opens the session (the same summary
   page, read-only). **All sessions** opens the Training log (list with month grouping and a calendar toggle).
6. **Programs** button in the section header (right): "Programs ▸". Not a row buried at the bottom; it is the menu the user asked for.

**Moved out of the Train root:** program days list, "This week's working sets" table, "Your programs", "Add a program"
(four buttons), Import program, Script help, Archive dialogs. All of those are in Programs.

**Exercises section:** search, a muscle filter (the body map; tap a muscle), equipment chips with **My equipment** on by
default, list rows with a thumbnail, name, main muscle and last result; **New exercise**. Tap a row for Exercise detail.

**Videos section:** section 7.

### 6c. Muscle volume (pushed from This week)

Top bar: back arrow, "Muscle volume", week picker (calendar weeks, previous / next). Body map (front and back toggle),
six region rows (chest, back, shoulders, arms, legs, core) with sets against the target range drawn as a band and a status
word; tap a region to expand its muscles and the **exercises that contributed** (effective sets). A line "Compared with
last week". Text carries every number the picture shows (accessibility). Targets: Settings > Training > Advanced.

### 6d. Programs menu (pushed from Train > Workout)

Top bar: back arrow, "Programs", "⋯" (**Script help**, **Archived programs**). In priority order:

1. **Active program** card: name, week of N, the day list (tap a day to edit its exercises), **Edit program** (primary),
   and a "⋯" for **Duplicate, Pause, Archive**.
2. **Your other programs**: rows with **Make active**, Edit, Duplicate, Archive.
3. **New program** with four choices (one list, one place): **Build from scratch**, **Make me one** (the generator; it
   asks days, minutes, experience, goal, focus and avoid, and remembers them), **From a template**, **Import** (file,
   text or link).
4. **My workouts** (saved one-offs): rename, delete, make into a day.
5. Archived programs (collapsed).

The **Program editor** (28 controls today) is unchanged by this round except: its balance header (Liftosaur M4),
and that **it opens only from here**. The Today workout card's "Make a program" link points here.

### 6e. Active workout (full screen)

Based on the patterns above. Top to bottom:

```
<  Push A   24:10                                   Finish
---------------------------------------------------------
 Rest  1:24  [-15] [+15] [Skip]          (sticky when running)
---------------------------------------------------------
 Bench press                                     [info] [⋯]
 3 x 8-10 @ 80 kg  ·  last: 80 x 9, 80 x 8, 80 x 8
 SET   PREVIOUS    kg      REPS     ✓
 W     40 x 10     40      10       ●
 1     80 x 9      80      9        ●
 2     80 x 8      80      [ ]      ○     <- focused
        per side: 20 + 10 + 2.5     (plates)
 3     80 x 8      [ ]     [ ]      ○
 + Add set
---------------------------------------------------------
 Incline dumbbell press ...
 + Add exercise
 [ Finish workout ]
```

1. **Top bar**: back arrow = minimise (the session keeps running; the session bar appears), title = day name, the
   elapsed timer, **Finish** (opens a sheet: Finish, Keep going, Discard).
2. **Rest timer strip**, sticky below the bar while running: remaining time as a thin ring, −15 s and +15 s, Skip. It
   starts when a set is checked. The default length is Settings > Training; the "⋯" of an exercise can override it for
   that session. Vibrate at the end per Settings.
3. **Exercise cards**, all in one vertical list; the one being worked is expanded, others show a one-line status
   ("3/3 done"). The header has the name (tap: **Exercise detail** page), the prescription ("3 × 8–10 @ 80 kg") and the
   "⋯": **Swap**, **Notes**, **Plates**, **Reorder**, **Rest for this exercise**, **Remove**.
4. **Set rows**: columns **Set** (W for warm-up), **Previous** (last session's weight × reps; tap copies), **kg** (units
   per Settings), **Reps**, **✓**. The target is the field's placeholder (so the row never shows two competing numbers).
   RPE appears as an optional column only when the program uses RPE. Checking a set starts the timer and, if it beats
   a record, shows a small trophy on the row (live PR).
5. **Plate math**: under the focused set, one line "Per side: 20 + 10 + 2.5" with the bar and plates from Settings.
   It is a chip, not a card; tapping it opens the plates diagram sheet. Only barbell exercises show it.
6. **Swap**: a bottom sheet of alternatives with the same main muscle, filtered by **My equipment**, most recently used
   first; picking one replaces the exercise for this session only, or "Replace in program" as an explicit second choice.
7. **Bottom**: Add exercise, Notes for the session, **Finish workout** (primary). No gear anywhere on this screen.

Info on a card opens Exercise detail (6g). The pill is hidden. The watch (Wear) mirrors the current set and rest as today.

### 6f. Post-workout summary (pushed after Finish)

Top bar: back arrow (acts as Done), "Workout complete", nothing on the right. Order:

1. **Header**: day name, date, duration. **Stats**: Volume, Sets, Reps, Duration.
2. **Personal records** (quiet, only if any): rows "Bench press: estimated 1-rep max 104 kg, up 2 kg", "Rep record: 80 kg × 9".
   "First time ever" is not a record (Liftosaur notes M6).
3. **Muscles worked**: front and back **muscle map** shaded by effective sets this session (main muscle full credit,
   helpers half, as the notes' convention), a legend, and the text list beneath ("Chest 9 sets, Triceps 6, Shoulders 5").
4. **This week so far**: the six regions as thin bars with this session's share highlighted, one hint line.
5. **Next time**: the weight changes the program will apply ("Bench 80 → 82.5 kg") with why ("hit the top of the range").
6. For an empty workout only: **Keep this workout?** (save as a program day or as a saved workout), the current dialog,
   as a card here instead of a dialog.
7. **Done** (primary) returns to Train; **Notes** is an optional text field.

### 6g. Exercise detail (a page, from the library, an active workout card, or a result)

Top bar: back arrow, name, "⋯" (**Edit**, **Hide**). Order: 1) **Media**: photo (bundled or yours) and a movement
diagram, labelled placeholders until content exists (v4 section 4 data notes). 2) **Muscle map** with main and also-works
chips. 3) **Equipment** chip with its icon. 4) **How to do it**: three to five steps, plus one "common mistake". 5) **Your
records**: estimated 1-rep max, best at 3, 5 and 8 reps, best session volume. 6) **Trend**: one chart, with a segmented
control Weight / 1-rep max / Volume (Liftosaur notes M5). 7) **History**: sessions with the sets. In an active workout the page
has **Add to workout** instead of Edit.

## 7. Videos

**Home: Train > Videos** (the third section of Train). Decision and why: it is training content, Train already has
sections, and a fifth tab would undo the four-tab calm. It is **not** a pushed menu: browsing is its own job and
deserves the same weight as Workout and Exercises. Video detail and "Make a workout" are pushed pages.

### 7a. Videos home, top to bottom

1. **Search field** ("Search workout videos"). Submit runs a search. Only the typed words are sent, only to YouTube,
   only on submit (not while typing), and the footer says so once.
2. **Filter row** under it: `Workouts only` (on) as a chip; chips for **Strength, Pilates, Yoga, Mobility, HIIT, Stretch**; **Length**
   (≤ 15, 15–30, 30–45, 45+). After a search, a line "12 of 30 results hidden as not workouts. **Show everything**" is
   shown; tapping it reveals all results **for that search only** (the next search filters again).
3. **Recommended** (the default page before any search): rows of video cards grouped by plain reasons, all
   deterministic and local: "For your light muscles this week (rear delts)", "Your 20-minute window", "Because you saved
   a pilates video", "No equipment". Built by templating queries from data on this phone (weekly volume, my equipment,
   saved videos) and filtering by the classifier. The switch "Personalise Recommended" is Settings > Videos. With it off, Recommended is a fixed set of
   category queries. No AI is involved.
4. **Saved** (watch later): a horizontal row; remove is a soft delete.
5. **Empty states**: no key ("Videos use YouTube and your own free key. Add it in Settings > Videos."), offline ("Videos need
   a connection"), quota used up ("YouTube's daily limit for your key is used. Try again tomorrow").

A **video card**: thumbnail (placeholder in the mockup), title (2 lines), channel, duration, a small badge: **Workout** or
**Chapters** (when chapters were found) or **Maybe** for borderline classifier scores.

### 7b. Video detail (pushed)

Top bar: back arrow, "Video", "⋯" (**Save for later**, **Open in YouTube**, **Share link**). Order:

1. **Player**: the official embedded YouTube player, 16:9. Never downloaded or rehosted.
2. Title, channel, length, the classifier's verdict as plain words ("Looks like a workout: 28 minutes, 7 chapters").
3. **Make this a workout** (primary, full width). Under it, a one-line explanation: "Reads the chapters and description on this
   phone first. No AI unless you ask."
4. **Chapters**: the timestamped list (tap seeks the player).
5. **Description**, collapsed.

### 7c. Make this a workout (full-screen task)

1. **Read** (deterministic): chapters and timestamps from the video, then lines of the description that look like
   exercises (names, "3x12", "45 sec on / 15 off"). Each detected line is matched to the exercise library by name and
   synonyms. Shows a spinner for a moment, never blocks the main thread.
2. **Review draft**: a list. Each row: matched exercise (green "Matched"), a new exercise (amber "New, will be created"), or
   unmatched text (grey "Skip" toggle); sets, reps or **timed** work, rest. Rows can be edited, reordered, removed.
   If nothing useful was found: "No chapters or exercise lines found." with two choices: **Improve with AI** and **Add by hand**.
3. **Improve with AI** (optional, only visible when AI is on and a key is set; **only on this tap**): sends the title, chapters,
   description and, if available, the transcript the user has permission to read, to the configured model; result must
   pass the schema validation (one repair retry, then a clear error; never show raw model text; `FEEDBACK-R2.md` B1). The
   result replaces the draft in the same review list, marked "AI suggested". Nothing is saved until the user taps Save.
4. **Save as**: **My workout** (a saved one-off, start any time) or **Program day** (picks the program). Then **Start now**.

Allergen and safety: not relevant to training. Data: saved videos and the source link are stored with the workout;
nothing downloaded.

### 7d. The workout filter (a deterministic classifier, with tests)

Inputs: YouTube `categoryId` (Sports and Howto lean positive; Music, Gaming, News lean negative), title keywords (workout,
routine, HIIT, pilates, yoga, mobility, stretch, "full body", "minute", sets and reps patterns) and negative keywords
(reaction, vlog, review, haul, "what I eat", unboxing, podcast, news, trailer), description cues (timestamps, "reps",
"sets", "equipment"), and duration (under 3 minutes or over 3 hours lean negative). It outputs a score and the three
labels above. Pure Kotlin in `domain/video/`, with a table-driven unit test with at least 30 labelled titles, a test for
each rule, and a test that an override ("Show everything") never changes the stored score. API cost note: a search call is
about 100 units of the default 10,000 per day, so about 100 searches per key per day; Recommended caches for 6 hours.

### 7e. Rules

* Source is the YouTube Data API v3 with a **user-supplied key** (stored with the other keys; used only in a request
  header or POST body, never in URLs or logs, as rule 6). Playback uses the official embedded player.
* Never download or rehost. Soft deletes on saved videos. Offline: saved metadata still shows; the player does not.
* No video feature runs in the background. No analytics.

## 8. Progress photos in the profile

Home: **You > Progress photos** (one home; there is no entry in Progress, and no entry in Settings except the reminder and the
export switch). Order: 1) **Latest**: the newest photo large with its date and angle chips (front, side, back, optional);
**Add photo** (camera or gallery). 2) **Timeline**: thumbnails by date, newest first. 3) **Compare**: pick two dates (side by
side), with a **slider** to wipe between them; under it the weight and measurements on those two dates, so the photos sit
"next to the weight and body trend" without a link out. 4) Footer: "Stored on this phone only. Not synced. Included in an
export only if you turn that on in Settings > Sync and export." Photos live in the app's private files, a `progress_photo`
table (date, angle, path, soft delete) added in `MIGRATION_2_3` with a `MigrationSchemaTest` case. Sync never carries them.

## 9. Old to new: every route

Verdicts: **moved**, **merged**, **removed**, **kept**, **new**. "Section" means a section of a tab root.

| Old route | Verdict | New home |
| --- | --- | --- |
| `Today` | kept, rebuilt | Four cards; macros always visible; water rows; no Edit Today |
| `Food` | kept, rebuilt | Dashboard, Log food, Log a drink, four sections |
| `Train` | kept, rebuilt | Sections Workout, Exercises, Videos |
| `Progress` | kept | Check-in card first; log and muscle view moved out |
| `More` | **removed** | The avatar opens `You` |
| `Onboarding` | kept | Copy updated (four tabs, avatar, Settings gear) |
| `MealPlans` | moved | Food > Plan section |
| `Recipes`, `RecipeHunter`, `RecipeImport`, `RecipeEditor`, `RecipeDetail`, `CookMode` | moved or kept | Food > Recipes section and its pushed pages (v4) |
| `EatingOut`, `PlanB`, `MenuSources` | merged | Food > Eating out section |
| `MealTemplates` | moved | Food > Meals section; Log food > Mine |
| `MyFoods` | moved | Log food > Mine |
| `FoodDetail` | kept, rebuilt | Adds the nutrition label and portion scaling; reached from every lookup |
| `FoodData` | **moved** | Settings > Data sources (Food database); the page disappears |
| `Allergens` (hub) | **removed** | The strip opens the one Allergens screen |
| `MyAllergens` | **merged** | Allergens (one screen) |
| `AllergenReference` | **merged** | Section of the same Allergens screen |
| `AllergenKeywords` | kept | Drill-in from an allergen |
| `Programs` | **moved and slimmed** | Train > Workout > Programs menu |
| `ProgramBuilder` | moved | Opened only from Programs |
| `ProgramTemplates`, `ProgramGenerator` | moved | Choices in Programs > New program |
| `ExerciseLibrary` | **moved, rebuilt** | Train > Exercises section; detail becomes a page |
| `Performance` | moved | Progress > Weight and body > Jump tests (shown when enabled) |
| `ActiveWorkout` | kept, rebuilt | Full screen, minimise, session bar |
| `WorkoutHistory` | **moved** | Train > Recent sessions > All sessions (Training log) |
| `WeeklyReport` | **merged** | Action inside Weekly check-in |
| `CoachCheckIn` | kept, renamed | Weekly check-in, from Progress only |
| `HealthConnect` | **moved** | Settings > Data sources |
| `BodyMetrics` | kept, slimmed | Weigh-in sheet; measurements in Progress |
| `Settings` | **rebuilt** | Nine groups, opened from You's gear |
| `Privacy` | **moved** | One line at the bottom of Settings |
| `Backup` | **merged, renamed** | Settings > Sync and export |
| `AiSettings` | **moved** | Settings > AI |
| `Help` | **moved, trimmed** | You > Help and tour |
| `Profile` + `YourPlan` | **merged** | You > Goals and plan; About you |
| `ScriptReference` | kept | From "?" in editors and from Help |
| New: `You`, `Drinks`, `DrinkCategory(id)`, `LogFood`, `WorkoutSummary(id)`, `MuscleVolume`, `TrainingLog`, `ExerciseDetail(id)`, `VideoDetail(id)`, `VideoToWorkout(id)`, `ProgressPhotos`, `PhotoCompare`, `WeeklyCheckIn`, `SettingsGroup(section)` | new | As described |

Sheets: the **Add food** sheet becomes the `LogFood` screen; the **Water and drinks** sheet becomes the Drinks page plus
Today's water rows (its settings move to Settings C); the **Exercise detail** sheet becomes a page; the Check-in settings
sheet is deleted (Settings G, I). All other sheets and the 25 dialogs stay.

## 10. Build order (for later tasks, not done here)

1. **Navigation shell**: avatar and You, Settings with nine empty groups, root-level shared routes, the transition and
   inset rules and `ScreenInsetsTest`. No data change.
2. **Settings move**: relocate controls group by group, deleting the old copies as each lands. Remove the backup UI.
3. **Today and Food**: macros row, water rows, dashboard, Drinks page, Log food as a screen, nutrition label (a
   pure `domain/nutrition/Label.kt` with tests).
4. **Drinks in the database**: `MIGRATION_2_3`, `food_category`, importers, tests.
5. **Train**: sections, Programs menu, active workout polish, summary, muscle volume (needs Liftosaur notes M1 and M2 first).
6. **Videos**: classifier and tests, key store, YouTube client with strict timeouts and caching, detail, parser, review.
7. **Progress photos**.

## 11. Open questions (kept to the minimum)

1. **Pill hidden on pushed screens?** I made this a decision because it removes the "under the pill" bug class. Default:
   yes. If you want the pill visible on one-level pages, the bottom padding rule in 2c already covers it.
2. **Old hydration entries into the food log?** Default: yes, migrate them into log entries of a seeded Water food, so there is one log.
3. **Where does the training log live?** Default: Train (using), with Progress holding long-term charts only.
4. **Videos Recommended: personalised from this phone's data?** Default: on, strictly local; the Settings switch turns it off.
5. **Allergens as the single exception to "all configuring in Settings".** Default: yes, it stays on the Allergens
   screen from the Food strip.
