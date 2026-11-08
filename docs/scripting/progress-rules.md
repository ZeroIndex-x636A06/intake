# Progress rules

A progress rule tells the app what to put on the bar next time, based on how the last session went. The app
always shows a one-line reason next to the number ("185 lb, up 5 from last time, all 4 sets hit 6 reps"), so you
can see why.

## Writing a rule

Put a `progress:` line in an exercise, or in the program header as a default for every exercise that does not
have its own:

```
# Program: Strength Block A
units: lb
progress: double progression, add 5 lb      <!-- the default -->

## Upper A
### Bench Press
sets: 4
reps: 6-8                                   <!-- uses the default -->

### Overhead Press
sets: 3
reps: 5
progress: linear, add 5 lb                  <!-- overrides the default -->

### Pull-up
sets: 3
reps: max
progress: none                              <!-- no rule at all -->
```

The words are forgiving. Case, spacing and the commas do not matter, and these all mean the same:

`linear, add 5 lb` · `Linear add 5lb` · `linear +5` · `linear, +5 lb`

| Rule | How to write it |
|---|---|
| [Linear](#linear) | `linear, add 5 lb` and optionally `, deload 10% after 3 misses` |
| [Double progression](#double-progression) | `double progression, add 5 lb` (or `double, add 5`) |
| [RPE based](#rpe-based) | `rpe` |
| [Percent of max](#percent-of-max) | `80% of max` |
| [Custom](#custom) | `custom: <expression>` |
| None | `none` |

If the rule is not recognised you get `bad-rule`, with a suggestion if it looks like a typo of a real one.

## Increments and units

`linear` and `double` need an increment: how much weight to add. Write it as `add 5 lb`, `+5`, `add 5`,
`add 2.5 kg` or `up 5 lb`. With no unit, the increment uses the program's `units:`. Leaving the increment out is
`rule-needs-increment`; an increment of zero or less is `bad-increment`.

Everything is stored in kilograms, so `add 5 lb` becomes about 2.27 kg internally. The app rounds the next weight
to the smallest jump your plates allow.

## Linear

*Add a fixed amount every session that goes to plan.*

- If **every set reached the bottom of the rep range**, the next session adds the increment.
- If not, the weight is held.
- After the configured number of misses in a row, the weight is **deloaded** by a percentage.

`deload 10% after 3 misses` sets the percentage and the number of misses in a row. Leave the deload off and the
defaults apply: 10% after 2 misses. `deload 10%` on its own sets only the percentage. Only a linear rule can
deload (`bad-deload`).

**Worked example.** Overhead press, 3 sets of 5, starting at 95 lb, `linear, add 5 lb, deload 10% after 3
misses`:

| Session | Weight | Reps | What happens |
|---|---|---|---|
| 1 | 95 lb | 5, 5, 5 | All sets hit 5, so next time is 100 lb |
| 2 | 100 lb | 5, 5, 4 | One set short: hold at 100 lb |
| 3 | 100 lb | 5, 4, 4 | Second miss in a row: hold at 100 lb |
| 4 | 100 lb | 5, 5, 3 | Third miss in a row: deload 10% to about 90 lb |

## Double progression

*Fixed weight until you reach the top of the rep range on every set, then add weight and start over at the
bottom.*

It needs a rep range (`reps: 10-12`). With `max` reps there is no top to reach (`amrap-with-double` warning).

**Worked example.** Dumbbell curl, 3 sets of 10 to 12, starting at 30 lb, `double progression, add 5 lb`:

| Session | Weight | Reps | What happens |
|---|---|---|---|
| 1 | 30 lb | 12, 11, 10 | Not all at 12. Same weight, aim for one more rep (lowest set was 10, so 11) |
| 2 | 30 lb | 12, 12, 11 | Same weight, aim for 12 on the last set |
| 3 | 30 lb | 12, 12, 12 | Every set hit the top: next is 35 lb, back to 10 reps |

## RPE based

*Adjust the weight so the effort you log converges on a target RPE.*

Write `progress: rpe`, and give the exercise the target in an `rpe:` field. RPE is how hard a set felt, 10 being
nothing left. The app averages the RPE you logged, looks up how heavy that effort is for the reps you did, and
scales the weight by the ratio between that and the target.

```
### Romanian Deadlift
sets: 3
reps: 8-10
rpe: 7.5
weight: 185
progress: rpe
```

If last session averaged RPE 9 against a target of 7.5, the next weight goes down. If it averaged 6, it goes up.
If you logged no RPE, the weight is held, since there is nothing to adjust against.

A rule of `rpe` on an exercise with no `rpe:` field is an error (`rpe-rule-needs-rpe`). If the rule only comes
from the program's default, it is a warning instead (`rpe-rule-without-rpe`), because the weight just will not
adjust for that exercise.

## Percent of max

*Prescribe a fixed percentage of your estimated one-rep max.*

`80% of max` (also `80%`, `80% of 1RM`). The percentage must be above 0 and at most 100 (`bad-percent`). The
estimated max comes from your logged sets with the Epley formula, and updates as you set new bests, so the
prescription climbs with you.

**Worked example.** `75% of max` on leg press. Your best recent set was 100 kg for 5 reps, an estimated max of
about 116.7 kg, so the next prescription is about 87.5 kg. After a set of 105 kg for 5, the estimate rises and
the prescription follows.

## Custom

*Your own formula.* Write `custom:` followed by an expression. It is checked when the file is imported, so a typo
is an error with a line and column (`bad-expression`), never a surprise mid-workout. The expression language is
the one the app already uses for custom rules.

```
progress: custom: if (completedReps >= reps * sets) weight + 2.5 else weight
```

**What the result means.** The expression produces **the next weight, in kilograms**. The app rounds it to your
plate increment. A negative result is rejected when it is evaluated.

**Units.** Custom expressions always work in kilograms, whatever `units:` says. `weight + 2.5` adds 2.5 kg
(about 5.5 lb).

### Variables

| Name | Value |
|---|---|
| `weight` | The heaviest weight used in the last session, in kg |
| `reps` | The bottom of the exercise's rep range |
| `sets` | The number of target sets |
| `completedReps` | The total reps done across the last session's sets |
| `rpe` | The highest RPE logged last session, or 0 if none was logged |
| `failureCount` | How many sessions in a row fell short |
| `week` | The current program week |

Any other name is an error that lists the ones above.

### Operators and functions

| Syntax | Meaning |
|---|---|
| `+` `-` `*` `/` | Arithmetic. Dividing by zero is an error. |
| `-x` | Negate |
| `( )` | Grouping |
| `<` `<=` `>` `>=` `==` `!=` | Comparisons. They give 1 (true) or 0 (false), so they can be used in arithmetic. |
| `if (condition) a else b` | `a` when the condition is not 0, otherwise `b`. Every `if` needs an `else`. |
| `min(a, b)` `max(a, b)` | The smaller or larger of two values |
| `round(x)` | Round to the nearest whole number |

A single `=` is an error (use `==`). There is no `and` or `or`; multiply two comparisons instead
(`(rpe < 8) * (failureCount == 0)`). An expression can be up to 500 characters and nest 32 levels.

### More examples

```
progress: custom: weight + 2.5 * (rpe < 8)
progress: custom: if (failureCount >= 2) weight * 0.9 else weight + 2.5
progress: custom: max(weight - 5, 20)
progress: custom: round(weight * 1.025)
progress: custom: weight + week
```

## None

`progress: none` switches progression off for an exercise, even when the program has a default.
