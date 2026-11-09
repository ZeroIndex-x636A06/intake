# Coach engine

The coach is a set of plain rules. It looks at your last one to two weeks, suggests a few small
changes, and tells you why with your own numbers. **Nothing changes until you tap Apply.** No AI is
used to decide anything. AI can optionally reword the text (see the end).

Code: `domain/coach/` (pure Kotlin, unit-tested), `data/coach/CoachRepository.kt` (reads the tables,
runs Apply), `work/CoachWorker.kt` (the weekly job), `ui/screens/coach/` (the check-in screen).
Every threshold below lives in one place, `CoachConfig`.

## When it runs

- A WorkManager job (`CoachWorker`) runs daily at about 08:20 and does nothing unless the check-in is due.
- **Due** means: the chosen weekday has come round and no check-in has been made since. A phone that was
  off on Monday gets it on Tuesday. Opening Today or the check-in screen also makes a due check-in.
- The weekday is yours to choose (Weekly check-in, Check-in settings). The default is the weekday your
  baseline week began, else Monday.
- The first check-in waits until there is some food or a weigh-in from the last 14 days.
- One notification, "Your weekly check-in is ready" (can be turned off). It opens the check-in screen.
- Reach the screen from the Today card, the notification, More, or Progress.

## What a suggestion contains

A kind, a plain title, a one-line why with the numbers, an optional longer "Why this?", an exact action
and a priority. Apply runs the action; Dismiss records it. Up to **4** suggestions are shown, lowest
priority number first. With none, the screen says "All on track".

History is kept in the `coach_suggestion` table (applied or dismissed, with the time). A suggestion you
applied or dismissed is not made again for **7 days**. After you apply a calorie change the calorie rule
stays quiet for **14 days**, so the trend can show the effect.

## The rules

All numbers are defaults in `CoachConfig`. "Window" is the 7 days ending today.

### 0. Data gate (priority 1)
If food is logged on fewer than **5 of 7 days** (a day counts at 800 kcal or more, the same rule as the
baseline week) or there are fewer than **3 weigh-in days**, calories are **not** changed. The card reads
"Not enough data this week to adjust" and says what to log. This beats rule 1.

### 1. Weight trend against goal pace (priority 10; 5 when losing faster than 1% a week)
Uses the smoothed trend (`WeightTrend`, EWMA alpha 0.1), never a single weigh-in. The rate is the least-squares
slope of the trend as a percent of body weight per week, measured over the last 14 days (needs at least 5
weigh-in days spanning 10 days) **and** the last 7 days (at least 3 points). Both must agree.

Target pace (percent of body weight per week; the same table in `CoachEngine.targetPct`):

| Phase | Gentle / Steady / Fast |
| --- | --- |
| Cut | -0.45 / -0.6 / -0.75 |
| Contest prep | minus your contest rate (0.5 to 1.0) |
| Lean bulk | +0.15 / +0.2 / +0.3 |
| Bulk | +0.25 / +0.35 / +0.5 |
| Recomp | -0.2 / -0.3 / -0.4 |
| Maintain, reverse diet | 0 |

- **Cut:** slower than **50%** of the target pace for both windows: **-100 kcal**, or **-150** when the
  trend is under 25% of the pace (barely moving or gaining). Faster than **150%** of the pace, or above
  **1%** a week: **+100 kcal** (**+150** above 1% or twice the pace).
- **Bulk and lean bulk:** the mirror image. Gaining under 50% of pace: +100 (+150 under 25%). Over 150%:
  -100 (-150 over 200%).
- **Maintain and recomp:** stay within **±0.25%** a week of the target. Outside it, 100 kcal toward it
  (150 beyond twice the band).
- **Reverse diet:** weight steady or falling: advance one step (your weekly step, 50 to 100 kcal, capped at
  maintenance). Climbing over 0.5% a week: "Hold your calories".
- **Contest prep:** tighter bands (**80%** and **120%** of the pace) and smaller steps (**50**, or **100**).
- **Safety rails come from the goal builder** (`GoalBuilder`/`PhasePlanner`): never below **1.1 x calories
  at rest** unless you turned the floor off in Advanced (then there is no floor); never a cut under 18.
  At the floor, the coach says so and changes nothing. Your Advanced settings are respected.
- **Apply** keeps protein, takes the calories from carbs first (down to 100 g), then fat (down to the larger
  of 0.6 g/kg and 20% of calories), and writes a new dated target. It also switches off the nightly
  automatic target update, so the two do not undo each other. History is kept; nothing is overwritten.

### 2. Diet break (priority 15)
For Advanced users with "diet break every N weeks" on a cut or contest prep. The week count starts at the
oldest applied plan in the unbroken run of cut plans. Reminds you in the week before the break and in the
break week (about maintenance kcal). Remind only: it never changes calories.

