# Device feedback, round 2 (2026-11-10)

This file collects the user's second round of testing on a real phone, with the decisions that follow from it. Where `docs/IA-v4.md` and this file disagree, this file wins. Agents: read this before working on any item listed here.

## Ground rules from the user

- **Using screens versus configuring screens.** Food, Train and Today are for *building and using*. Every setting that configures behaviour lives in **Settings** and nowhere else. The screens you use every day stay simple. Settings can be denser, because you rarely go there.
- **No duplicate entry points and no cross-links.** Each feature has exactly one home.
- **Food and hydration form one area.** Drinks are foods. They come from the same database and are logged from the same place.
- **Pushed screens behave like normal screens.** Every pushed screen has a top bar with a back arrow. The open and close transitions must look natural, without odd shrink or fade combinations. Top insets are correct everywhere, and nothing sits under the floating pill.

## Bugs

| # | Bug | Notes |
|---|---|---|
| B1 | AI fails in several places, including photo estimate: timeouts and unusable output | Vision and web-search calls need longer timeouts. Use strict structured output (JSON schema / `response_format`) where the model supports it, with schema validation, one repair retry, then a clear error. Never show raw model text. |
| B2 | The photo estimate screen has odd text and always uses grams | Display units must follow Settings. Fix the text layout. |
| B3 | Barcode says "camera could not start" after the photo estimator was opened | Two camera users at once. Release CameraX and the camera intent properly on leave, and share one camera owner. |
| B4 | Importing the Arby's PDF from a link froze the app | Example link: https://assets.ctfassets.net/30q5w5l98nbx/6CplhZeKpu7X9ts2pSE3Lg/624b24407875e1dbdf6f07f0da28897e/Arbys_Nutritional_and_Allergen_NOV_2026.pdf. Do all work off the main thread. Add size limits, progress and a Cancel button. For a large PDF, send it to OpenRouter's PDF input (the text-extraction engine) in pages or chunks instead of one huge request. A nutrition and allergen table PDF should produce items with macros and allergens, and every result still goes through the local allergen engine. |
| B5 | The menu close animation looks wrong, and there is no back arrow | See the ground rules. |
| B6 | Health Connect is connected, but Sync pulls in nothing | Check the read time ranges, permissions actually granted versus requested, the change token versus a full read on first sync, record types, the data origin filter, and the UI refresh after sync. Show "Last synced: N records" for each type. |
| B7 | AI benchmark problems | See `docs/TASKS.md` → "AI benchmark fixes": 429 handling, privacy-compatible candidates, no free tiers, compact results, insets. |

## Simplifications

- **One allergen screen.** Merge Allergen reference and My allergens: your allergens and keywords on top, then the hidden-ingredient reference below them.
- **One weekly check-in.** Merge the weekly report and the check-in into a single screen in Progress: summary, suggestions, and an Export/Print PDF action.
- **Sync & export.** Keep server sync and local JSON/CSV export and import. Remove the remote WebDAV, SFTP and SMB backups.
- **Train is about training.** The Train root shows today's workout, starting a workout, recent sessions and quick stats. Program creation, editing and management move into a "Programs" menu inside Train. Base the layout on established lifting apps (Strong, Hevy, Liftosaur), studied for patterns only; do not copy their assets.
- **Progress** is good as it is. Keep it, plus the merged weekly check-in.

## Feature changes

### Today and Food
- Today replaces "More detail" with three always-visible macros: protein, fat and carbs.
- The Food area shows the full nutrition dashboard: calories, protein, carbs, fibre, fat, saturated fat, sugar, salt or sodium, plus micronutrients when they are known.
- Any food looked up by search or barcode shows its **nutrition label**, rendered in the familiar label layout from the stored per-100 g values and scaled to the chosen portion.

