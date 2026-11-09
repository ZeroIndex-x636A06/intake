# Intake

A personal, offline-first Android app for nutrition and training. Single user,
no accounts, no analytics. It is installed by sideloading a signed APK.

Intake works fully offline and needs no API key. Every feature that uses the
network is off until you supply a key or a server address.

Requirements are in [docs/SPEC.md](docs/SPEC.md) and
[docs/SPEC-amendments.md](docs/SPEC-amendments.md). The current state of the
build is in [RESUME.md](RESUME.md).

## Features

Core, from SPEC.md:

- Food logging by recents, offline USDA search (about 8,000 foods bundled, no key), optional
  Open Food Facts search, barcode scan, quick add and
  hand-entered foods. Every path runs the local allergen check first.
- Adaptive TDEE measured from your own intake and weight trend.
- Exercise diagrams: every exercise opens a sheet with start and end photos (Free Exercise DB, public domain),
  a front and back muscle map, how-to steps and your own history. Add your own exercises with a photo, and
  save single-session workouts ("Arm day") that start any time. The plan creator uses your focus muscles, areas
  to go easy on and training weekdays, suggests starting weights and says why for every exercise. See
  [docs/EXERCISE-IMAGES.md](docs/EXERCISE-IMAGES.md) and [docs/WORKOUTS.md](docs/WORKOUTS.md).
- Profile & goals (More): pick your build and goal, run a 7-day baseline week, and get a plan
  (calories, macros, water, training) where every number says why and can be edited. Works with
  no network and no AI; Advanced adds g/kg macros, carb cycling, refeeds and diet breaks. See
  [docs/GOALS.md](docs/GOALS.md).
- Training: progression rules, active workout, rest timer, plate calculator,
  program builder, history. A new install has **no program**: build one by hand,
  let the generator make one ("Make one for me": a short wizard, a preview with a
  reason for every choice, nothing saved until you confirm), or copy a template.
  You can also start an empty workout and pick exercises as you go.
- Starter food library and a library of about 100 exercises (barbell, dumbbell,
  machine, cable, bodyweight, common isolations) are seeded locally. Built-in
  programs are templates, copied into your own editable program when picked.
- Write a meal plan or workout program by hand and import or export it as a
  plain-text script, with an offline in-app reference:
  [docs/scripting/](docs/scripting/README.md).
- Progress charts and a settings and privacy screen.

Navigation (SPEC-v3): a floating pill with five tabs, **Today, Food, Train,
Progress, More**. Slide a finger along the pill to switch tab (a light haptic
tick marks each tab crossed, lifting commits); a tap works as normal and
TalkBack gets ordinary tab semantics. On tablets the pill stands on end as a
rail. Each tab keeps its own back stack, and tapping the current tab returns it
to its root.

- **Food**: Log food, Meal templates, Meal plans (plans of any length, several
  of them, with a repeat schedule; build one by hand or import it), Recipes (type
  one in, import one from a link with no AI, or let Recipe Hunter find one, an
  optional AI), Eating out (Plan B, menu import), Allergens, Food script help.
- **Train**: start or carry on today's workout (or an empty one), Programs
  (build, edit, import and export), Make one for me, Templates, Exercises
  (search, filter, add your own, history and estimated max), Performance (jump
  tests), Program script help.
- **Progress**: charts, plus Training stats, Weekly report and Health Connect.
- **More**: Profile and goals, Settings, Backup and sync, AI (optional),
  Privacy, Help (every script doc).
- Script docs follow the topic: food screens open the meal-plan pages, training
  screens the program and progression-rule pages, and More, Help the full set.

Amendment features:

- **Coach.** A weekly check-in that reads your last week and suggests a few small changes (calories from your
  weight trend, protein, strength stalls, recovery, volume, water), each with its numbers and an Apply or Dismiss
  button, plus a "One thing to do today" card on Today. Plain rules, no AI needed: [docs/COACH.md](docs/COACH.md).
  Settings and the profile now travel with exports and backups: [docs/BACKUP.md](docs/BACKUP.md).
