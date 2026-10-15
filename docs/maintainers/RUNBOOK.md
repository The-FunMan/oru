# Maintainer runbook

The checklists for running this repository: setting it up, cutting releases, and the regular chores. The workflows and scripts named here are the ones in `.github/`, and the repo's own files are the source. Where the repo does not define something, the page says so.

## Start here

- [First-time setup](first-time-setup.md): settings, environments, secrets, the signing key, the local build and the first dry run.
- [Branch protection](branch-protection.md): the settings for `main` and `dev`, the commands that apply them, and how to check them.

## Releases

- [Dev prerelease](release-dev.md): how a dev build is cut from `dev`, the version rules and the dev number.
- [Stable release](release-stable.md): the Cut stable release workflow, the manual path, the checklist, verification, the device smoke test, and yanking a bad release.
- [Releases from the phone](from-the-phone.md): which workflow to run from the phone, the inputs, the approvals, and what to do after a failed run.

## Chores

- [Chores](chores.md): dependencies, the Rust and NDK bumps, the default patch bundle, upstream sync, translations, tokens and the signing key, security advisories, triage, stale branches, Actions storage, the privacy scan, and the homepage.
- [Upstream features not ported](upstream-not-ported.md): the list the upstream sync works from.

## Open items

- [Open items](open-items.md): what the repo does not have yet, and the settings the owner must set.

## Workflows at a glance

| File | Name in the Actions tab | Starts | Purpose |
| --- | --- | --- | --- |
| `ci.yml` | CI | pull requests and pushes to `dev` and `main`, manual, and `workflow_call` | tests, lint, the APK check, the patcher and the patch API guard, the native crates |
| `release-dev.yml` | Dev prerelease | pushes to `dev`, manual on `dev` | dev prerelease |
| `release-stable.yml` | Stable release | tag push `v*.*.*`, manual with a `tag` | stable release |
| `cut-stable.yml` | Cut stable release | manual, on `dev` | merges `dev` into `main`, tags and starts the stable release |
| `yank-release.yml` | Yank release | manual | marks a release as a pre-release, or deletes one (never tags) |
| `housekeeping.yml` | Housekeeping | weekly and manual | deletes runs, artifacts and caches older than 5 days |

## Scripts

| File | Used by | Purpose |
| --- | --- | --- |
| `.github/scripts/next-dev-version.sh` | Dev prerelease | the dev tag, the version and the skip rule |
| `.github/scripts/check-stable-tag.sh` | Stable release | checks the stable tag |
| `.github/scripts/validate-stable-version.sh` | Cut stable release | checks the version before a cut |
| `.github/scripts/ci-gate.sh` | Cut stable release | makes sure the required CI checks passed on the release commit |
| `.github/scripts/verify-apk.sh` | CI and both release workflows | checks the APK |
| `scripts/privacy-scan.sh` | the owner, before publishing | scans the tree and the new commits |
| `tools/parity/patch_api_guard.py` | CI and the release checklist | checks bundles against the patcher API |
