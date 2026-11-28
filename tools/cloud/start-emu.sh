#!/bin/bash
# Starts the emulator detached. Usage: start-emu.sh [avd]   (default: phone30; or $AVD). phone35 (API 35, google_apis) exists but needs ~7.4 GB free to create its userdata, see README
# Software rendering, no KVM. Wait for it with emu-ready.sh.
export ANDROID_HOME=${ANDROID_HOME:-/root/android-sdk} ANDROID_SDK_ROOT=${ANDROID_HOME:-/root/android-sdk}
AVD=${1:-${AVD:-}}
if [ -z "$AVD" ]; then
  AVD=phone30
fi
pgrep -f "qemu-system" >/dev/null && { echo "emulator already running"; exit 0; }
rm -f /root/.android/avd/$AVD.avd/*.lock
echo "starting $AVD"
setsid nohup $ANDROID_HOME/emulator/emulator -avd "$AVD" -no-window -no-audio -no-boot-anim -no-snapshot \
  -gpu swiftshader_indirect -accel off -cores 4 > /root/emu.log 2>&1 < /dev/null &
