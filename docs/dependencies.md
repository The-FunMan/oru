# Dependencies

Oru keeps its dependencies on the newest stable release. This page says how, and lists what is held back on purpose.

## How they stay current

- Dependabot checks Gradle (the app and the patcher), Rust crates and GitHub Actions every day, and groups each kind into one pull request to `dev` (`.github/dependabot.yml`).
- A Gradle update comes with its regenerated checksums in `gradle/verification-metadata.xml` and `patcher/gradle/verification-metadata.xml`. Run the Gradle build with `--write-verification-metadata sha256` and commit the result in the same pull request.
- The pull request must pass the same checks as any change: unit tests for both flavours, lint, the patcher tests and the public API check, and the Rust tests.
- Last full refresh: 16 October 2026, when every catalog was compared with Google Maven, Maven Central and the Gradle plugin portal and brought to the newest stable version.

## Held back on purpose

| Item | Why |
|---|---|
| Compose Material 3 `1.5.0-beta01` | The sheets use the 1.5 bottom sheet API, which the Compose BOM does not ship in a stable release yet. Move to the stable release when the BOM carries it. |
| Android Gradle Plugin `9.4.1`, Kotlin `2.4.21` | Newer lines (`9.5` alpha, Kotlin `2.5` beta) are pre-releases. Oru follows stable releases. |
| JUnit `5.14.4` in the patcher | JUnit 6 needs Java 17, and the patcher is built for Java 11 so it can run on Android. |
| ARSCLib and smali pins | Pinned to commits of forks that carry fixes the patcher needs, until those land upstream. |
| Patcher | The patcher lives in this repository (`patcher/`), so there is no published patcher to lag behind. |

Anything else that is behind is a bug in this page. Open an issue.
