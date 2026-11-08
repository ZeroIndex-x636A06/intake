# Model benchmark

Amendment §1 asks for the cheapest model that still does each AI task
correctly. Only a measurement can say "correctly", so the app carries a small
benchmark you run yourself, on your own OpenRouter key, from
Settings → AI → Run benchmark. Nothing is measured until you run it, and until
then every suggestion is labelled "not benchmarked".

## What is measured

Each text task has five hand-written fixtures in
`app/src/main/assets/benchmark/` (made-up restaurants and recipes; no personal
data). The requests use the same system prompts and response schemas as the
real features, with the fixture text standing in for what the feature would
have fetched. Web search and fetch are left out, so a run is repeatable and
bills no per-search fees.

| Task | Fixture | Scored on |
| --- | --- | --- |
| Menu import | Menu text with published macros and ingredients | Items found, kcal within 15%, protein within 20%, dairy items flagged |
| Recipe Hunter | A recipe page (one publishes only whole-recipe nutrition) | Per-serving kcal and protein, dairy flagged |
| Plan B | A menu plus remaining kcal and protein | Every offered option is real, fits the budget, matches its macros, and dairy is flagged |
| Daily insight | A numbers-only digest | Under 240 characters, a known focus, evidence listed, cites a number from the digest, no dairy food |
| Photo estimates | none | **Not benchmarked.** It needs photos with known answers and none are bundled; the table keeps saying so. |

Each (task, model) row stores:

- **Accuracy**: the mean of the five fixture scores, 0 to 100%.
- **JSON**: the share of replies that decoded against the schema on the first
  try, with no retry (the app's normal path retries once, so this is stricter).
- **Speed**: median request time.
- **Cost**: mean cost per request, from the `usage.cost` OpenRouter returns.

### The allergen rule

A model may raise an allergen warning; it may never clear one. If an item that
contains dairy comes back without dairy among the allergens the model raised,
that whole fixture scores zero, whatever else it got right. Raising dairy on a
clean item costs nothing, because over-warning is the safe direction. This
measures whether a model is a safe second opinion; it changes nothing about
how the app decides. Allergen decisions stay with the local engine, which
reads the ingredients itself and which no model reply can override.

A model needs at least 5 fixtures, 85% accuracy and 95% JSON validity to be
suggested as "benchmarked" (`ModelSuggester`). Because a dairy miss scores a
fixture at zero, a model that misses dairy even once in five cannot reach
85%.

## What it costs

Each model is sent five requests per task. Before you confirm, the screen shows
a ceiling on the cost, computed from the live catalog prices: the fixture's
prompt, schema and text at three characters a token (English is nearer four,
so this rounds up) plus the whole reply budget (3,000 tokens, 1,000 for the
daily insight) as if every reply used all of it. Real replies are far shorter,
so actual spend is normally a fraction of the figure. Free models cost nothing.

By default the candidates for a task are the model the app would suggest
plus the three cheapest others able to do it, so a four-task run is 80
requests, typically cents. Models that may keep your prompts (free ones, or
those whose listing says so) are left out unless you have allowed them in the
picker settings.

You set a hard **spend cap** (default $0.25 per run). The runner checks spend
before every request and stops as soon as it reaches the cap. Where a
provider does not report a cost for a request, that request is charged at its
estimate instead, so a missing figure cannot get past the cap.

## How to run it

1. Settings → AI: save your OpenRouter key. Without one the section is
   disabled and the rest of the app is unaffected.
2. Pick the tasks, check the models listed and the spend cap, and read the
   estimated cost. If the estimate is above the cap, the run will stop early.
3. Tap **Run benchmark** and confirm. Progress shows the model, fixtures done
   and spend so far. **Cancel** stops at once.

Requests run one at a time, so measured speed is not skewed by the run itself,
and they use your privacy setting (for "No data collection", OpenRouter's
`data_collection: deny`). A model that has no provider meeting that setting is
skipped, not failed.

A model's row is stored only when all its fixtures ran and OpenRouter reported
a cost. A model cut off by the cap, cancelled, or failing partway is listed as
"Not saved", so the picker never mistakes a partial result for a pass. Rows
that did finish are kept even if you cancel. Rerunning a (task, model) replaces
its row.

## What changes afterwards

Stored rows feed `ModelSuggester`. The results table fills in, and for any
task with a model that passed, the picker's suggestion switches from "not
benchmarked" to "Cheapest model that passed the benchmark for this task".
Tasks with no stored row still show "not benchmarked".

## Your key

The key is read from encrypted storage, passed to the OpenRouter client for
the run, and placed only in the `Authorization` header. It is never logged,
never in a URL or request body, and never in an error message: failures are
shown from fixed text, not from anything a server or exception says.
