# Privacy audit

Every way data can leave the phone, checked against the code on this branch. The
in-app copy is `ui/screens/settings/PrivacyScreen.kt`; this file is the same list with
code references so each claim can be checked. Paths are under
`app/src/main/java/com/zeroindex/intake/` unless they start with `wear/`,
`wearprotocol/` or `server/`.

The stance (SPEC §13, SPEC-amendments): fully functional offline and without any
key; the user's own keys are stored encrypted; nothing is sent unless a feature
the user switched on needs it.

## How it was audited

A search of `app/`, `wear/` and `wearprotocol/` for `OkHttpClient`, `Retrofit`,
`newCall`, `HttpURLConnection`, `URL(`, `Wearable.`, `HealthConnectClient`,
`NfcAdapter`, `LocationManager`, `ACTION_VIEW`,
`PrintManager`, `CreateDocument`, `WorkManager`/`CoroutineWorker`, `AsyncImage`
and `Log.`, plus the manifest and the dependency list.

Findings that hold for every path:

- **One HTTP stack.** `data/di/NetworkModule.kt:24` provides the only
  `OkHttpClient`. USDA and Open Food Facts use Retrofit on it; OpenRouter, sync,
  menu page fetches and the YouTube key test use it directly. There is no `HttpURLConnection` or `URL(...).openStream()`.
- **No analytics, crash reporting or ads.** `app/build.gradle.kts` and
  `gradle/libs.versions.toml` have no Firebase, Crashlytics, Sentry, AdMob or
  analytics artifact. The only Google dependency is `play-services-wearable`
  (the Wear Data Layer). `wear/build.gradle.kts` is the same. Coil is
  `coil-compose` only, without a network artifact, so it cannot load a URL; it
  shows local photo files.
- **No logging of secrets or payloads.** All `Log.*` calls (`ApiKeyStore.kt`,
  `FoodSearchRepository.kt`, `BarcodeRepository.kt`, `BarcodeScannerView.kt`,
  `PhotoCaptureView.kt`, the workers, `PhoneWearListener.kt`) log a fixed message
  and an exception; none logs a key, a prompt, a request or a response. No
  `HttpLoggingInterceptor` exists.
- **Keys.** OpenRouter and USDA keys live in `EncryptedSharedPreferences`
  (`data/keys/ApiKeyStore.kt:47`, AES-256-GCM values, AES-256-SIV names). Sync
  secrets (the device token) live in `data/backup/remote/BackupCredentialStore.kt:44`. The
  OpenRouter key and the sync token go only in an `Authorization: Bearer` header
  (`OpenRouterClient.kt:59-72`, `SyncApi.kt:67`). The USDA key goes only in an
  `X-Api-Key` header (`UsdaService.kt:61`).
- **Permissions** (`app/src/main/AndroidManifest.xml`): `INTERNET`, `CAMERA`
  (photo and barcode, processed on device; ML Kit barcode scanning is the bundled
  model), `ACCESS_COARSE_LOCATION` (Swap: eat out nearby, and the opt-in weather adjustment), `NFC`, the Health Connect
  permissions listed below, notifications, vibrate, wake lock, foreground service.
  `allowBackup="false"` with data extraction rules, so Android cloud backup and
  device-to-device transfer are off. The watch app requests only `VIBRATE`; it has
  no `INTERNET`.

## Never leaves the phone

