# Meal plan file format (version 1)

The Plan tab (SPEC section 9) ships empty. You fill a week in one of two ways:

1. **Choose a recipe for week N.** Pick any saved recipe (Recipe Hunter saves, or
   anything in My recipes). The week's dish name comes from the recipe, the protein
   type is text you edit, and the recipe becomes the week's MAIN dish.
2. **Import a plan file.** Plan tab, "Import plan file", pick a `.json` file with the
   system file picker. A preview shows what will be added and every allergen hit the
   local engine found. Nothing is written until you confirm.

Re-importing the same file updates the same rows: ids are derived from week number and
recipe name, so there are no duplicates. A re-import also restores anything from the file
that you removed in the app, and overwrites edits to those rows.

## Shape

Only `format`, `version` and, per week, `weekNumber` and `mainDishName` are required.
Everything else is optional, because a real plan is usually prose with no nutrition data.
Unknown keys are ignored.

```
{
  "format": "intake-meal-plan",
  "version": 1,

  // Plan-wide, shown collapsed under "Every week" on the Plan tab.
  "intro": "text",
  "standingBreakfasts": ["text"],
  "standingSnacks": ["text"],
  "pantry": ["text"],                 // becomes pantry-staple shopping items on every week
  "hiddenDairyChecklist": ["text"],
  "eatOutNotes": "text",

  "weeks": [{
    "weekNumber": 1,                  // 1-12, each at most once
    "mainDishName": "text",
    "proteinType": "text",
    "notes": "free text for the week",
    "servingTitle": "The bowls",      // heading for "serving"
    "serving": "how to serve it",
    "variations": ["one-line weeknight idea"],   // plain text, shown in "Other ways to eat it"
    "recipes": [{
      "name": "text",
      "role": "MAIN",                 // MAIN (at most one per week) or VARIATION
      "servings": 4,
      "totalTimeMinutes": 35,
      "steps": ["step text"],         // for MAIN these are the Sunday-batch steps; cook mode reads them
      "ingredients": [{
        "name": "text",
        "grams": 600,                 // optional
        "kcalPer100g": 180,           // optional, all four or none
        "proteinPer100g": 20,
        "carbsPer100g": 0,
        "fatPer100g": 11,
        "allergenTags": "free text"   // optional; can only add warnings
      }]
    }]
  }],

  "shoppingItems": [{
    "name": "text",
    "quantity": "free text, may be empty",
    "category": "PRODUCE",            // PRODUCE, MEAT, PANTRY, FROZEN or OTHER
    "isPantryStaple": false,
    "weekNumber": 1                   // optional; omit for every week in the file
  }],

  "eatOutOptions": [{
    "venueName": "text",
    "whatToOrder": "the exact words to say",
    "cautionNote": "text",
    "estimatedKcal": 420,             // all four optional; omit when the source gives no estimate
    "estimatedProtein": 34,
    "estimatedCarbs": 40,
    "estimatedFat": 12,
    "weekNumber": null                // null or omitted: applies to every week
  }]
}
```

### Validation

Rejected with a list of reasons (nothing imported): wrong `format`; a `version` other than
1 (a newer version says to update the app); no weeks; week number outside 1-12 or repeated;
blank dish or recipe names; two MAIN recipes in a week; duplicate recipe names in a week;
role other than MAIN/VARIATION; servings below 1; negative or implausible grams or
nutrition (over 900 kcal per 100 g, or protein+carbs+fat over 100 g per 100 g); a shopping
category outside the five; eat-out entries without a venue or order wording; files over 2 MB.

### Missing data

- An ingredient with `grams` and all four per-100g values is stored as a Food (source
  RECIPE) and a recipe ingredient, and counts toward the recipe's macros.
- Any other ingredient is kept as a text line on the recipe. The recipe's per-serving macros
  are then **incomplete**: they are not stored, the Plan tab says which ingredients were
  not counted, and "Log this meal" asks you to confirm or enter macros first instead of
  logging a partial total.
- An eat-out option without `estimatedKcal` is stored with zeros and shown as "No estimate".
  Logging it asks you to type macros.

### Where things land

| File content | Stored as |
|---|---|
| Week | `plan_week` (id derived from week number) |
| Week `serving`, `servingTitle`, `variations`, `notes` | JSON inside `plan_week.notes` (plain text notes still read as a note) |
| Plan-wide fields | JSON inside the `notes` of a `plan_week` row with `weekNumber` 0, which the week strip never shows |
| Recipe, ingredients, link | `recipe`, `recipe_ingredient`, `plan_week_recipe` |
| Shopping items and `pantry` | `shopping_list_item`, one per week |
| Eat-out options | `eat_out_option` |

No schema change was needed, and all of these tables are already in sync, export and backup.
Everything is written in one transaction.

## Allergens

Only the local engine decides. Every ingredient name, every recipe and dish name, every
variation idea and every eat-out `whatToOrder` is scanned with your profile and alias table.
An ingredient's `allergenTags` text is scanned too, so a tag such as "contains milk" can add a
warning, while "allergen-free" or "dairy-free" cannot clear one. The verdict is stored on the
recipe and the eat-out option, and re-checked with today's profile whenever it is shown.
The engine matches words: it does not know that a seasoning packet, tortilla, bun,
breadcrumb, cracker or Fritos can hide milk, so keep those on the file's own checklist and
read labels. "Dairy-free cheese", "dairy-free provolone" and "oat milk" come back as
**may contain**, never clear.

## Example

This is an **example**, not anyone's plan. The same file is in
`app/src/test/resources/example-meal-plan.json` (tests only; it is not bundled in the app).

```json
{
  "format": "intake-meal-plan",
  "version": 1,
  "weeks": [
    {
      "weekNumber": 1,
      "mainDishName": "Example chicken and rice bowls",
      "proteinType": "Chicken",
      "variations": ["Chicken in a warm wrap"],
      "recipes": [
        {
          "name": "Example chicken and rice bowls",
          "role": "MAIN",
          "servings": 4,
          "totalTimeMinutes": 35,
          "steps": ["Rinse the rice.", "Sear the chicken 5 minutes per side.", "Serve over rice."],
          "ingredients": [
            {"name": "Chicken thigh, boneless", "grams": 600, "kcalPer100g": 180, "proteinPer100g": 20, "carbsPer100g": 0, "fatPer100g": 11},
            {"name": "Lemon"}
          ]
        }
      ]
    }
  ],
  "eatOutOptions": [
    {"venueName": "Example Sandwich Shop", "whatToOrder": "Rotisserie chicken sandwich, no cheese", "cautionNote": "Ask for a glove change."}
  ]
}
```

## Eat-out filter

"Can't eat what I cooked?" shows: this week's own options; options for every week whose
text names the week's protein (chicken, beef/steak, pork, fish, and so on) or names no
protein at all. Options that name only a different protein are hidden behind "Show all
options". It reads local data only and works offline; with no options it links to Plan B.
