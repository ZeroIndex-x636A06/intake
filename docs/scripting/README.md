# Intake script

Intake script is a plain-text format for importing **workout programs** and **meal plans** into Intake.
It is Markdown-flavoured, so a script file reads as an ordinary note in any notes app, and you can write
it by hand in any text editor.

**The principle: one thing per line, labeled. Order and spacing do not matter.**
Every value sits on its own `key: value` line. You never have to remember a position, a separator or how many
spaces go where. Keys are not case sensitive, and the values themselves are forgiving (`6-8`, `6 - 8` and
`6 to 8` all mean the same thing).

**The format is still exact.** A deterministic parser reads the file, and no AI is involved anywhere in
importing. If a line is not understood, you get an **error with a line number and column**, never a silent skip.
Import is all-or-nothing: if there is any error, nothing is imported. Warnings are shown in a preview and do not
block the import.

AI is optional. If you like, a chatbot can write the file for you ([ai-prompt.md](ai-prompt.md)), but the app
never uses AI to import, and everything here works with nothing but a keyboard.

## Pages

| Page | What it covers |
|---|---|
| [program.md](program.md) | Workout programs: header, days, exercises, every field |
| [progress-rules.md](progress-rules.md) | The progression rules and the custom expression language |
| [meal-plan.md](meal-plan.md) | Meal plans: weeks, ingredients, steps, shopping, eating out |
| [errors.md](errors.md) | Every error and warning, with an example and the fix |
| [ai-prompt.md](ai-prompt.md) | An optional copy-paste prompt for a chatbot |
| [examples/strength-block.md](examples/strength-block.md) | A full program example |
| [examples/meal-plan-sample.md](examples/meal-plan-sample.md) | A two-week meal plan sample |

## Two-minute quick start

A program:

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

`#` is the program, `##` is a day, `###` is an exercise. Each exercise needs `sets` and `reps`; everything else
is optional.

A meal plan:

```
# Meal plan: My Plan
avoid: dairy

## Week 1: Taco bowls
Protein: beef
Servings: 5
Time: 45 min

Ingredients:
- 3 lb ground beef 90/10
- 2 cups rice
- salt and pepper

Steps:
1. Brown the beef, about 10 minutes.
2. Build five bowls.

## Eat out
### Subway
- Rotisserie chicken, doubled, no cheese
  caution: say "no cheese" before they start
```

`## Week N: <main dish>` starts a week (1 to 12). Ingredients are plain bullets written the way you would say
them. See [meal-plan.md](meal-plan.md).

The app's script import reads the first line to decide which kind of file it has been given, then shows a
preview with any warnings before anything is saved.

## File kinds

The first line that is not blank must be one of:

- `# Program: <name>` for a workout program
- `# Meal plan: <name>` for a meal plan

Anything else is an error (`not-an-intake-script`). Nothing may come before that line except blank lines and
comments.

## Headings

Headings use Markdown `#` marks. What each level means depends on the file kind:

| Level | Program | Meal plan |
|---|---|---|
| `#` | The program (once, first line) | The plan (once, first line) |
| `##` | A day | `Every week`, `Week N: dish`, `Eat out` or `About` |
| `###` | An exercise | A venue under `Eat out`, or `Recipe: name` inside a week |

Heading text is not case sensitive, and spacing does not matter.

## Conventions

These rules hold everywhere:

- **Labels.** A labeled line is `key: value`. The colon is required. Keys ignore case and spaces
  (`Per 100g`, `per 100 g` and `PER100G` are the same key). A value after the colon can have any spacing.
- **Unknown labels are errors.** A label the parser does not know is an error (`unknown-key`) that suggests the
  closest real one. A line that looks like a label but has no colon is `missing-colon`. Text where a field was
  expected is `unexpected-text`.
- **Each field once.** Giving the same field twice is `duplicate-key`. `note:` can repeat.
- **Blank lines and trailing spaces** are ignored. **CRLF** line endings are fine. A **bullet** can be `-`, `*`
  or `+` wherever a bullet is allowed.
- **Case never matters** for keywords, section names or units.
- **A value can't be empty.** `sets:` with nothing after the colon is `empty-value`.
- **Numbers** use a dot for decimals: `62.5`.

## Comments

HTML comments are ignored, inline or over several lines:

```
<!-- this is ignored -->
sets: 4 <!-- and so is this -->
```

A comment that is never closed with `-->` is an error (`unclosed-comment`), because it would hide the rest of the
file.

