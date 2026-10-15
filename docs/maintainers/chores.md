# Chores

Each entry says when the chore happens, who does it, the exact steps, how to verify it, and how long it takes. Entries that the repo does not define say so.

## 1. Dependency updates

- **When:** weekly, when Dependabot opens its pull requests (`.github/dependabot.yml`: weekly, target `dev`, for github-actions, gradle at `/` and `/patcher`, cargo at `/native` and `/native-packager`).
- **Who:** the owner.
- **Steps:**
  1. Open each Dependabot pull request. Read the release notes of the library.
  2. Check that CI is green. The pull request runs `ci.yml`.
  3. Dependency changes to Gradle need the verification metadata regenerated in the same pull request. `dependabot.yml` says so. The command is not written in the repo: the Gradle option `--write-verification-metadata sha256` is the one to use, and it is not verified here (see `open-items.md`).
  4. Version catalog changes are in `gradle/libs.versions.toml`. The Morphe patcher version is `morphe-patcher` there, and the patcher's own version is in `patcher/gradle.properties`.
  5. `arsclib` is pinned to a commit of a MorpheApp fork (the comment in `libs.versions.toml`). Bumping it is a decision, not a routine update.
  6. Merge with a merge commit or squash, as `CONTRIBUTING.md` says for pull requests to `dev`.
- **Verify:** `./gradlew :app:testOruDebugUnitTest :app:testMorpheDebugUnitTest` and `cd native && cargo test --locked`.
- **Time:** 15 to 60 minutes a week, depending on the number of updates.

## 2. Rust toolchain and NDK bumps

- **When:** when a native crate needs a newer Rust, or when the NDK should move (new Android release, or a fix).
- **Who:** the owner.
- **NDK steps.** The version `28.2.13676358` appears in these places. Change them all together:
  1. `.github/actions/setup-oru/action.yml`: the path `$ANDROID_HOME/ndk/28.2.13676358`, the cache key `ndk-28.2.13676358-...`, and the `sdkmanager` package `ndk;28.2.13676358`.
  2. `README.md`: the line "CI uses NDK 28.2.13676358".
  3. The `ndkVersion` in `app/build.gradle.kts` is not set (open item). Set it there too once it is decided.
- **Rust steps.** There is no `rust-toolchain` file, so CI uses the runner's stable toolchain. To pin a version, add `rust-toolchain.toml` to the crate roots and document it (open item). The Rust targets are listed in `setup-oru`: `aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android`.
- **Crate dependencies:** `native/Cargo.lock` and `native-packager/Cargo.lock` are committed. CI runs with `--locked`, so a dependency change needs a lock file update in the same pull request.
- **Verify:** `cd native-packager && cargo test --workspace --locked`, `cd native && cargo test --locked`, then a dev prerelease, and `verify-apk.sh` passes on its output.
- **Time:** an hour or two, longer when the NDK changes the native build.

## 3. The pre-installed default patch bundle

- **What it is:** Oru does not contain a patch bundle file. The default source is the Morphe patch bundle, read at run time from `MorpheApp/morphe-patches` (`SOURCE_REPO_URL` in `app/src/main/java/app/morphe/manager/util/Constants.kt`). The app treats it as the pre-installed source (`BundleSourceType.PreInstalled`, uid `DEFAULT_SOURCE_UID`).
- **Who:** nobody in this repo. The bundle is made by its authors.
- **Steps:** nothing to update in this repo. To check that the default source works, open Oru and look at the patch source list. To check its patches against the patcher, run the patch API guard (`release-stable.md`).
- **Verify:** `ci.yml` job `patch-api-guard` downloads the current stable and dev bundles on every pull request. The stable check blocks, the dev check is advisory.
- **Time:** none, except when the guard fails.

## 4. Syncing from upstream Morphe

Policy: Oru diverged on purpose. Upstream (`upstream` = `https://github.com/MorpheApp/morphe-manager.git`, as in `README.md`) is not merged wholesale. Chosen upstream commits are cherry-picked, one at a time, and each one is checked against Oru's own rules (no new settings, Oru identity, the patcher API rule).