| Item | Why we can say so |
| --- | --- |
| Allergen decisions | Made by the local matcher (`AllergenRepository.scan`, `domain/allergen/`). `ModelAllergenPolicy` merges an AI claim in as a warning only; it cannot clear a local match. The names of avoided allergens are sent in some AI prompts (below) so the model can avoid them, but no verdict is sent. |
| Food log, weight, measurements, workouts, plan, templates, settings | Room database and DataStore in app-private storage. They leave only by the paths in this document. |
| Your own exercise photos | A photo you add to a custom exercise is scaled, stripped of EXIF and kept in the app-private `photos` folder; the exercise row holds only the file name (`exercise.photoPath`). The image is not in backups, exports or sync, and never sent anywhere. Bundled exercise photos are assets from the Free Exercise DB (public domain). The camera permission is asked only when you tap "Take a photo". |
| Photos | Stored as files in app storage. Rows hold a URI string only (`sourcePhotoUri`, `photoUri`), so backup and sync move the path, never the image. The one exception is the photo estimate, which sends a resized copy (below). |
| Progress photos | Taken with the camera or chosen from the gallery on You > Progress photos, scaled, stripped of EXIF and kept in the app-private `progress_photos` folder. The `progress_photo` table (date, angle, file name) is exported and synced as metadata only; the images are **never synced or uploaded** and go into a zip only if you switch on "Include progress photos in the export" and tap Save photo files. | 
| API keys | Encrypted at rest; sent only to their own host (OpenRouter key to openrouter.ai, USDA key to api.nal.usda.gov, YouTube key to googleapis.com, as the `key` parameter the YouTube Data API requires; see "YouTube" below). Not in exports, backups or sync (`BackupTables.kt` has no key table). |
| TDEE and trend math, charts, insights history | Computed locally (`work/TdeeWorker.kt` is local only). |
| Barcode scanning and photo capture | CameraX and ML Kit on device. |

## Network paths

"Default" is the state on a fresh install.

### Food lookups (the user's action)

| Feature | Trigger | Host | Sent | Not sent | Default | Code |
| --- | --- | --- | --- | --- | --- | --- |
| USDA FoodData Central search | Typing in Add food, or Test on the USDA key | `api.nal.usda.gov` `GET /fdc/v1/foods/search` | The query text, page size, data types, the user's USDA key in the `X-Api-Key` header | Anything about the user. The local table is searched first. | Off until a key is saved (`FoodSearchRepository` returns `MissingKey`) | `data/remote/usda/UsdaService.kt:59`, `data/repository/FoodSearchRepository.kt:31`, `ui/screens/settings/SettingsViewModel.kt:129` |
| Open Food Facts barcode | Scanning (or typing) a barcode not already in the local table | `world.openfoodfacts.org` `GET /api/v2/product/{barcode}.json?fields=...` | The barcode, a fixed field list, and the User-Agent `Intake/<version> (personal Android app)` | Everything else. No key, no account, no email. | On, but only on a scan with no local hit | `data/remote/openfoodfacts/OpenFoodFactsService.kt:45`, `data/repository/BarcodeRepository.kt:43` |
| Offline USDA food database | Typing in Add food | Nothing leaves the phone. Searched in the bundled `food_ref.db` | None | n/a | Always on, needs no key or network | `data/foodref/FoodRefRepository.kt`, `docs/FOOD-DATA.md` |
| Open Food Facts search | Typing in Add food (600 ms debounce, 3 or more characters) while online | `search.openfoodfacts.org` `GET /search?q=...&fields=...` | The query text, the **language codes** `langs=<your phone's language>,en` (and the matching `product_name_<code>` field names), and the User-Agent `Intake/<version> (personal Android app)` | No key, no account, nothing else about the user, no email | **On**; switch it off in Food > Food database & credits (`FoodDataPrefs.offSearch`) | `data/remote/openfoodfacts/OffSearchService.kt`, `data/repository/OffRepository.kt` |
| Open Food Facts product pictures | A search or barcode result that has a picture is shown in Add food or My foods, while online | `images.openfoodfacts.org` `GET /images/products/.../front_xx.NNN.jpg` over HTTPS only | Only the request for that picture, and the User-Agent. The address comes from Open Food Facts' own answer; nothing about the user is sent | Nothing else. Other hosts, `http://` addresses and anything over 400 KB are refused. Pictures are cached in the app's cache folder (never in a backup) and fetched again if cleared. Only the address is saved on a food, so My foods can show it | **On with Open Food Facts search**; with that switch off no picture is requested at all, and offline only cached pictures show. USDA and offline foods use a generic icon and make no request | `ui/images/ProductImages.kt`, `domain/food/ProductImagePolicy.kt` |
| Open Food Facts contribution | Only when the user presses Send in the confirmation dialog of "Contribute to Open Food Facts", one product at a time | `world.openfoodfacts.org` `POST /cgi/product_jqm2.pl` over HTTPS | The barcode, product name, brand, quantity, nutrition per 100 g, ingredient text if entered, and the user's **own** Open Food Facts username and password as form fields **in the POST body** (never the URL or headers, never logged) | Photos, the log, any other food. Nothing is ever sent automatically | **Off**; needs the switch on, saved account, and a per-product confirmation. The result is public under the ODbL licence | `data/remote/openfoodfacts/OffContribution.kt`, `data/repository/OffRepository.kt`, `ui/screens/fooddata/FoodDetailScreen.kt` |

