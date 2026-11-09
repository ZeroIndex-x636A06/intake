# Task board

The brief is `docs/SPEC-v3.md`. To claim a task, put your branch name next to it
and push that change before you start. When you finish, move the task to Done
with its merge commit. Read `CLAUDE.md` before you claim anything.

## In progress

**Claimed by the coordinating session**

- **Food database, barcode fix, Open Food Facts.** Done on branch
  `worktree-agent-a9e0fd1e72bfd3051` and waiting to merge. This adds
  `MIGRATION_2_3` and `food.offContributedAt`.
- **Flexible meal plans, manual recipes, recipe import from a link.** Covers
  plans of any length, a loop schedule, multiple plans, a manual plan builder, a
  recipe editor, and JSON-LD recipe import. The schema part goes into
  `MIGRATION_2_3`.

## Open: anyone can claim these

Each task links to the section of `docs/SPEC-v3.md` that defines it.

1. **Profile, baseline week and goal builder.** See "Engines".
   - **Profile:** body type (a simple build picker plus an optional body-fat %),
     phase (cut, lean bulk, bulk, maintain, recomp, contest prep, reverse diet),
     experience, and training availability.
   - **Baseline week:** 7 days of logged intake, weigh-ins, steps, sleep and
     resting heart rate, all from existing data and Health Connect. When enough
     data exists, a WorkManager job sends a notification.
   - **Generators:** calorie and macro targets (Mifflin-St Jeor or
     Katch-McArdle on day one, then the measured TDEE), the water target, and a
     training program through
     `domain/training/generator/ProgramGenerator.generateProgram`.
   - **Display:** every number shows a one-line "why" and can be edited.
     Advanced mode adds g/kg macros, carb cycling, refeeds and diet breaks.
   - **Today:** add a "today's workout" card. Today is nutrition-only at the
     moment.
2. **Onboarding and Today tour.**
   - Do this after task 1, because it reuses the profile and baseline screens.
   - **Welcome flow:** units, about you, goal and phase, foods you avoid (My
     allergens), your plans (import / build / generate), optional extras. Every
     step can be skipped. Installs that already have data skip the flow.
   - **Coach-mark tour on Today.**
   - **One-time tips** on each tab.
   - **Settings:** add "Replay the tour". The Help screen already has a
     placeholder for it.
4. **Coach engine.** See "Additions → Coach engine".
   - Weekly check-in suggestions from deterministic rules, each with a "why",
     covering weight trend vs pace, adherence, strength stalls, recovery
     signals, protein/water gaps and volume per muscle. The user taps Apply or
     Dismiss.
   - A daily "one thing to do today" card on Today.
   - AI narrative is optional and only rewords the suggestions.
5. **Motion and clarity polish.** See SPEC-v3 point 5.
   - Motion tokens already exist in `ui/motion/Motion.kt`.
   - Animate the calorie ring sweep and count-ups, the water wave, protein-goal
     confetti, set and item check-offs, the rest-timer ring, chart draw-in and
     shimmer loading.
   - Add a "Reduce motion" setting.
   - Rewrite jargon as InfoTips (`ui/components/InfoTip.kt` exists). Give each
     screen one primary action, and make every empty state say what to do next.
6. **AI policy audit, usage meter and cost analysis.** See "Additions → AI
   policy and cost". Do this last, once the AI features are final.
   - Audit every OpenRouter call site.
   - Show calls and spend per feature.
   - Add a monthly budget cap.
   - Estimate daily and monthly cost from the chosen models, and write
     `docs/AI-COSTS.md`.
7. **Small follow-ups.**
   - Remove the duplicate Allergens section from Settings. My allergens
     replaces it.
   - Add "As many as you can" (AMRAP) to the UI, if it's wanted.
   - Show "mentioned as avoided" notes on recipe and plan screens.
   - Write database-level tests for `TrainingRepository`'s new functions
     (`moveDay`, `duplicateDay`, `copySettingsToOthers`,
     `createProgramFromBlueprint`).

## Needs the user

- Test on real devices. None of the v3 UI has been seen on a screen yet. Check:
  - the floating pill and its slide gesture;
  - barcode scanning on the release build;
  - the new screens.
- Keep the release keystore safe. Updates must be signed with the same key.
- Optional: deploy the sync server (`server/README.md`), add an OpenRouter key
  and run the benchmark, and set a backup destination.

## Done (v3)

- Hydration v2: drink types and hydration factors, caffeine and alcohol tracking and top-ups, opt-in Open-Meteo
  weather, sweat loss from logged workouts with a measured sweat rate, and a target breakdown in the water sheet
  (branch `worktree-agent-a18b02fa9b8eab30d`; see `docs/HYDRATION.md`). Schema change: `migrateHydration_2_3`.
  Not seen on a device.
- Navigation: floating pill, Food/Train/Progress/More hubs, split script docs
  (4485a76).
- Allergens v2: 15 built-in allergens, custom allergens and keywords,
  negation-aware matching, a new import preview (7eeaff5). Its schema edits now
  live in `MIGRATION_2_3` (see Schema repair).
- Schema repair: 3c7600b. v2 is restored byte-for-byte to 4952404.
  The allergen columns and `allergen_definition` moved into `migrateAllergens_2_3`,
  next to `migrateFoodOff_2_3`. Other agents add `migrateXxx_2_3` functions to
  `MIGRATION_2_3_STATEMENTS`. `MigrationSchemaTest` upgrades the released v2 to v3.
- Training v3: start from scratch, templates, exercise library, freestyle
  workouts, program generator. Merged locally and being verified.