- **When:** when an upstream fix is worth having. There is no fixed schedule.
- **Who:** the owner.
- **Steps:**
  1. Add the remote once, if it is missing: `git remote add upstream https://github.com/MorpheApp/morphe-manager.git`.
  2. Fetch it: `git fetch upstream`.
  3. Find the commit on the upstream branch and read it. Upstream's branch names were not checked from this repo.
  4. Cherry-pick it onto a branch from `dev`: `git cherry-pick -x <sha>`. Resolve conflicts by keeping Oru's version of the UX.
  5. Open a pull request to `dev`, and name the upstream commit in the description.
  6. Features on the list in `upstream-not-ported.md` are not cherry-picked unless the list says "to port".
- **Verify:** CI on the pull request, and the device smoke test for UI changes.
- **Time:** 30 minutes to a few hours per commit.

## 5. Translations

- **Source of truth:** `app/src/main/res/values/strings.xml` for the English text of the Morphe build, and `app/src/oru/res/values/strings.xml` for the Oru overrides. New user-visible text goes into the English file (`PULL_REQUEST_TEMPLATE.md`, `CONTRIBUTING.md`).
- **Translated files:** `values-*/strings.xml` folders. `app/build.gradle.kts` says Crowdin writes them, and it derives the list of languages from the folder names. Oru-only overrides for the translated languages are under `app/src/oru/res/values-*/strings.xml`.
- **Who:** the owner. Corrections go through pull requests to the `values-*` folders (`CONTRIBUTING.md`).
- **Regenerating overlays:** not set up in the repo. There is no Crowdin configuration, no script, and no workflow for the sync. Open item.
- **Verify:** `./gradlew :app:lint` (the lint config disables `MissingTranslation` and warns about `ExtraTranslation`) and a build of the Oru flavour.

## 6. Rotating the GitHub token

- **Repository secrets:** the repo defines no token secret. `${{ github.token }}` is created by GitHub for each run.
- **Gradle token:** the personal access token with `read:packages`, in `local.properties` (`gpr.key`) or `~/.gradle/gradle.properties` (`first-time-setup.md`, step 6).
- **When:** yearly, or at once if it may have leaked.
- **Steps:**
  1. Create a new token with `read:packages` in GitHub's token settings.
  2. Replace `gpr.key` on each machine.
  3. Revoke the old token in GitHub's token settings.
- **Verify:** `./gradlew :app:assembleOruDebug` resolves the registry artifacts.
- **Time:** 10 minutes.

## 7. Rotating the signing key

Read this before doing it. The signing key is part of the identity that Android and the updater check.

- **What happens to users:** Android installs an update only when it is signed by the same key as the installed app. The in-app updater checks the same thing (`signersMatch` in `OruUpdateRules.kt`). A new key therefore cannot update an existing install. Users must uninstall the old app first, and uninstalling deletes its data. Android also keeps a certificate history, which the app's signature check reads (`OruSignatureCheck.kt`).
- **What else changes:** `ORU_RELEASE_CERT_SHA256` (the repository variable), the keystore secrets and `keystore.jks` on the owner's machine. The updater itself reads no pin.
- **Steps:**
  1. Create the new key and keep it offline (`first-time-setup.md`, step 5).
  2. Update the secrets `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEYSTORE_ENTRY_ALIAS` and `KEYSTORE_ENTRY_PASSWORD` in both environments, and the variable `ORU_RELEASE_CERT_SHA256`.
  3. Write it in the release notes, with the instruction to uninstall before the update.
  4. Publish the next stable release and check it (`release-stable.md`, "Verify the assets").
- **Verify:** `apksigner verify --print-certs` shows the new digest.
- **Time:** half a day, plus the communication.
- **Note:** key rotation with a signing lineage is not set up.

## 8. Security advisories

- **Policy:** `SECURITY.md`. Reports come through GitHub's private vulnerability reporting, in the Security tab. Public issues are not used for security problems.
- **Who:** the owner.
- **Steps:**
  1. Acknowledge the report in the advisory. The advisory workflow is GitHub's, in the web UI, and is not in the repo.
  2. Fix the problem on a branch from `dev`, in the advisory's private fork if GitHub creates one.
  3. Publish the fix in the next stable release (`release-stable.md`). For a dev build that is affected, yank it with `delete-release` (`release-dev.md`).
  4. Publish the advisory after the release is out.
- **Scope:** `SECURITY.md` lists the in-scope areas.
- **Verify:** the advisory is published, and the fixed release has the fix.
- **Time:** depends on the problem.

