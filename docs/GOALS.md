# Profile, baseline week and goal builder

Every number in "Your plan" comes from the formulas on this page. They are pure Kotlin in
`app/src/main/java/com/zeroindex/intake/domain/goals/`, they run on the phone, and there is
no network and no AI in any of it. The same inputs always give the same plan.

The screens say "Daily calorie burn" for TDEE and "Calories at rest" for BMR, each with an
(i) that explains the term.

## Where things live

| Piece | File |
| --- | --- |
| Profile, build, phase and training answers | `ProfileModels.kt` (stored as one JSON value in DataStore) |
| Baseline readiness rule | `BaselineReadiness.kt` |
| Calories at rest, activity factor, measured burn, blending | `EnergyModel.kt` |
| Phase offsets, calorie floor, safety rails, pace in words | `PhasePlanner.kt` |
| Macros, carb cycling, refeeds, diet breaks | `MacroPlanner.kt` |
| Water, the plan as a whole, the "why" strings | `GoalBuilder.kt` |
| Reading and writing the data | `data/profile/ProfileRepository.kt` |
| Daily check and the one notification | `work/BaselineWorker.kt` |

There is **no schema change**. The profile, the baseline marker, the applied-plan history and the
advanced week pattern are JSON values in DataStore (`SettingsRepository`). Applied targets are
ordinary `nutrition_target` rows, which are already a dated history and are never overwritten.
Like the other settings, the profile is not in the backup or the sync stream; weigh-ins and
targets are.

## 1. Calories at rest (BMR)

Mifflin-St Jeor (Mifflin et al., 1990):

    male:    10 x kg + 6.25 x cm - 5 x age + 5
    female:  10 x kg + 6.25 x cm - 5 x age - 161

When a body-fat percentage between 3 and 60 is known, Katch-McArdle (McArdle, Katch and Katch)
is used instead, from lean mass:

    370 + 21.6 x lean kg        (lean kg = kg x (1 - body fat / 100))

Example: 80 kg, 180 cm, 30 y, male gives 800 + 1125 - 150 + 5 = 1,780 kcal.
75 kg at 20% fat gives 370 + 21.6 x 60 = 1,666 kcal.

## 2. Activity factor and the day-one burn

    day-one burn = calories at rest x activity factor
    activity factor = base + 0.03 x training days a week

The base is the "Day-to-day activity" choice outside training: mostly sitting 1.2, on my feet
1.375, physical job 1.55. Once Health Connect has at least three days of steps (or the baseline
has some), the choice is replaced by a straight line from 1.2 at 3,000 steps a day to 1.55 at
14,000 steps. Training is added separately so lifting is never counted twice.

This is an estimate. Published equations are typically within about 10% for most adults and
can be well off for an individual, which is why the baseline exists.

## 3. Measured burn from the baseline

    burn = average logged intake - (weight trend slope in kg/day x 7,700 kcal/kg)

- Only days that count as logged (see the rule below) go into the average.
- The weight trend reuses `WeightTrend` and the `UnitConverter.KCAL_PER_KG` constant that the
  nightly adaptive estimate uses. The 28-day estimator (`TdeeCalculator.estimate`) needs 14
  days that carry both a log and a weigh-in, so a one-week baseline cannot use its gate. The
  baseline therefore fits a least-squares line through the daily weights (alpha 1.0 in
  `WeightTrend.smooth`). An exponential average lags a short series and would understate the
  change, and over a week the line is the smoother.
- The 7,700 kcal/kg figure is the commonly used energy density of body fat (about 3,500 kcal per
  pound). It is an average, not a law: the real cost of a kilogram depends on how much is fat
  and how much is water and lean tissue, and it changes as someone diets (Hall, 2008 and later
  work on dynamic models). Over a week, water swings of half a kilo are worth a few hundred
  kcal a day, so a one-week measurement is blended with the formula and never trusted fully.

### Blending

    trust = min(logged days / 7, weigh-in days / 5, weigh-in span days / 6) x 0.8
    burn used = trust x measured + (1 - trust) x day-one estimate

- A measured burn below 1.0 or above 3.0 times calories at rest is treated as a logging mistake
  and ignored.
- If the nightly adaptive estimate has MEDIUM confidence (14+ days) it is used at 85%, HIGH
  confidence at 100%. LOW confidence is ignored.
- The plan says which one it used: "measured", "part measured" or "estimated", with the numbers.

## 4. The baseline week

The person taps "Start my baseline week" (Profile & goals). For the days that follow they log as
normal.

The rule (`BaselineReadiness`), counting the start day as day 1:

- at least **7 days** have passed;
- at least **5 days** are logged. A day counts as logged only with **at least one food entry and
  at least 800 kcal**, so a coffee-only day does not count;
- at least **4 weigh-in days**. Two weigh-ins on one day count as one, and 5 or more are
  recommended.

Steps, sleep, resting heart rate and workouts are collected and shown but never required.

If the rule is not met, the baseline stays open and the Today card lists what is missing and why it
matters ("2 more weigh-ins"). **After day 10** it is shown as extended, with a plain explanation of
what it is waiting for. A person can always build a plan from the formula instead.

Notification: `BaselineWorker` runs once a day (about 19:00). When the rule is met and nobody has
been told, it posts one "Your plan is ready to build" notification and records that, so it can
never repeat. Android 13+ is asked for the notification permission when the baseline starts. If
it is denied, nothing is lost: the Today card shows "Ready".

## 5. Phase adjustments

The daily calories are the burn plus an offset, as a share of the burn. The pace choices Gentle,
Steady and Fast pick the offset; the screen then states the resulting rate in plain words, for
example "About 0.5% of body weight per week (about 0.4 kg)". Every number can be edited.

