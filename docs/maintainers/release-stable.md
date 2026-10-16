# Stable release

A stable release is a GitHub release marked as the latest, with tag `vX.Y.Z`. It is built by `.github/workflows/release-stable.yml`, whose name is "Stable release". Two ways start it. Both end in the same run.

## Branches

- `dev` is the development branch. Dependabot pull requests and all pull requests target `dev` (`CONTRIBUTING.md`, `dependabot.yml`). Each push to `dev` runs `release-dev.yml`.
- `main` holds the stable releases. `release-stable.yml` requires the tagged commit to be an ancestor of `origin/main` (`check-stable-tag.sh`).
- The repo's workflow triggers name these two branches. Upstream's naming was not checked from this repo.
- `main` receives changes from `dev` only when a release is cut, as a pull request (see `branch-protection.md`).

## Version rules

- On the tagged commit, `app/gradle.properties` must hold the same version as the tag, with no `-dev` suffix (`check-stable-tag.sh`).
- Tag format: `vX.Y.Z`, checked by the regex `^v[0-9]+\.[0-9]+\.[0-9]+$`. A tag such as `vX.Y.Z-dev.N` fails that check.
- Ranges: major 0..20, minor 0..99, patch 0..999. The stable versionCode ends in 999 (see `release-dev.md`).
- The version must be higher than the latest stable tag (`validate-stable-version.sh`, used by the Cut stable release workflow).
- Release notes: `release-notes/vX.Y.Z.md` on the tagged commit, when present. Without the file, the notes are the commits since the previous stable tag. The first stable release has no previous tag, so it must have the file. The directory `release-notes/` does not exist in the repo yet.

## Pre-release checklist

- [ ] CI is green on the `dev` head (the CI workflow runs on every push to `dev`).
- [ ] App unit tests: `./gradlew :app:testOruDebugUnitTest`
- [ ] Patcher checks: `./gradlew :patcher:test :patcher:apiCheck :patcher:jar --stacktrace`
- [ ] Native crates: `cd native-packager && cargo test --workspace --locked` and `cd native && cargo test --locked`
- [ ] Python tools: `python -m unittest discover -s tools/parity -p "test_*.py"`
- [ ] Patch API guard, with the current stable and dev bundles of `MorpheApp/morphe-patches` (step 3 below). This is required when `patcher/` changed in a way that removes or renames public API. CI runs it on every pull request and blocks on the stable bundle.
- [ ] Version: `app/gradle.properties` holds `X.Y.Z` on `dev`, and `vX.Y.Z` does not exist yet.
- [ ] Release notes: `release-notes/vX.Y.Z.md` written, or the commit list is accepted.
- [ ] A dev prerelease of the same base was built and tested on a device.
- [ ] `CHANGELOG.md` at the root is inherited from upstream and no workflow writes it. Nothing for Oru is required there (open item).

## Patch API guard, exact steps

1. Build the patcher jar: `./gradlew :patcher:jar`.
2. Download the bundles, as CI does (`ci.yml`, job `patch-api-guard`). `gh` must be signed in:

   ```sh
   mkdir -p "$RUNNER_TEMP/bundles/stable" "$RUNNER_TEMP/bundles/dev"
   gh release download --repo MorpheApp/morphe-patches --pattern '*.mpp' --dir "$RUNNER_TEMP/bundles/stable"
   tag=$(gh release list --repo MorpheApp/morphe-patches --exclude-drafts --limit 30 \
         --json tagName,isPrerelease --jq '[.[] | select(.isPrerelease)][0].tagName')
   gh release download "$tag" --repo MorpheApp/morphe-patches --pattern '*.mpp' --dir "$RUNNER_TEMP/bundles/dev"
   ```

   On Windows without `$RUNNER_TEMP`, use any empty folder and set the path in the next command.
3. Run the guard with the two bundle arguments:

   ```sh
   python -I tools/parity/patch_api_guard.py \
     --patcher "$(ls patcher/build/libs/morphe-patcher-[0-9]*.jar | head -1)" \
     --bundle "$RUNNER_TEMP"/bundles/stable/*.mpp \
     --bundle "$RUNNER_TEMP"/bundles/dev/*.mpp
   ```

   Exit code 0 means clean. Exit code 1 lists the missing patcher references. Exit code 2 is a usage error, or a bundle with no class files.

## Cut the release: the Cut stable release workflow (recommended)

The workflow is `.github/workflows/cut-stable.yml`, name "Cut stable release". It is run by hand from the Actions tab, or from the phone (see `from-the-phone.md`). It only runs on the `dev` branch and only for the owner.

Inputs:

- `version`: `X.Y.Z`, with no `v` and no `-dev`.
- `dry_run`: true by default. A dry run performs the checks and writes the plan to the job summary. It opens, merges and tags nothing.
- `notes`: optional Markdown. When set, it is committed as `release-notes/vX.Y.Z.md` on `dev`. Required for the first stable release.

Jobs, in order:

1. `plan`: `validate-stable-version.sh`. Checks the format and ranges, that `vX.Y.Z` does not exist, that the version is higher than the latest stable tag, that `app/gradle.properties` on `dev` equals the version, and that notes exist for the first release.
2. `notes` (not in a dry run, only when `notes` is set): commits the notes file to `dev` with the workflow token and pushes it. A push with the workflow token starts no other workflow.
3. `ci`: `ci-gate.sh` on the release commit. It requires the checks `android`, `patcher`, `patch-api-guard` and `rust` to have succeeded on that commit. When no CI run exists for the commit, it starts `ci.yml` on `dev` and waits up to 90 minutes. A dry run only reports.
4. `cut` (environment `release`, so the owner approves it): opens or reuses the pull request `dev` -> `main` titled `Release vX.Y.Z`, merges it with `gh pr merge --merge --match-head-commit <sha>` (a merge commit), creates the tag `vX.Y.Z` on the merge commit with the API, and runs `gh workflow run release-stable.yml --ref main -f tag=vX.Y.Z`. If `main` already contains the release commit (for example `dev` was merged by hand), it opens and merges nothing and tags the head of `main`, after checking that its `app/gradle.properties` holds the version. It only reuses a pull request whose title is `Release vX.Y.Z` for the same tag.

After the cut job: the run of Stable release waits for the `release` environment. That is the second approval. Then it builds and publishes.

Why the checks run through `ci-gate.sh` and not through a reused workflow: a reusable workflow names its checks `<caller job> / <job>`, and the branch protection on `main` requires the plain names. A pull request opened with the workflow token starts no pull request workflow, so the required checks would never report on it. The check runs of the same commit are what count, and `ci-gate.sh` makes sure they exist.

Needs in the repository: "Allow GitHub Actions to create and approve pull requests" (Settings > Actions > General), merge commits allowed, the `release` environment with the owner as required reviewer, and the branch protection of `main` from `branch-protection.md`.

## Cut the release by hand (same result)

1. Run the pre-release checklist.
2. Open a pull request from `dev` to `main`. Merge it with "Create a merge commit". Do not squash it: a squash merge makes the next `dev` -> `main` pull request repeat the squashed commits.
3. Find the merge commit on `main`, then tag it and push the tag:

   ```sh
   git fetch origin
   git tag vX.Y.Z origin/main
   git push origin vX.Y.Z
   ```

   `origin/main` is the merge commit of step 2 when nothing else landed on `main` after it. Check it with `git log -1 origin/main` first.
4. The tag push starts `release-stable.yml` through `on: push: tags: ['v*.*.*']`. Approve the `release` environment when GitHub asks.

Do not do both paths for the same version. Do not push a tag that is not `vX.Y.Z` by hand: a `vX.Y.Z-dev.N` tag also matches the trigger and fails the check.

## After a stable release is published: bump `dev`

`next-dev-version.sh` fails with "tag vX.Y.Z already exists" while the base on `dev` still equals the released version. Right after the release, set `app/gradle.properties` on `dev` to the next version, for example `X.Y.(Z+1)`. The owner can push that directly to `dev`.

## What the stable workflow produces

- The build job, environment `release`, runs `assembleOruRelease` with the keystore, runs `verify-apk.sh --release`, writes `dist/oru-<version>.apk` and `dist/SHA256SUMS.txt`, and keeps the artifact `dist` for 5 days.
- The publish job attests the provenance of `dist/oru-*.apk` and runs `gh release create <tag> ... --verify-tag --latest`, with the notes file. The release is the latest one.

## Verify the assets

Run these on a computer, after the release is published:

1. Check the APK against the list. In a folder with both files:

   ```sh
   sha256sum -c SHA256SUMS.txt
   ```

   On Windows: `Get-FileHash oru-<version>.apk -Algorithm SHA256` and compare with the line in `SHA256SUMS.txt`.
2. Check the provenance:

   ```sh
   gh attestation verify oru-<version>.apk --repo The-FunMan/oru
   ```

3. Check the signing certificate against the variable `ORU_RELEASE_CERT_SHA256`. With the build tools that CI uses (36.0.0):

   ```sh
   "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs oru-<version>.apk
   ```

   The line `Signer #1 certificate SHA-256 digest:` must match the variable, with colons removed. `verify-apk.sh` does the same comparison in CI.

## Device smoke test

1. Install the new APK over the previous Oru build. Android must accept it, and the app must keep its data. If it refuses, the signing key changed (see `chores.md`).
2. Run one patch from start to end: choose an app, a version, the patches, patch, and install the result through the installer you use.
3. Check the update feed: on the previous build, the update check offers the new release, and the download is verified against `SHA256SUMS.txt`.

## Yank a bad release

The Yank release workflow (`yank-release.yml`, name "Yank release") takes a release back. It never deletes a tag or a branch.

- `mark-prerelease`: marks the release as a pre-release. The stable feed (`releases/latest`) then returns the previous stable release. The dev feed still lists it, so dev users can still get it.
- `delete-release`: deletes the release and its assets, and keeps the tag. Every feed loses it.

Inputs: `tag` (`vX.Y.Z` or `vX.Y.Z-dev.N`), `action`, and `confirm`, which must equal the tag.

What the app does afterwards:

- The updater offers only a version higher than the installed one. It never offers a downgrade, so a user who already has the bad build stays on it until a newer release is published.
- Users who have not updated yet are not offered the yanked release once it is a pre-release (stable channel) or deleted (both channels).
- Fix forward: publish the next patch version (`X.Y.(Z+1)`). The yanked version number is not reused, so the tag stays and the new version is cut normally.

Policy (not enforced by the repo): do not reuse a yanked version number. The validate script refuses an existing tag anyway.

## Tags that never had a release

If a stable run stopped before publishing, no release is public. The owner may then remove the tag by hand on a computer (for example `git push origin :refs/tags/vX.Y.Z`) and cut the same version again. This is the owner's decision. It is safe only while no build of that tag has been published, and the workflows do not do it.
