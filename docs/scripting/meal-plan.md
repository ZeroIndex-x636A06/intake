# Meal plans

A meal plan file describes up to twelve weeks of meals: one main dish per week with its ingredients and steps,
the breakfasts, snacks and pantry that stay the same all quarter, shopping lists, and what to order when you
can't eat what you cooked. Read [README.md](README.md) first for the conventions that apply everywhere.

**One thing per line, labeled. Order and spacing do not matter.** Ingredients are the exception that proves the
rule: they are plain bullets written the way you would say them ("3 lb ground beef", "salt and pepper"), and
anything extra about an ingredient goes on its own labeled line underneath.

A full two-week sample is in [examples/meal-plan-sample.md](examples/meal-plan-sample.md).

## Header

```
# Meal plan: 12-Week Dairy-Free
avoid: dairy
About:
How this works, in your own words. As many paragraphs as you like.
```

- `# Meal plan: <name>` is required and must not be empty.
- `avoid: dairy, soy` lists the allergens you avoid. **It is information only.** It is not read by the allergen
  engine and never clears a warning. See [Allergens](README.md#allergens).
- `About:` is free text for the "How this works" content. It runs until the next label or heading. You can also
  put it under its own `## About` heading.

## Sections

After the header come the `##` sections, in any order:

| Heading | Contents |
|---|---|
| `## Every week` | Things that stay the same all quarter |
| `## Week 1: Taco bowls` | One week's main dish (1 to 12) |
| `## Eat out` | What to order when you can't eat what you cooked |
| `## About` | Free text, same as `About:` in the header |

Any other `##` heading is `unknown-section`. A heading like `## Month 1` to group weeks is not supported; leave it
out.

Free-text sections (`About`, `Serve`, `Notes` and the intro of `Eat out`) run until the next label or heading.
Blank lines inside them are kept as paragraph breaks. **A line that begins with a label of the current section**
(for example `Time:` inside a week's `Notes:`) starts that field instead, so start such a sentence with a
different word.

### Every week

Lists that apply to every week of the plan. Each is a label followed by bullets:

```
## Every week
Breakfasts:
- Three eggs scrambled with bacon, wrapped in a tortilla
Snacks:
- Meat sticks
Pantry:
- rice, olive oil, soy sauce
Watch for:
- Seasoning packets, especially taco and ranch blends
- Flour tortillas and bread rolls
```

| Label | Meaning |
|---|---|
| `Breakfasts:` | Standing breakfasts (also `Standing breakfasts:`) |
| `Snacks:` | Standing snacks |
| `Pantry:` | Pantry staples. Each item becomes a pantry-staple shopping item in every week. Commas split an item, so `- rice, olive oil` is two items. |
| `Watch for:` | The hidden-dairy checklist, shown as a checklist (also `Hidden dairy checklist:`) |
| `Shopping:` | Shopping items for every week, same shape as a week's [Shopping](#shopping) |

For `Breakfasts`, `Snacks` and `Watch for`, a whole bullet is one item, commas included. An indented line
continues the bullet above it.

## Weeks

```
## Week 1: Taco bowls
```

`Week N` with N from 1 to 12, then a colon (or a dash) and the main dish. Each number can appear once
(`duplicate-week`; out of range is `bad-week-number`), and the dish must not be empty (`bad-week-heading`). Weeks
can be in any order. At least one week is required (`no-weeks`).

### Week fields

Inside a week, in any order. Fields are labeled lines:

| Field | Meaning | Example |
|---|---|---|
| `Protein:` | The protein type, free text | `Protein: beef` |
| `Servings:` | How many servings the recipe makes | `Servings: 5` (`5 servings`, `makes 5`, `7-8` uses the upper number) |
| `Time:` | Total time | `Time: 45 min`, `1 hour 30 minutes`, `90` (a bare number is minutes) |
| `Serve title:` | A heading for the serving text | `Serve title: The bowls` |

Sections inside a week, each a label followed by its lines:

| Section | Contents |
|---|---|
| `Ingredients:` | The recipe's ingredients, see [Ingredients](#ingredients) |
| `Steps:` | The Sunday batch steps, numbered or bulleted |
| `Serve:` | Free text: how to put the week's meals together |
| `Variations:` | One-line weeknight ideas, one per bullet. These are ideas, not recipes. |
| `Shopping:` | The week's shopping list, see [Shopping](#shopping) |
| `Notes:` | Free text about the week |

The week's recipe (servings, time, ingredients, steps) is named after the week's dish. A week with no ingredients,
steps, servings or time has no recipe, which is fine for a week that is all prose.

### Steps

Number your steps (`1.` or `1)`) or use bullets. Each step is one line, and an indented line continues the step
above it. A step that is not numbered or bulleted is an error, because the parser could not tell where it belongs.

```
Steps:
1. Brown the beef with the seasoning, about 10 min.
2. Simmer for 1 hour 30 minutes.
- Bullets work too, 3-4 minutes each side.
```

Durations in step text become cook-mode timers: `10 min`, `1 hour`, `3-4 minutes` (a range uses the upper value),
`35 minutes`, `30 seconds`, and `1 hour 30 minutes` as a single timer. The parser leaves the text exactly as
written. The app finds the durations when you cook.

### Extra recipes

A week normally has one recipe. To attach more (a side, a sauce), add a `### Recipe:` block inside the week:

```
## Week 3: Burgers
Servings: 5
Ingredients:
- 3 lb ground beef

### Recipe: Burger sauce
Servings: 8
Time: 5 min
Ingredients:
- 1/2 cup mayo
- 2 tbsp ketchup
Steps:
1. Stir it together.
```

Inside a `### Recipe:` block only `Servings`, `Time`, `Ingredients` and `Steps` are allowed. A second recipe with the
same name as another in the week is `duplicate-recipe`.

## Ingredients

```
Ingredients:
- 3 lb ground beef 90/10
- 2 packets Taco Bell mild seasoning
- 2 cups rice
  weight: 370 g
  per 100g: 176 kcal, 20 g protein, 0 g carbs, 10 g fat
- 1/2 cup chopped cilantro
- 5-6 limes
- salt and pepper
```

Each ingredient is a bullet, `<quantity> <unit> <name>`, as natural as you would say it.

**Quantity** can be:

| Form | Examples |
|---|---|
| a whole number | `3`, `12` |
| a decimal | `1.5` |
| a fraction | `1/2`, `3/4`, `1 1/2`, `½` |
| a range | `5-6`, `5 to 6` (the app uses the upper value, and the text is kept as written) |

**Unit** comes straight after the quantity, with or without a space (`3 lb`, `3lb`, `3 lbs`). Units are:

| Unit | Also written |
|---|---|
| g | gram, grams |
| kg | kilogram, kilograms |
| oz | ounce, ounces |
| lb | lbs, pound, pounds |
| cup | cups |
| tbsp | tablespoon, tablespoons, tbs |
| tsp | teaspoon, teaspoons |
| can | cans |
| packet | packets |
| clove | cloves |
| bunch | bunches |
| pinch | pinches |
| ml, l | |

No unit is fine: `4 avocados` is a count of 4. A word that is not in the list is simply part of the name, so
`2 large cans crushed tomatoes` is a quantity of 2 and the name "large cans crushed tomatoes". A leading "of" is
dropped (`2 cups of rice`).

**No quantity** is fine: `- salt and pepper` is a name-only ingredient. A quantity with no name (`- 3 lb`) is
`ingredient-no-name`.

When the unit is a weight (g, kg, oz, lb) the gram weight is worked out for you (`3 lb` is 1360.8 g). For anything
else (cups, cans, a count), add a `weight:` line if you want it to count toward macros.

### Ingredient fields

Optional labeled lines directly under an ingredient bullet (indent them or not; they belong to the bullet above):

| Field | Meaning | Example |
|---|---|---|
| `weight:` | Gram weight, as `<number> <g, kg, oz or lb>`. Overrides the weight worked out from the quantity. | `weight: 370 g` |
| `per 100g:` | Nutrition per 100 g. See [Nutrition](#nutrition). | `per 100g: 176 kcal, 20 g protein, 0 g carbs, 10 g fat` |
| `allergens:` | Free text such as `milk, soy`. **It can only add warnings.** | `allergens: milk` |

A weight at the end of the bullet in brackets, `- 2 cups rice (370g)`, is also understood and turned into the
weight.

## Nutrition

`per 100g:` gives calories and macros per 100 grams. The parts can be in any order, each separated by a comma,
and each can be written either way round:

```
per 100g: 176 kcal, 20 g protein, 0 g carbs, 10 g fat
per 100g: fat 10, protein 20, carbs 0, kcal 176
per 100g: 20p, 0c, 10f, 176 kcal
```

Recognised names: `kcal` (also `calories`, `cal`), `protein`, `carbs` (also `carbohydrates`), `fat`, and the
short `p`, `c`, `f`. A trailing `/100g` is accepted. A part that is not understood is `bad-nutrition`, and values
that are not possible (over 900 kcal, or protein, carbs and fat adding to more than 100 g) are
`nutrition-implausible`.

**Macro math only uses ingredients with a known weight and all four values.** Anything else is kept as a text
line, and the app reports the recipe's macros as **incomplete** rather than showing a wrong total. You get a
warning in two cases:

- an ingredient gives only some of the four values, or has values but no weight (`incomplete-nutrition`);
- some, but not all, of a recipe's ingredients are complete (`incomplete-macros`).

Never invent numbers. A plan with no nutrition at all is perfectly valid; leave the fields out.

Nutrition never affects allergen checks.

## Shopping

```
Shopping:
- Produce: 4 avocados, tomatoes, onion
- Meat: 3 lb ground beef
- Pantry: rice
```

Each bullet is `<Category>: item, item, item`. Items are separated by commas (commas inside brackets do not
split), and each item may start with a quantity (`4 avocados` has quantity "4"; `3 lb ground beef` has quantity
"3 lb"). Quantity is free text.

The category is one of **Produce, Meat, Pantry, Frozen, Other**, in any case. Common synonyms are mapped for you:

| Category | Also accepted (first word) |
|---|---|
| Produce | fruit, vegetables, veg, veggies, greens, fresh |
| Meat | protein, seafood, fish, poultry, deli, butcher (so `Meat & grains` is Meat) |
| Pantry | dry goods, grains, canned, spices, condiments, baking, staples, shelf |
| Frozen | freezer |
| Other | misc, miscellaneous, bakery, bread, snacks, drinks, household |

An unknown category is a **warning** (`shopping-unknown-category`) and the items go under Other. A bullet with no
`Category:` at all is `shopping-no-category`, also a warning. `Staples:` marks items as pantry staples.

**If a week has no `Shopping:` section**, its list is generated from the week's ingredients when the plan is
imported. The parsed plan records which weeks that applies to.

A `Shopping:` list inside `## Every week` applies to every week.

## Eat out

For the times you can't eat what you cooked. It works offline, in a parking lot with no signal.

```
## Eat out
Why the salads are the worst bet: the dressing is dairy top to bottom, and it is on the tongs.

### Subway
- Rotisserie chicken, doubled, no cheese, oil and vinegar
  caution: Say "no cheese" before they start, so the gloves change early.
  estimate: 450 kcal, 40 g protein
- Turkey, doubled, no cheese
  weeks: 1, 3-5
```

- Text directly under `## Eat out` (before any venue) is the plan's general eating-out guidance.
- Each `### <Venue>` starts a venue.
- Each bullet under a venue is **one order**. Its wording is the exact text to say at the counter.
- Paragraphs under the venue heading (not part of an order) are the **venue note**, added to each of that venue's
  orders' caution. A venue that has text but no orders is an error (`venue-no-orders`), because the text would be lost.
  Give it at least one order.

### Eat out orders

Optional labeled lines under an order bullet:

| Field | Meaning | Example |
|---|---|---|
| `caution:` | What to watch for with this order | `caution: Shared fryers.` |
| `estimate:` | kcal, protein, carbs, fat, in any order, all optional | `estimate: 450 kcal, 40 g protein` |
| `weeks:` | Which weeks the order applies to. Default is every week. | `weeks: 1, 3-5` |

`estimate:` uses the same parts as [nutrition](#nutrition) (without `/100g`). Leave out what you do not know; an
order with no estimate is shown as "no estimate" and asks you for macros when you log it. `weeks:` takes numbers
and ranges from 1 to 12, or `all`. An order with `weeks: 1, 3-5` is stored once for each of those weeks.

A `caution:` placed before the first order of a venue (unindented, on its own line) is the venue note.

## Common mistakes

| You wrote | What happens | Write |
|---|---|---|
| `Ingredients:` followed by `3 lb beef` | `unexpected-text`: items need a bullet | `- 3 lb beef` |
| `- 2 cups rice (370g)` | Works, but a labeled line is clearer | `weight: 370 g` on its own line |
| `Colour: red` in a week | `unknown-key` | remove it, or use `Notes:` |
| `## Month 1` | `unknown-section` | delete the heading |
| `Shopping: 4 avocados` with no bullet or category | the line is read as one item under Other | `- Produce: 4 avocados` |
| `Time: soon` | `bad-time` | `Time: 45 min` |
| a venue with only a paragraph | `venue-no-orders` | add `- <order wording>` |

The full list is in [errors.md](errors.md).
