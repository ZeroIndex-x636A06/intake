# Brief: Option A part 3, the Train tab and Videos

**Status:** in progress on branch `agent/train-wip`. That branch holds WIP commits that may not compile. So far they contain domain logic only: the video classifier, chapter parsing, exercise matching, weekly volume, swap ranking and PR logic. Merge `claude/app-build-k2ag1d` into it, finish the work, verify, then merge it back. See `docs/HANDOFF.md`.

The user's brief: "Train menu is too focused on creating new programs. It should be focused on training itself and then have the program editor and management in a menu within train… Find examples to base it around."

## Read first
- `CLAUDE.md` and `docs/HANDOFF.md`.
- `docs/FEEDBACK-R2.md`, including its final section, "Decision: Videos use a key".
- `docs/IA-v5.md`: the Train section, §9, and the adopted Train patterns.
- `docs/research/LIFTOSAUR-NOTES.md`. Liftosaur is AGPL-licensed, so use it for concepts only and never copy from it.
- `docs/SETTINGS-INVENTORY.md`, `docs/TASKS.md` (Part 1's handover), `RESUME.md`, `docs/EXERCISE-IMAGES.md` and `docs/WORKOUTS.md`.
- The mockup: `design/mockup-v5/option-a.html` and the frames `design/mockup-v5/shots/a-train.png`, `a-workout.png`, `a-summary.png`, `a-videos.png`, plus their versions in `shots/full/`.

## Part 1 handover
- **Route placement.** Screens that only Train opens go inside the Train graph. Screens two tabs share go once at the root of `IntakeNavHost`. Wrap screens in `PushedScreen` or `TabRootScreen`.
- **No settings on use screens.** A new setting goes in the inventory, `SettingsRegistry` and `SettingsControls.kt`.
- **Existing routes.** `Route.Train` and Programs are unchanged. `ActiveWorkout`, `ProgramBuilder`, `ProgramGenerator` and `Performance` are root routes.
- **Already in Settings.** The Videos settings (workouts-only, personalise) and the YouTube key are done. Equipment and the training defaults are in Settings → Training.
- **Duplicate titles.** The old Train and program screens still show their own headings under the new top bar. Remove those duplicates.
- **Settings to consume:** `weeklySets` (targets for the volume view), `onlyWorkouts`, `personalise` and the YouTube key.

## What to build (match the Option A frames)

### 1. Train home: Workout · Exercises · Videos
Three segmented tabs.

**Workout tab**
- **Today's session card:** program day, week and day, the exercise list, estimated duration, and a big Start workout button. It follows the weekday schedule. On a rest day it says "Rest day, next: …" and offers "Train anyway".
- **"Other ways to start":**
  - Empty workout
  - From a program (pick any day)
  - My workouts
  - From a video
  - Repeat last
- **This week's muscle volume:** weekly hard sets per region against the `weeklySets` targets, counted by calendar week. Show it on a body map with 6 regions, labelled in plain words (under, in range, over). Tapping a region shows the exercises that contributed to it.
- **Recent sessions:** the last 3–5, plus "All sessions".
- **Programs entry:** from the top-bar menu or a row.

**Programs area**
- Lists: active, other and archived programs.
- Create a program: build, generate, template, or import a script.
- Per program: edit, duplicate, archive, export as a script.
- My workouts management.
- Move the builder, generator and templates here, off the Train root.

### 2. Active workout
- **Header:** elapsed time, sets done out of total, Finish.
- **Exercise card:** a thumbnail and name. Tapping it opens the detail sheet; there is also a Form button.
- **Set rows:** Set · Previous · Weight · Reps · ✓.
  - Previous is the same set from the last session.
  - Rows are prefilled from the prescription.
  - Tapping ✓ logs the set, plays the check animation and a haptic, and starts the rest timer.
- **Rest strip:** a countdown ring, +15 s, and Skip.
- **Plate math line** for barbell lifts, using the plate settings.
- **"+ Add set"** and an **Up next** list.
- **Swap exercise:** rank alternatives by muscle similarity, then by the equipment the user owns. Carry the weight over from history, or use the conservative starting estimate.
- **Live PR flag.**
- **Minimised session bar** on tab roots, so the user can resume.
- Keep the watch Data Layer feed working (`wear/`, `WearBridge`).

### 3. Post-workout summary
- New records.
- Time, volume, sets and PRs.
- Muscles worked: this session's sets against this week's target, shown on the muscle map.
- The exercise list.
- "Save as workout" for freestyle sessions, and Done.
- Next-time changes from the progression rule.

### 4. Exercises tab
Restyle the existing library to match the frame: search, filters, thumbnails, the detail sheet, and "Add your own exercise". Remove the duplicate titles.

### 5. Videos (key-based, as `docs/FEEDBACK-R2.md` specifies)
**Search**
- Call `search.list` with `type=video`, `videoEmbeddable=true`, `safeSearch=moderate`, then `videos.list` with `part=snippet,contentDetails,statistics`.
- The key comes from `ApiKeyStore` and is sent as the `key` parameter. This is the one documented exception to the "keys never in URLs" rule. Never log it.

**Caching**
- `video_cache` with a TTL, and `saved_video`, both added in a v3 `migrateVideos_2_3()`.
- Purge cached API data after 30 days, as the YouTube API terms require.

**Workout filter**
- A deterministic classifier in `domain/videos/`. The WIP branch has a first version.
- Signals:
  - category 17 or 26;
  - workout, exercise and catalog keywords;
  - negative keywords (reaction, vlog, music, podcast, unboxing, news, meme, prank, gameplay);
  - duration;
  - channel.
- It returns a score and a reason.
- The default comes from Settings. "Show everything" applies to the current search only.

**Recommended**
- When the personalise setting is on, build queries from local data only: under-target muscles this week, equipment, program focus, saved videos. Show the reason on each row.
- When it is off, use generic queries.

**No key set**
- Explain what the key is for, and link to Settings → API keys and the help.
- Offer "Paste a YouTube link", using oEmbed: `https://www.youtube.com/oembed?url=…&format=json`.

**Playback**
- Use the official IFrame player in a WebView (`youtube-nocookie.com/embed/<id>`), with JavaScript enabled for that view only.
- Never download.
- Show YouTube attribution.

**"Make this a workout"**
- First, parse the chapters in the description deterministically. Fuzzy-match them to the catalog, with tests.
- Optional "Improve with AI" on tap, through `AiGateway`, with `AiFeature.VIDEO_TO_WORKOUT` and a `USER_ACTION` trigger. Update the `AiAuditTest` table and `docs/AI-COSTS.md`. Use the strict schema and repair path.
- The user reviews the result, then saves it as a My workout linked to the video.

**Docs**
- `docs/PRIVACY.md` (YouTube calls, key, caching, terms).
- A new `docs/VIDEOS.md`.

### 6. Weighted muscles (from the Liftosaur study, original data only)
Preferred: an `exercise_muscle` table with weights 1.0 / 0.5 / 0.25, seeded from general training knowledge. Fallback: primary = 1 and secondary = 0.5 from the existing fields. Say which one you built.

## Tests
- Classifier
- Chapter parsing and exercise matching
- Recommended query builder
- Cache TTL and purge
- Weekly volume, including fractional synergists
- Swap ranking
- Plate math
- Previous-set lookup
- PR detection
- Summary deltas
- Migrations
- `NavGraphGuardTest`

## Docs
Update `docs/TASKS.md`, `RESUME.md`, `docs/SETTINGS-INVENTORY.md` and `docs/WORKOUTS.md`.

## Constraints
- AI only on an explicit user action, through the gateway.
- No hard deletes.
- No model identifiers in commit message bodies.
