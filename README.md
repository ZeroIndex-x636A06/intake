# Intake

A personal, offline-first Android app for nutrition and training. Single user,
no accounts, no server, no analytics. Installed by sideloading a signed release
APK; it is never going near the Play Store.

Built to [SPEC.md](SPEC.md). See [RESUME.md](RESUME.md) for exactly where the
build is up to and what comes next.

---

## What you need

- A JDK 17 or newer (`java -version`). JDK 21 is what this was built against.
- The Android SDK, with **platform 37** and **build-tools 37.0.0**.
- Nothing else. The Gradle wrapper fetches Gradle 9.9.0 itself, with a pinned
  SHA-256, so there is no system Gradle to install.

If you do not have the SDK, the command-line tools are enough:

```bash
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
curl -O https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip
unzip commandlinetools-linux-16111833_latest.zip
mv cmdline-tools latest
yes | latest/bin/sdkmanager --licenses
latest/bin/sdkmanager --install "platforms;android-37.1" "build-tools;37.0.0" "platform-tools"
```

Then point the build at it, in `local.properties` at the repo root:

```properties
sdk.dir=/home/you/android-sdk
```

`local.properties` is untracked on purpose — it holds your SDK path and your
signing passwords.

---

## Signing

Generate the keystore once:

```bash
keytool -genkey -v -keystore ~/.android/personal-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias mealtrain
```

Add four lines to `local.properties`:

```properties
RELEASE_STORE_FILE=/home/you/.android/personal-release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=mealtrain
RELEASE_KEY_PASSWORD=...
```

A release build with no keystore configured fails immediately with these
instructions rather than producing an unsigned APK.

> **Back the keystore up somewhere that is not this laptop.** Android identifies
> an app by its signing key. If you ever sign with a different key, the only way
> to install is to uninstall first, and uninstalling takes every gram of logged
> data with it. There is no cloud backup — the JSON export is the backup.

---

## Build and install

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

`versionCode` is derived from the build timestamp as `yyMMddHH`, so each build
upgrades the last one in place instead of demanding an uninstall.

### Getting adb to see the phone

- On the phone: Settings → About phone → tap **Build number** seven times to
  unlock Developer options, then Settings → System → Developer options → enable
  **USB debugging**.
- `adb` ships in the `android-tools` package on Arch, `android-tools-adb` on
  Debian and Ubuntu, and `android-tools` on Fedora. It is also in the SDK at
  `~/android-sdk/platform-tools/adb`.
- `adb devices` showing the device as **unauthorized** means the RSA
  fingerprint prompt is waiting on the phone's screen. Accept it, and tick
  "always allow" so it stops asking.
- The device not appearing **at all** is almost always a udev permissions
  problem. Find the vendor ID with `lsusb`, then:

  ```bash
  # Samsung is 04e8; substitute your own vendor ID.
  echo 'SUBSYSTEM=="usb", ATTR{idVendor}=="04e8", MODE="0666", GROUP="plugdev"' \
    | sudo tee /etc/udev/rules.d/51-android.rules
  sudo udevadm control --reload-rules && sudo udevadm trigger
  ```

  Unplug and replug, then `adb kill-server && adb devices`.
- Make sure your user is in the `plugdev` group (`groups | grep plugdev`).

---

## Tests

```bash
./gradlew test                      # 111 unit tests, no device needed
./gradlew connectedDebugAndroidTest # Room DAO tests, needs a phone or emulator
```

| Suite | Count | Covers |
| --- | --- | --- |
| `AllergenMatcherTest` | 20 | whole-word matching, buttermilk vs butternut, structured tags, plant milks |
| `ExpressionEvaluatorTest` | 10 | the custom-rule parser, including nine malformed-rule shapes |
| `ProgressionEvaluatorTest` | 19 | all five progression rule types |
| `TdeeCalculatorTest` | 14 | TDEE maths, confidence bands, rate goals |
| `WeightTrendTest` | 7 | EWMA smoothing and slope |
| `DayRangeTest` | 10 | UTC-millis day boundaries, both DST transitions |
| `UnitConverterTest` | 7 | kg/lb and cm/in, storage vs display |
| `MacrosTest` + `MacroSplitTest` + `DayProgressTest` | 12 | per-100g scaling, protein range, fat floor |
| `PlateCalculatorTest` + `WarmupGeneratorTest` + `OneRepMaxTest` | 12 | loading, ramps, Epley |