### Weather for the water target (opt-in; `data/weather/`, `data/remote/openmeteo/OpenMeteo.kt`)

Off until the user turns on Settings, Adjust water for weather. No key, no account, no AI. Open-Meteo is free for
non-commercial use; its data is CC BY 4.0 and is credited in Settings and in Food, Food database & credits.

| Feature | Trigger | Host and request | Sent | Not sent | Default | Code |
| --- | --- | --- | --- | --- | --- | --- |
| City or ZIP lookup | The user types a place and presses Find | `geocoding-api.open-meteo.com` `GET /v1/search?name=<text>&count=5&language=en&format=json` | The text the user typed | Location, identity, the log | Off | `OpenMeteo.geocodingUrl`, `WeatherRepository.geocode` |
| Daily forecast | Today opens and the saved forecast is not from today (retry no sooner than 30 minutes after a failure) | `api.open-meteo.com` `GET /v1/forecast?latitude=<1 dp>&longitude=<1 dp>&daily=temperature_2m_max,relative_humidity_2m_mean&timezone=auto&forecast_days=1` | Coordinates rounded to one decimal (about 10 km). Rounded at the point they are produced (`CoarseCoordinates.round`), before they are stored or sent | Exact position, the log, any water or workout data | Off | `OpenMeteo.forecastUrl`, `WeatherRepository.refreshIfStale` |

- Approximate location uses the **coarse** permission only, takes one fix at most once a day while the app is open
  (`CoarseLocationProvider`), never in the background, and only if the user already granted it from the weather
  setting. A typed city or ZIP is geocoded once and only the rounded coordinates are kept.
- The forecast is cached for the day in app-private DataStore (`intake_hydration`). Offline, there is no request,
  no weather line, and the card says so.
- "Forget this location" deletes the saved coordinates, place name and cached forecast.
- The calls use the shared OkHttp client; nothing is logged.

### OpenRouter (the user's own key; `data/ai/OpenRouterClient.kt`)

Every AI call goes through `AiGateway` (`data/ai/AiGateway.kt`). Its guard refuses to
send anything unless the call names why it is happening (a tap, or a schedule the user
switched on), "Use AI features" is on, a key exists and this month's budget is not used
up; it also applies the privacy setting to the request. The full audit of call sites,
the master switch, the budget and the usage meter are in [AI-COSTS.md](AI-COSTS.md).
**Nothing new is sent by the usage meter, the budget or the cost screen**: they work only
from numbers already on the phone, and the usage log (`ai_usage`: feature, model, tokens,
cost, success, time; never a prompt, a reply or a key) stays on the device. It is not in
backups, exports or sync. The request body is built in
`OpenRouterClient.buildPayload` (line 94 on). `provider.data_collection = "deny"`
is set for the default `NO_TRAINING` and for `ZERO_RETENTION` (which adds
`zdr: true`); only the user choosing "Any provider" omits it (lines 137-145).
Default `aiPrivacy` is `NO_TRAINING` (`data/settings/SettingsRepository.kt:61`).
Every request also sends the header `X-Title: Intake`.

