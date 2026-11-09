# Task board

The brief is `docs/SPEC-v3.md`. To claim a task, put your branch name next to it
and push that change before you start. When you finish, move the task to Done
with its merge commit. Read `CLAUDE.md` before you claim anything.

## In progress

**Claimed by the coordinating session**

- **Hydration v2** (open task 2 below): weather, drink factors, caffeine, sweat.
- **Coach engine** (open task 3 below), plus putting settings and the profile into backup/export.

## Open: anyone can claim these

Each task links to the section of `docs/SPEC-v3.md` that defines it.

1. **Onboarding and Today tour.**
   - Task "Profile, baseline week and goal builder" is done, so reuse its pieces:
     `ProfileScreen` and its small composables (`OptionTile`, `ChipGroup`, `CommitField`
     in `ui/screens/profile/`), `ProfileRepository` / `UserProfile` for the "about you" and
     "goal and phase" steps, `startBaseline()` and `BaselineSnapshot` for the baseline offer, and
     `YourPlanScreen` as the "your plan" step. Everything saves as it is edited, so a welcome step
     can embed the same sections and simply skip.
   - A baseline can be started from the welcome flow with `ProfileRepository.startBaseline()`; ask
     for the notification permission the same way `ProfileScreen` does.
   - **Welcome flow:** units, about you, goal and phase, foods you avoid (My
     allergens), your plans (import / build / generate), optional extras. Every
     step can be skipped. Installs that already have data skip the flow.
   - **Coach-mark tour on Today.**
   - **One-time tips** on each tab.
   - **Settings:** add "Replay the tour". The Help screen already has a
     placeholder for it.
2. **Hydration v2.** See "Additions → Hydration v2".
   - Opt-in Open-Meteo weather (rounded location or a typed city).
   - Drink hydration factors.
   - Caffeine in mg, plus an alcohol top-up.
   - Sweat loss from workouts, with an optional measured sweat rate.
   - Wording must be honest: tea and coffee are not dehydrating at normal
     amounts.
3. **Coach engine.** See "Additions → Coach engine".
   - Weekly check-in suggestions from deterministic rules, each with a "why",
     covering weight trend vs pace, adherence, strength stalls, recovery
     signals, protein/water gaps and volume per muscle. The user taps Apply or
     Dismiss.
   - A daily "one thing to do today" card on Today.
   - AI narrative is optional and only rewords the suggestions.
4. **Motion and clarity polish.** See SPEC-v3 point 5.
   - Motion tokens already exist in `ui/motion/Motion.kt`.
   - Animate the calorie ring sweep and count-ups, the water wave, protein-goal
     confetti, set and item check-offs, the rest-timer ring, chart draw-in and
     shimmer loading.
   - Add a "Reduce motion" setting.
   - Rewrite jargon as InfoTips (`ui/components/InfoTip.kt` exists). Give each
     screen one primary action, and make every empty state say what to do next.
5. **AI policy audit, usage meter and cost analysis.** See "Additions → AI
   policy and cost". Do this last, once the AI features are final.
   - Audit every OpenRouter call site.
   - Show calls and spend per feature.
   - Add a monthly budget cap.
   - Estimate daily and monthly cost from the chosen models, and write
     `docs/AI-COSTS.md`.
6. **Small follow-ups.**
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
- A new signing key will be generated for the next release. Export a backup, uninstall, install, then import.
- Optional: deploy the sync server (`server/README.md`), add an OpenRouter key
  and run the benchmark, and set a backup destination.

## Done (v3)

- Navigation: floating pill, Food/Train/Progress/More hubs, split script docs
  (4485a76).
- Allergens v2: 15 built-in allergens, custom allergens and keywords,
  negation-aware matching, a new import preview (7eeaff5). Its schema edits now
  live in `MIGRATION_2_3` (see Schema repair).
- Schema repair: 3c7600b. v2 is restored byte-for-byte to 4952404.
  The allergen columns and `allergen_definition` moved into `migrateAllergens_2_3`,
  next to `migrateFoodOff_2_3`. Other agents add `migrateXxx_2_3` functions to
  `MIGRATION_2_3_STATEMENTS`. `MigrationSchemaTest` upgrades the released v2 to v3.
- Profile, baseline week and goal builder (4f4c902). More → Profile & goals; the baseline week with a daily WorkManager check and one
  notification; "Your plan" with a why, an (i) and an Edit on every number; Today's baseline and
  workout cards. Pure engine in `domain/goals/`, formulas in `docs/GOALS.md`. No schema change.
  The Hydration v2 task should add `WaterAdjustment` lines to `WaterPlanner`; the Coach engine can
  reuse `GoalBuilder` with `PlanOverrides` and `ProfileRepository.applyPlan`.
- Training v3: start from scratch, templates, exercise library, freestyle
  workouts, program generator (05b7466).
- Food database, barcode fixes, Open Food Facts search and contribution (ede46d6).
- Flexible meal plans, recipe editor, recipe import from a link (71e4911).
