# AI policy, usage meter and costs

What AI the app uses, when it is allowed to run, what it costs, and how the app keeps that
under control. Source of truth for the rules is `SPEC-v3.md` ("AI policy and cost"); this file
is the audit and the arithmetic. The code is in `data/ai/`, `domain/ai/` and `domain/ai/cost/`.

The three promises:

1. **AI is never used for something that does not need it.** Targets, water, programs,
   allergens, barcode and database lookups, script parsing and structured recipes are plain
   rules. A test fails if any of those areas starts calling the AI gateway.
2. **AI runs only when you ask, or on a schedule you switched on.** Every call carries a
   reason (`AiTrigger`): `USER_ACTION` or `OPT_IN_SCHEDULE`. Nothing else can reach a model.
3. **The app works fully without AI.** No key, the master switch off, or the budget used up:
   every AI button hides or says why, and nothing else changes.

## The guard (one place, `AiGateway.authorize`)

Every model call goes through `AiGateway`. Before a request is built, the gateway checks, in
this order, and refuses (sending nothing) at the first that fails:

| Check | Refusal |
| --- | --- |
| The call names an `AiTrigger` | Debug build: `IllegalStateException`, so a forgotten trigger is found at once. Release build: `AiError.NotAllowed`, nothing sent |
| The trigger is allowed for that feature (`AiFeature.allowedTriggers`). Only menu import, daily insight and coach reword may be scheduled | same as above |
| **Use AI features** (master switch) is on | `AiError.Off` |
| For `OPT_IN_SCHEDULE`: the feature's own switch is on (daily insight, coach reword, weekly menu refresh) | `AiError.NotAllowed` |
| An OpenRouter key exists | `AiError.NoKey` |
| This calendar month's spend is below the budget cap | `AiError.BudgetReached` |

The check runs again before the retry request of a reply that did not parse, so a retry
cannot go over the cap. The rule itself is the pure function `domain/ai/AiGuard.kt`; the
gateway tests (`AiGatewayTest`) count the requests that reach a fake OpenRouter to prove
"refused" means "nothing sent".

Screens use `AiGateway.isConfigured()` (switch on and key present) to decide whether to
offer a feature; it is a hint for hiding buttons, never the gate itself.

## Audit: every OpenRouter call site

Checked against the code on this branch. `AiAuditTest` scans the source and fails if a file
calls the gateway that is not in this table, if a call has no `AiFeature` and trigger, if
`OpenRouterClient` is used anywhere but the gateway, the model catalog and dependency
injection, or if a "never AI" area (domain, repositories, remote lookups, training, plan,
sync, backup, script, allergen and goals screens) references the gateway. To add a feature:
give it an `AiFeature`, route it through `AiGateway`, add it to `KNOWN` in that test and add a
row here.

