# Architecture

## Pieces

- **App** (`app/`): Kotlin, Jetpack Compose, Koin for injection, a foreground worker for the run. The package is still `app.morphe.manager` because the code started as Morphe Manager. The Oru product id is `app.oru.manager`. Two flavors: `oru` (the product) and `morphe` (parity with upstream, so upstream changes can be compared and merged). Shared code is in `main`, Oru-only resources and manifest in `src/oru`, debug-only helpers in `src/oruDebug`.
- **Patcher** (`patcher/`): a separate Gradle build, included from the root with `includeBuild`. It targets Java 11 and loads patch bundles. Patch bundles call its public API directly, so the API only grows. `:patcher:apiCheck` and `tools/parity/patch_api_guard.py` enforce that.
- **Native** (`native/`, `native-packager/`): Rust. `native` is the JNI index used while patching. `native-packager` packs and signs, and has a parity suite against the Kotlin path. Built for the four Android targets with NDK 28 and CMake 3.22.1 (the versions are in `.github/actions/setup-oru/action.yml`).
- **Site** (`site/`): static pages for GitHub Pages, deployed by `pages.yml` when `site/**` changes. `scripts/check-site.sh` guards links and forbidden words.

## The run pipeline

1. The user taps a card on Home. Home shows one verdict per app: ready, needs an APK, unsupported version, installed, update available.
2. The Focus screen opens. It shows the chosen version, the default patches, the signing note, and Expert controls when the user switched Expert on. A shared version gate (`ui/oru/sources/OruVersionGate.kt`) decides whether the installed or chosen version can be patched, and every screen uses it.
3. `PatcherWorker` (`patcher/worker/`) runs the patch: read the APK, decode resources, apply patches, write, sign, then install. It reports stages and counts. Decoding resources reports no progress, so the first percent of a run can sit still for a while.
4. The run screen (`ui/oru/run/`) maps the stages to one progress. The hexagon (`components/OruHexagonAssembly.kt`) fills from the bottom up, connected piece by piece; a run that is signed lights all pieces and only the install settles it into a check.
5. The status bar mirrors the run (`ui/model/RunLiveBar.kt`): stage segments in a Live Update, and a ready notification with an Install action that opens Focus. The action never installs directly.
6. Done is a receipt: a bounded, scrollable "What changed" list, the key notice on the first patch, and the install buttons.

Installers (`domain/installer/InstallerManager.kt`): the system installer by default, with the other installers offered only when the device has them. The install is always started by the user.

## Patch sources

A source is a URL to a patch bundle repository. `domain/bundles/SourceUrl.kt` normalises it (the `.git` form, case, trailing slashes) so the same source cannot be added twice. Adding a source probes it first and shows an inline error if it holds no bundle. Each app uses one source. Deleting a source in use names the affected apps and returns them to the default source. Sources with the same name are told apart by owner.

## Signing

Release builds are signed with the project key, which lives only in the `release` environment secrets and on the owner's machine. Local and PR builds use `-PsignAsDebug`. The patched apps are signed with a per-device key generated on first use; the first-patch notice explains that updates need the same device key. Key compromise handling is in [SECURITY.md](../../SECURITY.md).

## First run

A "Your first patch" card (`ui/oru/home/OruTourCard.kt`, state in `OruTourState.kt`) shows once, with Skip always visible. It stays hidden when anything is installed, patched elsewhere, saved, or the key notice was shown.

## CI and releases

`ci.yml` runs the app tests and lint, the debug-signed APK build and check, the patcher tests and API check, the patch API guard, the Rust crates and the Python tools. Releases are described in [docs/maintainers/RUNBOOK.md](../maintainers/RUNBOOK.md): dev prereleases from `dev`, stable through the Cut stable release workflow behind the `release` environment.
