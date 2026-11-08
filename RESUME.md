# Where this build is up to

Read this first when picking the work back up. Branch:
`claude/app-build-k2ag1d`.

## Done

All of SPEC.md's built phases and every amendment feature are implemented:

- Core: Today, food logging with the allergen engine, barcode, adaptive TDEE,
  Progress, training with progression rules, settings.
- Hydration, explosive performance metrics, NFC meal tags, weekly PDF report.
- OpenRouter layer: photo estimate, Recipe Hunter, Plan B, menu import, daily
  insight, model picker and in-app benchmark.
- Health Connect with BMR-netted workout calories.
- JSON/CSV export, encrypted nightly backups over WebDAV, SFTP and SMB.
- Sync server (`server/intake_sync.py`), sync client, device pairing UI.
- Wear OS companion (`wear/`, `wearprotocol/`).
- Adaptive landscape and tablet layout.
- Privacy screen and `docs/PRIVACY.md`.
- Intake script (`docs/scripting/`, `domain/script/`): hand-written meal plans and
  programs. Plan tab: Import plan (`.md`/`.txt`/`.json`, detected by content) and
  Export plan as script. Train tab: Import program; program builder: Export program
  as script. Errors open a diagnostics sheet with "How to fix"; warnings and allergen
  hits show in the preview. Program import is one Room transaction with ids from
  `seedId` (program name + day index + exercise name), so re-import updates in place
  and missing rows are soft-deleted. `ExerciseDoc.weight` is
  `program_exercise.startWeightKg` (in schema v2 and `MIGRATION_1_2`), used as the
  first prescription when a lift has no history. The wiki is bundled into assets by
  the `copyScriptDocs` task in `app/build.gradle.kts` and shown by
  `ScriptReferenceScreen` (`domain/docs/Markdown.kt` parses it).

- Navigation and IA rebuild (SPEC-v3 "Information architecture"):
  - `ui/navigation/FloatingPillBar.kt` is the floating pill (vertical on Medium+
    widths). Touch is one `awaitEachGesture` on the whole pill; the logic is pure in
    `PillGeometry.kt` (`PillHitTest`, `PillSlideTracker`). The pill is the Scaffold
    `bottomBar`, so its height reaches every screen through `contentPadding`.
  - `IntakeNavHost` has one nested graph per tab (`TabGraph`), so each tab has its own
    back stack. Screens several tabs open (script reference, AI settings, settings,
    backup, performance) are registered in each graph that links to them. Add new
    routes inside the owning tab's graph, above its marker comment.
  - `TabPolicy` decides switch / pop to root / nothing. Fade-through between tabs,
    shared-axis X inside a tab, via `ui/motion/Motion.kt` (tokens, `LocalReduceMotion`,
    which follows `ANIMATOR_DURATION_SCALE == 0`). Predictive back is enabled in the manifest.
  - Hubs live in `ui/screens/hub/` (`HubScreen` + `HubItem`): Food, Recipes, Eating out,
    Allergens, Train. More and Help are in `ui/screens/more/`. `PlanScreen` is now the
    "Meal plans" route; `TrainScreen` is the "Programs" route.
  - Script docs are split by `ScriptTopic`: a food or training entry shows only its pages,
    with an "All script docs" link.
  - Placeholders awaiting other work: Food > Recipes "Import from a link", Food >
    Allergens "My allergens", More "Body type, phase and baseline", Help "Replay the tour".

- Training rework (SPEC-v3): no program on a new install; templates are in code
  (`data/seed/ProgramTemplates.kt`) and copied into editable rows; exercise
  library (`domain/training/generator/ExerciseCatalog.kt`, topped up on every
  open, hidden exercises stay hidden); exercise library screen with history and
  estimated max; manual builder with plain-language progression; freestyle
  workouts (pick exercises as you go, optional "Save as a program day"); the
  deterministic generator `generateProgram(GeneratorInputs): GeneratedProgram`
  in `domain/training/generator/` (call it from baseline/onboarding, then save
  with `TrainingRepository.createProgramFromBlueprint`). No schema change.
  Existing installs keep every program and session: nothing is deleted, the
  old "Upper / lower, four days" program is now just an ordinary program (it can
  be archived, which is a soft delete), and the library gains the new exercises.
  Not done: a workout card on Today (Today is nutrition only in this codebase).

