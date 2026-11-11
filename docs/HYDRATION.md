# Hydration

How the daily water target and the day's total are worked out. The pure maths is in
`app/src/main/java/com/zeroindex/intake/domain/hydration/` and is unit-tested; the screens only show it.

The honest summary: **tea and coffee count toward your water like water does.** At normal
amounts they hydrate about as well as water. Caffeine has a small, short-lived diuretic effect, and
mostly at higher single doses. Alcohol, at higher strengths, is net dehydrating. The app says exactly
that and nothing scarier.

## The day's total: volume x factor

Each logged drink has a type and an "adds to hydration" factor, frozen on the row when it is logged
(`hydration_log.hydrationFactor`), so changing a factor later does not rewrite history. The total is the
sum of `volumeMl x factor`, **never below zero** (a few shots cannot make the day negative). The raw
volume is still stored, and the sheet shows both: "Poured 2.3 L, counts as 2.4 L".

| Drink | Factor | Caffeine per 250 ml | Strength | Notes |
| --- | --- | --- | --- | --- |
| Water, sparkling water | 1.0 | 0 | 0 | |
| Tea | 1.0 | 47 (black tea; green is nearer 28) | 0 | Counts in full |
| Coffee | 1.0 | 95 | 0 | Counts in full |
| Milk | 1.5 | 0 | 0 | Study value was a little higher; rounded down |
| Juice | 1.1 | 0 | 0 | Study value was higher; rounded down |
| Soda | 1.0 | 24 (cola) | 0 | |
| Sports drink | 1.1 | 0 | 0 | |
| Oral rehydration solution | 1.5 | 0 | 0 | |
| Beer | 0.9 (1.0 at or under 3.5% ABV) | 0 | 5% | |
| Wine | 0.7 | 0 | 12% | Our estimate, see below |
| Spirits | `-ABV` (-0.4 at 40%) | 0 | 40% | Minus the pure alcohol, see below |
| Custom | 1.0 | 0 | 0 | User sets all three |

Every number is editable in the water sheet, Advanced, "Drink values" (blank means default). An energy
drink is a Custom drink at about 80 mg per 250 ml; green tea is Tea with 28.

**Spirits.** The factor is `-ABV`, so `volume x factor = minus the millilitres of pure alcohol`. A 44 ml
shot at 40% is -17.6 ml. The water in the spirit is deliberately not counted. The day's total is floored
at zero.

### Where the numbers come from

- **Beverage hydration index.** Maughan RJ, Watson P, Cordery PA, et al. "A randomized trial to assess the
  potential of different beverages to affect hydration status: development of a beverage hydration
  index." *Am J Clin Nutr* 2016;103(3):717-723. After a 1 litre drink in euhydrated men, oral
  rehydration solution, skimmed and full-fat milk, and orange juice held on to more fluid than still water;
  tea, coffee, cola, diet cola, a sports drink and lager did not differ meaningfully from water. Our
  milk, ORS and juice factors are rounded **down** from the study's values on purpose, and the study
  was one dose in healthy young men at rest, so they are conservative estimates, not measurements of you.
- **Coffee.** Killer SC, Blannin AK, Jeukendrup AE. "No evidence of dehydration with moderate daily
  coffee intake: a counterbalanced cross-over study in a free-living population." *PLoS ONE* 2014;9(1):e84154.
  Four cups (about 800 ml) a day for three days produced no differences in hydration markers compared
  with water.
- **Wine and spirits.** Not in the beverage hydration index. Alcohol suppresses the hormone that
  holds back urine, so higher-strength drinks pass more fluid than they supply (Shirreffs SM, Maughan RJ,
  *Am J Physiol* 1997 and *Alcohol Clin Exp Res* 1998 on rehydration after exercise with alcohol). The
  wine and spirit numbers are our own rough estimates of that, they are not from a table, and you can change them.
