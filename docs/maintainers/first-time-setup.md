# First-time setup

A checklist to run this repository from nothing. Tick each item when it is done. Items marked "not in the repo" are settings or steps the repository does not define; they are listed in `open-items.md` too.

## 1. Repository settings (Settings > General and the others)

- [ ] Visibility: public (the in-app update check reads the public releases feed).
- [ ] Default branch: `dev`. Pull requests target `dev` (see `CONTRIBUTING.md`).
- [ ] Branch protection on `main` and on `dev`. Follow `branch-protection.md` and run its "Verify" commands.
- [ ] Merge settings: "Allow merge commits" on (the dev-to-main pull request is merged with a merge commit). "Allow squash merging" on (pull requests to `dev` are squash-merged).
- [ ] Dependabot alerts and Dependabot security updates on. Version updates are already configured in `.github/dependabot.yml` (weekly, target `dev`, for github-actions, gradle at `/` and `/patcher`, and cargo at `/native` and `/native-packager`). Dependabot alerts and security updates are a repository setting, not in the repo: not set up yet as far as the repo shows.
- [ ] Private vulnerability reporting on (Settings > Code security). `SECURITY.md` tells people to use the Security tab. The setting is not in the repo.
- [ ] Issues on. The issue forms are `.github/ISSUE_TEMPLATE/bug_report.yml` and `feature_request.yml`. Blank issues are off in `config.yml`.
- [ ] Discussions: not used. No file in the repo links to Discussions.

## 2. Actions settings (Settings > Actions > General)

- [ ] Actions permissions: allow the actions this repository uses. The workflows use `actions/*`, `gradle/actions/*`, `Swatinem/rust-cache`, `actions/dependency-review-action` and the local `./.github/actions/setup-oru`. The exact allow-list is a repository setting that the repo does not define.
- [ ] Workflow permissions: "Read repository contents and packages permissions" (read-only default). Every workflow sets its own `permissions:` block, so the default only matters for jobs that do not set one.
- [ ] "Allow GitHub Actions to create and approve pull requests": on. Needed by the Cut stable release workflow, which opens the pull request.
- [ ] Fork pull request workflows: "Require approval for first-time contributors" or stricter. Fork pull requests must not run with secrets.
- [ ] Artifact and log retention: 5 days (Settings > Actions > General > Artifact and log retention). The workflow files already set `retention-days: 5` on every upload, and this setting is the repository-wide ceiling. Both must be 5.
- [ ] Pages: enable with source "GitHub Actions" when the project homepage workflow exists (see `open-items.md`).

## 3. Environments (Settings > Environments)

Create these environments. The workflows reference them by these exact names.

- [ ] `dev-release`: used by the build job of `release-dev.yml`. Deployment branches: `dev` only. No required reviewer, because dev builds run on every push to `dev`. Secrets for the dev build go here.
- [ ] `release`: used by the build job of `release-stable.yml` and by the `cut` job of `cut-stable.yml`. Required reviewers: the owner, so the owner approves each run from the web or the phone. Deployment branches and tags: `dev`, `main`, and the tag pattern `v*.*.*`. Secrets for the stable build go here.
- [ ] `github-pages`: created by GitHub when Pages deploys. Not in the repo yet (see `open-items.md`).

## 4. Secrets and variables (Settings > Environments > the environment > Environment secrets and variables)

Names are exact. Each is read by the workflow named in the last column.

| Name | Kind | Holds | Read by |
| --- | --- | --- | --- |
| `KEYSTORE_B64` | secret | the release keystore file, base64 encoded | `release-dev.yml` build, `release-stable.yml` build |
| `KEYSTORE_PASSWORD` | secret | the keystore password | both builds above |
| `KEYSTORE_ENTRY_ALIAS` | secret | the alias of the signing key in the keystore | both builds above |
| `KEYSTORE_ENTRY_PASSWORD` | secret | the password of the signing key | both builds above |
| `ORU_RELEASE_CERT_SHA256` | variable | the SHA-256 fingerprint of the signing certificate, hex, colons optional | both builds above, through `.github/scripts/verify-apk.sh --release` |

