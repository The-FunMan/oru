#!/usr/bin/env bash
# Downloads the patch bundle that tools/parity/pinned-bundle.env names and checks its SHA-256, so the API guard
# in CI runs against the same bytes on every run. The download is retried; a checksum mismatch is never retried.
#
#   GH_TOKEN=... .github/scripts/fetch-pinned-bundle.sh OUT_DIR
#
# Prints the path of the verified bundle on the last line.
set -euo pipefail

out=${1:?usage: fetch-pinned-bundle.sh OUT_DIR}
here=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=/dev/null
source "$here/../../tools/parity/pinned-bundle.env"

mkdir -p "$out"
"$here/retry.sh" 4 gh release download "$BUNDLE_TAG" --repo "$BUNDLE_REPO" --pattern "$BUNDLE_ASSET" --dir "$out" --clobber

actual=$(sha256sum "$out/$BUNDLE_ASSET" | cut -d' ' -f1)
if [ "$actual" != "$BUNDLE_SHA256" ]; then
  echo "fetch-pinned-bundle: $BUNDLE_ASSET has sha256 $actual, expected $BUNDLE_SHA256" >&2
  exit 1
fi
echo "$out/$BUNDLE_ASSET"
