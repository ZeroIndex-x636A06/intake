# Intake: guide for agents

Intake is an offline-first personal Android app for nutrition and training. It
also has a Wear OS companion and an optional self-hosted sync server. Read this
file before you change anything, then read `docs/TASKS.md` to pick up work.

## Where things are

| Path | What |
| --- | --- |
| `app/` | Phone app: Kotlin, Compose (Material 3), Room, Hilt, WorkManager, OkHttp |
| `wear/` | Wear OS app. It has the same applicationId and the same signing key as the phone app. |
| `wearprotocol/` | Pure Kotlin types shared by the phone and the watch |
| `server/` | Sync server: one Python stdlib file plus SQLite, with Docker |
| `tools/` | Build-time data scripts, such as the USDA food database builder |
| `docs/SPEC.md` | Original spec |
| `docs/SPEC-amendments.md` | First round of changes |
| `docs/SPEC-v3.md` | **Current overhaul brief.** It wins where the specs disagree. |
| `docs/TASKS.md` | **Task board.** Open work, who is on it, what is done. |
| `docs/scripting/` | The Intake script language wiki, also bundled into the app |
| `docs/ALLERGENS.md`, `PRIVACY.md`, `SYNC-EXPORT.md`, `BENCHMARK.md`, `FOOD-DATA.md`, `MEAL-PLAN.md` | Feature docs |
| `RESUME.md` | Detailed state of the code, plus what is and isn't verified |
| `design/intake-mockup-v2.html` | Visual style reference |

`app/src/main/java/com/zeroindex/intake/` is organised like this:
- `domain/` is pure Kotlin with no Android imports. Put logic here and unit-test it.
- `data/` holds Room, repositories, remote services, sync and backup.
- `ui/` holds screens and components. Navigation lives in `ui/navigation/`:
  - a floating pill bar with tabs Today · Food · Train · Progress · More;
  - each tab has its own nav graph;
  - add new routes inside the right tab graph, above its "Add new … routes above
    this line" marker.

## Rules you must not break

1. **The app works with no network, no account, no API key and no AI.** AI
   (OpenRouter) is an optional convenience. It runs only on an explicit user
   action, never for targets, allergens, plans, lookups or parsing. See
   `docs/SPEC-v3.md` "AI policy".
2. **Allergen safety is local and deterministic.** A model or an imported file can
   raise a warning but never clear one. Ingredient panels and Open Food Facts tags
   are never downgraded. See `docs/ALLERGENS.md`.
3. **Nothing is hard-deleted.** Every table has `deletedAt`; use soft deletes.
4. **Data formats:**
   - Timestamps are UTC epoch millis.
   - Weights are stored in kg, lengths in cm, nutrition per 100 g.
   - Display units are a setting.
5. **Room migrations:**
   - Never use `fallbackToDestructiveMigration`.
   - **Schema v2 is RELEASED**, because the user has it installed (APK built at
     4952404). Never edit v2 or `MIGRATION_1_2`.
   - New changes go into v3 via `MIGRATION_2_3`, until a v3 APK ships.
   - Every migration needs a `MigrationSchemaTest` case.
6. **Secrets:**
   - Never commit or print `local.properties`, keystores, `.env` or `*.db` files
     (the bundled `app/src/main/assets/food_ref_v1.db` is the only exception).
   - API keys live in EncryptedSharedPreferences. They go only in request headers
     or POST bodies, never in URLs or logs.
7. **Never commit the user's personal files.** `release/` is git-ignored and holds
   their real meal plan and the APKs.
8. **Dependencies and source files:**
   - No new Maven dependencies unless you've checked they resolve.
   - No literal U+FEFF in source, because lint fails on it. Use `"﻿"`.
9. Keep model identifiers out of code, docs and commit message bodies. The
   attribution trailers your tool adds, such as `Co-Authored-By`, are fine; keep them.

## Build, test, verify

You need JDK 17+ (21 works) and the Android SDK with platform 37 and build-tools
37.0.0. Point `sdk.dir` at the SDK in an untracked `local.properties`.

```sh
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug
./gradlew :wearprotocol:test :wear:lintDebug
./gradlew :app:assembleDebug :wear:assembleDebug          # installable debug APKs
python3 server/test_intake_sync.py                          # sync server tests
```

Before every merge, all of these must hold:
- unit tests pass;
- `app/build/reports/lint-results-debug.txt` shows **0 errors**;
- `MigrationSchemaTest` passes.

There is no emulator in the cloud containers. Say so when UI hasn't been seen on a
device. Never claim it has.

**Release builds** need the signing properties `RELEASE_STORE_FILE`,
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` in
`local.properties`. The keystore is not in the repo. The user has decided not to
carry the original container key forward, and will generate a new keystore when
needed. The first release signed with the new key will not install over the
existing app, so the user must move their data across:
1. Export a backup.
2. Uninstall the app.
3. Install the new release.
4. Import the backup.

When you hand over that first release, say so. If you don't have a key, build
debug APKs only.

**In a cloud container:** if Gradle gets HTTP 429 from Maven Central, fetch the
missing jars with `curl` into `~/.m2/repository`, using the same path layout, and
add an init script that calls `repositories.mavenLocal()`. Then build with
`--offline`. On a small machine, use `--no-parallel --max-workers=1`, and don't
run two Gradle builds at once, because it can run out of memory.

## How to work alongside other agents

- **Shared integration branch:** `claude/app-build-k2ag1d`. `main` is
  fast-forwarded to it after every verified push, so `main` is always a tested
  snapshot. Start new work from either one; merge back into
  `claude/app-build-k2ag1d`.
- **Your branch:** create one from that branch, named `agent/<short-task-name>`.
  Merge the integration branch into yours often. Use plain merges; don't rebase
  shared history.
- **Claim a task:** before starting, edit `docs/TASKS.md` to mark it with your
  branch name, and push that change. Skip tasks someone else has claimed.
- **Keep work small and local.**
  - Own a feature area.
  - Touch shared files only to add things: `IntakeNavHost.kt`, `Destinations.kt`,
    the hub screens, `Migrations.kt` and `SettingsRepository.kt`.
  - Put logic in `domain/` with tests.
- **When you're done:**
  - merge the integration branch into yours;
  - run the full verify;
  - then merge or open a PR into `claude/app-build-k2ag1d`;
  - update `docs/TASKS.md` and `RESUME.md`, saying what changed and what isn't
    verified.
- **Commit messages:** describe the change plainly. If your tool adds attribution
  lines, keep them.
