#!/usr/bin/env bash
# Works out the dev prerelease for a push to dev or a manual run.
#
# The base version X.Y.Z comes from app/gradle.properties and must be a plain release number within the versionCode
# ranges (major 0..20, minor 0..99, patch 0..999). The dev number N is one
# above the highest existing vX.Y.Z-dev.* tag. The job fails when vX.Y.Z already exists, so the base is bumped after
# every stable release. A push that changes no build input since the last dev tag is skipped; a manual run always builds.
#
# Writes tag, version and skip to $GITHUB_OUTPUT and the release notes to dist/RELEASE_NOTES.md.
set -euo pipefail

base=$(sed -n 's/^version[[:space:]]*=[[:space:]]*//p' app/gradle.properties | tr -d '[:space:]\r')
if ! [[ "$base" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "app/gradle.properties must hold X.Y.Z for dev builds, found '$base'" >&2
  exit 1
fi
IFS=. read -r base_major base_minor base_patch <<< "$base"
if [ "$base_major" -gt 20 ] || [ "$base_minor" -gt 99 ] || [ "$base_patch" -gt 999 ]; then
  echo "base version $base is outside major 0..20, minor 0..99, patch 0..999 (the versionCode ranges)" >&2
  exit 1
fi
if git rev-parse -q --verify "refs/tags/v$base" >/dev/null; then
  echo "tag v$base already exists: bump the version in app/gradle.properties" >&2
  exit 1
fi

last_n=0
last_tag=""
while IFS= read -r t; do
  n=${t##*-dev.}
  if [[ "$n" =~ ^[0-9]+$ ]] && [ "$n" -gt "$last_n" ]; then
    last_n=$n
    last_tag=$t
  fi
done < <(git tag -l "v$base-dev.*")

next_n=$((last_n + 1))
if [ "$next_n" -gt 998 ]; then
  echo "dev build number $next_n exceeds maximum allowed (998): bump base version in app/gradle.properties" >&2
  exit 1
fi

version="$base-dev.$next_n"
tag="v$version"

skip=false
if [ "${GITHUB_EVENT_NAME:-}" != "workflow_dispatch" ] && [ -n "$last_tag" ]; then
  if git diff --quiet "$last_tag" HEAD -- app native native-packager patcher gradle \
       build.gradle.kts settings.gradle.kts gradle.properties gradlew; then
    skip=true
  fi
fi

mkdir -p dist
if [ -n "$last_tag" ]; then
  git log --no-merges --pretty='- %s (%h)' "$last_tag..HEAD" > dist/RELEASE_NOTES.md
else
  printf 'First development build of Oru %s, from commit %s.\n' "$version" "$(git rev-parse --short HEAD)" \
    > dist/RELEASE_NOTES.md
fi

{
  echo "tag=$tag"
  echo "version=$version"
  echo "skip=$skip"
} >> "${GITHUB_OUTPUT:?GITHUB_OUTPUT is not set}"
echo "dev build $tag, skip=$skip"
