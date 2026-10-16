# CI triage

What to do when a workflow run is red. Read the failing step first. Do not retry in a loop, and do not weaken a check to get green.

## Find the failure

```sh
gh run list --limit 10 --json databaseId,name,status,conclusion,headSha,event
gh run view <id> --json jobs --jq '.jobs[] | "\(.name): \(.conclusion)"'
gh run view <id> --log-failed | grep -aiE "error|failed|what went wrong" | head -40
```

A job that is `failure` with no steps was refused before it started. The reason is in the check run annotations:

```sh
gh api repos/The-FunMan/oru/check-runs/<job id>/annotations --jq '.[].message'
```

## Failures seen so far

| Symptom | Cause | Fix |
| --- | --- | --- |
| "Verification of Gradle distribution failed" | `distributionSha256Sum` in a `gradle-wrapper.properties` does not match the zip | Copy the value from `https://services.gradle.org/distributions/gradle-<version>-bin.zip.sha256`, in both the root and `patcher/` wrappers. Check with an empty `GRADLE_USER_HOME`. |
| `dependency-review`: "not supported on this repository" | The dependency graph is off | Enable it in Settings > Advanced Security |
| "Branch dev is not allowed to deploy to release" | The `release` environment does not allow `dev` | Settings > Environments > release > Deployment branches and tags: `main`, `dev`, `v*.*.*` |
| "is not greater than the latest stable tag" | A tag that is not an Oru release exists | List tags with `git ls-remote --tags origin`. Only Oru tags belong. |
| "app/gradle.properties holds X, not Y" | The version input differs from the committed version | Make them equal, on `dev`, before cutting |
| "tag vX.Y.Z already exists: bump the version" (Dev prerelease) | `version` equals a stable tag | Bump `version` in `app/gradle.properties` to the next minor |
| `sdkmanager could not install the SDK packages` | A package name changed | Compare with `sdkmanager --list`; update `.github/actions/setup-oru/action.yml` and `scripts/dev/setup-linux.sh` together |
| Dependabot PR fails verification | `verification-metadata.xml` not updated | Run the build with `--write-verification-metadata sha256` and commit both files |
| "Dependency verification failed" on a `*.gradle.plugin-*.pom` for a Kotlin plugin, only on some runs | The plugin portal and Maven Central serve different bytes for the same marker POM | Add the other repository's checksum as `<also-trust value="..."/>` inside that artifact's `<sha256>` in both `verification-metadata.xml` files. Fetch `https://plugins.gradle.org/m2/<path>` and `https://repo.maven.apache.org/maven2/<path>` and hash both. |
| CI says `pending` for a long time with no jobs | The run waits for approval (first-time contributor) or for a queued runner | Approve it in the Actions tab, or wait |
| A required approval is waiting | The `release` environment needs the owner | Approve it from the Actions tab or the phone |

## Order of work

1. Name the cause from the log, in one sentence.
2. Reproduce it locally or on the dev box when it is cheap (`scripts/dev/check.sh quick`).
3. Fix the cause in a commit with a conventional title, for example `fix(build): correct the Gradle 9.8.1 wrapper checksum`.
4. Push only when the owner has asked for it, wait for the new run, and read it to the end.
5. If the cause was new, add it to the table above and to [Lessons](../knowledge/lessons.md).

## What not to do

- Do not re-run a failed job without reading it.
- Do not mark a test skipped, delete a check or loosen a gate to pass.
- Do not use the owner's approval to cover a run you did not read.
- Do not change repository or environment settings to make a run pass without telling the owner what changed.
