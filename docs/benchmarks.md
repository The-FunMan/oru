# Speed measurement

How long Oru takes to patch an app, compared with another well-known patch manager, measured once on an emulator. It is one small test, not a promise. Read the limits before you quote it.

## Result

Same input, same patch bundle, same 96 patches, patching only (the install is not included).

| Run | Oru | Other manager |
|---|---|---|
| 1 | 93 s | 151 s |
| 2 | 86 s | 156 s |
| 3 | 91 s | 162 s |
| Mean | 90 s | 156 s |

On this test Oru finished in about 58% of the time, about 1.7 times faster. Oru's own timer on its finish screen read 82 to 85 s.

## Setup

- Emulator: the Android Emulator with a Pixel 10 Pro XL device profile, Android 17 (API 37.2, 16 KB page size) on an x86_64 image, 4 GB of RAM, 4 CPU cores and a 512 MB app heap limit, 1344 by 2992, hardware accelerated, run headless on a Windows PC. No builds ran on the PC during the timed runs, only light work such as screenshots over adb.
- Input: one stock APK of a video app, 206,810,938 bytes, SHA-256 `3b66f53a846e8a32268868c12c3072a69e305dc44a626ff5be63b3823dc6a823`. It is newer than the version the bundle lists as supported, so both apps were told to patch it anyway (experimental versions).
- Patches: the same stable patch bundle, version 1.47.0, in both apps, 96 of 102 patches selected, the default selection of each app.
- Oru: version 0.0.1 development build, the commit before the hexagon fix (`bd1df8ee`). Other manager: its 1.35.0 release, which reports itself as 1.35.1-dev.1. It is the app Oru was built from, so the maintainer can name it and its version on request.
- Order: one warm-up run in each app (not counted), then Oru, other, Oru, other, Oru, other. Each app was force-stopped before its run.
- Timing: wall clock from the tap on Patch to the finish screen ("ready" in Oru, "Patched" in the other manager), read by polling the screen about every 3 s, so each time is good to about 4 s. Both stop before the system installer.

## Limits

- One device type, an emulator, three runs each. A phone will give different times and could give a different ratio.
- The two apps define "finished" differently inside, even though both stop before the install.
- Raw results and screenshots are kept by the maintainer; the numbers above are copied from them.
- It says nothing about other apps, other bundles or other phones.

## Repeat it

1. Install both managers and put the same stock APK on the device.
2. Make both use the same bundle version and the same patch selection.
3. Patch in each app from "Use a file", alternating, and time each run.