- **Standard drink.** 14 g of pure alcohol (the US standard). Pure alcohol is 0.789 g/ml, so one standard
  drink is about 17.7 ml: roughly 355 ml of 5% beer, 148 ml of 12% wine or 44 ml of 40% spirits.
  `standard drinks = volume x ABV x 0.789 / 14`.

## Caffeine

Each drink's caffeine is `mg per 250 ml x volume / 250`, summed for the day.

- **From 300 mg a day** the target gets a small top-up: +100 ml up to 400 mg, +200 ml above 400 mg. The
  line's reason reads "Large amounts of caffeine can increase urine output a little", and says tea and
  coffee still count.
- **Over 400 mg a day** (the FDA's general guidance for healthy adults) a gentle note shows. It is a
  suggestion to ease off, not an alarm.
- **A single drink over 200 mg** gets its own note: the acute effect is a high-single-dose one.

## Alcohol

Standard drinks add **200 ml of water per standard drink to the target**, up to 1 litre, with the plain
reason "Alcohol makes you pass more urine, so each standard drink adds a glass of water to aim for."
Alcohol is not written to Health Connect as hydration.

## Weather (opt-in)

Settings, Water, "Adjust water for weather". Off by default. With no location, or no network, there is
**no weather line**, and the card and sheet say so.

Bands, on the day's forecast high (`WeatherAdjustment`):

| High | Adds |
| --- | --- |
| under 25 C | 0 |
| 25 to under 30 C | +250 ml |
| 30 to 35 C | +500 ml |
| over 35 C | +750 ml |
| above 27 C and mean humidity 70% or more | +250 ml more |

The total is capped at **+1 litre**. It shows as a line such as "+500 ml · 33 C today".

**Location, one of two ways.**

- **Approximate location.** Coarse permission only. One fix at most once a day, only while the app is
  open and Today loads; rounded to 1 decimal (about 10 km) the moment it is read.
- **City or ZIP.** Geocoded once through Open-Meteo; only the rounded coordinates are kept.

**Requests** (no key; free for non-commercial use; data CC BY 4.0, credited in the app):

```
GET https://geocoding-api.open-meteo.com/v1/search?name=<city or ZIP>&count=5&language=en&format=json
GET https://api.open-meteo.com/v1/forecast?latitude=<1 dp>&longitude=<1 dp>&daily=temperature_2m_max,relative_humidity_2m_mean&timezone=auto&forecast_days=1
```

The forecast is cached for the day, fetched when Today opens if the cache is not from today (no
WorkManager job, no background location), and retried no sooner than 30 minutes after a failure. It fails
quietly. See [PRIVACY.md](PRIVACY.md).

## Sweat from workouts

A logged workout of 5 minutes or more adds `minutes / 60 x rate` litres. The rate comes from the
session's average effort (RPE of its working sets that were rated):

| Average RPE | Rate |
| --- | --- |
| under 6 | light, 0.5 L/h |
| 6 to under 8 | moderate, 0.8 L/h |
| 8 and up | hard, 1.2 L/h |
| not rated | moderate, 0.8 L/h |

This **replaces** the flat training-day amount (default 500 ml). The flat amount remains for a training
day with no timed workout yet. A day's workout water is capped at 2 L, a session left open counts at
most 4 hours, and measured active calories (1 ml per kcal, capped at 1 L) only add what is beyond the
workout already counted, so one workout is not counted twice. The rates are mid-range figures for mild
weather; hot weather is the separate weather line.

**Measure my sweat rate** (water sheet, Advanced). Weigh yourself before and after one session, with
the same clothes, dry; note what you drank during it and how long it took:

```
litres per hour = (weight before - weight after + fluid drunk) / hours
```

A kilogram lost is about a litre. The sheet shows the working, for example `(80.0 kg - 79.2 kg) + 0.50 L
drunk = 1.30 L over 1.00 h = 1.30 L/h`. Results outside 0.1 to 3 L/h, or from a test under 10 minutes, are
rejected. "Use my measured rate" replaces the light, moderate and hard defaults.

## The target

```
target = base + workout sweat (or flat training-day amount) + other activity + weather + caffeine + alcohol
```

- **Base.** The number you set, else 35 ml per kg of your latest weight (the middle of the commonly cited
  30 to 40 ml/kg range), else 2,500 ml until you log a weight. Rounded to 50 ml.
- **Adjust the target for the day** switched off: the target is the base alone.
- Each line has a short reason and an (i) in the water sheet.

`HydrationTarget.compute` keeps its original signature; `HydrationPlan.compose` is the fuller function
the repository uses.

## Data

Schema v3 (`migrateHydration_2_3`): `hydration_log` gains `drinkType` (default `WATER`), `hydrationFactor`
(1.0), `caffeineMg` (0) and `alcoholMl` (0); `hydration_preset` gains `drinkType`. Existing rows become
plain water, so every old total is unchanged, and v2 backup and sync rows without the new keys decode
to the same thing. User settings (weather, drink edits, measured sweat) live in their own DataStore,
`intake_hydration` (`HydrationPrefs`). Volumes are millilitres and times UTC epoch millis. Nothing is
hard-deleted.

## Drinks are foods (Nutrition > Drinks)

A drink is an ordinary `food` row with `drinkCategory` set (Water, Coffee, Tea, Soda, Juice, Sports & energy, Milk &
alternatives, Alcohol, Other; added in `migrateDrinks_2_3`). Its nutrition columns are per 100 ml, and it may carry
caffeine per 100 ml, strength (ABV) and its own hydration factor. About twenty generic drinks are seeded with stable ids
(`DrinkSeed`); a seeded drink the person removes is never brought back. USDA and Open Food Facts beverages are
categorised by fixed rules (`DrinkCategorizer`, no AI).

**One flow.** Every way of logging a drink (the Drinks page, a result in Log food, Food detail, Today's water card and its
quick sizes) goes through `DrinkLogger`. One log writes the hydration row (volume x factor, caffeine, alcohol) and, when
the drink has calories, macros or sodium, a food-log row, so sugar and calories count in the day. Plain water writes the
hydration row only. The hydration row keeps the food-log row's id; deleting either soft-deletes both and Undo restores
both. Factor precedence: the person's own edit (Settings > Water and drinks > Advanced), then the drink's own number,
then the type's default (so seeded oat milk counts 1.0 where milk counts 1.5).

**The Drinks page, top to bottom.** Today's total; the category chips; the chosen category's drinks (the seeded
ones and any the person added); the quick sizes for the selected drink; then **a search**, directly under those
predefined options; then what was logged today.

**The drinks search.** It searches what Log food searches (the person's own drinks, the offline USDA database, Open
Food Facts when online and switched on, and the USDA API on request when a key is saved) and lists **only drinks**.
Log food lists only foods. Both use the one rule in `DrinkRule` (docs/FOOD-DATA.md, "Foods and drinks are separate
searches"): the source's category first (USDA "Beverages", Open Food Facts `en:beverages` and the specific drink
tags), the name rules otherwise; soups, broths, oils and sauces are never drinks, milk and plant milks are. Rows read
**per 100 ml**, because a drink's nutrition columns are per 100 ml (1 ml counted as 1 g), whatever the source calls it.

Tapping a result from USDA or Open Food Facts copies it to the phone with its drink category set
(`FoodRefRepository.copyDrinkToMyFoods`, `OffSearchRepository.adopt`), selects it and shows the quick sizes under the
search field. A size then logs it through `DrinkLogger` like any other drink, so it counts for water and for calories,
and it is listed under its category from then on. Nothing is saved for results that are not tapped (the USDA API is
the exception, as in Log food: it saves what it returns). Log food's "Log a drink" row opens this page with the
words typed there already in the search.

**Allergens.** Every drink goes through the local allergen engine before it is written (the red sheet on a match), and
each row on the Drinks page, search results included, shows its verdict. The quick sizes, the target and the drink values are Settings, not part
of the Drinks page.
