# Allergens

Extends SPEC section 7 and the v3 brief ("Allergen false alarms in the user's plan"). The local engine returns one of four flags for a food, plus "clear".

## Levels

| Tier | Meaning | UI | Blocks logging |
|---|---|---|---|
| CONTAINS | A keyword, or an Open Food Facts allergen tag, names the allergen | Red full-width sheet, "log it anyway" tap | Yes |
| MAY_CONTAIN | The food is often made with it (a keyword at that level), OFF "traces", or a model's guess | Amber review sheet | No, one review |
| CHECK_LABEL | The food commonly hides the allergen, or is a plant/"-free" substitute that sometimes still contains it | A calm outlined chip, "Check the label", with a one-line reason | No, and no extra tap |
| MENTIONED_AVOIDED | Free text names the allergen only to say it is left out ("no cheese") | Import preview and details only | No. Not counted as a problem |
| CLEAR | Nothing found | none | No |

Ordering is CONTAINS > MAY_CONTAIN > CHECK_LABEL > MENTIONED_AVOIDED > CLEAR (`AllergenMatchKind.rank`, and `AllergenStatus.rank` / `worseOf`). `AllergenScanResult.hasAnything` is true only for the first two, so every existing sheet and gate ignores CHECK_LABEL and MENTIONED_AVOIDED by construction. Per-item model flags (`ModelAllergenPolicy.Verdict.perItem`) never carry MENTIONED_AVOIDED.

## Predefined allergens

Fifteen allergens ship, each with a deep keyword list of common names, derived ingredients and hidden sources (`domain/allergen/AllergenCatalog.kt`):

- **US top 9:** milk and dairy (`dairy`), egg, fish, crustacean shellfish (`shellfish`), tree nuts, peanuts, wheat, soy, sesame.
- **EU-14 additions:** gluten (cereals), celery, mustard, lupin, molluscs, sulphites.

The key (lower case) is what the profile and every keyword row store; it never changes. Each keyword has a **level**: contains (the word means it is in the food), may contain (often made with it, not always: "pizza" for dairy, "sushi" for fish) or check the label. Plurals match automatically, hyphen and space are interchangeable, and accents are folded ("creme fraiche").

Some phrases contain an allergen word without being the allergen and are excluded per allergen: "peanut butter", "cocoa butter", "cream of tartar" are not dairy; "almond flour" and "rice flour" are not wheat; "water chestnut" is not a tree nut; "oyster mushroom" is not a mollusc.

### Check-the-label carriers

`HiddenCarriers` generalises the original dairy-only list to every allergen: foods that tend to hide the allergen without naming it (flour tortillas, buns and breadcrumbs for dairy; buns and bagels for sesame; stock and soup for celery; vinegar and jam for sulphites; gluten-free pasta for lupin). Each carries a short reason shown with the chip. They live in code, so a list change reaches existing installs on update, and they can never weaken a keyword. They are always on; the keyword screen lists them read-only.

Decisions, unchanged from the dairy list:

- Bare `roll` is not a term (egg rolls, spring rolls, "rolled oats"); rolls count when qualified as bread. Bare `bun` / `buns` is a term.
- Bare `seasoning` is not a term; `taco ... seasoning`, `ranch ... seasoning`, fajita and chili seasoning, and seasoning packet / mix / envelope are.
- `chocolate` alone is a dairy term; "milk chocolate" is already a CONTAINS hit. `caramel` does not match "caramelized".
- "Natural flavor" is a dairy term only (it is on the user's own checklist). Other major allergens must name themselves on a label.
- Only the food name is scanned, plus free-text ingredient lines where there is no label to read (plan recipe and Recipe Hunter lists). A real ingredient panel is not scanned for carriers.

## Negation-aware matching

Deterministic and clause-local (`domain/allergen/Negation.kt`). No model, no network.

**Clauses.** Text splits at commas, semicolons, colons, periods (not decimals), parentheses, " - ", and the words but / then / however / although / though / whereas. " and " splits only when the next word starts a new clause, that is a verb (add, melt, serve, ...) or a subject (it, they, you, ...). "Skip the cheese and sour cream" stays one clause, so both are negated; "no cheese and add butter" splits, so butter is not.

**Negators** reach forward only, from the negator to the end of its clause, but at most four content words (articles and "and" / "or" do not count) and not past with / on / in / using / over / because / if. Negators: no (not "no-bake"), not, without, skip / skipping, hold the, minus, omit, leave out, instead of, rather than, avoid, never, "free of / free from", and "check / checking ... for X" (the scope starts after "for"). "made with oil, not butter" negates butter; "cheese, no salt" does not negate cheese. "Check the bun for milk" also treats the bun as already being checked, so the bun's own carrier reminder is dropped; a negated carrier ("no bun") is dropped too.

**Result.** A negated keyword hit becomes MENTIONED_AVOIDED, "Mentions butter as something to avoid". It is informational only. If the same text also says the allergen for real ("cheeseburger with extra cheese, no cheese on the side") the stronger hit wins; a negated mention never replaces one.

**Claims that never negate:**

- "lactose-free" is about lactose, not milk: a hit stays CONTAINS ("lactose-free milk" blocks).
- "non-dairy" / "nondairy" is CHECK_LABEL (it can contain casein).
- "dairy-free <substitute>" (cheese, provolone, butter, shreds, slice, alternative ...) is CHECK_LABEL. For cheeses the reason is "Some dairy-free cheeses still contain casein". A keyword that is only MAY_CONTAIN in a clause that makes a "-free" claim is also softened to CHECK_LABEL.
- Plant milks and substitutes (oat, almond, soy, coconut, rice, cashew, pea, hemp milk; vegan, plant or plant-based butter and cheese) are CHECK_LABEL with a reason, never MAY_CONTAIN. They still apply to every field, because they name an ingredient, not a claim. Soy milk is still a CONTAINS hit for a soy allergy: the soy keyword is not softened.
- **Self-hits.** The allergen's own name inside its free claim ("dairy" in "dairy-free mac and beef") is not a hit, in every field.

## Safety rule

Negation and "-free" wording apply only to text a person wrote: free-text names, notes, recipe ideas, eat-out wording, and plan recipe lines. They never apply to:

- a product's ingredient panel (`ingredients` when `checkLabelInIngredients` is false);
- Open Food Facts tags (`structuredContains`, `structuredTraces`);
- a plan file's `allergens:` tags (passed as `panelText`).

"Contains: milk" or "may contain milk" on a panel stays a hard warning. The matcher takes `freeText = false` by default, so every caller that has not opted in is unchanged; only the meal-plan scanner (`MealPlanRepository.scanner`: plan import, script import and plan screens) sets it. A model can raise a warning but never clear one: a model claim still raises an amber review on an item that only has CHECK_LABEL or MENTIONED_AVOIDED local hits.

## Never-clear rule

CHECK_LABEL can only add a nudge. For an allergen that already has a CONTAINS or MAY_CONTAIN hit (any field, any source) no CHECK_LABEL match is produced, and merging scans takes the worse verdict. Open Food Facts tags that say "milk" are CONTAINS as before; a product whose tags show no milk can still get CHECK_LABEL from its name. A shorter hit inside a longer one is dropped only when that does not weaken the verdict (a softened wrapper such as "almond cream cheese" does cover the "cream" and "cheese" inside it).

## Search rows

Every result row in Log food and in the Drinks search shows the engine's verdict when the person avoids anything:
the phone's own foods, the bundled USDA database, Open Food Facts and the USDA API alike. A row is scanned with
everything it carries: name, brand, ingredient text, and Open Food Facts allergen and trace tags. Stored tags are
handed to the engine as structured tags (CONTAINS, never downgraded) as well as being read as text; allergen and trace
tags are stored together, so a trace counts as "contains" for a stored food, which is stricter than the product and
never weaker.

A clear result is only as strong as the data behind it (`AllergenEvidence`, tested):

| Row | Clear chip |
|---|---|
| A generic food (bundled USDA, USDA Foundation / SR Legacy), or a food the person entered | "No allergens": the name is the food |
| A packaged product with an ingredient list or allergen tags | "No allergens": the label data was read |
| A packaged product (Open Food Facts, a barcode, a branded USDA API food) with no ingredient list and no tags | "No ingredient list: check the label", in the outlined check-the-label style. Never "No allergens" |

This only ever makes a clear result more cautious. A hit of any tier is shown as the hit it is, and the full check
still runs before anything is logged.

### Imported restaurant menu items

Imported menu items appear in Log food ("Restaurant menus") and in the Swap sheet's saved eat-out section. They are
scanned again every time they are shown or logged (`AllergenRepository.menuScanner`), because the verdict stored at
import can be older than the person's allergen list. The scan reads the name, the description and the ingredient text,
and hands the restaurant's stated "Contains: ... ; May contain: ..." line to the engine as structured contains and
trace inputs (`StatedAllergens`). A stated list can raise a verdict and never clear one.

A menu item that carries no ingredient text and no stated allergens (`MenuSearch.hasNoIngredientInfo`, which is
`AllergenEvidence` for a name-only product) is never shown as "No allergens": Log food says "No ingredient list: check
the label", and Swap offers it as "check the label" with the reason "Ingredients not listed". An item read from a
restaurant's own allergen chart always has stated text: the list the chart gave, or a line saying the chart lists no
major allergens. That line covers the major allergens only; it cannot speak for a custom allergen the chart never
considered, the same as any generic food.

### Nearby and menu orders, and "Choose anyway"

The nearby finder and imported menu items read an order's title and modification note as free text, so "Whopper without cheese" or "no cheese, no butter" is not counted as cheese or butter (`freeText = true`); this clears nothing, and everything else (the rest of the name, a description, a stated "Contains" list, the model's own claims, which can only raise) still counts. A nearby option whose only finding was a negated mention is shown as "Check with the restaurant", never as clear. Ingredient panels, Open Food Facts tags and stated allergen lists are never negated.

