# Workout programs

A program file describes a training block: a header with defaults, then days, then exercises. Read
[README.md](README.md) first for the conventions that apply everywhere (labels, case, comments).

**One thing per line, labeled. Order and spacing do not matter.** Exercise names come only from the `###`
heading.

```
# Program: Strength Block A
units: lb
rest: 2:00
progress: double progression, add 5 lb

## Upper A

### Bench Press
sets: 4
reps: 6-8
rpe: 8
weight: 185
rest: 2:30

### Pull-up
sets: 3
reps: max
```

A complete example is in [examples/strength-block.md](examples/strength-block.md).

## Header keys

Right under `# Program: <name>`, before the first `##`, you can set defaults. Each is optional, and they can
come in any order.

| Key | Meaning | Default |
|---|---|---|
| `units` | `lb` or `kg` (`lbs`, `pounds`, `kgs` also work). The unit for every weight and increment that has no unit of its own. | `kg` |
| `rest` | Default rest between sets for exercises that give none. See [Rest](#rest). | none (the exercise library's default applies) |
| `progress` | Default progression rule for exercises that give none. See [progress-rules.md](progress-rules.md). | none |
| `notes` | Free text about the program. Can repeat. | none |

Stored weights are always kilograms. A program written in `lb` is converted when imported, so `185` with
`units: lb` is stored as about 83.9 kg, and shown back to you in whatever unit your settings use.

The name must not be empty: `# Program:` alone is `missing-program-name`.

## Days

Each `## <name>` heading is a program day, in the order written. Under a day heading you can put a `note:`
line and then exercises. A day with no exercises is a warning (`empty-day`), and two days with the same name are
a warning (`duplicate-day`).

```
## Upper A
note: Keep the first two lifts heavy.
```

A program needs at least one day (`no-days`).

## Exercises

Each `### <name>` heading is an exercise in the day above it, in order. The name is the heading text and
nothing else. An exercise before any day is `exercise-outside-day`, and a field line (such as `sets:`) before
any `###` heading is `field-outside-exercise`.

## Exercise fields

Under the exercise heading, one field per line, in any order:

| Field | Required | Example |
|---|---|---|
| `sets` | yes | `sets: 4` |
| `reps` | yes | `reps: 6-8` |
| `rpe` | no | `rpe: 8` |
| `weight` | no | `weight: 185 lb` |
| `rest` | no | `rest: 2:30` |
| `progress` | no | `progress: linear, add 5 lb` |
| `note` | no, repeatable | `note: pause on the chest` |

Aliases: `set`, `rep`, `load` (for `weight`), `progression`, `notes`. A bullet in front (`- sets: 4`) is fine.

### Sets

A whole number from 1 to 30. `4` and `4 sets` both work.

### Reps

| You write | Meaning |
|---|---|
| `8` | exactly 8 |
| `6-8`, `6 - 8`, `6 to 8`, `6–8` | a range, 6 to 8 |
| `max`, `amrap` | as many as you can; there is no target |

A trailing `reps` is ignored (`6-8 reps`). The numbers run from 1 to 100, and a range cannot run backwards
(`8-6` is `bad-reps`). A range with equal ends (`8-8`) is the same as `8`.

An `amrap` exercise is stored with a floor of 1 and a ceiling of 99. Double progression has no top of the range
to reach with `max` reps, which is a warning (`amrap-with-double`).

### Weight

The starting weight.

| You write | Meaning |
|---|---|
| `185` | 185 in the program's units |
| `185 lb`, `185lb`, `185lbs` | 185 pounds |
| `80 kg` | 80 kilograms, even in a pounds program |
| `62.5` | decimals are fine |
| `bodyweight`, `bw` | a bodyweight exercise with no added load |
| `bodyweight + 25 lb` | a bodyweight exercise with 25 lb added |


### Rest

Seconds of rest between sets. All of these mean two and a half minutes:

`2:30` · `150s` · `150 sec` · `2 min 30 s` · `2.5 min`

and `90 seconds`, `1 minute 30 seconds`, `1m` work the same way. The value must be between 5 seconds and 1
hour. A bare number (`rest: 90`) is an error, because it could mean seconds or minutes: write `90s` or `1:30`.

### RPE

The target effort, a number from 1 to 10 (`8`, `7.5`). `@8` and `rpe 8` are accepted as the value. The `rpe`
progression rule needs this field ([progress-rules.md](progress-rules.md#rpe-based)).

### Short form

As an optional shortcut, one line inside an exercise can give sets and reps together:

```
### Box Jump
4x3
rest: 90 seconds
```

`4x6-8`, `4 x 6-8`, `4×6-8`, `3xAMRAP` and `3 x max` all work. Use it instead of `sets:` and `reps:`, not as well
as them (`duplicate-key`). The labeled lines are the documented way, and the one the app writes when it exports.

### Notes

`note:` can repeat on an exercise, on a day (before its first exercise) and in the header as `notes:`.

## Examples

A bodyweight exercise with no progression:

```
### Pull-up
sets: 3
reps: max
weight: bodyweight
progress: none
```

A weighted dip that adds 2.5 kg whenever every set hit its reps (see
[custom rules](progress-rules.md#custom)):

```
### Weighted Dip
sets: 3
reps: 8-10
weight: bodyweight + 25 lb
progress: custom: if (completedReps >= reps * sets) weight + 2.5 else weight
```

Everything in different order, with loose spacing and case (valid, and the same as writing it tidily):

```
### Bench Press
  WEIGHT :185lb
  Rest:   2:30
RPE: 8
REPS : 6 - 8
Sets:4
```

## Common mistakes

| You wrote | What happens | Write |
|---|---|---|
| `Bench Press 4x6-8 @8 185` | `unexpected-text`: names and numbers do not share a line | a `### Bench Press` heading and one field per line |
| `4 sets of 6` | `unexpected-text` | `sets: 4` and `reps: 6` |
| `sets 4` | `missing-colon` | `sets: 4` |
| `weigth: 185` | `unknown-key`, with "Did you mean weight" | `weight: 185` |
| `rest: 90` | `bad-rest` | `rest: 90s` |
| `reps: 8-6` | `bad-reps` | `reps: 6-8` |
| `progress: linear` | `rule-needs-increment` | `progress: linear, add 5 lb` |
| a day with exercises written as `- Bench 4x6` bullets | `unexpected-text` | `###` headings |

Every message includes the line and column. The full list is in [errors.md](errors.md).
