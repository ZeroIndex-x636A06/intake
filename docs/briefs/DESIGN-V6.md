# DESIGN-V6: "Ink and Lime" visual redesign

Single source of truth for implementers. The owner drew a new mockup ("Intake 2.0"); this brief turns it into tokens,
components, a screen map and a build plan. It supersedes `design/mockup-v5` (Option A, Manrope/DM Sans, green/sand/clay)
for look and layout. `CLAUDE.md` and `docs/SPEC-v3.md` still win on behaviour.

- Mockup: `design/mockup-v6/` (`index.html`, `screens/*.html`, `source/*.dc.html`, `source/intake.css`, `source/canvas.json`).
  Open `screens/Today.html` etc. in a browser, or see the PNGs in `design/mockup-v6/shots/<Screen>.png` (412 px wide,
  full height; `Main`, `Map`, `Onboarding` are 1440 wide boards).
- Source of every value below: `design/mockup-v6/screens/intake.css` (identical to `source/intake.css`) plus the inline styles
  in the screens. The mockup is 390 px wide: **1 CSS px = 1 dp (or sp for text)**.
- The mockup is a picture, not a spec of behaviour. Where this brief and a screen disagree, this brief wins; where this
  brief and `CLAUDE.md` disagree, `CLAUDE.md` wins.
- Personal data in the mockup was neutralised (avatar initial and the profile line on You). Weights, measurements and
  food names in the screens are sample data only.

## Visual language in ten lines

1. Warm off-white ground, white cards, near-black "ink", and one acid-lime accent (`#D4F25A`). Name: "Ink and Lime".
2. Flat. Cards have no shadow and no border; only the dock, the live pill and a selected segment cast a shadow. No blur or glass anywhere.
3. Very round: cards 24 dp, sheets 30 dp top, buttons/chips/search/dock are full pills, icon tiles 14 dp.
4. Ink (black) is the primary button, selected pill and selected day; lime is the highlight (centre +, active tab, hero buttons, avatar, "important" card).
5. Space Grotesk 600 with tight tracking for titles and every number; Inter for everything read.
6. A dark "ink" hero card on Today (ring plus macro bars) and lime for the one thing that needs attention (baseline week, weekly check-in, tip).
7. One status language: green Fits, grey neutral, amber May contain / Check the label / Not checked, red Contains / Blocked, violet AI.
8. Macros are fixed colours: protein blue, carbs orange, fat violet, water cyan, sleep indigo, heart red.
9. Line icons, 22 dp, 1.8 stroke, round caps. Bar-chart marks are rounded 5/2 dp bars.
10. Floating black dock with four tabs (Today, Food, Train, Insights) and a lime centre + that opens a Quick log sheet.

---

## (a) Design tokens

### a.1 Colour

Light is `.phone`, dark is `.phone.dark` in `intake.css`. Both themes are drawn in CSS; the dark theme appears rendered on
`CookMode`. **Nothing in a.1 is "proposed" except the rows marked PROPOSED.**

| Token | Light | Dark | Role |
| --- | --- | --- | --- |
| `bg` (ground) | `#F3F4EF` | `#0C0F0E` | screen background |
| `card` | `#FFFFFF` | `#171B1A` | cards, lists, sheets, search field, icon buttons |
| `soft` | `#ECEEE7` | `#212725` | chips, segmented track, tiles inside cards, steppers, bar track, input of a set cell |
| `ink` | `#111514` | `#EEF1EC` | primary text, primary button, selected pill, switch track on, progress fill, snackbar bg |
| `inkOn` | `#FFFFFF` | `#0C0F0E` | text/icons on `ink` |
| `muted` | `#566060` | `#A3ACA9` | secondary text, eyebrows, unselected tab in a segmented control |
| `line` | `#E2E5DE` | `#29302D` | dividers, outlines, switch track off, sheet grabber |
| `accent` (lime) | `#D4F25A` | `#D4F25A` | centre +, active dock tab, avatar, lime cards, hero buttons, switch knob (on) |
| `onAccent` | `#111514` | `#111514` | text on lime. **Fixed in both themes.** Lime is never used as text on a light surface. |
| `dock` | `#111514` | `#232927` | floating tab bar |
| `scrim` | `rgba(10,12,11,.48)` | same | behind sheets and dialogs |
| `protein` | `#3D6BF5` | `#86A2FF` | protein |
| `carb` | `#E07A12` | `#FFA54F` | carbs; "over target" calorie bar |
| `fat` | `#8B5CF6` | `#BBA0FF` | fat |
| `water` | `#1A9BD7` | `#56C6F5` | water, hydration |
| `sleep` | `#5B5BD6` | `#A3A3FF` | sleep bars |
| `heart` | `#D64545` | `#FF8C8C` | resting heart rate line |
| `ok` / `okBg` | `#1A6E42` / `#DFF3E7` | `#6BD9A3` / `#12301F` | Fits, done set, "-" weight delta |
| `warn` / `warnBg` | `#7E4C00` / `#FFEFCC` | `#F7BE55` / `#33270F` | May contain, Check the label, Not checked, Due |
| `bad` / `badBg` | `#A8231A` / `#FCE4E1` | `#FF9384` / `#3B1714` | Contains, Blocked, Discard, Log it anyway |
| `ai` / `aiBg` | `#4E33C9` / `#ECE8FE` | `#BDAEFF` / `#241D46` | every AI label and AI card edge |
| Hero card (`inkc`) in dark | n/a | PROPOSED `#1B211F` with `onHero` `#EEF1EC` | see note |

Notes:

- In the mockup the hero card is `background: var(--ink)`. In light that is near-black with light text; in dark `ink` becomes
  near-white, which would put lime on white. The mockup does not draw dark Today. **PROPOSED:** add `hero`/`onHero` tokens;
  light `#111514`/`#FFFFFF`, dark `#1B211F`/`#EEF1EC`. The same applies to the dark "Maintenance" tile on Insights. The ring
  progress and "Log food" button stay lime in both.
- Light-theme chart marks that use `ink` (steps bars, weight line, days-logged dots) simply follow `ink` in dark.
- `--soft` is declared twice for dark in the CSS; the last one, `#212725`, is what renders.
- Allergen colours: **replace** `AllergenRed` (`#9F3026`) and `AllergenAmber` (`#825014`) with `bad`/`warn` and their backgrounds.
  The semantics are unchanged (Contains is red, May contain is amber, and they stay visibly different, SPEC section 7.3).
- Contrast: lime/`onAccent`, `ink`/`bg`, `muted`/`bg` and the status pairs were chosen by the owner to read at body size; implementers
  must still run the project's lint and spot-check each chip pair at font scale 1.5 in both themes.
- Do not use Material dynamic colour by default (existing "Use wallpaper colours" setting stays, default off). When it is on,
  keep status, macro and accent tokens fixed; only `bg/card/soft/ink` may follow the wallpaper.

Compose mapping (new file `ui/theme/Tokens.kt`, provided by `IntakeTheme`):

```kotlin
@Immutable class IntakeColors(
  val accent: Color, val onAccent: Color, val dock: Color, val scrim: Color,
  val hero: Color, val onHero: Color, val soft: Color,
  val protein: Color, val carb: Color, val fat: Color, val water: Color, val sleep: Color, val heart: Color,
  val ok: Color, val okBg: Color, val warn: Color, val warnBg: Color,
  val bad: Color, val badBg: Color, val ai: Color, val aiBg: Color,
)
val LocalIntakeColors = staticCompositionLocalOf<IntakeColors> { error("no IntakeTheme") }
val MaterialTheme.intake: IntakeColors @Composable get() = LocalIntakeColors.current
```

M3 `ColorScheme`: `background = bg`, `surface = card`, `surfaceContainerLowest/Low/Container/High = card` or `soft` (use `soft` for
`surfaceContainerHighest` and `surfaceVariant`), `onSurface/onBackground = ink`, `onSurfaceVariant = muted`,
`outline = muted`, `outlineVariant = line`, **`primary = ink`, `onPrimary = inkOn`** (so default M3 filled buttons, selected
chips and switches become black pills), `primaryContainer = accent`, `onPrimaryContainer = onAccent`, `secondaryContainer = soft`,
`error = bad`, `errorContainer = badBg`, `onErrorContainer = bad`, `scrim = scrim`. `tertiary*` can map to `ai/aiBg`.
Delete the Green/Sand/Clay palettes, `ProteinAccent/CarbAccent/FatAccent`, `WaterAccent`, `Heat*`, `Plate*`; re-point them to
the tokens above (heatmap: full `ok`, partial `okBg`, empty `soft`; plates: large `protein`, small `carb`, bar `muted`).

