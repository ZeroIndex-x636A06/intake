# Round 3 backlog: everything the owner sent from the phone, and where each item stands

Written 2026-11-11 so that nothing sent while work was in progress is lost. Read with `docs/FEEDBACK-R3.md` (items 1 to
15, the first batch). The owner's words are quoted or closely paraphrased. "Mine" marks an interpretation.

## Status at 06871e3

Branch `claude/optimistic-noether-fullk5`, head `06871e3`. Round 3 is merged on that branch. It is not yet in
`claude/app-build-k2ag1d` or `main`. Verified there: 1599 unit tests, 0 lint errors (app and wear).

- **Done:** A, B, C, D, E, F (parts 1 and 2), G and H.
- **Left:**
  - F's simpler layout. The mockup is at `design/mockup-r3/food-layout.png`; it waits for the owner to pick option 1, 2 or 3.
  - Watch workouts in the training log. This needs an `external_session` table.
  - "Find this product" goes to search only, not to scan.
  - Reading a PDF on the phone needs Android 15 or newer. Older phones use the AI path.
  - `pdfbox-android` was not added.
  - The "Open AI settings" button is only on Swap. It is not on Menu import, Recipe import, insights or Make this a workout.
- **Not seen yet:** nothing in round 3 has been seen on the owner's phone.

## State of the branch when this was written

- Branch `claude/optimistic-noether-fullk5`, head `e93b885`, pushed. Full verify passed on it: unit tests, 0 lint errors
  (app and wear).
- Done and merged from the first batch (FEEDBACK-R3 items 1 to 14): plan preview redesign, weights in the person's unit,
  known lifts in the generator, automatic easy week (`program.deloadEveryWeeks`, `migrateDeload_2_3`), program editor
  rows, today's workout by rotation, picture-first video player, Hide Shorts (setting 114), full YouTube key guide,
  paste-a-transcript, allergen chip on every search row, food language rule and setting (115) in Settings and
  onboarding, separate food and drink searches with the Drinks search under the predefined options.
- A preview APK of `e93b885` is built at `app/build/outputs/apk/preview/app-preview.apk` but has **not been sent**.
  Send a fresh one after the items below, not this one.
- Seen on the emulator from the first batch: the new plan preview, the known-lifts question, the program editor rows.
  **Not seen:** the Drinks search, onboarding's language step, the video page, anything from the food branch.
- `RESUME.md`, `docs/TASKS.md` and `docs/HANDOFF.md` do not mention round 3 yet. Update them at the end.
- Container tooling: SDK at `/root/android-sdk`; the Maven mirror must be restarted after every container restart
  (`cd /root/mvnproxy && setsid nohup python3 proxy.py &`, or `tools/cloud/mvn-mirror.py`); builds go through
  `flock /root/gradle.lock`; emulator AVD `phone30` via `/root/start-emu.sh` and `/root/emu-ready.sh`; driver
  `/root/ui.py` (also `tools/cloud/emu-ui.py`).

## A. AI calls fail: "No provider for this model meets your privacy setting" (blocks every AI feature)

What they sent:
- Swap > Find healthy options nearby > Search near me: "Search did not finish. No provider for this model meets your
  privacy setting. Pick another model, or relax the setting."
- Menu import of an Arby's nutrition PDF: the same failure ("this should be able to read this menu").
- Recipe import > Try with AI: "The AI step failed: No provider for this model meets your privacy setting."
  "Still getting this with ai requests too."
- Their Settings > AI screenshot: Photo estimates = DeepSeek V4.1 Flash (suggested); Recipe Hunter, Plan B finder,
  Menu import, Daily insight = Anthropic Claude Haiku 5.5 (suggested). All "Cheapest capable model from the suggested
  shortlist, not benchmarked yet". The photo estimate (DeepSeek) worked; every Haiku task failed.

What is known:
- `OpenRouterClient` sends `provider.data_collection = "deny"` for the default privacy setting (`NO_TRAINING`), plus
  `zdr` for zero retention, plus `require_parameters = true` whenever a schema or tools are sent. OpenRouter answers 404
  "No endpoints found matching your data policy" when no provider of that model satisfies all of it. The same 404 can
  come from the person's own OpenRouter account privacy settings (openrouter.ai/settings/privacy).
