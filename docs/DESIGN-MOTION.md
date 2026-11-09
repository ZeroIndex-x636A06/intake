# Motion and clarity

The brief (SPEC-v3 point 5): "things need to be a little bit simpler to understand and I want fancier
animations", and "some of the menus are just WAY too much". The aim is the smartest yet simplest
health app: easy for anyone, deep when you dig in.

Nothing here removes a feature or changes stored data. The pure rules are in `domain/motion/MotionRules.kt`
(unit-tested); the Compose pieces are in `ui/motion/`. **None of the motion has been seen running**: there is
no emulator in the cloud containers.

## Tokens (`ui/motion/Motion.kt`)

| Token | Value | Used for |
| --- | --- | --- |
| `DurationShort` | 100 ms | press feedback |
| `DurationMedium` | 250 ms | expanding sections, list entrances, check morph |
| `DurationLong` | 350 ms | the longest ordinary transition |
| Sweeps, count-ups, chart draw-in | 600 to 800 ms | first show only; the one place we go past 350 ms |
| `Standard`, `EmphasizedDecelerate`, `EmphasizedAccelerate` | easings | everything |
| `PressScale` | 0.97 | `Modifier.pressScale()` |
| `PillDamping`, `PillStiffness` | 0.72, 420 | the floating pill |

Animated values are read inside `graphicsLayer { }`, `drawBehind { }` or `Canvas { }` lambdas, so a frame
redraws a layer instead of recomposing a screen. The one exception is `CountUpText`, which recomposes a
single `Text` for about 600 ms.

## Reduce motion

`LocalReduceMotion` is provided once in `IntakeApp`:

    reduce = system ANIMATOR_DURATION_SCALE == 0  OR  Settings, Reduce motion

(`MotionRules.reduceMotion`; `isMotionReduced(scale, userSetting)` calls it.) The setting is the DataStore key
`reduce_motion` (`AppSettings.reduceMotion`, Settings, "Look and units"). It is in the portable settings
allow-list, so it travels in a backup (`PortableSettings`, "look and feel"). `last_celebration_day` (the day the
protein burst last played) is device-local and listed as such in `PortableSettingsTest`.

When reduce motion is on: no sweeps, count-ups, bursts, pulses, stagger, shimmer sliding, wave phase or
draw-in; every component shows its final state at once. Haptics are separate and still play. Endless animations
(`rememberLoopingPhase`: the water wave and the shimmer) also stop while the element is off-screen
(`rememberOnScreenState`, from `onGloballyPositioned`).

## Where each animation lives

| Animation | Where |
| --- | --- |
| Ring sweep on first show, glide on change (protein, then carbs, then fat) | `MacroRing` via `rememberSweepFraction` |
| Calories-left count-up; screen readers get only the final value | `CountUpText` (`ui/motion/MotionKit.kt`), used by `MacroRing` |
| Macro and protein bars animate width | `AnimatedBar` (`MacroRing.kt`) |
| Ring pulse + confirm haptic after a log that raises today's calories | `pulseOn` in `MacroRing`, triggered by `rememberRingEvents` in `TodayScreen`, rule `MotionRules.shouldPulseAfterLog` |
| First protein target each day: tick + 14-particle burst, about 600 ms | `BurstOverlay` (`MotionCanvas.kt`), rule `MotionRules.shouldCelebrateProtein`, day stored in `last_celebration_day` |
| Water: sine-wave fill with animated phase, paused off-screen and with reduce motion | `WaterWave` (`MotionCanvas.kt`), used by `HydrationCard` |
| List entrance: fade + slide up, first 8 items, 30 ms apart, never replayed on scroll-back | `staggerIn` + `StaggerGate`; Today cards, meal slots, hub rows, recipe list |
| Shopping check-off: check morph, strike-through, fade, haptic | `ShoppingRow` in `PlanScreen`, `AnimatedCheck` |
| Logging a set: check morph, haptic, rest starts | `SetRow` in `ActiveWorkoutScreen`, `AnimatedCheck` |
| New best: row colour wash and a "New best" tag | `flashOn`, rule `NewBest.isNewBest` (vs last session and earlier sets today, not all-time). The best estimated max in Training stats also flashes when first shown |
| Rest timer: circular countdown ring | `RestRing`, in the Active workout rest strip; `TimerState.totalSeconds`. **Not done on the watch** |
| Charts: line draws in (path length), bars grow, dots appear as the line passes | `rememberDrawIn` + `drawPathPartial` in `Charts.kt` (weight trend, calories vs target, performance overlay) |
| Loading: shimmer skeletons instead of spinners | `Modifier.shimmer()`, `SkeletonLine`, `SkeletonList`; food search (Open Food Facts, USDA, barcode lookup), Progress, recipe import, and every `LoadingState` |
| Press feedback, 0.97 | `Modifier.pressScale()`: Log food, Start, Resume, Build my plan, Cook, Log weight, hub rows, drink chips, slot Log food row, recipe cards |
| Expanding sections: animated size + rotating chevron | `MoreDetail`, `ExpandChevron`; the plan `Accordion` |
| Baseline progress bar fills in | `BaselineCard` via `rememberSweepFraction` |

