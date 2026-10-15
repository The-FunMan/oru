#!/usr/bin/env bash
# Checks a stable release tag before it is built.
#
# Input: TAG, a plain vX.Y.Z tag. The tagged commit must be on origin/main, and app/gradle.properties in that commit
# must hold the same version without a -dev suffix. Release notes come from release-notes/<tag>.md when present;
# otherwise they are generated from the commits since the previous stable tag, and the first stable release must have
# its notes file.
#
# Writes tag and version to $GITHUB_OUTPUT and the notes to dist/RELEASE_NOTES.md.
set -euo pipefail

tag=${TAG:?TAG is not set}
if ! [[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "not a stable tag: $tag" >&2
  exit 1
fi
version=${tag#v}

IFS=. read -r ver_major ver_minor ver_patch <<< "$version"
if [ "$ver_major" -gt 20 ] || [ "$ver_minor" -gt 99 ] || [ "$ver_patch" -gt 999 ]; then
  echo "$tag is outside major 0..20, minor 0..99, patch 0..999 (the versionCode ranges)" >&2
  exit 1
fi

if ! git merge-base --is-ancestor HEAD origin/main; then
  echo "$tag is not on origin/main" >&2
  exit 1
fi

committed=$(sed -n 's/^version[[:space:]]*=[[:space:]]*//p' app/gradle.properties | tr -d '[:space:]\r')
if [ "$committed" != "$version" ]; then
  echo "app/gradle.properties holds '$committed' but the tag is $tag" >&2
  exit 1
fi

mkdir -p dist
notes="release-notes/$tag.md"
if [ -f "$notes" ]; then
  cp "$notes" dist/RELEASE_NOTES.md
else
  prev=$(git describe --tags --abbrev=0 --match 'v[0-9]*.[0-9]*.[0-9]*' --exclude '*-dev*' "$tag~1" 2>/dev/null || true)
  if [ -z "$prev" ]; then
    echo "$notes is missing and there is no earlier stable tag to generate notes from" >&2
    exit 1
  fi
  git log --no-merges --pretty='- %s (%h)' "$prev..HEAD" > dist/RELEASE_NOTES.md
fi

{
  echo "tag=$tag"
  echo "version=$version"
} >> "${GITHUB_OUTPUT:?GITHUB_OUTPUT is not set}"
echo "stable tag $tag checked"
