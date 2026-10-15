# Dev prerelease

A dev prerelease is a GitHub pre-release with the APK built from the `dev` branch. It is cut by `.github/workflows/release-dev.yml`, whose name is "Dev prerelease".

## Triggers (as the workflow file has them)

- `push` to the branch `dev`. The workflow runs on every push to `dev`, and each run either builds or skips (see "Skip rule").
- `workflow_dispatch`: a manual run from the Actions tab. Choose the branch `dev` in the "Run workflow" menu. The file has no inputs.
- The build job fails on any other branch, because the first step in `build` checks that the ref is `dev`.

## What the run does

1. `gate`: calls `ci.yml` (through `workflow_call`), the same jobs as on a pull request.
2. `build` (environment `dev-release`):
   - `.github/scripts/next-dev-version.sh` works out the tag, the version, the skip flag and `dist/RELEASE_NOTES.md`.
   - If not skipped: decodes the keystore into `app/keystore.jks`, sets the version in the runner's copy of `app/gradle.properties` (not committed), builds `:app:assembleOruRelease`, runs `verify-apk.sh --release`, copies the APK to `dist/oru-<version>.apk` and writes `dist/SHA256SUMS.txt`.
   - Removes the keystore (an `if: always()` step), then uploads `dist/` as the artifact `dist`, kept 3 days.
3. `publish` (write permissions only here): attests the build provenance of `dist/oru-*.apk`, then runs `gh release create <tag>` with the APK, `SHA256SUMS.txt`, `--target $GITHUB_SHA`, `--prerelease`, the title `Oru <version> (dev)` and the notes file.

The tag is created by `gh release create`, not by hand. Do not create `vX.Y.Z-dev.N` tags yourself.

## Version rules

- `app/gradle.properties` holds the base `version = X.Y.Z`, a plain release number. The dev script rejects anything else. The file currently holds `0.0.1` on this branch.
- Ranges, checked by `next-dev-version.sh` and by `app/build.gradle.kts`: major 0..20, minor 0..99, patch 0..999, dev number 1..998.
- Dev tag: `vX.Y.Z-dev.N`. `N` is one above the highest existing `vX.Y.Z-dev.*` tag, so it restarts at 1 for each new base.
- The run fails when the tag `vX.Y.Z` already exists. Bump the base after every stable release (see `release-stable.md`).
- versionCode (from `app/build.gradle.kts`): `major * 100000000 + minor * 1000000 + patch * 1000 + s`, where `s` is the dev number for a dev build and 999 for a stable one. Examples: `1.2.3-dev.5` gives 1002003005, `1.2.3` gives 1002003999. The largest possible code is 2099999999.
- versionName is the full version text, for example `0.0.1-dev.1`.
- The APK name is `oru-<version>.apk`, for example `oru-0.0.1-dev.1.apk`.

## Skip rule

On a push, the run skips the build when no build input changed since the last dev tag. The inputs are `app`, `native`, `native-packager`, `patcher`, `gradle`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` and `gradlew`. A manual run always builds. The `gate` still runs on every push.

## Release notes

- After an earlier dev tag: `git log --no-merges --pretty='- %s (%h)' <last dev tag>..HEAD`, one line per commit.
- For the first dev build of a base: the single line `First development build of Oru <version>, from commit <sha>.`

## Cut a dev release

1. Merge or push the change to `dev`. The push starts the run, unless the skip rule applies.
2. To build on purpose, open Actions > Dev prerelease > Run workflow, branch `dev`, Run workflow. A manual run always builds.
3. Wait for `gate`, `build` and `publish` to finish. If the run is not approved (only when a reviewer is set on `dev-release`, which the repo does not set; see `first-time-setup.md`), approve it.

## Check the result

- The Releases page shows `oru-<version>.apk` and `SHA256SUMS.txt` on a pre-release tagged `v<version>`.
- The run's `publish` job has the attestation step.
- Verify each asset (see `release-stable.md`, "Verify the assets"). The same checks apply to dev and stable.

## What the in-app updater does with a dev release

- The updater is in the app. It reads `ORU_RELEASE_REPO` from `app/src/main/java/app/morphe/manager/util/Constants.kt`, which is `The-FunMan/oru`.
- With the pre-release switch on (the preference key is `manager_prereleases`), it reads `releases?per_page=30` and takes the highest version newer than the installed one. Dev users also get stable releases.
- With the switch off, it reads `releases/latest`, which excludes pre-releases, and never offers a dev build.
- Before an update is offered, the APK URL must be under `https://github.com/The-FunMan/oru/releases/download/`. The download is checked against `SHA256SUMS.txt`, and the signers of the new APK must equal the signers of the installed app.

## Yank a bad dev release

See `release-stable.md`, "Yank a bad release". For a dev build, use `delete-release` in the Yank release workflow to take it off every feed. The tag stays, so the next dev build takes the next number.