| Feature | Trigger | Sent | Not sent | Default | Code |
| --- | --- | --- | --- | --- | --- |
| Model list | Opening Settings, AI or any AI task, when the cached list is over 24 h old | `GET openrouter.ai/api/v1/models`, no key, no body | Anything of the user's. Public; works with no key. | On, **but not requested while "Use AI features" is off** | `data/ai/ModelCatalogRepository.kt:73,82`, `OpenRouterClient.kt:53` |
| Key check | Saving or testing the key | `GET /api/v1/key` with the key in `Authorization` | Nothing else | Only with a key | `AiSettingsViewModel.kt:105`, `AiGateway.kt:94` |
| Photo estimate | User takes or picks a photo and asks for an estimate | The resized JPEG (`PhotoPreparer`), "Estimate this meal.", and the names of avoided allergens in the system prompt (`VisionModels.kt:89`) | The allergen verdict, name, weight, other log entries | Only with a key; per photo | `data/ai/vision/VisionTracker.kt:57-70` |
| Recipe Hunter | User presses Find recipes | Macro, time and cuisine limits, free-text notes, avoided allergens, source URLs of recipes already found. Tools: `openrouter:web_search` and `openrouter:web_fetch`, run **by OpenRouter**, not the phone | Anything from the log or body | Only with a key; per search | `data/ai/recipes/RecipeHunter.kt:106-112,180-215` |
| Swap: eat out nearby (was Plan B) | User taps Search in the Swap sheet | Coordinates rounded to two decimals (about 1.1 km) from the coarse permission, or the typed zip code; kcal and protein left today; avoided allergens; the search radius from Settings. Same web tools, run by OpenRouter | Exact location, the log, names of foods eaten | Only with a key; location is read only on that search and only with the permission | `data/ai/planb/PlanBFinder.kt:139-148,229-247`, `domain/ai/PlanBFit.kt:56` |
| Menu import (manual) | User adds or refreshes a menu source | Pasted text (up to `MenuText.MAX_CHARS`), or the PDF bytes (also base64 in a `file` part), or text this phone fetched from the entered URL, or just the URL when OpenRouter should read it with `web_fetch`; restaurant name; avoided allergens | Anything else | Only with a key; per import | `data/ai/menu/MenuIngestor.kt:81-125` |
| Menu import (weekly) | WorkManager, every 7 days, unmetered network, battery not low. **Registered only when "Refresh saved menus weekly" is switched on** (Settings, AI), and cancelled when it is switched off | Same as above for each **enabled URL or PDF** source. Pasted-text sources are skipped. A page with structured menu data is read on the phone with no AI call | Same | **Off.** Does nothing unless that switch, "Use AI features" and a key are all on | `work/MenuIngestionWorker.kt`, `MenuIngestor.runScheduled`, `domain/ai/MenuStructured.kt` |
| Nearby restaurants (map) | Only when you tap Search on the nearby card (Eating out > Find healthier options nearby, or Swap > Eat out nearby). One request per search, never in the background | `overpass-api.de` `POST /api/interpreter` over HTTPS, OpenStreetMap's free Overpass service | The search area as **latitude and longitude rounded to about 1 km**, the radius (from your 10 or 15 minute walk or drive setting), the three amenity types (restaurant, fast food, cafe), and the User-Agent `Intake/1.0 (personal nutrition app; one map request per tap)`. Your location is read once, on that tap (approximate permission only), and is not stored | No key, no account, no allergens, no targets, no food log, no device id. The service sees the phone's IP address and the rounded place | **Off until you tap**. If it fails, nothing else is tried: the card offers the AI search, which also runs only on a tap | `data/nearby/OverpassClient.kt`, `domain/nearby/NearbyPlaces.kt` |
| Nearby menus (no AI) | After the map answers, for each restaurant in turn (two at a time, at most 30 per search), unless a menu for it is already saved on the phone | `GET` of the chain's own nutrition page from the bundled table `assets/nearby_chains.json`, then the restaurant's own website (the `website` tag from the map) and up to 3 links on it that mention nutrition, allergens, menu or a PDF. One level deep, one request each, HTTPS only | The request itself, with the User-Agent `Intake/1.0 (personal menu import; one request per menu per week)`. No cookies, no key, nothing about you. Those sites see the phone's IP address, as for any link you import | Nothing goes to Intake, Anthropic or OpenStreetMap. A site that refuses (403, 429) is skipped, never worked around. A menu that is found is saved as a menu source, so the next search reads it from the phone | **Only inside a search you tapped.** Saved menus make no request | `data/nearby/NearbyMenuSearch.kt`, `data/ai/menu/MenuFetcher.kt`, `domain/nearby/MenuDiscoveryRules.kt` |
| Nearby menus (AI) | Only if you switched on "Use AI to find menus I can't" on the search card for this search, and only for a restaurant where the steps above found nothing. At most 6 lookups per search | OpenRouter (your key), the same route as Plan B, with a web search | The restaurant name, its website address and cuisine from the map. **No location, no allergens, no targets.** The nutrition page it names is then imported by the normal menu import; an estimate, if no page exists, is labelled as an estimate | The model never clears an allergen: the local engine runs on everything and a model claim can only add a warning | **Off by default and not remembered between searches**; disabled without AI on and a key | `data/nearby/NearbyMenuSearch.kt` (`MENU_IMPORT`, see AI-COSTS.md row 11) |
| Direct page fetch for menus | Part of the two rows above, for URL sources | `GET <the URL the user entered>` from the phone, with `User-Agent: Intake/1.0 (...)` | No key, no user data. The destination is whatever site the user named. | Per import | `MenuIngestor.fetchText` line 193 |
| Daily insight | `TodayViewModel` calls `ensureToday()` on opening Today; at most one per day, and the gateway treats it as an opt-in schedule | The digest from `domain/ai/InsightDigest.kt`: averages, targets, weight trend, TDEE, body fat and lean mass, session counts, RPE trend, sleep, resting HR, steps, water. Plus avoided allergens in the system prompt (`DailyInsights.kt:180`). The digest is shown to the user in the card | Food names, notes, dates, places | **Off** (`dailyInsightEnabled = false`, `SettingsRepository.kt:75`); needs a key as well | `data/ai/insight/DailyInsights.kt:69-112` |
| Coach reword | The "Reword my check-in with AI" switch (Weekly check-in screen), then each new check-in | Only each suggestion's title and one-line reason (they contain your numbers, such as "120 g of protein; the target is 170 g"). No food names, dates, notes or raw data. Allergen names are not sent; replies are checked by the local allergen engine | The log, body data, profile, the rule's action | **Off**; needs a key as well. The coach itself (docs/COACH.md) never uses AI | `data/coach/CoachReworder.kt`, `domain/coach/CoachSupport.kt` |
| Benchmark | User starts it in Settings, AI, Run benchmark. Never automatic | The bundled fixture texts (menu, recipe, plan B and insight samples, no personal data) and the app's own system prompts, sent to OpenRouter with the same privacy settings as other AI calls (`data_collection: deny`). Needs the user's OpenRouter key | Anything from the log, body or profile | Only with a key, only when started; a user-set spend cap (default $0.25) stops the run. Results are stored locally in `model_benchmark` and are synced like other tables | `data/ai/benchmark/BenchmarkRunner.kt`, `docs/BENCHMARK.md`, `ModelCatalogRepository.benchmarks` |

