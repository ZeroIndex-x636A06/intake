# QA playbook: driving Intake end to end in the emulator

For the agents that run QA passes. The loop is: test, log, fix, retest, until a full pass finds nothing wrong in layout,
function or context. "Context" means the content is right: a food shown as dairy-free that has dairy is wrong, a total that
does not add up is wrong, copy that describes a feature that is gone is wrong.

What is here:

| Path | What |
| --- | --- |
| `seed.sh` | Install a debug APK, wipe data, import the seed through the app's own Import, end on Today |
| `seed/generate.py` | Builds the seed backup (invented data) for the emulator's date and time zone |
| `seed/intake-qa-seed.json` | One generated copy, for diffing the format. `seed.sh` regenerates its own |
| `files/` | Test PDFs to push to the emulator. **Git-ignored, never committed.** `fetch-files.sh` re-fetches |
| `CHECKLIST.md` | Every screen and flow, by area. A pass walks part of it |
| `../../docs/QA-LOG.md` | Where findings go, and the format |
| `../cloud/` | `start-emu.sh`, `emu-ready.sh`, `emu-ui.py`, the Maven mirror (see `docs/HANDOFF.md` section 4) |

Rules that apply to the whole app (CLAUDE.md): no hard deletes, allergen safety is local and deterministic, never print or
commit `local.properties`, keystores or `release/`. Do not use anything from `release/` as test data. If you fix app
code, follow CLAUDE.md (branch `agent/<task>`, full verify before merge). A pure QA pass only writes the log and
screenshots.

## 1. Which emulator

| AVD | State |
| --- | --- |
| `phone30` | Android 11, x86_64, 1080x2280. **The one that works.** The default in `start-emu.sh`. Cold boot to a usable system takes about 2.5 to 5 minutes, and the first launch of a freshly installed app takes about a minute |
| `phone35` | Android 15 (API 35, Google APIs, x86_64, Pixel 6 profile, software GPU). Image installed and AVD created, **but it cannot start in this container: the emulator insists on a 6 GB userdata partition (7.4 GB free needed) and the volume has 4.7 GB free.** Freeing about 3 GB (the unused `system-images/android-35/aosp_atd` and `.../default` images are 3.4 GB, and `/root/*.apk` are old builds) lets it start. It has not been booted, so its speed is unknown. Run `tools/cloud/start-emu.sh phone35` after freeing space, then record the boot time in `docs/HANDOFF.md` section 4 |

Why API 35 matters: `PdfRenderer` text extraction (API 35+) and a modern Health Connect. On `phone30` neither can be
exercised; say so in the log instead of calling them passed.

Start, wait, check:

```sh
tools/cloud/start-emu.sh            # or: start-emu.sh phone35 ; AVD=phone35 start-emu.sh
tools/cloud/emu-ready.sh            # waits for system services to be stable for a minute, sets animations off
adb devices                         # emulator-5554 device
```

Always start it detached (the scripts use `setsid nohup`), or it dies with your shell. Do not run Gradle while testing:
the build starves the emulator (no KVM, software rendering). Build first, then test.

```sh
cd /home/user/intake
cd /root/mvnproxy && (pgrep -f proxy.py >/dev/null || setsid nohup python3 proxy.py > proxy.log 2>&1 < /dev/null &); cd -
flock /root/gradle.lock ./gradlew --no-parallel --max-workers=2 :app:assembleDebug
```

Stop it when you finish: `adb emu kill` (then `pgrep -f qemu-system` should print nothing). Leave it stopped.

Disk is tight (about 4.7 GB free). Do not leave big screenshots, APKs or `adb bugreport` files around.

## 2. Install, seed, screenshots

```sh
tools/qa/seed.sh                      # installs app/build/outputs/apk/debug/app-debug.apk, wipes data, imports the seed
tools/qa/seed.sh --no-install --shots # re-seed without reinstalling, and write design/qa/seed-*.png
```

It takes 10 to 20 minutes on `phone30` (the first launch after `pm clear` alone is about 5). What it does: installs the APK (`install -r -g`) and runs
`cmd package compile -m speed -f` (without it the first screens take over a minute); generates the seed for the device's
date and zone; pushes it to `/sdcard/Download/intake-qa-seed.json`; `pm clear`; skips the welcome; You, then Settings,
then Sync and export, then Import a backup; picks the file; taps Import; relaunches on Today.

The app package is `com.zeroindex.intake.debug` (debug build). The `preview` build (`com.zeroindex.intake.preview`) can
sit beside it. `seed.sh` stops the preview build because two apps slow the emulator.