| # | Call site (file) | Feature | Trigger | Deterministic path first? | What is sent | Model task | Gated |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `data/ai/vision/VisionTracker.kt` | `PHOTO_ESTIMATE` | **User action**: take or pick a photo and ask for an estimate | None can do this job (a photo of a plate has no barcode or label). Barcode scan, the offline USDA database, Open Food Facts search and manual entry are separate paths the person picks instead | The resized JPEG, "Estimate this meal.", names of avoided allergens in the system prompt | Photo estimates (vision) | Guard; entry hidden when AI is off or no key; allergens still decided locally |
| 2 | `data/ai/recipes/RecipeHunter.kt` | `RECIPE_HUNTER` | **User action**: Find recipes. Up to 3 rounds, each guarded | Recipe import from a link reads the page's own data with no AI. Every result is filtered locally (allergens, macros, time) | Macro, time and cuisine limits, notes, avoided allergens, URLs already found. Web search and fetch run on OpenRouter's side | Recipe Hunter (tools) | Guard; entry hidden when AI is off |
| 3 | `data/ai/planb/PlanBFinder.kt` | `PLAN_B` | **User action**: search for a place | Saved eat-out options and cleared menu items are ranked on the phone first, with no network (`offlineOptions`) | Location rounded to about 1 km (or a typed zip), calories and protein left today, avoided allergens | Plan B finder (tools) | Guard; the search entry is hidden when AI is off, the offline list stays |
| 4 | `data/ai/menu/MenuIngestor.kt` via `MenuSourcesViewModel` | `MENU_IMPORT` | **User action**: Import or Refresh on a source | A page that publishes schema.org `MenuItem` data is read directly (`domain/ai/MenuStructured.kt`) with **no** model call. Only pasted text, PDFs and pages without that data go to the model | Pasted text, PDF bytes, or the page text the phone fetched, restaurant name, avoided allergens | Menu import | Guard; entry hidden when AI is off |
| 5 | `data/ai/menu/MenuIngestor.runScheduled` via `work/MenuIngestionWorker.kt` | `MENU_IMPORT` | **Opt-in schedule**: weekly, only after "Refresh saved menus weekly" is switched on. **Off by default** | Same as row 4: structured pages refresh with no model call | Same as row 4, for each enabled URL or PDF source | Menu import | Guard (`OPT_IN_SCHEDULE`, `menuAutoRefresh`); the job is not registered until the switch is on and does nothing if it is off |
| 6 | `data/ai/insight/DailyInsights.kt` via `TodayViewModel` | `DAILY_INSIGHT` | **Opt-in schedule**: once a day when Today opens, only if "Daily insight" is on. **Off by default** | The digest is computed locally (`InsightDigest`); the model only words one suggestion. The coach engine's check-in is rules, no AI | Numbers-only digest of two weeks, avoided allergens | Daily insight | Guard (`OPT_IN_SCHEDULE`, `dailyInsightEnabled`) |
| 7 | `data/coach/CoachReworder.kt` via `CoachWorker` and `CoachCheckInViewModel` | `COACH_REWORD` | **Opt-in schedule** (after the weekly check-in) or **user action** (Check in now, or switching the reword on). **Off by default** | Every suggestion, number and action comes from `CoachEngine` rules; a reword is accepted only if the numbers are unchanged and the allergen engine allows it | Each suggestion's title and one-line reason | Daily insight model | Guard (`coachAiReword`); the switch is disabled when AI is off |
| 8 | `data/recipe/RecipeAiExtractor.kt` via `RecipeImportScreen` | `RECIPE_TRY_AI` | **User action**: the "Try with AI" button | JSON-LD, then microdata, then page text are read first (`RecipePageParser`); the button appears only when none found a recipe | Up to 20,000 characters of the page text and its address | Recipe Hunter model, no search | Guard; the button is hidden when AI is off or there is no key |
| 10 | `data/ai/video/VideoWorkoutAi.kt` via `MakeWorkoutViewModel` | `VIDEO_TO_WORKOUT` | **User action**: the "Improve with AI" button on Make this a workout | Chapters and description lines are read on the phone first (`VideoWorkoutDrafter`) and matched to the exercise library; the button is an optional second step the person taps after seeing that draft, or when nothing was found | The video's title, length, chapters and a trimmed description (up to 4,000 characters), no personal data | Daily insight model (text) | Guard; the button is hidden when AI is off or there is no key. The reply must fit a strict schema (one repair attempt); it only fills the review list, nothing is saved unseen |
| 9 | `data/ai/benchmark/BenchmarkRunner.kt` | `BENCHMARK` | **User action**: Run benchmark, after the cost ceiling is shown and confirmed | n/a (it measures models) | The bundled fixtures, no personal data | per task | Guard (switch, budget); also has its own spend cap |

### Which model, and what happens when it has no provider

- **The suggestion is privacy-checked.** `ModelSuggester.suggest` takes the privacy setting and the endpoint data
  (`domain/ai/ModelFit.kt`): a model with no healthy provider that meets the setting and accepts the parameters the task
  sends is not suggested (and shows as unavailable in the picker). The request always sets `require_parameters`, so the
  parameters count: a strict schema or JSON mode, `max_tokens`, `tools` for the web-search tasks, and `temperature` only
  for a model that lists it. A request no longer sends `temperature` to a model whose providers do not list it (Claude
  Haiku 5.5 and GPT-6 Luna list none); that mismatch is what made every task on those models fail with the privacy
  message while the photo task, on a model that lists it, worked.
