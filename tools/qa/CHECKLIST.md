# QA feature checklist

Built from the nav graph (`ui/navigation/Destinations.kt`, `IntakeNavHost.kt`), the settings registry
(`domain/settings/SettingsRegistry.kt`, 126 settings in 12 groups) and `docs/IA-v5.md`. Where IA-v5 and the app
disagree, the app is the truth and the difference is itself a finding (S3 copy, or S2 if a feature is missing).

How to use it: each pass takes one or more areas, walks every line against the seeded emulator (`tools/qa/seed.sh`),
and logs findings in `docs/QA-LOG.md`. Tick a line only after you have looked at it on screen, not from the code.

Every line is a check. Lines marked **[ctx]** are context checks (is the content right), **[lay]** are layout checks
(does it look right), the rest are function checks (does it work). The "Every screen" block at the end applies to every
screen you visit; do not repeat it per area, just log the screen where it fails.

The seed user: male, about 195 lb, imperial units, **dairy allergy set to Strict**, target 2,260 kcal and 170 g protein,
21 days of food logs, 6 finished workouts, a weigh-in gap from 20 to 7 days ago. Its dairy traps are in
`tools/qa/README.md` "What the seed contains".

---

## A. First run, shell and navigation (14)

1. Cold start on a fresh install shows the welcome flow, not an empty Today. Each step has Back and Skip where the
   copy says so. Quitting midway and relaunching resumes at the saved step.
2. After the seed import the app opens on Today with data; the welcome does not come back (existing data skips it).
3. Floating pill bar shows Today, Nutrition, Train, Progress; the active tab is clearly marked. [lay]
4. Tab roots: avatar top right opens You; no back arrow on a tab root. [lay]
5. System back on a tab root other than Today goes to Today; on Today it leaves the app.
6. Tapping the current tab's pill item pops that tab to its root.
7. Switching tabs and back keeps each tab's scroll position and sub-screen (each tab has its own graph).
8. Pushed screens hide the pill and have a back arrow, one-line title, and nothing under the status bar or gesture bar. [lay]
9. Push and pop transitions are a plain horizontal slide; no shrink or flash of the screen underneath.
10. Rotate to landscape on Today, Nutrition, Train, Progress: no clipping, pill becomes a rail or still clears content. [lay]
11. Large font (Settings > Display > Font size largest) and display size largest: no clipped text on the four tab roots. [lay]
12. Dark theme and light theme: every tab root readable, no white flash on screen change, contrast of grey text on cards. [lay]
13. Active workout minimised: a session bar appears above the pill on Train and Today and resumes the workout.
14. A deep link or notification tap (if reproducible from `adb shell am start`) lands on the right screen with a back path.

## B. Today (24)

1. Date title with previous and next arrows; going back a day shows that day's meals, water and weight; Next is
   disabled or inert on today. Arrows share state with Nutrition's day.
2. Calories ring: "N kcal left" equals target minus eaten for the day, and goes to "over" wording, not a negative number,
   when exceeded. [ctx]
