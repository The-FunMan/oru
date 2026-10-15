#!/usr/bin/env bash
# Checks a stable version before cut-stable.yml opens a release.
#
# Input: VERSION, a plain X.Y.Z (no v, no -dev). It must be inside the versionCode ranges (major 0..20, minor 0..99,
# patch 0..999), greater than the newest vX.Y.Z tag, and not tagged yet. app/gradle.properties on the checked-out
# commit must hold the same version. Without an earlier stable tag there are no commits to take notes from, so the
# first stable release needs NOTES (the release notes text).
#
# Writes tag, version and sha to $GITHUB_OUTPUT when it is set.
set -euo pipefail

version=${VERSION:?VERSION is not set}
fail() {
  echo "validate-stable-version: $*" >&2
  exit 1
}

if ! [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  fail "version must be X.Y.Z, found '$version'"
fi
IFS=. read -r ver_major ver_minor ver_patch <<< "$version"
if [ "$ver_major" -gt 20 ] || [ "$ver_minor" -gt 99 ] || [ "$ver_patch" -gt 999 ]; then
  fail "$version is outside major 0..20, minor 0..99, patch 0..999 (the versionCode ranges)"
fi

tag="v$version"
if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
  fail "$tag already exists"
fi

latest=$(git tag -l 'v[0-9]*.[0-9]*.[0-9]*' | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sed 's/^v//' | sort -V | tail -n 1 || true)
if [ -n "$latest" ] && [ "$(printf '%s\n%s\n' "$latest" "$version" | sort -V | tail -n 1)" != "$version" ]; then
  fail "$version is not greater than the latest stable tag v$latest"
fi

committed=$(sed -n 's/^version[[:space:]]*=[[:space:]]*//p' app/gradle.properties | tr -d '[:space:]\r')
if [ "$committed" != "$version" ]; then
  fail "app/gradle.properties holds '$committed', not $version"
fi

if [ -z "$latest" ] && [ -z "${NOTES:-}" ]; then
  fail "the first stable release needs the notes input"
fi

sha=$(git rev-parse HEAD)
echo "validate-stable-version: $tag at $sha is valid (latest stable: ${latest:-none})"
if [ -n "${GITHUB_OUTPUT:-}" ]; then
  {
    echo "tag=$tag"
    echo "version=$version"
    echo "sha=$sha"
  } >> "$GITHUB_OUTPUT"
fi
