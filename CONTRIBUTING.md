# Contributing to Oru

Thank you for helping. This page explains how changes get in. For the product rules behind them, read the principles in the [README](README.md).

## Before you write code

- Search the [existing issues](https://github.com/The-FunMan/oru/issues?q=is%3Aissue) first.
- For a feature or a behaviour change, open an issue and agree on it before you start. Pull requests for features need an issue.
- Typo and documentation fixes can go straight to a pull request.
- Oru prefers defaults over settings. A proposal that adds a user-facing setting must say why a default cannot work.

## Branches and pull requests

- Branch from `dev`. Name the branch `<type>/<short-topic>`, for example `fix/share-sheet-crash`.
- Open pull requests against `dev`. `main` holds stable releases and receives changes from `dev` only when a release is cut.
- Pull requests are squash-merged. The pull request title becomes the commit subject, so write it as a commit subject.
- Title format: `type(scope): imperative summary`, at most 72 characters, no trailing period. Types: `fix`, `feat`, `perf`, `refactor`, `docs`, `test`, `build`, `ci`, `chore`.
- Fill in the pull request template. Link the issue with `Closes #<number>`.
- Commits carry your own name and a noreply address. Do not add trailers that the project does not use.
- Keep commit messages and docs free of emoji and em dashes.

## Build and test

Requirements: JDK 21, the Android SDK, a Rust toolchain with the Android targets for the native code, and a GitHub token with `read:packages` (see the README).

| What | Command |
| --- | --- |
| App unit tests (Oru and Morphe flavors) | `./gradlew :app:testOruDebugUnitTest :app:testMorpheDebugUnitTest` |
| Lint | `./gradlew :app:lint` |
| Patcher tests and API check | `./gradlew :patcher:test :patcher:apiCheck` |
| Native packager tests | `cd native-packager && cargo test --workspace --locked` |
| Native parser tests | `cd native && cargo test --locked` |
| Python tools | `python -m unittest discover -s tools/parity -p "test_*.py"` |

CI runs all of these on every pull request. Do not mark a test as skipped to get a green check. If a test cannot run on your machine, say so in the pull request.

## Patcher API rule

A pull request that removes or renames any public class, method or field in `patcher/` must pass `tools/parity/patch_api_guard.py` against the current upstream patch bundles. Patch bundles call the patcher API directly, and a change there breaks them. The pull request template asks for the bundles you checked.

## Code style

- Kotlin: the official style (`kotlin.code.style=official` in `gradle.properties`).
- Rust: `rustfmt` and `clippy`.
- Do not add a dependency without an issue. Do not fork or copy third-party code into the tree. Libraries are used as published.
- Strings: new user-visible text goes into the English `values/strings.xml`. Oru-only English overrides go into `app/src/oru/res/`.

## UX principles

- Defaults over settings.
- No invented copy. Show real stages and real counts.
- Motion must be interruptible and must have a plain fallback on low-power devices and on Android versions that lack the effect.
- The Oru build does not show the name Morphe. The exceptions are the credits and licence texts, and the names of the custom branding image files.

## Translations

Translation changes go through pull requests to the `values-*/strings.xml` files, after the English text is final.

## Security

Do not report security problems in public issues or pull requests. See [SECURITY.md](SECURITY.md).

## Conduct

Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Maintainers

The maintainer checklists (setup, releases, chores) are in [docs/maintainers/RUNBOOK.md](docs/maintainers/RUNBOOK.md).