3. Protein, fat, carbs rows show eaten / target g and the bars match the numbers. Target protein reads 170 g, kcal 2,260. [ctx]
4. Sum check: add the entries in the Meals card by hand; the ring's eaten kcal matches within rounding. [ctx]
5. Log food button and scan icon open Log food (Search) and Log food (Scan).
6. Next up card: shows only what is due (planned meal, today's workout); hidden when empty. Start goes straight into
   the session.
7. Water tile: litres so far against the target, in the volume unit from Settings (fl oz in the seed). [ctx]
8. Water tile rows: drinks as rows with minus and plus; minus removes the last of that drink, plus adds one more of
   the same size; the total and the ring on Nutrition change. [ctx]
9. "Log a different drink" opens Drinks and back returns to Today.
10. Weight tile: last weigh-in in lb; "Weigh in" sheet saves a value and the tile updates; the weight is stored as kg
    (check the CSV export). [ctx]
11. Today after the 2-week gap in weigh-ins: the tile says how old the last weigh-in is rather than implying today. [ctx]
12. Meals card: four compact rows (breakfast, lunch, dinner, snack) with entries and a plus; plus opens Log food for
    that slot.
13. Tapping an entry opens its detail or edit; edit grams rescales kcal and macros proportionally. [ctx]
14. Delete an entry (soft delete), then Undo from the snackbar restores it; the totals follow both ways.
15. Entry logged with "Logged despite: dairy" shows that text and the allergen warning is still visible, not cleared. [ctx]
16. Today card switches (Settings > Appearance and units > Today's cards) hide and show the cards.
17. Reminders: the monthly export reminder appears only when 30 days have passed and sync is not paired. [ctx]
18. Coach or daily-insight card: with AI off it shows nothing AI-generated and no "AI" wording that implies it ran. [ctx]
19. Empty state: a day with no entries (go back before the seed data) shows a clear empty state, not zeros that look
    like data. [lay]
20. Long food names (rename a log entry to 80 characters) ellipsize or wrap without pushing the kcal off screen. [lay]
21. The session bar and the pill do not overlap the last card when scrolled to the bottom. [lay]
22. Pull or scroll performance with 21 days of data: no jank spike, no ANR in logcat.
23. Time zone: change the emulator time zone (`adb shell setprop persist.sys.timezone Asia/Tokyo` then restart the app);
    entries stay on their original local day. [ctx]
24. Day rollover at midnight: leave the app open across 00:00 (or change the clock); Today moves to the new day.

## C. Nutrition tab (42)

Dashboard
1. Allergen strip at the top reads "Avoiding milk" or the app's wording for dairy, with Edit; it opens Allergens. [ctx]
2. Dashboard rows in order (calories, protein, carbs, fibre, fat, saturated fat, sugar, salt or sodium); values equal
   the Today numbers. [ctx]
3. An unknown nutrient is "not listed", not 0, and the card says how many foods lack the value. [ctx]
4. "More nutrients" expands and collapses.
5. Hydration line shows litres against the target and opens Drinks. [ctx]
6. Day arrows change the date and Today follows.
7. Salt or sodium row follows Settings > Nutrition > "Show salt or sodium". [ctx]
8. Sections switch: Plan, Recipes, Meals, Eating out; the sticky switch stays under the header when scrolled. [lay]

Log food
9. Log food opens full screen with a slot chip; changing the chip moves where the entry lands.
10. Search "milk": results show an allergen chip; dairy items are marked and cannot be added silently. [ctx]
11. Search "oat milk" and "almond milk": the plant milk note ("check the label") appears, not a hard dairy block. [ctx]
12. Add a dairy food (cheddar cheese from the seed): the allergen warning sheet appears with a way to cancel. Cancel
    logs nothing. [ctx]
13. "Choose anyway" (or the app's override wording) logs it with "Logged despite: dairy" and the warning stays. [ctx]
14. Scope chips All, Foods, Drinks filter the same database.
15. Tabs Find, Scan, Mine, Quick add: Mine lists my foods, saved meals, recipes. Empty states for each. [lay]
16. Quick add: calories and macros by hand; blank macros are stored as "not listed". Estimate from photo is present
    but with no key says so and does nothing else. [ctx]
17. Scan with no camera in the emulator: a clear message, no crash. Barcode typed by hand (if offered) looks up.
18. No network: a search that needs the network fails with a plain message; local foods still search. [ctx]
19. Footer line "Allergen check runs before saving." is accurate: confirm it actually runs for a typed food. [ctx]
20. Keyboard: the focused field scrolls into view, the Add button is not hidden by the keyboard. [lay]

Food detail and label
21. Food detail shows the nutrition label rescaling live as the portion changes; Per portion and Per 100 g toggle. [ctx]
22. Portion units follow Settings (g or oz). [ctx]
23. Ingredients and allergens block underlines matches; "Ranch dressing" (buttermilk, no milk tag) must be flagged as
    dairy. "Dark chocolate 70%" ("may contain milk") must be a may-contain, not clear. "Protein bar, chocolate"
    (whey protein isolate) must be flagged. [ctx]
24. "Milk chocolate bar", "Greek yogurt", "Butter", "Whey protein powder", "Cheddar cheese", "Pepperoni pizza slice" are
    all flagged dairy. "Almond milk" and "Oat milk" are not a hard block. [ctx]
25. Edit a food, save; history entries keep their old values (macros are copied at log time). [ctx]
26. Delete is a soft delete (food disappears from lists, old log entries remain). [ctx]
27. Favourite star toggles and persists.
28. Menu "Contribute to Open Food Facts" is off or explains why when the setting is off. [ctx]

Drinks
29. Drinks page: hydration summary, drink selector chips, size quick buttons in fl oz (8, 12, 16.9, 20) for imperial. [ctx]
30. One tap logs and shows "Added ... Undo"; undo removes both the food entry and the water. [ctx]
31. Custom size field accepts a number and rejects empty or zero.
32. "My drinks": the seeded "Coffee, black + 3 tsp sugar" and "Black tea + 2 tbsp oat milk" are listed; open one,
    the add-ins show; sugar 3 tsp is about 12.6 g, kcal about 51 for 240 ml. [ctx]
33. Create a new custom drink (latte with milk): the dairy tag is added and the allergen warning fires on log. [ctx]
34. Drink search (DrinksSearch) with an empty result shows an empty state. [lay]
35. Caffeine total on the Drinks summary equals the sum of the day's coffee and tea. [ctx]

Plan, Recipes, Meals, Eating out
36. Recipes list shows the 5 seeded recipes with allergen chips: fajita bowls clear, mac and cheese contains,
    salmon clear, chili with sour cream contains, grandma's soup unknown or "not checked". [ctx]
37. Recipe with no nutrition listed shows "not listed" and not 0 kcal; logging it asks for macros or logs unlisted. [ctx]
38. Recipe detail: servings stepper rescales ingredients and per-serving numbers; per-serving numbers equal the
    ingredient totals divided by servings. [ctx]
39. Cook mode keeps the screen awake, steps are readable at arm's length, back exits cleanly.
40. Recipe editor: add an ingredient, save, allergen status recomputed locally; a dairy ingredient raises it. [ctx]
41. Recipe hunter and recipe import (URL): with no network, a plain failure message; with a pasted page, parses. [ctx]
42. Meals (templates): "Usual breakfast" and "Post-workout shake" list with kcal; Log in one tap; editor works;
    NFC write option says what it needs and does not crash with no NFC. [ctx]
43. Meal plans and shopping list: empty state with the seed (no plan), import entry point is clear; shopping list
    empty state. [lay]
44. Eating out: the 2 saved options show their allergen chip; "Choose anyway" on a CONTAINS option uses the warning
    dialog; imported menu "Sunrise Diner (invented)" lists 8 items and each item's verdict matches its ingredients
    (cheeseburger, garden salad with ranch, pancakes, chocolate shake are dairy). [ctx]
45. Menu sources: add a PDF source with `tools/qa/files` PDFs (see README); the parser reports a count and a review
    state; a PDF that is an allergen chart (Ireland) parses into per-allergen columns. [ctx]
46. Swap sheet ("something else instead of the planned meal") lists options and blocks dairy ones. [ctx]
47. Healthier nearby with no location: a clear "needs location" state, no crash. [ctx]

Allergens screen
48. Your allergens switch list, search, "Add your own"; Dairy is on and Strict; changing to Avoid and back persists. [ctx]
49. Keywords per allergen: list shows built-ins and custom; built-ins can be switched off but not deleted; the off
    switch warns. [ctx]
50. Hidden-ingredient guide is readable and collapsible.
51. Add a custom keyword "ghee": a food named "ghee" is then flagged; remove it and it is not. [ctx]

## D. Train tab (38)

Workout section
1. Train opens on Workout with today's session (the program's next day: Upper B or Lower A per the seed's
   currentDayIndex) and one big Start button. [ctx]
2. "Other ways to start" lists the options (empty workout, program day, video, generator).
3. This week (muscle volume) numbers match the week's logged sets; tap opens Muscle volume. [ctx]
4. Recent sessions list the 6 seeded sessions, newest first, with duration, volume and date. [ctx]
5. Start a session: set rows show last time's weight and reps as the reference. [ctx]
6. Log a set: weight stepper uses plate math (bar 45 lb or 20 kg per Settings); reps stepper; RPE; Done starts the rest
   timer. [ctx]
7. Rest timer counts down, vibrates if enabled, can be skipped or extended; survives screen off.
8. Add warm-up set, add set, delete set, reorder exercises, swap an exercise, add a note.
9. Weights display in lb (imperial) and are stored in kg (check CSV). 135 lb shows 135, not 134.9. [ctx]
10. Minimise the workout (back arrow): session bar shows; kill and relaunch the app: the session is resumable. [ctx]
11. Finish sheet: shows summary and a discard option only inside it; discarding asks for confirmation.
12. Workout summary: totals, PRs, volume per muscle, share or save; numbers agree with the sets logged. [ctx]
13. Workout history (training log): 6 sessions, tap opens detail with every set; edit a set; delete is soft. [ctx]
14. Bodyweight exercises (Pull-Up) show reps only, or bodyweight + added weight, never "0 lb". [ctx]

Programs
15. Programs menu lists "Home gym upper/lower" as active, with days and exercises; Programs "⋯" holds actions only. [ctx]
16. Program builder: add a day, add an exercise from the library, targets for sets, reps, RPE, rest; save.
17. Program templates list and import; Program generator (Make me a program) honours the home-gym equipment: no
    machine-only exercises suggested. [ctx]
18. Progression suggestions after the seeded sessions are sensible (load goes up only when reps were hit). [ctx]
19. Script language: import a short Intake script from the wiki (Script reference), errors are readable and point at
    the line. [ctx]

Exercises and videos
20. Exercise library: search, filter by muscle and equipment; equipment filter matches the home-gym profile. [ctx]
21. Exercise detail: description, muscles, history chart, PRs; custom exercise can be added with a photo path
    (photo is not in backup, noted in docs). [ctx]
22. Exercise images load or show a placeholder; no broken-image icon. [lay]
23. Videos section with no YouTube key: shows the "needs your own key" state and explains where to add it; no crash,
    no empty white page. [ctx]
24. Videos section copy: the group blurb says "Intake never downloads a video" while Settings > Videos has an
    "Offline downloads and direct playback" switch. The copy must not contradict the switch. (Known stale copy.) [ctx]
25. Saved videos from the backup (none seeded) empty state. [lay]
26. Video detail and Make a workout from a video: with no key and no network, a clean error.
27. Performance (jump tests) appears only when switched on in Settings > Training; add a test, see it in Progress. [ctx]

Layout
28. Set rows at largest font: weight and reps fields not clipped, Done reachable. [lay]
29. Landscape during a workout: fields still usable. [lay]
30. Long exercise names (custom 60 chars) wrap. [lay]
31. Empty states: no program (deactivate it), no sessions, no exercises matching a search. [lay]
32. Ripple shape: pressed state on set rows, chips and cards is clipped to the shape, not a square ripple over a
    rounded card. [lay]
33. Dark theme: set rows, RPE chips, the rest timer ring, the PR badges. [lay]
34. Active workout screen stays awake and does not draw under the cutout. [lay]
35. Number fields reject letters and negative numbers; empty save does not crash.
36. Session bar text is not truncated for the longest day name. [lay]
37. Back from Workout summary goes to Train, not into the finished workout.
38. Time shown for a session matches wall clock in the device time zone (seed sessions at 17:30 local). [ctx]

## E. Progress tab (26)

1. Weekly check-in card first, with a week range chip and Open check-in; highlighted only when due. [ctx]
2. Weight and body: range chips (4 w, 12 w, 6 m, 1 y); the chart shows the gap from 20 to 7 days ago as a gap, not
   a straight line pretending to be data. [ctx]
3. Weight trend value and the goal line (goal 185 lb) read in lb; the weekly rate is plausible for the seeded data
   (about -0.5 lb a week or flat), not wild because of the gap. [ctx]
4. Weigh in from Progress; add measurements (waist, chest, arm...) in inches; stored in cm. [ctx]
5. Body metrics (BodyMetrics) list, edit and delete (soft). Empty state with none. [lay]
6. Body fat entry optional; leaving it blank is saved as none, not 0. [ctx]
7. Nutrition block: calorie and protein adherence over the range; average macros; average hydration; numbers agree
   with a hand average of the seeded days. [ctx]
8. Days with no food logged are excluded from averages or shown as missing, not counted as 0 kcal. [ctx]
9. Training block: estimated 1RM lines for the main lifts, PR list, volume trend, sessions per week; the 6 seeded
   sessions give plausible 1RMs (bench 95 lb x 8 is about 120 lb, not 300). [ctx]
10. Jump tests show only when switched on.
11. Weekly check-in screen: summary lines for food, body, training agree with the data; suggestions have Apply and
    Dismiss; Apply says what it does and changes only that. [ctx]
12. Check-in "⋯": Export or print this week produces a PDF or share sheet; Past check-ins lists them. [ctx]
13. Reword with AI appears only under Settings > AI and does nothing with AI off. [ctx]
14. Progress photos (from You): add a photo from gallery (push an image to the emulator), compare two, delete; photos
    are metadata-only in a backup (documented). [ctx]
15. Photo compare with fewer than 2 photos: empty state. [lay]
16. Charts: axis labels not overlapping, values readable in dark theme, tap or drag shows a value, a single data
    point still draws. [lay]
17. Range chip change is instant and keeps the chosen range after rotating. [lay]
18. TDEE or targets "why" text on Progress matches You > Goals and plan. [ctx]
19. Units: switch Settings to kg and cm, every Progress number and axis changes with it, nothing still says lb. [ctx]
20. Switch the week start or check-in day; the card range follows.
21. Long ranges (1 y) with 5 weeks of data: no crash, axis is not absurd. [lay]
22. Landscape chart view. [lay]
23. Empty state for every card with a fresh install (before seeding). [lay]
24. Health Connect numbers (steps, active calories) appear only when granted; otherwise nothing implied. [ctx]
25. Scroll to the end: the last card clears the pill. [lay]
26. Dark theme contrast for chart lines and grid. [lay]

## F. You, Goals and plan, Profile (16)

1. You: profile card (initial, name "Sam Tester", age, height 6 ft, weight in lb, goal in one line). [ctx]
2. Goals and plan: calories 2,260 and protein 170 with the reason line; numbers are read-only here and the manual
   override lives in Settings > Nutrition. [ctx]
3. Phase, pace, goal weight and rate editable; changing phase recomputes the plan text but keeps a manual override. [ctx]
4. Profile (About you): sex, birth year, height (in feet and inches for imperial), body fat optional, build, activity.
5. Height shown as 6 ft 0 in for 183 cm; edit in inches stores cm. [ctx]
6. Protein g per kg shown (1.9) and 1.9 x 88.4 kg is about 168 g. [ctx]
7. YourPlan explanations are accurate for the chosen phase (a cut explains a deficit, never "surplus"). [ctx]
8. Calorie floor warning appears when a manual target is below the floor and "Allow calories below the floor" is off. [ctx]
9. Advanced nutrition (carb cycling, refeed, diet break) fields validate numbers and explain themselves.
10. Help and tour: replay the tour, show tips again, show welcome again; each works and returns cleanly. [ctx]
11. Scripts wiki opens (ScriptReference), anchors scroll to the right heading, back works. [ctx]
12. Privacy screen text matches `docs/PRIVACY.md` (what leaves the phone, nothing without a tap). [ctx]
13. Gear opens Settings; back from Settings returns to You.
14. Avatar initial is from the display name.
15. Long display name (60 chars) wraps in the profile card. [lay]
16. Empty profile (fresh install, skipped welcome) shows prompts to fill the basics instead of NaN or 0. [ctx]

## G. Settings (one line per setting: change it, leave and return, confirm it stuck, confirm the effect)

12 group pages from the Settings list; each row shows a one-line summary ("lb, in, fl oz, System"). The count in
brackets is the registry's. Every setting needs three checks: persists after restart, does what its blurb says, and its
off or empty state is sane.

- **Appearance and units (7)**: Weight; Length; Drinks and water; Food portions; Theme; Use wallpaper colours; Reduce
  motion. Switch units and visit Today, Nutrition, Train, Progress, You: no stale unit anywhere. [ctx]
- **Nutrition (15)**: Work my targets out from my data; Calories by hand; Protein; Fat; Add active calories to my
  target; Show salt or sodium; Eat out nearby: how far; Recalculate from my data; Allow calories below the floor; Carb
  cycling; Carb cycle shift; Refeed days per week; Diet break every; Contest prep weekly loss; Reverse diet calories
  per week. The last two show only in their phase. Turning auto targets off keeps 2,260 and 170 editable; on, the
  numbers are recomputed and the seed's manual target is not silently lost. [ctx]
- **Water and drinks (11)**: Daily water target; Adjust the target for the day; Training-day increase; Adjust water for
  weather (no location: asks or explains); Weather location; City or ZIP code; Forget this location; Quick sizes;
  Drink values (table: hydration factor, caffeine, strength); Measure my sweat rate; Use my measured sweat rate.
- **Training (8)**: Default rest; Vibrate when rest ends; Bar weight; Plates available per side; My equipment preset;
  My equipment items (the seed: home gym of 9 items, preset shows "Pick my own"); Show jump tests in Progress; Weekly
  sets target per muscle region. Changing equipment changes what the generator and exercise filter offer. [ctx]
- **Health Connect (1)**: Allow all Health Connect permissions. On Android 11 the app shows "not available" or asks to
  install it; on API 34+ the permission sheet opens. Record the exact state seen. [ctx]
- **Food database (6)**: Search Open Food Facts while typing; Food search language; Show products in other languages;
  Allow contributing; OFF username; OFF password. With no network, searching with the switch on fails politely.
- **Videos (4)**: Show only workouts by default; Personalise Recommended; Hide Shorts; Offline downloads and direct
  playback. The group blurb "Intake never downloads a video" contradicts the last switch (see D.24). [ctx]
- **Reminders (12)**: Weekly check-in notification and day; Progress photo reminder; Export reminder; Workout reminder
  and time; Meal reminders and the three times; Water reminders and hours. Turn each on; notification permission
  prompt on API 33+ appears at the right time; a reminder actually posts (set the time to now plus 2 minutes). [ctx]
- **Sync and export (10)**: Server URL; Pairing code; Name this device; Linked devices; Stop syncing here; Export
  passphrase; Include progress photos; Export JSON; Export CSV; Import a backup. Export with and without a
  passphrase; import the export back: preview lists changes, nothing deleted, wrong passphrase asks again. [ctx]
  The CSVs open in a spreadsheet with the right units in the header (lb, in). [ctx]
- **AI optional (9)**: Use AI features (off); Monthly budget; Privacy; Suggest free models; Daily insight; Reword my
  check-in; Refresh saved menus weekly; Reset usage counters; Run benchmark. With no key, turning AI on explains it
  needs a key and nothing runs. [ctx]
- **API keys (3)**: OpenRouter, USDA, YouTube: paste, save, test, remove. Test with a fake key shows a clear invalid
  message; the key never appears in a URL or in logcat (grep logcat for the fake key). [ctx]
- **About and help (7)**: Replay the tour; Show the welcome again; Show screen tips again; Script language help; What
  leaves this phone; Credits and licences; Version (matches the build). [ctx]

## H. Backup, export and import (12)

1. Settings > Sync and export > Import a backup opens the system picker; choosing `intake-qa-seed.json` shows the
   preview (rows per table, settings line "Settings: units, targets, profile, ...") before writing. [ctx]
2. Importing the same file twice changes nothing the second time (preview says 0 changes). [ctx]
3. Importing never deletes local rows.
4. A non-Intake JSON file and a truncated file show a plain error and change nothing.
5. An encrypted export asks for the passphrase; a wrong one asks again, a right one imports.
6. Export file name and date stamp; share sheet or save dialog works.
7. Round trip: export from the seeded app, uninstall, install, import: Today matches the screenshot from before. [ctx]
8. Settings in the export (units, profile, theme) come back; keys do not. [ctx]
9. Soft-deleted rows are in the export and stay deleted after import. [ctx]
10. Import of a schema newer than the app is refused with the "update the app" message. [ctx]
11. Export reminder timestamp updates after an export.
12. Large import (seed has about 700 rows) completes without ANR on this slow emulator.

## I. Allergen safety cross-checks (the most important block; any failure is S1)

Run these on a fresh pass after every fix that touches foods, recipes, menus, drinks or allergens.

1. Every seeded dairy food is flagged on search, detail, log and Today (see C.24). [ctx]
2. Hidden dairy: ranch dressing (buttermilk), whey protein isolate bar, protein powder, chocolate (may contain milk),
   casein, ghee, "natural flavor" with milk tag. None may show as clear. [ctx]
3. Plant milks (oat, almond, soy, coconut) are a check-label note, not a block; "oat milk" in a custom drink adds that
   note only. [ctx]
4. Recipes: "Chicken fajita bowls" and "Salmon, sweet potato and broccoli" are clear; "Baked mac and cheese", "Turkey
   chili with sour cream" are contains; add butter to a clear recipe and the status flips to contains. [ctx]
5. Meal templates with a dairy item block or warn on log; swap sheet, eat-out options and menu items do the same. [ctx]
6. An imported menu or file can raise a warning, never clear one: edit the menu item text to remove "cheese" and
   confirm a stored CONTAINS stays until re-scanned from ingredients, and that a re-scan only ever raises. [ctx]
7. Open Food Facts tags and ingredient panels are never downgraded by a free-text "dairy free" claim in a name or
   description ("dairy-free cheese" with milk in the panel stays flagged). [ctx]
8. Change strictness Strict to Avoid and back: the verdicts and copy follow; set no allergens: the strip says so and
   nothing claims "dairy-free". [ctx]
9. Nothing in the UI uses the words "dairy-free" or "safe" for a food unless the local engine cleared it from a real
   ingredient panel. [ctx]
10. AI off or no key: allergen verdicts are identical (the engine is local). [ctx]
11. Allergen state after a backup import equals the state before it (profile row and keywords). [ctx]
12. Wear and notifications never show a food as safe that the phone flags.

## J. Every screen (apply while visiting any screen above)

Layout
1. Top bar: back arrow, one-line title, at most two actions; title not clipped by the avatar or actions. [lay]
2. Nothing drawn under the status bar, camera cutout, gesture bar or the pill. [lay]
3. Last item of every list clears the pill (tab roots) or the gesture bar (pushed). [lay]
4. Touch targets are 48 dp or more; adjacent controls do not overlap. [lay]
5. Ripple and pressed state are clipped to the control's shape (no square ripple on a rounded card or chip). [lay]
6. Long text: 80-character names, long notes, long recipe titles wrap or ellipsize, never push controls off screen. [lay]
7. Largest font and display size: no clipping, no overlapping text. [lay]
8. Dark theme: text contrast at least 4.5:1 for body, 3:1 for large text and icons; no pure-white cards; dividers visible. [lay]
9. Light theme: nothing relies on a dark-only colour (white text on white). [lay]
10. Empty state for every list: icon or line of text saying what goes here and what to tap; no blank screen. [lay]
11. Loading and error states exist and are not stuck (network calls with no network end within a timeout). [lay]
12. Keyboard: fields not hidden, IME action does something sensible, Done dismisses. [lay]
13. Landscape and split-screen: usable. [lay]

Function
14. Every button does something or is visibly disabled with a reason.
15. Back arrow, system back and the back gesture agree.
16. Rotating or backgrounding mid-edit does not lose a typed value or crash.
17. No logcat `FATAL EXCEPTION`, ANR or `StrictMode` violation (see README).

Context
18. Units follow Settings everywhere (lb or kg, in or cm, fl oz or ml, g or oz). [ctx]
19. Numbers add up (rings, totals, averages, per-serving). [ctx]
20. Copy is accurate to what the app does now: no feature named that no longer exists, no "AI" shown when none ran,
    no "Intake never downloads a video" next to a download switch, no "sheet" for a screen. [ctx]
21. Dates and times in the device zone and format; no "Today" for yesterday. [ctx]
22. Nothing says "sync" or "backup" in a way that implies a remote service the app no longer has (the remote WebDAV
    backup is gone). [ctx]

---

Totals (counted lines): A 14, B 24, C 51, D 38, E 26, F 16, G 12 groups (126 settings), H 12, I 12, J 22.
