# Optional: ask a chatbot to write the file

**This is optional.** The app never uses AI to import a script. A deterministic parser reads the file, and
everything here works by hand. If you would rather have a chatbot do the typing, give it the prompt below, then
read what it wrote, because **the chatbot can be wrong** and the parser will tell you about format mistakes but
cannot check that the food or the weights are sensible.

Paste the prompt, then add your own plan or workout after it.

## Prompt for a workout program

````text
Write a workout program in "Intake script", a strict plain-text format. Output only the file, in one code block,
nothing else. Follow these rules exactly.

Structure: "# Program: <name>" first. Then optional header lines, one per line: "units: lb" or "units: kg",
"rest: 2:00" (default rest), "progress: <rule>" (default rule), "notes: <text>". Then days as "## <Day name>".
Under each day, exercises as "### <Exercise name>". The exercise name is only the heading text.

Under each exercise, one field per line as "key: value". Order does not matter.
- sets: a whole number (required)
- reps: "8", "6-8" or "max" (required)
- rpe: a number from 1 to 10 (optional)
- weight: "185", "185 lb", "80 kg", "bodyweight" or "bodyweight + 25 lb" (optional; no unit means the program's units)
- rest: "2:30", "150s" or "2 min 30 s" (optional; never a bare number)
- progress: one of
  "linear, add 5 lb" (optionally ", deload 10% after 3 misses"),
  "double progression, add 5 lb",
  "rpe" (only if the exercise has an rpe line),
  "80% of max",
  "none",
  "custom: <expression>" (variables: weight, reps, sets, completedReps, rpe, failureCount, week, all weights in kg;
  operators + - * / < <= > >= == !=, if (cond) a else b, min(a, b), max(a, b), round(x))
- note: free text (optional, may repeat)

Never put several values on one line. Never use tables. Do not add any other keys. Do not invent weights: leave
"weight:" out if I did not give one. You may use <!-- comments -->.

Example:

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
````

## Prompt for a meal plan

````text
Write a meal plan in "Intake script", a strict plain-text format. Output only the file, in one code block,
nothing else. Follow these rules exactly.

Structure: "# Meal plan: <name>" first. Optional "avoid: dairy, soy" (information only), and optional
"About:" followed by free text.

"## Every week" may contain these labels, each followed by "- " bullets: "Breakfasts:", "Snacks:", "Pantry:",
"Watch for:".

Each week is "## Week N: <main dish>" with N from 1 to 12. Inside a week, labeled lines in any order:
"Protein: <text>", "Servings: <number>", "Time: <e.g. 45 min>". Then sections, each a label on its own line:
- "Ingredients:" then bullets like "- 3 lb ground beef", "- 2 cups rice", "- 4 avocados", "- salt and pepper".
  Units allowed: g, kg, oz, lb, cup, tbsp, tsp, can, packet, clove, bunch, pinch, or a plain count. Fractions
  (1/2) and ranges (5-6) are fine. Extra facts go on indented lines under the bullet, never on the same line:
  "  weight: 370 g", "  per 100g: 176 kcal, 20 g protein, 0 g carbs, 10 g fat". Only include nutrition numbers
  that I gave you or that are on a label. Never guess nutrition.
- "Steps:" then numbered lines "1. ...". Keep durations in the text ("about 10 min").
- "Serve:" then free text. "Variations:" then bullets (one-line ideas). "Notes:" then free text.
- "Shopping:" then bullets "- Produce: 4 avocados, tomatoes". Categories: Produce, Meat, Pantry, Frozen, Other.

"## Eat out" may start with free text, then venues as "### <Venue>". Each order is a bullet containing the exact
words to say, with optional indented lines "  caution: ...", "  estimate: 450 kcal, 40 g protein"
(kcal, protein, carbs, fat; any, all optional) and "  weeks: 1, 3-5". A venue must have at least one order.

Never put several values on one line except lists named above. Never use tables. Do not add any other labels.
Do not judge allergens or claim anything is safe: write ingredients as plain text and let the app decide.
You may use <!-- comments -->.
````

## Using the result

1. Save the chatbot's output as a text or Markdown file.
2. Import it. The parser lists any problem with its line and column, and nothing is imported until the file has
   no errors.
3. Read the preview, especially the warnings and every ingredient. The format check is exact; the content
   is yours to verify.

The rules the prompt condenses are in [program.md](program.md), [progress-rules.md](progress-rules.md) and
[meal-plan.md](meal-plan.md). If the chatbot gets something wrong, [errors.md](errors.md) has the fix.

`avoid:` lines, the word "dairy-free", and nutrition values can **never** clear an allergen warning. Only the
app's local allergen engine decides, and it only ever adds warnings. See [Allergens](README.md#allergens).
