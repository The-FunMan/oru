# Remote dev box

How to work on Oru from a remote Linux machine (a cloud VM, a home server) over SSH, including from a phone. Nothing here names a particular machine. Keep the machine's address, port and key in your own notes, not in this repository.

## What the box needs

- Linux x86_64, 2 or more CPU cores, 8 GB of RAM or more (4 GB of swap helps), and 25 GB of free disk for the toolchain and the build outputs.
- `curl`, `tar`, `unzip`, `git`, `python3`, `tmux` and the GitHub CLI (`gh`).
- A disk formatted with the default ext4 inode ratio. An Android SDK and a Gradle cache are millions of small files. A disk made for a few large files (a media disk) can run out of inodes with plenty of free space: check `df -i` as well as `df -h`.

## Set up

```sh
git clone -b dev https://github.com/The-FunMan/oru.git /path/on/the/big/disk/oru
cd /path/on/the/big/disk/oru
git config user.name The-FunMan
git config user.email 336727550+The-FunMan@users.noreply.github.com
ORU_TOOLS=/path/on/the/big/disk/oru-tools scripts/dev/setup-linux.sh
```

The script installs JDK 21, the Android SDK with the NDK and CMake that CI uses, Rust with the four Android targets and a small-machine Gradle home, all under `ORU_TOOLS`, and writes `env.sh` there. Run it again after the versions in `.github/actions/setup-oru/action.yml` change.

Set the identity per repository, as above. If the machine is shared with other projects or other GitHub accounts, do not change the global git identity or the global `gh` login.

## Credentials

The box reads one token from a file, `$ORU_TOOLS/github-token`, mode 600. `env.sh` exports it as `GITHUB_TOKEN` and `GH_TOKEN` and sets `GITHUB_ACTOR`, so `gh` and Gradle use it for Oru without touching any other login on the machine.

- Use a fine-grained token limited to the Oru repository. For builds it needs read access to packages. To push it also needs contents write; to start workflows it needs actions write. Give it only what you use, and set an expiry.
- Create the file by typing the token yourself:

  ```sh
  install -m 600 /dev/null "$ORU_TOOLS/github-token"
  cat > "$ORU_TOOLS/github-token"     # paste the token, press Ctrl-D
  ```

- The signing key and the release secrets never go on the box. Releases are built by GitHub Actions.
- To let `git push` use the token, the repository's helper is `!gh auth git-credential` (set in the clone with `git config --local credential.helper '!gh auth git-credential'`).

## Daily use

```sh
tmux new-session -A -s oru          # a session that survives SSH drops; detach with Ctrl-b d
source "$ORU_TOOLS/env.sh"
cd /path/on/the/big/disk/oru
git pull --ff-only
scripts/dev/check.sh quick          # a few minutes
scripts/dev/check.sh full           # everything CI runs
```

Run one Gradle build at a time. After a long session, `./gradlew --stop` frees the daemons.

## Running Claude Code on the box

- Install it, log in once with `/login`, and start it inside tmux in the repository directory. It reads `CLAUDE.md` and `AGENTS.md` itself.
- Long builds and CI waits go to the background; the session survives a closed phone.
- It has the same rules as anywhere else: it does not push, tag or change settings unless asked, and a denied action stays denied.

## Housekeeping

| Check | Command | Act when |
| --- | --- | --- |
| Space | `df -h /path/on/the/big/disk` | above 80% |
| Inodes | `df -i /path/on/the/big/disk` | above 80% |
| Gradle cache | `du -sh $GRADLE_USER_HOME` | above 6 GB: delete `$GRADLE_USER_HOME/caches` and let it refill |
| Build outputs | `git clean -xfdn` (a dry run) | before long idle periods |
| Memory | `free -h` | a daemon near the limit: `./gradlew --stop` |
| Toolchain drift | compare the versions in `scripts/dev/setup-linux.sh` with the setup action | after a bump |

## If the box is not enough

A cold full build on 2 cores and 8 GB is slow but works, with the Gradle heap set in `$ORU_TOOLS/gradle-home/gradle.properties`. Let GitHub Actions do the heavy runs: push the branch and read the result with `gh run list` and `gh run view`. See [CI triage](ci-triage.md).