- [ ] The five names above exist in the environments of the workflows that read them (`dev-release` for the dev build, `release` for the stable build).
- [ ] No other secret is referenced by the repo's workflows. The GitHub token is `${{ github.token }}`, which GitHub creates for each run and is not stored.
- [ ] The Cut stable release workflow reads no secret. It uses `${{ github.token }}` with the permissions set in the file.

The repo does not define the variable for the Gradle token (see step 7).

## 5. The release signing key

The release key signs the APKs that users install and update. Losing it, or leaking it, is covered in `chores.md` ("Rotating the signing key").

- [ ] Create the key on a machine you control. Key creation is not set up in the repo: there is no script for it. The keystore must be at `app/keystore.jks` for a local release build, the alias must be the value you store in `KEYSTORE_ENTRY_ALIAS`, and the passwords must match the two password secrets. A PKCS12 keystore protects the key with the store password and ignores a different key password, so set `KEYSTORE_ENTRY_PASSWORD` to the same value as `KEYSTORE_PASSWORD`. Example with the JDK tool (check the options with `keytool -help` first):

  ```sh
  keytool -genkeypair -keystore app/keystore.jks -storetype PKCS12 -alias oru-release -keyalg RSA -keysize 4096 -validity 10000
  ```

- [ ] Record the SHA-256 fingerprint of the certificate:

  ```sh
  keytool -list -v -keystore app/keystore.jks
  ```

  Copy the `SHA256:` value without the colons into `ORU_RELEASE_CERT_SHA256` (the variable, lowercase or uppercase hex both work, because `verify-apk.sh` strips colons and compares in lowercase).
- [ ] Store the fingerprint in your own notes too. If the variable is lost you cannot check the APKs against it.
- [ ] Make a second, offline copy of the keystore file and the passwords (for example an encrypted drive that is not connected to the internet). The `.gitignore` already excludes `*.jks`, `*.keystore`, `*.p12`, `keystore.jks` and `keystore.properties`, so the file cannot be committed by accident. Check with `git status --ignored` before each commit.
- [ ] Create the value of `KEYSTORE_B64` from the file, on a Linux or Git Bash shell:

  ```sh
  base64 -w0 app/keystore.jks > "$HOME/keystore.b64"
  ```

  Paste the content of that file as the secret `KEYSTORE_B64`, then delete the text file. Creating the base64 text is not defined in the repo; the workflows decode it with `base64 -d`.
- [ ] Delete `app/keystore.jks` from the working tree when you do not build a release locally. The release workflows delete their own copy at the end of each run.

## 6. Gradle token for the GitHub Packages repository

The build resolves the `app.morphe` artifacts from `https://maven.pkg.github.com/MorpheApp/registry` (`settings.gradle.kts`). That repository needs a token that can read packages.

- [ ] Create a personal access token with the `read:packages` scope. `patcher/docs/2_1_setup.md` describes the same token for the patcher.
- [ ] Put it on your machine, in `local.properties` in the repository root (ignored by `.gitignore`) or in `~/.gradle/gradle.properties`:

  ```properties
  gpr.user=<your GitHub user name>
  gpr.key=<the token>
  ```

  `settings.gradle.kts` also reads `GITHUB_TOKEN` and `GITHUB_ACTOR` from the environment, and `patcher/build.gradle.kts` does too.
- [ ] CI: the workflows do not set `GITHUB_TOKEN` or `gpr.*` for Gradle. Whether CI resolves the registry without them is not verified. Check the first CI run. This is an open item.

## 7. Local machine

