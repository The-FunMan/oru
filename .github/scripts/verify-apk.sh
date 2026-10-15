#!/usr/bin/env bash
# Checks an Oru APK before it is published or uploaded.
#
# Usage: verify-apk.sh [--release] <apk>
#
# Checks, in every mode:
#   - the native libraries for all four ABIs are present (a missing library makes the patcher fall back to the JVM);
#   - 16 KB page alignment of the uncompressed libraries (zipalign -P 16, required on Android 15 and later);
#   - the signature verifies.
# With --release, the signing certificate SHA-256 must equal ORU_RELEASE_CERT_SHA256 (hex, colons optional).
# That also rejects any build signed with the Android debug key.
#
# Environment: ANDROID_HOME, and ORU_RELEASE_CERT_SHA256 with --release.
set -euo pipefail

release=false
if [ "${1:-}" = "--release" ]; then
  release=true
  shift
fi
if [ "$#" -ne 1 ]; then
  echo "usage: verify-apk.sh [--release] <apk>" >&2
  exit 2
fi
apk=$1
if [ ! -f "$apk" ]; then
  echo "APK not found: $apk" >&2
  exit 1
fi

build_tools="${ANDROID_HOME:?ANDROID_HOME is not set}/build-tools/36.0.0"
zipalign="$build_tools/zipalign"
apksigner="$build_tools/apksigner"

failed=0
fail() {
  echo "verify-apk: $*" >&2
  failed=1
}

entries=$(unzip -Z1 "$apk")
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  for lib in liboru_patcher.so liboru_packager.so; do
    if ! grep -qxF "lib/$abi/$lib" <<<"$entries"; then
      fail "missing lib/$abi/$lib"
    fi
  done
done

if ! "$zipalign" -c -P 16 4 "$apk"; then
  fail "16 KB alignment check failed"
fi

# Exits non-zero when the signature does not verify, which stops the script under set -e.
signer_info=$("$apksigner" verify --print-certs "$apk")
digest=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<<"$signer_info" | head -n 1 | tr -d ': ' | tr 'A-F' 'a-f')
if [ -z "$digest" ]; then
  fail "could not read the signing certificate digest"
fi
echo "signing certificate SHA-256: ${digest:-unknown}"

if [ "$release" = true ]; then
  expected=$(printf '%s' "${ORU_RELEASE_CERT_SHA256:?ORU_RELEASE_CERT_SHA256 is not set}" | tr -d ': ' | tr 'A-F' 'a-f')
  if [ "$digest" != "$expected" ]; then
    fail "signing certificate $digest does not match ORU_RELEASE_CERT_SHA256"
  fi
fi

if [ "$failed" -ne 0 ]; then
  exit 1
fi
echo "verify-apk: $apk passed"