## 9. Triage of issues and pull requests

- **Issues:** the forms are `bug_report.yml` (label `bug`) and `feature_request.yml` (label `feature request`). `config.yml` routes security reports to the advisory form, and patch problems to `MorpheApp/morphe-patches`. The `question` label in `config.yml` is not created by the repo: open item.
- **Pull requests:** the template (`PULL_REQUEST_TEMPLATE.md`) has the checklist: title format, the Patcher API check, no new setting without a reason, no new dependency without an issue, no emoji or em dashes.
- **CODEOWNERS:** `@The-FunMan` reviews everything (`CODEOWNERS`).
- **Who:** the owner.
- **Steps:** label, reply, and close duplicates with a link. Features need an issue before a pull request (`CONTRIBUTING.md`).
- **Verify:** no open pull request older than the last release without a decision.
- **Time:** 15 minutes a week.

## 10. Cleaning stale branches

- **When:** after each stable release.
- **Who:** the owner.
- **Steps (standard git, not defined in the repo):**
  1. `git fetch --prune`.
  2. List branches merged into `dev`: `git branch --merged dev`.
  3. Delete merged local branches one at a time, with `git branch -d <name>`.
  4. Delete merged remote branches with `git push origin --delete <name>`. Never delete `dev` or `main`. Branch protection forbids deleting them anyway.
- **Verify:** `git branch -r` lists only the branches that are still in use.
- **Time:** 10 minutes.

## 11. Actions storage housekeeping

- **What:** the Housekeeping workflow (`housekeeping.yml`) runs every Monday at 03:17 UTC, and deletes workflow runs, artifacts and caches older than 5 days in this repository. It never touches releases or release assets.
- **The uploads:** every `upload-artifact` step sets `retention-days` (CI 5, dev build 3, stable build 5).
- **Owner setting:** Settings > Actions > General > Artifact and log retention must also be 5 days. Set it once.
- **Caches:** GitHub also evicts Actions caches that were not used for 7 days (GitHub's own policy, not checked in the repo).
- **Verify:** Actions > Housekeeping > Run workflow, `dry_run` on. The log lists what would go.
- **Time:** none, once the weekly run works.

## 12. Privacy scan before publishing

- **When:** before the first public push, and before each release that adds text or images.
- **Who:** the owner.
- **What it looks for:** personal identifiers (names, account names, e-mail addresses), device serial numbers, and local paths of the owner's machine. The generic patterns are in `scripts/privacy-patterns.txt`. The owner's own values go in `scripts/privacy-patterns.local.txt`, which is untracked and ignored by `.gitignore`. Real values are never written into a tracked file.
- **Steps:**
  1. Create `scripts/privacy-patterns.local.txt` with one regular expression per line (only on the owner's machine).
  2. Run the scan over the tree and the new commits:

     ```sh
     scripts/privacy-scan.sh
     scripts/privacy-scan.sh origin/main..HEAD
     ```

     The script runs `git grep -I -i -n -E -f <patterns> -- .` over the tracked files, and `git log -p` over the range, matched with `grep -i -E`. The default range is `origin/dev..HEAD`.
  3. Exit code 0 means nothing matched. Exit code 1 means a match: the matching lines are printed, so do not paste them anywhere, and remove the value from the file and from the history before publishing.
- **Verify:** run it once with a known pattern in the local file, and check that it fails.
- **Time:** 5 minutes.

## 13. The project homepage under site/

- **What:** the homepage is planned under `site/`, deployed by a `pages.yml` workflow that another agent creates. It is not in this branch yet.
- **When:** when the homepage changes.
- **Steps:** edit files under `site/` on `dev`. The workflow deploys them once it exists.
- **Settings:** GitHub Pages must use the source "GitHub Actions", and the `github-pages` environment must allow deployments from the branches `dev` and `main`. Neither is set up yet (open item).
- **Verify:** the Pages URL shows the change after the workflow run.

## 14. Dev version after a stable release

- **When:** right after each stable release is published.
- **Steps:** bump `app/gradle.properties` on `dev` to the next version. The details are in `release-stable.md` ("After a stable release is published").
- **Verify:** a dev push builds. Until the bump, `next-dev-version.sh` fails with "tag vX.Y.Z already exists".
