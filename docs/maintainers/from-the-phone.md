# Releases from the phone

This page is for running the release workflows from the GitHub mobile app or the web browser, with no computer. Every action here is a run of a workflow from the Actions tab. Nothing is run on a computer, and each workflow checks that you are the owner before it does anything.

## Which workflow for what

| Task | Workflow (name in the Actions tab) | Run on | Inputs |
| --- | --- | --- | --- |
| Build and publish a dev prerelease | Dev prerelease | branch `dev` | none |
| Cut a stable release | Cut stable release | branch `dev` | `version`, `dry_run`, `notes` |
| Check a stable release before cutting it | Cut stable release | branch `dev` | `dry_run` true |
| Take a bad release back | Yank release | default branch | `tag`, `action`, `confirm` |
| Clean up old runs, artifacts and caches | Housekeeping | default branch | `dry_run` |
| Run CI on the current `dev` head | CI | branch `dev` | none |

In the Actions tab: pick the workflow, choose "Run workflow", set the branch, fill the inputs, and tap "Run workflow".

## Inputs

- Dev prerelease: no inputs. Set the branch to `dev`. It fails on any other branch.
- Cut stable release:
  - `version`: `X.Y.Z`, for example `1.2.0`. No `v`, no `-dev`. It must be higher than the latest stable tag and must equal `app/gradle.properties` on `dev`.
  - `dry_run`: keep it on the first time. It shows the plan in the run summary and changes nothing.
  - `notes`: optional text. Required for the first stable release.
- Yank release:
  - `tag`: the release tag, for example `v1.2.0` or `v1.2.1-dev.3`.
  - `action`: `mark-prerelease` (off the stable feed) or `delete-release` (off every feed).
  - `confirm`: the same tag again. The run stops when it differs.
- Housekeeping: `dry_run` is on by default when you run it by hand. Turn it off to delete. The weekly scheduled run deletes.

## Approvals

- The `dev-release` environment (dev builds): no reviewer, so the dev build runs at once.
- The `release` environment: the owner approves. Cut stable release asks once, in its `cut` job. Stable release asks once more, in its build job, when the tag starts the build. Two taps in total, both in the run page or the notification.

## Cut a stable release from the phone, step by step

1. Check that `dev` is ready: CI is green on the latest commit of `dev` (Actions > CI, branch `dev`).
2. Actions > Cut stable release > Run workflow. Branch `dev`. `version` `X.Y.Z`. `dry_run` on. Run it.
3. Open the run summary. The plan lists the tag, the commit and the steps. If the run fails, the message names the problem. Fix it on `dev` and repeat from step 2.
4. Run it again with `dry_run` off. Add `notes` if this is the first stable release.
5. Approve the `release` environment for the `cut` job.
6. Wait for the Stable release run that the cut job started. Approve the `release` environment for its build job.
7. When the release is published, check it (next section).
8. Bump the version on `dev` to the next patch, for example `X.Y.(Z+1)`, so dev builds keep working. Do this from the web editor on `dev`: open `app/gradle.properties`, change the `version` line, and commit to `dev`.

## Check the release afterwards

On the Releases page, the latest release has:

- `oru-<version>.apk`, the APK
- `SHA256SUMS.txt`, the checksum list

In the Actions run of Stable release, the `publish` job has an attestation step that ran without an error.

Checks that need a computer, to do later: the SHA-256 of the APK against `SHA256SUMS.txt`, `gh attestation verify`, and the signer check. They are in `release-stable.md`, "Verify the assets".

Checks on a device, to do later: install over the previous build, and check the update feed. They are in `release-stable.md`, "Device smoke test".

## If a run fails midway

The state of the repository after each step tells you what to do next. Re-running a run is safe in each state shown here.

| Failed at | Public state | What to do |
| --- | --- | --- |
| `plan` (Cut stable release) | nothing changed | Fix the version or the file on `dev` and run again |
| `notes` | nothing, or the notes file is on `dev` | Run again. A notes file that is already committed is not committed twice |
| `ci` | the notes file may be on `dev`, no pull request yet | Look at the failing CI check, fix it on `dev`, then run again. A run on a new `dev` head needs CI again |
| `cut`, before the merge | an open pull request `dev` -> `main` | Run again. It reuses the open pull request |
| `cut`, after the merge, before the tag | the merge is on `main`, no tag | Run again. It finds the merged pull request, creates the tag and starts the release |
| `cut`, after the tag, the start of the release failed | the tag `vX.Y.Z` exists, no release | Actions > Stable release > Run workflow, branch `main`, `tag` `vX.Y.Z`. Do not run Cut stable release again for the same tag |
| Stable release, build or publish | the tag exists, no release (publish is one command, so there is no half-published release) | Run Stable release again for the same tag if the cause was not in the code (a secret, a network error). If the code needs a change, do not reuse the tag: release the next version, and see "Tags that never had a release" in `release-stable.md` |

Two things never to do from the phone: push a tag by hand while a cut is running, and run the Cut stable release workflow twice for the same version at the same time (its concurrency group prevents that, but do not rely on it for a retry after a long wait).

If the release is already published and wrong, do not edit the tag. Run Yank release (`mark-prerelease` or `delete-release`) and publish the next version. The rules are in `release-stable.md`, "Yank a bad release".

## Housekeeping from the phone

Housekeeping runs every Monday on its own. It deletes workflow runs, artifacts and caches older than 5 days in this repository. It never touches releases or release assets. To look first, run it with `dry_run` on and read the log.

## Limits to know

- Each run is a one-time action. The workflows do not retry on their own, except the CI wait in `ci-gate.sh`.
- The mobile app shows the run summary and the logs, and the approvals. A computer is needed for the verification steps in `release-stable.md`.
- The repository settings in `first-time-setup.md` and `branch-protection.md` must be in place. Without the "Allow GitHub Actions to create and approve pull requests" setting, the cut job cannot open the pull request.