- **Gateway fallback** (`AiGateway.sendWithFallback`): on a "no provider" answer (privacy or parameters) the gateway
  tries the next models from `AiEnvironment.fallbackModels` (the suggestion, then the other shortlisted models, then any
  other capable model, all provider-compatible), at most **3 models in all**, with the same privacy setting. Each attempt
  is its own row in the usage log (failure kind "Privacy setting"). Any other error is reported at once. If all fail, the
  message names the app's "Privacy for AI requests" and the OpenRouter account's privacy page. The benchmark does not
  fall back: it measures one named model.
- A call that fails over to another model can cost differently from the model the person chose; the model that actually
  answered is what the usage log and the cost line record.

### Photo estimate: questions and the follow-up

When the photo model is not confident it returns up to three short questions (2 to 4 choices each). They show as chips
above the estimate. Pressing "Update the estimate with my answers" sends **one** follow-up request (same feature, same
user action, same guard and budget check, no photo: the first look, the answers and the note) and replaces the numbers.
The cost line shows both calls summed. No questions are shown when the model says it is confident. A packaged product
the model names offers "Find this product instead" (Log food search with the name filled in). Hidden-calorie sources carry
a basis (seen in the photo, your note, your answer, or a guess); a guess is not shown as counted, and the prompt forbids
adding butter, milk or oil by default. Allergen warnings raised by the first look are carried into the follow-up and can
only be added to (`PhotoFollowUp.carryAllergens`).

Two OpenRouter requests are not model calls and never send anything about you:

| Call | Trigger | Notes |
| --- | --- | --- |
| Model list, `GET /models` (`ModelCatalogRepository`) | Opening Settings → AI, or just before a model call when the saved list is over 24 hours old | Public, no key. **Not requested when "Use AI features" is off.** |
| Key check, `GET /key` (`AiGateway.testKey`) | You press Test connection, or save a key | The key goes in the Authorization header only |

Outcome of the audit, violations found and fixed:

- **The weekly menu job ran for everyone.** It was registered at every launch and relied on a
  key to do nothing. It is now registered only when you switch on "Refresh saved menus weekly",
  cancelled when you switch it off, and a job left over from an older install returns at once
  while the switch is off. When it does run, structured pages need no model.
- **No master switch, no budget, no meter.** Added (below). Previously the benchmark called
  the HTTP client directly, outside any guard; it now goes through the gateway.
- **Menu pages with structured data went to the model anyway.** They are now read directly.
- **Router entries with a negative price** (`openrouter/auto` lists -1,000,000 $/M, meaning
  "varies") sorted as the cheapest model and would have produced negative costs. They are now
  never suggested and never priced (`ModelInfo.hasKnownPrice`).
- Confirmed already correct: daily insight and coach reword are off by default; recipe import
  reads JSON-LD first and AI only on tap; nothing for targets, water, programs, allergens,
  lookups, scripts or structured recipes calls a model.

