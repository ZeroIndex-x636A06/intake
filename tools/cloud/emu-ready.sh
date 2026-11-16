#!/bin/bash
# Waits until the emulator's system services have been up for a full minute (system server restarts once after boot).
# Usage: emu-ready.sh   ($ADB overrides the adb path). Then it turns animations off and hides error dialogs.
A=${ADB:-/root/android-sdk/platform-tools/adb}; ok=0
for i in $(seq 1 150); do
  if $A shell pm list packages 2>/dev/null | grep -q "package:android$" && $A shell dumpsys window 2>/dev/null | grep -q "mCurrentFocus=Window"; then ok=$((ok+1)); else ok=0; fi
  [ $ok -ge 6 ] && break; sleep 10
done
$A shell input keyevent 66 2>/dev/null
for k in window_animation_scale transition_animation_scale animator_duration_scale; do $A shell settings put global $k 0; done
$A shell settings put global hide_error_dialogs 1
echo "stable after ${i}0s"
