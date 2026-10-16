# Product decisions

Settled choices, with the reason. Change one only on purpose, and update this page in the same pull request.

## Product

| Decision | Reason |
| --- | --- |
| Defaults over settings. A new user-facing setting needs a written reason a default cannot work. One-time flags are fine. | A short list of choices is the product. Every setting is a question the user has to answer. |
| One patch source per app. | Mixing sources per patch makes failures impossible to explain. |
| The app never downloads stock APKs. The APK step opens the download page for the chosen version in the browser. | Legal and trust: the user fetches the file themselves. |
| The Oru build does not show the name Morphe, except in credits, licences and branding file names. | Oru is independent of Morphe, with credit. |
| No auto-install. Installing is always a user action; the ready notification opens Focus. | The user must see what is installed. |
| Haptics only, no sound design. | Quiet by default. |
| Simple mode shows the current step and real counts, nothing else. Expert shows the controls, the log and per-step times. Simple has no "Patch anyway". | The mode picks how much to show; it is not a second app. |
| Visual richness is a goal. Do not cap effects to save CPU. | People spend minutes a week in the app, so the app should look as good as it can. |
| Stage and count text is real or absent. | Invented progress text is dishonest. |

## Words and claims

- The speed claim says "another well-known patch manager" and never names it. It gives the measured numbers (about 1.7 times faster on the emulator, 90 s against 156 s), the setup in the fine print, and a link to [benchmarks](../benchmarks.md). Do not repeat it without that fine print and do not extend it to real phones until it is measured there.
- Marketing text, the site and screenshots show no third-party app names. The README disclaimer list is the only place for them.
- Screenshots show nothing personal.
- The README is the only page with the "Use of generative AI" section.
- Disclaimers do not give legal immunity, and the pages do not claim they do. Real protection is key hygiene: a required reviewer on the `release` environment, secrets only in environments.

## Engineering

- The patcher public API is a compatibility surface: additive changes only.
- Dependencies stay on the newest stable release; the held-back items and the reason for each are in [dependencies.md](../dependencies.md).
- Libraries are used as published. No forks, no vendored copies of third-party code.
- Actions artifacts and caches are kept 5 days at most (`housekeeping.yml`).
- `main` is protected and receives only merges from `dev` through the cut workflow. `dev` is where work happens.
- Stable tags are `vX.Y.Z` and belong to Oru only. The inherited upstream tags were deleted from the repository so the version check and the version code ranges (major 0..20, minor 0..99, patch 0..999) stay meaningful.

## Open product questions

Kept in [open-items](../maintainers/open-items.md). Examples at the time of writing: the batch screen rebuild, the wording of the install-with scope, an explanation and undo for the Customized state, and finer progress during resource decoding (a patcher change).
