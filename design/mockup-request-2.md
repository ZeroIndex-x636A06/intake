# Intake — mockup request, round 2

Paste everything below the line into the same chat that produced
`intake-mockup.html`.

---

Extend the Intake mockup with the screens below. Keep **exactly** the same
design system as before: Manrope for headings, DM Sans for body, the same
tokens (`--primary #1F5D50`, `--pale`, `--sand`, `--clay`, the fixed protein /
carb / fat accents `#2E7D6B` / `#B08415` / `#9A5540`, allergen red `#9F3026`
and amber `#825014`), the same 22px card radius, hairline outlines, 48px
minimum touch targets, and light + dark frames side by side. Add them to the
same gallery as new groups. Use realistic content: the user is a lifter with a
strict dairy allergy, eating around 2,040 kcal and 190 g protein a day, and
displays weight in pounds.

Every screen needs its **empty**, **loading** and **error** state shown at
least once somewhere in the gallery, not only the happy path.

## 1. Today additions
- **Hydration card** on Today, below the macro ring: progress toward a daily
  water target (e.g. 1,850 / 3,000 ml), with 3–4 one-tap preset buttons
  (250 ml glass, 500 ml bottle, 750 ml shaker, custom). Tapping the card opens
  a sheet to edit presets and the target, and shows today's entries with undo.
  The target can be "dynamic" (raised on training days) — show the reason
  ("+500 ml · training day").
- **Daily insight card**: one short AI-written actionable suggestion
  ("You're 40 g under protein three days running and your squat RPE is
  climbing — add a protein-heavy snack before tomorrow's session."), with a
  small "why" disclosure listing the data it used, and a dismiss.

## 2. AI model picker (Settings → AI)
- One row per task: Photo estimates, Recipe Hunter, Plan B finder, Menu
  import, Daily insight.
- Each shows the chosen model, its cost per request in plain money
  ("≈ $0.0004 / photo"), and a **Suggested** badge on the recommended one.
- Tapping opens a picker sheet: model list filtered to ones that can do that
  task, each with price, a "Free" tag, and a clear warning tag on models whose
  provider may retain prompts ("May keep your prompts").
- A "Benchmark results" section: a small table of accuracy / JSON validity /
  speed / cost per model per task.
- API key field for OpenRouter, with test button and connected state.

## 3. Recipe Hunter
- A search form: macro goal per serving (e.g. ≥ 40 g protein, ≤ 600 kcal),
  servings, max prep time, cuisine chips, and a locked "Dairy-free" chip that
  cannot be turned off.
- A working state that shows the agent's progress honestly ("Searching… ·
  Reading 6 recipes… · Checking ingredients against your allergens…").
- Results list: recipe cards with macros per serving, source site, and a
  status pill — **Passed allergen check**, or **Removed: contains butter** for
  ones the local engine rejected (show at least one rejected card, greyed).
- Recipe detail: ingredients (dairy matches would be highlighted like the
  allergen sheet), steps, macros, "Save to my recipes", "Add to plan".

## 4. Plan B fast food finder
- Location permission prompt explaining why, with "Use zip code instead".
- Results: a list of nearby restaurants, each with a best-fit order, macros,
  distance, a fit score against what's left today ("Fits: 42 g of your 58 g
  protein left"), and dairy notes. Keep the existing "Say this when ordering"
  quote block and the cross-contact caution.
- **Landscape tablet / car head-unit layout** (≈ 1280×720): two-pane, list on
  the left, detail on the right, extra-large touch targets, readable at arm's
  length.

## 5. Menu import
- A list of menu sources (restaurant name, URL or PDF, last imported, item
  count, status: OK / Failed / Never run) with an "Add source" action
  (paste URL, pick PDF, paste text).
- An import run screen showing parsed items with macros and allergen flags,
  letting the user accept or reject before anything is saved.
- A failure state that names the reason ("This site blocks automated
  access").

## 6. NFC meal prep
- Meal templates list with an NFC chip icon on templates written to a tag.
- "Write to tag" flow: hold-phone-near-tag illustration, success state,
  "tag is read-only / too small" error.
- The confirmation toast/snackbar shown after tapping a tag logs a meal, with
  Undo.

## 7. Explosive performance
- Entry form: vertical jump, standing reach, total jumping reach (in inches),
  date, optional note. Show that total reach is computed from the other two.
- Progress tab card: jump height trend overlaid on weekly training volume
  (two y-axes or a normalised overlay), with a legend.

## 8. Health Connect
- Connection screen listing each data type with its own permission state:
  active calories, body fat, skeletal muscle, sleep, resting HR, heart rate,
  steps, weight.
- A "How active calories are counted" explainer: raw burn, minus BMR for that
  window, equals what's added — with a worked example.

## 9. Weekly check-in report
- In-app preview of the report with a "Save PDF" and "Print" action and a
  week picker.
- **The PDF pages themselves** at US Letter portrait: week-at-a-glance
  averages (weight, intake, burn, sleep, resting HR, steps); daily macro bar
  charts (protein, carbs, fat) with a table of calories / fiber / sugar;
  calories in vs burned; body-weight trendline; sleep duration and
  consistency; resting vs all-day heart rate; training summary (volume,
  exercises, sets × reps, notes). It must read well printed in black and
  white.

## 10. Backups and sync
- Backup settings: destination type (WebDAV / SFTP / SMB), host, path,
  username, password/key, "Test connection", schedule (nightly), last
  backup time and size, and a failed-backup state.
- Sync settings: server URL, pair-this-device flow (QR code or code entry),
  list of paired devices with last-seen, sync status ("Up to date · 2 min
  ago"), and how a conflict is shown to the user if one ever needs a choice.

## 11. Wear OS companion (round watch, 450×450)
- Current exercise with the prescribed weight × reps.
- Set logging: rep count with +/− and a big ✓, RPE picker (6–10 in halves).
- Rest timer: full-screen countdown ring, +30 s, skip.
- "Phone not connected" state.
- Use the same palette on a true-black background.

## 12. App-wide large-screen layout
- Today and Progress in landscape/tablet with a navigation rail instead of the
  bottom bar, and two columns of cards.
