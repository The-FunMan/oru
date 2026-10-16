# Lessons

Each entry is something that cost time once, and the check that prevents it.

| What happened | Why | Prevent it |
| --- | --- | --- |
| Every Gradle job failed with "Verification of Gradle distribution failed". | The wrapper `distributionSha256Sum` was wrong. It passed locally because that machine had the distribution cached. | Take the checksum from the official `.sha256` file next to the zip, and run `./gradlew --version` with an empty `GRADLE_USER_HOME` before pushing a wrapper change. |
| The stable release check refused every version as "not greater than v1.35.0". | The repository carried the upstream tags from the inherited history. | Only Oru tags live in the repository. If an upstream sync brings tags, do not push them. |
| The Cut stable release workflow failed before any step: "Branch dev is not allowed to deploy to release". | The `release` environment allowed only `main` and the tag pattern, and the cut job runs on `dev`. | Allowed refs of the `release` environment: `main`, `dev` and `v*.*.*`. Check them after any change to the environment. |
| A workflow shows in the Actions tab only after it is on the default branch. | GitHub lists workflows from the default branch. | The very first merge to `main` is manual. After that the cut workflow handles it. |
| The first dev prerelease after a stable tag failed with "tag already exists". | The version in `app/gradle.properties` equalled the stable tag. | After each stable release, bump `version` on `dev` to the next minor. |
| `dependency-review` fails on pull requests. | The dependency graph is off in the repository settings. | Enable the dependency graph. |
| A keystore with a different entry password failed to sign. | PKCS12 stores use one password for the store and the entry. | Set the entry password equal to the store password. |
| A device with a full ext4 data disk reported "No space left on device" with 54 GB free. | The disk had been created with 16,384 inodes (one per 4 MiB), a layout for a few big files. Small-file trees such as an Android SDK need millions. | Check `df -i` as well as `df -h`. Create work disks with the default inode ratio. |
| A screenshot of a device showed personal calendar text. | The launcher was visible. | Capture only Oru's own screens and delete anything else at once. See [device QA](../maintainers/device-qa.md). |
| Scripted Back presses left the app and landed on the launcher. | Back on the root screen exits. | Close sheets by swiping down from their title. Never press Back on a root screen. |
| The Play Protect scan prompt on install asks for a fingerprint. | Debug-signed builds trigger the scan. | Choose "Don't install app", then the system "Update this app?" prompt, then Update. Never enter a fingerprint on someone's behalf. |
| Progress sat at 9% for 25 seconds, then jumped. | Resource decoding reports no progress. | The hexagon sweeps on a stall instead of inventing a percentage; finer reporting needs a patcher change. |
| A pull request CI run failed with "Dependency verification failed" for a Kotlin plugin POM, while the same commit passed on a push. | The plugin portal and Maven Central serve different bytes for the same Kotlin plugin marker POM. A cold run (pull requests read the cache but do not write it) can fetch the portal copy; a warm cache holds the Central copy. | Each Kotlin marker POM in `verification-metadata.xml` carries both checksums (`also-trust`). After a Kotlin bump and `--write-verification-metadata`, see [CI triage](../maintainers/ci-triage.md) and add any missing second checksum. |