Docs: `docs/SPEC.md`, `docs/SPEC-amendments.md`, `docs/SPEC-v3.md`, `docs/BACKUP.md`,
`docs/BENCHMARK.md`, `docs/PRIVACY.md`, `server/README.md`.

## Verified

- JVM unit tests pass.
- Backup transports (WebDAV, SFTP with host-key pinning, SMB) tested against
  real servers with `INTAKE_REMOTE_IT=1`.
- Two-device sync through the real server, with `INTAKE_SYNC_IT=1`.
- Both release APKs (`app-release.apk`, `wear-release.apk`) were built and
  signed with the same certificate.

## Not built

- The user's actual 12-week plan content. The meal plan module (SPEC section 9: week
  strip, shopping list, steps, cook mode, "can't eat what I cooked", plan-file
  import) is built and data-driven, but ships empty. Fill it by assigning saved
  recipes or importing a plan file (`docs/MEAL-PLAN.md`). `FoodSeed` is still not
  paired with a recipe seeder.
- Demo data loader in Settings (`SettingsRepository.demoDataLoaded` exists).

## NOT verified

- No emulator or on-screen visual check of any screen (this includes the script import sheets and the reference screen, and the whole training rework: library, builder, generator wizard and preview, freestyle workout, templates).
- The watch's feed for a freestyle workout: a freestyle exercise appears on the watch only once a set is logged for it.
- The floating pill: slide gesture, spring highlight, haptics, colours in both themes, the edge
  back-gesture rule, TalkBack, and the nested-graph tab back stacks (save/restore, pop to
  root, duplicated shared destinations) are compiled and unit-tested for their pure logic only.
- No real OpenRouter calls. The client, prompts and parsing are tested only
  against fakes and fixtures.
- No real watch and phone pairing. The Data Layer path has not run on devices.
- No NFC hardware. Tag writing and reading are untested on a device.
- Health Connect has not run on a device.
- Vision (photo estimate) is not benchmarked. No photos with known answers are
  bundled, so the picker shows it as "not benchmarked".

## Invariants to keep

- Keys are user-supplied and kept in EncryptedSharedPreferences. They are sent
  only in headers and never logged.
- Nothing is hard-deleted. Rows get `deletedAt`, and every read filters
  `deletedAt IS NULL`. Sync relies on this.
- Timestamps are UTC epoch millis. Day boundaries come from `DayRange`/`Clock`.
  Weights are stored in kg and lengths in cm. Pounds and inches are display
  only.
- Never use `fallbackToDestructiveMigration`.
- A model can raise an allergen warning but never clear one. The local engine
  decides.
- Room schema v2 is unreleased. Changes may still go into v2, with
  `schemas/2.json` regenerated and the migration test kept passing.
- `domain/` must not import `android.*`.
- Every way to add food goes through `AllergenRepository.scan` first.
- Macro columns on `food` are per 100 g. `food_log` stores absolute amounts
  copied at log time.
- Instrumented test method names must be camelCase. D8 rejects spaces in dexed
  lambda class names below DEX 040.

## Build environment notes

- AGP 9 has built-in Kotlin support. Do not apply `org.jetbrains.kotlin.android`.
  The root `build.gradle.kts` `buildscript` block raises Kotlin and KSP, so keep
  it in step with `libs.versions.toml`.
- KSP is pinned at 2.3.12 because the 2.3.13 plugin marker is incompletely
  published.
- Charts use Compose Canvas, not Vico.
- `local.properties` holds the SDK path and signing properties. It is untracked.
  Never commit or print it.
- Release signing properties: `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`,
  `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. The wear module reads the same
  ones, so both APKs get the same key.

## Known gaps and judgment calls

- Not stored by program import: day notes and exercise notes (no columns). New
  exercises from a script get primary muscle "other".
- Plan export writes shopping rows the recipes would not generate themselves;
  generated lists and the plan-wide pantry go back as they came. Check marks are not exported.
- The mockup's "optional web assistant" is not built. It would add a network
  path beyond those in SPEC section 3.2. Amend the spec or drop it from the
  design before anyone writes it.
- Whole-word allergen matching does not catch derived words such as "creamery".
  A test pins this.
- `TrainingRepository.consecutiveFailures` looks only at the last session.
- The sync server stores rows as plaintext SQLite, by design.
- The MCP server from the amendments roadmap is not built.
