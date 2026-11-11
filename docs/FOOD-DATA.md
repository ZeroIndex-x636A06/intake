# Food data

Where Intake's foods come from, how search works, and how a food you made can go back
to an open database. Search works with no key and no internet.

## Sources

| Source | What it is | Licence | Bundled? |
| --- | --- | --- | --- |
| USDA FoodData Central: SR Legacy (April 2018) and Foundation Foods (December 2025) | About 8,000 generic foods (apple, cheddar, chicken breast) | Public domain (CC0 1.0), U.S. Department of Agriculture | Yes, as `app/src/main/assets/food_ref_v1.db` |
| Open Food Facts | Crowdsourced packaged foods and barcodes | ODbL (database) and DbCL (contents), openfoodfacts.org | **No.** Looked up online only; ODbL forbids shipping it inside the app without share-alike terms |
| USDA FoodData Central API | The same USDA data, live, with branded foods | Public domain | No. Only with the user's own key, as before |

Credit text shown in the app (Food > Food database & credits) lives in
`data/foodref/FoodRefInfo.kt`:

- USDA FoodData Central, U.S. Department of Agriculture, Agricultural Research Service,
  public domain (CC0 1.0).
- Open Food Facts, made available under the Open Database Licence (ODbL), with contents
  under the Database Contents Licence.

## The bundled database

- **Dataset versions:** SR Legacy 2018-04 (`FoodData_Central_sr_legacy_food_csv_2018-04.zip`)
  and Foundation Foods 2025-12-18 (`FoodData_Central_foundation_food_csv_2025-12-18.zip`).
- **Rows:** 8,054 foods (7,697 SR Legacy, 357 Foundation Foods). A Foundation food replaces an SR
  Legacy food of the same name; 10 records with no energy value are skipped.
- **Size:** 1.33 MB (`food_ref_v1.db`), well under the 5 MB aim. The APK compresses it further.
- **Each row:** name, category, kcal, protein, carbs, fat, fibre, sugars, sodium (mg) and
  saturated fat, all **per 100 g**, plus up to three common portions with gram weights
  (from `food_portion`).
- **Separate database.** Room opens it with `createFromAsset` as `FoodRefDatabase`
  (`food_ref.db`), apart from the user's `intake.db`, so nothing of the user's migrates
  because of it. It is never written to. A new dataset is a new asset file plus a higher
  `FoodRefDatabase.VERSION`; Room re-copies it (`fallbackToDestructiveMigration` is used
  for this reference file only, and never for `intake.db`).

### Rebuilding it

```
curl -O https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip
curl -O https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_foundation_food_csv_2025-12-18.zip
unzip each into its own folder, then:
python3 tools/build_food_db.py --sr <sr folder> --ff <foundation folder> \
    --out app/src/main/assets/food_ref_v1.db
python3 -I tools/test_build_food_db.py        # runs the converter on tools/fixtures
```

`tools/build_food_db.py` uses only the Python standard library. Room validates the table
against `RefFoodEntity` when it first opens the file, so the script's `CREATE TABLE` and the
entity must match; `FoodRefAssetTest` compares the asset with the exported Room schema.

## Search

Order in Log food > Search, each source labelled:

1. **My foods and recents** (the user's table).
2. **Food database (USDA, works offline).**
3. **Packaged foods (Open Food Facts)**, only when online and switched on.
4. **USDA online (your key)**, only when a key is saved (the existing path).

When nothing matches, "Create a food" opens the form with the query as the name.

### Foods and drinks are separate searches

Log food lists **foods only**. The Drinks page (Nutrition > Drinks) has its own search, under its categories and
quick sizes, over the same four sources, and lists **drinks only** (docs/HYDRATION.md, "Drinks are foods").

One rule decides, for both searches and for the copy to the phone: `DrinkRule` in `domain/drinks/DrinkCategory.kt`
(tested in `DrinkCategoryTest`).

- A food already on the phone is a drink exactly when its `drinkCategory` is set.
- A result from a source is judged by the source's own category first (USDA food group "Beverages"; Open Food Facts
  `categories_tags` such as `en:beverages`, `en:sodas`, `en:plant-based-milks`), then by the name rules when the
  source gives none ("Coke", "Orange juice", "Oat milk").
- Soups, broths, stocks, oils, sauces, gravies, dressings and vinegar are never drinks, whatever the source says. Nor are
  powders, mixes, concentrates, condensed or dry milk, coffee beans or tea leaves. Milk and plant milks are drinks.

Each source is narrowed by `SearchScope` (`FOODS` or `DRINKS`): the offline database ranks every candidate and then
drops the other kind, so "milk" still fills a list; Open Food Facts results are filtered after mapping, and the Drinks
search also adds the fixed filter `categories_tags:"en:beverages"` to the query so a page is not spent on foods.

In Log food the "Log a drink" row stays at the foot of the list, and carries the words typed into the Drinks search.
When the words match only drinks (on the phone, in the offline database, or in what Open Food Facts answered), the
empty state says "Drinks are logged from Drinks" with a button that opens Drinks with the same words. Recents and the
Mine tab still list drinks the person has logged or saved; they are not searches.

### Allergen chip on every row

