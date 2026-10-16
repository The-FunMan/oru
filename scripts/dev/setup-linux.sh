#!/usr/bin/env bash
# Sets up a Linux x86_64 box to build and test Oru: JDK 21, the Android SDK with the NDK and CMake the native
# crates need, Rust with the Android targets, and a Gradle home sized for a small machine.
#
# Everything is installed under one directory, ORU_TOOLS (default ~/oru-tools), so it can live on a large disk and
# nothing outside it is touched. No sudo. Needs curl, tar, unzip and git. Safe to run again: finished steps are skipped.
#
# The versions below match .github/actions/setup-oru/action.yml. Change both together.
#
#   ORU_TOOLS=/path/to/big/disk/oru-tools scripts/dev/setup-linux.sh
#   source "$ORU_TOOLS/env.sh"
set -euo pipefail

tools=${ORU_TOOLS:-$HOME/oru-tools}
jdk_major=21
sdk_packages=("platforms;android-37.0" "build-tools;36.0.0" "ndk;28.2.13676358" "cmake;3.22.1" "platform-tools")
cmdline_tools_url=https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
rust_targets=(aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android)

log() { printf '\n== %s\n' "$*"; }
need() { command -v "$1" >/dev/null || { echo "setup-linux: $1 is required" >&2; exit 1; }; }
for cmd in curl tar unzip git; do need "$cmd"; done
[ "$(uname -m)" = x86_64 ] || { echo "setup-linux: only x86_64 is scripted" >&2; exit 1; }

mkdir -p "$tools"
tools=$(cd "$tools" && pwd)
echo "Installing into $tools ($(df -h --output=avail "$tools" | tail -n 1 | tr -d ' ') free)"

log "JDK $jdk_major (Temurin)"
if [ ! -x "$tools/jdk/bin/java" ]; then
  tmp=$(mktemp -d)
  curl -fsSL --retry 5 --retry-delay 3 "https://api.adoptium.net/v3/binary/latest/$jdk_major/ga/linux/x64/jdk/hotspot/normal/eclipse" -o "$tmp/jdk.tgz"
  mkdir -p "$tools/jdk"
  tar -xzf "$tmp/jdk.tgz" -C "$tools/jdk" --strip-components=1
  rm -rf "$tmp"
fi
"$tools/jdk/bin/java" -version 2>&1 | head -n 1

log "Android command line tools"
sdk="$tools/android-sdk"
if [ ! -x "$sdk/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp=$(mktemp -d)
  curl -fsSL --retry 5 --retry-delay 3 "$cmdline_tools_url" -o "$tmp/cmdline.zip"
  unzip -q "$tmp/cmdline.zip" -d "$tmp"
  mkdir -p "$sdk/cmdline-tools"
  rm -rf "$sdk/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$sdk/cmdline-tools/latest"
  rm -rf "$tmp"
fi

log "SDK packages"
export JAVA_HOME="$tools/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
sdkmanager="$sdk/cmdline-tools/latest/bin/sdkmanager"
# yes gets SIGPIPE once sdkmanager exits, so pipefail is off for this one pipeline.
( set +o pipefail; yes | "$sdkmanager" --sdk_root="$sdk" --licenses >/dev/null )
"$sdkmanager" --sdk_root="$sdk" "${sdk_packages[@]}"

log "Rust"
export RUSTUP_HOME="$tools/rustup" CARGO_HOME="$tools/cargo"
if [ ! -x "$CARGO_HOME/bin/rustup" ]; then
  curl -fsSL --retry 5 --retry-delay 3 https://sh.rustup.rs | sh -s -- -y --profile minimal --no-modify-path >/dev/null
fi
"$CARGO_HOME/bin/rustup" target add "${rust_targets[@]}"
"$CARGO_HOME/bin/rustc" --version

log "Gradle home"
mkdir -p "$tools/gradle-home"
# A small machine: two workers, a daemon that leaves room for the Kotlin daemon and the OS.
# These override the repo's gradle.properties only on this box.
cat > "$tools/gradle-home/gradle.properties" <<'PROPS'
org.gradle.jvmargs=-Xms512m -Xmx3g -Dfile.encoding=UTF-8
org.gradle.workers.max=2
kotlin.daemon.jvmargs=-Xmx2g
PROPS

log "env.sh"
cat > "$tools/env.sh" <<ENV
# Source this before building Oru: source "$tools/env.sh"
export ORU_TOOLS="$tools"
export JAVA_HOME="$tools/jdk"
export ANDROID_HOME="$sdk"
export ANDROID_SDK_ROOT="$sdk"
export GRADLE_USER_HOME="$tools/gradle-home"
export RUSTUP_HOME="$tools/rustup"
export CARGO_HOME="$tools/cargo"
export PATH="\$JAVA_HOME/bin:\$CARGO_HOME/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/cmdline-tools/latest/bin:\$PATH"
# Credentials are read from a file, never stored in this script. The file holds one line: a token that can read
# packages (and, for pushing, write to the repository). Create it with: install -m 600 /dev/null "$tools/github-token"
if [ -s "$tools/github-token" ]; then
  export GITHUB_ACTOR="\${GITHUB_ACTOR:-The-FunMan}"
  export GITHUB_TOKEN="\$(tr -d '[:space:]' < "$tools/github-token")"
  export GH_TOKEN="\$GITHUB_TOKEN"
fi
ENV

echo
echo "Done. Next: source $tools/env.sh"
du -sh "$tools" 2>/dev/null | cut -f1 | sed 's/^/Installed size: /'
