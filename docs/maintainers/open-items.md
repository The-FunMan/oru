# Open items

Things the repository does not have yet, or that a person must set up before the repo is runnable from nothing. Each item says what is missing and where it is referenced. Items are numbered so they can be discussed.

## Missing in the repo

1. **No canary job.** No workflow in `.github/workflows` runs a canary. Nothing to check against.
2. **No Gradle token in CI.** `ci.yml` and the release workflows do not set `GITHUB_TOKEN` or the `gpr.*` properties, but `settings.gradle.kts` requires registry credentials for `maven.pkg.github.com/MorpheApp/registry`, where the `app.morphe` artifacts come from. Whether CI resolves them is not verified. Check the first CI log.
3. **NDK not pinned in Gradle.** `app/build.gradle.kts` has no `ndkVersion`. CI installs `28.2.13676358` and uses the path in `setup-oru`. Set the version in Gradle, or accept the one the plugin picks, and say which.
4. **Rust toolchain not pinned.** No `rust-toolchain.toml`. CI uses the runner's stable toolchain.
5. **No key generation or keystore helper.** `first-time-setup.md` gives a `keytool` example. It is not a repo procedure.
6. **`release-notes/` does not exist.** The first stable release needs `release-notes/vX.Y.Z.md`, which the Cut stable release workflow can write from its `notes` input.
7. **Root `CHANGELOG.md` and `app/CHANGELOG.md` are inherited from upstream.** No workflow writes them, and their content is upstream's.
8. **Base version is a placeholder.** `app/gradle.properties` holds `0.0.1`. Set the first real base on `dev`.
9. **Gradle verification metadata command is not written.** `dependabot.yml` says the file must be regenerated; the command is not documented.
10. **`lint-baseline.xml` and `app/aboutlibraries`.** Both are committed. Which task regenerates them is not written in the repo.
11. **`pages.yml` and `site/`.** Not in this branch. Another agent creates them. Needed for the homepage (`chores.md`, 13).
12. **Labels.** `config.yml` links to a `question` label that the repo does not create.
13. **Discussions.** Not used by the repo. Turn it on or leave it off on purpose.
14. **Upstream naming.** Whether upstream uses the same branch names was not checked from this repo.

## Repository settings the owner must set (not in the repo)

15. **Environments.** Create `dev-release` (no reviewer) and `release` (owner as required reviewer, deployment branches `dev`, `main`, and tags `v*.*.*`). Create `github-pages` when Pages deploys (item 19). Also the `production` environment that the stable build used before. It is no longer referenced, so it can be deleted.
16. **Secrets and variables.** `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEYSTORE_ENTRY_ALIAS`, `KEYSTORE_ENTRY_PASSWORD` in the `dev-release` and `release` environments. `ORU_RELEASE_CERT_SHA256` as a variable.
17. **Branch protection on `main` and `dev`.** The lead applied it once, with the commands in `branch-protection.md`. The live state was not checked for this document. The required status checks (`android`, `patcher`, `patch-api-guard`, `rust`) are to be added after the first CI run on `main`.
18. **GitHub Pages.** Enable it with the source "GitHub Actions", and let the `github-pages` environment deploy from `dev` and `main`.
19. **Actions settings.** Allowed actions, "Allow GitHub Actions to create and approve pull requests" (needed by the Cut stable release workflow), merge commits allowed, artifact and log retention 5 days, fork pull request approval, and read-only default workflow permissions.
20. **Private vulnerability reporting, Dependabot alerts and security updates.** Turn them on. The repo cannot show that they are on.
21. **Dev release reviewer.** The `dev-release` environment has no reviewer, so every push to `dev` that changes build inputs makes a release. This is a decision for the owner to confirm.

## New workflows: not yet run

22. **Cut stable release, Yank release and Housekeeping have not been run.** The first Cut stable release should be a dry run. Check the job summary before a real run.
23. **Visibility of manual workflows.** A `workflow_dispatch` workflow is listed in the Actions tab only after its file is on the default branch (`dev`). Until the branch is merged, these runs are not available from the phone.
24. **Cut stable release needs a merge commit.** The merge uses `gh pr merge --merge`. The repo setting "Allow merge commits" must be on.
25. **Cut stable release uses gh features that the repo does not already use.** `gh release edit --prerelease` and `gh release delete --yes` in Yank release, and `gh pr merge --match-head-commit` in Cut stable release. Check them with the first run.
26. **Cut stable release creates the tag with the REST API.** `gh api -X POST repos/.../git/refs` with `-f` fields, which gh sends as JSON. Check the first run.
27. **Notes commit identity.** The notes commit uses the identity `github-actions[bot]` with the address `41898282+github-actions[bot]@users.noreply.github.com`. That address is GitHub's bot address as far as known, and is not checked from the repo.
28. **Housekeeping caches are selected by creation time.** Caches older than 5 days are deleted, including a cache that is still used. Choose `last_accessed_at` instead if this costs too much CI time.
29. **Stable release workflow environment name changed.** `release-stable.yml` now uses `release` where it used `production`. Confirm that no other reference to `production` remains.

## Privacy and local files

30. **Private patterns.** The owner creates `scripts/privacy-patterns.local.txt` on the owner's machine. It is ignored by git and is not in the repo.
31. **Privacy scan not yet run on the whole history.** Run `scripts/privacy-scan.sh` before the first public push, with the range that covers the history to publish.

## Decisions pending

32. **Release base.** The first real base version on `dev`.
33. **Branch protection of `main`: linear history.** Recommended off, because the dev-to-main pull request is merged with a merge commit.
34. **Dev branch direct pushes.** Allowed for the owner. Restrict with `restrictions` if other people get write access.
