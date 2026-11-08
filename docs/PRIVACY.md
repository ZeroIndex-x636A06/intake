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
`NfcAdapter`, `LocationManager`, `JSch`, `SMBClient`, `ACTION_VIEW`,
`PrintManager`, `CreateDocument`, `WorkManager`/`CoroutineWorker`, `AsyncImage`
and `Log.`, plus the manifest and the dependency list.

Findings that hold for every path:

- **One HTTP stack.** `data/di/NetworkModule.kt:24` provides the only
  `OkHttpClient`. USDA and Open Food Facts use Retrofit on it; OpenRouter, sync,
  WebDAV and menu page fetches use it directly. SFTP uses JSch and SMB uses SMBJ
  (their own sockets). There is no `HttpURLConnection` or `URL(...).openStream()`.
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
  (`data/keys/ApiKeyStore.kt:47`, AES-256-GCM values, AES-256-SIV names). Backup
  and sync secrets live in `data/backup/remote/BackupCredentialStore.kt:44`. The
  OpenRouter key and the sync token go only in an `Authorization: Bearer` header
  (`OpenRouterClient.kt:59-72`, `SyncApi.kt:67`). The USDA key goes only in an
  `X-Api-Key` header (`UsdaService.kt:61`).
- **Permissions** (`app/src/main/AndroidManifest.xml`): `INTERNET`, `CAMERA`
  (photo and barcode, processed on device; ML Kit barcode scanning is the bundled
  model), `ACCESS_COARSE_LOCATION` (Plan B only), `NFC`, the Health Connect
  permissions listed below, notifications, vibrate, wake lock, foreground service.
  `allowBackup="false"` with data extraction rules, so Android cloud backup and
  device-to-device transfer are off. The watch app requests only `VIBRATE`; it has
  no `INTERNET`.

## Never leaves the phone

| Item | Why we can say so |
| --- | --- |
| Allergen decisions | Made by the local matcher (`AllergenRepository.scan`, `domain/allergen/`). `ModelAllergenPolicy` merges an AI claim in as a warning only; it cannot clear a local match. The names of avoided allergens are sent in some AI prompts (below) so the model can avoid them, but no verdict is sent. |
| Food log, weight, measurements, workouts, plan, templates, settings | Room database and DataStore in app-private storage. They leave only by the paths in this document. |
| Photos | Stored as files in app storage. Rows hold a URI string only (`sourcePhotoUri`, `photoUri`), so backup and sync move the path, never the image. The one exception is the photo estimate, which sends a resized copy (below). |
| API keys | Encrypted at rest; sent only to their own host (OpenRouter key to openrouter.ai, USDA key to api.nal.usda.gov). Not in exports, backups or sync (`BackupTables.kt` has no key table). |
| TDEE and trend math, charts, insights history | Computed locally (`work/TdeeWorker.kt` is local only). |
| Barcode scanning and photo capture | CameraX and ML Kit on device. |

## Network paths

"Default" is the state on a fresh install.

### Food lookups (the user's action)

| Feature | Trigger | Host | Sent | Not sent | Default | Code |
| --- | --- | --- | --- | --- | --- | --- |
| USDA FoodData Central search | Typing in Add food, or Test on the USDA key | `api.nal.usda.gov` `GET /fdc/v1/foods/search` | The query text, page size, data types, the user's USDA key in the `X-Api-Key` header | Anything about the user. The local table is searched first. | Off until a key is saved (`FoodSearchRepository` returns `MissingKey`) | `data/remote/usda/UsdaService.kt:59`, `data/repository/FoodSearchRepository.kt:31`, `ui/screens/settings/SettingsViewModel.kt:129` |
| Open Food Facts barcode | Scanning a barcode not already in the local table | `world.openfoodfacts.org` `GET /api/v2/product/{barcode}.json?fields=...` | The barcode and a fixed field list | Everything else. No key, no account. | On, but only on a scan with no local hit | `data/remote/openfoodfacts/OpenFoodFactsService.kt:45`, `data/repository/BarcodeRepository.kt:43` |

### OpenRouter (the user's own key; `data/ai/OpenRouterClient.kt`)

