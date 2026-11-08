# Intake v3 — overhaul brief

This document records the user's v3 feedback (2026-11-08) and supersedes
docs/SPEC.md and docs/SPEC-amendments.md wherever they disagree. Every
constraint in SPEC §3 still holds:
- The app is offline-first.
- Nothing is hard-deleted.
- Timestamps are UTC epoch millis.
- Values are stored in kg and cm.
- Migrations are never destructive.
- Keys are kept in EncryptedSharedPreferences and sent only in headers.
- A model can raise an allergen warning but never clear one.

## What the user said, condensed

1. The app is built too closely around one person's use case. It must work
   for a regular person and also be powerful enough for a bodybuilder:
   - simple by default;
   - depth behind "Advanced";
   - fully usable by hand, with no imports and no AI;
   - AI is only a convenience.
2. It feels bare. Today it only works if you import a plan or a program.
   Training starts from built-in programs instead of from scratch.
3. Menus are too busy and badly grouped:
   - food script docs sit under Train;
   - important features such as menu import are hidden in More.
4. Navigation:
   - Replace Plan with **Food**. Every food feature lives there, in clear
     sections, and the same goes for Train, Progress and the rest.
   - The bottom bar becomes a **floating pill** (reference: a dark rounded
     pill above the content, icon over label, the selected item on a
     lighter inner pill).
   - Sliding a finger along the pill switches tabs.
5. First run is confusing: there is no onboarding and no tutorial. Things
   need to be simpler to understand, with fancier animations.
6. Allergens:
   - A predefined list of common allergens with keywords ships with the
     app.
   - Users can add custom allergens.
   - Users can add custom keywords to any allergen, built-in or custom.
7. The user's plan, written for a dairy allergy, imports with 4 blocked
   items, 9 "may contain" items and 14 "check the label" items. Most of
   these are false alarms. See "Allergen false alarms" below.
8. Meal plans:
   - They must not be fixed at 12 weeks. A plan can be any length.
   - A plan can loop on a schedule.
   - Predefined plans are still supported.
9. Recipe Hunter must import a recipe directly from a URL.
10. Food search:
    - It has to work without an API key, using an open food database.
    - Foods added locally can be contributed back to that database.
    - Barcode scanning does not work and must be fixed.
11. Baseline week:
    - Track the user's normal stats for a week.
    - Then notify them that Intake has enough data to build a training
      plan, a nutrition plan, calorie and macro goals, and a water target.
    - The user picks a body type and a phase (cut, bulk, maintain and so
      on).

## Information architecture

The floating pill has five destinations: **Today · Food · Train · Progress · More**.
Each hub screen is a short list of clearly named sections. There is no wall
of cards.

| Hub | Sections |
| --- | --- |
| Today | Calories and macros left, water, today's planned meal, today's workout, quick log. Nothing else. |
| Food | **Log** (search, barcode, my foods, meal templates and NFC) · **Meal plans** (active plan, builder, import/export) · **Recipes** (my recipes, add by hand, import from URL, Recipe Hunter as an optional AI) · **Eating out** (saved options, Plan B, menu import) · **Allergens** (my allergens, keywords, hidden-ingredient reference) · **Food script help** |
| Train | **Today's workout / start a workout** (including freestyle) · **Programs** (mine, build from scratch, templates as an option, import/export) · **Exercises** (library, add custom) · **Performance** (jump tests) · **Program script help** |
| Progress | Weight trend, body measurements, calories and adaptive TDEE, training volume and PRs, weekly report PDF, Health Connect data |
| More | Profile and goals (body type, phase, targets, baseline) · Settings (units, theme, reduce motion) · Backup and sync · AI (optional) · Privacy · Help (replay the tour, all script docs) |

The script docs split by topic: food pages are reached from Food, program
pages from Train, and the full set from More → Help.

## Allergen false alarms in the user's plan

Each case below is from the actual import preview.

**Negated mentions** are currently a hard block:
- "sauce made with oil, **not butter**"
- "**checking the bun for milk**"
- "Frito pie **without the cheese**"
- "**skip the cheese** and sour cream"

The author is avoiding the allergen, so these should be an informational
"mentioned as avoided" note. They are not a block and are not counted as
problems.

The rule is clause-local and deterministic. Negators include no, not,
without, skip, hold the, minus, "X-free", "free of X" and "check(ing) … for
X".

Some claims never negate:
- "lactose-free" still contains milk;
- "non-dairy" can contain casein (it is soft-flagged);
- "dairy-free" on a substitute is soft-flagged as check-the-label, because
  some vegan cheeses contain casein.

**Self-hits.** The word "dairy" inside "dairy-free mac" must not be
reported as the allergen "dairy".

**Plant milks and substitutes** are currently "may contain": oat, almond,
soy, coconut, rice and cashew milk; vegan, dairy-free or plant butter and
cheese. They should be check-the-label, with a reason.

**Preview display:**
- One row per item, never duplicated (week 9 is shown twice today).
- Show the full text, not the text cut off at 40 characters.
- Group by tier, with "Blocked" open and the softer tiers collapsed behind
  counts.