PDF menus: reading text out of a PDF needs a PDF library, and the project takes no new
dependencies, so a PDF still goes to the model (with OpenRouter's text extraction). This is the
one place the "deterministic first" rule is not met; it is a follow-up if a PDF text extractor
is ever added.

## Master switch and budget (Settings → AI)

- **Use AI features** (default on, because saving a key was already the opt-in; it is the kill
  switch). Off: no AI entry point is offered, nothing is sent, the model list is not fetched,
  the three scheduled switches are disabled.
- **Monthly budget cap** in USD, default **$2.00**, editable. Spend is this calendar month's
  total in the usage log (your time zone). At **80%** a notice shows. At **100%** calls are
  refused with a clear message until the 1st of next month or until you raise the cap.
- **Resetting the counters never lowers the budget total.** It only moves the meter's start
  line, so it cannot be used to get around the cap.
- The cap is checked on what has already been spent, so the call that crosses the line
  completes; the next one is refused. A single call can therefore overshoot by up to its own
  cost (cents for the largest feature).

What a call is charged: the cost OpenRouter reports for it (`usage.cost`, requested with
`usage.include`). If it reports none, the catalog price times the tokens used (or the feature's
typical size if tokens are missing too), plus per-image, per-request and per-search fees. A
model with no known price and no reported cost counts as $0 and the row is marked estimated.

## Usage meter (Settings → AI)

Every request that leaves the phone is logged in the `ai_usage` table: feature, model, trigger,
prompt and completion tokens, cost, whether the cost was reported or estimated, success or
failure, and the time. A reply that did not parse is logged as a failed call but still costs
what it cost, and a retry is its own row. Never stored: the prompt, the reply, the key.

Settings → AI shows, per feature, calls and dollars for today, this week (from Monday) and this
month, a total, and the month against the budget as a progress bar. **Reset counters** is a soft
reset: it stores a timestamp and the meter counts from there; no row is deleted.

**Schema.** `ai_usage` is added in v3 as `migrateAiUsage_2_3()` (appended to
`MIGRATION_2_3_STATEMENTS`); v2 is untouched and there is no v4. Columns: `id`, `feature`,
`model`, `triggerKind`, `promptTokens`, `completionTokens`, `costUsd`, `costReported`,
`success`, `at`, `createdAt`, `updatedAt`, `deletedAt` (indexes on `at`, `feature`,
`deletedAt`). `MigrationSchemaTest` upgrades the released v2 and checks the result against
`3.json`. **It does not sync and is not in backups** (it is not in `BackupTables.kt`, and a test
checks that): usage is per device, and merging it would double count a bill. The master switch,
the cap, the uses-a-day numbers and the menu-refresh switch are settings and do travel in the
settings backup; the reset marker is per device and does not.

## Cost estimate (Settings → AI → Estimated cost)

For each feature the screen shows the model in use (your pick for that task, else the
suggestion), the average tokens per call and where that number came from, the cost per call, per
day and per month, and an editable **uses a day**. The arithmetic is `domain/ai/cost/AiCostModel.kt`:

```
per call  = input tokens  x prompt price / 1,000,000
          + output tokens x completion price / 1,000,000
          + per-request fee
          + images   x per-image fee
          + searches x search fee               (0.007 USD each, built in)
per day   = uses a day x per call               (0 if the feature is switched off)
per month = per day x 30
```

Amounts are rounded to a millionth of a dollar; the screen shows enough places to read small
amounts ($0.0004, not $0.00).

**Tokens per call**, best source first (the screen labels which one it used):

1. **Measured from your usage**: once a feature has 3 successful calls that reported tokens, the
   average prompt and completion tokens from the log replace the guess. A measured figure always
   beats a default.
2. **Benchmark cost**: if the model in use was benchmarked for that task, its measured cost per
   request is used (plus the search fee, because the benchmark sends plain requests).
3. **Built-in estimate**: the typical request size below. These are deliberately on the high side,
   and are the same sizes `ModelSuggester` prices with. The benchmark's own up-front ceiling
   (prompt characters / 3, plus the whole output budget) is the same idea for its own requests.

| Feature (`AiFeature`) | Input tokens | Output tokens | Extras | Default uses a day |
| --- | --- | --- | --- | --- |
| Photo estimates (`PHOTO_ESTIMATE`) | 1,800 | 600 | 1 image | **2** |
| Recipe Hunter (`RECIPE_HUNTER`) | 18,000 | 3,000 | 3 searches | **0.3** |
| Plan B finder (`PLAN_B`) | 14,000 | 2,000 | 3 searches | **0.2** |
| Menu import (`MENU_IMPORT`) | 40,000 | 8,000 | | **0.1** |
| Daily insight (`DAILY_INSIGHT`) | 4,000 | 400 | | **1**, but **0 while the switch is off** |
| Coach reword (`COACH_REWORD`) | 700 | 500 | | **1/7**, but **0 while the switch is off** |
| Recipe "Try with AI" (`RECIPE_TRY_AI`) | 6,700 | 1,500 | | **0.05** |
| Video to workout (`VIDEO_TO_WORKOUT`) | 2,500 | 800 | | **0.05** |

The benchmark is counted in the meter but is not part of the estimate (it is a one-off you start).

**Recommended model.** If benchmark results exist, the screen shows the cheapest model that
passed (`ModelSuggester`, 85% accuracy and 95% valid JSON on at least 5 fixtures). If not it says
"Run the benchmark to pick" and names no model: no vendor is preferred in code. Photo estimates
are not in the benchmark yet (it needs photos with known answers), so that row says so and uses
the cheapest capable model from the suggestion. Candidates are filtered by capability from the
catalog (vision for photos, tools and 100k context for the search tasks, 200k for menus),
and never include router entries with a variable price.

## Worked example (an estimate, not a measurement)

Prices from `https://openrouter.ai/api/v1/models` (public, no key), fetched **9 November 2026**.
**Prices change often; treat these as a snapshot.** No OpenRouter key was available when this
was written, so **no AI call was made**: every number below is arithmetic on the built-in typical
request sizes, not on anything a model returned. Real calls will differ, which is exactly what
the "measured from your usage" step corrects. The models are a few candidates for illustration,
not a recommendation; which one is best for you is what the benchmark decides.

| Model | prompt $/M | completion $/M | context |
| --- | --- | --- | --- |
| `anthropic/claude-haiku-5.5` | 0.100 | 0.500 | 1000k |
| `openai/gpt-6-luna` | 0.100 | 0.500 | 1050k |
| `deepseek/deepseek-v4.1-flash` | 0.051 | 0.567 | 1048k |
| `google/gemini-3.8-flash` (newest Gemini flash, what `google/gemini-*-flash` resolves to) | 0.825 | 4.125 | 1048k |
| `meta-llama/llama-3.3-70b-instruct` | 0.590 | 0.790 | 131k |

Cost of **one call** of each feature:

| Feature | request (in / out tokens) | `claude-haiku-5.5` | `gpt-6-luna` | `deepseek-v4.1-flash` | `gemini-3.8-flash` | `llama-3.3-70b-instruct` | cheapest capable paid model in the catalog |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PHOTO_ESTIMATE | 1,800 / 600, 1 image | $0.0005 | $0.0005 | $0.0004 | $0.0040 | cannot read images | `nex-agi/nex-n2.5-mini` $0.0001 |
| RECIPE_HUNTER | 18,000 / 3,000, 3 searches | $0.024 | $0.024 | $0.024 | $0.048 | $0.034 | `inclusionai/ling-3.0-tiny` $0.021 |
| PLAN_B | 14,000 / 2,000, 3 searches | $0.023 | $0.023 | $0.023 | $0.041 | $0.031 | `inclusionai/ling-3.0-tiny` $0.021 |
| MENU_IMPORT | 40,000 / 8,000 | $0.0080 | $0.0080 | $0.0066 | $0.066 | context too small (200k needed) | `inclusionai/ling-3.0-tiny` $0.0010 |
| DAILY_INSIGHT | 4,000 / 400 | $0.0006 | $0.0006 | $0.0004 | $0.0050 | $0.0027 | `inclusionai/ling-3.0-tiny` <$0.0001 |
| COACH_REWORD | 700 / 500 | $0.0003 | $0.0003 | $0.0003 | $0.0026 | $0.0008 | `inclusionai/ling-3.0-tiny` <$0.0001 |
| RECIPE_TRY_AI | 6,700 / 1,500 | $0.0014 | $0.0014 | $0.0012 | $0.012 | $0.0051 | `inclusionai/ling-3.0-tiny` $0.0002 |
| VIDEO_TO_WORKOUT | 2,500 / 800 | $0.0007 | $0.0007 | $0.0006 | $0.0060 | $0.0030 | `inclusionai/ling-3.0-tiny` <$0.0001 |

Two things stand out. For the two search features **the search fee is most of the bill**: three
searches at $0.007 are $0.021 of a $0.023 call, so a cheaper model barely helps. And a pricier
model costs several times more on the large menu and insight requests, which is where the
benchmark's accuracy result is worth weighing against price. The "cheapest capable" column is
only the cheapest by price: it is not tested, and the app would not suggest it without a
benchmark pass (and it filters models that may keep your prompts).

Daily and monthly totals at the default uses a day (month = 30 days):

| Scenario | Per day | Per month |
| --- | --- | --- |
| A. `claude-haiku-5.5`-priced model for everything, defaults (insight and coach reword off) | $0.014 | **$0.41** |
| B. Same, with daily insight and coach reword switched on | $0.014 | **$0.43** |
| C. Cheapest capable paid model per task, everything on | $0.011 | **$0.33** |
| D. Newest Gemini flash for everything, everything on | $0.043 | **$1.29** |

Breakdown of scenario A (per call x uses a day = per day): photo $0.0005 x 2 = $0.0010;
Recipe Hunter $0.024 x 0.3 = $0.0073; Plan B $0.023 x 0.2 = $0.0047; menu import $0.0080 x 0.1 =
$0.0008; "Try with AI" $0.0014 x 0.05 = $0.0001; insight and reword 0 because they are off. In
scenario B insight adds $0.0006 and reword $0.0000(4). All four scenarios sit well under the
default $2.00 monthly budget; scenario D, the priciest, uses about 65% of it at these defaults.
Using Recipe Hunter and Plan B ten times as often (3 and 2 a day) would move scenario A from
about $0.41 to about $4 a month, which the cap would stop at $2.00.

## Reliability, retries and the spend they cause (B1)

- **Retries count toward spend.** When a reply does not fit the schema the gateway sends one repair request in the
  same action ("Your previous reply did not match the schema: ... Reply with JSON only."). It goes through the
  same guard, so a spent budget stops it, and it is logged as its own row. The failed first attempt keeps its cost.
  The repair carries the model's earlier reply back as input (up to 12,000 characters) and never repeats a web search.
- **Transport retries are not extra rows.** A 429 or 5xx is retried inside one call, up to three tries, waiting for the
  provider's `Retry-After` (cap 30 s; a longer wait is reported as rate limited, not waited out) or 1 s then 2 s with
  jitter. A rate-limited request is not billed. A timeout or a dropped connection is never retried, because the
  provider may already have done and billed the work.
- **Timeouts per feature.** Photos, Recipe Hunter, Plan B and menu import: 120 s read, 150 s call. Recipe "Try with AI"
  and the benchmark: 90 s. Daily insight and coach reword: 45 s. An image, a PDF or a web tool raises a request to 120 s.
- **Why a call failed** is on every failed usage row (`ai_usage.failureKind`: timed out, unreadable answer, provider
  refused, rate limited, no connection, key rejected, out of credit, privacy setting, provider error, cancelled) and
  Settings -> AI lists this month's failures per feature. Schema: `migrateAiFailureKind_2_3()`.
- **Menu PDFs.** A PDF is read by OpenRouter's `file-parser` plugin with the free text engine (`cloudflare-ai`; the old
  name `pdf-text` is redirected to it), so the parsing has no per-page fee. The model still reads the parsed text, so
  its input tokens are billed. A PDF is cut into 1 to 2 pages per request (`PdfChunkPlanner`), so a 10-page
  nutrition table is five requests, each an ordinary small one, and each is logged and guarded. Scanned PDFs with no
  text layer would need the paid OCR engine ($2 per 1,000 pages), which the app does not use.
- **Benchmark.** Requests are paced one at a time with a 1 s gap. A 429 is waited out (5, 15, 45 s or `Retry-After`) and
  only then is that one model skipped. `:free` and zero-priced models are never candidates.

## What is not verified

- No OpenRouter key was available, so **no live call was made**. The gateway, guard, usage
  recording, budget, month rollover and cost model are tested against a fake HTTP layer;
  OpenRouter's real `usage.cost` and token fields are parsed by the existing client as before
  and covered by its own tests, but the end-to-end path has not been exercised on a device.
- The 0.007 USD search fee is a built-in assumption (`AiTask.WEB_SEARCH_USD`), not read from the
  catalog; if OpenRouter changes it, searches are mis-estimated until the constant is updated.
  Real spend uses the reported cost, so the budget is not affected.
- No emulator: none of the new Settings → AI cards (switch, budget, meter, cost screen) has been
  seen on a screen.
- Structured menu parsing is tested on hand-written JSON-LD, not on real restaurant pages.
