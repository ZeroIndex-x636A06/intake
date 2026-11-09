# Task board

The brief is `docs/SPEC-v3.md`. To claim a task, put your branch name next to it
and push that change before you start. When you finish, move the task to Done
with its merge commit. Read `CLAUDE.md` before you claim anything.

## In progress

**Claimed by the coordinating session**


## Open: anyone can claim these

Each task links to the section of `docs/SPEC-v3.md` that defines it.

4. **AI policy audit, usage meter and cost analysis.** See "Additions → AI
   policy and cost". Do this last, once the AI features are final.
   - Audit every OpenRouter call site.
   - Show calls and spend per feature.
   - Add a monthly budget cap.
   - Estimate daily and monthly cost from the chosen models, and write
     `docs/AI-COSTS.md`.

## Needs the user

- Test on real devices. None of the v3 UI has been seen on a screen yet. Check:
  - the floating pill and its slide gesture;
  - barcode scanning on the release build;
  - the new screens.
- A new signing key will be generated for the next release. Export a backup, uninstall, install, then import.
- Optional: deploy the sync server (`server/README.md`), add an OpenRouter key
  and run the benchmark, and set a backup destination.

## Done (v3)

- Motion and clarity polish and the small follow-ups (branch `worktree-agent-ac43c051116cef0e9`; see `docs/DESIGN-MOTION.md`):
  reduce-motion setting, ring sweep and count-up, protein burst, water wave, stagger, check morphs, rest ring, chart
  draw-in, shimmer, press feedback, per-screen declutter, Settings regrouped (duplicate Allergens removed, Replay the tour added),
  AMRAP label, mentioned-as-avoided notes, `ProgramEdits` tests. No schema change. Not seen on a device. Skipped: shared-element
  transition and the watch rest ring.
- Onboarding and Today tour: nine-step welcome flow, auto-skip for installs with data, Today coach marks, first-visit
  tips per tab, Help replay rows (branch `agent/onboarding-tour`; see `RESUME.md`). No schema change. Not seen on a device.
- Coach engine, "One thing to do today" card, and settings/profile in backup and export (branch `agent/coach-engine`).
  Rules and thresholds in `docs/COACH.md`; backup keys in `docs/BACKUP.md`. New table `coach_suggestion` in
  `migrateCoach_2_3`. Not seen on a device.

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
- Profile, baseline week and goal builder (4f4c902). More → Profile & goals; the baseline week with a daily WorkManager check and one
  notification; "Your plan" with a why, an (i) and an Edit on every number; Today's baseline and
  workout cards. Pure engine in `domain/goals/`, formulas in `docs/GOALS.md`. No schema change.
  The Hydration v2 task should add `WaterAdjustment` lines to `WaterPlanner`; the Coach engine can
  reuse `GoalBuilder` with `PlanOverrides` and `ProfileRepository.applyPlan`.
- Training v3: start from scratch, templates, exercise library, freestyle
  workouts, program generator (05b7466).
- Food database, barcode fixes, Open Food Facts search and contribution (ede46d6).
- Flexible meal plans, recipe editor, recipe import from a link (71e4911).