- **Hydration.** Water card on Today with one-tap drink chips and a daily target that
  shows its working. Every drink has a type and an "adds to hydration" factor (tea and
  coffee count like water), caffeine and alcohol are tracked, workouts add sweat water,
  and an opt-in weather adjustment (Open-Meteo, rounded location or a typed city) adds
  water on hot days. See [docs/HYDRATION.md](docs/HYDRATION.md).
- **Explosive performance metrics.** Vertical jump, standing reach and total
  reach, overlaid on training volume on the Progress tab.
- **AI features through OpenRouter**, using your own key. See
  [docs/PRIVACY.md](docs/PRIVACY.md) for what each one sends.
  - Photo estimate: a conservative macro range, reviewed before saving.
  - Recipe Hunter: web search for recipes, verified locally for dairy.
  - Plan B: nearby places that fit your remaining macros.
  - Menu import: from a URL, PDF or pasted text, weekly refresh optional.
  - Daily insight: one suggestion from a numbers-only digest. Off by default.
  - A master "Use AI features" switch, a monthly budget cap (default $2, a notice at 80%, calls
    pause at 100%), a usage meter per feature and an estimated-cost screen in Settings, AI.
    AI runs only when you tap something or on a schedule you switch on, and never for targets,
    water, programs, allergens or lookups: [docs/AI-COSTS.md](docs/AI-COSTS.md).
  - Model picker with a benchmark you run on your own key:
    [docs/BENCHMARK.md](docs/BENCHMARK.md).
  - A model can raise an allergen warning but never clear one. The local
    engine decides.
- **NFC meal-prep tags.** Save a meal as a template, write it to a tag, tap the
  container to log it.
- **Health Connect.** Reads and writes granted types. Active calories from
  workouts have BMR for the same window subtracted, so they are not
  double-counted.
- **Weekly PDF report.** Made on the phone, saved or printed.
- **Export and backup.** JSON and CSV export. Encrypted nightly backups to
  WebDAV, SFTP or SMB: [docs/BACKUP.md](docs/BACKUP.md).
- **Self-hosted sync server.** Last-writer-wins CRDT with a hybrid logical
  clock, one Python file: [server/README.md](server/README.md).
- **Wear OS companion.** Log sets, enter RPE and run the rest timer from the
  watch.
- **Adaptive layout.** A vertical pill rail and two-column Today and Progress on
  tablets and in landscape. Predictive back is on, and transitions are instant
  when the system animation scale is 0.

## Layout

```
app/           Android phone app (com.zeroindex.intake)
wear/          Wear OS companion
wearprotocol/  Data Layer message types shared by app and wear
server/        Sync server (intake_sync.py), Dockerfile, compose file, tests
docs/          SPEC, SPEC-amendments, BACKUP, BENCHMARK, PRIVACY, FOOD-DATA, GOALS
tools/         build_food_db.py: rebuilds the bundled USDA food database
design/        HTML mockups used as the design reference
schemas/       Exported Room schema JSON (committed)
```

Inside `app/`, `domain/` is pure Kotlin with no `android.*` imports.
Repositories are the only code that touches DAOs or the network.

## Build (Linux)

Requirements:

- JDK 17 or newer. The Java target is 17.
- Android SDK with platform 37 and build-tools 37.0.0. `compileSdk` and
  `targetSdk` are 37. `minSdk` is 29 for the phone and 30 for the watch.
- Versions come from `gradle/libs.versions.toml`: Android Gradle Plugin 9.5.1,
  Kotlin 2.4.21, KSP 2.3.12. The Gradle wrapper downloads Gradle itself.
- Put the SDK path in `local.properties` at the repo root, which is untracked:

  ```properties
  sdk.dir=/home/you/android-sdk
  ```

Debug build:

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Debug builds use the
application id suffix `.debug`.

Release build. Add these properties to `local.properties` (names only, use your
own values):

```properties
RELEASE_STORE_FILE=<path to your .jks>
RELEASE_STORE_PASSWORD=<store password>
RELEASE_KEY_ALIAS=<key alias>
RELEASE_KEY_PASSWORD=<key password>
```