Room DAO tests are instrumented rather than Robolectric: Robolectric's native
SQLite artifact could not be fetched from Maven Central in the build
environment, and an unrunnable suite is worse than one that needs a device.
Their method names are camelCase because D8 rejects spaces in dexed lambda
class names below DEX 040.

---

## Layout

```
app/src/main/java/com/zeroindex/intake/
├── data/                 Room, network, DI. Only repositories touch DAOs.
│   ├── db/entity/        23 tables, all soft-deletable
│   ├── db/dao/           15 DAOs, every read filtered on deletedAt IS NULL
│   ├── remote/           USDA search, Open Food Facts barcode lookup
│   ├── repository/       the only things that touch DAOs or network
│   ├── keys/             EncryptedSharedPreferences for user-supplied API keys
│   ├── seed/             starter food library, exercises, program
│   └── settings/         DataStore-backed preferences
├── domain/               pure Kotlin. No Android imports here, by rule.
│   ├── allergen/         whole-word alias matching
│   ├── nutrition/        macro maths, protein range, fat floor
│   ├── tdee/             EWMA smoothing, measured maintenance
│   ├── training/         progression rules, custom-rule parser, plates, 1RM
│   ├── time/             UTC-millis <-> local-day conversion
│   └── units/            kg/cm storage, lb/in display
├── timer/                foreground countdown for rest and cook-mode timers
├── work/                 nightly TDEE recalculation
└── ui/                   Compose screens, theme, navigation
schemas/                  exported Room schema JSON, committed
```

`domain/` having no Android imports is what makes it unit-testable without a
device, and it is enforced by convention — if you find yourself importing
`android.*` there, the logic belongs in `data/` or `ui/`.

---

## Dependency versions

Every version in `gradle/libs.versions.toml` was resolved against Google Maven
and Maven Central on 2026-11-08 rather than taken from memory:

| | |
| --- | --- |
| Android Gradle Plugin | 9.5.1 |
| Gradle | 9.9.0 (wrapper, checksum-pinned) |
| Kotlin | 2.4.21 |
| KSP | 2.3.12 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 29 |
| Compose BOM | 2026.09.00 |
| Room | 2.8.5 |
| Hilt | 2.60.1 |

Three things to know before bumping anything:

- **AGP 9 has built-in Kotlin support.** The `org.jetbrains.kotlin.android`
  plugin is no longer applied, and applying it is a hard error. AGP ships an
  older Kotlin than current, so the `buildscript` block in the root
  `build.gradle.kts` raises Kotlin and KSP — which is why Kotlin's version
  appears in two places. Keep those classpath lines in step with
  `[versions] kotlin` and `[versions] ksp`.
- **KSP 2.3.13 is pinned back to 2.3.12.** The 2.3.13 Gradle plugin marker is
  incompletely published (clean 404 on its `.module`), so plugin resolution
  fails.
- **Charts use Compose Canvas, not Vico.** §1 allows either; Canvas keeps the
  dependency surface small and the rendering exactly as the spec describes.

Retrofit, OkHttp and kotlinx.serialization are compiled in so the release build
proves the R8 keep rules for them, which §15.4 and §16 call out as exactly what
R8 breaks.

---

## Privacy

Everything lives in a single Room database in app-private storage. Cloud backup
and device transfer are both switched off in the manifest, so nothing syncs
anywhere.

Exactly three things ever leave the phone, and only when you ask: a search term
for USDA food lookup, a barcode for Open Food Facts, and a photo for AI
estimation. API keys are supplied by you at runtime and stored in
`EncryptedSharedPreferences`; no key is in this repo, and the app is fully
functional without one, minus the AI feature. There is an in-app privacy screen
saying the same.