**Choosing anyway.** A "contains" option in Swap, Eating out nearby and imported menus has a "Choose anyway" button. It opens a warning that names the allergens and the evidence and says the warning stays on the entry; Cancel has focus, confirm is red. Only a person's tap can do this (never a model or a file). The item is logged with "Logged despite: dairy" added to the entry's name and serving label, and no status anywhere is changed to clear (`AllergenOverride`, no schema change).

## Custom allergens and keywords

My allergens (Food, Allergens) lists all fifteen plus your own, searchable. A switch per allergen says whether you avoid it, and the profile's severity (Avoid / Strict) is set on the same screen. Tap an allergen for its keywords, grouped by level, with Add keyword.

- **Custom allergens** (for example "Nightshades", "Red dye 40") are rows in `allergen_definition` with `isCustom = true`. Removing one is a soft delete (`deletedAt`), together with its custom keywords.
- **Custom keywords** can be added to any allergen, built-in or custom, with a level. Removing one is a soft delete. Re-adding a removed keyword revives the same row.
- **Built-in keywords** cannot be deleted. They can be turned off one by one (`isEnabled = false`), a soft flag; the app warns before turning one off. A turned-off keyword stays in the table, in export, backup and sync, and the matcher skips it. Re-adding a built-in keyword never lowers its level.
- Custom keywords at "check the label" behave like carriers and appear in the chip lists.

## Where the data lives

Schema v2 (unreleased, so `MIGRATION_1_2` was extended in place):

- `allergen_alias` gains `level` (TEXT, default CONTAINS), `isCustom` (default 0) and `isEnabled` (default 1). Existing rows keep CONTAINS; rows whose id does not start with `seed-` are marked custom.
- New table `allergen_definition` (`id`, `name` unique, `displayName`, `description`, `isCustom`, `sortOrder`, audit columns).
- `allergen_definition` is in `BackupTables`, so export, backup and sync (which reads the same list) carry it. Nothing here is ever hard-deleted.

Seeding (`AllergenSeed`): deterministic ids (`seed-<allergen>-<keyword>`, `seed-allergen-<key>`), the fixed `SEED_TIMESTAMP`, and `INSERT OR IGNORE`, run at first run and topped up on every open (it is a no-op once every built-in row exists). Because an existing row is never overwritten, a keyword you turned off stays off and a level is never weakened on update.

`AllergenStatus` (on recipes, menu items and eat-out options) is stored by name in a TEXT column, so MENTIONED_AVOIDED needs no migration. A newer device that syncs or backs up a MENTIONED_AVOIDED row to a build that does not know it is rejected with the existing "does not match this app version" message rather than crashing.

## Import preview

Both the plan import and the script import use one preview (`ImportAllergenSection`):

- One row per distinct item and verdict; a dish named in the week and again as its recipe is one row.
- The full item text, never cut at 40 characters, with only the key word in bold.
- Blocked opens expanded. May contain, Check the label and Mentioned as avoided are collapsed sections with counts.
- A plain summary line, e.g. "2 need a decision · 5 worth a look · 3 mentioned as avoided" (worth a look = may contain + check the label).
- Plain reasons: "Says butter", "Mentions butter as something to avoid", "Some dairy-free cheeses still contain casein".

## Plan "Watch for" list

`PlanWatchTerms` turns phrases the plan author put in quotation marks (for example `"natural flavoring"`) into extra CHECK_LABEL terms for food logging and meal-plan scans. Unquoted lines such as "Flour tortillas and hamburger, hot dog and hoagie rolls" are not split into nouns. They stay visible in the plan's "Every week" list, and the built-in list already covers the common ones.

## Where it shows

Add food (search rows, portion step), barcode result (only when no stronger flag), meal-plan ingredient lines and shopping list, eat-out rows, Recipe Hunter, Plan B, menu import review, and the allergen reference screen. CHECK_LABEL is not an allergen flag for the safe check: a food logged three or more times with no contains/may-contain flag keeps its green check and still shows the "Check the label" chip. MENTIONED_AVOIDED shows as a clear check in lists and as its own section in the import preview.