### a.2 Typography

| Family | Use | Weights | Licence | Delivery |
| --- | --- | --- | --- | --- |
| Space Grotesk | titles, section headings, **all numbers** (stats, ring centre, set weights, steppers) | 500, 600, 700 | SIL OFL 1.1 | Google Fonts |
| Inter | all reading text, buttons, chips, labels | 400, 500, 600, 700 | SIL OFL 1.1 | Google Fonts |

The mockup loads both from Google Fonts at view time. **The app must bundle them**, never use downloadable fonts: downloadable
fonts need Google Play services and the network, and rule 1 says the app works offline with no account. Both are variable
fonts on Google Fonts, so follow the existing pattern in `Type.kt` (one variable `.ttf` per family in `res/font`, weight from the `wght`
axis): `inter_variable.ttf` (subset to Latin and Latin Extended to keep it small; Inter's full variable file is large) and
`space_grotesk_variable.ttf`. Both are OFL 1.1, which allows bundling, subsetting and embedding in an app; the licence text
must ship with them. Add `design/fonts/Inter-OFL.txt` and `design/fonts/SpaceGrotesk-OFL.txt` (next to the existing Manrope and
DM Sans ones) and list both under Credits and licences (Settings > About and help). Do not rename the font families. Remove
`manrope_variable.ttf`, `dm_sans_variable.ttf` and their OFL files when the last reference is gone (phase 1 does this and
checks with grep; the APK size should drop or stay level). No new Maven dependency is needed.

Type scale (CSS to Compose). Tracking `em` x size = sp. Keep the existing rule: body at least 12 sp and labels at least 11 sp,
so the 11 and 11.5 px labels are exactly at the floor. All text uses `sp` and must scale to 1.5.

| Mockup class | Spec | Compose slot |
| --- | --- | --- |
| Hero number (Weekly "48") | SG 600, 48/1.0, -0.03em (-1.4 sp) | `displayLarge` |
| Big stat, onboarding/summary title | SG 600, 34-44/1.08, -0.025em | `displayMedium` (34) |
| `.title` screen title ("Mon, Nov 16", "Food", "Insights") | SG 600, 30/32, -0.025em (-0.75 sp) | `headlineLarge` |
| Stat numbers in tiles and cards (`.num` 24, 28) | SG 600, 24-28, -0.03em | `headlineMedium` (24), `headlineLarge` is 30 |
| Sheet title, big `.num` 22 | SG 600, 22/26, -0.02em | `headlineSmall` |
| `.h2` section/card title | SG 600, 19/23, -0.015em (-0.3 sp) | `titleLarge` |
| `.num` 16 to 20 (day strip date, small stats) | SG 600, 16-20, -0.03em | new `NumberSmall` TextStyle (not an M3 slot) |
| `.h3` row title | Inter 600, 15/20 | `titleMedium` |
| row title small / seg button | Inter 600, 13.5/18 | `titleSmall` |
| body | Inter 400-500, 15/21 | `bodyLarge` |
| `.sub` secondary | Inter 400, 13/18, `muted` | `bodyMedium` |
| caption | Inter 400, 12/16 | `bodySmall` |
| `.btn` | Inter 600, 14.5; `.btn.sm` 13 | `labelLarge` (14.5); `labelMedium` 13 |
| `.chip`, field label | Inter 600, 12.5 | `labelMedium` variant (12.5) |
| `.eyebrow` | Inter 600, 11.5, +0.08em (+0.9 sp), UPPERCASE, `muted` | `labelSmall` + `.uppercase()` |
| dock tab label | Inter 600, 11 | `labelSmall` variant (11) |

Numbers use `FontFeature "tnum"` where they change live (timers, set weights); Space Grotesk supports it. Do not change copy
case: the eyebrow is uppercased visually via `TextStyle`/`uppercase()` of the existing string, not a new string.

### a.3 Spacing

| Token | dp | Use |
| --- | --- | --- |
| `screenGutter` | 20 | `.pad` side padding on every tab root and pushed screen (current app: check; phone width 360 still fits) |
| `stackGap` | 12 | vertical gap between cards (`.stack`) |
| `gridGap2` | 12 | two-column tile grid |
| `gridGap3/4` | 10 | three- and four-column grids (Quick log, plan weeks) |
| `rowGap` | 8 | chip rows (`.wrap`), button rows |
| `cardPad` | 18 (default), 20 hero, 14 small tile, 12 compact | inside cards |
| `listRowPad` | 13 vertical, 18 horizontal, min height 56 | `.li` |
| `iconGap` | 12 to 14 | icon tile to text |
| `topBar` | 20 top, 12 bottom, 12 gap | `.top` |
| `sectionHeader` | 10 top, 4 horizontal | `.sec` |
| `dockInset` | 16 sides, 20 bottom, dock height 70 | floating dock |
| `livePillOffset` | 16 sides, bottom 102 (dock top + 12) | session bar |
| `tabRootBottom` | dock height + 20 + 16 + safe area | scroll end padding |

Touch target minimum 44 dp for every button and chip row (the mockup uses 44 as `min-height`); chips are 28 dp tall visually
but must have a 44 dp hit area (`minimumInteractiveComponentSize`). Use `heightIn(min = ...)`, never fixed heights.

### a.4 Corner radius

| Token | dp | Components |
| --- | --- | --- |
| `Pill` | 999 (`CircleShape`/full) | buttons, chips, pills, search field, segmented control and its items, dock, FAB, avatar, stepper, switch, bars |
| `Card` | 24 | `.card`, `.list` |
| `Sheet` | 30 (top corners only) | bottom sheets |
| `Dialog` | 28 | dialogs |
| `Tile` | 20 to 22 | tiles inside a page (quick tiles 20, recipe card 22, Train tiles 20) |
| `Inner` | 18 | day-strip button, limit boxes, Recipe Hunter limit rows |
| `Live` | 20 | live (session) pill |
| `Input` | 14 | text input (48 dp tall), icon tile 40 dp |
| `Thumb` | 16 | image thumbnails (video 18, recipe photo 16) |
| `Snack` | 16 | snackbar |
| `SetRow` | 14; set cell 12; tick 12 | active workout |
| `IconSm` | 10 | 32 dp icon tile |
| `Bar` | 99 | progress bars (8 dp thick); chart bars 5 top / 2 bottom |

Update `IntakeShapes` (extraSmall 10, small 14, medium 18, large 24, extraLarge 30) and `IntakeShape.*` to these names. Any
`RoundedCornerShape` literal in a screen file must be replaced by a token in phase 2.

### a.5 Elevation, borders, blur, glass

- Cards, lists, tiles, sheets, dialogs: **no shadow**, no border. Separation is the white `card` on the `bg` ground.
- Dock: `0 14 36 rgba(0,0,0,.22)`, i.e. `shadow(elevation = 12.dp, ambient/spot = black 22%)`. Live pill: `0 10 28 rgba(0,0,0,.14)`.
- Selected segment: `0 1 3 rgba(0,0,0,.08)` (elevation 1). Switch knob: `0 1 2 rgba(0,0,0,.2)`.
- Snackbar: no shadow (it is ink on ground).
- Borders: 1 dp `line` dividers between list rows (not above the first). Outlined buttons, inputs and `chip.out`: 1.5 dp `line`
  (inset, so it does not change size). Selected current set row: 2 dp `ink` inset outline. Tour spotlight: 3 dp lime ring.
- Scrim: `scrim` colour over the screen, then the sheet/dialog on top.
- **No blur, no frosted glass, no gradient** anywhere in the mockup. Remove the glass treatment from `PillBackdrop` and use the
  solid `dock` colour; if the pill backdrop exists to keep icons readable over scrolling content, the solid fill replaces it.
- Images: recipe/video thumbnails sit on `soft` with a 16 to 18 dp radius; video duration badge `rgba(0,0,0,.7)`, white 11.5/600,
  6 dp radius.

### a.6 Icons

Outline line icons on a 24 dp viewBox: 22 dp default (`.i`), 28 dp large (`.l`, the FAB plus and
hero glyphs), 16 dp small (`.s`) and 14 dp extra small (`.xs`) inside chips. Stroke 1.8 (of 24), round caps and joins, no fill,
`currentColor`. The mockup draws its own inline SVG paths (Today: calendar; Food: fork and knife; Train: dumbbell;
Insights: line chart with baseline; shield with check for allergens; sparkle for AI; barcode scanner brackets; camera; etc.).
**Implementation:** convert the mockup SVG path set to `ImageVector`s (vector drawables under `res/drawable/ic_*.xml`,
`strokeWidth 1.8`, `strokeLineCap round`, `strokeLineJoin round`, `tint` from content colour) in one `ui/theme/IntakeIcons.kt`
(phase 1). Do not mix the filled Material icon set into redesigned screens. `material-icons-extended` is not needed. Paths live in
`design/mockup-v6/source/*.dc.html` (search for `<svg class="i`). Icon tile: 40 dp (`ic`), radius 14, `soft` fill, `ink` glyph;
variants `lime` (lime fill, `onAccent` glyph) and `inkb` (ink fill, `inkOn` glyph); small tile 32 dp radius 10.

### a.7 Motion

The mockup defines no motion beyond: the `.pulse` dot on the live pill (10 dp lime dot with a 5 dp, 25% lime halo),
scrim plus sheet in/out, and snackbar. Keep the existing `ui/motion` tokens (`docs/DESIGN-MOTION.md`: 100/250/350 ms, `PressScale` 0.97,
pill spring, Reduce motion). Rules for the redesign:

- Ring sweep and count-up on Today stay (first show only). Bar and chart draw-ins stay.
- Dock tab switch: the active tab recolours to lime (200 ms, `Standard`); no sliding blob unless the current pill spring is kept
  (see section d: the dock is not a draggable pill any more, owner decision g4).
- Sheet: slide up 350 ms `EmphasizedDecelerate`, scrim fades.
- Everything honours `LocalReduceMotion`.

---

## (b) Component inventory

Each entry gives the CSS class in `intake.css`, the exact spec and the Kotlin home. "Phase 1" components are built once in
`ui/components` and `ui/theme`; screens only consume them. Where a current component exists it is replaced in place (same
name where possible so screens do not change).

### b.1 Surfaces

| Component | Spec | Current app home |
| --- | --- | --- |
| **Card** (`.card`) | `card` fill, radius 24, padding 18, no shadow. Content in a column, gap 12. | `IntakeCard`, `SectionCard` in `Common.kt` |
| **Hero card** (`.card.inkc`) | `hero` fill (ink in light), text `onHero`, secondary text 72% alpha, padding 20, gap 18. Ring 132 dp + three macro bars, then a text row, then buttons. | new `HeroCard` (Today); `Common.kt` |
| **Accent card** (`.card.lime`) | `accent` fill, text `onAccent` (sub 75%); buttons inside: translucent `rgba(17,21,20,.10)` and solid `#111514` with white text. Used for Baseline week, Weekly check-in, tip card. | `AccentNote`, `SolidBanner` |
| **Soft card** (`.card.soft`) | `soft` fill; used for the export reminder (icon, 13 sp text in `ink`, small white button) | `SandNote`/`SandStrip` |
| **List** (`.list` + `.li`) | one `card` radius-24, `overflow hidden`; rows 56 dp min, padding 13/18, gap 14, `line` divider on top of every row but the first | `SettingRow`, `MenuItem` |
| **Tile** (grid2/3) | `card`, radius 20, padding 14, icon tile at top, title 13.5/600 below, sub 13 `muted`. Tap target the whole tile. Variants: ink tile ("Maintenance"), soft tile (Quick log items). | `ActionTile`, `SummaryTile`, `StatTile` |
| **Stat tile** (Weight, Activity, Sleep, Steps, HR, Days logged) | `card` radius 24, eyebrow (11.5 caps) then `.num` 28 value with a 13 sp unit in `muted`, then a delta chip or mini chart. | `StatTile`, `Charts.kt` |
| **Macro tile** (Diary) | `soft`, radius 18, label 13, `.num` value 20 with `/target` in 13 `muted`, 8 dp bar in the macro colour. | `MacroTiles` |
| **Section header** (`.sec`) | `.h2` 19 left, optional right action/count in `sub`, 10 dp top padding | `SectionLine`, `SubHead` |
| **Divider** (`.hr`) | 1 dp `line` | |

### b.2 Chips, pills, tags

All chips: pill, min height 28, padding 0 10, gap 6, text 12.5/600, no border. Hit area 44 dp.

| Chip | Background / text | Used for |
| --- | --- | --- |
| neutral (`.chip`) | `soft` / `ink` | default, filter, "Only the name was checked", "Checking…", "Mentioned as avoided", "Planned" (lime variant) |
| `ok` | `okBg` / `ok` | **Fits your allergens** (with a 14 dp check icon), weight delta "-0.8", Applied, Ready, Accepted |
| `warn` | `warnBg` / `warn` | **May contain**, **Check the label**, **Not checked**, Due, Possible match, Over/Under range |
| `bad` | `badBg` / `bad` | **Contains your allergen**, **Blocked**, Failed |
| `ai` | `aiBg` / `ai` | every AI label ("AI", "Read by AI", "AI optional"), with a 14 dp sparkle icon; the small form is min height 20, padding 0 6, 11 sp |
| `inkb` | `ink` / `inkOn` | selected small filter, "1" counts |
| `lime` | `accent` / `onAccent` | "Planned" label, highlights |
| `out` | transparent, 1.5 dp `line` inset / `ink` | "Needs your key", unselected filters with an outline |
| **Pill** (`.pill`) | min height 44, padding 0 16, radius 999, `card` fill, 14/600; `.on` = `ink` fill and `inkOn`. Used for filters ("Muscle", "Equipment"), water sizes (on a `soft` sheet they use `soft` fill), cuisine chips. | `ChoiceChip` |
| **Count badge in shield chip** | "Avoiding 2": ok-styled chip with a shield icon in the Food header | `AllergenStrip` |

**Allergen chip mapping (keep the app's exact strings; never invent new ones, copy rules unchanged):**

| App label (strings.xml) | Chip | Icon |
| --- | --- | --- |
| Fits your allergens | `ok` | check |
| Only the name was checked | neutral (never green, never a pass) | none |
| Checking… / Checking allergens | neutral | none (optional 14 dp spinner only if it exists today) |
| May contain (also "soft match", "Possible cross-contact") | `warn` | none |
| Contains / Contains your allergen | `bad` | none |
| Check the label | `warn` | none |
| Not checked | `warn` | none |
| Blocked | `bad` | none |
| Mentioned as avoided | neutral | none |

The mockup rows abbreviate to "Name checked" in tight rows; use the existing short string where the app already has one, else the
full string. **A chip is never green unless the check really read a label or panel** (rule 2). Contains must never look softer than May
contain; Contains/Blocked always `bad`, red text on pale red, never neutral.

### b.3 Buttons

| Button | Spec |
| --- | --- |
| **Primary** (`.btn.primary`) | min height 44, padding 0 18, pill, `ink` fill, `inkOn` text, 14.5/600. Default M3 `Button` via `primary = ink`. |
| **Accent** (`.btn.accent`) | `accent` fill, `onAccent` text. The one lime button per screen at most (hero "Log food", Start). |
| **Soft** (`.btn`) | `soft` fill, `ink` text. Secondary action ("Swap", "Open plan"). |
| **Line** (`.btn.line`) | transparent, 1.5 dp `line` inset, `ink` text ("Add exercise"). |
| **Ghost** (`.btn.ghost`) | transparent, padding 0 10, text only ("More detail", "Dismiss"). |
| **Danger** (`.btn.danger`) | `badBg` fill, `bad` text ("Log it anyway", Discard). The destructive-but-allowed option is danger-soft; never a primary ink button. |
| **Block** | full width |
| **Small** (`.btn.sm`) | padding 0 14, 13 sp, still 44 dp tall hit target (visual 36 allowed with `minimumInteractiveComponentSize`) |
| **Icon button** (`.iconbtn`) | 44 dp circle, `card` fill (or `soft`, or transparent), `ink` glyph 22 dp |
| **Avatar** (`.avatar`) | 44 dp circle, `accent` fill, `onAccent` 17/600 SG initial. 64 dp / 26 sp on You. |
| **Dock FAB** | 56 dp circle, `accent`, `onAccent` 28 dp plus (becomes an X while the Quick log sheet is open) |

Pressed state: `PressScale 0.97`, no ripple colour change beyond the default. Disabled: 38% alpha. Buttons inside the
hero use `rgba(255,255,255,.12)` fill with inherited colour for icon buttons (scan, photo).

### b.4 Navigation

| Component | Spec |
| --- | --- |
| **Dock** (`.dock`) | floating, 16 dp from each side, 20 dp above the bottom inset (add gesture/nav inset), height 70, pill, `dock` fill, padding 0 10, shadow (a.5). Five slots: Today, Food, **FAB**, Train, Insights; `space-between`. Tab: icon 22 + label 11/600 below, min 60 x 52, inactive `#C5CBC8`, **active `accent`** (icon and label). Hidden on pushed screens, sheets and full-screen tasks, as today. Wide screens: vertical rail (see d). |
| **Live pill** (session bar, `.live`) | floating above the dock: 16 dp sides, 102 dp from the bottom, height 58, radius 20, `card` fill, shadow, padding 0 8 0 16, gap 12; 10 dp lime dot with 5 dp 25% lime halo; "Workout · Push A" title and "32:14 elapsed" sub; a small ink "Resume" pill button on the right. |
| **Top bar, tab root** (`.top`) | padding 20/20/12; left: eyebrow (11.5 caps `muted`) over `.title` 30; right: avatar 44. On Food and Train/Insights the right cluster is `[Avoiding n]` shield chip + avatar. Insights adds a one-line sub under the title. |
| **Top bar, pushed** | 44 dp circular `card` back button at the left (ChevronLeft), centred or left `.h2`-weight title, up to two icon actions (44 dp circles). No pill. |
| **Top bar, full-screen task** | Active workout: 44 dp circular down-chevron (minimise), centre title (Push A) with the elapsed time under it (13 `muted`, SG), lime **Finish** pill right. Log food: back, title "Log food" + "to Lunch" in `muted`. |
| **Segmented control** (`.seg`) | `soft` track, pill, padding 4, gap 2; items min height 40, 13.5/600, `muted`; selected item `card` fill, `ink` text, shadow 1. Used for Food (Diary Plan Recipes Library), Train (Workout Exercises Videos), range selector (1M 3M 6M 1Y All), Log food source tabs (as chips with icons), Front/Side/Back. Scrollable if it does not fit at font scale 1.5 (make it horizontally scrollable instead of truncating). |
| **Day strip** (Today) | seven equal buttons, gap 6, min height 56, radius 18; weekday initial 12 `muted` over `.num` 16 `ink`; today is a filled `ink` button with `inkOn` text; other days transparent. |
| **Day switcher** (Diary) | 44 dp round `card` previous button, centred "Today, Nov 16 v" 15/600 with a chevron (opens the day picker), 44 dp round next button (disabled, 40% alpha, on today). |
| **Week chips** (Plan) | `.wk`: min 52 x 44, radius 14, `card`; `.on` = `ink`. |

### b.5 Sheets, dialogs, snackbars, overlays

| Component | Spec |
| --- | --- |
| **Bottom sheet** | `card` fill, top corners 30, padding 10/20/28, grabber (40 x 5, radius 9, `line`, centred, 16 below), title `headlineSmall` 22 left with context `sub` right ("Lunch · 12:40"), content, safe-area bottom. Scrim above. Use M3 `ModalBottomSheet` with these shape/colour values and drag handle replaced by the grabber. |
| **Quick log sheet** | title row, then a 3-column grid (gap 10) of 11 quick tiles: soft tile 96 dp min height, radius 20, padding 14, icon on top, 13.5/600 label; "Food" tile is lime, "Start workout" tile is ink; "Photo estimate" has the small AI chip. Under it eyebrow "Water · one tap" and a row of `soft` pills (8, 12, 16 fl oz, 24). The FAB becomes an X over the sheet; the dock stays visible above the scrim. **New surface: owner decision g1.** |
| **Allergen sheet** (Food safety check) | sheet; red eyebrow "FOOD SAFETY CHECK" (11.5 caps, `bad`) next to a 48 dp `badBg` shield tile, title 22 SG; a `soft` card (radius 24) with the food name + `bad` chip, a bold-keyword explanation, a `muted` line "Checked on this phone against the label · Strict mode"; a `muted` 13 sp warning; then **Don't log it** (primary ink, block), **Log it anyway** (danger, block) and a text button "Find a safer swap". Order, labels and defaults are unchanged. The camera preview stays dark behind it. |
| **Dialog** | `card`, radius 28, padding 22, inset 20 from the sides, scrim behind; title 19 SG, body 15, buttons right: ghost cancel and ink confirm (danger-soft for destructive confirm). |
| **Snackbar** (`.snack`) | 16 dp sides, height 52, radius 16, `ink` fill / `inkOn` text 14, padding 0 6 0 16, action button text lime (`accent`; in dark where `ink` is light use `onAccent`-on-lime pill or the dark `inverse` role, PROPOSED: snackbar bg is `inverseSurface` = `ink`, action colour = `accent` in light, `onAccent` on an `accent` chip in dark). Sits above the dock/live pill. Undo keeps its position and timeout. |
| **Tour spotlight** | scrim 35% with a 3 dp lime ring cutout. |
| **Scrim** | `scrim` token. |

### b.6 Inputs and controls

| Component | Spec |
| --- | --- |
| **Text input** (`.input`) | height 48 (min, grows with font scale), radius 14, 1.5 dp `line` border, `card` fill, padding 0 14, 15/500. Label above (`.field label`): 12.5/600 `muted`, 6 dp gap. Focus: border `ink`. Error: border `bad`, helper in `bad`. |
| **Search field** (`.search`) | height 48, pill, `card` fill, padding 0 16, leading 22 dp icon in `muted`, 15/500 text |
| **Stepper** (`.stepper`) | pill, `soft`, padding 3; two 38 dp round `card` buttons (minus, plus) with a 44 dp-min centred value 14/600. On Today water rows the buttons are 44 dp outlined circles (1.5 dp `line`). Hit area 44. |
| **Switch** (`.sw`) | 50 x 30 pill; off `line` track, white 24 dp knob (shadow 1); on `ink` track with an **accent-coloured** knob at the right. |
| **Radio** (`.rad`) | 22 dp circle, 2 dp `muted` ring; on: 7 dp inner dot filled `ink` (rendered as inset 7). |
| **Checkbox** (`.box`, cook mode) | 22 dp, radius 7, 2 dp ring in the current colour; checked = filled. |
| **Day number pad** (`.dn`, program generator) | 72 dp tall, radius 22, `card`, SG 600 26; selected = `ink`. |
| **Selection tile** (onboarding goals) | `card` radius 22 with title and `sub`; selected = ink border/fill (see Onboarding.html). |

### b.7 Rings, charts, progress

| Component | Spec |
| --- | --- |
| **Calorie ring (hero)** | 132 dp, radius 56, stroke 12, round cap, start at 12 o'clock clockwise; track = text colour at 14% alpha; progress `accent`; centre: SG 600 30 "1,284" over Inter 12 "kcal left" at 72%. On Diary the ring is 88 dp, stroke 10, progress `ink`, track `soft`, centre "1,116 of 2,400". If over target the progress segment turns `carb` (orange) rather than red. |
| **Macro bar** | 8 dp tall pill; track `soft` (on hero `rgba(255,255,255,.14)`); fill = macro colour; label row 13 sp `muted`: name left, "96 / 165 g" right. |
| **Water bar** | 8 dp, `soft` track, `water` fill; value "42 / 101 fl oz" with `.num` for 42. |
| **Coverage track** (muscles this week, `.track`) | 10 dp pill, `soft`; a 30%-wide `line` target band centred; `ink` fill capsule inset 2; row grid 84 / flex / 72 dp: label, track, status chip (In range = ok, Under = warn, Over = warn). |
| **Bar chart** (sleep, steps, calories) | `bars`: 56 dp tall, gap 5, bar radius 5/5/2/2; sleep `sleep`, steps `ink` with the current day lime outlined, calories track `soft` bars with a dashed target line and `carb` for over-target days. |
| **Line chart** (weight trend) | `ink` 2 dp line over grey dots; end marker 12 dp lime dot with `ink` ring; delta chip top right (`ok` "-4.2 in 3 months"). Resting HR: `heart` 2 dp line, no axes. |
| **Dots** (days logged) | 8 dp circles, logged = `ink`, missed = `soft`. |
| **Hairline progress** (baseline week) | seven 6 dp segments, gap 4; done `#111514`, pending 18% black. |
| **Per-muscle volume, body map** | keep existing components and recolour (`MuscleMapView`, `RegionBodyMap`, `BodyFigure`) with `soft`/`ink`/`accent`. |

### b.8 Row components

| Row | Spec |
| --- | --- |
| **Meal row** (Diary/Today "Meals" list) | inside one `list`: slot name 15/600, kcal right 13 `muted`, entries below 13 `muted` ("Greek yogurt bowl · Oat latte"); empty slot shows "Nothing yet" `muted` + a 44 dp `soft` round **+** button at the right; planned meal shows a lime "Planned" chip, the dish line, and buttons Log it (ink sm), Open plan (soft sm), Swap (ghost sm). Allergen chip right-aligned on the entry row. |
| **Food row** (Log food, Library, Drinks recent) | `li`: 15/600 name, 13 `muted` "165 kcal · 31 g protein · 100 g · Made by you", allergen chip right. Source group headers are `eyebrow` rows ("Food database · works offline"). |
| **Next up timeline** (Today) | one `list` card with three rows: icon tile (40: soft for meal, lime for coach, ink for workout), eyebrow-style small line ("Lunch · planned"), 15/600 title, buttons under. |
| **Recipe card** (`.rc`) | `card` radius 22, padding 8/8/12, 104 dp photo (`soft`, radius 16) with an allergen chip at the top right, title 15/600, `sub` "640 kcal · 48 g P · 30 min"; two columns. |
| **Ingredient row** (`.ing`) | space-between, padding 8, 1 dp `line` divider top, 14 sp; step row: 26 dp `ink` circle with 12 sp number + 14 sp text. |
| **Nutrition label row** (`.nl`) | grid `1fr 80 80`, padding 9, divider top, 14 sp, numbers right aligned. |
| **Settings row** | `li` with icon tile, title 15/600, `sub`, trailing chip/switch/chevron; sections are `eyebrow` headings above one `list` each. |
| **Menu row, program/video row** | as `li`; video thumb 16:9, radius 18 on `#1D2321` with a duration badge. |

### b.9 Workout specifics

| Component | Spec |
| --- | --- |
| **Set row** (`.sr`) | grid `32 / flex / 64 / 56 / 44`, gap 8, min height 48, padding 0 8, radius 14; columns: set number (SG 600), "Previous" (`muted` 13 "185 x 8"), weight cell, reps cell, tick. Cells (`.cell`) 40 dp tall, radius 12, `soft`, 600. Tick (`.tick`) 40 dp radius 12 `soft`, `muted` check. **Done**: row fill `okBg`, cells transparent, tick `ok` fill with white check. **Current**: 2 dp `ink` inset outline. Header row ("Set / Previous / lb / Reps") 13 `muted`. The unit label follows the weight unit (kg or lb). |
| **Rest strip** | ink card radius 24 (`hero`), full width under the top bar: left eyebrow "RESTING" (11.5 caps, 72%) over SG 600 32 timer "1:24"; right: two 44 dp circular `rgba(255,255,255,.14)` buttons "-15" "+15" and a lime **Skip** pill. |
| **Plate row** | `soft` pill row: dumbbell icon, "Per side: 45 + 25 + 2.5", right-aligned white "45 bar" pill. Actions below as `soft` pills: "Use last time's numbers", "Pick a weight", "Show the plates", outlined "+ Add set". |
| **Exercise card** | `card` radius 24, header (SG 600 20 exercise name, `sub` "4 x 6-8 · effort 8 · rest 2:00"), a 44 dp soft round overflow button, set rows, helpers, "Form note" field. |
| **Up next card** | `card` with a 44 dp soft tile (arrow), eyebrow-style "Up next", 15/600 title, ghost "Swap". |
| **Discard workout** | centred text button in `bad`, 15/600 (not a filled button). |

### b.10 Empty and loading states

The mockup draws no empty/loading screens. Use the existing copy; apply: centred column inside a `card` (or directly on
`bg` for a whole screen), 40 dp `soft` icon tile (22 dp line icon `muted`), `h3` title, `sub` body, one ink pill button. Loading is a
`soft` skeleton block with the card's radius (replace shimmer colours with `soft` to `line`); errors use `badBg` with `bad`
text and a "Try again" soft button. Never remove or reword existing empty-state strings.

---

## (c) Screen map

Mockup screens (34 files in `screens/`) mapped to routes in `ui/navigation/Destinations.kt` and the screen inventory in
`docs/FEATURES.md` (read from branch `agent/feature-inventory`; its section numbers are given as `F n.n`). "Group" is the phase 2
group in section e.

| Mockup screen | App screen / route | F | Group | Notes |
| --- | --- | --- | --- | --- |
| `Main` | none (design brief board) | n/a | n/a | Not a screen. Carries palette, status language, IA statement. |
| `Map` | none (coverage map) | 9 | n/a | Useful checklist: 49 routes, 12 settings groups, 7 watch screens. |
| `Onboarding` (steps 1, 4, 9 drawn) | `Onboarding` | 1.1 | E | 10 steps; header "Step n of 10 · Goal" + "Skip setup"; selection tiles; extras list with Set up buttons. Draw the 7 undrawn steps in the same language. |
| `Today` | `Today` | 2 | A | Day strip, baseline lime card, export reminder soft card, hero, Next up, Water, Weight and Activity tiles, insight, Meals, snackbar, live pill. |
| `QuickLog` | no equivalent (new sheet over any tab) | n/a | shell | See decision g1. |
| `LogFood` | `LogFood` | 3.6 | B1 | Source chips: Search, Scan, Photo (AI), Mine, Quick. "All sources" filter row. Source group headers. |
| `FoodDetail` | `FoodDetail` | 3.14 | B1 | Hero numbers line (kcal and macros), portion stepper, allergen note card, nutrition label grid, ingredients, edit/contribute/remove, sticky "Log to Lunch · 140 kcal" button. |
| `AllergenCheck` | food safety sheet, `AllergenWarningSheet` / `AllergenOverrideDialog` | 8.1 | B3 | Dark scanner behind a sheet. Keep the exact order and labels. |
| `Drinks` | `Drinks`, `DrinksSearch`, `CustomDrink` | 3.7 | B1 | Logged-today summary, My drinks tiles, Recent, Customise a drink. |
| `FoodDiary` | `Nutrition` tab root (segment Diary) and `NutritionMeal` | 3.1, 3.13 | B1 | Header with shield chip + avatar, segmented control, day switcher, overview card, tip card, meals list. |
| `FoodPlan` | `MealPlans` (segment Plan) | 3.3 | B2 | Plan selector header, week chips W1..W4 + weekday row, dish card, standing items, hidden dairy checklist. Plan options menu. |
| `Recipes` | `Recipes` (segment Recipes) | 3.4 | B2 | Hunter promo card (AI optional), add/import rows, My recipes grid. |
| `RecipeHunter` | `RecipeHunter` | 3.4 | B2 | Limit boxes, cuisine chips, AI switch row, result cards with chips, "Read by AI" + "Check allergens". |
| `RecipeDetail` | `RecipeDetail` | 3.4 | B2 | Allergen/time/servings chips, macro line, Log a serving, Cook, Add to week, Original, ingredients, steps, "Estimated from ingredients" panel, notes. |
| `CookMode` | `CookMode` | 3.4 | B2 | The one dark-themed screen in the mockup: dark `phone`, step title, timers, ingredient checkboxes, Back / Next step. Keep screen on, swipe gestures. |
| `FoodLibrary` | no single screen today: `MealTemplates`, `ShoppingList`, `EatingOut`, `MenuSources`, My foods, `Drinks` | 3.2, 3.5, 3.9, 3.10, 3.12 | B2 | New Library segment is a hub that lists them (decision g2). |
| `SwapNearby` | `SwapSheet` (dialog destination) + nearby search sheet | 3.11 | B2 | Drawn full-height with tabs Plan and recipes / Saved / Nearby; keep it a sheet. |
| `MenuImport` | `MenuSources` + review sheets | 3.12 | B2 | Source cards with status chips, review list with Accept/Reject. |
| `Allergens` | `Allergens`, `AllergenKeywords` | 3.8 | B3 | Header chip "Avoiding peanuts and milk", Avoid/Strict segmented, allergen list, keyword editor, hidden-ingredient reference, recently flagged. |
| `Train` | `Train` tab root (Workout segment) | 4.1 | C | Today card, "Other ways to start" list, "Make me a program" row, "This week" coverage tracks, recent sessions, Programs promoted to a header action and a card. |
| `ActiveWorkout` | `ActiveWorkout` | 4.8 | C | Rest strip, exercise card, set rows, plates, form note, up next, discard. |
| `WorkoutSummary` | `WorkoutSummary` | 4.9 | C | Title "Push A, done.", 3 stat tiles + records, muscles worked bars, exercise list with Change, Next time, "Keep this workout?" form. |
| `Programs` | `Programs`, `ProgramBuilder`, `ProgramTemplates` | 4.2-4.4 | C | Active program card, New program 3-tile grid, other programs, My workouts. |
| `ProgramGenerator` | `ProgramGenerator` | 4.5 | C | "n of 9" header, number pad answers, summary of answers so far, Back/Next. |
| `Exercises` | `Train` (Exercises segment) + exercise detail/form sheets | 4.7 | C | Filter pills, exercise rows with a Custom chip, detail sheet with 3 stat tiles. |
| `Videos` | `Train` (Videos segment), `VideoDetail`, `MakeWorkout` | 4.11 | C | Saved strip, recommended card with chapters, "Make this a workout". |
| `Insights` | `Progress` tab root (renamed Insights) | 5 | D | Lime weekly check-in hero, 2 link tiles, range segments, bento of stat tiles, calories chart, measurements list, photos card, averages + dark maintenance tile, jump tests. |
| `WeeklyCheckIn` | `WeeklyCheckIn` | 5.1 | D | Week chips, stat grid, Export PDF / Print, suggestion cards (Not decided / Applied), medical note. |
| `PhotoCompare` | `PhotoCompare`, `ProgressPhotos` | 6.3 | D | Two photos with dates, Front/Side/Back segment, data under each. Photos stay on the phone. |
| `You` | `You` | 6.1 | E | Big avatar, profile, goals card, photos card, Help list. |
| `YourPlan` | `YourPlan` | 6.4 | E | Number rows with "why" lines, training row, Apply plan. |
| `Settings` | `Settings` (home) and `SettingsGroup` pages | 6.5 | E | Four sections instead of one list (decision g6). Group pages are not drawn: apply the list/switch/radio patterns. |
| `SettingsAI` | `SettingsGroup("ai")` | 6.5 | E | Master switch, budget, privacy radios, scheduled vs on-tap lists, advanced. |
| `Watch` | `wear/` (7 screens) | 7 | F | Round face on `#0B0D0C`, `#262C2A` buttons, lime primary. |

### c.1 App screens the mockup does not cover (apply the language this way)

| App screen | How to apply the new language |
| --- | --- |
| `Profile` (About you, goals) | Pushed screen: pushed top bar, `list` rows with `soft` icon tiles, 48 dp inputs, segmented controls for sex/phase/pace, ink primary Save. |
| `BodyMetrics` (log weight, measurements) | Pushed screen; field list in a `card`, large SG 600 number entry, unit as a `soft` seg, date row, ink Save. |
| `TrainingStats`, `Performance` (jump tests), `WorkoutHistory` | Insights bento tiles and `list` rows; charts follow b.7. |
| `MealTemplates`, `MealTemplateEditor`, `ShoppingList`, `EatingOut` | Library list rows, food rows, checkbox rows (cook-mode box), pill actions. |
| `RecipeEditor`, `RecipeImport`, `ProgramBuilder`, `ProgramTemplates`, `MakeWorkout`, `VideoDetail`, shorts player | Form screens: input/label spec, `card` sections, ink primary. Players keep dark backgrounds with white controls and the duration-badge style. |
| `Privacy`, `ScriptReference` | Reading layout: `.title`, 15/21 body, `h2` headings, `card` for code samples (mono stays the existing monospace font). |
| `AllergenKeywords` | Part of Allergens; keyword rows are `li` rows with a level chip (Contains = bad, May contain = warn, Check the label = warn). |
| Backup/sync/Health Connect/key sheets, plan-manager sheets, import previews, exercise picker/form/detail, finish-workout dialog, set edit dialog, photo add sheet, model picker | Sheet and dialog spec in b.5; lists as `li`; every AI row carries the `ai` chip. |
| Notifications, widgets, splash, launcher icon | Out of scope for the first pass. Splash/window background uses `bg`. Revisit icon with the owner (decision g12). |
| Tour overlay and tab tips | Tab tips use the lime tip card (see Diary "Tap a meal to see..."), Tour keeps the scrim and gets the lime ring. |
| Wear tile/complications, if any | Group F decides; keep dark. |

---

## (d) Navigation and information-architecture changes

Current: floating pill with **Today · Nutrition · Train · Progress**; You behind the avatar on every tab root; Settings is the gear
on You; Nutrition has a "More" row; Progress is a tab of cards; no global "+".

The mockup implies:

| # | Change | Detail | Needs owner decision |
| --- | --- | --- | --- |
| 1 | **Centre + opens Quick log** | A fifth dock slot (lime FAB) between Food and Train opens a sheet with 11 quick actions and one-tap water. Today those actions are scattered buttons. It adds a new global entry point. Each tile deep-links to an existing route/sheet (Food, Drink, Scan, Photo, Quick, Meal template -> `LogFood`/`Drinks`/templates; Weight, Measurements -> `BodyMetrics`; Progress photo -> `ProgressPhotos`; Start workout -> Train; Jump test -> `Performance`; NFC tag -> NFC write flow). | **Yes.** Confirm the list; "NFC tag" and "Jump test" tiles exist only behind settings today (jump tests is a Progress setting): show those only when the feature is on. Water pills use the saved sizes from Settings, not hard-coded 8/12/16/24. |
| 2 | **Nutrition becomes Food, with four segments** Diary / Plan / Recipes / Library, and the "More" list goes away | Today the Plan, Recipes, Meals, Eating out, Drinks, Templates and Shopping are pushed routes behind `NutritionMore`. The mockup hosts Plan and Recipes as segments and groups Templates, Shopping, Eating out, Restaurant menus, My foods and Drinks in Library. Routes can stay (deep links, NFC, notifications) while the segment bodies reuse the screen contents. `Route.Nutrition` and `Route.NutritionMore` stay for the first pass; `NutritionMore` is unreachable from the UI. | **Yes** (moves screens, removes a hub). |
| 3 | **Allergen shield in the Food header** | The "Avoiding 2" chip (shield) replaces the compact allergen strip that sits in the Nutrition body today and still opens `Allergens`. The strip's empty state ("No allergens set. Add yours") becomes an amber chip. The mockup also says "Allergens stay out of Settings", which is already true. | No, but confirm the strip is not shown twice. |
| 4 | **Labels** | Tab label "Nutrition" -> "Food"; "Progress" -> "Insights" (`nav_nutrition`, `nav_progress`, titles, tour and tip copy, `Route` names unchanged). Icon changes: Today calendar, Food fork-knife, Train dumbbell, Insights line chart. | **Yes** (copy change). |
| 5 | **Dock is not a drag-select pill** | Five static slots, active tab lime. The current `FloatingPillBar` has drag-to-select geometry (`PillGeometry`, `PillBackdrop`) for four tabs; the mockup shows a plain dock. Keep tap behaviour (`TabPolicy`: switch, pop to root), per-tab back stacks, back-to-Today. Dropping the drag indicator is a behaviour simplification. | **Yes** |
| 6 | **Today: day strip replaces the arrows** | A seven-day strip (the last six days and today) plus the date title replaces the date with previous/next arrows and the "Back to today" chip. Future days are not selectable. Needs a way to go earlier than seven days (the old day picker), so keep the date-title tap or a "calendar" action. | **Yes** |
| 7 | **Settings in four sections** | Home groups the same twelve group pages under "Display and you", "Tracking", "Connections and data" and "Help". Which group goes where is drawn only partly (see `Settings`, `Map`). Search stays. `SettingsGroup` ids unchanged. | **Yes**: confirm section membership |
| 8 | **Insights shortcuts** | Insights gets a Progress photos card (also reachable from You) and a "Log weight and measurements" tile next to "Training stats"; the weekly check-in becomes the lime hero. The standalone "Log weight" button is replaced by that tile and the weight card. No data change. | No (all exist) |
| 9 | **Programs on Train** | Programs is a card and a header icon (already an icon + row today). "Make me a program" becomes a row on Workout and stays in empty states. | No |
| 10 | **Live workout pill** | Same behaviour as the session bar; restyled and positioned above the dock; follows across all tabs. | No |
| 11 | **Wide screens and landscape** | Keep the rail on wide screens: a vertical dock with the same five slots, FAB at the top, active tab lime. In phone landscape keep the horizontal dock; reduce its height to 60 dp if the window height is under 480 dp, and keep tab roots scrollable with end padding for it. | No |
| 12 | **Avatar** | 44 dp lime disc with the user's initial, same place and target as today. (The mockup letter was a placeholder.) | No |
| 13 | **Weekly check-in "Reword my check-in" and "Daily insight" AI rows** (SettingsAI) | Drawn as scheduled AI actions ("Runs on a schedule"). Confirm they exist today and are off by default. If any row does not exist, **do not add it**. | **Yes** (verify, then keep or drop the row) |

Any mockup element that has no existing behaviour (items 1, 2, 5, 6, 7, 13, and anything else an implementer finds that is not
on the Today/Food/Train/Insights lists in `docs/FEATURES.md`) must **not** be invented. Build what exists; if the drawing needs
a missing feature, leave it out and note it in `RESUME.md`.

---

## (e) Implementation plan

Order matters. Phase 1 is one agent because it edits shared files. Phase 2 groups run in parallel and each touches only its own
files. Claim each in `docs/TASKS.md` per `CLAUDE.md`. Each agent works on `agent/design-v6-<group>` branched from the
phase 1 merge. Never run two Gradle builds at once on a small machine.

### Phase 0: owner decisions

Resolve the list in section g (at minimum g1, g2, g3, g4, g5, g6) before phase 1b and the groups that depend on them.

### Phase 1: theme, tokens, core components (one agent, branch `agent/design-v6-core`)

Files (all shared; no one else touches them until this merges):

- `app/src/main/java/com/zeroindex/intake/ui/theme/Color.kt`, `Theme.kt`, `Type.kt`, `Shape.kt`; new `Tokens.kt` (`IntakeColors`,
  spacing, radius, elevation), new `IntakeIcons.kt` (vector set from the mockup).
- `app/src/main/res/font/`: add `inter_variable.ttf`, `space_grotesk_variable.ttf`; delete Manrope/DM Sans when unreferenced. `design/fonts/`:
  add the two OFL files. Credits and licences copy (string only, in `strings.xml`).
- `app/src/main/res/values/colors.xml`, `values-night/colors.xml`, `themes.xml`, `values-night/themes.xml`: window background and status/nav bar to
  `bg` (splash too).
- `ui/components/`: `Common.kt` (cards, stat/summary tiles, accent note), `Controls.kt` (segmented, chips, banners, setting
  rows, tags, flags, math grid, pills), `States.kt` (status pill, empty/loading/error, labeled field, switch row, equation
  strip, macro tiles, result card), `IntakeTopBar.kt` (avatar, top bars), `ScreenScaffold.kt`, `ActionTile.kt`, `MacroRing.kt`, `MacroSplitBar.kt`,
  `Charts.kt`, `NutritionFactsLabel.kt`, `PlateVisual.kt`, `MuscleMapView.kt`, `RegionBodyMap.kt`, `BodyFigure.kt`, `InfoTip.kt`, `AiWaitCard.kt`, `EquipmentChecklist.kt`,
  `MusclePickers.kt`, `ExercisePhotos.kt`, `SyncedText.kt`, `ImeFocus.kt` (only if styles are affected).
- Allergen chips and banners (shared by many screens, so here and not in a group): `ui/allergen/AllergenStrip.kt`, `AllergenBanner.kt`,
  `CheckLabelChip.kt`, `PendingEntryAllergen.kt`. The new `AllergenChip(status)` takes the existing status type and maps per b.2.
  `AllergenWarningSheet.kt` and `AllergenOverrideDialog.kt` belong to group B3 (they are sheets).
- Navigation chrome: `ui/navigation/FloatingPillBar.kt`, `PillBackdrop.kt`, `PillGeometry.kt`, `ScreenFrames.kt` (top bars), `NavTransitions.kt`
  only if needed; `Destinations.kt` (`TopLevelDestination` icons only; labels via strings, per decision g3). Add the dock FAB with an `onFabClick` hook that
  does nothing until phase 1b; add `LiveWorkoutPill` styling in `ui/screens/workout/SessionBar.kt` (the only non-shared file here, because it is global chrome).
- `ui/motion/*`: only to add the lime tab recolour; keep tokens.
- Tests: update any unit test that asserts colours or pill geometry (`PillGeometry`, if the drag geometry is dropped, per decision g4); add a
  token test that every status pair meets 4.5:1 contrast (pure Kotlin helper in `domain/` or `ui/theme` test source set; no Android imports).
- Preview or screenshot: a debug-only "Design kit" screen is optional and must not ship in release. If added, put it under `src/debug`.

Acceptance: app builds, every existing screen renders in the new theme without code changes (colours, fonts and shapes flow through),
light and dark both ok, `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` clean (0 errors), `MigrationSchemaTest`
unchanged (no schema work), fonts bundled with licence files, no new Maven dependency.

### Phase 1b: shell and IA wiring (same agent as phase 1 or a second single agent; after the owner's decisions)

Files: `ui/navigation/IntakeNavHost.kt`, `Destinations.kt`, new `ui/components/QuickLogSheet.kt` (shell with a list of tile
descriptors; destinations injected), the Food tab host (segmented control hosting `NutritionHomeScreen`, `PlanScreen`, `RecipesScreen`
and a Library hub), `ShellPolicy.kt` and `TabPolicy.kt` (tests). Strings for labels. This is the only place routes change.

### Phase 2: screen groups (parallel; each only its own files)

Each group restyles with the Phase 1 components, deletes local one-off styles, and changes no behaviour. Replace literal dp, colours
and fonts with tokens. Add a before/after screenshot only if the emulator is available (`docs/HANDOFF.md` section 4); say what was seen on the
emulator and what was not seen on a device.

| Group | Branch | Files |
| --- | --- | --- |
| **A Today** | `agent/design-v6-today` | `ui/screens/today/*` (`TodayScreen.kt`, `LogEntrySheets.kt`, `HydrationAdvanced.kt`, view models untouched except UI state for the day strip), `ui/SharedDay.kt` if the day strip needs it. |
| **B1 Food: Diary, Log food, Detail, Drinks** | `agent/design-v6-food-log` | `ui/screens/nutrition/*`, `ui/addfood/LogFoodScreen.kt`, `RecipeRows.kt`, `ui/screens/fooddata/FoodDetailScreen.kt`, `ui/screens/drinks/*`, `ui/barcode/*`, `ui/photo/*`, `ui/camera/*`, `ui/images/ProductImages.kt`. |
| **B2 Food: Plan, Recipes, Library** | `agent/design-v6-food-plan` | `ui/screens/plan/*`, `ui/screens/recipe/*`, `ui/screens/hub/*`, `ui/screens/shopping/*`, `ui/screens/templates/*`, `ui/screens/menu/*`, `ui/screens/swap/*`, `ui/images/RecipePhoto.kt`. |
| **B3 Allergens** | `agent/design-v6-allergen` | `ui/screens/allergen/*`, `ui/allergen/AllergenWarningSheet.kt`, `AllergenOverrideDialog.kt`. Allergen semantics test must still pass. |
| **C Train** | `agent/design-v6-train` | `ui/screens/train/*`, `ui/screens/workout/*` (except `SessionBar.kt`, done in phase 1), `ui/screens/videos/*`. |
| **D Insights** | `agent/design-v6-insights` | `ui/screens/progress/*`, `ui/screens/checkin/*`, `ui/screens/report/*`, `ui/screens/body/*`, `ui/screens/performance/*`, `ui/screens/photos/*`. |
| **E You, Settings, Onboarding** | `agent/design-v6-you-settings` | `ui/screens/you/*`, `ui/screens/profile/*`, `ui/screens/settings/*`, `ui/screens/ai/*`, `ui/screens/backup/*`, `ui/screens/health/*`, `ui/screens/more/*`, `ui/onboarding/*`. |
| **F Wear OS** | `agent/design-v6-wear` | `wear/**` only. Own small token file; do not depend on phone `ui/theme`. Reads `design/mockup-v6/screens/Watch.html`. Same applicationId and signing rule unchanged. |

Dependencies: B1 and B2 both host content inside the Food segments added in 1b. A to F can start as soon as phase 1 has merged;
B2, B1, D depend on 1b only for where their screens are reached from, not for how they look.

Merge order: phase 1, then 1b, then groups in any order (disjoint files), then a final integration agent runs full verify, fixes
seams (spacing between groups, any component a group had to fork) and updates `docs/TASKS.md`, `RESUME.md`, `docs/HANDOFF.md`.

Shared-file rule for phase 2: if a group needs a new shared component or a change to one, it does **not** edit `ui/components`; it
adds a small private composable in its own folder and lists it in its PR for the integrator to promote.

### Verification (every group)

`./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug` (lint 0 errors), `:wearprotocol:test :wear:lintDebug` for F,
`MigrationSchemaTest`. Check at font scale 1.5, in landscape, in dark and light, with imperial units and metric units.

---

## (f) Rules for implementers

1. **Visual and layout only.** Keep all behaviour, data, route arguments, settings keys and copy. Do not add features the mockup
   draws that do not exist (see d). Where the mockup clearly adds or moves something, it is listed in section g and waits for an
   owner decision; until then keep the current behaviour and place.
2. **Copy.** Keep every existing string. Do not reword to match the mockup (the mockup abbreviates and has sample copy). The only copy
   changes allowed are the owner-approved renames (Food, Insights).
3. **Allergens (rule 2 of CLAUDE.md).** Allergen logic stays local and deterministic. A model or an import can raise a warning, never clear one.
   Chip mapping is b.2. Never show a green or neutral chip for a row that was not read; "Checking…" is never blank and never green; Contains
   is never softened. "Log it anyway" stays the secondary, danger-styled action behind "Don't log it". Do not touch `domain/allergen`.
4. **AI (rule 1).** Every AI action shows the `ai` chip and runs only on an explicit tap or a switch the user turned on. No AI default
   changes. The app is complete with no network, no account, no key.
5. **Nothing hard-deleted, schema untouched.** This work changes no Room entity, DAO or migration. If you find you need one, stop.
6. **Imperial-friendly.** Every number the mockup shows in lb, in, fl oz or oz is sample data; render with the user's unit setting
   (weight kg or lb, length cm or in, drinks ml or fl oz, portions g or oz). Never hard-code "lb", "in", "fl oz" or "kg" in a layout;
   column headers such as "lb" in the set row follow the setting. Allow wider text for unit strings (fl oz is wider than ml) and use tabular numbers.
   Stored values remain kg/cm/per 100 g. Do not re-derive stored values from displayed ones.
7. **Font scale 1.5 and landscape.** No fixed heights on text containers: use `heightIn(min)` and `wrapContentHeight`. The dock, chips,
   segmented control, set row (switch to a two-line layout: row 1 set number and Previous, row 2 weight/reps/tick), stat tiles and 3- and 4-column grids
   must reflow (grids drop to 2 columns, 1 column on the narrowest at scale 1.5). Segmented controls scroll horizontally rather than truncate.
   Landscape and tablets use `ui/adaptive/ReadableWidth` (max content width) and the rail; do not stretch cards to full width. Check at 360 dp width.
8. **Dark and light both.** Use tokens only. No hard-coded hex outside `ui/theme`. Test every screen in both themes; verify lime-on-white never
   happens (the hero/lime-card pairings in a.1).
9. **Accessibility.** 44 dp touch targets; status never by colour alone (chips carry words; the Fits chip has a check icon); keep existing content
   descriptions and semantics (tab `selected`, `Role.Tab`); decorative icons `contentDescription = null`; contrast per a.1; honour Reduce motion.
10. **Components, not copies.** Use the phase 1 components. Do not fork a card/chip/button inside a screen file; if a component lacks something, see the
    shared-file rule in section e.
11. **Fonts.** Space Grotesk for titles and numbers, Inter for the rest. Do not introduce a third family. No downloadable fonts.
12. **Secrets, personal files, dependencies (rules 6-8).** No new Maven dependency (icons are vector drawables, fonts are bundled files). No literal U+FEFF in
    source. Do not commit `release/`, keystores, `local.properties`, `.env`, `*.db`. Do not copy personal data from anywhere into screenshots or tests.
13. **Do not claim device results.** The emulator is not a phone. State what was seen on an emulator, what only compiled, and what was not seen on a device.
14. **Process.** Branch per group, claim in `docs/TASKS.md`, merge the integration branch often, update `RESUME.md` and `docs/TASKS.md`, keep model identifiers out of
    code, docs and commit message bodies; keep the attribution trailers.

---

## (g) Behaviour and IA changes needing an owner decision

1. **Quick log sheet** behind a new centre + (11 tiles plus one-tap water): new global entry point (d1). Which tiles, and are Jump test and NFC tag shown only when their features are on?
2. **Food segments** Diary / Plan / Recipes / Library and the removal of the "More" list; Library as a new hub of Templates, Shopping, Eating out, Restaurant menus, My foods and Drinks (d2).
3. **Renames** Nutrition to Food and Progress to Insights, including tour, tab-tip and any notification text (d4).
4. **Dock instead of the drag-select pill**: static five-slot dock, no drag indicator (d5). Rail on wide screens stays.
5. **Today day strip** replacing previous/next arrows and the "Back to today" chip; how to reach days older than a week (d6).
6. **Settings home in four sections**; which of the twelve groups sit in which (d7).
7. **Insights additions**: Progress photos card on Insights (also on You), "Log weight and measurements" tile replacing the Log weight button (d8).
8. **AI rows** "Daily insight", "Reword my check-in", "Refresh saved menus weekly" shown under "Runs on a schedule" in Settings AI: verify they exist today; keep or drop (d13).
9. **Hero card in dark theme** (a.1): PROPOSED dark `hero` token instead of an inverted ink; approve or choose another.
10. **Allergen palette**: replacing the current deep red/amber with the mockup's `bad/warn` pairs (a.1). Semantics unchanged; confirm the colour change.
11. **Macro colours change meaning**: protein is now blue (was green), carbs orange (was amber), fat violet (was clay), water cyan. Confirm, since users have learned the old colours.
12. **Launcher icon, splash and notification icons** are not in the mockup; leave as they are unless the owner supplies a direction.
13. **Fonts**: bundling Inter and Space Grotesk (OFL 1.1) and dropping Manrope and DM Sans; Credits and licences gets two entries.

## Appendix: files in this brief's inputs

- `design/mockup-v6/screens/intake.css` is the full class list (surfaces, controls, navigation, overlays) and the two token blocks.
- `design/mockup-v6/screens/*.html` use `<x-dc>` wrappers and inline styles; treat them as picture data. The PNGs in `shots/` were rendered with a
  headless browser at 412 px wide, scale 1, full page height; Space Grotesk and Inter were loaded from Google Fonts for the render.
- `design/mockup-v6/source/canvas.json` holds each board's position and size on the design canvas (390 px phone boards; 1440 px for Main, Map, Onboarding). Not needed for
  implementation.