**Skipped: shared-element transition (recipe card to recipe detail).** `SharedTransitionLayout` is in this
Compose version, but the recipe list and detail are separate `composable<Route>` destinations in different
graphs; passing a `SharedTransitionScope` and `AnimatedVisibilityScope` through `IntakeNavHost` and the hub
screens touches every route. Not worth the risk without a device to check it.

## Clarity and declutter: before to after

**Today**
- Ring centre: kcal eaten, "of target", "remaining" as small text, then 3 stat tiles -> **calories left** as the big number
  (counts up), "x of y kcal eaten" under it.
- Three macro tiles (protein, carbs, fat) -> **protein left** is the one bar under the ring; carbs, fat and fibre are in a
  collapsed **More detail**.
- A green "x g protein left - y kcal to spend" block repeating both numbers -> one quiet sentence of encouragement. The
  no-target case now says where to set one.
- "Add food" button at the very bottom, below four meal cards -> **Log food** is the one big button directly under the ring.
  Slot rows say "Log food" too. (Tour target `add-food` moved with it.)
- Macros are explained by an (i) next to Protein the first time you see it.

**Food hub**
- 10 rows, "Log food" among them -> a big **Log food** button with "Scan a barcode instead" beneath it, then 5 rows (Meal plans,
  Recipes, Meal templates, Eating out, Allergens); My foods, Food database & credits and Food script help are folded under
  **More food tools**.
- Recipes: the "No recipes yet" note had no button -> empty state with **Add a recipe**.

**Meal plans**
- Header with "Import plan", Plans, New plan, Script reference, Export plan as script -> **Plans** and an overflow menu (New plan,
  Import, Export, Script reference).
- Sticky bottom: one "Log this meal" plus a full-colour "Can't eat what I cooked?" banner -> **Cook this meal** is the primary,
  Log this meal sits beside it, and the safer-option banner is quiet (variant colour).
- Shopping list: checkbox -> animated check, fade, strike-through and haptic; the "x" text button is an icon button with a label.
- Accordion arrows (text glyphs) -> a rotating chevron with animated size.

**Train**
- "Start {day name}" -> **Start today's workout**; "Start an empty workout" is a text button, not a second big button.
- Templates, Performance and Program script help (6 rows) -> folded under **More training tools**; Programs, Make one for me
  and Exercises stay.
- Active workout: a 34 dp tick -> 48 dp target with a morphing check and haptic; the rest strip has a countdown ring and "Skip rest".
  As-many-as-you-can sets show "3 x max" with an "As many as you can" label and (i), and the reps box is not pre-filled with 1.

**Progress**
- "Add a weigh-in" button at the bottom of the weight card -> **Log weight** is the one big button under the heading. An empty
  weight card has an empty state with the same button.
- Blank cards while loading -> shimmer skeletons.

**More, Settings, Coach**
- Settings was Preferences, API keys, Allergens (a full editor), Targets (6 inputs), Food database, Training (4), Water, Health
  Connect -> **Look and units** (adds Reduce motion), **Targets** (goal weight, rate, Recalculate; protein, fat and manual calories
  under *Fine-tune*), **Training** (rest, vibrate), **Water**, **Allergens** (one link row to My allergens), **Food database**,
  **Help** (Replay the tour), **Advanced** (collapsed: bar weight and plates, wearable calories, API keys, AI tools).
- Coach check-in: settings card and past check-ins are folded; "Check in now" is a text button once a check-in exists.

**Everywhere**
- Verbs: "Add food / Add a weigh-in / Save jump test / Save (weight)" -> **Log** food, weight, jump test.
- Info (i) buttons are 48 dp. Set and shopping ticks are 48 dp. Animated numbers expose only the final value.
- Empty states: Performance, Training stats and My foods now say what to do next. The first two have no button because the
  action is on the same screen or another tab (a button would need new navigation plumbing).
- Jargon: TDEE, BMR, RPE and 1RM were already replaced by "daily calorie burn", "calories at rest", "effort" and "estimated
  max" with (i) tips (`GoalGlossary`, `Glossary`); this pass added `Glossary.MACROS` and the AMRAP label.
- Allergen warnings (the red sheet, blocking, "may contain", check-the-label) were not touched.

Not reworked: Profile and Your plan (they already carry a why, an (i) and an Edit on every number), the Add food sheet layout.

## Follow-ups from the task board

- Settings no longer has its own Allergens editor; a **My allergens** row opens the real screen.
- "As many as you can": `RepTarget` (`domain/training/RepTarget.kt`) in the builder summary and sheet, and the active workout.
- Quiet **Mentioned as avoided** pill and note on recipe detail, the plan screen and eat-out cards (`MentionedAvoided`).
- **Replay the tour** is in Settings as well as Help.
- `TrainingRepository` row building pulled out into `data/training/ProgramEdits.kt` (`moveDay`, `duplicateDay`,
  `copySettings`, renumbering, copy names) with `ProgramEditsTest`; `createProgramFromBlueprint` already built its rows in
  `ProgramBlueprintBuilder` (`ProgramBlueprintTest`). There is no Robolectric, so the transaction wiring itself is still untested.
