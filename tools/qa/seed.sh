#!/bin/bash
# Installs a debug APK, wipes app data, imports the QA seed through Settings > Sync and export > Import a backup,
# and ends with the app on Today. Needs a booted emulator (tools/cloud/start-emu.sh, emu-ready.sh).
#
#   tools/qa/seed.sh [path/to/app-debug.apk] [--no-install] [--shots]
#
# - default APK: app/build/outputs/apk/debug/app-debug.apk (build it with :app:assembleDebug first)
# - --shots-only: skip install and import, just relaunch on Today and take the screenshots
# - --shots: also writes design/qa/seed-today.png, seed-nutrition.png, seed-progress.png, seed-train-log.png
# The seed is generated for the emulator's own date and time zone (tools/qa/seed/generate.py), so Today has data.
# All of it is invented. It never reads release/.
#
# Why DPAD keys in the file picker: on the API 30 emulator DocumentsUI ignores `input tap` on file rows (drawer and
# buttons work, file rows do not). Search, then DPAD_DOWN and ENTER, picks the file reliably.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ADB=${ADB:-/root/android-sdk/platform-tools/adb}; export ADB
UI="python3 $ROOT/tools/cloud/emu-ui.py"
PKG=com.zeroindex.intake.debug
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"; INSTALL=1; SHOTS=0; IMPORT=1
for a in "$@"; do case "$a" in --no-install) INSTALL=0;; --shots) SHOTS=1;; --shots-only) SHOTS=1; IMPORT=0;; *) APK="$a";; esac; done
export EMU_SHOTS=${EMU_SHOTS:-$ROOT/design/qa}
mkdir -p "$EMU_SHOTS"
SEED=/sdcard/Download/intake-qa-seed.json

say() { echo "[seed $(date +%H:%M:%S)] $*"; }
dump() { timeout 150 $UI dump 2>/dev/null; }
# wait_for "text" [tries]: poll the UI dump until a node's text contains $1 (case-insensitive)
wait_for() { local i; for i in $(seq 1 ${2:-12}); do dump | grep -qi -- "$1" && return 0; sleep 5; done; say "timed out waiting for: $1"; return 1; }
tap() { timeout 150 $UI tap "$1" ${2:-0} >/dev/null 2>&1; }
focus() { $ADB shell dumpsys window | grep mCurrentFocus | tail -1; }

$ADB get-state >/dev/null 2>&1 || { echo "no emulator: run tools/cloud/start-emu.sh and emu-ready.sh first"; exit 1; }
TZ_DEV=$($ADB shell getprop persist.sys.timezone | tr -d '\r'); TZ_DEV=${TZ_DEV:-Etc/UTC}
DEV_DATE=$($ADB shell date +%Y-%m-%d | tr -d '\r')
say "device date $DEV_DATE zone $TZ_DEV"

launch() { $ADB shell am start -n $PKG/com.zeroindex.intake.MainActivity >/dev/null 2>&1; }

if [ $IMPORT = 1 ]; then
OUT=$(mktemp -d)/intake-qa-seed.json
python3 "$ROOT/tools/qa/seed/generate.py" --tz "$TZ_DEV" --end "$DEV_DATE" --now-hour "$($ADB shell date +%H | tr -d '\r')" -o "$OUT" || exit 1
$ADB push "$OUT" $SEED >/dev/null && say "pushed seed to $SEED"

if [ $INSTALL = 1 ]; then
  say "installing $APK"; $ADB install -r -g "$APK" | tail -1
  $ADB shell cmd package compile -m speed -f $PKG >/dev/null 2>&1   # without this the first launches are very slow
fi
for k in window_animation_scale transition_animation_scale animator_duration_scale; do $ADB shell settings put global $k 0; done
$ADB shell settings put global hide_error_dialogs 1
$ADB shell am force-stop com.zeroindex.intake.preview 2>/dev/null
$ADB shell pm clear $PKG >/dev/null
$ADB logcat -c

launch; say "launched; waiting for the welcome"
wait_for "Skip setup" 30 || exit 1
tap "Skip setup"
wait_for "You: profile and settings" 30 || exit 1
tap "You: profile and settings"
wait_for "Goals and plan" 20 || exit 1
tap "Settings"
wait_for "Sync and export" 20 || exit 1
tap "Sync and export"
wait_for "Import a backup" 20 || { $UI swipe up; }
dump | grep -q "Import a backup" && true
# the Import row can be below the fold
for i in 1 2 3; do dump | grep "Import a backup" | grep -qv "^ *0 *0" && break; $UI swipe up; sleep 2; done
tap "Import a backup"
wait_for "Search" 20 || exit 1
say "picker open; searching for the seed file"
tap "Search"; sleep 6
$ADB shell input text intake-qa-seed; sleep 5; $ADB shell input keyevent 66
wait_for "intake-qa-seed.json" 10 || exit 1
picked=0
for combo in "20 20 66" "20 66" "20 20 20 66" "61 66"; do
  for k in $combo; do $ADB shell input keyevent $k; sleep 1; done
  sleep 25
  if ! focus | grep -q documentsui; then picked=1; break; fi
done
[ $picked = 1 ] || { say "could not pick the file in the picker"; exit 1; }
wait_for "Import" 20 || exit 1
say "preview shown:"; dump | grep -E "changes|new," | head -30
tap "Import" 0
wait_for "Imported" 30 || { say "no 'Imported N changes' message"; dump | head -20; exit 1; }
dump | grep -i "imported"

fi

say "restarting to land on Today"
$ADB shell am force-stop $PKG; launch
wait_for "Log food" 30 || exit 1
# the Today tour starts after the welcome was skipped; it covers the screen
for i in 1 2 3; do
  if dump | grep -q "Skip tour"; then tap "Skip tour"; sleep 8; break; fi
  sleep 5
done
dump | head -12

if [ $SHOTS = 1 ]; then
  say "screenshots into $EMU_SHOTS"
  $UI shot seed-today
  tap "Nutrition" 0; wait_for "Eating out" 12; sleep 3; $UI shot seed-nutrition
  tap "Progress" 0; wait_for "check-in" 12; sleep 3; $UI shot seed-progress
  tap "Train" 0; wait_for "Recent" 12; sleep 3
  # the Train log is "All sessions", under Recent sessions at the bottom of Train > Workout
  for i in 1 2 3 4 5; do $UI swipe up; sleep 3; done
  tap "All sessions" 0; sleep 20
  $UI shot seed-train-log
  tap "Today" 0; sleep 10
fi
say "crashes since launch:"; $UI crash | head -5
say "done: app is on Today with the seed"