### Hydration, folded into Food
- Drinks come from the same database as food. Browse by category; for example "Soda" lists all the sodas.
- Hydration counts water volume *and* nutrition: sugar, caffeine, calories and alcohol all count.
- Quick buttons for common drink sizes: 8 oz glass, 12 oz can, 16.9 oz bottle, 20 oz bottle, 250/330/500 ml. Show them in the user's units.
- Drinks get a full page inside Food. The Today water card only adds or removes drinks already logged today.

### Open Food Facts contribution
- The user asked for the app to "build the nutrition label and submit it with the food".
- Decision: we already send the structured nutrition values, and Open Food Facts renders its own nutrition table from them.
- Open Food Facts product photos must be real photos of the product. A generated label image would be misleading, so we do not upload one.
- What we add instead: an optional **"Photograph the nutrition label"** step. The photo is uploaded as the product's nutrition image through the Open Food Facts image upload API, with the user's confirmation as today.

### Menus from Google
- The user wants restaurant menus like the ones Google shows (example: Prime Cut Steakhouse).
- Google has no public API for those menus, and scraping Google is against its terms.
- Approach: search for the restaurant using OpenRouter web search on an explicit tap, or with the user pasting the restaurant name or website. Then find its own menu page or a PDF, and import that through the existing menu pipeline (schema.org Menu first, AI only for unstructured pages).
- Tell the user plainly when no menu can be found.

### Videos (new)
- A section to search, pull and watch workout, pilates and exercise videos.
- It has a Recommended page.
- Non-workout results are filtered out automatically. A one-tap "Show everything" override applies to that search only.
- **Parse a video into workout details.** Read the video's chapters and description deterministically first. Optional AI on an explicit tap turns the transcript or description into an Intake workout (exercises, sets, reps, timings) that the user reviews before saving.
- Source: the YouTube Data API v3, which needs a key the user supplies, stored like the other keys. Playback uses the official embedded player.
- Never download or rehost videos.
- The filter is a deterministic classifier over category, title, description and duration, with tests.

### Profile
- Progress photos, recorded in `docs/IA-v4.md` §8.

## Defaults for the open IA questions (`docs/IA-v4.md` §7)

The user has not answered these individually, so apply the recommended answers:
- 4 tabs, with "You" behind the avatar.
- Weight stays small on Today.
- Find-anything comes later.
- One equipment list.
- Keep the names Plan B and Recipe Hunter, with subtitles.
- Export passphrase is optional.
- A monthly export reminder only when sync is off.
- Jump tests are hidden until enabled.
- Exercise photos are bundled.
- Weekly volume uses calendar weeks.
- Show 6 regions with detail on tap.

Note that the user's "food and hydration combined" request changes the Food tab layout.

## Decision: Videos use a key (2026-11-10)

- The user chose the key-based approach. Videos uses the **YouTube Data API v3** with a key the user supplies.
- The key is entered in Settings → API keys. It is stored in EncryptedSharedPreferences like the other keys, and sent only as the request's `key` parameter to googleapis.com. That parameter is what the YouTube API expects. The key is never logged.
- Without a key, the Videos section explains what the key is for, links to the setup steps in Help, and offers "Paste a YouTube link" so a single video can still be added and played.
- Search: `search.list` (type=video, with the category and duration hints) followed by `videos.list`, which returns snippet, contentDetails (duration) and the category.
- Quota is shown in plain words in Settings: about 100 searches a day on the free quota. Results are cached locally to save quota.
- Workout filter: a deterministic classifier with tests. It uses the category (Sports, Howto & Style, People & Blogs combined with keywords), keywords in the title and description, the duration, and channel signals. A one-tap "Show everything" applies to the current search only.
- Recommended page: built from local data only (this week's under-trained muscles, equipment, saved videos), turned into search queries. It can be switched off in Settings.
- "Make this a workout": parse chapters and the description deterministically first, then optionally use AI when the user taps. The user reviews the result before it is saved as a workout.
- Playback uses the official embedded IFrame player in a WebView. Videos are never downloaded or rehosted. Follow the YouTube API Services Terms: attribution and branding, a privacy disclosure in `docs/PRIVACY.md`, and deleting cached API data within 30 days.