## Limits

- A script can be up to 2,000,000 characters.
- At most 100 problems are listed. The rest are cut off with a `too-many-problems` error, so a file that is not a
  script at all produces a short list rather than thousands of lines.
- Stored weights are always kilograms. A program written in pounds is converted on import.

## Allergens

**The script never decides anything about allergens.** The parser passes every ingredient name, dish name,
variation and eat-out order through, unchanged, to the importer, where the app's local allergen engine reads the
text and decides.

- `avoid: dairy` in a meal plan is **information only**. It does not change what the allergen engine looks for,
  and it never clears or hides a warning.
- Writing "dairy-free" in a dish, ingredient or order **can never clear a warning**. "Dairy-free cheese" still
  comes back as *may contain*.
- Nutrition values (`per 100g:`) say nothing about allergens.
- An `allergens:` line on an ingredient can only **add** a warning.

Read labels. The engine matches words; it cannot know that a seasoning packet or a bread roll hides milk. Keep
those on your plan's `Watch for:` checklist.

## Grammar

A concise grammar for both kinds. `[...]` is optional, `{...}` is zero or more, `|` is a choice, and quoted text
is matched without regard to case. `NL` is a line break, `WS` is any amount of spacing.

```
file          = { blank | comment } , ( program | meal-plan )
blank         = WS NL
comment       = "<!--" , anything , "-->"          (* may span lines *)
field(k)      = WS , [ bullet ] , k , WS , ":" , WS , value , NL
bullet        = "-" | "*" | "+"

(* ---- program ---- *)
program       = "# Program:" name NL , { header-field } , { day }
header-field  = field("units") | field("rest") | field("progress") | field("notes")
day           = "##" name NL , { field("note") } , { exercise }
exercise      = "###" name NL , { exercise-line }
exercise-line = field("sets") | field("reps") | field("rpe") | field("weight")
              | field("rest") | field("progress") | field("note") | short-form
short-form    = sets "x" reps NL                    (* alias for sets and reps *)
sets          = integer 1..30
reps          = integer | integer ("-" | "to") integer | "max" | "amrap"
weight        = number [ unit ] | ( "bodyweight" | "bw" ) [ "+" number [ unit ] ]
unit          = "lb" | "lbs" | "kg"
rest          = m:ss | duration                     (* 2:30, 150s, 2 min 30 s *)
rpe           = number 1..10
progress      = "none" | "rpe" | number "%" [ "of max" ]
              | ( "linear" | "double" [ "progression" ] ) "," increment [ "," deload ]
              | "custom:" expression
increment     = [ "add" | "+" ] number [ unit ]
deload        = "deload" number "%" [ "after" integer [ "misses" ] ]

(* ---- meal plan ---- *)
meal-plan     = "# Meal plan:" name NL , { plan-field } , { plan-section }
plan-field    = field("avoid") | "About:" free-text
plan-section  = every-week | week | eat-out | "## About" NL free-text
every-week    = "## Every week" NL , { list("Breakfasts") | list("Snacks") | list("Pantry")
                                     | list("Watch for") | shopping }
week          = "## Week" integer [ ":" | "-" ] dish NL , { week-part } , { recipe }
week-part     = field("Protein") | field("Servings") | field("Time") | field("Serve title")
              | "Serve:" free-text | ingredients | steps | list("Variations") | shopping
              | "Notes:" free-text
recipe        = "### Recipe:" name NL , { field("Servings") | field("Time") | ingredients | steps }
ingredients   = "Ingredients:" NL , { "-" ingredient NL , { ingredient-field } }
ingredient    = [ quantity [ unit ] ] name
quantity      = number | fraction | number "-" number          (* 3, 1/2, 5-6 *)
ingredient-field = field("weight") | field("per 100g") | field("allergens")
steps         = "Steps:" NL , { ( integer "." | "-" ) text NL }
shopping      = "Shopping:" NL , { "-" category ":" item { "," item } NL }
category      = "Produce" | "Meat" | "Pantry" | "Frozen" | "Other" | synonym
eat-out       = "## Eat out" NL , [ free-text ] , { venue }
venue         = "###" name NL , [ free-text ] , { order }
order         = "-" wording NL , { field("caution") | field("estimate") | field("weeks") }
```

The exact rules for every field are in [program.md](program.md), [progress-rules.md](progress-rules.md) and
[meal-plan.md](meal-plan.md).