#### Added by the AI reliability work (B1, B4, B7)

- **The "Anything we can't see?" note** on the photo screen (up to 300 characters, optional) is sent to OpenRouter in the
  same request as the photo. It is not stored beyond the screen and is not part of the usage log.
- **A photo estimate may be two calls.** When the model is not sure it returns up to three short questions; you tap
  answers and press "Update the estimate with my answers". That second request goes to OpenRouter with the same
  privacy setting and carries **no photo**: only the model's own first look (food names, grams, calories), your
  answers, and your "Anything we can't see?" note if you wrote one. It runs only when you press the button, and the
  cost line shows both calls.
- **"No provider meets your privacy setting" and the fallback.** If the chosen model has no provider that satisfies
  the privacy setting, the app tries up to two other models that do (same setting, never relaxed) and logs each
  attempt. If all fail, the message names both places that decide it: Settings, AI, "Privacy for AI requests", and your
  OpenRouter account's own privacy page (openrouter.ai/settings/privacy), which can block providers too.
- **A menu PDF is read on the phone first, with no AI.** On Android 15 or later the platform PDF reader's text is
  parsed on the phone (`NutritionTableParser`); if it finds a nutrition table, nothing is sent anywhere. Only when it
  finds none does the app offer the AI path below, and only a tap on that offer sends the PDF.
- **PDFs are sent to OpenRouter for parsing.** A menu PDF, from a link or a picked file, is sent as page-sized pieces in
  `file` parts with the `file-parser` plugin (free text engine). The privacy setting applies, but note that
  OpenRouter's own documentation says plugin processing is not covered by ZDR routing. A link is downloaded by the
  phone first (15 MB cap, honest User-Agent, cancellable). Allergen text in a PDF only ever raises a warning.
- **Endpoint lookups for the benchmark.** Choosing benchmark candidates fetches `GET /api/v1/endpoints/zdr` (cached a day)
  and `GET /api/v1/models/<id>/endpoints` for up to 8 models per task. Both are public, carry no key and no user
  data.
