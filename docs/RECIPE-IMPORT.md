# Recipe import

Pasting a link on Food > Recipes > Import from a link reads the recipe on the phone, with no AI. This page records
what is read, where it is stored and why. Privacy is in `docs/PRIVACY.md` ("Recipe link import").

## What is read, and from where

| Item | Source on the page | Stored on |
| --- | --- | --- |
| Name, servings, steps, ingredients | JSON-LD `Recipe`, then schema.org microdata | `recipe`, `recipe.ingredientsText` |
| Notes | WP Recipe Maker `wprm-recipe-notes`; Tasty Recipes `tasty-recipes-notes-body` (JSON-LD has no notes field) | `recipe.notes` (plain text, one note per line) |
| Prep, cook and total time | JSON-LD `prepTime`, `cookTime`, `totalTime` (total is the sum when missing) | `recipe.prepMinutes`, `cookMinutes`, `totalTimeMinutes` |
| Fibre, sugar, saturated fat, sodium | JSON-LD `NutritionInformation` (`fiberContent`, `sugarContent`, `saturatedFatContent`, `sodiumContent`), or microdata | `recipe.fiberPerServing`, `sugarPerServing`, `saturatedFatPerServing` (grams), `sodiumMgPerServing` (milligrams) |
| Photo address | JSON-LD `image`: a string, an array, or an `ImageObject` | `recipe.imageUrl` (the address only) |
| Ingredient groups | WP Recipe Maker `wprm-recipe-ingredient-group` with `wprm-recipe-group-name`; Tasty Recipes `tasty-recipes-ingredients` with h4 headers | see below |

Units: the app stores sodium in **milligrams** everywhere (foods, logs), so a page's "1.2 g" becomes 1200 and a bare
number is read as milligrams. Fibre, sugar and saturated fat are grams; "2000 mg" becomes 2.

## JSON-LD wrapped in comments

Some sites (eatwell101.com, among others) wrap the JSON-LD in a CDATA comment with a space after the marker:
`// <![CDATA[ {...} // ]]>`. The reader strips `//<![CDATA[`, `// <![CDATA[`, `/* <![CDATA[ */` and their closing forms
(`CDATA_OPEN` / `CDATA_CLOSE` in `RecipePageParser`) before parsing. Before this, the Recipe block failed to parse and the
page was reported as having no recipe data. Fixture: `recipes/eatwell101-cdata.html` (the page's own Recipe block, unchanged).

## Tracking parameters

`RecipeUrl.check` removes query parameters named `utm_*` (any case), `fbclid` and `gclid` before anything is fetched, and
the fetcher stores the final address the same way, so the "Open original" link has no tracking. Other parameters and their
order are kept (`RecipeUrl.stripTracking`).

## Clean-up

`IngredientCleaner` runs on every ingredient line: WP Recipe Maker's `(, cut into cubes)` becomes `(cut into cubes)`,
entities are decoded (twice, so a double-escaped `&amp;#8217;` becomes the apostrophe), double spaces and stray commas
are fixed, and brackets left empty are dropped.

## Ingredient groups: one label per ingredient

JSON-LD flattens "For the Noodles / For the Chicken / For the Sauce", which leaves "1 tablespoon butter" and
"1/2 cup butter" ambiguous. The HTML pass (`RecipeHtmlExtras`) reads the headers and matches each HTML line to the JSON-LD
line by its text (digits and letters only, unicode fractions normalised). If every line is matched, or both lists have
the same length, the labels are kept; otherwise the groups are not trusted and the recipe is left ungrouped. A page whose
only group has no name is ungrouped.

**Chosen approach: an ordered group label on each ingredient, not a header pseudo-row.** A pseudo-row would have been read
as an ingredient by the shopping list, the plan exporter and the allergen scan, all of which treat every line of
`ingredientsText` as an ingredient. A label per ingredient leaves those lines pure.

- Free-text ingredients are the lines of `recipe.ingredientsText`. Their labels are `recipe.ingredientGroups`: one label
  per line, in the same order, a blank line meaning "no group", null when nothing is grouped
  (`IngredientGroupsCodec`).
- Ingredients picked from a food (or typed with grams and macros) are rows in `recipe_ingredient`; their label is
  `recipe_ingredient.groupLabel`.
- When a recipe is loaded, food-backed ingredients come first and text lines after them, as before; a run of consecutive
  ingredients with the same label forms one section (`IngredientSections`).
- In the import preview and the pasted-lines box a line starting with `##` is a group heading for the lines below it
  (`## For the Sauce`; a lone `##` ends the group), so groups can be edited as text. The editor shows headings between rows
  and has a "Group" field on the add/edit ingredient sheet.
- Shown on the recipe screen, in cook mode (the Ingredients button), in the editor and in the shopping list, where a merged
  item carries a note such as "For the Chicken, For the Sauce" (`shopping_list_item.note`).

## Allergen tags per line

The recipe screen scans each ingredient line alone with the normal ingredient rules (`AllergenRepository.lineScanner`,
`checkLabelInIngredients = true`, never read for negation) and tags the strongest result: "heavy whipping cream · contains
dairy", "vegan butter · check the label". The summary chip at the top keeps the verdict stored when the recipe was saved;
tapping it scrolls to and highlights the tagged lines. A tag only describes what the local engine found; it never lowers or
clears anything (`LineAllergens`).

## Photo

`ProductImagePolicy.Source.RECIPE_PAGE`: https only, a public host name, at most 3 MB, the same disk cache and offline
behaviour as product pictures, independent of the Open Food Facts switch. Open Food Facts pictures are unchanged
(`Source.OPEN_FOOD_FACTS`).

## Schema (v3, `migrateRecipes_2_3`)

`recipe`: `notes`, `prepMinutes`, `cookMinutes`, `imageUrl`, `fiberPerServing`, `sugarPerServing`, `sodiumMgPerServing`,
`saturatedFatPerServing`, `ingredientGroups`. `recipe_ingredient`: `groupLabel`. `shopping_list_item`: `note`. All nullable,
so v2 rows keep their meaning; backup, export and sync carry them because those entities are serialised whole, and a v2
file decodes with the new fields empty.

## Tests

`RecipeImportTest` runs every item above against two small hand-written fixtures in `src/test/resources/recipes`
(`wprm-groups.html`, `tasty-groups.html`), invented for the tests and shaped like the two plugins' markup.