- Use plain wording, for example "Mentions butter as something to avoid".

## Engines (no AI required)

- **Targets.**
  - Day one uses Mifflin-St Jeor.
  - After the baseline, the TDEE comes from logged intake and the weight
    trend (SPEC §8).
  - Phase sets the calorie offset: cut, lean bulk, bulk, maintain, recomp,
    contest prep, reverse diet.
  - Protein is set in g/kg. Fat has a floor. Carbs take the rest.
  - Advanced options: carb cycling, refeeds, diet breaks and custom g/kg.
- **Water.** Body weight × ml/kg, plus a training bonus, plus a climate
  setting.
- **Training generator.**
  - Inputs: days per week, session length, experience, equipment, goal and
    weak points.
  - The split comes from rules: full body, upper/lower or PPL.
  - Weekly sets per muscle fall within evidence-based ranges, adjusted by
    experience.
  - Rep ranges and the progression rule follow the goal.
  - A deload runs every 4–6 weeks.
  - The output is a normal editable Program.
- **Body type.**
  - A simple build picker: slim, average, muscular, heavier. It sets
    starting assumptions and copy only.
  - An optional body-fat % estimate refines the Katch-McArdle calculation.
  - The classic ecto/meso/endomorph labels may appear as familiar names,
    but they are not used as physiology.

Every generated number shows a one-line "why", and every one can be edited.

## Notes for implementers

- Run compile, unit tests and lint, with 0 lint errors, before every merge.
- No literal U+FEFF characters in source.
- No new Maven dependencies. Maven Central rate-limits Gradle here.
- Data files may be bundled. USDA FoodData Central is public domain.
  Open Food Facts is ODbL: use it online and do not bundle it.
- Never commit the user's personal plan (release/ is git-ignored).

## Additions (2026-11-08, second round)

**Goal.** The aim is the smartest yet simplest health app: deep insights, but simple
enough for anyone. It should be close to replacing a trainer, and still work with no AI.

### Hydration v2: weather and drinks

- **Weather.** Fetch the day's high temperature and humidity from Open-Meteo. It is
  free and needs no key. Raise the water target in heat and humidity.
  - Location is opt-in. Either it is approximate (rounded to about 10 km, fetched
    once a day), or the user types a city or ZIP code.
  - No location means no weather adjustment. The app says so.
  - Add this to PRIVACY.md.
- **Drinks count by what they actually do.** Each drink type has a hydration factor
  taken from the beverage hydration index research:
  - water, tea and coffee at normal amounts ≈ 1.0
  - milk and oral rehydration drinks > 1.0
  - beer and wine count less
  - spirits count as negative
- **Caffeine.** Tracked in mg. Above about 300 mg in a day, show a note and add a
  small top-up to the water target.
- **Alcohol.** Adds a top-up as well.
- **Wording.** Copy must be honest. Moderate tea and coffee are not dehydrating, so
  the app must not claim they are.
- **Workouts.** Training adds sweat loss: session duration × an intensity-based rate.
  An Advanced option lets the user measure their own sweat rate by weighing
  before and after a session.

### Coach engine: rules, no AI needed

A weekly check-in reviews the past week and suggests changes. Each suggestion has a
plain "why". The user taps to apply it or dismiss it, and nothing changes silently.

| Signal | What the coach does |
| --- | --- |
| Weight trend vs goal pace | Adjust calories in small steps |
| Adherence (days logged, targets hit) | Don't change targets on bad data. Say so. |
| Strength stalls (no progress in 2–3 sessions) | Suggest a deload or an exercise swap |
| Recovery (sleep, resting HR, HRV trend from Health Connect, effort ratings) | Suggest an easier day or a deload |
| Protein or water shortfall | Point it out |
| Volume per muscle vs target range | Point it out |

**Daily "one thing to do today" card on Today.** It is chosen by priority rules, not AI.

**The AI version is optional.** It rewords or expands the coach's findings. It never
decides numbers on its own.

### AI policy and cost

- **Deterministic first.** AI runs only when no rule can do the job, and only on an
  explicit user action:
  - photo estimate
  - unstructured menu text
  - Recipe Hunter search
  - the optional coach narrative
  - recipe URL import falls back to AI only when the page has no structured recipe
    data, and only when the user taps "Try with AI"
- **Never AI** for:
  - targets
  - water
  - program generation
  - allergen decisions
  - barcode or database lookups
  - structured recipe import
  - parsing scripts
- **Audit every OpenRouter call site.** Each one is gated behind a user action or an
  opt-in schedule.
- **AI usage meter (Settings → AI):**
  - calls and dollars per feature for today, the week and the month, from the cost
    OpenRouter reports
  - a monthly budget cap that blocks further calls once it is reached
- **Cost estimate.** Once a model is chosen for each feature, show the expected daily
  and monthly cost. The calculation is:
  `uses per day (user-editable defaults per feature) × average tokens per call
  (measured from benchmark runs) × the model's price`.
  The same calculation goes in docs/AI-COSTS.md.