```bash
./gradlew :app:assembleRelease :wear:assembleRelease
```

The phone release build fails with instructions if no keystore is configured.
The wear release build is unsigned if the properties are missing.

The wear app must be signed with the same key as the phone app and has the same
`applicationId`, `com.zeroindex.intake`. The Data Layer will not connect
otherwise. Both modules read the same `RELEASE_*` properties, so one keystore
covers both. Keep a copy of the keystore off this machine. A different key
means uninstalling first, which deletes your data.

Outputs: `app/build/outputs/apk/release/app-release.apk` and
`wear/build/outputs/apk/release/wear-release.apk`.

### Tests

```bash
./gradlew test
```

This runs the JVM unit tests for all modules. The Room DAO tests need a device:
`./gradlew connectedDebugAndroidTest`.

Server tests:

```bash
cd server && python3 -m unittest test_intake_sync.py
```

Two integration suites are skipped unless you opt in:

- `INTAKE_REMOTE_IT=1`: `RemoteStoreIntegrationTest` runs the WebDAV, SFTP and
  SMB transports against real servers. It expects WebDAV on
  `127.0.0.1:8099/dav`, sshd on `127.0.0.1:2222` and smbd on `127.0.0.1:445`
  with a share named `backup`. Setup is in [docs/BACKUP.md](docs/BACKUP.md).

  ```bash
  INTAKE_REMOTE_IT=1 ./gradlew :app:testDebugUnitTest --tests '*RemoteStoreIntegrationTest*'
  ```

- `INTAKE_SYNC_IT=1`: `SyncProtocolIntegrationTest` starts `server/intake_sync.py`
  itself and syncs two simulated devices through it. It needs `python3` on the
  path and nothing else.

  ```bash
  INTAKE_SYNC_IT=1 ./gradlew :app:testDebugUnitTest --tests '*SyncProtocolIntegrationTest*'
  ```

## Install

Enable USB debugging on the phone (Developer options), then:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

For the watch, enable Developer options, then Wireless debugging on the watch.
Use its pairing code and address:

```bash
adb pair <watch-ip>:<pairing-port>
adb connect <watch-ip>:<debugging-port>
adb -s <watch-ip>:<debugging-port> install -r wear/build/outputs/apk/release/wear-release.apk
```

`versionCode` comes from the build time, so each build upgrades the last one in
place.

## First-run setup

The app now walks you through it. On first launch a short welcome asks for your units, a few
basics, your goal and the foods you avoid, then offers a baseline week or an instant estimate and a
quick tour of Today. Every step can be skipped, and More, Help can replay the tour, the welcome and
the screen tips. The list below is only for the optional extras, and none of it is needed to use the
app offline.

1. **OpenRouter key.** More, AI: save your key, then run the benchmark so
   the picker can suggest models. See [docs/BENCHMARK.md](docs/BENCHMARK.md).
2. **USDA key (optional, not needed for search).** Search works offline with no key; see
   [docs/FOOD-DATA.md](docs/FOOD-DATA.md) for the data sources and licences.
   A key only adds live USDA branded foods. More, Settings: save a FoodData Central key to enable USDA
   search. See [docs/PRIVACY.md](docs/PRIVACY.md).
3. **Health Connect.** Progress, Health Connect: grant the read and write types you
   want. Nothing is read until you do.
4. **Backups.** More, Backup & sync: choose WebDAV, SFTP or SMB and set a
   passphrase. Backups are never sent without one. Keep the passphrase
   somewhere other than the phone. See [docs/BACKUP.md](docs/BACKUP.md).
5. **Sync server.** Deploy `server/`, give it an https address, and pair each
   device with a code. See [server/README.md](server/README.md).

## Privacy

Data lives in app-private storage. Android cloud backup and device transfer are
off. Keys are supplied by you, stored encrypted, sent only in request headers
to their own service, and never logged. Data leaves the phone only through
features you turn on. [docs/PRIVACY.md](docs/PRIVACY.md) lists every path with
code references.
