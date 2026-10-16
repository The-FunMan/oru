#!/usr/bin/env bash
# The local version of the CI checks. Run it from the repository root before you push.
#
#   scripts/dev/check.sh quick   privacy scan, site check, Rust and Python tests, patcher tests (a few minutes)
#   scripts/dev/check.sh full    quick, plus the app unit tests for both flavors, lint and the debug-signed Oru APK
#
# Build tools come from the environment: source the env.sh that scripts/dev/setup-linux.sh wrote, or have JDK 21,
# the Android SDK, Rust and GITHUB_ACTOR / GITHUB_TOKEN set up yourself.
set -euo pipefail

mode=${1:-quick}
case "$mode" in quick | full) ;; *) echo "usage: $0 quick|full" >&2; exit 2 ;; esac
cd "$(git rev-parse --show-toplevel)"

step() { printf '\n== %s\n' "$*"; }

step "Privacy scan and site check"
scripts/privacy-scan.sh
scripts/check-site.sh

step "Rust crates and Python tools"
(cd native-packager && cargo test --workspace --locked)
(cd native && cargo test --locked)
python3 -I -m unittest discover -s tools/parity -p "test_*.py"

step "Patcher tests, API check and jar"
./gradlew :patcher:test :patcher:apiCheck :patcher:jar --stacktrace

if [ "$mode" = full ]; then
  step "App unit tests (both flavors) and lint"
  ./gradlew :app:testOruDebugUnitTest :app:testMorpheDebugUnitTest :app:lint --stacktrace

  step "Oru release APK, debug-signed"
  ./gradlew :app:assembleOruRelease -PsignAsDebug --stacktrace
  .github/scripts/verify-apk.sh app/build/outputs/apk/oru/release/*.apk
fi

printf '\ncheck.sh %s: all green\n' "$mode"
