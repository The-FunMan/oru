# Device QA

How to test Oru on a real phone or an emulator without touching anything else on it. The owner names the device for each task. If none is named, use an emulator.

## Ground rules

- Touch only Oru (`app.oru.manager`) and the patched app under test. Never install, uninstall, clear or open any other package.
- Never enter a PIN, password or fingerprint. If the screen is locked, stop and ask the owner to unlock it.
- Never capture the launcher, Recents, the notification shade, other apps or any personal screen. Capture only Oru's own screens. If a screenshot shows anything personal, delete it at once.
- Read Oru's notifications from the system dump, not from the shade:

  ```sh
  adb -s "$ANDROID_SERIAL" shell dumpsys notification --noredact | grep -A60 'pkg=app.oru.manager'
  ```

- Do not press Back where it would leave Oru. Close sheets by swiping down from their title.
- Changing Oru's own theme or settings is allowed for a test; put them back afterwards.
- Name devices in notes by model only. Never write serial numbers into the repository, commits or issues.

## Setup

```sh
export ANDROID_SERIAL=<serial from adb devices>     # in your shell only
export MSYS_NO_PATHCONV=1                           # on Windows with Git Bash
scripts/dev/oru-focus-guard.sh                      # exit 0 only when Oru is the focused window
```

Run the focus guard before every tap or capture. It relaunches Oru once if another window has focus and fails if Oru still is not in front.

Install a build:

```sh
adb install -r app/build/outputs/apk/oru/release/*.apk
```

Debug-signed and release-signed builds use different keys, so one cannot update the other: uninstall Oru first when you switch (that deletes Oru's data, so only do it on a device the owner gave you for this).

Play Protect may offer a scan on install. Choose "Don't install app", then the system "Update this app?" prompt, then Update. A fingerprint request is for the owner: decline and ask.

## Capturing

```sh
adb -s "$ANDROID_SERIAL" exec-out screencap -p > shot.png
adb -s "$ANDROID_SERIAL" shell uiautomator dump /sdcard/ui.xml && adb -s "$ANDROID_SERIAL" pull /sdcard/ui.xml
```

Keep captures outside the repository. Before a capture goes anywhere public, check it for names, accounts, notifications and package names.

## Smoke test for a release candidate

Run on a device with the signed release build, after the release is built and before it is announced.

1. Install the release APK over nothing (fresh) or over a previous release (update).
2. First open: the first-run card shows once. Skip works. It does not come back.
3. Home shows one verdict per app. An app with an unsupported version says so in one wording everywhere.
4. Pick an app that is ready and tap it. Focus shows the version, the default patches and the signing note.
5. Start the run. The hexagon fills from the bottom up, stages are real, counts move, and Keep going in the background works. Cancel asks for confirmation and Keep patching is the primary button.
6. The ready notification names the app (never "Morphe") and its Install action opens Focus.
7. Install. The system prompt appears; confirm. Done shows What changed in a scrollable list with the buttons still reachable.
8. Settings: theme follows the system; the key backup notice is clear; Sources lists the default source.
9. Add a source with a bad address: the inline error appears and nothing is saved. Add one with a valid address, switch an app to it, delete it: the dialog names the affected apps.
10. Rotate the device and open it in dark and light themes. Nothing clips or overlaps the bottom pill.

Record the result in the release pull request or the release notes thread: build, device model, Android version, pass or fail per step.

## Speed measurements

Use [benchmarks](../benchmarks.md). Same stock APK, same patches and bundle, interleaved runs, at least three each. State the device or the emulator profile with the result.