Every AI call goes through `AiGateway.structured` (`data/ai/AiGateway.kt:56-65`),
which refuses to run without a key (`AiError.NoKey`) and applies the privacy
setting to the request. The request body is built in
`OpenRouterClient.buildPayload` (line 94 on). `provider.data_collection = "deny"`
is set for the default `NO_TRAINING` and for `ZERO_RETENTION` (which adds
`zdr: true`); only the user choosing "Any provider" omits it (lines 137-145).
Default `aiPrivacy` is `NO_TRAINING` (`data/settings/SettingsRepository.kt:61`).
Every request also sends the header `X-Title: Intake`.

| Feature | Trigger | Sent | Not sent | Default | Code |
| --- | --- | --- | --- | --- | --- |
| Model list | Opening Settings, AI (`AiSettingsViewModel.kt:94`) or any AI task (`AiGateway.modelFor` line 38) when the cached list is over 24 h old | `GET openrouter.ai/api/v1/models`, no key, no body | Anything of the user's. Public; works with no key. | On (refreshes while the AI screen is open or an AI task runs) | `data/ai/ModelCatalogRepository.kt:73,82`, `OpenRouterClient.kt:53` |
| Key check | Saving or testing the key | `GET /api/v1/key` with the key in `Authorization` | Nothing else | Only with a key | `AiSettingsViewModel.kt:105`, `AiGateway.kt:94` |
| Photo estimate | User takes or picks a photo and asks for an estimate | The resized JPEG (`PhotoPreparer`), "Estimate this meal.", and the names of avoided allergens in the system prompt (`VisionModels.kt:89`) | The allergen verdict, name, weight, other log entries | Only with a key; per photo | `data/ai/vision/VisionTracker.kt:57-70` |
| Recipe Hunter | User presses Find recipes | Macro, time and cuisine limits, free-text notes, avoided allergens, source URLs of recipes already found. Tools: `openrouter:web_search` and `openrouter:web_fetch`, run **by OpenRouter**, not the phone | Anything from the log or body | Only with a key; per search | `data/ai/recipes/RecipeHunter.kt:106-112,180-215` |
| Plan B | User searches for a place | Coordinates rounded to two decimals (about 1.1 km) from the coarse permission, or the typed zip code; kcal and protein left today; avoided allergens. Same web tools, run by OpenRouter | Exact location, the log, names of foods eaten | Only with a key; location is read only on that search and only with the permission | `data/ai/planb/PlanBFinder.kt:139-148,229-247`, `domain/ai/PlanBFit.kt:56` |
| Menu import (manual) | User adds or refreshes a menu source | Pasted text (up to `MenuText.MAX_CHARS`), or the PDF bytes (also base64 in a `file` part), or text this phone fetched from the entered URL, or just the URL when OpenRouter should read it with `web_fetch`; restaurant name; avoided allergens | Anything else | Only with a key; per import | `data/ai/menu/MenuIngestor.kt:81-125` |
| Menu import (weekly) | WorkManager, every 7 days, unmetered network, battery not low. **Scheduled unconditionally** at launch but a no-op without a key | Same as above for each **enabled URL or PDF** source. Pasted-text sources are skipped | Same | Runs only if a key exists and a source exists (sources default `enabled = true` once added) | `IntakeApplication.kt:36`, `work/MenuIngestionWorker.kt:34-37,49`, `MenuIngestor.runScheduled` line 183 |
| Direct page fetch for menus | Part of the two rows above, for URL sources | `GET <the URL the user entered>` from the phone, with `User-Agent: Intake/1.0 (...)` | No key, no user data. The destination is whatever site the user named. | Per import | `MenuIngestor.fetchText` line 193 |
| Daily insight | `TodayViewModel.kt:83` calls `ensureToday()` on opening Today; at most one per day | The digest from `domain/ai/InsightDigest.kt`: averages, targets, weight trend, TDEE, body fat and lean mass, session counts, RPE trend, sleep, resting HR, steps, water. Plus avoided allergens in the system prompt (`DailyInsights.kt:180`). The digest is shown to the user in the card | Food names, notes, dates, places | **Off** (`dailyInsightEnabled = false`, `SettingsRepository.kt:75`); needs a key as well | `data/ai/insight/DailyInsights.kt:69-112` |
| Benchmark | User starts it in Settings, AI, Run benchmark. Never automatic | The bundled fixture texts (menu, recipe, plan B and insight samples, no personal data) and the app's own system prompts, sent to OpenRouter with the same privacy settings as other AI calls (`data_collection: deny`). Needs the user's OpenRouter key | Anything from the log, body or profile | Only with a key, only when started; a user-set spend cap (default $0.25) stops the run. Results are stored locally in `model_benchmark` and are synced like other tables | `data/ai/benchmark/BenchmarkRunner.kt`, `docs/BENCHMARK.md`, `ModelCatalogRepository.benchmarks` |