### 3. Recovery (priority 20)
Signals, each only when there is enough data:

| Signal | Trips when |
| --- | --- |
| Sleep (Health Connect) | under **6 h** on **3 or more** of the week's nights (needs 4 nights of data) |
| Resting heart rate | the week's average is **more than 5 bpm** above the 28 days before it (needs 3 recent and 7 baseline days) |
| HRV | the week's average is **more than 10%** below the 28 days before it. The app does not store HRV yet, so this stays quiet until it does |
| Effort | **more than half** of at least 10 logged sets were **RPE 9 or harder** |

Two or more signals: "Make your next session a little easier" (**1 set fewer** per exercise for the next
**3 days**). Three signals, or an easier day you already applied this week: "Take an easier week" (a
deload, below), unless you had one in the last 28 days. The copy is cautious: a pattern worth noticing, not
a diagnosis, and Intake is not medical advice.

### 4. Strength stall (priority 30)
For lifts in the active program with at least 3 sessions in the last 42 days. The estimated max (Epley,
`OneRepMax`) of the two later sessions did not beat the oldest by **0.5%**, and total volume did not rise
**5%**: a stall. If no deload happened in the last **28 days**: "Take a deload week" (**-10% weight for 7
days**). Otherwise: swap the stalled lift for another in your library with the same primary muscle and the
same equipment (lifts you have trained before win ties). No match, no suggestion.
- **Deload Apply** sets a coach deload for 7 days. Weights the progression rules prescribe come out 10%
  lighter, rounded to your plates, and are labelled "Coach deload week". The program itself is not edited.
- **Swap Apply** changes the exercise on the program's rows through `TrainingRepository.updateProgramExercise`.

### 5. Protein (priority 40)
Average protein on logged days (at least 3) under **85%** of target. The details name 2 or 3 high-protein
foods: your own most-logged foods first, then a short list of whole foods from the offline database (chicken
breast, eggs, Greek yogurt, tuna, lentils, cottage cheese, tofu, turkey). A food qualifies at 8 g protein or
more per 100 g and 30% or more of its calories from protein. **Every suggestion goes through the local
allergen engine** (`AllergenRepository.scan`); any match of any kind, including "check the label", removes it.
Read-only ("Got it").

### 6. Volume per muscle (priority 50)
Weekly hard sets (working sets, warm-ups excluded) per muscle against `VolumeGuide.allowed` for your
experience, for muscles in the active program only.
- **Well below** (under **75%** of the bottom of the range) in **both** of the last two weeks, with at least
  2 sessions each week (so skipped workouts are not blamed on the program): **+2 sets** for the worst muscle.
- **Above** the range this week **and** at least one recovery signal: **-2 sets** for the worst muscle.
- Apply spreads the sets over that muscle's exercises in the active program (2 to 8 sets each).

### 7. Water (priority 60)
Average intake under **80%** of the average target over the week, and water logged on at least 3 days (so it
does not nag someone who does not use the tracker). Read-only. Totals come from `HydrationRepository`
(`consumedMl` and the day's target), so drink factors and weather are already counted.

## Today: "One thing to do today"
Exactly one item, in this order (`DailyFocusPicker`):
1. a check-in with suggestions waiting;
2. a missing weigh-in on a weigh-in day (you weigh in most days, or on this weekday in 2 of the last 4
   weeks, or the baseline week needs weigh-ins; from 07:00);
3. protein under **60%** of target from 18:00;
4. today's workout, when the active program is scheduled for today and none is finished;
5. water behind for the time of day (expected share rises from 0 at 07:00 to 1 at 22:00; from 11:00 when more than
   25 points behind);
6. baseline items still missing;
7. otherwise a quiet "on track" line with a real number: a logging streak (3 or more days), days logged this
   week, or workouts done this week; a new user is asked for a first meal.

One button goes to the right screen. It recomputes whenever food, weigh-ins, water, workouts or the check-in change.

## Optional AI wording
Off by default. Needs an OpenRouter key **and** "Reword my check-in with AI". It sends only each
suggestion's title and one-line why (they contain your numbers) through `AiGateway` with your privacy
setting. Each reworded item is accepted only if it contains **exactly the same numbers**, is short, and the
allergen engine does not block it; otherwise the original stays. Apply always runs the rule's own action.
If the call fails, the original text is shown. It uses the daily-insight model choice.

## Not built / limits
- HRV is read as an input but the app does not capture it from Health Connect yet.
- Only the weekly job and the screen make check-ins; there is no mid-week trigger.
- A carb-cycling or refeed week pattern is ignored after a coach calorie change (the pattern only applies
  while the target equals the one it was built from).
- Not seen on a device: the notification, the screen layout and the Health Connect signals.
