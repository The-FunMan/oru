#!/usr/bin/env bash
# Exits 0 only when Oru is the focused window on the device. If another window has focus it launches Oru once
# and checks again, so a tap or a capture never lands on a screen that is not Oru's.
#
# Uses the device named by ANDROID_SERIAL (adb reads it itself). With one device attached it can stay unset.
set -u
export MSYS_NO_PATHCONV=1

package=${ORU_PACKAGE:-app.oru.manager}

focused() {
  adb shell dumpsys window | grep mCurrentFocus | grep -q "$package"
}

for _ in 1 2; do
  if focused; then exit 0; fi
  adb shell monkey -p "$package" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 3
done

focused