| Phase | Gentle | Steady | Fast |
| --- | --- | --- | --- |
| Cut | -15% | -20% | -25% |
| Lean bulk | +5% | +7.5% | +10% |
| Bulk | +10% | +15% | +20% |
| Recomp | -5% | -7.5% | -10% |
| Maintain | 0 | 0 | 0 |

- **Contest prep** (Advanced): the weekly loss rate, 0.5 to 1.0% of body weight (default 0.75),
  converted to kcal: `weight x rate% x 7,700 / 7` taken off the burn.
- **Reverse diet** (Advanced): starts from what the person eats now (the baseline average, else
  85% of the burn) and adds 50 to 100 kcal per week (default 75) until it reaches the burn. The
  plan lists every week.

## 6. Safety rails

Shown as plain warnings, not blocks, except where noted.

- Calories are never below **1.1 x calories at rest**. A lower number is raised to the floor and
  the person is told. Only the Advanced "allow calories below the floor" switch keeps a lower
  number, and it still warns.
- A loss faster than **1% of body weight a week** shows a warning.
- **Under 18**: no deficit, whatever the phase. The target is the burn, with a warning.
- Pregnancy, breastfeeding, eating disorders and medical conditions are out of scope. The profile
  screen says so.

## 7. Macros

1. **Protein** in g/kg of body weight, or of **lean mass** when body fat is known. Ranges follow
   the ISSN position stand on protein (Jager et al., 2017) and Helms et al. (2014) for lean people
   in a deficit:

   | Phase | Range | Default |
   | --- | --- | --- |
   | Cut, contest prep | 2.2 to 2.6 | 2.4 / 2.6 |
   | Recomp | 2.0 to 2.4 | 2.2 |
   | Lean bulk, bulk, maintain, reverse | 1.6 to 2.2 | 1.8 |

   Advanced lets a person type any value from 0.8 to 3.5 g/kg; outside the usual range a note says so.
2. **Fat** is at least 0.6 g/kg of body weight **and** at least 20% of calories. Defaults are 0.8
   g/kg (0.7 on a cut or recomp, 0.6 in contest prep).
3. **Carbs** take the rest. If calories are too low for both protein and the fat floor, protein is
   trimmed and the plan says so.

Protein, fat and carbs are rounded to whole grams and add up to the calorie target within a few kcal
(less than 1%).

### Advanced week shaping

Both tools move carbs between days and never change the weekly total. Protein and fat stay the same
every day.

- **Carb cycling**: training days get N% (15, 25 or 35) more carbs than the average of the days they
  share with; rest days get the remainder (never below 20% of the average).
- **Refeeds**: on 1 or 2 training days (the last ones of the week) carbs rise until the day reaches
  maintenance calories; the other days give back exactly what was added. If that would push the other
  days under the calorie floor, fewer refeeds are scheduled and the plan says so. Refeeds only apply
  in a deficit.
- **Diet breaks**: with "every N weeks" (8 to 12, cut and contest prep only), week N+1 is a week at
  maintenance, then the count starts again. Intake shows the schedule; it does not switch the target
  by itself.

When applied, the week pattern is stored next to the target and used for each weekday **only while
the stored calorie target is still the one it was built from**, so a later edit or recalculation
always wins over an old pattern.

## 8. Water

`HydrationTarget` is reused: 35 ml per kg of body weight (rounded to 50 ml), plus its training-day
bonus (500 ml by default). The plan card shows both. `WaterPlanner.plan` takes a list of
`WaterAdjustment(label, ml)`, so the weather, drink and caffeine work in "Hydration v2" can add lines
without changing the function. Applying the plan sets the water base.

## 9. Training

The profile answers (days, session length, equipment, experience, goal) map straight to
`GeneratorInputs`. With no explicit goal, one is picked from the phase (cut: lose fat; bulks and
recomp: build muscle; maintain and reverse: general fitness). The builder runs `generateProgram`,
shows a summary and links to the existing generator screen, which opens with the same answers.
Nothing is saved until Apply, and "Create this program" can be switched off.

## 10. What Apply writes

- a new dated `nutrition_target` row (history kept, never overwritten);
- goal rate, protein and fat g/kg settings, so the nightly updater stays in the same range;
- the water base;
- the week pattern, if any;
- the program, if chosen;
- one entry in the applied-plan history (last 12).

If the plan uses carb cycling, refeeds, contest prep or a reverse diet, the nightly rate-based
target updater is switched off, because those are not a single steady rate. The screen says so
before Apply.

## Build picker

Slim, Average, Muscular and Heavier set wording and the phase suggested first. They do **not**
change any formula, calorie, macro or training volume (there is a test for that). The classic
ectomorph, mesomorph and endomorph names are shown only as small secondary labels, because
somatotypes are not a reliable description of physiology.

## Sources

- Mifflin MD, St Jeor ST, et al. A new predictive equation for resting energy expenditure in healthy
  individuals. Am J Clin Nutr 1990;51:241-247.
- Katch FI, McArdle WD. Nutrition, Weight Control, and Exercise (Katch-McArdle resting metabolic
  rate from lean body mass).
- Jager R, et al. International Society of Sports Nutrition position stand: protein and exercise.
  J Int Soc Sports Nutr 2017;14:20.
- Helms ER, Aragon AA, Fitschen PJ. Evidence-based recommendations for natural bodybuilding contest
  preparation: nutrition and supplementation. J Int Soc Sports Nutr 2014;11:20.
- Hall KD. What is the required energy deficit per unit weight loss? Int J Obes 2008;32:573-576
  (why 7,700 kcal/kg is an approximation).
- Byrne NM, et al. Intermittent energy restriction improves weight loss efficiency in obese men:
  the MATADOR study. Int J Obes 2018;42:129-138 (diet breaks).