- The app's *suggested* model for a task is picked without checking the model's providers against the privacy setting.
  The benchmark already does that check (`BenchmarkCandidates`, `ModelCatalogRepository.ensureEndpoints`); the
  suggestion and the live call do not.
- `AiGateway.send` has no fallback: one model, one failure.

To do:
1. The suggested model for every task must be one whose providers meet the privacy setting (reuse the endpoint check
   the benchmark uses). Models that do not are shown as "Not available with your privacy setting", never suggested.
2. In `AiGateway`, on this failure, try the next privacy-compatible models from the task's shortlist (same privacy
   setting, never relaxed silently), then fail with a message that says what to do and has a button to Settings > AI.
3. The failure text must name both causes: the app's privacy setting, and the OpenRouter account's own privacy page.
4. Check `require_parameters`: it may be what excludes Haiku's providers when a schema or web tool is sent. If so,
   prefer a capable model that has a provider supporting the parameters, rather than dropping the requirement.
5. Tests with a fake HTTP layer: first model 404, second succeeds; all fail gives the new message.

## B. Menu import must read a restaurant's nutrition PDF

- The Arby's file is at `/root/.claude/uploads/19145525-a2ba-5270-99b1-80a63e1da922/f8bdfa47-Arbys_Nutritional_and_Allergen_NOV_2026.pdf`
  (10 pages, a real text layer). Each row: item name, "Contains: Milk, Egg, Wheat; May contain: Sesame" in red, then 12
  numbers: serving weight g, calories, calories from fat, total fat, saturated fat, trans fat, cholesterol mg, sodium
  mg, total carbohydrate, fibre, sugars, protein. Section headings are red bands (LIMITED TIME OFFERS, SLOW ROASTED
  BEEF ...). Column headings are rotated text.
- Today a PDF always goes to the AI model. With item A fixed the AI path should read this file.
- Wanted (owner's principle: works with no AI): read a text-layer nutrition table on the phone. Needs PDF text
  extraction: either the platform's `PdfRenderer` text API (Android 15+ only) or the `pdfbox-android` library (check it
  resolves; new dependency). Then a pure parser: a line that ends in a run of numbers is an item; the "Contains / May
  contain" text feeds the allergen engine as stated allergens (it can raise, never clear); columns are mapped from the
  heading words. Test the parser on text extracted from this file.
- Also from the food agent's report: imported menu items only appear inside Menu import. They are not in Log food
  search, not in Swap (the function `PlanBFinder.offlineOptions()` exists and is never called), not in the Eating out
  list. Owner asked "how would McDonald's full menu look here?": answer it and make imported menus usable: Swap first
  (small), then a "Restaurant menus" section in Log food logged per serving (about a day).

## C. Health Connect

What they said:
- "Health connect needs an allow all button. To allow all the permissions at one time."
- "The only place health connect data seems to be showing up is in the weekly checkin. Weigh ins that were already in
  my health connect are not showing up in the app, only place you can see sleep numbers is in weekly checkin. And it
  seems that the health connect tie in is mostly useless right now. This needs to be fixed and we need to take more
  advantage of the tie in and use it in more places where it could be useful."

To do:
1. One "Allow all" button that requests every read and write permission in one system sheet, beside the per-type
   switches.
2. Bug: weigh-ins already in Health Connect do not appear in the app (Progress weight trend, Today's weight card, the
   plan's starting weight). Find why (import window, de-duplication, or weight read but never written to the body
   table) and fix, with a test on the fake `HealthDataSource`.
3. Use the data where it helps. Candidates (mine): Today: steps, sleep last night, active calories; Progress: sleep,
   steps and resting heart rate charts next to weight; the calorie target when "add active calories" is on; the coach's
   recovery signals (already read sleep and resting HR); hydration's activity top-up; workouts from the watch in the
   training log. Show where each number came from.

## D. Reminders

- "I would also like to add settings for daily reminders for workouts, logging meals, and hydration reminders."
- To do: three reminder settings with times (workout on training days; meals at breakfast, lunch and dinner times, only
  if that meal is not logged; water through the day, only when behind), real notifications through WorkManager, a
  channel each, the notification permission asked when the first one is switched on. Each setting goes in the
  inventory, `SettingsRegistry` and `SettingsControls` (ids from 116). Also turn the progress-photo line into a
  notification (older backlog).