### Servers the user runs

| Feature | Trigger | Destination | Sent | Not sent | Default | Code |
| --- | --- | --- | --- | --- | --- | --- |
| Nightly backup | WorkManager, daily around 02:00, network connected, battery not low; also Back up now | The WebDAV, SFTP (JSch, host key pinned) or SMB (SMBJ, signing required) server the user entered | A file `intake-backup-<time>.intake`: the full JSON of every table, **AES-256-GCM encrypted with the user's passphrase** (PBKDF2-SHA256) on the phone first. `BackupRunner.run` skips if no passphrase is set ("Backups are never sent unencrypted"). | Photo files, API keys, settings, secrets | **Off** until a destination exists and the schedule is not Manual (`BackupWorker.sync` line 54) | `data/backup/remote/BackupRunner.kt:103-125`, `work/BackupWorker.kt`, `BackupScheduler.kt`, `WebDavStore.kt`, `SftpStore.kt`, `SmbStore.kt`, `domain/backup/BackupDestination.kt:93` (plain HTTP only to private/local hosts), `docs/BACKUP.md` |
| Device sync | While paired: 30 s after a local change (debounced), every 30 minutes, and Sync now | The Intake sync server the user runs (`server/intake_sync.py`), over HTTPS; plain HTTP only to a local address (`WebDavUrlPolicy`) | **Plaintext rows** as JSON (`WireRow.payload`) for every table except `backup_run`, in batches of 400; pairing code and device name at pairing; the token as a Bearer header | Photo files, API keys, backup secrets, settings. The server stores rows unencrypted on its disk by design (`server/README.md`) | **Off** until paired | `data/sync/SyncApi.kt:44-110`, `data/sync/SyncEngine.kt:52,111`, `work/SyncScheduler.kt:44`, `work/SyncWorker.kt:50` |

### Between the user's own devices and apps

| Feature | Trigger | Data | Default | Code |
| --- | --- | --- | --- | --- |
| Wear OS Data Layer | `WearBridge.start()` publishes while a workout session is in progress; the watch queues logged sets | Phone to watch: one DataItem `/intake/workout/active` with the workout title, exercises, planned and logged sets, rest times, unit (`ActiveWorkout`). Watch to phone: `/intake/workout/logged/*`, one set (weight, reps, RPE, times). Items are deleted after they are read. Carried by Google Play services between paired devices; Intake sends nothing to a server of its own | On, but only has effect with a workout running and a watch paired. No watch or no Play services: logs a line and nothing else | `wear/WearBridge.kt:56,73`, `wear/PhoneWearListener.kt:26`, `wearprotocol/.../WearProtocol.kt`, `wear/src/main/.../WatchWorkout.kt:42` |
| Health Connect | On resume and, debounced, after local writes (never on a timer) | Reads only granted types: exercise, total and active calories, BMR, steps, sleep, heart rate, resting HR, weight, body fat, lean mass. Writes only granted types: nutrition, weight, hydration, exercise sessions. Handled by the Health Connect store on the phone; Intake does not send it to a server (though a Health Connect row can then be read by other apps the user allows) | **Off** until the user grants permissions | `data/health/HealthConnectManager.kt:96,144`, `data/health/HealthSync.kt:44`, manifest `READ_*`/`WRITE_*` |
| NFC meal tags | User writes a tag, or taps one | The tag holds a URI `intake://log-template/<id>` and an Android Application Record with the package name. No food data. Reading a tag logs the template locally | Only on user action; `NfcTagWriter` | `nfc/NfcTagWriter.kt:28-60`, `MainActivity.kt:79` |

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
4. **The weekly menu re-import worker is scheduled for everyone** at launch and
   relies on `gateway.isConfigured()` and the sources list to do nothing. No
   network call happens without a key and an enabled source, but the work request
   exists on every install.
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
