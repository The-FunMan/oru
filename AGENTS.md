# Instructions for coding agents

This file is for AI coding agents working in this repository (Claude Code, Codex, Copilot and others). Humans should start with [CONTRIBUTING.md](CONTRIBUTING.md). The rules below apply to every change, whoever makes it.

## What this is

Oru is an Android app that patches other apps on the device: it takes an APK the user already has, runs patch bundles on the phone, signs the result and installs it. It is built from Morphe Manager (GPLv3) and stays independent of it, with credit. Everything is in this one repository: the app, the patcher library, two Rust crates, the project site and the CI.

Product principles: simple, opinionated, honest, quick. Defaults over settings. No invented copy: show real stages and real counts.

## Map

| Path | What it holds |
| --- | --- |
| `app/` | The Android app, with one flavor, `oru`. Oru screens are in `app/src/main/java/app/morphe/manager/ui/oru/`. |
| `patcher/` | The patcher library, a separate Gradle build included by the root build. Its public API is a compatibility surface. |
| `native/`, `native-packager/` | Rust crates (JNI index, packaging and signing). |
| `tools/parity/` | Python tools: the patch API guard and the parity checks. |
| `site/` | The project page (GitHub Pages). Checked by `scripts/check-site.sh`. |
| `docs/` | `docs/maintainers/` runbooks, `docs/knowledge/` how the code and the product fit together, `docs/dependencies.md`, `docs/benchmarks.md`. |
| `scripts/` | `privacy-scan.sh`, `check-site.sh` and `scripts/dev/` (box setup and the local checks). |
| `.github/` | Workflows, composite actions and the release scripts. |

More detail: [docs/knowledge/architecture.md](docs/knowledge/architecture.md).

## Commands

Set up a Linux box once with `scripts/dev/setup-linux.sh`, then `source` the `env.sh` it writes. Gradle needs `GITHUB_ACTOR` and `GITHUB_TOKEN` (a token that can read packages) in the environment. Never print the token or write it into a file in the tree.

| What | Command |
| --- | --- |
| Quick local checks | `scripts/dev/check.sh quick` |
| Everything CI runs | `scripts/dev/check.sh full` |
| App unit tests | `./gradlew :app:testOruDebugUnitTest` |
| Lint | `./gradlew :app:lint` |
| Patcher tests and API check | `./gradlew :patcher:test :patcher:apiCheck` |
| Debug-signed Oru APK | `./gradlew :app:assembleOruRelease -PsignAsDebug` |
| Privacy scan | `scripts/privacy-scan.sh` |

Run one Gradle build at a time. The machine may be small. Do not mark a test skipped to get a green run: fix it, or say it cannot run.

## Rules for code

- The Oru build never shows the name Morphe, except in credits, licence texts and the names of branding image files. Strings for Oru go into `app/src/oru/res/` or the `oru_strings*.xml` files.
- No new user-facing setting without a written reason why a default cannot work. One-time flags are fine.
- One patch source per app. The app never downloads stock APKs: the APK step only opens the download page for the chosen version in the browser.
- The patcher public API changes only by addition. Run `:patcher:apiCheck` and `tools/parity/patch_api_guard.py` before removing or renaming anything public.
- Do not fork, vendor or copy third-party code into the tree. Libraries are used as published. Do not add a dependency without an issue. Dependency state is in [docs/dependencies.md](docs/dependencies.md).
- Kotlin official style. Rust with `rustfmt` and `clippy`. Line endings are LF, except `*.bat`.
- Motion must be interruptible and must have a plain fallback. Keep visuals rich: do not cap effects for CPU.
- When you change a Gradle wrapper version, take the checksum from the official `.sha256` file next to the distribution on `services.gradle.org`, and verify with a clean `GRADLE_USER_HOME`. A wrong value passes on a machine that has the distribution cached and fails everywhere else.

## Rules for text

- No emoji. No em dashes. Plain, short sentences.
- No third-party app names in the site, the screenshots or the marketing copy. The one place for them is the disclaimer list in the README.
- Claims must be measured. The speed claim names no other manager and carries its fine print and a link to `docs/benchmarks.md`.
- Screenshots must show nothing personal: no accounts, notifications, package names or third-party app names.

## Rules for git

- Branch from `dev`. Pull requests go to `dev`; `main` only receives merges when a stable release is cut.
- Identity for every commit, as author and committer: `The-FunMan <336727550+The-FunMan@users.noreply.github.com>`. Set it per repository, never globally on a shared machine.
- Commit subjects and pull request titles use conventional commits: `type(scope): imperative summary`, at most 72 characters, no trailing period. Types: `fix`, `feat`, `perf`, `refactor`, `docs`, `test`, `build`, `ci`, `chore`. Scope is optional and names the area, for example `fix(run): keep the done buttons reachable`. The body says why. Do not use the old `Oru:` prefix. No trailers such as `Co-Authored-By`, no "Generated with" lines, no emoji, no em dashes.
- Do not push, force-push, tag or delete refs unless the owner asked for it in this task. Never rewrite published history.
- Never commit secrets, keystores or tokens. The signing key is not in this repository and not on shared machines.
- Run `scripts/privacy-scan.sh` before every push. Nothing personal belongs in the tree, in messages or in refs: no real names or e-mail addresses other than the noreply identity above, no device serials, no home directories, no local paths. Third-party repositories used for testing never appear in code, tests or messages: use made-up owners like `owner/repo`.

## Rules for testing on devices

- Use only the device or emulator the owner named for the task. Install, uninstall and clear only Oru and the patched app under test. Never touch any other package, and never read or capture personal screens.
- Never enter a PIN, password or fingerprint. If the device is locked, stop and ask.
- Delete any screenshot that shows personal content at once. Read notifications through filtered `dumpsys` output, not by pulling the shade down.
- Procedure and checklists: [docs/maintainers/device-qa.md](docs/maintainers/device-qa.md).

## When CI is red

Read the failing step first, then [docs/maintainers/ci-triage.md](docs/maintainers/ci-triage.md). Fix the cause. Do not retry in a loop and do not weaken a check.

## Releases

Dev prereleases come from pushes to `dev`. Stable releases are cut from the Actions tab and approved by the owner. See [docs/maintainers/RUNBOOK.md](docs/maintainers/RUNBOOK.md). Agents prepare release notes and run dry runs; the owner approves.

## Knowledge and runbooks

- [docs/knowledge/](docs/knowledge/README.md): architecture, product decisions, and the lessons that are easy to relearn the hard way.
- [docs/maintainers/RUNBOOK.md](docs/maintainers/RUNBOOK.md): setup, releases, chores, the dev box, CI triage, device QA.