## E. The tour's scrolling

- "Your scroll is kinda jumpy. It should smoothly scroll the screen so that the box can highlight the section it wants
  to show."
- This is the Today tour (`ui/onboarding/Tour.kt`): it jumps the list to each target. Animate the scroll, wait for it to
  settle, then move the highlight box.

## F. Food detail and the Samsung Health comparison

What they said about the Food page (they like it: "Loving this screen"):
- "The serving size doesn't show as 1 packet and in the selector, it isn't made very clear how 43g is 1 serving."
  (Instant Oatmeal Apples & Cinnamon, Quaker, from Open Food Facts; the card reads "43g / 43 g".)
- To do: show the serving in words with its weight, "1 serving (43 g)" or the label's own "1 packet (43 g)" when Open
  Food Facts gives one; the stepper counts servings ("1 serving", "1.5 servings") with the grams under it.

What they like in Samsung Health (three screenshots: a meal page, a food day page, a meal with a photo):
- "I do like how it shows the numbers as a bar split into the different categories. If you could do that alongside of
  the numbers that we already have in our app, that would be great." A single bar split carb / fat / protein with the
  percentage under each part. To do: add it beside the existing numbers (food detail, the meal rows, the day).
- "I really like the simplicity of the food layout here. I know our app has more to it, but if we could find a way to
  get it looking more simple like the samsung health app I would like it. **If we do that, I want to have a mockup of
  the changes before a decision is made.**" To do: an HTML mockup (2 or 3 options) of a simpler Nutrition / meal layout,
  rendered to PNG, sent to the owner. Do not build it until they choose.

## G. The AI photo estimate

- The same bowl: Samsung's label value was 160 kcal (Quaker apples & cinnamon oatmeal, 43 g packet). Our estimate said
  "cooked oatmeal porridge 200 g, 190 kcal" plus "diced peaches (likely canned in juice) 90 g, 45 kcal", total 235
  kcal, with a dairy "may contain" note. "It way overestimated on the calories and got the completely wrong thing."
- "If it is unsure, rather than a failure or wrong estimation, i would prefer it to ask questions. Obviously if
  questions can be avoided great! But if not, I would rather it ask them."
- To do: let the model return up to about three short questions with choices when it is not confident ("Is this a
  packet of instant oatmeal? Plain / flavoured / home-made", "What are the orange pieces? Apple / peach / other",
  "Cooked with water or milk?"), show them as chips before the estimate, and send the answers back for the final
  numbers. No questions when confidence is high. Also offer "Find this product instead" (search or scan) when the
  answer names a packaged food, since the label is always better than a guess. The hidden-calorie guesses (butter,
  milk) must follow the answers, not be added by default.

## H. Recipe import from a link

- `https://www.eatwell101.com/tomato-zucchini-pasta-recipe?utm_source=Pinterest&utm_medium=organic` gives "No recipe
  found. This page has no recipe data we can read." Then the AI step fails with item A's error.
- To do: fetch the page and see why the no-AI reader misses it (no JSON-LD Recipe, a different shape such as a
  `@graph`, microdata, or a plugin's own markup). Extend the reader for that shape with a fixture test. Strip tracking
  parameters (`utm_*`) before fetching. The AI step works once A is fixed.

## I. Process

- "I know I have sent a lot since the last time you worked so please go through and check everything I have sent
  before starting. Once you have it written to a file, compact, then begin your work." This file is that record.
- Mockups come before big layout changes (item F). Everything else here is a fix or an asked-for feature and can be
  built directly.

## Order of work

1. A (AI privacy failure): it blocks menu import, recipe AI, nearby search and insights.
2. H (recipe link) and F's serving wording and macro bar: small, visible.
3. C (Health Connect: allow all, weigh-ins, more places).
4. D (reminders) and E (tour scroll).
5. G (photo questions).
6. B (PDF on the phone, imported menus in Swap and Log food).
7. F's simpler layout: mockup only, then wait for the owner.
8. Emulator pass on everything new, docs (`RESUME.md`, `TASKS.md`, `HANDOFF.md`, `FEEDBACK-R3.md`), fresh preview APK.
