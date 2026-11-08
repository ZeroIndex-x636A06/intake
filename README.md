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

- Food logging by recents, local and USDA search, barcode scan, quick add and
  hand-entered foods. Every path runs the local allergen check first.
- Adaptive TDEE measured from your own intake and weight trend.
- Training: progression rules, active workout, rest timer, plate calculator,
  program builder, history.
- Starter food library, exercises and a training program are seeded locally.
- Write a meal plan or workout program by hand and import or export it as a
  plain-text script, with an offline in-app reference:
  [docs/scripting/](docs/scripting/README.md).
- Progress charts and a settings and privacy screen.

Amendment features:

- **Hydration.** Water card on Today with volume presets and a daily target.
- **Explosive performance metrics.** Vertical jump, standing reach and total
  reach, overlaid on training volume on the Progress tab.
- **AI features through OpenRouter**, using your own key. See
  [docs/PRIVACY.md](docs/PRIVACY.md) for what each one sends.
  - Photo estimate: a conservative macro range, reviewed before saving.
  - Recipe Hunter: web search for recipes, verified locally for dairy.
  - Plan B: nearby places that fit your remaining macros.
  - Menu import: from a URL, PDF or pasted text, weekly refresh optional.
  - Daily insight: one suggestion from a numbers-only digest. Off by default.
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
- **Adaptive layout.** Navigation rail and two-column Today and Progress on
  tablets and in landscape.

## Layout

```
app/           Android phone app (com.zeroindex.intake)
wear/          Wear OS companion
wearprotocol/  Data Layer message types shared by app and wear
server/        Sync server (intake_sync.py), Dockerfile, compose file, tests
docs/          SPEC, SPEC-amendments, BACKUP, BENCHMARK, PRIVACY
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

Nothing here is needed to use the app offline.

1. **OpenRouter key.** Settings, AI: save your key, then run the benchmark so
   the picker can suggest models. See [docs/BENCHMARK.md](docs/BENCHMARK.md).
2. **USDA key (optional).** Settings: save a FoodData Central key to enable USDA
   search. See [docs/PRIVACY.md](docs/PRIVACY.md).
3. **Health Connect.** More, Health Connect: grant the read and write types you
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
