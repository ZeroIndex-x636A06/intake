# Allergen tiers

Extends SPEC section 7. The local engine returns one of three flags for a food, plus "clear".

| Tier | Meaning | UI | Blocks logging |
|---|---|---|---|
| CONTAINS | An alias, or an Open Food Facts allergen tag, names the allergen | Red full-width sheet, "log it anyway" tap | Yes |
| MAY_CONTAIN | Plant-milk or "dairy-free" wording, OFF "traces", or a model's guess | Amber review sheet | No, one review |
| CHECK_LABEL | The food commonly hides the allergen (taco seasoning, flour tortillas, buns, breadcrumbs, crackers, Fritos, ...) | A calm outlined chip, "Check the label", with a one-line reason | No, and no extra tap |
| CLEAR | Nothing found | none | No |

Ordering is CONTAINS > MAY_CONTAIN > CHECK_LABEL > CLEAR (`AllergenMatchKind.rank` and
`AllergenStatus.rank` / `worseOf`). `AllergenScanResult.hasAnything` is true only for the first two, so every
existing sheet and gate ignores CHECK_LABEL by construction.

## Never-clear rule

CHECK_LABEL can only add a nudge. For an allergen that already has a CONTAINS or MAY_CONTAIN hit
(any field, any source: alias text, Open Food Facts tags, imported plan files, model claims) no
CHECK_LABEL match is produced, and merging scans takes the worse verdict. A model claim still raises
MAY_CONTAIN on an item that only has a CHECK_LABEL hint. Open Food Facts tags that say "milk" are CONTAINS
as before; a product whose tags show no milk can still get CHECK_LABEL from its name.

## Where the terms live

`domain/allergen/HiddenCarriers.kt`, an in-code list, not the alias table. The alias table has no severity
column, adding one needs schema v2 to change for no gain, and an in-code list reaches existing installs on
update with no re-seed. Each term has a short reason shown in the UI. Matching is whole-word and
case-insensitive, with an optional `...` that allows up to two words in between.

Decisions:

- Bare `roll` is not a term (egg rolls, spring rolls, sushi rolls, "rolled oats"); rolls count when
  qualified as bread: hoagie, hamburger, hot dog, burger, dinner, sandwich, sub, bread, kaiser, hawaiian,
  crescent, cinnamon. Bare `bun` / `buns` is a term.
- Bare `seasoning` is not a term ("Italian seasoning" is a dried herb blend). Terms are `taco ... seasoning`
  (so "Taco Bell mild seasoning" matches), `ranch ... seasoning`, fajita and chili seasoning, and seasoning
  packet / mix / envelope.
- `chocolate` alone is a term. "Milk chocolate" is already a CONTAINS hit through "milk".
- `caramel` does not match "caramelized": whole words only.
- Only the food name is scanned, plus free-text ingredient lines where there is no label to read (plan recipe
  and Recipe Hunter ingredient lists). A real ingredient panel is not scanned: "natural flavor" would nag on
  nearly every product, and the panel already lists milk where it is present.
- Not covered: "restaurant steak" and "butter on grilled chicken at chains". They depend on where the food is
  eaten, not what it is called, and bare "steak" would nag on home cooking. They stay in the written checklist.

## Plan "Watch for" list

`PlanWatchTerms` turns phrases the plan author put in quotation marks (for example `"natural flavoring"`)
into extra CHECK_LABEL terms for food logging and meal-plan scans. Unquoted lines such as "Flour tortillas and
hamburger, hot dog and hoagie rolls" are not split into nouns: "hamburger" alone would flag every burger. They
stay visible in the Plan tab's "Every week" list, and the built-in list already covers the common ones.

## Where it shows

Add food (search rows, portion step), barcode result (only when no stronger flag), meal-plan ingredient lines and
shopping list, eat-out rows, Recipe Hunter, Plan B, menu import review, and the allergen reference screen
(under the hidden dairy checklist). It is not shown on the red or amber sheets.

## Safe Foods (SPEC 7.5)

CHECK_LABEL is not an allergen flag for the safe check: a food logged three or more times with no
contains/may-contain flag keeps its green check and still shows the "Check the label" chip. The check says the
food has not tripped the engine; the chip is a reminder to read this package, so one does not hide the other.

## Storage and sync

`AllergenStatus` (on recipes, menu items and eat-out options) is stored by name in a TEXT column, so adding
CHECK_LABEL needs no migration. `AllergenMatchKind` is never stored. A newer device that syncs or backs up a
CHECK_LABEL row to a build that does not know it is rejected with the existing "does not match this app
version" message rather than crashing; the schema is unreleased, so that is accepted.
