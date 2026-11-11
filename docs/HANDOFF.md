# Handoff: where to pick up

Last updated 2026-11-11 by the coordinating session. If you are a new agent, read this first, then `CLAUDE.md`.

## 1. Where things stand

**Branches**
- `claude/optimistic-noether-fullk5` holds the latest work, head `06871e3`. Round 3 (phone feedback) is merged on it; see
  `RESUME.md` (first section) and `docs/R3-BACKLOG.md`. Verified there: 1599 unit tests, 0 lint errors (app and wear).
- `main` and `claude/app-build-k2ag1d` still need the merge. This session can only push its own branch, so the merge into
  `claude/app-build-k2ag1d` (then `main`) is still to do. Re-verify after merging.
- `agent/nutrition-wip` and `agent/train-wip` are fully merged and can be deleted.

**The app today**
- Navigation: Option A shell, four tabs (Today · Nutrition · Train · Progress) and You behind the avatar.
- Nutrition and Train are rebuilt to the Option A frames; Videos is in Train. What the briefs asked for and was left out is
  listed in `RESUME.md`.
- The main flows have been walked on an emulator (list in `RESUME.md`). Nothing has been seen on a real phone yet.
- Builds: `:app:assemblePreview` makes "Intake Preview" (`com.zeroindex.intake.preview`), which installs beside the released
  app and beside a debug build.

**Design source of truth**
- `design/mockup-v5/option-a.html`, plus the frames in `design/mockup-v5/shots/a-*.png`.
- The user chose Option A ("probably the best so far"). They plan per-page adjustments after the build.
- Spec: `docs/IA-v5.md`, where §9 wins over earlier sections.
- `docs/FEEDBACK-R2.md` holds the decisions, including that Videos uses a key.
- `docs/SETTINGS-INVENTORY.md` lists every setting.

## 2. What to do next, in order

1. **Owner tests the preview on the phone.** The preview APK and `release/SHA256SUMS-debug` are in `release/` (git-ignored).
   The preview app has its own data, so it does not touch the installed app. A preview built in another container is signed
   with a different debug key: uninstall the old preview before installing a new one.
2. **Owner picks a food layout option.** The mockup is `design/mockup-r3/food-layout.png` (options 1, 2 and 3). Build only after
   the choice (`docs/R3-BACKLOG.md`, item F).
3. **Merge to the integration branch and `main`.** Merge `claude/optimistic-noether-fullk5` into `claude/app-build-k2ag1d`, run the
   full verify, push, then fast-forward `main`.
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
- **Autonomy.** Work on your own. Only report things that need the user's hands or a decision. Be honest about what has not been verified on a device. An emulator can be run in the cloud container (section 4), but it is slow and is not a real phone.
- **Pushing.** Commit and push after each verified piece. Never push unverified code to `main`. Keep `main` a tested snapshot.
- **Personal files.** The user's real meal plan is `release/meal-plan-12-week.md`, which is git-ignored. Never commit it.
- **Product principles.** Smartest yet simplest:
  - simple everyday screens, and depth in Settings;
  - one home per feature, with no cross-links;
  - no AI unless the user taps for it;
  - allergen safety is deterministic: nothing can clear a warning.
- **Mockups first.** For big UI changes, mock up before building. The user likes being shown options.

## 4. Environment notes (cloud container)

A fresh container has none of this; the last session rebuilt it in about 20 minutes.

- **SDK.** Command-line tools into `/root/android-sdk`, then `sdkmanager "platform-tools" "platforms;android-37.0"
  "build-tools;37.0.0"`. `local.properties` holds only `sdk.dir=/root/android-sdk`. There is no signing key; build debug or
  preview.
- **Maven Central returns 429 to Gradle here, but not to curl.** Run a small caching mirror on `127.0.0.1` that fetches
  `repo.maven.apache.org` with curl, and a Gradle init script (`~/.gradle/init.d/`) that points every
  `repo.maven.apache.org` repository at it (plugin management, dependency resolution and buildscript). Then Gradle works
  online with no `--offline` and no hand-seeded jars. Both files are in `tools/cloud/`, with the emulator driver.
- **Gradle.** `--no-parallel --max-workers=2` (3 when nothing else runs). When several agents share the machine, run every
  build through one lock: `flock /root/gradle.lock ./gradlew ...`.
- **Emulator.** There is no KVM, so it runs on software emulation: `sdkmanager "emulator"
  "system-images;android-30;default;x86_64"`, an AVD from that image, and
  `emulator -avd <name> -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -accel off`.
  - Android 15 images are too heavy (the system process is killed for being slow), and the ATD images give black screenshots.
  - Boot takes about 5 minutes, and system services restart once after boot; wait until they have been up for a minute.
  - Turn animations off, set `hide_error_dialogs`, and after each install run
    `adb shell cmd package compile -m speed -f com.zeroindex.intake.debug`.
  - Drive it with `adb shell input` and `uiautomator dump`; screenshots with `adb exec-out screencap -p`. One action every
    10 to 20 seconds. Queued input makes Android kill the app as not responding, which is the emulator, not the app; a
    real hang shows the same way, so read `/data/anr` (needs `adb root`) before deciding which it was.
  - Do not build while testing: Gradle starves the emulator. Start it detached (`setsid`), or it dies with the shell.
- **Usage limits.** Subagents hit the account's 5-hour usage limit several times. Tell agents to commit WIP checkpoints
  often; a stopped agent can be resumed with a message once the limit resets.
- **Playwright.** It is pre-installed, so `require('playwright')` works. Use it to render HTML mockups to PNG.
