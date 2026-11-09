# Exercise photos, the muscle map and custom exercises

Every exercise has an info button (library, picker, program builder) and the active workout opens the same
sheet when you tap the exercise name or its picture. The sheet shows, from top to bottom: start and end
photos (fading between them, or side by side; the fade stops under the reduce-motion setting), the muscle
map, how to do it, equipment, and your own history and estimated max.

Nothing here needs a network, an account or AI. All of it is bundled in the app.

## Where the photos come from

**Free Exercise DB**, <https://github.com/yuhonas/free-exercise-db>, pinned to commit
`f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5`.

- Licence: `LICENSE.md` in that repository is the **Unlicense** (public domain dedication). Checked on
  2026-11-09, both on the default branch and at the pinned commit. It is a compiled dataset (the maintainer's
  README says it was restructured from earlier community lists), so the original provenance of each photo was
  not independently traced. We accept that small risk. Do not mix in the paid dataset the README advertises;
  that one is not Unlicense.
- The credit is shown in the app (exercise library footer, the exercise sheet, and Food, Food database &
  credits) and here: *Exercise photos and steps: Free Exercise DB (github.com/yuhonas/free-exercise-db),
  public domain (Unlicense).*
- Muscle names in that dataset are coarse and are **not** used. Our own table (`ExerciseCatalog.kt` plus
  `MuscleMap.kt`) decides what each exercise works.

## What is bundled

| Item | Value |
| --- | --- |
| Catalog exercises | 94 |
| With photos | 85 (two photos each, start and end) |
| Without | 9, listed with reasons under `_unmatched` in `tools/exercise_image_map.json`. They keep the muscle map. |
| Photos | WebP, 480 px wide, quality 75, 170 files |
| Size | about 2.25 MB of photos (2.7 MB on disk with the index), budget 6 MB |
| Steps | 1 to 5 short steps per exercise in `exercises/exercises.json` |

Files live in `app/src/main/assets/exercises/` as `<catalog id>_0.webp`, `<catalog id>_1.webp` and
`exercises.json`. The catalog id is the same UUID `ExerciseCatalog` derives from the name
(`seedId("exercise", name)`).

## Rebuilding

The mapping is **hand-reviewed**, never fuzzy-matched: `tools/exercise_image_map.json` maps each catalog
exercise name to a Free Exercise DB id.

```sh
python3 tools/build_exercise_images.py --check   # the map covers every catalog exercise exactly once
python3 tools/build_exercise_images.py           # download at the pinned commit, convert, write the assets
```

The script needs Python 3, plus Pillow (or the `cwebp` tool) to write WebP. To take a newer upstream, review
the upstream diff and licence, change `SOURCE_COMMIT` in the script and the hash above, and rebuild.
`ExerciseImagesAssetTest` fails if an indexed image is missing, a file is stray, the map and the catalog
disagree, or the total passes 6 MB.

## The muscle map

`domain/training/muscles/MuscleMap.kt` (pure Kotlin) holds the regions and the hand-authored outlines;
`ui/components/MuscleMapView.kt` only draws them with theme colours (strong for the main muscles, light for
helpers; readable in light and dark). It follows the taxonomy proposed in
`docs/research/LIFTOSAUR-NOTES.md` section 6.2: **20 groups under 6 body regions**. The sheet shows the six
regions first and "Show detail" lights the fine groups.

| Region | Groups |
| --- | --- |
| Chest | upper chest, mid and lower chest |
| Back | lats, mid back, upper traps, lower back |
| Shoulders | front, side and rear delts |
| Arms | biceps, triceps, forearms |
| Legs | quads, hamstrings, glutes, hip abductors, inner thighs, calves |
| Core | abs, obliques |

An exercise stores a main muscle and a list of helper muscles (library names such as `back`).
`MuscleMap.highlightFor` turns those into regions and narrows a few broad ones by name (a lateral raise is
the side delt, a pulldown is the lats, an incline press is the upper chest).

**Seam for the weighted model.** The notes propose an `exercise_muscle` table with weights 1.0 / 0.5 / 0.25.
That is not built: this task keeps the simple main/helper model. `MuscleMap.highlightFor` is the single place
a per-exercise weighted lookup would plug in (it already returns main and helper regions), the region list is
the taxonomy the table would key on, and a table can be added to `migrateExercises_2_3` without touching
the generator.

## Your own exercises

Library and picker both have **Add your own exercise**. The form takes a name, an optional photo (camera or
gallery), the muscles (tap the body, or use the chip list), equipment, rest and optional how-to text (stored
in `exercise.notes`). Equipment uses the existing equipment list; the equipment taxonomy in the research note
is a later job.

**Your photo is not backed up.** It is scaled to 1024 px, turned upright, stripped of EXIF (so no location)
and kept in the app's private `photos` folder; only its file name is in the `exercise.photoPath` column. The
backup and sync formats carry rows, not files (see `docs/BACKUP.md`), so after a restore or on a second device
the exercise comes back without its photo. The form says so. Camera use asks for the camera permission only
when you tap "Take a photo".