- **Zero-retention check.** OpenRouter publishes no per-endpoint "does not train" flag; the only public data policy is
  the zero-retention list. "No data collection" and "Zero retention" candidates are therefore limited to models with a
  zero-retention endpoint, which can hide a model that would in fact route. "Any provider" is not filtered.

### YouTube (Train > Videos; the user's own key; `data/remote/youtube/YoutubeApi.kt`)

| Feature | Trigger | Destination | Sent | Not sent | Default | Code |
| --- | --- | --- | --- | --- | --- | --- |
| Video search | The person submits a search or taps a style chip | `www.googleapis.com/youtube/v3/search` then `videos` | The search words (typed words plus the chip's word), `type=video`, `videoEmbeddable`, `safeSearch=moderate`, and the key as the `key` query parameter the API requires (the one documented exception to "keys never in URLs"; it is never logged, never in an error message and never stored with a result) | Name, profile, body data, history, what is saved | Needs a key; nothing is sent without one. Scrolling for more sends the same words with YouTube's `pageToken` (not secret) | `YoutubeApi.searchPage`, `VideoRepository.searchPage` |
| Recommended | Opening Videos with a key, at most once per 6 hours (cached) | Same | Up to 3 searches whose words are built on the phone from this week's under-target muscle regions, equipment, the active program's name and saved video titles. With "Personalise Recommended" off, fixed generic words only | Counts, weights, anything not in the words | On with a key | `RecommendedQueries`, `VideosViewModel.loadRecommended` |
| One video | Opening a video, a pasted link, Make this a workout | `videos` (with a key) or `www.youtube.com/oembed` (no key) | The video id (and the key, with a key) | Everything else | On tap | `VideoRepository.video` |
| Thumbnails | A result is drawn | `i.ytimg.com` and other YouTube picture hosts, https only | A plain GET | Key, cookies | Kept in memory only, never on disk | `VideoThumbnail.kt` |
| Direct playback and downloads (**opt-in, off by default**, Settings > Videos; not an official YouTube feature) | Opening a video, or tapping Download, with the option on | yt-dlp (the youtubedl-android library, running on the phone) talks to YouTube and its video hosts (`googlevideo.com`) the way a browser would; it may also fetch a newer yt-dlp from its own release host once per run when YouTube changes | The video id and what any web client sends (IP address, user agent). **No YouTube API key is used for this.** The stream address is held in memory only, never logged, never stored | Key, name, profile, body data, history | Off until the person confirms the terms warning | `data/video/direct/YtDlpEngine.kt`, `PlaybackSources.kt`, `VideoDownloads.kt` |
| Playback | Opening a video | `www.youtube-nocookie.com` in a WebView (YouTube's own embed) | What the player sends YouTube itself. JavaScript is on for that one view only; it cannot open files or other sites. Nothing is downloaded | The key | On tap | `YoutubePlayer.kt` |

Downloads: a downloaded video is a file in the app's private storage (`files/video_downloads`, not shared storage, not visible to other apps or the gallery) and a row in `downloaded_video` (schema v6). Neither is in a backup, export or sync. Delete on the Downloaded screen removes the file and soft-deletes the row. Downloading and skipping ads outside YouTube's own apps is against YouTube's terms and can get a key suspended, which is why this is off by default and shows that warning once before it can be turned on. Intake never uploads a downloaded file.

Caching and the 30-day rule: API results live in `video_cache` and `video_search` (reused for 6 hours / a day), are not part of any backup, export or sync, and are deleted 30 days after they were fetched (when Videos opens, and by "Clear cached video information"). The snapshot columns of a saved video are cleared at the same age; the save itself (a soft-deletable row) is the person's own data and stays. "Improve with AI" is a separate, optional tap through the AI gateway (see `docs/AI-COSTS.md`).

### Servers the user runs

| Feature | Trigger | Destination | Sent | Not sent | Default | Code |
| --- | --- | --- | --- | --- | --- | --- |
| Device sync | While paired: 30 s after a local change (debounced), every 30 minutes, and Sync now | The Intake sync server the user runs (`server/intake_sync.py`), over HTTPS; plain HTTP only to a local address (`SyncUrlPolicy`) | **Plaintext rows** as JSON (`WireRow.payload`) for every table except `backup_run`, in batches of 400; pairing code and device name at pairing; the token as a Bearer header | Photo files, API keys, backup secrets, settings. The server stores rows unencrypted on its disk by design (`server/README.md`) | **Off** until paired | `data/sync/SyncApi.kt:44-110`, `data/sync/SyncEngine.kt:52,111`, `work/SyncScheduler.kt:44`, `work/SyncWorker.kt:50` |

### Between the user's own devices and apps

| Feature | Trigger | Data | Default | Code |
| --- | --- | --- | --- | --- |
| Wear OS Data Layer | `WearBridge.start()` publishes while a workout session is in progress; the watch queues logged sets | Phone to watch: one DataItem `/intake/workout/active` with the workout title, exercises, planned and logged sets, rest times, unit (`ActiveWorkout`). Watch to phone: `/intake/workout/logged/*`, one set (weight, reps, RPE, times). Items are deleted after they are read. Carried by Google Play services between paired devices; Intake sends nothing to a server of its own | On, but only has effect with a workout running and a watch paired. No watch or no Play services: logs a line and nothing else | `wear/WearBridge.kt:56,73`, `wear/PhoneWearListener.kt:26`, `wearprotocol/.../WearProtocol.kt`, `wear/src/main/.../WatchWorkout.kt:42` |
| Health Connect | On resume and, debounced, after local writes (never on a timer) | Reads only granted types: exercise, total and active calories, BMR, steps, sleep, heart rate, resting HR, weight, body fat, lean mass, and (where the phone offers it) older history, which lets weigh-ins from before Intake was connected be read (weight is read a year back). Imported weigh-ins are marked as from Health Connect and are never written back. The Allow all button asks for every permission in one system sheet. Steps, sleep, active calories and resting heart rate are shown on Today (Activity card) and in Progress, each labelled "from Health Connect" and hidden when there is no data. Writes only granted types: nutrition, weight, hydration, exercise sessions. Handled by the Health Connect store on the phone; Intake does not send it to a server (though a Health Connect row can then be read by other apps the user allows) | **Off** until the user grants permissions | `data/health/HealthConnectManager.kt:96,144`, `data/health/HealthSync.kt:44`, manifest `READ_*`/`WRITE_*` |
| NFC meal tags | User writes a tag, or taps one | The tag holds a URI `intake://log-template/<id>` and an Android Application Record with the package name. No food data. Reading a tag logs the template locally | Only on user action; `NfcTagWriter` | `nfc/NfcTagWriter.kt:28-60`, `MainActivity.kt:79` |

### Recipe link import (the user's action)

| Feature | Trigger | Host | Sent | Not sent | Default | Code |
| --- | --- | --- | --- | --- | --- | --- |
| Import a recipe from a link | Only when the user taps "Read this page" on Food > Recipes > Import from a link, after pasting an address | The site the user typed, straight from the phone: a single `GET` of that page | The request itself, with the User-Agent `Intake/<version> (recipe import)`. No cookies, no key, no account, nothing about the user | Nothing goes to Intake, Anthropic or any third party. The site sees the phone's IP address, like any page visit | Never automatic. HTTPS only (plain `http://` only for local hosts such as `localhost`, `192.168.x.x`, `*.local`). Page size capped at 5 MB, 25 s timeout. The recipe is read on the phone from the page's own data, with no AI | `data/recipe/RecipeFetcher.kt`, `domain/recipe/RecipePageParser.kt` |
| Recipe Hunter page fetch | Only after the user taps Search or Find more in Recipe Hunter. Never automatic | Each candidate recipe address the search returned, straight from the phone: a single `GET` per page, the same as link import | The request itself, with the User-Agent `Intake/<version> (recipe import)`. No cookies, no key, no account, nothing about the user | Nothing goes to Intake, Anthropic or any third party. Each recipe site sees the phone's IP address, like any page visit | HTTPS only, page size and timeout capped as for link import, a few pages at a time. The recipe is read on the phone from the page's own data, with no AI | `data/recipe/RecipeFetcher.kt`, `domain/recipe/RecipePageParser.kt`, `data/ai/recipes/RecipeHunter.kt` |
| "Try with AI" on a page with no recipe data | Only when the user taps that button, and only if an OpenRouter key is saved | `openrouter.ai`, through the same gateway as every other AI call | The visible text of that page (up to 20,000 characters), under the user's AI privacy setting, counted in the AI usage meter and budget cap | The allergen profile and any other data | Off. The button is hidden without a key | `data/recipe/RecipeAiExtractor.kt`, `data/ai/AiGateway.kt` |
| Recipe photo | Showing a recipe that was imported from a link: its list row, the top of its screen, and the import preview | The photo address the page itself published (JSON-LD `image`), usually the recipe site's own host or its image CDN: a single `GET`. The user typed that site, so this is the recipe's own site, not a third party chosen by Intake | The request itself, with the User-Agent `Intake/<version> (recipe import)`. No cookies, no key, nothing about the user | Nothing goes to Intake, Anthropic or Open Food Facts. The site sees the phone's IP address, as it did when the page was imported | HTTPS only; a public host name (IP addresses, `localhost`, `*.local`, `*.lan`, credentials in the address and unusual ports are refused); at most 3 MB; redirects from https to http are refused. Cached in the app's cache folder (never in a backup) and fetched again if cleared; offline only cached photos show. Only the address is saved on the recipe. **Independent of the Open Food Facts switch**, because it does not talk to Open Food Facts. A recipe typed by hand has no photo and makes no request | `ui/images/ProductImages.kt`, `ui/images/RecipePhoto.kt`, `domain/food/ProductImagePolicy.kt` (`Source.RECIPE_PAGE`) |

The recipe is saved with its source address so the user can open the original page, and with the photo's address
so the picture can be shown (the picture itself is never stored in the database, a backup or sync). The notes, times,
nutrition and ingredient groups are read from the page already fetched; no further request is made for them.
Allergens are checked on the phone from the ingredient lines, whatever the page or a model says.

### Files and links, by the user's action

| Feature | What happens | Code |
| --- | --- | --- |
| JSON export, CSV | Written to a location picked with the system file picker (`CreateDocument`). Encrypted if a passphrase is set | `ui/screens/backup/BackupScreen.kt:82,85` |
| Weekly PDF | Saved with `CreateDocument`, or sent to the system print service (`PrintManager`); the print service the user chooses (including a cloud print service) then handles it | `ui/screens/report/WeeklyReportScreen.kt:63,174` |
| Recipe "Open the original page" | `Intent.ACTION_VIEW` of the recipe's source URL: opens the browser | `ui/screens/plan/RecipeHunterScreen.kt:314`, `RecipeDetailScreen.kt:124` |
| Install Health Connect | `ACTION_VIEW` of `market://details?id=com.google.android.apps.healthdata` | `ui/screens/health/HealthConnectScreen.kt:95` |

## Issues and mismatches found

Item 1 was fixed in code. The other items are outside the privacy screen and docs
that this audit was limited to, or are design choices to confirm.

1. **USDA key in the URL query (fixed).** `UsdaService.search` now sends the key
   as an `X-Api-Key` header (`@Header`), so it stays out of URLs, proxy logs and
   server access logs.
2. **Avoided allergen names are sent to OpenRouter** in the photo estimate, Recipe
   Hunter, Plan B, menu import and daily-insight prompts. This is intentional (the
   model is told what to avoid) but it is health information, and the daily insight
   is described elsewhere as "numbers only". The digest itself is numbers only; the
   system prompt is not. The Privacy screen now says so.
3. **Sync is plaintext on the server** by design. The Privacy screen says so. Plain
   HTTP is allowed to local addresses.
4. **The weekly menu re-import worker was scheduled for everyone** at launch and
   relied on the key and the sources list to do nothing. Fixed: it is now an opt-in
   (Settings, AI, "Refresh saved menus weekly", off by default), registered only
   while that is on. A request left by an older install exits at once while it is off.
5. **Menu import fetches the entered URL directly from the phone**, not only via
   OpenRouter, so that site sees the phone's IP address. Delivery marketplaces
   that forbid scraping are refused (`MenuText.forbidsScraping`).
6. **Print service.** `PrintManager` hands the PDF to whichever print service the
   user selects, which may be a cloud service.
7. **Wear Data Layer** transport is up to Google Play services (Bluetooth or
   Wi-Fi between paired devices, possibly cloud-assisted on some setups). Intake
   cannot see or control the route.
8. **The privacy-text claim "API keys go only in Authorization headers"** should
   therefore read "never in logs or exports, and only to their own service" until
   item 1 is changed. The Privacy screen uses that wording.
