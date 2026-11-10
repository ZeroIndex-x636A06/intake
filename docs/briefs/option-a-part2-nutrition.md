# Brief: Option A part 2, the Nutrition tab

**Status:** in progress on branch `agent/nutrition-wip`. That branch holds WIP commits that may not compile. Merge `claude/app-build-k2ag1d` into it, finish the work, verify, and merge it back. See `docs/HANDOFF.md`.

## Reading list
- `CLAUDE.md` and `docs/HANDOFF.md`.
- `docs/FEEDBACK-R2.md`.
- `docs/IA-v5.md`, especially §9, which wins over anything that conflicts with it.
- `docs/SETTINGS-INVENTORY.md`.
- `docs/TASKS.md`, which contains Part 1's handover notes.
- `RESUME.md`, the "Option A part 1" section.
- The mockup: `design/mockup-v5/option-a.html`, and the frames `design/mockup-v5/shots/a-nutrition.png`, `a-log.png`, `a-photo.png`, `a-swap.png`, `a-drinks.png`, `a-food.png` and `a-allergens.png`. Taller versions are in `shots/full/`.

## Part 1 handover rules
- **Where routes go.** A screen that only Nutrition opens goes inside the Nutrition tab graph. A screen two tabs open goes once, at the root of `IntakeNavHost`.
- **Screen wrappers.** Pushed screens use `PushedScreen`. Tab roots use `TabRootScreen`. The top bar is `IntakeTopBar`.
- **No settings on use screens.** A new setting goes into all three of these, or `SettingsRegistryTest` fails:
  - the inventory;
  - `SettingsRegistry`;
  - `SettingsControls.kt`.
- **Placeholders to replace:**
  - `Route.Nutrition`, which is the old hub;
  - the temporary `DrinksScreen`;
  - the `SwapSheet` stub;
  - the Allergens placeholder row.
- **Drink settings** (quick sizes, drink values, water target, units) live in Settings → Water and drinks. Keep them out of Drinks.
- **Today's water card** only adds or removes entries that are already logged.
- **Settings you must read:**
  - `portionUnit`: g or oz for food portions everywhere, including the photo estimate;
  - `showSodium`: salt vs sodium;
  - `eatOutRadius`.

## Build, matching the Option A frames

### 1. Nutrition home, top to bottom
1. **Allergen strip.** Red-tinted, with the user's allergen chips and a status line such as "Dairy · strict". Tapping it opens the merged `Allergens` screen.
2. **Primary tiles.** Three equal tiles: Search, Scan, Photo.
3. **Today's planned meal.** Shows Swap and Log it.
4. **Nutrition dashboard.**
   - A calorie ring.
   - Bars or tiles for protein, carbs, fibre, fat, saturated fat, sugar, salt or sodium, and hydration.
   - Micronutrients under "More nutrients".
   - Unknown values show "not listed", never 0.
5. **Sections:**
   - Plan (meal plans);
   - Recipes;
   - Meals (templates);
   - Eating out (saved options and menu import).
6. **A Drinks row.**

### 2. Log food
A full-screen sheet with three equal modes:
- **Search:** unified search across my foods and recents, offline USDA, Open Food Facts, and the USDA API when a key is set. Results show thumbnails and allergen badges.
- **Scan:** the existing barcode scanner, in its bounded card.
- **Photo:** the existing multi-food photo flow, restyled.

Portions follow `portionUnit`. "My foods" becomes a "Mine" tab here.

### 3. Food detail
- Image, portion stepper, macro tiles, allergen block, Log, and "Swap for another".
- Contribute to Open Food Facts, when eligible.
- **Nutrition Facts label:**
  - FDA style, with % Daily Value, scaled to the portion.
  - Built from per-100 g values. A missing nutrient shows "not listed".
  - A reusable composable, backed by a pure domain model with tests.

### 4. Drinks
Drinks come from the same food database.

**Data**
- `food.drinkCategory`, nullable, added in `migrateDrinks_2_3()`.
- Seed generic drinks with the seedId pattern:
  - water, sparkling water;
  - coffee, latte (dairy);
  - black tea, green tea;
  - cola, diet cola, lemon-lime soda;
  - orange juice, apple juice;
  - sports drink, energy drink;
  - milk, oat milk, almond milk;
  - beer, wine, spirits.
- Each seeded drink carries per-100 ml nutrition, caffeine, ABV and a hydration factor.
- Categorise USDA and Open Food Facts beverages by rules, with tests.

**Logging**
- One repository call logs BOTH a hydration entry (volume × factor, caffeine, alcohol) AND a food log entry, so sugar and kcal count.
- A soft delete removes both.
- Bridge the existing hydration logging so there is one flow.

**UI**
- Category chips: Water, Coffee, Tea, Soda, Juice, Sports & energy, Milk & alternatives, Alcohol, Other. Tapping Soda lists every soda.
- Search.
- Quick sizes taken from Settings, plus Custom.
- Today's drinks list, and the day's caffeine and sugar from drinks.
- Every drink goes through the allergen engine.

### 5. Swap sheet (replaces Plan B)
Opened from Today's Next up and from the planned meal on Nutrition.

**Sections**
1. Plan & recipes.
2. Saved: templates and saved meals.
3. Eat out nearby:
   - saved eat-out options first (these work offline);
   - then "Find healthy options nearby", which uses the Plan B finder through AI, optionally, on tap, within `eatOutRadius`, with the location-once or ZIP code flow.

**Each option shows**
- Macro deltas against the planned meal, for example "+120 kcal, −8 g protein".
- Allergen status. Blocked options are shown, disabled, with the reason.

**Choosing an option** logs it in place of the planned meal. Remove Plan B as a standalone destination.

### 6. Allergens
Wire the strip to the merged screen. Match the frame.

### 7. Tidy-up
- Remove the old Food hub rows and any duplicate entry points.
- Script help lives in Settings → About and help.

## Tests
- Label Daily Value maths, and "not listed".
- Drink categorisation.
- A drink log creates both rows, and deleting it soft-deletes both.
- Swap deltas and allergen gating.
- Dashboard aggregation with unknown values.
- `portionUnit` conversions.
- The migration case.
- `NavGraphGuardTest`.

## Docs
Update:
- `docs/TASKS.md`
- `RESUME.md`
- `docs/SETTINGS-INVENTORY.md`
- `docs/HYDRATION.md`
- `docs/PRIVACY.md`, if anything there changed.

## Constraints
- **Allergen safety stays as it is:** deterministic, and nothing can clear a warning.
- No hard deletes.
- No model identifiers in commit message bodies.