**Picker quirk.** On this image DocumentsUI ignores `input tap` on file rows (the drawer and buttons work). Use
Search, type the file name, Enter, then `input keyevent 20` (DPAD_DOWN) and `66` (ENTER). `seed.sh` does this. If a
test needs to open a file from the picker (a PDF menu import, a photo), do the same.

**Midnight.** The seed is dated to the device's today. If the device clock crosses midnight after seeding, Today moves
to an empty day (this bit the first run, at 00:00 UTC). The emulator's zone is UTC; before a long pass set one where it is
not near midnight, then re-seed: `adb root; adb shell setprop persist.sys.timezone America/New_York`. (An emulator
restart goes back to UTC.) `seed.sh` reads the zone and date from the device each time.

Re-seed whenever you want a known state; a pass that changes data (deleting a recipe, changing units) should end with
`seed.sh` again. Settings values are part of the seed (units, profile, targets), so units come back to imperial.

### What the seed contains

Invented user "Sam Tester" (display name), male, born 1990, 183 cm, about 195 lb. Imperial units (lb, in, fl oz), portions in
grams. Target 2,260 kcal, 170 g protein, 245 g carbs, 70 g fat, set by hand (`auto_targets` off). Goal 185 lb. Profile
phase Cut, pace Gentle, 4 training days. Equipment "Pick my own" with nine home-gym items (Olympic barbell, plates, power
rack, adjustable bench, adjustable dumbbells, pull-up bar, dip station, bands, kettlebells).