When the person avoids anything, every result row, from every source, shows the local engine's verdict
(`AllergenRepository.foodScanner`), computed off the main thread from everything the row carries: name, brand, the
ingredient text if there is any, and the Open Food Facts allergen and trace tags (handed to the engine as the product's
own allergen list, which is never downgraded). The search index of Open Food Facts returns tags but no ingredient text
(checked against the live service; `ingredients_text` is requested in case that changes), so a search row from there
is tags plus name. A packaged product with neither tags nor ingredients cannot be cleared by its name: its chip reads
"No ingredient list: check the label", not "No allergens" (docs/ALLERGENS.md, "Search rows").

Offline search uses a `LIKE '%token%'` match for every query word (up to four) over the
name column, fetches up to 500 candidates shortest-name first, and ranks them in Kotlin
(`FoodRefSearch`): all words start a word and the name begins with the first word, then
all words start a word, then mid-word matches; ties go to the shorter name. A scan of
about 8,000 short rows measured under 10 ms on a desktop JVM; expect tens of
milliseconds on a phone, well inside the 300 ms debounce. No FTS table is needed.

Results show kcal and protein for the food's first listed portion. Choosing one **copies it
into the user's foods** as source `DATABASE` with the id `usda-<fdcId>` (so choosing it
again reuses the first copy and any edits), and the portion step offers the other listed
portions. Logs copy their numbers at log time, so they never change if the bundled dataset
does. Every food, from every source, goes through the allergen engine when it is logged
(SPEC section 7); generic foods have no ingredient text, so the name is what is scanned.

## Open Food Facts

### Search (no key, optional)

`GET https://search.openfoodfacts.org/search?q=<text>&page_size=40&langs=<languages>&fields=...`
(search-a-licious). Debounced at 600 ms, needs at least three characters, shown only when
the phone is online, and can be turned off in Settings > Food database. **Only the query text
is sent**, plus the User-Agent, the food languages and, from the Drinks search, the fixed
beverages filter. Results are saved to the user's foods only when one is chosen. Forty hits
are asked for because the language rule and the food / drink split both leave rows out.

Fields asked for: `code, product_name, product_name_<each food language>, lang, lc, brands, quantity,
serving_size, serving_quantity, allergens_tags, traces_tags, ingredients_text, nutriments, categories_tags`
and the two thumbnail fields.

### Food search language

Settings > Food database has **Food search language** (Phone language, English, Spanish, French, German,
Italian, Portuguese, Dutch; the default is the phone's language) and **Show products in other languages** (off by
default). Both are also asked on the units step of the welcome flow. The person's food languages are the chosen one
and then English.

With the switch off, a product is listed only when it has a name in one of those languages:

- a non-empty `product_name_<lang>` for one of them, or
- its main language (`lang`, or `lc`) is one of them, in which case the plain `product_name` is that name.

A product whose only name is the plain `product_name` and whose main language is another one is left out. That is
what hides "Gemarineerde kipfilet" and "Frites moyennes - McDonald's" from an English reader (`OffLanguage.hasNameIn`,
`pickName` and `isShown`, tested in `OffLanguageTest` with those names). The older script rule still applies on top:
a Cyrillic name tagged English is not listed either. With the switch on, everything is listed under the name the
product has. The rule cannot catch a product whose contributor typed a Dutch name into the English field.

Nothing here translates anything, and nothing here touches allergens: a hidden result is simply not listed.

### Barcode lookup (no key)

`GET https://world.openfoodfacts.org/api/v2/product/<barcode>.json?fields=...`. A barcode
already on the phone is found locally and sends nothing.

### User-Agent

Every Open Food Facts request sends `User-Agent: Intake/<version> (personal Android app)`.
It never contains an email address. No key or account is sent by search or lookup.

### Contributing (opt-in)

Eligible: a food with a valid barcode (8 to 14 digits) that the user made or edited
(source `MANUAL` or `BARCODE`, not a lookup or search result left untouched, not a bundled
USDA food).

1. Food database & credits: turn on "Allow contributing" and enter your **own** Open Food
   Facts username (not email) and password. They are stored in `EncryptedSharedPreferences`
   with the other keys and never logged.
2. On the food's detail screen, "Contribute to Open Food Facts" opens a dialog listing
   exactly what will be sent. Nothing is sent without pressing Send, each time.
3. The request is the documented `POST https://world.openfoodfacts.org/cgi/product_jqm2.pl`
   form API (`https://openfoodfacts.github.io/openfoodfacts-server/api/tutorial-off-api/`).
   `user_id` and `password` are form fields **in the POST body over HTTPS**. The URL has no
   query string, and a unit test sends the real request through a stub transport to prove the
   password is not in the URL or headers. The newer v3 API was not used because the v2
   `product_jqm2.pl` form is the one the current tutorial documents.
4. Sent: `code`, `product_name`, `brands`, `quantity` (typed in the dialog), `nutrition_data_per=100g`,
   `nutriment_energy-kcal`, `nutriment_proteins`, `nutriment_carbohydrates`, `nutriment_fat`,
   and, when above zero, `nutriment_saturated-fat`, `nutriment_sugars`, `nutriment_fiber`,
   `nutriment_sodium` (grams), plus `ingredients_text` when entered, `app_name`, `app_version`
   and a short `comment`. No photos.
5. A success (`status: 1`) stamps `food.offContributedAt` (UTC millis).

Your contribution is public under the ODbL licence.

## Data model change

`food.offContributedAt INTEGER` (nullable), database version 2 to 3 (`MIGRATION_2_3`,
one `ALTER TABLE ... ADD COLUMN`). New `FoodSource` values `DATABASE` and
`OPEN_FOOD_FACTS` are stored by name in the existing text column.