- [ ] JDK 21. CI uses Temurin 21 (`.github/actions/setup-oru/action.yml`).
- [ ] Android SDK with: platform 37.0 (`platforms;android-37.0`), build-tools 36.0.0 (`build-tools;36.0.0`, used by `verify-apk.sh`), NDK 28.2.13676358 (`ndk;28.2.13676358`), CMake 3.22.1 (`cmake;3.22.1`). The package names are the ones in `setup-oru`. Install them with `sdkmanager`.
- [ ] `ANDROID_HOME` points at the SDK. `verify-apk.sh` requires it.
- [ ] Rust with the Android targets the native crates need:

  ```sh
  rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
  ```

  The repo does not pin a Rust version (no `rust-toolchain` file). CI uses the runner's default stable toolchain.
- [ ] Note: `app/build.gradle.kts` does not set `ndkVersion`. Gradle uses the NDK that the Android Gradle plugin selects, and CI installs 28.2.13676358. Whether the two match locally is not verified. Open item.
- [ ] Gradle wrapper 9.8.0 (`gradle/wrapper/gradle-wrapper.properties`). Use `gradlew` on Linux and Git Bash, `gradlew.bat` on Windows.
- [ ] Python 3 for `tools/parity` (standard library only).

## 8. First local build and test

Run from the repository root. These are the commands CI runs (`ci.yml`) and the ones in `CONTRIBUTING.md`.

- [ ] Unit tests and lint:

  ```sh
  ./gradlew :app:testOruDebugUnitTest :app:lint --stacktrace
  ```

- [ ] Patcher tests, API check and jar:

  ```sh
  ./gradlew :patcher:test :patcher:apiCheck :patcher:jar --stacktrace
  ```

- [ ] Native packager and native crate tests:

  ```sh
  cd native-packager && cargo test --workspace --locked
  cd native && cargo test --locked
  ```

- [ ] Python tools:

  ```sh
  python -m unittest discover -s tools/parity -p "test_*.py"
  ```

- [ ] A debug build of the Oru flavour:

  ```sh
  ./gradlew :app:assembleOruDebug
  ```

- [ ] A debug-signed release build with no secrets, the same as CI's PR build:

  ```sh
  ./gradlew :app:assembleOruRelease -PsignAsDebug --stacktrace
  .github/scripts/verify-apk.sh app/build/outputs/apk/oru/release/*.apk
  ```

  `verify-apk.sh` needs `ANDROID_HOME` and checks the native libraries, the 16 KB alignment and the signature.

- [ ] Optional, a signed release build with your key. It needs `app/keystore.jks` and the three variables `KEYSTORE_PASSWORD`, `KEYSTORE_ENTRY_ALIAS` and `KEYSTORE_ENTRY_PASSWORD` in your shell, plus `ORU_RELEASE_CERT_SHA256`:

  ```sh
  ./gradlew :app:assembleOruRelease --stacktrace
  .github/scripts/verify-apk.sh --release app/build/outputs/apk/oru/release/*.apk
  ```

- [ ] Patch API guard, with the upstream bundles (see `release-stable.md`, step 3).
- [ ] Privacy scan before the first public push:

  ```sh
  scripts/privacy-scan.sh
  ```

## 9. First release dry run on a private fork

The release workflows only run in `The-FunMan/oru`: every release job has `if: github.repository == 'The-FunMan/oru'`. A fork therefore skips them. A dry run on a private fork needs a change in the fork only.

- [ ] Create a private fork of the repository. Do not push its changes back to `The-FunMan/oru`.
- [ ] In the fork, change the repository check in the release workflows to the fork's name, and keep that change out of the upstream branch.
- [ ] In the fork, create the environments `dev-release` and `release` and the five secrets and the variable from step 4, using a test keystore. The test keystore must never be the real one, and its values stay in the fork.
- [ ] Run "Dev prerelease" from the Actions tab on the fork's `dev` branch. Check the output against `release-dev.md`.
- [ ] Run "Cut stable release" with `dry_run` true, then with `dry_run` false on a test version, and check the result against `release-stable.md`.
- [ ] Delete the fork when the dry run is done.