## Adapting a recipe (Recipe Hunter, "Could be adapted")

`domain/allergen/Substitutions.kt`. Deterministic, local, no AI. It only ever runs on a recipe whose ingredient lines scan as CONTAINS an avoided allergen. A recipe whose lines scan clean is "Already fine" and stays in the main results whatever its title says ("Vegan Mac and Cheese", "Dairy-Free Cheesecake"); the title rules below never block or downgrade it.

- **Title scan.** Recipe Hunter reads the title with the free-text rules (negation and "-free" wording, so "Dairy-free alfredo" is not evidence) and the ingredient lines with the strict rules, then combines them. Labelled-free products in the lines ("vegan cheddar", "non-dairy milk") stay "check the label", never "contains" and never cleared.
- **Swap classes.** Per allergen, ingredient classes with named substitutes in order of preference: cheese, cream cheese, butter, milk, cream, sour cream, yogurt, buttermilk, condensed milk (dairy); eggs (flax egg), egg whites (aquafaba), mayonnaise (egg); pasta, noodles, breadcrumbs, bread, grains, thickening flour, soy sauce (wheat, gluten, soy); nut topping, nut butter, nut milk, peanuts, peanut butter, peanut oil, sesame seeds, sesame oil, tahini, soy milk, miso. A substitute that carries another allergen the person avoids (oat milk with gluten, tamari with soy, cashew cream with tree nuts) is skipped; if none is left the recipe is not adaptable.
- **Role and quantity.** Each class has a share-of-recipe-weight limit (from `IngredientAmounts`, approximate grams). Above the limit the ingredient is a main ingredient: refused. Between the borderline share and the limit the swap is offered as "Borderline" with a warning. A cream sauce at about 20 to 40% of the recipe is borderline; above 40% it is refused. All of one allergen's lines together above 40% is refused.
- **Count cap.** More than 3 distinct lines carrying avoided allergens: refused.
- **Defining dishes and titles.** A title for a dish that is the allergen (cheesecake, custard, alfredo, tres leches, mac and cheese, meringue, souffle, quiche, brioche, hummus, satay...) is refused, but only when the lines actually contain the allergen. A title that merely names an ingredient is refused once the ingredient is a real share.
- **Baking chemistry.** In a baked good, 3 or more eggs, eggs above 20% of weight, or wheat flour above 8% is the structure: refused. One egg or a thickening spoon of flour is adaptable.
- **Anything unplaced is refused:** a flagged line with no class (unless it is optional and tiny: then "leave it out"), a warning no ingredient line explains, an unusable substitute.
- **Never clears a warning.** The original recipe keeps its CONTAINS verdict and warning on the card. "Save adapted copy" saves a separate recipe made of the swapped lines, re-scanned locally (and with any model claim merged back in); if the copy still blocks, nothing is offered. A plant cheese saves as "check the label", never "clear". Nutrition is the page's figure for the original, kept and noted as an estimate in the notes, or "not listed" when the page gave none.

### Estimates, nutrition-only menus and "No allergens found" (round 5)

The nearby and imported-menu lists showed "No allergens found" for AI-estimated items (McDonald's Hotcakes, Filet-O-Fish, Whopper, BK French Fries). The cause: the estimate path asked the model for an ingredient list, so the item carried ingredient text, `hasNoIngredientInfo` was false, the scan found nothing, and the chip read clear. The stored "check the label" status was never consulted when the list was drawn. Saved eat-out options and AI-found options had the same gap, and an AI-read PDF with no allergen column was saved as CLEAR.

The rule now (`MenuAllergenEvidence`, tested): a menu item can be clear only when its source STATED allergen information (a chart row, a "Contains:" line, an ingredient panel, or the chart's "no major allergens listed" line) and that information is clean. Otherwise the best verdict is "Check with the restaurant" (the check-label tier). That covers:

- AI estimates. Marked with the description prefix "AI estimate"; the model's own ingredient guess is moved into the description, so it is never ingredient text. What the model says an item contains is stored as a stated "May contain" line, so it raises an amber review now and whenever the person's allergens change. It is raise-only.
- Pages or PDFs with no allergen column (the OSU nutrition PDF): no ingredient text is kept, and an AI-read item with no ingredient text is saved as CHECK_LABEL, not CLEAR.
- Saved eat-out options and options an AI found: no ingredient text exists, so they never read as clear.
- Merged chart plus nutrition menus: items matched to a chart row carry the chart's stated list, so the chart's verdicts apply; nutrition-only items stay unstated.

The estimate prompt asks the model to list the allergens it knows each item contains (Hotcakes: milk, egg, wheat), for every item, whether or not the person avoids anything.
