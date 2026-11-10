# Handoff: where to pick up

Last updated 2026-11-10 by the coordinating session. If you are a new agent, read this first, then `CLAUDE.md`.

## 1. Where things stand

**Branches**
- `main` and `claude/app-build-k2ag1d` are identical, verified snapshots. The verify was 1273 unit tests passing and 0 lint errors at `9f2cf62` (Option A part 1). The commits after that only add docs.
- `claude/app-build-k2ag1d` is the integration branch. Work merges into it. After a verified push, `main` is fast-forwarded to it with `git push origin claude/app-build-k2ag1d:main`.
- Two work-in-progress branches are pushed. Both were interrupted by usage limits and may not compile:
  - `agent/nutrition-wip`: Option A part 2 (Nutrition tab). The brief is `docs/briefs/option-a-part2-nutrition.md`. Done so far:
    - drinks in the food database;
    - a unified drink log (hydration plus food rows);
    - the nutrition label and dashboard domain models;
    - the swap maths.
    The Nutrition UI screens are not done yet.
  - `agent/train-wip`: Option A part 3 (Train tab and Videos). The brief is `docs/briefs/option-a-part3-train.md`. Done so far:
    - the video classifier;
    - chapter parsing;
    - exercise matching;
    - weekly volume;
    - swap ranking;
    - PR logic.
    The UI and the YouTube client are not done yet.

**The app today**
- Navigation: Option A shell, with four tabs (Today · Nutrition · Train · Progress) and You behind the avatar.
- Today is rebuilt, and You has progress photos.
- Settings has 12 groups and 113 controls.
- Sync and export replaced the remote backups.
- The weekly check-in and the allergens screen are each merged into one.
- The Nutrition and Train tabs still show their old contents until parts 2 and 3 land.

**Design source of truth**
- `design/mockup-v5/option-a.html`, plus the frames in `design/mockup-v5/shots/a-*.png`.
- The user chose Option A ("probably the best so far"). They plan per-page adjustments after the build.
- Spec: `docs/IA-v5.md`, where §9 wins over earlier sections.
- `docs/FEEDBACK-R2.md` holds the decisions, including that Videos uses a key.
- `docs/SETTINGS-INVENTORY.md` lists every setting.

## 2. What to do next, in order

1. **Finish part 2 (Nutrition) and part 3 (Train and Videos).** They can run in parallel, but only one Gradle build at a time on a small machine. For each branch:
   1. `git checkout agent/<x>-wip`
   2. `git merge claude/app-build-k2ag1d`
   3. Read its brief and the last WIP commit, then finish it.
   4. Run the full verify (see `CLAUDE.md`).
   5. Merge into `claude/app-build-k2ag1d`. If both branches touch `Migrations.kt` or `schemas/.../3.json`, keep every `migrateXxx_2_3()` step in `MIGRATION_2_3_STATEMENTS`. Rebuild so KSP regenerates `3.json`; do not hand-merge it. Re-verify.
   6. Push, then fast-forward `main`.
2. **Build debug APKs** (`:app:assembleDebug :wear:assembleDebug`). Copy them to `release/intake-phone-debug.apk` and `release/intake-wear-debug.apk` (that folder is git-ignored), write `release/SHA256SUMS-debug`, and send them to the user.
   - Tell the user to **uninstall or clear data on the old debug app first**. The v3 schema keeps changing during development, and a debug install of an older v3 will crash on the new one.
   - Their real app (`com.zeroindex.intake`, schema v2) is separate and unaffected.
3. **Per-page adjustments.** The user will review each page and style on the phone and send changes. Record them in a new `docs/FEEDBACK-R3.md`, then implement.
4. **Backlog**, from `docs/FEEDBACK-R2.md` and `docs/TASKS.md`:
   - **Open Food Facts nutrition-label photo upload.** Real photos only, through the OFF image upload API (`imagefield=nutrition`), with the user's confirmation. We do not generate label images.
   - **Restaurant menus "like Google shows".** Google has no public API and scraping it is against its terms. Do a web search through OpenRouter on tap to find the restaurant's own menu page or PDF, then run it through the existing menu pipeline. Say so plainly when nothing is found.
   - **Training depth**, per `docs/research/LIFTOSAUR-NOTES.md`:
     - a specific equipment taxonomy (about 45 items, with a "what I own" list);
     - a diagram or icon for every piece of equipment (original art, or MIT/ISC icon sets);
     - weighted muscles, if part 3 used the fallback;
     - graphs and PR lists;
     - supersets;
     - a program "playground".
   - A notification for the progress-photo reminder (today it only shows as a line on You).
   - "Find anything" search (deferred by the user).
   - Shared-element transitions, a watch rest ring, Health Connect HRV for the coach, and better ingredient groups for mixed recipes.
5. **Release.** The user will generate a new signing keystore. The first release signed with it will not install over the existing app. The user must export, uninstall, install the new build, then import. Say this when you hand over that build.

## 3. How the user likes to work

- **Platform.** The user runs Linux. Give Linux instructions unless they ask otherwise.
- **Delegation.** They want the coordinator to write plans and hand the work to subagents: Haiku for mechanical tasks (verify, push, build APKs), Sonnet for building and design.
- **Autonomy.** Work on your own. Only report things that need the user's hands or a decision. Be honest about what has not been verified on a device. There is no emulator in the cloud container.
- **Pushing.** Commit and push after each verified piece. Never push unverified code to `main`. Keep `main` a tested snapshot.
- **Personal files.** The user's real meal plan is `release/meal-plan-12-week.md`, which is git-ignored. Never commit it.
- **Product principles.** Smartest yet simplest:
  - simple everyday screens, and depth in Settings;
  - one home per feature, with no cross-links;
  - no AI unless the user taps for it;
  - allergen safety is deterministic: nothing can clear a warning.
- **Mockups first.** For big UI changes, mock up before building. The user likes being shown options.

## 4. Environment notes (cloud container)

- **SDK and Gradle.**
  - The Android SDK is at `/root/android-sdk`, with platform 37 and build-tools 37.0.0.
  - `local.properties` holds `sdk.dir` plus a container-only signing key. The user decided not to keep that key.
  - Gradle runs `--offline` with `--no-parallel --max-workers=1`, because Maven Central returns 429 to Gradle here. Missing jars were seeded into `~/.m2` with curl, and an init script in `~/.gradle/init.d/` adds `mavenLocal()`.
- **New container.** In a fresh container, recreate the SDK, `local.properties` and the `~/.m2` seeding (`CLAUDE.md` has the recipe). Use debug builds unless the user provides a key.
- **Memory.** Running more than two Gradle builds at once ran this 15 GB machine out of memory.
- **Usage limits.** Subagents hit the account's 5-hour usage limit several times. Tell agents to commit WIP checkpoints often. A worktree that was never committed can be lost when the container restarts.
- **Playwright.** It is pre-installed, so `require('playwright')` works. Use it to render HTML mockups to PNG.