| Area | Rows |
| --- | --- |
| Allergen | Dairy, **Strict**, note "Milk allergy. Strict." (the app's keywords are its own seed) |
| Foods | 36: 20 everyday foods (spaghetti, oats, almond milk, homemade chili ...), 10 dairy or hidden-dairy or may-contain foods, 4 seeded drinks (same ids as the app's own), 2 custom drinks |
| Food log | 21 days, about 11 entries a day with 3 meals, snacks, drinks (coffee with sugar, tea with oat milk). Recipe servings logged without a food id |
| Dairy traps | See below |
| Water | 121 hydration rows, 3 to 6 glasses a day plus the drinks |
| Recipes | 5: Chicken fajita bowls (dairy-free), Baked mac and cheese (cheese), Salmon sweet potato and broccoli (dairy-free), Turkey chili with sour cream (yogurt topping), Grandma's vegetable soup (no nutrition listed, no ingredients matched) |
| Meal templates | "Usual breakfast", "Post-workout shake" |
| Custom drinks | "Coffee, black + 3 tsp sugar", "Black tea + 2 tbsp oat milk" (real `CustomDrinkRecipe` JSON) |
| Weigh-ins | 17 over 35 days, a 14-day gap (20 to 7 days ago), a few skipped days, waist and chest and arm on three of them |
| Training | Program "Home gym upper/lower" (Upper A, Lower A, Upper B, Lower B, 17 exercises), 10 exercises, 6 finished sessions (19, 17, 14, 12, 7, 5 days ago), 87 sets with warm-ups and RPE, weights rising a little in the second week |
| Eat out | 2 saved options (PLAN_B origin, what "Save" in Healthier nearby writes): a burrito bowl (CLEAR) and a chicken salad (CHECK_LABEL) |
| Menu | One imported text menu, "Sunrise Diner (invented)", 8 items. Backups do carry menu sources and items, so no hand import is needed. The verdicts are the seed author's judgment, not the engine's: check them (QA I.5) |
| Not in the seed | Meal plan, shopping list, progress photos (files are never in a backup), saved videos, health summaries, coach state, AI usage |

Dairy traps (what a correct app does):

- Logged with "Logged despite: dairy" in the name and serving label (the "Choose anyway" result): pepperoni pizza 14 days
  ago, cheddar 11 days ago, mac and cheese recipe serving 9 days ago, Greek yogurt 6 days ago.
- Logged plain, before the allergy was strict (no override text): whey protein 19 days ago, milk chocolate 18 days ago.
- Logged plain, **hidden dairy**: ranch dressing (buttermilk, no milk tag) 3 days ago. The app must still flag it when
  looked at, even though it was logged without a warning.
- Foods whose panel says "may contain milk" (dark chocolate) or lists whey isolate (protein bar): must be may-contain
  and flagged, not clear.
- Plant milks (almond, oat) are check-label notes, not hard blocks.
- Turkey chili with sour cream: the recipe row says CONTAINS (Greek yogurt topping). It was logged 1 day ago without an
  override note, an inconsistency a QA agent may flag (the entry has no "Logged despite" text).

## 3. Driving the UI

Screenshots and taps go through adb. Nothing else is needed.

```sh
adb exec-out screencap -p > design/qa/q1-3-today.png     # full-size PNG, 1080x2280
export EMU_SHOTS=design/qa; python3 tools/cloud/emu-ui.py shot name   # half-size 540x1200 into $EMU_SHOTS/name.png
```

Use `adb exec-out screencap -p` for findings (full size). Check disk: a full-size PNG is 150 to 250 KB.

`tools/cloud/emu-ui.py` (a copy of the `ui.py` in /root; reads `ADB` and `EMU_SHOTS`):

| Command | What |
| --- | --- |
| `dump` | Every node with text or a content description, with its centre `x y`. Off-screen nodes show `0 0` |
| `tap "text"` `[n]` | Taps the node whose text equals (else contains) "text"; `n` picks the nth match |
| `tapxy x y` | Tap at coordinates |
| `text "str"` | Types text (spaces become `%s`; `adb shell input text` cannot type every symbol) |
| `back` | Back key |
| `swipe up` or `down` | Scroll the list |
| `shot name` | Half-size screenshot to `$EMU_SHOTS/name.png` |
| `crash` | The last 3000 characters of the AndroidRuntime errors in logcat |

Finding elements: `dump` is the first tool (it runs `uiautomator dump`, then reads the XML). For bounds, resource ids,
checked or enabled state, or clickable flags use the raw XML:

```sh
adb shell uiautomator dump /sdcard/u.xml && adb exec-out cat /sdcard/u.xml | python3 -c "import sys,re; ..."
```

Other useful inputs: `adb shell input swipe 540 1800 540 600 600` (scroll), `input keyevent 4` (back), `66` (enter),
`61` (tab), `20` (down). Rotation: `adb shell settings put system accelerometer_rotation 0; adb shell settings put
system user_rotation 1` (landscape; `0` portrait). Dark theme: `adb shell cmd uimode night yes` (`no`). Font size:
`adb shell settings put system font_scale 1.3` (1.0 normal), display size: `adb shell wm density 540` (`wm density reset`).
Time zone: `adb shell setprop persist.sys.timezone Asia/Tokyo` and restart the app. Back to the home screen:
`adb shell input keyevent 3`.

**Pace.** The emulator is slow: one action every 10 to 20 seconds, and `dump` itself takes 10 to 60 seconds. Queued
input makes Android kill the app as "not responding": that is the emulator, not the app. A real hang shows the same way,
so read `/data/anr` (`adb root` first) before deciding which it was. After a tap, wait for the next screen
(`dump | grep` for a word on it) rather than sleeping a fixed time.

**Drag and long press** (reorder, swipe-to-delete): `adb shell input swipe x1 y1 x2 y2 duration_ms`, with a duration of 600
or more for a long press then drag.

### Crashes, ANRs, logcat

Capture them while the emulator is still running, because a stopped emulator loses the buffer.

```sh
adb logcat -c                                          # before a flow
adb logcat -d -b crash > /tmp/crash.txt                # after: crashes
adb logcat -d | grep -E "FATAL EXCEPTION|ANR in com.zeroindex|StrictMode|Process: com.zeroindex"
adb shell dumpsys activity processes | grep -A2 zeroindex   # is it alive
adb root && adb pull /data/anr /tmp/anr                # traces of an ANR (the emulator's own ANRs of launcher3 or systemui are noise)
```

Decide: if the ANR is `com.zeroindex.*` and the trace's main thread is in app code, it is a finding (S1). If it is
`launcher3`, `systemui`, `phone`, or the app was killed during heavy input, it is the emulator; note it in the pass
header, not as a finding. Never paste a key, token or the contents of `local.properties` into the log.

Also grep logcat for any secret you typed into the app (a fake API key): it must not appear (CLAUDE.md rule 6).

## 4. What cannot be tested here

| Thing | State in this setup |
| --- | --- |
| **AI (OpenRouter)** | No key. Test only the no-key state: the AI switch, its explanation, that every AI button says it needs a key and does nothing else, that no AI wording appears on a screen where nothing ran, and that the app works fully with AI off. A fake key can be saved to test "invalid key" copy, nothing more |
| **YouTube / Videos** | No key. Test the no-key state of Train > Videos, Video detail, Make a workout, and Settings > Videos and API keys. Saved videos from a backup show only ids and a snapshot |
| **Nearby (Healthier nearby, Eat out nearby)** | No real location. Test the "needs location" and "no network" states. `adb emu geo fix <lon> <lat>` can set a location, but there is no network for the place search, so results stay empty. Say so |
| **Weather for water** | No location and no network: only the opt-in and the failed or empty state |
| **Wear OS watch** | No watch. The phone side logs "No watch link: ApiException" at startup; that is expected. Do not test the watch app |
| **Camera, barcode scan, photo estimate** | No camera hardware beyond the emulator's virtual scene. Test permission prompts and the cancel path; scan with the virtual scene finds nothing |
| **NFC** | No NFC. The meal template NFC write must say what it needs and not crash |
| **Open Food Facts, USDA, recipe URL import, sync server** | No outbound network from the app unless the container proxy lets it through. Test the offline failure messages. The bundled `food_ref_v1.db` and the local USDA data work offline |
| **Notifications and reminders** | The notification shade can be read with `adb shell cmd statusbar expand-notifications` and `dumpsys notification`. Reminder times can be set to now plus 2 minutes. Doze and exact-alarm limits of a real phone are not reproduced |
| **Health Connect** | Android 11 (`phone30`): Health Connect is not part of the system, the app should say it is unavailable or ask to install it; the real permission sheet and records cannot be exercised. Android 14+ (`phone35`) has it built in, so permissions, a read of zero records and a write can be tried, but with no data source there is nothing to read. Record exactly what you saw |
| **PdfRenderer text** | API 35+ only (`phone35`). On `phone30` a PDF menu import can only use whatever fallback the app has; log "PDF text extraction not testable on API 30" rather than pass or fail it |
| **Real device things** | Haptics, the exact feel of animations, performance on a phone, battery, a real Samsung's One UI back gesture and font settings, foldables, cutouts other than the emulator's. Never claim these were seen |

Say in every pass what was seen on the emulator and what has not been seen on a device.

## 5. Test files

`tools/qa/files/` (git-ignored; `fetch-files.sh` re-fetches what it can):

| File | Source |
| --- | --- |
| `mcdonalds-ireland-allergen-chart.pdf` | McDonald's Ireland allergen chart (leaflet `ROI_p09_LEAF_33204_DIGI1`, 2 pages), supplied by the owner. Public restaurant document. Not fetchable by a stable URL here: ask for it again or save it from the McDonald's Ireland allergen page |
| `osu-mcdonalds-usa-nutrition-facts.pdf` | `https://fcs.osu.edu/sites/fcs/files/imce/PDFs/mcdonalds-nutrition-facts.pdf` (10 pages), fetched with an honest User-Agent: `curl -L -A "Intake-QA/1.0 (personal QA of an Android app; public document)" -o ... URL` |

Push and use:

```sh
adb push tools/qa/files/mcdonalds-ireland-allergen-chart.pdf /sdcard/Download/
adb push tools/qa/files/osu-mcdonalds-usa-nutrition-facts.pdf /sdcard/Download/
```

then Nutrition > Eating out > menu sources (or the menu import entry) > PDF, pick the file with the Search and DPAD route
above. Expect the Ireland chart to parse as an allergen chart (columns from `AllergenChartParser`), and the OSU one as
a nutrition table. Check every parsed item against the PDF by eye: wrong rows are findings (S2), a milk column read as
"clear" is S1. These are public documents: do not commit them and do not edit them.

For a progress photo or exercise photo, push any invented image (a plain PNG made with Python) to `/sdcard/Download/`.

## 6. Running a pass

1. Read `docs/HANDOFF.md`, `docs/QA-LOG.md` (open findings first) and the area you own in `CHECKLIST.md`.
2. Start the emulator, build if the app changed, `tools/qa/seed.sh`.
3. `adb logcat -c`. Walk the area line by line. Look at each screen (screenshot) before judging it. Check numbers
   against the seed (the dairy traps and the figures above are known).
4. For each defect, write a finding in `docs/QA-LOG.md` (format there), with a screenshot in `design/qa/`.
5. Check logcat for crashes and ANRs before ending, and put the result in the pass header.
6. Commit the log and screenshots (small ones only; delete duplicates). Push to your branch.
7. After fixes land, retest the same lines with the same seed and update each finding's status. Re-run the allergen block
   (CHECKLIST I) after any fix that touches foods, recipes, drinks, menus or the engine.
8. Stop the emulator when done: `adb emu kill`.

Stop condition for the loop: one full pass over every area with no new S1, S2 or S3 finding, the allergen block
clean, and every earlier finding fixed or explicitly marked wontfix with a reason.
