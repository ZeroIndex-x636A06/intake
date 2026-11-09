# Errors and warnings

Every problem the parser reports has a **severity**, a **line**, a **column**, a **message** and a link to the
wiki section that explains the rule. Each one below is listed under its name, with an example that triggers it
and the fix.

- An **error** stops the import: if there is any error, nothing is imported.
- A **warning** is shown in the preview and does not block the import.

Messages are written to be read: `Line 14: expected a field such as "sets: 4" or "reps: 6-8", or a new heading,
found "4 sets of 6"`. If the same mistake repeats, you see it on every line it appears on, up to 100 problems.

See also: [README.md](README.md), [program.md](program.md), [progress-rules.md](progress-rules.md),
[meal-plan.md](meal-plan.md).

## General

### not-an-intake-script

Error. The first line is not `# Program: <name>` or `# Meal plan: <name>`.

Example: a file that starts with `My workout`, or is empty.

Fix: start the file with `# Program: My Program` or `# Meal plan: My Plan`. See [file kinds](README.md#file-kinds).

### file-too-large

Error. The file is over 2,000,000 characters.

Fix: split it, or check it is really a script file.

### unclosed-comment

Error. A `<!--` comment is never closed with `-->`.

Example: `<!-- warm up first` and no `-->`.

Fix: add `-->` where the comment ends, or remove the `<!--`.

### too-many-problems

Error. More than 100 problems were found, and the rest are not listed.

Fix: fix the ones shown and import again. A file that is not a script at all (or is in the wrong format) usually
causes this.

### unexpected-heading

Error. A heading that does not belong at that place: a second `# ` line, a `####` heading, an empty day name, or
a `###` where only `### Recipe:` or a venue is allowed.

Example: in a meal plan, `### Subway` inside a week.

Fix: use `##` for days, weeks and sections, and `###` only for exercises, venues and `Recipe:`. See
[headings](README.md#headings).

### unknown-key

Error. A `label:` the parser does not know in this place. The message suggests the closest real label and lists
the valid ones.

Example: `weigth: 185` under an exercise.

Fix: `weight: 185`.

### missing-colon

Error. A line starts with a known label but has no colon after it.

Example: `sets 4`.

Fix: `sets: 4`.

### duplicate-key

Error. A field was given twice. (`note:` may repeat.)

Example: two `sets:` lines in one exercise, or `4x6` and `sets: 4` together.

Fix: keep one.

### empty-value

Error. A label has nothing after the colon.

Example: `sets:`.

Fix: give it a value, or delete the line.

### unexpected-text

Error. A line that is not a field, a list item or a heading in the place it appears.

Example: `4 sets of 6` under an exercise, or `3 lb beef` under `Ingredients:` with no bullet.

Fix: write it as a labeled field (`sets: 4`, `reps: 6`) or as a bullet (`- 3 lb beef`).

## Programs

### missing-program-name

Error. `# Program:` with no name.

Fix: `# Program: Strength Block A`.

### unknown-units

Error. `units:` is not lb or kg.

Example: `units: stone`.

Fix: `units: lb` or `units: kg`.

### bad-rest

Error. A rest value that is not understood, under 5 seconds, over 1 hour, or a bare number.

Example: `rest: 90`.

Fix: `rest: 90s`, `rest: 1:30` or `rest: 1 min 30 s`. See [rest](program.md#rest).

### no-days

Error. The program has no `## Day` heading.

Fix: add `## Upper A`, then exercises under it.

### empty-day

Warning. A day with no exercises.

Fix: add exercises, or delete the day.

### duplicate-day

Warning. Two days have the same name (case and spacing ignored).

Fix: rename one.

### exercise-outside-day

Error. A `###` exercise heading before any `##` day.

Fix: add a `## Day name` heading above it.

### field-outside-exercise

Error. An exercise field such as `sets: 4` after a day heading but before any `###` exercise heading.

Fix: add `### Exercise name` above the field.

### empty-exercise-name

Error. `###` with no name.

Fix: `### Bench Press`.

### missing-sets

Error. An exercise has no `sets`.

Fix: add `sets: 4` (or the short form `4x6-8`).

### missing-reps

Error. An exercise has no `reps`.

Fix: add `reps: 6-8`, or `reps: max`.

### bad-sets

Error. `sets` is not a whole number from 1 to 30.

Example: `sets: many`, `sets: 0`.

Fix: `sets: 4`. See [sets](program.md#sets).

### bad-reps

Error. `reps` is not understood, out of 1 to 100, or a backwards range.

Example: `reps: lots`, `reps: 8-6`.

Fix: `reps: 6-8`, `reps: 8` or `reps: max`. See [reps](program.md#reps).

### bad-rpe

Error. `rpe` is not a number from 1 to 10.

Example: `rpe: 11`.

Fix: `rpe: 8`. See [RPE](program.md#rpe).

### bad-weight

Error. `weight` is not a number, with an optional unit, or `bodyweight`.

Example: `weight: heavy`.

Fix: `weight: 185`, `weight: 80 kg` or `weight: bodyweight`. See [weight](program.md#weight).

### bad-rule

Error. A `progress:` value that is not a rule, or has extra words the parser can't place.

Example: `progress: linier, add 5`.

Fix: use one of `linear, add 5 lb`, `double progression, add 5 lb`, `rpe`, `80% of max`, `none` or
`custom: <expression>`. See [writing a rule](progress-rules.md#writing-a-rule).

### rule-needs-increment

Error. A `linear` or `double` rule with no amount to add.

Example: `progress: linear`.

Fix: `progress: linear, add 5 lb`. See [increments](progress-rules.md#increments-and-units).

### bad-increment

Error. The increment is zero, or something that is not an amount.

Example: `progress: linear, add lots`.

Fix: `progress: linear, add 5 lb`.

### bad-deload

Error. A deload clause that is wrong: a percentage outside 0 to 100, zero misses, no number, or on a rule that is
not `linear`.

Example: `progress: double, add 5, deload 10%`.

Fix: `progress: linear, add 5 lb, deload 10% after 3 misses`. See [linear](progress-rules.md#linear).

### bad-percent

Error. A percent-of-max rule with a percentage that is zero, over 100, or malformed.

Example: `progress: 150% of max`.

Fix: `progress: 80% of max`. See [percent of max](progress-rules.md#percent-of-max).

### bad-expression

Error. A `custom:` rule with no expression, or one the expression evaluator rejects. The message includes the
evaluator's own explanation.

Example: `progress: custom: weight +` or `custom: bogus + 1`.

Fix: `progress: custom: weight + 2.5`. See [custom](progress-rules.md#custom) for the variables and operators.

### rpe-rule-needs-rpe

Error. An exercise uses the `rpe` rule but has no `rpe:` target.

Example: `progress: rpe` with no `rpe:` line.

Fix: add `rpe: 8`. See [RPE based](progress-rules.md#rpe-based).

### rpe-rule-without-rpe

Warning. An exercise inherits the program's `rpe` rule but has no `rpe:` target, so its weight will not adjust.

Fix: give that exercise an `rpe:` line, or give it its own `progress:`.

### amrap-with-double

Warning. Double progression on an exercise with `reps: max`, which has no top of the range to reach.

Fix: give the exercise a rep range, or a different rule. See [double progression](progress-rules.md#double-progression).

## Meal plans

### missing-plan-name

Error. `# Meal plan:` with no name.

Fix: `# Meal plan: Winter plan`. See [header](meal-plan.md#header).

### bad-start-date

Error. A `starts:` line in the header whose value is not a date.

Example: `starts: next Monday`.

Fix: write the first day of week 1 as year-month-day, for example `starts: 2026-11-09`. Leave the line out to start on the day you import. See [header](meal-plan.md#header).

### bad-repeat

Error. A `repeat:` line in the header whose value is not `no`, `yes` or a count.

Example: `repeat: sometimes`.

Fix: use `repeat: no` (stop after the last week), `repeat: yes` (start over forever) or `repeat: 3 times` (go through the whole plan three times). See [header](meal-plan.md#header).

### unknown-section

Error. A `##` heading that is not `Every week`, `Week N: <dish>`, `Eat out` or `About`.

Example: `## Month 1`.

Fix: delete it. See [sections](meal-plan.md#sections).

### no-weeks

Error. The plan has no `## Week N: <dish>` heading.

Fix: add at least `## Week 1: Taco bowls`. See [weeks](meal-plan.md#weeks).

### bad-week-heading

Error. A week heading without a number or without a dish name.

Example: `## Week 1`, or `## Week: tacos`.

Fix: `## Week 1: Tacos`.

### bad-week-number

Error. A week number outside 1 to 104.

Example: `## Week 13: Tacos`.

Fix: use a number from 1 to 104 (two years of weeks).

### duplicate-week

Error. The same week number twice.

Fix: renumber one of them.

### bad-servings

Error. `Servings:` is not a number.

Example: `Servings: lots`.

Fix: `Servings: 5`. See [week fields](meal-plan.md#week-fields).

### bad-time

Error. `Time:` is not a duration.

Example: `Time: soon`.

Fix: `Time: 45 min`, `Time: 1 hour 30 minutes`, or just `Time: 45`.

### ingredient-no-name

Error. An ingredient that has an amount but no name.

Example: `- 3 lb`.

Fix: `- 3 lb ground beef`. See [ingredients](meal-plan.md#ingredients).

### bad-ingredient-weight

Error. An ingredient `weight:` that is not a weight in g, kg, oz or lb, or is not plausible.

Example: `weight: heavy`.

Fix: `weight: 370 g`. See [ingredient fields](meal-plan.md#ingredient-fields).

### bad-nutrition

Error. A `per 100g:` part that is not understood, is repeated, or the line has no values.

Example: `per 100g: lots of it`.

Fix: `per 100g: 176 kcal, 20 g protein, 0 g carbs, 10 g fat`. See [nutrition](meal-plan.md#nutrition).

### nutrition-implausible

Error. `per 100g:` values that cannot be real: over 900 kcal, or protein, carbs and fat adding to more than 100 g.

Example: `per 100g: 2000 kcal`.

Fix: check the label; the values are per 100 g.

### incomplete-nutrition

Warning. An ingredient gives only some of kcal, protein, carbs and fat, or has nutrition but no weight. It will
not count toward macros.

Fix: give all four values and a `weight:`, or remove the nutrition line.

### incomplete-macros

Warning. In one recipe, some ingredients are complete (weight and all four values) and some are not, so the
recipe's macros will be reported as incomplete.

Fix: complete the remaining ingredients, or accept the incomplete report. Nothing is invented for you.

### duplicate-recipe

Error. Two recipes with the same name in one week (including the week's own dish).

Fix: rename the `### Recipe:` block. See [extra recipes](meal-plan.md#extra-recipes).

### shopping-unknown-category

Warning. A shopping category the parser does not know. The items go under Other.

Example: `- Whatever: widgets`.

Fix: use Produce, Meat, Pantry, Frozen or Other. See [shopping](meal-plan.md#shopping).

### shopping-no-category

Warning. A shopping bullet with no `Category:` in front. The items go under Other.

Example: `- 4 avocados, onion`.

Fix: `- Produce: 4 avocados, onion`.

### venue-no-orders

Error. A venue with no `- ` order lines. If it has text, that text would be lost.

Example: `### Zaxby's` followed by a paragraph only.

Fix: add an order such as `- Grilled chicken sandwich, no ranch` and keep the paragraph as the venue's note. See
[eat out](meal-plan.md#eat-out).

### bad-weeks-list

Error. `weeks:` that is not a list of weeks from 1 to 104.

Example: `weeks: 14`.

Fix: `weeks: 1, 3-5`, or `weeks: all`. See [eat out orders](meal-plan.md#eat-out-orders).

### bad-estimate

Error. `estimate:` with parts that are not understood.

Example: `estimate: yes`.

Fix: `estimate: 450 kcal, 40 g protein`.
